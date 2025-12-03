package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and registration endpoints")
public class AuthController {

    private final AuthenticationService authenticationService;

    @Operation(summary = "Login", description = "Authenticate a user and return a JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody User loginRequest) {
        try {
            Map<String, String> response = authenticationService.authenticate(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Operation(summary = "Sign up", description = "Register a new user (SUPER_ADMIN or ADMIN)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request or username exists")
    })
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User signupRequest) {
        try {
            Map<String, Object> response = authenticationService.signup(signupRequest);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
