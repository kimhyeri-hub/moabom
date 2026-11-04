package com.moabom.grants;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTokenRevocationRepository implements TokenRevocationRepository {
    private final ConcurrentHashMap<String, Long> until = new ConcurrentHashMap<>();

    @Override
    public void revoke(String tokenId, long expiresAtEpochSeconds) {
        until.put(tokenId, expiresAtEpochSeconds);
    }

    @Override
    public boolean isRevoked(String tokenId) {
        Long expiresAt = until.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= Instant.now().getEpochSecond()) {
            until.remove(tokenId, expiresAt);
            return false;
        }
        return true;
    }
}
