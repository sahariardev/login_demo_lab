package com.example.authdemo.service;

import com.example.authdemo.model.User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory user store holding 5 demo users.
 */
@Service
public class UserService {

    private final Map<String, User> users = new LinkedHashMap<>();

    public UserService() {
        addUser(new User("admin", "admin123", "Admin User"));
        addUser(new User("rifat", "rifat123", "Rifat Ahmed"));
        addUser(new User("john", "john123", "John Doe"));
        addUser(new User("jane", "jane123", "Jane Smith"));
        addUser(new User("guest", "guest123", "Guest User"));
    }

    private void addUser(User user) {
        users.put(user.getUsername(), user);
    }

    /**
     * Looks up a user by username.
     *
     * @return the user, or {@code null} when no user with that username exists
     */
    public User findByUsername(String username) {
        if (username == null) {
            return null;
        }
        return users.get(username.trim().toLowerCase());
    }

    /**
     * Validates the supplied credentials.
     *
     * <p>The username is checked first, then the password, so the caller can
     * report which one was wrong.</p>
     */
    public LoginResult authenticate(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            return LoginResult.USERNAME_NOT_FOUND;
        }
        if (password == null || !user.getPassword().equals(password)) {
            return LoginResult.WRONG_PASSWORD;
        }
        return LoginResult.SUCCESS;
    }
}
