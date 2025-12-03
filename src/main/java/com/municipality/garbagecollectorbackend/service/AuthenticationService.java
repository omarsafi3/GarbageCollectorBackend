package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling authentication operations.
 * Separates authentication logic from the controller layer.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates a user and returns a JWT token.
     *
     * @param username the username
     * @param password the raw password
     * @return Map containing the JWT token
     * @throws BadCredentialsException if credentials are invalid
     */
    public Map<String, String> authenticate(String username, String password) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(user);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        return response;
    }

    /**
     * Registers a new user based on role.
     *
     * @param signupRequest the user signup request
     * @return Map containing user details
     * @throws IllegalArgumentException if validation fails
     */
    public Map<String, Object> signup(User signupRequest) {
        // Check if username exists
        if (userService.findByUsername(signupRequest.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User created;

        // Determine whether to create SUPER_ADMIN or ADMIN
        if (signupRequest.getRole() == User.Role.SUPER_ADMIN) {
            created = userService.createSuperAdmin(signupRequest);
        } else if (signupRequest.getRole() == User.Role.ADMIN) {
            if (signupRequest.getDepartmentId() == null) {
                throw new IllegalArgumentException("departmentId is required for ADMIN role");
            }
            created = userService.createAdmin(signupRequest, signupRequest.getDepartmentId());
        } else {
            throw new IllegalArgumentException("Invalid role");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Signup successful");
        response.put("userId", created.getId());
        response.put("username", created.getUsername());
        response.put("role", created.getRole());
        response.put("departmentId", created.getDepartmentId());

        return response;
    }
}
