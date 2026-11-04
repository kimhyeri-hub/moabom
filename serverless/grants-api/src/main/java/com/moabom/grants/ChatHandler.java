package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ChatHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatClient client;

    public ChatHandler() {
        this(OpenAiChatClient.fromEnvironment());
    }

    public ChatHandler(ChatClient client) {
        this.client = client;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (event == null || !"POST".equals(event.getHttpMethod())) {
            return ApiResponses.error(405, "POST 메서드로 요청해 주세요.", false);
        }
        try {
            JsonNode payload = MAPPER.readTree(event.getBody() == null ? "" : event.getBody());
            String message = payload.path("message").asText("").trim();
            if (message.isBlank()) {
                return ApiResponses.error(400, "메시지를 입력해 주세요.", false);
            }
            String reply = client.complete(ChatPrompts.SYSTEM, message);
            return ApiResponses.ok(Map.of("reply", reply));
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(400, "JSON 본문을 확인해 주세요.", false);
        } catch (RuntimeException ex) {
            return ApiResponses.error(500, "챗봇 응답을 만들지 못했습니다.", false);
        } catch (Exception ex) {
            return ApiResponses.error(400, "JSON 본문을 확인해 주세요.", false);
        }
    }
}
