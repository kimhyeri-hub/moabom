package com.moabom.grants.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moabom.grants.Eligibility;
import com.moabom.grants.Grant;
import com.moabom.grants.RequirementClarity;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LocalCatalog {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocalCatalog() {
    }

    public static List<Grant> load() throws IOException {
        return read("local-grants.json");
    }

    public static List<Grant> loadOfficial() throws IOException {
        return read("official-grants.json");
    }

    public static List<Grant> loadAll() throws IOException {
        List<Grant> grants = new ArrayList<>();
        grants.addAll(load());
        grants.addAll(loadOfficial());
        return grants;
    }

    private static List<Grant> read(String resourceName) throws IOException {
        try (InputStream input = LocalCatalog.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException(resourceName + " was not found on the classpath");
            }
            List<Grant> grants = new ArrayList<>();
            for (JsonNode item : MAPPER.readTree(input)) {
                boolean sample = item.hasNonNull("sample") && item.get("sample").asBoolean();
                String referenceText = textOrNull(item.get("referenceText"));
                if (referenceText == null && sample) {
                    referenceText = "테스트용 가상 데이터";
                }
                grants.add(new Grant(
                        item.get("grantId").asText(),
                        recommendationText(item, "recommendationCategory", "categoryTitle"),
                        item.get("title").asText(),
                        item.get("description").asText(),
                        textOrNull(item.get("endDate")),
                        textOrNull(item.get("link")),
                        new Eligibility(
                                textOrNull(item.get("targetAgeGroup")),
                                intOrNull(item.get("minAge")),
                                intOrNull(item.get("maxAge")),
                                intOrNull(item.get("minIncomeBracket")),
                                intOrNull(item.get("maxIncomeBracket")),
                                textOrNull(item.get("targetRegion")),
                                clarity(item, "householdClarity"),
                                codes(item, "householdTypes"),
                                clarity(item, "specialClarity"),
                                codes(item, "specialConditions")
                        ),
                        sample,
                        recommendationKeywords(item),
                        textOrNull(item.get("sourceUrl")),
                        textOrNull(item.get("referenceDate")),
                        referenceText,
                        textOrNull(item.get("applicationStart")),
                        textOrNull(item.get("sourceServiceId"))
                ));
            }
            return grants;
        }
    }


    private static RequirementClarity clarity(JsonNode item, String name) {
        String value = textOrNull(item.get(name));
        if (value == null) {
            return RequirementClarity.UNKNOWN;
        }
        try {
            return RequirementClarity.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return RequirementClarity.UNKNOWN;
        }
    }

    private static java.util.List<String> codes(JsonNode item, String name) {
        JsonNode node = item.get(name);
        if (node == null || !node.isArray()) {
            return java.util.List.of();
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        node.forEach(code -> {
            if (code != null && !code.isNull() && !code.asText().isBlank()) {
                values.add(code.asText());
            }
        });
        return values;
    }

    private static String recommendationText(JsonNode item, String recommendationField, String fallbackField) {
        String recommendation = textOrNull(item.get(recommendationField));
        if (recommendation != null) {
            return recommendation;
        }
        return textOrNull(item.get(fallbackField));
    }

    private static List<String> recommendationKeywords(JsonNode item) {
        List<String> recommendation = keywords(item, "recommendationKeywords");
        if (!recommendation.isEmpty()) {
            return recommendation;
        }
        return keywords(item, "keywords");
    }

    private static List<String> keywords(JsonNode item, String name) {
        JsonNode node = item.get(name);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(keyword -> {
            if (keyword != null && !keyword.isNull() && !keyword.asText().isBlank()) {
                values.add(keyword.asText());
            }
        });
        return values;
    }

    private static Integer intOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (text == null || text.isBlank() || "null".equals(text)) {
            return null;
        }
        return node.asInt();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        if (text == null || text.isBlank() || "null".equals(text)) {
            return null;
        }
        return text;
    }
}
