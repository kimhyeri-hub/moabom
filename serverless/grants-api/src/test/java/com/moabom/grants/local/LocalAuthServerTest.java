package com.moabom.grants.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moabom.grants.AccessTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAuthServerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocalApiServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void signupSeparatesUsersAndChatStaysLocal() throws Exception {
        server = LocalApiServer.start(0, new AccessTokens("unit-test-secret-not-for-deployment"));
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + server.port();

        JsonNode userA = json(client, base, "POST", "/auth/signup", account("javaUserA", "java-a@example.com"), null);
        JsonNode userB = json(client, base, "POST", "/auth/signup", account("javaUserB", "java-b@example.com"), null);
        assertEquals(201, userA.get("status").asInt());
        assertNotEquals(userA.get("body").get("user").get("id").asText(), userB.get("body").get("user").get("id").asText());

        HttpResponse<String> duplicate = send(client, base, "POST", "/auth/signup", account("javaUserA", "other@example.com"), null);
        assertEquals(409, duplicate.statusCode());
        HttpResponse<String> badPassword = send(client, base, "POST", "/auth/login",
                "{\"loginId\":\"javaUserA\",\"password\":\"wrong-pass-1\"}", null);
        assertEquals(401, badPassword.statusCode());

        String tokenA = userA.get("body").get("token").asText();
        String idA = userA.get("body").get("user").get("id").asText();
        String idB = userB.get("body").get("user").get("id").asText();
        assertEquals(201, send(client, base, "PUT", "/users/" + idA + "/profile", profile(25, "예시특별시", 3), tokenA).statusCode());
        assertEquals(201, send(client, base, "POST", "/users/" + idA + "/events",
                "{\"type\":\"SCRAP\",\"grantId\":\"policy-161300000099\"}", tokenA).statusCode());
        assertEquals(201, send(client, base, "PUT", "/users/" + idB + "/profile", profile(70, "예시도", 8), userB.get("body").get("token").asText()).statusCode());

        JsonNode recommendationsA = MAPPER.readTree(send(client, base, "GET", "/users/" + idA + "/recommendations", null, tokenA).body());
        JsonNode recommendationsB = MAPPER.readTree(send(client, base, "GET", "/users/" + idB + "/recommendations", null, userB.get("body").get("token").asText()).body());
        assertEquals("policy-161300000099", recommendationsA.get("recommendations").get(0).get("grant").get("grantId").asText());
        assertNotEquals(
                recommendationsA.get("recommendations").get(0).get("grant").get("grantId").asText(),
                recommendationsB.get("recommendations").get(0).get("grant").get("grantId").asText()
        );
        assertEquals(403, send(client, base, "GET", "/users/" + idB + "/profile", null, tokenA).statusCode());
        assertEquals(401, send(client, base, "GET", "/users/" + idA + "/profile", null, null).statusCode());
        assertEquals(200, send(client, base, "GET", "/grants", null, null).statusCode());

        assertEquals(200, send(client, base, "POST", "/auth/logout", "{}", tokenA).statusCode());
        assertEquals(401, send(client, base, "GET", "/auth/me", null, tokenA).statusCode());

        HttpResponse<String> options = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(base + "/auth/me"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(204, options.statusCode());
        assertTrue(options.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("PATCH"));

        HttpResponse<String> chat = send(client, base, "POST", "/chat", "{\"message\":\"청년 지원금\"}", null);
        assertEquals(200, chat.statusCode());
        assertTrue(chat.body().contains("OpenAI는 호출하지 않았습니다"));
    }

    @Test
    void profileUpdateStoresInterestsAndChangesRecommendations() throws Exception {
        server = LocalApiServer.start(0, new AccessTokens("unit-test-secret-not-for-deployment"));
        HttpClient client = HttpClient.newHttpClient();
        String base = "http://127.0.0.1:" + server.port();
        JsonNode created = json(client, base, "POST", "/auth/signup", account("interestUser", "interest@example.com"), null);
        assertEquals(201, created.get("status").asInt());
        String token = created.get("body").get("token").asText();
        String id = created.get("body").get("user").get("id").asText();

        HttpResponse<String> saved = send(client, base, "PUT", "/users/" + id + "/profile",
                "{\"age\":25,\"region\":\"서울특별시\",\"incomeBracket\":3,\"interests\":[\"주거\"]}", token);
        assertEquals(201, saved.statusCode());
        assertTrue(saved.body().contains("주거"));
        JsonNode loaded = json(client, base, "GET", "/users/" + id + "/profile", null, token);
        assertEquals(200, loaded.get("status").asInt());
        assertEquals("주거", loaded.get("body").get("interests").get(0).asText());
        JsonNode housing = MAPPER.readTree(send(client, base, "GET", "/users/" + id + "/recommendations", null, token).body());
        assertEquals("주거", housing.get("recommendations").get(0).get("grant").get("categoryTitle").asText());

        assertEquals(200, send(client, base, "PUT", "/users/" + id + "/profile",
                "{\"age\":25,\"region\":\"서울특별시\",\"incomeBracket\":3,\"householdTypes\":[\"SINGLE\"],\"interests\":[\"교육\"]}",
                token).statusCode());
        JsonNode education = MAPPER.readTree(send(client, base, "GET", "/users/" + id + "/recommendations", null, token).body());
        assertEquals("교육", education.get("recommendations").get(0).get("grant").get("categoryTitle").asText());

        HttpResponse<String> login = send(client, base, "POST", "/auth/login",
                "{\"loginId\":\"interestUser\",\"password\":\"Moabom-pass-1\"}", null);
        assertEquals(200, login.statusCode());
        HttpResponse<String> patched = send(client, base, "PATCH", "/auth/me",
                "{\"name\":\"수정회원\",\"email\":\"interest@example.com\",\"dateOfBirth\":\"20020304\",\"region\":\"서울특별시\",\"incomeBracket\":3}",
                token);
        assertEquals(200, patched.statusCode());
        assertTrue(patched.body().contains("수정회원"));
    }

    private static String account(String loginId, String email) {
        return "{\"name\":\"회원\",\"loginId\":\"" + loginId + "\",\"password\":\"Moabom-pass-1\",\"dateOfBirth\":\"20020304\",\"email\":\"" + email + "\"}";
    }

    private static String profile(int age, String region, int income) {
        return "{\"age\":" + age + ",\"region\":\"" + region + "\",\"incomeBracket\":" + income + "}";
    }

    private static JsonNode json(HttpClient client, String base, String method, String path, String body, String token) throws Exception {
        HttpResponse<String> response = send(client, base, method, path, body, token);
        return MAPPER.createObjectNode()
                .put("status", response.statusCode())
                .set("body", response.body().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(response.body()));
    }

    private static HttpResponse<String> send(HttpClient client, String base, String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
