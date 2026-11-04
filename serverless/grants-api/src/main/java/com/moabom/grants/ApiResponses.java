package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

final class ApiResponses {
    static final Pattern ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9-]{0,79}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiResponses() {
    }

    static APIGatewayProxyResponseEvent ok(Object body) {
        return json(200, body, false);
    }

    static APIGatewayProxyResponseEvent okLocalDev(Object body) {
        return json(200, body, true);
    }

    static APIGatewayProxyResponseEvent localDev(int statusCode, Object body) {
        return json(statusCode, body, true);
    }

    static APIGatewayProxyResponseEvent error(int statusCode, String message, boolean localDev) {
        return json(statusCode, Map.of("message", message), localDev);
    }

    static String pathId(APIGatewayProxyRequestEvent event) {
        if (event == null || event.getPathParameters() == null) {
            return null;
        }
        return event.getPathParameters().get("id");
    }

    private static APIGatewayProxyResponseEvent json(int statusCode, Object body, boolean localDev) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers(localDev))
                    .withBody(MAPPER.writeValueAsString(body))
                    .withIsBase64Encoded(false);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("JSON 응답을 만들지 못했습니다.", ex);
        }
    }

    private static Map<String, String> headers(boolean localDev) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Access-Control-Allow-Origin", "*");
        if (localDev) {
            headers.put("X-Moabom-Local-Dev", "authenticated-user-route");
        }
        return headers;
    }
}
