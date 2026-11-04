package com.moabom.grants.local;

import com.moabom.grants.AccessTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalApiServerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_ID = "local-user";
    private static final AccessTokens TOKENS = new AccessTokens("unit-test-secret-not-for-deployment");

    private LocalApiServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        server = LocalApiServer.start(0, TOKENS);
        client = HttpClient.newHttpClient();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void profileViewScrapAndCancelChangeRecommendations() throws Exception {
        assertEquals(8, LocalCatalog.load().size());
        assertTrue(LocalCatalog.load().stream().allMatch(com.moabom.grants.Grant::sample));
        assertEquals(12, LocalCatalog.loadOfficial().size());

        HttpResponse<String> created = send("PUT", "/users/local-user/profile",
                "{\"age\":25,\"region\":\"서울특별시\",\"incomeBracket\":3}");
        assertEquals(201, created.statusCode());

        JsonNode grants = MAPPER.readTree(send("GET", "/grants", null).body());
        assertEquals(12, grants.get("grants").size());
        grants.get("grants").forEach(grant -> {
            assertFalse(grant.get("sample").asBoolean());
            assertFalse(grant.get("title").asText().startsWith("[예시]"));
        });
        assertEquals(404, send("GET", "/grants/example-001-youth-transport", null).statusCode());

        JsonNode anyang = MAPPER.readTree(send("GET", "/grants/policy-383000000209", null).body());
        assertEquals("안양시", anyang.get("eligibility").get("targetRegion").asText());
        assertTrue(anyang.get("eligibility").get("minIncomeBracket").isNull());

        assertEquals("policy-1309680-1141", recommendationIds().get(0));
        assertEquals(5, recommendationIds().size());
        assertFalse(recommendationIds().contains("policy-383000000209"));
        assertFalse(recommendationIds().contains("policy-537000001436"));
        assertFalse(recommendationIds().contains("policy-650000001119"));
        assertFalse(recommendationIds().contains("example-001-youth-transport"));
        JsonNode firstRecommendation = MAPPER.readTree(
                send("GET", "/users/local-user/recommendations", null).body()).get("recommendations").get(0);
        assertEquals("CHECK_REQUIRED", firstRecommendation.get("eligibilityStatus").asText());
        assertTrue(firstRecommendation.get("reasons").size() > 0);

        HttpResponse<String> scrap = send("POST", "/users/local-user/events",
                "{\"type\":\"SCRAP\",\"grantId\":\"policy-161300000099\"}");
        assertEquals(201, scrap.statusCode());
        assertEquals("policy-161300000099", recommendationIds().get(0));

        HttpResponse<String> cancelled = send("DELETE", "/users/local-user/events",
                "{\"type\":\"SCRAP\",\"grantId\":\"policy-161300000099\"}");
        assertEquals(200, cancelled.statusCode());
        assertTrue(MAPPER.readTree(cancelled.body()).get("deleted").asBoolean());
        assertEquals("policy-1309680-1141", recommendationIds().get(0));

        HttpResponse<String> duplicate = send("DELETE", "/users/local-user/events",
                "{\"type\":\"SCRAP\",\"grantId\":\"policy-161300000099\"}");
        assertFalse(MAPPER.readTree(duplicate.body()).get("deleted").asBoolean());

        send("POST", "/users/local-user/events", "{\"type\":\"VIEW\",\"grantId\":\"policy-161300000099\"}");
        send("DELETE", "/users/local-user/events", "{\"type\":\"SCRAP\",\"grantId\":\"policy-161300000099\"}");
        JsonNode history = MAPPER.readTree(send("GET", "/users/local-user/behaviors", null).body());
        assertEquals(1, history.get("behaviors").size());
        assertEquals("VIEW", history.get("behaviors").get(0).get("type").asText());

        send("PUT", "/users/local-user/profile", "{\"age\":70,\"region\":\"서울특별시\",\"incomeBracket\":3}");
        JsonNode aged = MAPPER.readTree(send("GET", "/users/local-user/recommendations", null).body());
        assertFalse(recommendationIds().contains("policy-161300000099"));
        assertEquals("policy-336000000096", aged.get("recommendations").get(0).get("grant").get("grantId").asText());
        assertTrue(aged.get("recommendations").get(0).get("contentSimilarity").asDouble() > 0.0);
        assertEquals("CHECK_REQUIRED", aged.get("recommendations").get(0).get("eligibilityStatus").asText());
    }

    private List<String> recommendationIds() throws Exception {
        JsonNode body = MAPPER.readTree(send("GET", "/users/local-user/recommendations", null).body());
        List<String> ids = new ArrayList<>();
        body.get("recommendations").forEach(item -> ids.add(item.get("grant").get("grantId").asText()));
        return ids;
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (path.startsWith("/users/")) {
            request.header("Authorization", "Bearer " + TOKENS.sign(USER_ID, java.time.Instant.now().getEpochSecond() + 3600));
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.method(method, HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
