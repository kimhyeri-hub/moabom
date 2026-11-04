package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHandlerTest {
    @Test
    void usesTheExistingPromptAndDoesNotCallOpenAi() throws Exception {
        AtomicReference<String> seenPrompt = new AtomicReference<>();
        AtomicReference<String> seenMessage = new AtomicReference<>();
        ChatHandler handler = new ChatHandler((systemPrompt, userMessage) -> {
            seenPrompt.set(systemPrompt);
            seenMessage.set(userMessage);
            return "모의 응답";
        });
        var response = handler.handleRequest(new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{\"message\":\"청년 교통비 알려줘\"}"), null);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("모의 응답"));
        assertEquals(ChatPrompts.SYSTEM, seenPrompt.get());
        assertEquals("청년 교통비 알려줘", seenMessage.get());
        assertEquals("gpt-4o-mini", ChatPrompts.MODEL);
        assertTrue(ChatPrompts.SYSTEM.contains("모아봄 챗봇"));
    }

    @Test
    void blankKeyFailsBeforeAnyNetworkCall() {
        OpenAiChatClient client = new OpenAiChatClient("", "http://127.0.0.1:9/should-not-be-called");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.complete(ChatPrompts.SYSTEM, "안녕"));
        assertTrue(failure.getMessage().contains("OPENAI_API_KEY"));
    }

    @Test
    void emptyMessageIsRejected() {
        ChatHandler handler = new ChatHandler((systemPrompt, userMessage) -> {
            throw new IllegalStateException("mock should not run");
        });
        var response = handler.handleRequest(new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("{\"message\":\"  \"}"), null);
        assertEquals(400, response.getStatusCode());
    }
}
