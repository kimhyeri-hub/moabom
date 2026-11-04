package com.moabom.grants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Calls OpenAI only when {@link #complete} runs with a real key.
 * The local server does not use this class, and tests do not call complete with a key.
 */
public final class OpenAiChatClient implements ChatClient {
    static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String endpoint;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiChatClient(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT);
    }

    OpenAiChatClient(String apiKey, String endpoint) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
    }

    public static OpenAiChatClient fromEnvironment() {
        return new OpenAiChatClient(System.getenv("OPENAI_API_KEY"));
    }

    @Override
    public String complete(String systemPrompt, String userMessage) {
        if (apiKey.isBlank() || apiKey.startsWith("not-used")) {
            throw new IllegalStateException("OPENAI_API_KEY가 설정되지 않았습니다.");
        }
        ObjectNode request = mapper.createObjectNode();
        request.put("model", ChatPrompts.MODEL);
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(10000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            byte[] bytes = mapper.writeValueAsBytes(request);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (status >= 400 || stream == null) {
                throw new IllegalStateException("챗봇 응답을 받지 못했습니다.");
            }
            JsonNode response = mapper.readTree(stream);
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("챗봇 응답이 비어 있습니다.");
            }
            return content.asText();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("챗봇 응답을 받지 못했습니다.");
        }
    }
}
