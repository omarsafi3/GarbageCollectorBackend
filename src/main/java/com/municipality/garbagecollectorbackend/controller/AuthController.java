package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Role;
import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.service.UserService;
import com.municipality.garbagecollectorbackend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody User loginRequest) {
        User user = userService.findByUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid username"));

        if (!userService.getPasswordEncoder().matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String token = jwtUtil.generateToken(user);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User signupRequest) {

        // Check if username exists
        if (userService.findByUsername(signupRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }

        User created;

        // Determine whether to create SUPER_ADMIN or ADMIN
        if (signupRequest.getRole() == User.Role.SUPER_ADMIN) {
            created = userService.createSuperAdmin(signupRequest);
        } else if (signupRequest.getRole() == User.Role.ADMIN) {
            if (signupRequest.getDepartmentId() == null) {
                return ResponseEntity.badRequest().body("departmentId is required for ADMIN role");
            }
            created = userService.createAdmin(signupRequest, signupRequest.getDepartmentId());
        } else {
            return ResponseEntity.badRequest().body("Invalid role");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Signup successful");
        response.put("userId", created.getId());
        response.put("username", created.getUsername());
        response.put("role", created.getRole());
        response.put("departmentId", created.getDepartmentId());

        return ResponseEntity.ok(response);
    }


}
