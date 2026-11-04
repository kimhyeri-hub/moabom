package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteScrapFlowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_ID = "example-user-unscrap";

    private InMemoryBehaviorRepository behaviors;
    private PostBehaviorHandler postEvent;
    private DeleteScrapHandler deleteScrap;
    private ListBehaviorsHandler listEvents;
    private RecommendHandler recommend;

    @BeforeEach
    void setUp() {
        InMemoryGrantsRepository grants = new InMemoryGrantsRepository(List.of(
                TestGrants.grant("example-a-base", "주거", List.of("월세"), 19, 39, "예시특별시", 1, 6),
                TestGrants.grant("example-b-focus", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6)
        ));
        InMemoryUserProfileRepository profiles = new InMemoryUserProfileRepository(new HashMap<>());
        behaviors = new InMemoryBehaviorRepository(new ArrayList<>());
        AccessTokens tokens = AuthRequests.TOKENS;
        new PutProfileHandler(profiles, tokens).handleRequest(profileRequest(), null);
        postEvent = new PostBehaviorHandler(grants, profiles, behaviors, tokens);
        deleteScrap = new DeleteScrapHandler(profiles, behaviors, tokens);
        listEvents = new ListBehaviorsHandler(profiles, behaviors, tokens);
        recommend = new RecommendHandler(grants, profiles, behaviors, tokens);
    }

    @Test
    void cancellingScrapUpdatesRecommendationsAndKeepsView() throws Exception {
        assertEquals(List.of("example-a-base", "example-b-focus"), recommendationIds());

        postEvent.handleRequest(event("VIEW", "example-a-base"), null);
        postEvent.handleRequest(event("SCRAP", "example-b-focus"), null);
        assertEquals("example-b-focus", recommendationIds().get(0));

        var deleted = deleteScrap.handleRequest(event("SCRAP", "example-b-focus"), null);
        JsonNode deletedBody = MAPPER.readTree(deleted.getBody());
        assertEquals(200, deleted.getStatusCode());
        assertTrue(deletedBody.get("deleted").asBoolean());
        assertEquals("example-a-base", recommendationIds().get(0));

        JsonNode history = MAPPER.readTree(listEvents.handleRequest(path(null), null).getBody());
        assertEquals(1, history.get("behaviors").size());
        assertEquals("VIEW", history.get("behaviors").get(0).get("type").asText());
        assertEquals("example-a-base", history.get("behaviors").get(0).get("grantId").asText());

        var duplicate = deleteScrap.handleRequest(event("SCRAP", "example-b-focus"), null);
        assertEquals(200, duplicate.getStatusCode());
        assertFalse(MAPPER.readTree(duplicate.getBody()).get("deleted").asBoolean());
        assertEquals(1, behaviors.listByUser(USER_ID).size());
    }

    @Test
    void rejectsViewCancellation() throws Exception {
        postEvent.handleRequest(event("VIEW", "example-a-base"), null);

        var response = deleteScrap.handleRequest(event("VIEW", "example-a-base"), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("조회 기록은 유지"));
        assertEquals(1, behaviors.listByUser(USER_ID).size());
    }

    private List<String> recommendationIds() throws Exception {
        JsonNode body = MAPPER.readTree(recommend.handleRequest(path(null), null).getBody());
        List<String> ids = new ArrayList<>();
        body.get("recommendations").forEach(item -> ids.add(item.get("grant").get("grantId").asText()));
        return ids;
    }

    private static APIGatewayProxyRequestEvent profileRequest() {
        return path("{\"age\":30,\"region\":\"예시특별시\",\"incomeBracket\":3}");
    }

    private static APIGatewayProxyRequestEvent event(String type, String grantId) {
        return path("{\"type\":\"" + type + "\",\"grantId\":\"" + grantId + "\"}");
    }

    private static APIGatewayProxyRequestEvent path(String body) {
        return new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", USER_ID))
                .withHeaders(Map.of("Authorization", AuthRequests.header(USER_ID)))
                .withBody(body);
    }
}
