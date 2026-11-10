package balgorithm.model;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class User {
    private final String id;
    private final String name;
    private final String email;
    private final String passwordHash;

    public User(String id, String name, String email, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }

    public String toFileLine() {
        return encode(id) + "\t" + encode(name) + "\t" + encode(email) + "\t" + encode(passwordHash);
    }

    public static User fromFileLine(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 4) throw new IllegalArgumentException("Invalid user row");
        return new User(decode(fields[0]), decode(fields[1]), decode(fields[2]), decode(fields[3]));
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
