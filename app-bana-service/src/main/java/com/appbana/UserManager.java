package com.appbana;

import com.appbana.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UserManager - Simple file-based user management service.
 * Stores users in date/users.json
 */
public class UserManager {
    private static final String USERS_FILE = "data/users.json";
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
     * Initialize user manager, load users from disk
     */
    public static void initialize() {
        try {
            Path usersPath = Paths.get(USERS_FILE);
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
        if (tenantId == null || tenantId.trim().isEmpty()) {
            // Auto-generate if not provided
            tenantId = "t-" + UUID.randomUUID().toString().substring(0, 8);
        }

        User user = User.builder()
                .id(idGenerator.incrementAndGet())
                .name(name)
                .email(email)
                .tenantId(tenantId)
                // In a real app, hash this password!
                .passwordHash(password)
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
     * Authenticate a user
     */
    public static User authenticate(String email, String password) {
        User user = usersByEmail.get(email.toLowerCase());
        if (user != null && user.getPasswordHash().equals(password)) {
            user.updateLastLogin();
            saveUsers();
            return user;
        }
        return null;
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
            mapper.writeValue(Paths.get(USERS_FILE).toFile(), new ArrayList<>(usersById.values()));
        } catch (IOException e) {
            System.err.println("[UserManager] Failed to save users: " + e.getMessage());
        }
    }
}
