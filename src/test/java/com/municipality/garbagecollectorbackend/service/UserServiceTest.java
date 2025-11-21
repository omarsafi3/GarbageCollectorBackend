package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Role;
import com.municipality.garbagecollectorbackend.model.User;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private DepartmentRepository departmentRepository;
    private UserService userService;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        departmentRepository = mock(DepartmentRepository.class);

        userService = new UserService();
        userService.userRepository = userRepository;
        userService.departmentRepository = departmentRepository;
    }

    @Test
    void testGetAllUsers() {
        List<User> users = List.of(
                new User("1", "u1", "p1", Role.ADMIN, "d1"),
                new User("2", "u2", "p2", Role.SUPER_ADMIN, null)
        );

        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertEquals(2, result.size());
        verify(userRepository).findAll();
    }

    @Test
    void testGetUserById() {
        User user = new User("10", "admin", "pass", Role.ADMIN, "d1");
        when(userRepository.findById("10")).thenReturn(Optional.of(user));

        User result = userService.getUserById("10");

        assertEquals(user, result);
        verify(userRepository).findById("10");
    }

    @Test
    void testGetUserById_notFound() {
        when(userRepository.findById("X")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.getUserById("X"));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void testCreateSuperAdmin_success() {
        User user = new User(null, "super", "pass", Role.ADMIN, "d1");
        when(userRepository.findByUsername("super")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User saved = userService.createSuperAdmin(user);

        assertEquals(Role.SUPER_ADMIN, saved.getRole());
        assertNull(saved.getDepartmentId());
        verify(userRepository).save(user);
    }

    @Test
    void testCreateSuperAdmin_duplicateUsername() {
        User user = new User(null, "super", "pass", Role.ADMIN, "d1");
        when(userRepository.findByUsername("super")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createSuperAdmin(user));

        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testCreateAdmin_success() {
        User user = new User(null, "admin", "pass", Role.SUPER_ADMIN, null);
        Department dep = new Department("d10", "Route 1", 10.0, 20.0);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(departmentRepository.findById("d10")).thenReturn(Optional.of(dep));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User saved = userService.createAdmin(user, "d10");

        assertEquals(Role.ADMIN, saved.getRole());
        assertEquals("d10", saved.getDepartmentId());
        verify(userRepository).save(user);
    }

    @Test
    void testCreateAdmin_duplicateUsername() {
        User user = new User(null, "admin", "pass", Role.SUPER_ADMIN, null);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createAdmin(user, "d10"));

        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testCreateAdmin_departmentNotFound() {
        User user = new User(null, "admin", "pass", Role.SUPER_ADMIN, null);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(departmentRepository.findById("d404")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.createAdmin(user, "d404"));

        assertEquals("Department not found", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testUpdateUser_success() {
        User existing = new User("1", "old", "oldpass", Role.ADMIN, "d1");
        User updated = new User(null, "new", "newpass", Role.SUPER_ADMIN, null);

        when(userRepository.findById("1")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("new")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUser("1", updated);

        assertEquals("new", result.getUsername());
        assertEquals("newpass", result.getPassword());
        assertEquals(Role.SUPER_ADMIN, result.getRole());
        assertNull(result.getDepartmentId());
        verify(userRepository).save(existing);
    }

    @Test
    void testUpdateUser_duplicateUsername() {
        User existing = new User("1", "old", "oldpass", Role.ADMIN, "d1");
        User updated = new User(null, "dup", "newpass", Role.SUPER_ADMIN, null);
        User other = new User("2", "dup", "pass", Role.ADMIN, "d1");

        when(userRepository.findById("1")).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("dup")).thenReturn(Optional.of(other));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateUser("1", updated));

        assertEquals("Username already exists", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testUpdateUser_notFound() {
        when(userRepository.findById("404")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.updateUser("404", new User()));

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void testDeleteUser() {
        userService.deleteUser("10");
        verify(userRepository).deleteById("10");
    }
}
