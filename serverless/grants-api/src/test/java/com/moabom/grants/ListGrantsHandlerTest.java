package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListGrantsHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void omitsSampleGrantsFromThePublicList() throws Exception {
        ListGrantsHandler handler = handlerWithSeed();

        JsonNode body = body(handler.handleRequest(new APIGatewayProxyRequestEvent(), null));

        assertEquals(200, handler.handleRequest(new APIGatewayProxyRequestEvent(), null).getStatusCode());
        assertEquals(0, body.get("grants").size());
        assertEquals(3, SampleGrants.load().size());
        assertTrue(SampleGrants.load().stream().allMatch(Grant::sample));
    }

    @Test
    void returnsPublicGrantEligibilityAndPaginates() throws Exception {
        ListGrantsHandler handler = new ListGrantsHandler(new InMemoryGrantsRepository(java.util.List.of(
                published("policy-a", "2026-12-31"),
                published("policy-b", "2026-12-31"),
                published("policy-c", null),
                sampleGrant()
        )));
        APIGatewayProxyRequestEvent firstRequest = new APIGatewayProxyRequestEvent()
                .withQueryStringParameters(Map.of("limit", "2"));

        var firstResponse = handler.handleRequest(firstRequest, null);
        JsonNode firstBody = body(firstResponse);
        assertEquals(200, firstResponse.getStatusCode());
        assertEquals(2, firstBody.get("grants").size());
        assertFalse(firstBody.get("grants").get(0).get("sample").asBoolean());
        assertEquals(19, firstBody.get("grants").get(0).get("eligibility").get("minAge").asInt());
        assertEquals("예시특별시", firstBody.get("grants").get(0).get("eligibility").get("targetRegion").asText());
        assertEquals(1, firstBody.get("grants").get(0).get("eligibility").get("minIncomeBracket").asInt());
        assertEquals(5, firstBody.get("grants").get(0).get("eligibility").get("maxIncomeBracket").asInt());
        String nextToken = firstBody.get("nextToken").asText();
        assertEquals("policy-b", nextToken);

        APIGatewayProxyRequestEvent secondRequest = new APIGatewayProxyRequestEvent()
                .withQueryStringParameters(Map.of("limit", "2", "nextToken", nextToken));
        JsonNode secondBody = body(handler.handleRequest(secondRequest, null));
        assertEquals(1, secondBody.get("grants").size());
        assertEquals("policy-c", secondBody.get("grants").get(0).get("grantId").asText());
        assertFalse(secondBody.has("nextToken"));
        assertTrue(secondBody.get("grants").get(0).get("endDate").isNull());
    }

    @Test
    void rejectsInvalidLimitAndToken() {
        ListGrantsHandler handler = new ListGrantsHandler(new InMemoryGrantsRepository(java.util.List.of()));

        var badLimit = handler.handleRequest(request("limit", "0"), null);
        assertEquals(400, badLimit.getStatusCode());
        assertTrue(badLimit.getBody().contains("limit"));

        var badToken = handler.handleRequest(request("nextToken", "../secret"), null);
        assertEquals(400, badToken.getStatusCode());
        assertFalse(badToken.getBody().contains("secret"));
    }

    @Test
    void hidesRepositoryFailures() {
        ListGrantsHandler handler = new ListGrantsHandler(new GrantsRepository() {
            @Override
            public GrantPage listGrants(int limit, String exclusiveStartGrantId) {
                throw new IllegalStateException("table arn and credential details");
            }

            @Override
            public java.util.Optional<Grant> getGrant(String grantId) {
                return java.util.Optional.empty();
            }
        });

        var response = handler.handleRequest(new APIGatewayProxyRequestEvent(), null);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("지원금 목록을 조회하지 못했습니다."));
        assertFalse(response.getBody().contains("credential"));
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
    }

    private static Grant published(String id, String endDate) {
        return new Grant(
                id,
                "교통",
                id,
                "공개 테스트 행",
                endDate,
                "https://example.com/moabom/grants/" + id,
                new Eligibility("예시", 19, 34, 1, 5, "예시특별시"),
                false,
                java.util.List.of("버스")
        );
    }

    private static Grant sampleGrant() {
        return new Grant(
                "example-hidden",
                "교통",
                "[예시] 숨김",
                "실제 정책이 아닙니다.",
                null,
                "https://example.com/moabom/grants/example-hidden",
                new Eligibility("예시", 19, 34, 1, 5, "예시특별시"),
                true,
                java.util.List.of("버스")
        );
    }

    private static ListGrantsHandler handlerWithSeed() throws IOException {
        return new ListGrantsHandler(new InMemoryGrantsRepository(SampleGrants.load()));
    }

    private static APIGatewayProxyRequestEvent request(String name, String value) {
        return new APIGatewayProxyRequestEvent().withQueryStringParameters(Map.of(name, value));
    }

    private static JsonNode body(com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent response)
            throws IOException {
        return MAPPER.readTree(response.getBody());
    }
}
