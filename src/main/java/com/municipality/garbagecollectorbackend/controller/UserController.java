package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/super-admin")
    public User createSuperAdmin(@RequestBody User user) {
        return userService.createSuperAdmin(user);
    }

    @PostMapping("/admin/{departmentId}")
    public User createAdmin(@PathVariable String departmentId, @RequestBody User user) {
        return userService.createAdmin(user, departmentId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User updatedUser) {
        return ResponseEntity.ok(userService.updateUser(id, updatedUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
