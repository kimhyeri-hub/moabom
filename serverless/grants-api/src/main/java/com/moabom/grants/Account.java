package com.moabom.grants;

/**
 * Credential record. userId is the same decimal id used by profile and recommendation routes.
 * passwordHash is a BCrypt string or a legacy Django pbkdf2_sha256 string, never a plaintext password.
 */
public record Account(
        String userId,
        String loginId,
        String email,
        String name,
        String dateOfBirth,
        String passwordHash,
        String region,
        Integer incomeBracket
) {
    public Account withUserId(String nextUserId) {
        return new Account(nextUserId, loginId, email, name, dateOfBirth, passwordHash, region, incomeBracket);
    }

    public Account withPasswordHash(String nextHash) {
        return new Account(userId, loginId, email, name, dateOfBirth, nextHash, region, incomeBracket);
    }

    public Account withDetails(String nextName, String nextEmail, String nextBirth, String nextRegion, Integer nextIncome) {
        return new Account(userId, loginId, nextEmail, nextName, nextBirth, passwordHash, nextRegion, nextIncome);
    }
}
