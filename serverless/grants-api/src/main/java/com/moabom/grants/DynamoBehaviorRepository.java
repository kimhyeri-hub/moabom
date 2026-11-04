package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DynamoBehaviorRepository implements BehaviorRepository {
    static final String TABLE_NAME_ENV = "BEHAVIORS_TABLE_NAME";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoBehaviorRepository(DynamoDbClient dynamoDb, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public static DynamoBehaviorRepository fromEnvironment() {
        String tableName = System.getenv(TABLE_NAME_ENV);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        return new DynamoBehaviorRepository(DynamoDbClient.create(), tableName);
    }

    @Override
    public List<UserBehavior> listByUser(String userId) {
        List<UserBehavior> behaviors = new ArrayList<>();
        Map<String, AttributeValue> startKey = null;
        for (int page = 0; page < 20 && behaviors.size() < 500; page++) {
            QueryRequest.Builder request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("userId = :userId")
                    .expressionAttributeValues(Map.of(":userId", AttributeValue.fromS(userId)))
                    .limit(100);
            if (startKey != null) {
                request.exclusiveStartKey(startKey);
            }
            QueryResponse response = dynamoDb.query(request.build());
            for (Map<String, AttributeValue> item : response.items()) {
                UserBehavior behavior = toBehavior(userId, item);
                if (behavior != null) {
                    behaviors.add(behavior);
                }
            }
            if (response.lastEvaluatedKey() == null || response.lastEvaluatedKey().isEmpty()) {
                break;
            }
            startKey = response.lastEvaluatedKey();
        }
        behaviors.sort(Comparator
                .comparing(UserBehavior::occurredAt)
                .thenComparing(behavior -> behavior.type().name())
                .thenComparing(UserBehavior::grantId));
        if (behaviors.size() > 500) {
            return List.copyOf(behaviors.subList(0, 500));
        }
        return List.copyOf(behaviors);
    }

    @Override
    public BehaviorWriteResult saveBehavior(UserBehavior behavior) {
        String behaviorSk = BehaviorKeys.behaviorSk(behavior.type(), behavior.grantId());
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.fromS(behavior.userId()));
        item.put("behaviorSk", AttributeValue.fromS(behaviorSk));
        item.put("behaviorType", AttributeValue.fromS(behavior.type().name()));
        item.put("grantId", AttributeValue.fromS(behavior.grantId()));
        item.put("occurredAt", AttributeValue.fromS(behavior.occurredAt()));
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(behaviorSk)")
                    .build());
            return new BehaviorWriteResult(behavior, true);
        } catch (ConditionalCheckFailedException ex) {
            UserBehavior existing = toBehavior(behavior.userId(), dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            "userId", AttributeValue.fromS(behavior.userId()),
                            "behaviorSk", AttributeValue.fromS(behaviorSk)
                    ))
                    .build()).item());
            if (existing == null) {
                throw new IllegalStateException("중복 행동 기록을 읽지 못했습니다.");
            }
            return new BehaviorWriteResult(existing, false);
        }
    }


    @Override
    public ScrapDeleteResult deleteScrap(String userId, String grantId) {
        String behaviorSk = BehaviorKeys.behaviorSk(BehaviorType.SCRAP, grantId);
        try {
            dynamoDb.deleteItem(software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(java.util.Map.of(
                            "userId", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(userId),
                            "behaviorSk", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(behaviorSk)
                    ))
                    .conditionExpression("attribute_exists(behaviorSk)")
                    .build());
            return new ScrapDeleteResult(true);
        } catch (ConditionalCheckFailedException ex) {
            return new ScrapDeleteResult(false);
        }
    }

    private static UserBehavior toBehavior(String userId, Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        String typeName = text(item.get("behaviorType"));
        String grantId = text(item.get("grantId"));
        String occurredAt = text(item.get("occurredAt"));
        if (typeName == null || grantId == null || occurredAt == null) {
            return null;
        }
        try {
            return new UserBehavior(userId, BehaviorType.valueOf(typeName), grantId, occurredAt);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(AttributeValue value) {
        if (value == null || value.s() == null || value.s().isBlank()) {
            return null;
        }
        return value.s();
    }
}
