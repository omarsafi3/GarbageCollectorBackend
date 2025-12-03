package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users", description = "User management endpoints")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "Get all users", description = "Retrieves a list of all users")
    @ApiResponse(responseCode = "200", description = "List of all users")
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Get user by ID", description = "Retrieves a user by their unique ID")
    @ApiResponse(responseCode = "200", description = "User found")
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Create super admin", description = "Creates a new super admin user")
    @ApiResponse(responseCode = "201", description = "Super admin created successfully")
    @PostMapping("/super-admin")
    public ResponseEntity<User> createSuperAdmin(@RequestBody User user) {
        User createdUser = userService.createSuperAdmin(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @Operation(summary = "Create admin", description = "Creates a new admin user for a specific department")
    @ApiResponse(responseCode = "201", description = "Admin created successfully")
    @PostMapping("/admin/{departmentId}")
    public ResponseEntity<User> createAdmin(@PathVariable String departmentId, @RequestBody User user) {
        User createdUser = userService.createAdmin(user, departmentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @Operation(summary = "Update user", description = "Updates an existing user by ID")
    @ApiResponse(responseCode = "200", description = "User updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User updatedUser) {
        User user = userService.updateUser(id, updatedUser);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Delete user", description = "Deletes a user by ID")
    @ApiResponse(responseCode = "204", description = "User deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
