package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthFlowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DJANGO_HASH =
            "pbkdf2_sha256$260000$salt$hPvVEutECp0JjRhYqwibgqTefTo/DzKXnCroL0aoSXM=";

    private InMemoryAccountRepository accounts;
    private InMemoryTokenRevocationRepository revocations;
    private AccessTokens tokens;
    private AuthService auth;
    private SignupHandler signup;
    private LoginHandler login;
    private LogoutHandler logout;
    private MeHandler me;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        revocations = new InMemoryTokenRevocationRepository();
        tokens = new AccessTokens("unit-test-secret-not-for-deployment", revocations);
        auth = new AuthService(accounts, tokens, new PasswordHashes(4));
        signup = new SignupHandler(auth);
        login = new LoginHandler(auth);
        logout = new LogoutHandler(tokens);
        me = new MeHandler(auth, tokens);
    }

    @Test
    void signupLoginLogoutAndRejectedDuplicates() throws Exception {
        var created = signup.handleRequest(post("/auth/signup", accountJson("flowuserA", "a@example.com")), null);
        assertEquals(201, created.getStatusCode());
        JsonNode body = MAPPER.readTree(created.getBody());
        assertFalse(body.toString().contains("passwordHash"));
        assertFalse(body.toString().contains("Moabom-pass-1"));
        assertTrue(accounts.findByLoginId("flowuserA").orElseThrow().passwordHash().startsWith("$2a$"));
        String token = body.get("token").asText();
        String userId = body.get("user").get("id").asText();

        var duplicate = signup.handleRequest(post("/auth/signup", accountJson("flowuserA", "other@example.com")), null);
        assertEquals(409, duplicate.getStatusCode());

        var badPassword = login.handleRequest(post("/auth/login", "{\"loginId\":\"flowuserA\",\"password\":\"wrong-pass-1\"}"), null);
        assertEquals(401, badPassword.getStatusCode());

        var loggedIn = login.handleRequest(post("/auth/login", "{\"loginId\":\"flowuserA\",\"password\":\"Moabom-pass-1\"}"), null);
        assertEquals(200, loggedIn.getStatusCode());

        var meResponse = me.handleRequest(bearer("GET", token), null);
        assertEquals(200, meResponse.getStatusCode());
        assertEquals(userId, MAPPER.readTree(meResponse.getBody()).get("user").get("id").asText());
        assertEquals(401, me.handleRequest(new APIGatewayProxyRequestEvent().withHttpMethod("GET"), null).getStatusCode());

        assertEquals(200, logout.handleRequest(bearer("POST", token), null).getStatusCode());
        assertEquals(401, me.handleRequest(bearer("GET", token), null).getStatusCode());
    }

    @Test
    void importedDjangoHashLogsInAndBecomesBcrypt() throws Exception {
        accounts.insertPreservingId(new Account(
                "42", "legacyuser", "legacy@example.com", "기존회원", "2000-01-02",
                DJANGO_HASH, null, null
        ));
        var loggedIn = login.handleRequest(post("/auth/login", "{\"loginId\":\"legacyuser\",\"password\":\"secret\"}"), null);
        assertEquals(200, loggedIn.getStatusCode());
        Account upgraded = accounts.findByUserId("42").orElseThrow();
        assertTrue(upgraded.passwordHash().startsWith("$2a$"));
        assertNotEquals(DJANGO_HASH, upgraded.passwordHash());
        assertEquals("42", MAPPER.readTree(loggedIn.getBody()).get("user").get("id").asText());
    }

    @Test
    void otherUserAndMissingTokenStayForbidden() throws Exception {
        JsonNode first = MAPPER.readTree(signup.handleRequest(post("/auth/signup", accountJson("usera", "a@example.com")), null).getBody());
        JsonNode second = MAPPER.readTree(signup.handleRequest(post("/auth/signup", accountJson("userb", "b@example.com")), null).getBody());
        GetProfileHandler profiles = new GetProfileHandler(new InMemoryUserProfileRepository(new java.util.HashMap<>()), tokens);
        APIGatewayProxyRequestEvent cross = bearer("GET", first.get("token").asText())
                .withPathParameters(Map.of("id", second.get("user").get("id").asText()));
        assertEquals(403, profiles.handleRequest(cross, null).getStatusCode());
        APIGatewayProxyRequestEvent missing = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPathParameters(Map.of("id", first.get("user").get("id").asText()));
        assertEquals(401, profiles.handleRequest(missing, null).getStatusCode());
    }

    private static String accountJson(String loginId, String email) {
        return "{\"name\":\"회원\",\"loginId\":\"" + loginId + "\",\"password\":\"Moabom-pass-1\",\"dateOfBirth\":\"20020304\",\"email\":\"" + email + "\"}";
    }

    private static APIGatewayProxyRequestEvent post(String path, String body) {
        return new APIGatewayProxyRequestEvent().withHttpMethod("POST").withPath(path).withBody(body);
    }

    private static APIGatewayProxyRequestEvent bearer(String method, String token) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withHeaders(Map.of("Authorization", "Bearer " + token));
    }
}
