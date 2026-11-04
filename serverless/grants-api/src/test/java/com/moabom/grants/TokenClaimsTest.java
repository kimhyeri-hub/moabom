package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenClaimsTest {
    private static final String SECRET = "unit-test-secret-not-for-deployment";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private final AccessTokens tokens = new AccessTokens(SECRET, new InMemoryTokenRevocationRepository());

    @Test
    void rejectsExpiredWrongIssuerWrongAudienceAndOtherAlgorithms() {
        assertEquals(401, tokens.errorIfNoSession(bearer(tokens.sign("7", Instant.now().getEpochSecond() - 30))).getStatusCode());
        assertEquals(401, tokens.errorIfNoSession(bearer(crafted("{\"alg\":\"none\",\"typ\":\"JWT\"}", claims("7")))).getStatusCode());
        String wrongIssuer = claims("7").replace("\"iss\":\"moabom-auth\"", "\"iss\":\"other\"");
        assertEquals(401, tokens.errorIfNoSession(bearer(crafted("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", wrongIssuer))).getStatusCode());
        String wrongAudience = claims("7").replace("\"aud\":\"moabom-api\"", "\"aud\":\"other-api\"");
        assertEquals(401, tokens.errorIfNoSession(bearer(crafted("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", wrongAudience))).getStatusCode());
        assertEquals(null, tokens.errorIfNoSession(bearer(tokens.sign("7", Instant.now().getEpochSecond() + 600))));
    }

    @Test
    void revokedTokenCannotBeReused() throws Exception {
        String token = tokens.sign("7", Instant.now().getEpochSecond() + 600);
        String[] parts = token.split("\\.");
        String payloadPart = parts[1];
        int padding = (4 - payloadPart.length() % 4) % 4;
        String payload = new String(Base64.getUrlDecoder().decode(payloadPart + "=".repeat(padding)), StandardCharsets.UTF_8);
        String jti = new ObjectMapper().readTree(payload).get("jti").asText();
        APIGatewayProxyRequestEvent event = bearer(token);
        tokens.revokeBearer(event);
        assertTrue(new InMemoryTokenRevocationRepository().isRevoked("missing") == false);
        assertEquals(401, tokens.errorIfNoSession(event).getStatusCode());
        assertTrue(jti.length() > 10);
    }

    private static String claims(String subject) {
        long exp = Instant.now().getEpochSecond() + 600;
        return "{\"sub\":\"" + subject + "\",\"iss\":\"moabom-auth\",\"aud\":\"moabom-api\",\"exp\":" + exp + ",\"jti\":\"fixed-jti\"}";
    }

    private static String crafted(String headerJson, String payloadJson) {
        String header = ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signing = header + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return signing + "." + ENCODER.encodeToString(mac.doFinal(signing.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static APIGatewayProxyRequestEvent bearer(String token) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withHeaders(Map.of("Authorization", "Bearer " + token));
    }
}
