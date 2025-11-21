package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Role;
import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    public UserRepository userRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User createSuperAdmin(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        user.setRole(Role.SUPER_ADMIN);
        user.setDepartmentId(null);
        return userRepository.save(user);
    }

    public User createAdmin(User user, String departmentId) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        user.setRole(Role.ADMIN);
        user.setDepartmentId(departmentId);

        return userRepository.save(user);
    }

    public User updateUser(String id, User updated) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<User> userWithSameUsername = userRepository.findByUsername(updated.getUsername());
        if (userWithSameUsername.isPresent() && !userWithSameUsername.get().getId().equals(id)) {
            throw new RuntimeException("Username already exists");
        }

        existing.setUsername(updated.getUsername());
        existing.setPassword(updated.getPassword());
        existing.setRole(updated.getRole());
        existing.setDepartmentId(updated.getDepartmentId());

        return userRepository.save(existing);
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

}