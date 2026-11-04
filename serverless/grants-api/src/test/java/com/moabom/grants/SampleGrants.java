package com.moabom.grants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SampleGrants {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SampleGrants() {
    }

    static JsonNode readSeed() throws IOException {
        Path fromModule = Path.of("..", "seed", "sample-grants.json").normalize();
        Path fromRepositoryRoot = Path.of("serverless", "seed", "sample-grants.json").normalize();
        Path seed = Files.exists(fromModule) ? fromModule : fromRepositoryRoot;
        return MAPPER.readTree(seed.toFile());
    }

    static List<Grant> load() throws IOException {
        List<Grant> grants = new ArrayList<>();
        for (JsonNode item : readSeed()) {
            grants.add(new Grant(
                    item.get("grantId").asText(),
                    item.get("categoryTitle").asText(),
                    item.get("title").asText(),
                    item.get("description").asText(),
                    textOrNull(item.get("endDate")),
                    item.get("link").asText(),
                    new Eligibility(
                            item.get("targetAgeGroup").asText(),
                            item.get("minAge").asInt(),
                            item.get("maxAge").asInt(),
                            item.get("minIncomeBracket").asInt(),
                            item.get("maxIncomeBracket").asInt(),
                            item.get("targetRegion").asText()
                    ),
                    item.get("sample").asBoolean(),
                    keywords(item)
            ));
        }
        return grants;
    }

    private static List<String> keywords(JsonNode item) {
        JsonNode node = item.get("keywords");
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

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
