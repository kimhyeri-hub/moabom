package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ListGrantsHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{0,79}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GrantsRepository grants;

    public ListGrantsHandler() {
        this(DynamoGrantsRepository.fromEnvironment());
    }

    public ListGrantsHandler(GrantsRepository grants) {
        this.grants = grants;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            GrantPage page = publicPage(parseLimit(event), parseNextToken(event));
            return json(200, responseBody(page));
        } catch (IllegalArgumentException ex) {
            return json(400, Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            logFailure(context);
            return json(500, Map.of("message", "지원금 목록을 조회하지 못했습니다."));
        }
    }

    private GrantPage publicPage(int limit, String exclusiveStartGrantId) {
        List<Grant> visible = grants.listAllGrants().stream()
                .filter(grant -> grant != null && !grant.sample() && grant.grantId() != null)
                .toList();
        List<Grant> remaining = visible.stream()
                .filter(grant -> exclusiveStartGrantId == null
                        || grant.grantId().compareTo(exclusiveStartGrantId) > 0)
                .toList();
        List<Grant> page = remaining.stream().limit(limit).toList();
        String nextToken = remaining.size() > page.size() && !page.isEmpty()
                ? page.get(page.size() - 1).grantId()
                : null;
        return new GrantPage(page, nextToken);
    }

    private static int parseLimit(APIGatewayProxyRequestEvent event) {
        String raw = queryParam(event, "limit");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(raw);
            if (limit < 1 || limit > MAX_LIMIT) {
                throw new IllegalArgumentException("limit은 1 이상 100 이하의 정수여야 합니다.");
            }
            return limit;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("limit은 1 이상 100 이하의 정수여야 합니다.");
        }
    }

    private static String parseNextToken(APIGatewayProxyRequestEvent event) {
        String token = queryParam(event, "nextToken");
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("nextToken 형식이 올바르지 않습니다.");
        }
        return token;
    }

    private static String queryParam(APIGatewayProxyRequestEvent event, String name) {
        if (event == null || event.getQueryStringParameters() == null) {
            return null;
        }
        return event.getQueryStringParameters().get(name);
    }

    private static Map<String, Object> responseBody(GrantPage page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grants", page.grants());
        if (page.nextToken() != null) {
            body.put("nextToken", page.nextToken());
        }
        return body;
    }

    private static APIGatewayProxyResponseEvent json(int statusCode, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers())
                    .withBody(MAPPER.writeValueAsString(body))
                    .withIsBase64Encoded(false);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 응답을 만들지 못했습니다.", ex);
        }
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Access-Control-Allow-Origin", "*");
        return headers;
    }

    private static void logFailure(Context context) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log("GET /grants failed");
        }
    }
}
