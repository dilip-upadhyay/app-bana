package com.appbana;

import com.appbana.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S4.1 — {@link UserManager} must hash new registrations with BCrypt via {@code PasswordService}
 * and transparently rehash any legacy plaintext row the instant it proves itself via a real
 * successful login.
 *
 * <p>Deliberately never touches the real {@code data/users.json} — that file holds real local
 * dev accounts (confirmed by inspection before writing this class), not just fixtures, and
 * {@code UserManager.saveUsers()} overwrites it wholesale on every register/authenticate call.
 * Every test method here redirects {@code UserManager} to a per-class {@code @TempDir} file via
 * the {@code appbana.usersFile} system property (added alongside this task, mirroring
 * {@code AppRoutes}'s identical {@code appbana.dataDir} pattern) and resets {@code UserManager}'s
 * static in-memory state before and after the whole class runs, so this suite leaves zero residue
 * in either the real file or the shared static maps for any other test class in the same JVM.
 */
public class UserManagerPasswordHashingTest {

    private static String originalUsersFileProperty;

    @TempDir
    static Path tempDir;

    @BeforeAll
    public static void redirectToTempFile() {
        originalUsersFileProperty = System.getProperty("appbana.usersFile");
        System.setProperty("appbana.usersFile", tempDir.resolve("s41-users.json").toString());
        UserManager.resetForTesting();
        UserManager.initialize();
    }

    @AfterAll
    public static void restoreRealUsersFile() {
        UserManager.resetForTesting();
        if (originalUsersFileProperty == null) {
            System.clearProperty("appbana.usersFile");
        } else {
            System.setProperty("appbana.usersFile", originalUsersFileProperty);
        }
    }

    @BeforeEach
    public void clearUsersBetweenTests() {
        UserManager.resetForTesting();
    }

    private static boolean looksLikeBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    @Test
    public void newRegistrationStoresABcryptHashNotThePlaintextPassword() {
        String email = "s41-new@example.test";
        String rawPassword = "Correct-Horse-1";

        User registered = UserManager.register("S4.1 Fixture", email, rawPassword, "s41-tenant");

        assertNotEquals(rawPassword, registered.getPasswordHash(),
                "the stored value must never be the raw password");
        assertTrue(looksLikeBcryptHash(registered.getPasswordHash()),
                "a fresh registration must be stored as a BCrypt hash: " + registered.getPasswordHash());

        User authenticated = UserManager.authenticate(email, rawPassword);
        assertNotNull(authenticated, "the correct password must authenticate against the new hash");
        assertEquals(registered.getId(), authenticated.getId());

        assertNull(UserManager.authenticate(email, "wrong-password"),
                "an incorrect password must never authenticate");
    }

    @Test
    public void legacyPlaintextRowIsTransparentlyRehashedOnSuccessfulLogin() {
        String email = "s41-legacy@example.test";
        String rawPassword = "legacy-plaintext-pw-1";

        // Simulate a pre-S4.1 row (e.g. one of data/users.json's real seed rows): register()
        // now always hashes, so it can never itself produce the legacy shape this test targets —
        // seed it by registering normally, then downgrading the stored hash back to plaintext
        // in place (UserManager's maps hold the live User instance, so mutating it here is
        // exactly as if that row had been sitting in data/users.json since before this task).
        User seeded = UserManager.register("Legacy Fixture", email, rawPassword, "s41-tenant");
        seeded.setPasswordHash(rawPassword);

        assertEquals(rawPassword, UserManager.getUserByEmail(email).getPasswordHash(),
                "fixture setup sanity check: the seeded row must start out as plaintext");

        User authenticated = UserManager.authenticate(email, rawPassword);
        assertNotNull(authenticated, "the correct password must still authenticate against the legacy plaintext row");

        String rehashed = UserManager.getUserByEmail(email).getPasswordHash();
        assertNotEquals(rawPassword, rehashed,
                "a successful login against a legacy plaintext row must immediately rehash it");
        assertTrue(looksLikeBcryptHash(rehashed),
                "the rehashed value must be a real BCrypt hash: " + rehashed);

        // The rehashed value itself must be genuinely valid — not just "different from before".
        User secondLogin = UserManager.authenticate(email, rawPassword);
        assertNotNull(secondLogin, "the same password must still authenticate after the transparent rehash");
        assertEquals(rehashed, UserManager.getUserByEmail(email).getPasswordHash(),
                "a second successful login must not rehash again (already BCrypt, no-op)");
    }

    @Test
    public void wrongPasswordAgainstLegacyPlaintextRowDoesNotAuthenticateOrMutateTheStoredValue() {
        String email = "s41-legacy-wrong@example.test";
        String rawPassword = "legacy-plaintext-pw-2";

        User seeded = UserManager.register("Legacy Fixture 2", email, rawPassword, "s41-tenant");
        seeded.setPasswordHash(rawPassword);

        assertNull(UserManager.authenticate(email, "totally-wrong-password"),
                "an incorrect password must never authenticate, even against a legacy plaintext row");
        assertEquals(rawPassword, UserManager.getUserByEmail(email).getPasswordHash(),
                "a failed login attempt must never rehash or otherwise mutate the stored value");
    }
}
