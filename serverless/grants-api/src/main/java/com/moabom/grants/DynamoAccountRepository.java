package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Prepared DynamoDB account store. Local execution uses {@link InMemoryAccountRepository}.
 * This class is not called by tests and does not open a connection unless a method is invoked.
 */
public final class DynamoAccountRepository implements AccountRepository {
    static final String TABLE_NAME_ENV = "ACCOUNTS_TABLE_NAME";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoAccountRepository(DynamoDbClient dynamoDb, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public static DynamoAccountRepository fromEnvironment() {
        String tableName = System.getenv(TABLE_NAME_ENV);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        return new DynamoAccountRepository(DynamoDbClient.create(), tableName);
    }

    @Override
    public Account insertNew(Account draft) {
        long id = Long.parseLong(dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("accountKey", AttributeValue.fromS("COUNTER")))
                .updateExpression("ADD nextId :one")
                .expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1")))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build()).attributes().get("nextId").n());
        Account account = draft.withUserId(Long.toString(id));
        putLock("LOGIN#" + account.loginId(), account.userId(), "이미 사용 중인 아이디입니다.");
        try {
            putLock("EMAIL#" + account.email(), account.userId(), "이미 사용 중인 이메일입니다.");
        } catch (AccountConflictException ex) {
            delete("LOGIN#" + account.loginId());
            throw ex;
        }
        putUser(account);
        return account;
    }

    @Override
    public void insertPreservingId(Account account) {
        if (account.userId() == null || !ApiResponses.ID.matcher(account.userId()).matches()) {
            throw new IllegalArgumentException("사용자 ID 형식이 올바르지 않습니다.");
        }
        putLock("USER#" + account.userId(), account.userId(), "이미 사용 중인 사용자 ID입니다.");
        try {
            putLock("LOGIN#" + account.loginId(), account.userId(), "이미 사용 중인 아이디입니다.");
            putLock("EMAIL#" + account.email(), account.userId(), "이미 사용 중인 이메일입니다.");
        } catch (AccountConflictException ex) {
            delete("USER#" + account.userId());
            delete("LOGIN#" + account.loginId());
            throw ex;
        }
        putUser(account);
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("accountKey", AttributeValue.fromS("COUNTER")))
                .updateExpression("SET nextId = if_not_exists(nextId, :zero)")
                .expressionAttributeValues(Map.of(":zero", AttributeValue.fromN("0")))
                .build());
        try {
            dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("accountKey", AttributeValue.fromS("COUNTER")))
                    .conditionExpression("nextId < :imported")
                    .updateExpression("SET nextId = :imported")
                    .expressionAttributeValues(Map.of(":imported", AttributeValue.fromN(account.userId())))
                    .build());
        } catch (ConditionalCheckFailedException ignored) {
            // The counter is already past this imported id.
        }
    }

    @Override
    public Optional<Account> findByLoginId(String loginId) {
        return lockOwner("LOGIN#" + loginId).flatMap(this::findByUserId);
    }

    @Override
    public Optional<Account> findByUserId(String userId) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("accountKey", AttributeValue.fromS("USER#" + userId)))
                .build()).item();
        if (item == null || item.isEmpty() || item.get("loginId") == null) {
            return Optional.empty();
        }
        return Optional.of(new Account(
                text(item.get("userId")),
                text(item.get("loginId")),
                text(item.get("email")),
                text(item.get("name")),
                text(item.get("dateOfBirth")),
                text(item.get("passwordHash")),
                text(item.get("region")),
                number(item.get("incomeBracket"))
        ));
    }

    @Override
    public boolean loginIdExists(String loginId) {
        return lockOwner("LOGIN#" + loginId).isPresent();
    }

    @Override
    public void replace(Account account) {
        Account current = findByUserId(account.userId())
                .orElseThrow(() -> new IllegalArgumentException("인증된 사용자를 찾을 수 없습니다."));
        if (!current.loginId().equals(account.loginId())) {
            throw new IllegalArgumentException("아이디는 변경할 수 없습니다.");
        }
        if (!current.email().equals(account.email())) {
            putLock("EMAIL#" + account.email(), account.userId(), "이미 사용 중인 이메일입니다.");
            delete("EMAIL#" + current.email());
        }
        putUser(account);
    }

    private void putLock(String key, String userId, String conflictMessage) {
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(Map.of(
                            "accountKey", AttributeValue.fromS(key),
                            "userId", AttributeValue.fromS(userId),
                            "entityType", AttributeValue.fromS("LOCK")
                    ))
                    .conditionExpression("attribute_not_exists(accountKey)")
                    .build());
        } catch (ConditionalCheckFailedException ex) {
            throw new AccountConflictException(conflictMessage);
        }
    }

    private void putUser(Account account) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("accountKey", AttributeValue.fromS("USER#" + account.userId()));
        item.put("entityType", AttributeValue.fromS("USER"));
        item.put("userId", AttributeValue.fromS(account.userId()));
        item.put("loginId", AttributeValue.fromS(account.loginId()));
        item.put("email", AttributeValue.fromS(account.email()));
        item.put("name", AttributeValue.fromS(account.name()));
        item.put("passwordHash", AttributeValue.fromS(account.passwordHash()));
        if (account.dateOfBirth() != null) {
            item.put("dateOfBirth", AttributeValue.fromS(account.dateOfBirth()));
        }
        if (account.region() != null) {
            item.put("region", AttributeValue.fromS(account.region()));
        }
        if (account.incomeBracket() != null) {
            item.put("incomeBracket", AttributeValue.fromN(account.incomeBracket().toString()));
        }
        dynamoDb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    private Optional<String> lockOwner(String key) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("accountKey", AttributeValue.fromS(key)))
                .build()).item();
        if (item == null || item.get("userId") == null || item.get("userId").s() == null) {
            return Optional.empty();
        }
        return Optional.of(item.get("userId").s());
    }

    private void delete(String key) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("accountKey", AttributeValue.fromS(key)))
                .build());
    }

    private static String text(AttributeValue value) {
        if (value == null || value.s() == null || value.s().isBlank()) {
            return null;
        }
        return value.s();
    }

    private static Integer number(AttributeValue value) {
        if (value == null || value.n() == null || value.n().isBlank()) {
            return null;
        }
        return Integer.valueOf(value.n());
    }
}
