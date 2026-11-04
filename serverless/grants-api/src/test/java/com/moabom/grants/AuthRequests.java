package com.moabom.grants;

import java.time.Instant;

final class AuthRequests {
    static final AccessTokens TOKENS = new AccessTokens("unit-test-secret-not-for-deployment");

    private AuthRequests() {
    }

    static String header(String userId) {
        return "Bearer " + TOKENS.sign(userId, Instant.now().getEpochSecond() + 3600);
    }
}
