package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private DepartmentRepository departmentRepository;
    private EmployeeService employeeService;

    @BeforeEach
    void setup() {
        employeeRepository = mock(EmployeeRepository.class);
        departmentRepository = mock(DepartmentRepository.class);

        employeeService = new EmployeeService();
        employeeService.employeeRepository = employeeRepository;
        employeeService.departmentRepository = departmentRepository;
    }

    @Test
    void testGetAllEmployees() {
        List<Employee> list = List.of(
                new Employee("1", "Elyes", "Sallem", true, null),
                new Employee("2", "Omar", "Safi", false, null)
        );
        when(employeeRepository.findAll()).thenReturn(list);

        List<Employee> result = employeeService.getAllEmployees();

        assertEquals(2, result.size());
        verify(employeeRepository, times(1)).findAll();
    }

    @Test
    void testGetEmployeeById() {
        Employee emp = new Employee("123", "Elyes", "Sallem", false, null);
        when(employeeRepository.findById("123")).thenReturn(Optional.of(emp));

        Optional<Employee> result = employeeService.getEmployeeById("123");

        assertTrue(result.isPresent());
        assertEquals(emp, result.get());
        verify(employeeRepository, times(1)).findById("123");
    }

    @Test
    void testGetAvailableEmployees() {
        List<Employee> employees = List.of(
                new Employee("1", "Elyes", "Sallem", true, null),
                new Employee("2", "Omar", "Safi", false, null),
                new Employee("3", "abc", "def", true, null)
        );

        when(employeeRepository.findAll()).thenReturn(employees);

        List<Employee> available = employeeService.getAvailableEmployees();

        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(Employee::getAvailable));
        verify(employeeRepository, times(1)).findAll();
    }

    @Test
    void testSaveEmployee_withExistingDepartment() {
        Department dep = new Department("d1", "Route 1", 10.1, 36.8);
        Employee emp = new Employee(null, "Elyes", "Sallem", true, dep);

        when(departmentRepository.existsById("d1")).thenReturn(true);
        when(employeeRepository.save(emp)).thenReturn(emp);

        Employee saved = employeeService.saveEmployee(emp);

        assertNotNull(saved);
        verify(employeeRepository, times(1)).save(emp);
    }

    @Test
    void testSaveEmployee_withNonExistingDepartment() {
        Department dep = new Department("d999", "Route X", 0.0, 0.0);
        Employee emp = new Employee(null, "Elyes", "Sallem", true, dep);

        when(departmentRepository.existsById("d999")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> employeeService.saveEmployee(emp));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void testUpdateEmployee_withExistingDepartment() {
        Department dep = new Department("d1", "Route 1", 10.1, 36.8);
        Employee existing = new Employee("123", "Elyes", "Sallem", true, dep);
        Employee update = new Employee(null, "Omar", "Safi", false, dep);

        when(employeeRepository.findById("123")).thenReturn(Optional.of(existing));
        when(departmentRepository.existsById("d1")).thenReturn(true);
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee result = employeeService.updateEmployee("123", update);

        assertNotNull(result);
        assertEquals("Omar", result.getFirstName());
        assertEquals("Safi", result.getLastName());
        assertFalse(result.getAvailable());
        assertEquals(dep, result.getDepartment());
        verify(employeeRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateEmployee_withNonExistingDepartment() {
        Department dep = new Department("d999", "Route X", 0.0, 0.0);
        Employee existing = new Employee("123", "Elyes", "Sallem", true, dep);
        Employee update = new Employee(null, "Omar", "Safi", false, dep);

        when(employeeRepository.findById("123")).thenReturn(Optional.of(existing));
        when(departmentRepository.existsById("d999")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> employeeService.updateEmployee("123", update));
        verify(employeeRepository, never()).save(any());
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