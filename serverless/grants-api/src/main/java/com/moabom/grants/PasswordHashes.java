package com.moabom.grants;

import at.favre.lib.crypto.bcrypt.BCrypt;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * New passwords are stored as BCrypt. Django pbkdf2_sha256 hashes are verified only,
 * then replaced with BCrypt after a successful login.
 */
public final class PasswordHashes {
    private static final int MAX_DJANGO_ITERATIONS = 2_000_000;

    private final int bcryptCost;

    public PasswordHashes() {
        this(12);
    }

    public PasswordHashes(int bcryptCost) {
        if (bcryptCost < 4 || bcryptCost > 15) {
            throw new IllegalArgumentException("BCrypt cost가 범위를 벗어났습니다.");
        }
        this.bcryptCost = bcryptCost;
    }

    public String hash(String password) {
        return BCrypt.withDefaults().hashToString(bcryptCost, password.toCharArray());
    }

    public boolean matches(String password, String stored) {
        if (password == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return BCrypt.verifyer().verify(password.toCharArray(), stored).verified;
        }
        if (stored.startsWith("pbkdf2_sha256$")) {
            return djangoPbkdf2(password, stored);
        }
        return false;
    }

    public boolean needsUpgrade(String stored) {
        return stored != null && stored.startsWith("pbkdf2_sha256$");
    }

    static boolean djangoPbkdf2(String password, String stored) {
        String[] parts = stored.split("\\$", 4);
        if (parts.length != 4 || !"pbkdf2_sha256".equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (iterations < 1 || iterations > MAX_DJANGO_ITERATIONS) {
            return false;
        }
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (expected.length == 0) {
            return false;
        }
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    parts[2].getBytes(StandardCharsets.UTF_8),
                    iterations,
                    expected.length * 8
            );
            byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (GeneralSecurityException ex) {
            return false;
        }
    }
}
