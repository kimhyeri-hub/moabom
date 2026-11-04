package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetGrantHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsGrantUsingTheSameShapeAsTheListItem() throws Exception {
        Grant grant = TestGrants.grant(
                "example-detail", "교통", List.of("버스"), 19, 34, "예시특별시", 1, 5);
        GetGrantHandler handler = new GetGrantHandler(new InMemoryGrantsRepository(java.util.List.of(grant)));

        var response = handler.handleRequest(request("example-detail"), null);
        JsonNode body = MAPPER.readTree(response.getBody());

        assertEquals(200, response.getStatusCode());
        assertEquals("example-detail", body.get("grantId").asText());
        assertEquals("예시특별시", body.get("eligibility").get("targetRegion").asText());
        assertEquals(19, body.get("eligibility").get("minAge").asInt());
        assertEquals("버스", body.get("keywords").get(0).asText());
        assertFalse(body.get("sample").asBoolean());
        assertFalse(body.has("grants"));
    }

    @Test
    void hidesSampleGrantFromThePublicDetail() throws Exception {
        Grant sample = new Grant(
                "example-hidden",
                "교통",
                "[예시] 숨김",
                "실제 정책이 아닙니다.",
                null,
                "https://example.com/moabom/grants/example-hidden",
                new Eligibility("예시", 19, 34, 1, 5, "예시특별시"),
                true,
                List.of("버스")
        );
        GetGrantHandler handler = new GetGrantHandler(new InMemoryGrantsRepository(List.of(sample)));

        var response = handler.handleRequest(request("example-hidden"), null);

        assertEquals(404, response.getStatusCode());
        assertEquals("지원금을 찾을 수 없습니다.", MAPPER.readTree(response.getBody()).get("message").asText());
    }

    @Test
    void returns404WhenGrantIsMissing() throws Exception {
        GetGrantHandler handler = new GetGrantHandler(new InMemoryGrantsRepository(java.util.List.of()));

        var response = handler.handleRequest(request("example-missing"), null);
        JsonNode body = MAPPER.readTree(response.getBody());

        assertEquals(404, response.getStatusCode());
        assertEquals("지원금을 찾을 수 없습니다.", body.get("message").asText());
    }

    @Test
    void rejectsMalformedGrantId() throws Exception {
        GetGrantHandler handler = new GetGrantHandler(new InMemoryGrantsRepository(java.util.List.of()));

        var response = handler.handleRequest(request("../secret"), null);

        assertEquals(400, response.getStatusCode());
        assertFalse(response.getBody().contains("secret"));
    }

    private static APIGatewayProxyRequestEvent request(String id) {
        return new APIGatewayProxyRequestEvent().withPathParameters(Map.of("id", id));
    }
}
