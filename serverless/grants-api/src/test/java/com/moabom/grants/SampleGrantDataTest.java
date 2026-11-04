package com.moabom.grants;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleGrantDataTest {
    @Test
    void seedContainsOnlyFictionalGrantsWithRecommendationFields() throws IOException {
        JsonNode grants = SampleGrants.readSeed();
        assertEquals(3, grants.size());

        Set<String> ids = new HashSet<>();
        for (JsonNode grant : grants) {
            ids.add(grant.get("grantId").asText());
            assertEquals("GRANT", grant.get("entityType").asText());
            assertEquals("예시 카테고리", grant.get("categoryTitle").asText());
            assertTrue(grant.get("title").asText().startsWith("[예시]"));
            assertTrue(grant.get("description").asText().contains("실제 정책이 아닙니다"));
            assertTrue(grant.get("link").asText().startsWith("https://example.com/"));
            assertTrue(grant.get("sample").asBoolean());
            assertTrue(grant.get("targetAgeGroup").asText().length() > 0);
            assertTrue(grant.get("minAge").asInt() <= grant.get("maxAge").asInt());
            assertTrue(grant.get("minIncomeBracket").asInt() >= 1);
            assertTrue(grant.get("maxIncomeBracket").asInt() >= grant.get("minIncomeBracket").asInt());
            assertTrue(grant.get("targetRegion").asText().contains("예시"));
        }
        assertEquals(3, ids.size());
        assertTrue(grants.get(2).get("endDate").isNull());
    }
}
