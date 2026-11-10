package balgorithm.service;

import balgorithm.model.User;
import balgorithm.storage.FileStorage;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthService {
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_HASH_BITS = 256;
    private final FileStorage storage;
    private final SecureRandom random = new SecureRandom();

    public AuthService(FileStorage storage) {
        this.storage = storage;
    }

    public User register(String name, String email, String password) throws IOException {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleanName.isBlank() || cleanName.length() > 80) {
            throw new IllegalArgumentException("Enter a name between 1 and 80 characters.");
        }
        if (!cleanEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || cleanEmail.length() > 160) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length() < 6 || password.length() > 200) {
            throw new IllegalArgumentException("Your password must be between 6 and 200 characters.");
        }
        List<User> users = storage.readUsers();
        if (users.stream().anyMatch(user -> user.getEmail().equalsIgnoreCase(cleanEmail))) {
            throw new IllegalArgumentException("An account with that email already exists.");
        }
        User user = new User(UUID.randomUUID().toString(), cleanName, cleanEmail, hashPassword(password));
        users.add(user);
        storage.writeUsers(users);
        return user;
    }

    public User login(String email, String password) throws IOException {
        String cleanEmail = email == null ? "" : email.trim();
        String suppliedPassword = password == null ? "" : password;
        for (User user : storage.readUsers()) {
            if (user.getEmail().equalsIgnoreCase(cleanEmail)
                    && passwordMatches(suppliedPassword, user.getPasswordHash())) return user;
        }
        throw new IllegalArgumentException("Email or password is incorrect.");
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = derivePasswordHash(password, salt, PASSWORD_ITERATIONS);
        return PASSWORD_ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean passwordMatches(String password, String savedHash) {
        String[] parts = savedHash.split(":", -1);
        if (parts.length != 3) return false;
        try {
            int iterations = Integer.parseInt(parts[0]);
            if (iterations != PASSWORD_ITERATIONS) return false;
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(expected, derivePasswordHash(password, salt, iterations));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derivePasswordHash(String password, byte[] salt, int iterations) {
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec keySpec = new PBEKeySpec(passwordCharacters, salt, iterations, PASSWORD_HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Java does not provide PBKDF2WithHmacSHA256.", exception);
        } finally {
            keySpec.clearPassword();
            java.util.Arrays.fill(passwordCharacters, '\0');
        }
    }
}
