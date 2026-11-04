package com.moabom.grants;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ProfileValidator {
    static final int MIN_AGE = 0;
    static final int MAX_AGE = 120;
    static final int MIN_INCOME_BRACKET = 1;
    static final int MAX_INCOME_BRACKET = 10;
    static final int MAX_REGION_LENGTH = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProfileValidator() {
    }

    public static UserProfile parse(String userId, String body) {
        JsonNode node = objectBody(body);
        return new UserProfile(
                userId,
                requiredInt(node, "age", MIN_AGE, MAX_AGE, "연령은 0 이상 120 이하의 정수여야 합니다."),
                requiredRegion(node),
                requiredInt(node, "incomeBracket", MIN_INCOME_BRACKET, MAX_INCOME_BRACKET,
                        "소득분위는 1 이상 10 이하의 정수여야 합니다."),
                optionalCodes(node, "householdTypes", PolicyCodes.HOUSEHOLDS, "가구 형태"),
                optionalCodes(node, "specialConditions", PolicyCodes.SPECIALS, "특수조건"),
                optionalInterests(node)
        );
    }

    private static JsonNode objectBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("요청 본문이 올바른 JSON 객체여야 합니다.");
        }
    }

    private static int requiredInt(JsonNode node, String field, int min, int max, String message) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalArgumentException(message);
        }
        long raw = value.longValue();
        if (raw < min || raw > max) {
            throw new IllegalArgumentException(message);
        }
        return (int) raw;
    }

    private static String requiredRegion(JsonNode node) {
        JsonNode value = node.get("region");
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("거주지역은 1자 이상 100자 이하여야 합니다.");
        }
        String region = value.asText().trim();
        if (region.isEmpty() || region.length() > MAX_REGION_LENGTH) {
            throw new IllegalArgumentException("거주지역은 1자 이상 100자 이하여야 합니다.");
        }
        return region;
    }

    private static List<String> optionalCodes(JsonNode node, String field, Set<String> allowed, String label) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException(label + "은 문자열 배열이어야 합니다.");
        }
        List<String> codes = new ArrayList<>();
        value.forEach(item -> {
            if (item == null || !item.isTextual()) {
                throw new IllegalArgumentException(label + "은 문자열 배열이어야 합니다.");
            }
            codes.add(item.asText());
        });
        return PolicyCodes.normalize(codes, allowed, label);
    }

    private static List<String> optionalInterests(JsonNode node) {
        JsonNode value = node.get("interests");
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("관심 분야는 문자열 배열이어야 합니다.");
        }
        List<String> interests = new ArrayList<>();
        value.forEach(item -> {
            if (item == null || !item.isTextual()) {
                throw new IllegalArgumentException("관심 분야는 문자열 배열이어야 합니다.");
            }
            interests.add(item.asText());
        });
        return InterestLabels.normalize(interests);
    }
}
