package com.appbana;

import com.appbana.model.User;
import com.appbana.service.PasswordService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UserManager - Simple file-based user management service.
 * Stores users in date/users.json
 */
public class UserManager {
    private static final String USERS_FILE_DEFAULT = "data/users.json";
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Map<Long, User> usersById = new ConcurrentHashMap<>();
    private static final Map<String, User> usersByEmail = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(0);

    private UserManager() {
        // Prevent instantiation
    }

    /**
     * Resolves the users-file path, honoring an {@code appbana.usersFile} system-property
     * override (mirrors {@code AppRoutes}'s identical {@code appbana.dataDir} pattern).
     * Read lazily on every call rather than baked into a {@code static final} at class-load
     * time, so a test can override it via {@code System.setProperty(...)} at any point before
     * calling {@link #initialize()} — including after this class has already been referenced —
     * without depending on class-initialization ordering. This is what lets tests exercise
     * real register/authenticate behavior without ever touching the real {@code data/users.json}
     * (which holds real local dev accounts, not just fixtures).
     */
    private static String usersFilePath() {
        return System.getProperty("appbana.usersFile", USERS_FILE_DEFAULT);
    }

    /**
     * Initialize user manager, load users from disk
     */
    public static void initialize() {
        try {
            Path usersPath = Paths.get(usersFilePath());
            if (Files.exists(usersPath)) {
                List<User> userList = mapper.readValue(usersPath.toFile(), new TypeReference<List<User>>() {
                });
                long maxId = 0;
                for (User user : userList) {
                    usersById.put(user.getId(), user);
                    usersByEmail.put(user.getEmail().toLowerCase(), user);
                    if (user.getId() > maxId)
                        maxId = user.getId();
                }
                idGenerator.set(maxId);
                System.out.println("[UserManager] Loaded " + userList.size() + " users.");
            } else {
                Files.createDirectories(usersPath.getParent());
                System.out.println("[UserManager] No existing users found. Initialized empty store.");
            }
        } catch (IOException e) {
            System.err.println("[UserManager] Failed to load users: " + e.getMessage());
        }
    }

    /**
     * Register a new user
     */
    public static User register(String name, String email, String password, String tenantId)
            throws IllegalArgumentException {
        if (usersByEmail.containsKey(email.toLowerCase())) {
            throw new IllegalArgumentException("Email already active");
        }
        if (password == null || password.isBlank()) {
            // S4.1: fail with the same clear, existing-pattern IllegalArgumentException used
            // above rather than letting PasswordService.hashPassword's own less-specific
            // "Password cannot be null or empty" surface first — and rather than silently
            // storing an unhashed empty string, which the pre-S4.1 code would have done.
            throw new IllegalArgumentException("Password is required");
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            // Auto-generate if not provided
            tenantId = "t-" + UUID.randomUUID().toString().substring(0, 8);
        }

        User user = User.builder()
                .id(idGenerator.incrementAndGet())
                .name(name)
                .email(email)
                .tenantId(tenantId)
                // S4.1: BCrypt hash on write — was previously stored as the raw plaintext
                // password (see the S4 credential-hygiene sub-phase this closes).
                .passwordHash(PasswordService.hashPassword(password))
                .status(User.UserStatus.ACTIVE)
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        usersById.put(user.getId(), user);
        usersByEmail.put(user.getEmail().toLowerCase(), user);

        saveUsers();
        System.out.println("[UserManager] Registered user: " + email + " (Tenant: " + tenantId + ")");
        return user;
    }

    /**
     * Authenticate a user.
     *
     * <p>S4.1: transparently upgrades any legacy plaintext row (a Phase-1 registration, or one
     * of {@code data/users.json}'s pre-existing seed rows predating this task) to a BCrypt hash
     * the instant it proves itself via one real successful login — no forced reset, no dual
     * write path. Every registration from this point on is already BCrypt-hashed by
     * {@link #register}, so the plaintext branch below only ever fires for a row this method
     * has not yet touched.
     */
    public static User authenticate(String email, String password) {
        User user = usersByEmail.get(email.toLowerCase());
        if (user == null || !verifyCredential(password, user.getPasswordHash())) {
            return null;
        }

        if (!looksLikeBcryptHash(user.getPasswordHash())) {
            user.setPasswordHash(PasswordService.hashPassword(password));
        }

        user.updateLastLogin();
        saveUsers();
        return user;
    }

    /**
     * Verifies a raw password against a stored value that may be either a BCrypt hash (current
     * format, S4.1+) or a legacy plaintext value (pre-S4.1 registrations / {@code
     * data/users.json} seed rows). Mirrors {@code GenericAppAuthController.verifyCredential}
     * (S3.3/M5) exactly, including its null/empty guard — {@code PasswordService.verifyPassword}/
     * {@code hashPassword} both throw {@code IllegalArgumentException} on a blank argument, so
     * this method must be safe to call with any non-null String regardless of caller-side
     * guards.
     */
    private static boolean verifyCredential(String rawPassword, String storedValue) {
        if (rawPassword == null || rawPassword.isEmpty() || storedValue == null || storedValue.isEmpty()) {
            return false;
        }
        if (looksLikeBcryptHash(storedValue)) {
            return PasswordService.verifyPassword(rawPassword, storedValue);
        }
        // Constant-time compare for the legacy plaintext fallback — avoids a timing
        // side-channel on the comparison itself, same rationale as GenericAppAuthController.
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedValue.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean looksLikeBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    /**
     * Get user by ID
     */
    public static User getUser(Long id) {
        return usersById.get(id);
    }

    /**
     * Get user by Email
     */
    public static User getUserByEmail(String email) {
        if (email == null)
            return null;
        return usersByEmail.get(email.toLowerCase());
    }

    /**
     * Save users to disk
     */
    private static void saveUsers() {
        try {
            mapper.writeValue(Paths.get(usersFilePath()).toFile(), new ArrayList<>(usersById.values()));
        } catch (IOException e) {
            System.err.println("[UserManager] Failed to save users: " + e.getMessage());
        }
    }

    /**
     * Test-only: clears all in-memory user state and resets the id generator. Package-private —
     * production code has no legitimate reason to wipe every user. Pair with overriding the
     * {@code appbana.usersFile} system property before calling {@link #initialize()}, so tests
     * never read from or write to the real {@code data/users.json} (which holds real local dev
     * accounts, not just fixtures).
     */
    static void resetForTesting() {
        usersById.clear();
        usersByEmail.clear();
        idGenerator.set(0);
    }
}
