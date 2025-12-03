package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.EmployeeStatus;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee testDriver;
    private Employee testCollector;
    private Department testDepartment;

    @BeforeEach
    void setUp() {
        testDepartment = new Department("dep1", "Test Department", 36.8, 10.1);

        testDriver = new Employee();
        testDriver.setId("emp1");
        testDriver.setFirstName("John");
        testDriver.setLastName("Driver");
        testDriver.setRole(Employee.EmployeeRole.DRIVER);
        testDriver.setAvailable(true);
        testDriver.setStatus(EmployeeStatus.AVAILABLE);
        testDriver.setDepartment(testDepartment);

        testCollector = new Employee();
        testCollector.setId("emp2");
        testCollector.setFirstName("Jane");
        testCollector.setLastName("Collector");
        testCollector.setRole(Employee.EmployeeRole.COLLECTOR);
        testCollector.setAvailable(true);
        testCollector.setStatus(EmployeeStatus.AVAILABLE);
        testCollector.setDepartment(testDepartment);
    }

    @Test
    void testGetAllEmployees() {
        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector));

        List<Employee> result = employeeService.getAllEmployees();

        assertEquals(2, result.size());
        verify(employeeRepository).findAll();
    }

    @Test
    void testGetEmployeeById_found() {
        when(employeeRepository.findById("emp1")).thenReturn(Optional.of(testDriver));

        Optional<Employee> result = employeeService.getEmployeeById("emp1");

        assertTrue(result.isPresent());
        assertEquals("John", result.get().getFirstName());
    }

    @Test
    void testGetEmployeeById_notFound() {
        when(employeeRepository.findById("emp999")).thenReturn(Optional.empty());

        Optional<Employee> result = employeeService.getEmployeeById("emp999");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAvailableEmployees() {
        Employee unavailable = new Employee();
        unavailable.setId("emp3");
        unavailable.setAvailable(false);
        unavailable.setStatus(EmployeeStatus.IN_ROUTE);

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector, unavailable));

        List<Employee> result = employeeService.getAvailableEmployees();

        assertEquals(2, result.size());
    }

    @Test
    void testGetAvailableEmployeesByDepartment() {
        Department otherDept = new Department("dep2", "Other", 0.0, 0.0);
        Employee otherEmployee = new Employee();
        otherEmployee.setId("emp3");
        otherEmployee.setAvailable(true);
        otherEmployee.setStatus(EmployeeStatus.AVAILABLE);
        otherEmployee.setDepartment(otherDept);

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector, otherEmployee));

        List<Employee> result = employeeService.getAvailableEmployeesByDepartment("dep1");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.getDepartment().getId().equals("dep1")));
    }

    @Test
    void testAssignEmployeesToVehicle_success() {
        when(employeeRepository.findAllById(List.of("emp1", "emp2")))
            .thenReturn(List.of(testDriver, testCollector));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = employeeService.assignEmployeesToVehicle("v1", List.of("emp1", "emp2"));

        assertTrue(result);
        verify(employeeRepository, times(2)).save(any(Employee.class));
    }

    @Test
    void testAssignEmployeesToVehicle_lessThanTwoEmployees() {
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> employeeService.assignEmployeesToVehicle("v1", List.of("emp1")));

        assertTrue(ex.getMessage().contains("At least 2 employees"));
    }

    @Test
    void testAssignEmployeesToVehicle_noDriver() {
        Employee collector2 = new Employee();
        collector2.setId("emp3");
        collector2.setRole(Employee.EmployeeRole.COLLECTOR);
        collector2.setAvailable(true);
        collector2.setStatus(EmployeeStatus.AVAILABLE);

        when(employeeRepository.findAllById(List.of("emp2", "emp3")))
            .thenReturn(List.of(testCollector, collector2));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> employeeService.assignEmployeesToVehicle("v1", List.of("emp2", "emp3")));

        assertTrue(ex.getMessage().contains("DRIVER"));
    }

    @Test
    void testAssignEmployeesToVehicle_noCollector() {
        Employee driver2 = new Employee();
        driver2.setId("emp3");
        driver2.setRole(Employee.EmployeeRole.DRIVER);
        driver2.setAvailable(true);
        driver2.setStatus(EmployeeStatus.AVAILABLE);

        when(employeeRepository.findAllById(List.of("emp1", "emp3")))
            .thenReturn(List.of(testDriver, driver2));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> employeeService.assignEmployeesToVehicle("v1", List.of("emp1", "emp3")));

        assertTrue(ex.getMessage().contains("COLLECTOR"));
    }

    @Test
    void testAssignEmployeesToVehicle_employeeNotAvailable() {
        testDriver.setStatus(EmployeeStatus.IN_ROUTE);

        when(employeeRepository.findAllById(List.of("emp1", "emp2")))
            .thenReturn(List.of(testDriver, testCollector));

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> employeeService.assignEmployeesToVehicle("v1", List.of("emp1", "emp2")));

        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void testMarkEmployeesInRoute() {
        testDriver.setAssignedVehicleId("v1");
        testCollector.setAssignedVehicleId("v1");

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        employeeService.markEmployeesInRoute("v1");

        verify(employeeRepository, times(2)).save(any(Employee.class));
    }

    @Test
    void testReleaseEmployeesFromVehicle() {
        testDriver.setAssignedVehicleId("v1");
        testDriver.setStatus(EmployeeStatus.IN_ROUTE);
        testCollector.setAssignedVehicleId("v1");
        testCollector.setStatus(EmployeeStatus.IN_ROUTE);

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        employeeService.releaseEmployeesFromVehicle("v1");

        verify(employeeRepository, times(2)).save(argThat(emp -> 
            emp.getStatus() == EmployeeStatus.AVAILABLE && emp.getAssignedVehicleId() == null
        ));
    }

    @Test
    void testGetEmployeesByVehicle() {
        testDriver.setAssignedVehicleId("v1");
        testCollector.setAssignedVehicleId("v1");
        
        Employee other = new Employee();
        other.setId("emp3");
        other.setAssignedVehicleId("v2");

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector, other));

        List<Employee> result = employeeService.getEmployeesByVehicle("v1");

        assertEquals(2, result.size());
    }

    @Test
    void testVehicleHasRequiredEmployees_true() {
        testDriver.setAssignedVehicleId("v1");
        testDriver.setStatus(EmployeeStatus.ASSIGNED);
        testCollector.setAssignedVehicleId("v1");
        testCollector.setStatus(EmployeeStatus.ASSIGNED);

        when(employeeRepository.findAll()).thenReturn(List.of(testDriver, testCollector));

        boolean result = employeeService.vehicleHasRequiredEmployees("v1");

        assertTrue(result);
    }

    @Test
    void testVehicleHasRequiredEmployees_noDriver() {
        testCollector.setAssignedVehicleId("v1");
        testCollector.setStatus(EmployeeStatus.ASSIGNED);

        when(employeeRepository.findAll()).thenReturn(List.of(testCollector));

        boolean result = employeeService.vehicleHasRequiredEmployees("v1");

        assertFalse(result);
    }

    @Test
    void testSaveEmployee_withDepartment() {
        Employee newEmployee = new Employee();
        newEmployee.setFirstName("New");
        newEmployee.setLastName("Employee");
        newEmployee.setDepartment(testDepartment);

        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        Employee result = employeeService.saveEmployee(newEmployee);

        assertNotNull(result);
        assertEquals(EmployeeStatus.AVAILABLE, result.getStatus());
        assertEquals(Employee.EmployeeRole.COLLECTOR, result.getRole()); // Default role
        assertTrue(result.getAvailable());
    }

    @Test
    void testSaveEmployee_departmentNotFound() {
        Employee newEmployee = new Employee();
        newEmployee.setFirstName("New");
        Department fakeDept = new Department("fake", null, 0.0, 0.0);
        newEmployee.setDepartment(fakeDept);

        when(departmentRepository.findById("fake")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> employeeService.saveEmployee(newEmployee));
    }

    @Test
    void testUpdateEmployee_success() {
        Employee updated = new Employee();
        updated.setFirstName("Updated");
        updated.setLastName("Name");
        updated.setAvailable(false);
        updated.setRole(Employee.EmployeeRole.DRIVER);
        updated.setDepartment(testDepartment);

        when(employeeRepository.findById("emp1")).thenReturn(Optional.of(testDriver));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        Employee result = employeeService.updateEmployee("emp1", updated);

        assertEquals("Updated", result.getFirstName());
        assertEquals("Name", result.getLastName());
        assertFalse(result.getAvailable());
        assertEquals(Employee.EmployeeRole.DRIVER, result.getRole());
    }

    @Test
    void testUpdateEmployee_notFound() {
        when(employeeRepository.findById("emp999")).thenReturn(Optional.empty());

        Employee result = employeeService.updateEmployee("emp999", new Employee());

        assertNull(result);
    }

    @Test
    void testDeleteEmployee() {
        employeeService.deleteEmployee("emp1");
        verify(employeeRepository).deleteById("emp1");
    }
}
