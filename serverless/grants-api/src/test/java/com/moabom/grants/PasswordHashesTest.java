package com.moabom.grants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashesTest {
    private static final String DJANGO_HASH =
            "pbkdf2_sha256$260000$salt$hPvVEutECp0JjRhYqwibgqTefTo/DzKXnCroL0aoSXM=";

    private final PasswordHashes passwords = new PasswordHashes(4);

    @Test
    void bcryptRoundTripDoesNotStoreThePassword() {
        String stored = passwords.hash("Moabom-pass-1");
        assertTrue(stored.startsWith("$2a$"));
        assertFalse(stored.contains("Moabom-pass-1"));
        assertTrue(passwords.matches("Moabom-pass-1", stored));
        assertFalse(passwords.matches("wrong-password", stored));
        assertFalse(passwords.needsUpgrade(stored));
    }

    @Test
    void djangoPbkdf2HashVerifiesAndNeedsUpgrade() {
        assertTrue(passwords.matches("secret", DJANGO_HASH));
        assertFalse(passwords.matches("other", DJANGO_HASH));
        assertTrue(passwords.needsUpgrade(DJANGO_HASH));
        assertFalse(passwords.matches("secret", "plain-secret"));
    }
}
