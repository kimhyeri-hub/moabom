package com.moabom.grants;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies HS256 bearer tokens.
 * The subject is the account user id, the same decimal string used on profile routes.
 * A user id in the request path is accepted only when it equals that subject.
 * Issuer, audience, expiry, and the HS256 algorithm are required. Logout stores the jti until exp.
 */
public final class AccessTokens {
    public static final String ISSUER = "moabom-auth";
    public static final String AUDIENCE = "moabom-api";
    public static final long TTL_SECONDS = 12 * 60 * 60;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String secret;
    private final TokenRevocationRepository revocations;

    public AccessTokens(String secret) {
        this(secret, null);
    }

    public AccessTokens(String secret, TokenRevocationRepository revocations) {
        this.secret = secret == null ? "" : secret;
        this.revocations = revocations;
    }

    public AccessTokens withRevocations(TokenRevocationRepository revocations) {
        return new AccessTokens(secret, revocations);
    }

    public static AccessTokens fromEnvironment() {
        String table = System.getenv(DynamoTokenRevocationRepository.TABLE_NAME_ENV);
        TokenRevocationRepository stored = null;
        if (table != null && !table.isBlank()) {
            stored = DynamoTokenRevocationRepository.fromEnvironment();
        }
        return new AccessTokens(System.getenv("MOABOM_AUTH_SECRET"), stored);
    }

    public String issue(String subject) {
        return sign(subject, Instant.now().getEpochSecond() + TTL_SECONDS);
    }

    public APIGatewayProxyResponseEvent errorIfUnauthorized(APIGatewayProxyRequestEvent event, String pathUserId) {
        APIGatewayProxyResponseEvent missing = errorIfNoSession(event);
        if (missing != null) {
            return missing;
        }
        if (pathUserId == null || !ApiResponses.ID.matcher(pathUserId).matches()) {
            return ApiResponses.error(400, "사용자 ID 형식이 올바르지 않습니다.", true);
        }
        if (!pathUserId.equals(verifiedSubject(event))) {
            return ApiResponses.error(403, "요청한 사용자와 인증된 사용자가 다릅니다.", true);
        }
        return null;
    }

    public APIGatewayProxyResponseEvent errorIfNoSession(APIGatewayProxyRequestEvent event) {
        if (secret.isBlank()) {
            return ApiResponses.error(401, "인증 설정을 확인할 수 없습니다.", true);
        }
        if (bearer(event) == null) {
            return ApiResponses.error(401, "인증 정보가 없습니다.", true);
        }
        try {
            verifiedSubject(event);
            return null;
        } catch (IllegalArgumentException ex) {
            return ApiResponses.error(401, "인증 정보가 유효하지 않습니다.", true);
        }
    }

    public String verifiedSubject(APIGatewayProxyRequestEvent event) {
        String token = bearer(event);
        if (token == null) {
            throw new IllegalArgumentException("missing");
        }
        return verify(token);
    }

    public void revokeBearer(APIGatewayProxyRequestEvent event) {
        String token = bearer(event);
        if (token == null) {
            throw new IllegalArgumentException("missing");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("shape");
        }
        verify(token);
        if (revocations == null) {
            throw new IllegalStateException("revocation store");
        }
        JsonNode payload = read(parts[1]);
        revocations.revoke(payload.get("jti").asText(), payload.get("exp").asLong());
    }

    public String sign(String subject, long expiresAtEpochSeconds) {
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", subject);
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", expiresAtEpochSeconds);
        claims.put("jti", UUID.randomUUID().toString());
        String payload;
        try {
            payload = encode(MAPPER.writeValueAsBytes(claims));
        } catch (Exception ex) {
            throw new IllegalStateException("토큰을 만들지 못했습니다.", ex);
        }
        String signing = header + "." + payload;
        return signing + "." + encode(hmac(signing));
    }

    private String verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("shape");
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] actual;
        try {
            actual = decode(parts[2]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("signature");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("signature");
        }
        JsonNode header = read(parts[0]);
        if (header == null || !"HS256".equals(header.path("alg").asText())) {
            throw new IllegalArgumentException("alg");
        }
        JsonNode payload = read(parts[1]);
        if (payload == null || !payload.hasNonNull("sub") || !payload.has("exp")) {
            throw new IllegalArgumentException("payload");
        }
        long exp = payload.get("exp").asLong();
        if (exp <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("expired");
        }
        if (!ISSUER.equals(payload.path("iss").asText())) {
            throw new IllegalArgumentException("issuer");
        }
        if (!audienceMatches(payload.get("aud"))) {
            throw new IllegalArgumentException("audience");
        }
        String tokenId = payload.path("jti").asText("");
        if (tokenId.isBlank()) {
            throw new IllegalArgumentException("jti");
        }
        if (revocations != null && revocations.isRevoked(tokenId)) {
            throw new IllegalArgumentException("revoked");
        }
        String subject = payload.get("sub").asText();
        if (!ApiResponses.ID.matcher(subject).matches()) {
            throw new IllegalArgumentException("subject");
        }
        return subject;
    }

    private static boolean audienceMatches(JsonNode audience) {
        if (audience == null || audience.isNull()) {
            return false;
        }
        if (audience.isTextual()) {
            return AUDIENCE.equals(audience.asText());
        }
        if (audience.isArray()) {
            for (JsonNode item : audience) {
                if (item.isTextual() && AUDIENCE.equals(item.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode read(String segment) {
        try {
            return MAPPER.readTree(decode(segment));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("json");
        } catch (Exception ex) {
            throw new IllegalArgumentException("json");
        }
    }

    private byte[] hmac(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("토큰 서명을 확인하지 못했습니다.", ex);
        }
    }

    private static String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private static byte[] decode(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return DECODER.decode(value + "=".repeat(padding));
    }

    private static String bearer(APIGatewayProxyRequestEvent event) {
        if (event == null || event.getHeaders() == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : event.getHeaders().entrySet()) {
            if (entry.getKey() != null && "authorization".equalsIgnoreCase(entry.getKey())) {
                String value = entry.getValue();
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                if (trimmed.length() < 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    return null;
                }
                String token = trimmed.substring(7).trim();
                return token.isEmpty() ? null : token;
            }
        }
        return null;
    }
}
