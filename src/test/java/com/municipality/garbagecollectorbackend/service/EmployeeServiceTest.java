package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private EmployeeService employeeService;

    @BeforeEach
    void setup() {
        employeeRepository = mock(EmployeeRepository.class);

        employeeService = new EmployeeService();
        employeeService.employeeRepository = employeeRepository;
    }

    @Test
    void testGetAllEmployees() {
        List<Employee> list = List.of(
                new Employee("1", "Elyes", "Sallem", true),
                new Employee("2", "Omar", "Safi", false)
        );
        when(employeeRepository.findAll()).thenReturn(list);

        List<Employee> result = employeeService.getAllEmployees();

        assertEquals(2, result.size());
        verify(employeeRepository, times(1)).findAll();
    }

    @Test
    void testGetEmployeeById() {
        Employee emp = new Employee("123", "Elyes", "Sallem", false);
        when(employeeRepository.findById("123")).thenReturn(Optional.of(emp));

        Optional<Employee> result = employeeService.getEmployeeById("123");

        assertTrue(result.isPresent());
        assertEquals(emp, result.get());
        verify(employeeRepository, times(1)).findById("123");
    }

    @Test
    void testGetAvailableEmployees() {
        List<Employee> employees = List.of(
                new Employee("1", "Elyes", "Sallem", true),
                new Employee("2", "Omar", "Safi", false),
                new Employee("3", "abc", "def", true)
        );

        when(employeeRepository.findAll()).thenReturn(employees);

        List<Employee> available = employeeService.getAvailableEmployees();

        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(Employee::getAvailable));
        verify(employeeRepository, times(1)).findAll();
    }

    @Test
    void testSaveEmployee() {
        Employee emp = new Employee(null, "Elyes", "Sallem", true);
        when(employeeRepository.save(emp)).thenReturn(emp);

        Employee saved = employeeService.saveEmployee(emp);

        assertNotNull(saved);
        assertEquals("Elyes", saved.getFirstName());
        verify(employeeRepository, times(1)).save(emp);
    }

    @Test
    void testUpdateEmployee() {
        Employee existing = new Employee("123", "Elyes", "Sallem", true);
        Employee updated = new Employee(null, "Omar", "Safi", false);

        when(employeeRepository.findById("123")).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee result = employeeService.updateEmployee("123", updated);

        assertNotNull(result);
        assertEquals("Omar", result.getFirstName());
        assertEquals("Safi", result.getLastName());
        assertFalse(result.getAvailable());

        verify(employeeRepository, times(1)).findById("123");
        verify(employeeRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateEmployee_notFound() {
        when(employeeRepository.findById("999")).thenReturn(Optional.empty());

        Employee result = employeeService.updateEmployee("999", new Employee());

        assertNull(result);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void testDeleteEmployee() {
        employeeService.deleteEmployee("456");
        verify(employeeRepository, times(1)).deleteById("456");
    }

}
