package com.moabom.grants;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DynamoUserProfileRepository implements UserProfileRepository {
    static final String TABLE_NAME_ENV = "USERS_TABLE_NAME";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public DynamoUserProfileRepository(DynamoDbClient dynamoDb, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    public static DynamoUserProfileRepository fromEnvironment() {
        String tableName = System.getenv(TABLE_NAME_ENV);
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException(TABLE_NAME_ENV + " is not set");
        }
        return new DynamoUserProfileRepository(DynamoDbClient.create(), tableName);
    }

    @Override
    public Optional<UserProfile> getProfile(String userId) {
        Map<String, AttributeValue> item = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("userId", AttributeValue.fromS(userId)))
                .build()).item();
        return toProfile(item);
    }

    @Override
    public void saveProfile(UserProfile profile) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.fromS(profile.userId()));
        item.put("age", AttributeValue.fromN(Integer.toString(profile.age())));
        item.put("region", AttributeValue.fromS(profile.region()));
        item.put("incomeBracket", AttributeValue.fromN(Integer.toString(profile.incomeBracket())));
        putStrings(item, "householdTypes", profile.householdTypes());
        putStrings(item, "specialConditions", profile.specialConditions());
        putStrings(item, "interests", profile.interests());
        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    private static Optional<UserProfile> toProfile(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty() || item.get("userId") == null || item.get("userId").s() == null) {
            return Optional.empty();
        }
        return Optional.of(new UserProfile(
                item.get("userId").s(),
                number(item.get("age")),
                text(item.get("region")),
                number(item.get("incomeBracket")),
                strings(item.get("householdTypes"), PolicyCodes.HOUSEHOLDS, "가구 형태"),
                strings(item.get("specialConditions"), PolicyCodes.SPECIALS, "특수조건"),
                InterestLabels.normalize(rawStrings(item.get("interests")))
        ));
    }


    private static void putStrings(Map<String, AttributeValue> item, String name, java.util.List<String> values) {
        if (values != null && !values.isEmpty()) {
            item.put(name, AttributeValue.fromSs(values));
        }
    }

    private static java.util.List<String> strings(AttributeValue value, java.util.Set<String> allowed, String label) {
        return PolicyCodes.normalize(rawStrings(value), allowed, label);
    }

    private static java.util.List<String> rawStrings(AttributeValue value) {
        if (value == null) {
            return java.util.List.of();
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        if (value.ss() != null) {
            values.addAll(value.ss());
        } else if (value.l() != null) {
            value.l().forEach(entry -> {
                if (entry.s() != null) {
                    values.add(entry.s());
                }
            });
        }
        return values;
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
