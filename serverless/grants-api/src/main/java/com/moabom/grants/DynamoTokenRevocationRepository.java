package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;

/**
 * Prepared logout denylist. The partition key is the JWT jti and expiresAt is the DynamoDB TTL.
 * Local execution uses {@link InMemoryTokenRevocationRepository}. This class does not connect until a method runs.
 */
public final class DynamoTokenRevocationRepository implements TokenRevocationRepository {
    static final String TABLE_NAME_ENV = "TOKEN_REVOCATIONS_TABLE_NAME";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoTokenRevocationRepository(DynamoDbClient dynamoDb, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public static DynamoTokenRevocationRepository fromEnvironment() {
        String tableName = System.getenv(TABLE_NAME_ENV);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        return new DynamoTokenRevocationRepository(DynamoDbClient.create(), tableName);
    }

    @Override
    public void revoke(String tokenId, long expiresAtEpochSeconds) {
        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        "jti", AttributeValue.fromS(tokenId),
                        "expiresAt", AttributeValue.fromN(Long.toString(expiresAtEpochSeconds))
                ))
                .build());
    }

    @Override
    public boolean isRevoked(String tokenId) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("jti", AttributeValue.fromS(tokenId)))
                .build()).item();
        if (item == null || item.get("expiresAt") == null || item.get("expiresAt").n() == null) {
            return false;
        }
        return Long.parseLong(item.get("expiresAt").n()) > Instant.now().getEpochSecond();
    }
}
