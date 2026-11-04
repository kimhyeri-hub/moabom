package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommendHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsScoredRecommendationsForALocalUser() throws Exception {
        UserProfile user = TestGrants.user("example-user-api", 30, "예시특별시", 3);
        Grant grant = TestGrants.grant("example-api-grant", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        RecommendHandler handler = new RecommendHandler(
                new InMemoryGrantsRepository(List.of(grant)),
                new InMemoryUserProfileRepository(Map.of(user.userId(), user)),
                new InMemoryBehaviorRepository(List.of(
                        TestGrants.behavior(user.userId(), BehaviorType.VIEW, grant.grantId(), "2026-11-01T00:00:00Z")
                )),
                AuthRequests.TOKENS
        );

        var response = handler.handleRequest(request(user.userId()), null);
        JsonNode body = MAPPER.readTree(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals("authenticated-user-route", response.getHeaders().get("X-Moabom-Local-Dev"));
        assertTrue(body.get("localDevOnly").asBoolean());
        assertFalse(body.get("scoring").get("optimized").asBoolean());
        assertEquals("score = 0.6 * contentSimilarity + 0.4 * behaviorInterest", body.get("scoring").get("formula").asText());
        assertEquals(1, body.get("recommendations").size());
        assertTrue(body.get("recommendations").get(0).get("score").asDouble() > 0.0);
        assertTrue(body.get("recommendations").get(0).get("reasons").size() > 0);
        assertEquals(grant.grantId(), body.get("recommendations").get(0).get("grant").get("grantId").asText());
    }

    @Test
    void returns404WhenUserProfileDoesNotExist() throws Exception {
        RecommendHandler handler = new RecommendHandler(
                new InMemoryGrantsRepository(List.of()),
                new InMemoryUserProfileRepository(Map.of()),
                new InMemoryBehaviorRepository(List.of()),
                AuthRequests.TOKENS
        );

        var response = handler.handleRequest(request("example-missing-user"), null);

        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("사용자를 찾을 수 없습니다."));
    }

    private static APIGatewayProxyRequestEvent request(String id) {
        return new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", id))
                .withHeaders(Map.of("Authorization", AuthRequests.header(id)));
    }
}
