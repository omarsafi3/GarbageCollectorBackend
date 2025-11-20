package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DepartmentServiceTest {

    private DepartmentRepository departmentRepository;
    private DepartmentService departmentService;

    @BeforeEach
    void setup() {
        departmentRepository = mock(DepartmentRepository.class);
        departmentService = new DepartmentService();
        departmentService.departmentRepository = departmentRepository; // manual injection
    }

    @Test
    void testGetAllDepartments() {
        List<Department> departments = List.of(
                new Department("1", "Route 1", 36.8, 10.1),
                new Department("2", "Route 2", 36.9, 10.2)
        );
        when(departmentRepository.findAll()).thenReturn(departments);

        List<Department> result = departmentService.getAllDepartments();

        assertEquals(2, result.size());
        verify(departmentRepository, times(1)).findAll();
    }

    @Test
    void testGetDepartmentById() {
        Department dept = new Department("1", "Route 1", 36.8, 10.1);
        when(departmentRepository.findById("1")).thenReturn(Optional.of(dept));

        Optional<Department> result = departmentService.getDepartmentById("1");

        assertTrue(result.isPresent());
        assertEquals("Route 1", result.get().getName());
        assertEquals(36.8, result.get().getLatitude());
        assertEquals(10.1, result.get().getLongitude());
        verify(departmentRepository, times(1)).findById("1");
    }

    @Test
    void testSaveDepartment() {
        Department dept = new Department("1", "Route 1", 36.8, 10.1);
        when(departmentRepository.save(dept)).thenReturn(dept);

        Department saved = departmentService.saveDepartment(dept);

        assertNotNull(saved);
        assertEquals("Route 1", saved.getName());
        assertEquals(36.8, saved.getLatitude());
        assertEquals(10.1, saved.getLongitude());
        verify(departmentRepository, times(1)).save(dept);
    }

    @Test
    void testUpdateDepartment() {
        Department existing = new Department("1", "Route 1", 36.8, 10.1);
        Department updated = new Department(null, "Route 1A", 37.0, 11.0);

        when(departmentRepository.findById("1")).thenReturn(Optional.of(existing));
        when(departmentRepository.save(existing)).thenReturn(existing);

        Department result = departmentService.updateDepartment("1", updated);

        assertNotNull(result);
        assertEquals("Route 1A", result.getName());
        assertEquals(37.0, result.getLatitude());
        assertEquals(11.0, result.getLongitude());
        verify(departmentRepository, times(1)).findById("1");
        verify(departmentRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateDepartment_notFound() {
        when(departmentRepository.findById("999")).thenReturn(Optional.empty());

        Department result = departmentService.updateDepartment("999", new Department());

        assertNull(result);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void testDeleteDepartment() {
        departmentService.deleteDepartment("1");
        verify(departmentRepository, times(1)).deleteById("1");
    }

}
