package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListBehaviorsHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsBehaviorHistoryInTimeOrder() throws Exception {
        UserProfile user = TestGrants.user("example-user-history", 30, "예시특별시", 3);
        ListBehaviorsHandler handler = new ListBehaviorsHandler(
                new InMemoryUserProfileRepository(Map.of(user.userId(), user)),
                new InMemoryBehaviorRepository(List.of(
                        TestGrants.behavior(user.userId(), BehaviorType.SCRAP, "example-b", "2026-11-02T00:00:00Z"),
                        TestGrants.behavior(user.userId(), BehaviorType.VIEW, "example-a", "2026-11-01T00:00:00Z")
                )),
                AuthRequests.TOKENS
        );

        var response = handler.handleRequest(
                new APIGatewayProxyRequestEvent()
                        .withPathParameters(Map.of("id", user.userId()))
                        .withHeaders(Map.of("Authorization", AuthRequests.header(user.userId()))),
                null
        );
        JsonNode body = MAPPER.readTree(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals(true, body.get("localDevOnly").asBoolean());
        assertEquals("VIEW", body.get("behaviors").get(0).get("type").asText());
        assertEquals("example-a", body.get("behaviors").get(0).get("grantId").asText());
        assertEquals("SCRAP", body.get("behaviors").get(1).get("type").asText());
    }
}
