package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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

class StoredRecommendationFlowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_ID = "example-user-flow";

    private InMemoryGrantsRepository grants;
    private InMemoryUserProfileRepository profiles;
    private InMemoryBehaviorRepository behaviors;
    private PutProfileHandler putProfile;
    private GetProfileHandler getProfile;
    private PostBehaviorHandler postEvent;
    private ListBehaviorsHandler listEvents;
    private RecommendHandler recommend;

    @BeforeEach
    void setUp() {
        grants = new InMemoryGrantsRepository(List.of(
                TestGrants.grant("example-a-base", "주거", List.of("월세"), 19, 39, "예시특별시", 1, 6),
                TestGrants.grant("example-b-focus", "교통", List.of("버스"), 19, 39, "예시특별시", 1, 6),
                TestGrants.grant("example-c-senior", "생활", List.of("장년"), 60, 80, "예시특별시", 1, 6)
        ));
        profiles = new InMemoryUserProfileRepository(new HashMap<>());
        behaviors = new InMemoryBehaviorRepository(new ArrayList<>());
        AccessTokens tokens = AuthRequests.TOKENS;
        putProfile = new PutProfileHandler(profiles, tokens);
        getProfile = new GetProfileHandler(profiles, tokens);
        postEvent = new PostBehaviorHandler(grants, profiles, behaviors, tokens);
        listEvents = new ListBehaviorsHandler(profiles, behaviors, tokens);
        recommend = new RecommendHandler(grants, profiles, behaviors, tokens);
    }

    @Test
    void savesProfileAndReadsItBack() throws Exception {
        APIGatewayProxyResponseEvent created = putProfile.handleRequest(profileRequest(30), null);
        APIGatewayProxyResponseEvent loaded = getProfile.handleRequest(pathRequest(null), null);

        assertEquals(201, created.getStatusCode());
        assertEquals(200, loaded.getStatusCode());
        JsonNode body = MAPPER.readTree(loaded.getBody());
        assertEquals(30, body.get("age").asInt());
        assertEquals("예시특별시", body.get("region").asText());
        assertEquals(3, body.get("incomeBracket").asInt());
        assertTrue(body.get("localDevOnly").asBoolean());

        APIGatewayProxyResponseEvent updated = putProfile.handleRequest(profileRequest(31), null);
        JsonNode updatedBody = MAPPER.readTree(getProfile.handleRequest(pathRequest(null), null).getBody());
        assertEquals(200, updated.getStatusCode());
        assertFalse(MAPPER.readTree(updated.getBody()).get("created").asBoolean());
        assertEquals(31, updatedBody.get("age").asInt());
    }

    @Test
    void savesBehaviorAndDoesNotStoreTheSameActionTwice() throws Exception {
        putProfile.handleRequest(profileRequest(30), null);

        APIGatewayProxyResponseEvent created = postEvent.handleRequest(eventRequest("VIEW", "example-b-focus"), null);
        APIGatewayProxyResponseEvent duplicate = postEvent.handleRequest(eventRequest("VIEW", "example-b-focus"), null);
        JsonNode history = MAPPER.readTree(listEvents.handleRequest(pathRequest(null), null).getBody());

        assertEquals(201, created.getStatusCode());
        assertFalse(MAPPER.readTree(created.getBody()).get("duplicate").asBoolean());
        assertEquals(200, duplicate.getStatusCode());
        assertTrue(MAPPER.readTree(duplicate.getBody()).get("duplicate").asBoolean());
        assertEquals(
                MAPPER.readTree(created.getBody()).get("occurredAt").asText(),
                MAPPER.readTree(duplicate.getBody()).get("occurredAt").asText()
        );
        assertEquals(1, history.get("behaviors").size());
        assertEquals("VIEW", history.get("behaviors").get(0).get("type").asText());
        assertEquals("example-b-focus", history.get("behaviors").get(0).get("grantId").asText());
    }

    @Test
    void storedViewAndScrapChangeRecommendationOrder() throws Exception {
        putProfile.handleRequest(profileRequest(30), null);

        assertEquals(List.of("example-a-base", "example-b-focus"), recommendationIds());

        postEvent.handleRequest(eventRequest("VIEW", "example-b-focus"), null);
        assertEquals(List.of("example-b-focus", "example-a-base"), recommendationIds());

        postEvent.handleRequest(eventRequest("SCRAP", "example-a-base"), null);
        List<String> afterScrap = recommendationIds();
        assertEquals("example-a-base", afterScrap.get(0));
        assertTrue(afterScrap.indexOf("example-a-base") < afterScrap.indexOf("example-b-focus"));
        assertFalse(afterScrap.contains("example-c-senior"));
    }

    @Test
    void storedProfileExcludesIneligibleGrants() throws Exception {
        putProfile.handleRequest(profileRequest(70), null);

        assertEquals(List.of("example-c-senior"), recommendationIds());
    }

    @Test
    void rejectsInvalidInputAndMissingGrant() throws Exception {
        APIGatewayProxyResponseEvent badAge = putProfile.handleRequest(
                pathRequest("{\"age\":121,\"region\":\"예시특별시\",\"incomeBracket\":3}"), null);
        APIGatewayProxyResponseEvent missingProfile = postEvent.handleRequest(
                eventRequest("VIEW", "example-a-base"), null);
        putProfile.handleRequest(profileRequest(30), null);
        APIGatewayProxyResponseEvent missingGrant = postEvent.handleRequest(
                eventRequest("SCRAP", "example-missing-grant"), null);
        APIGatewayProxyResponseEvent badType = postEvent.handleRequest(
                eventRequest("CLICK", "example-a-base"), null);

        assertEquals(400, badAge.getStatusCode());
        assertTrue(badAge.getBody().contains("연령"));
        assertEquals(404, missingProfile.getStatusCode());
        assertEquals(404, missingGrant.getStatusCode());
        assertTrue(missingGrant.getBody().contains("지원금을 찾을 수 없습니다."));
        assertEquals(400, badType.getStatusCode());
        assertFalse(badType.getBody().contains("CLICK"));
    }

    private List<String> recommendationIds() throws Exception {
        JsonNode body = MAPPER.readTree(recommend.handleRequest(pathRequest(null), null).getBody());
        List<String> ids = new ArrayList<>();
        body.get("recommendations").forEach(item -> ids.add(item.get("grant").get("grantId").asText()));
        return ids;
    }

    private static APIGatewayProxyRequestEvent profileRequest(int age) {
        return pathRequest("{\"age\":" + age + ",\"region\":\"예시특별시\",\"incomeBracket\":3}");
    }

    private static APIGatewayProxyRequestEvent eventRequest(String type, String grantId) {
        return pathRequest("{\"type\":\"" + type + "\",\"grantId\":\"" + grantId + "\"}");
    }

    private static APIGatewayProxyRequestEvent pathRequest(String body) {
        return new APIGatewayProxyRequestEvent()
                .withPathParameters(Map.of("id", USER_ID))
                .withHeaders(Map.of("Authorization", AuthRequests.header(USER_ID)))
                .withBody(body);
    }
}
