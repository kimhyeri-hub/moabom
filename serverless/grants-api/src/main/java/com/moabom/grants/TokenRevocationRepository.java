package com.moabom.grants;

public interface TokenRevocationRepository {
    void revoke(String tokenId, long expiresAtEpochSeconds);

    boolean isRevoked(String tokenId);
}
