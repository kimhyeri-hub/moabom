package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccessTest {
    @Test
    void missingOrInvalidTokenReturns401() {
        GetProfileHandler handler = new GetProfileHandler(new InMemoryUserProfileRepository(Map.of()), AuthRequests.TOKENS);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent().withPathParameters(Map.of("id", "user-a"));

        assertEquals(401, handler.handleRequest(request, null).getStatusCode());

        APIGatewayProxyRequestEvent forged = request.withHeaders(Map.of("Authorization", "Bearer not-a-token"));
        assertEquals(401, handler.handleRequest(forged, null).getStatusCode());
    }

    @Test
    void differentUserIdReturns403() {
        GetProfileHandler handler = new GetProfileHandler(new InMemoryUserProfileRepository(Map.of()), AuthRequests.TOKENS);
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", "user-b"))
                .withHeaders(Map.of("Authorization", AuthRequests.header("user-a")));

        var response = handler.handleRequest(request, null);

        assertEquals(403, response.getStatusCode());
        assertTrue(response.getBody().contains("인증된 사용자가 다릅니다"));
    }

    @Test
    void behaviorAndRecommendationsStayWithTheAuthenticatedUser() throws Exception {
        Grant shared = TestGrants.grant("example-shared", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6);
        UserProfile first = TestGrants.user("user-a", 25, "예시특별시", 3);
        UserProfile second = TestGrants.user("user-b", 70, "예시도", 9);
        InMemoryGrantsRepository grants = new InMemoryGrantsRepository(List.of(shared));
        InMemoryUserProfileRepository profiles = new InMemoryUserProfileRepository(Map.of(
                first.userId(), first,
                second.userId(), second
        ));
        InMemoryBehaviorRepository behaviors = new InMemoryBehaviorRepository(List.of(
                TestGrants.behavior(first.userId(), BehaviorType.SCRAP, shared.grantId(), "2026-11-01T00:00:00Z")
        ));
        RecommendHandler recommend = new RecommendHandler(grants, profiles, behaviors, AuthRequests.TOKENS);
        ListBehaviorsHandler history = new ListBehaviorsHandler(profiles, behaviors, AuthRequests.TOKENS);

        var firstHistory = history.handleRequest(request(first.userId()), null);
        var secondHistory = history.handleRequest(request(second.userId()), null);
        var firstScore = recommend.handleRequest(request(first.userId()), null);
        var secondScore = recommend.handleRequest(request(second.userId()), null);

        assertEquals(200, firstHistory.getStatusCode());
        assertTrue(firstHistory.getBody().contains("SCRAP"));
        assertEquals(200, secondHistory.getStatusCode());
        assertTrue(secondHistory.getBody().contains("\"behaviors\":[]") || !secondHistory.getBody().contains("SCRAP"));
        assertNotEquals(firstScore.getBody(), secondScore.getBody());
    }

    private static APIGatewayProxyRequestEvent request(String userId) {
        return new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", userId))
                .withHeaders(Map.of("Authorization", AuthRequests.header(userId)));
    }
}
