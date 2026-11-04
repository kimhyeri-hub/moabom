package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DynamoGrantsRepository implements GrantsRepository {
    static final String TABLE_NAME_ENV = "GRANTS_TABLE_NAME";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoGrantsRepository(DynamoDbClient dynamoDb, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public static DynamoGrantsRepository fromEnvironment() {
        String tableName = System.getenv(TABLE_NAME_ENV);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        return new DynamoGrantsRepository(DynamoDbClient.create(), tableName);
    }

    @Override
    public GrantPage listGrants(int limit, String exclusiveStartGrantId) {
        Map<String, AttributeValue> values = Map.of(
                ":entityType", AttributeValue.fromS(GrantAttributes.GRANT_PARTITION)
        );
        QueryRequest.Builder request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(GrantAttributes.ENTITY_TYPE + " = :entityType")
                .expressionAttributeValues(values)
                .limit(limit);

        if (exclusiveStartGrantId != null) {
            Map<String, AttributeValue> startKey = new HashMap<>();
            startKey.put(GrantAttributes.ENTITY_TYPE, AttributeValue.fromS(GrantAttributes.GRANT_PARTITION));
            startKey.put(GrantAttributes.GRANT_ID, AttributeValue.fromS(exclusiveStartGrantId));
            request.exclusiveStartKey(startKey);
        }

        QueryResponse response = dynamoDb.query(request.build());
        List<Grant> grants = response.items().stream()
                .map(GrantMapper::fromItem)
                .filter(grant -> grant.grantId() != null && !grant.grantId().isBlank())
                .toList();

        String nextToken = null;
        if (response.lastEvaluatedKey() != null && response.lastEvaluatedKey().get(GrantAttributes.GRANT_ID) != null) {
            nextToken = response.lastEvaluatedKey().get(GrantAttributes.GRANT_ID).s();
        }
        return new GrantPage(grants, nextToken);
    }

    @Override
    public Optional<Grant> getGrant(String grantId) {
        Map<String, AttributeValue> key = Map.of(
                GrantAttributes.ENTITY_TYPE, AttributeValue.fromS(GrantAttributes.GRANT_PARTITION),
                GrantAttributes.GRANT_ID, AttributeValue.fromS(grantId)
        );
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        Grant grant = GrantMapper.fromItem(item);
        if (grant.grantId() == null || grant.grantId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(grant);
    }
}
