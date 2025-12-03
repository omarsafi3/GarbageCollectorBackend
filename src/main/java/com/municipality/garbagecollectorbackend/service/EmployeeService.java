package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.EmployeeStatus;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    @Autowired
    public EmployeeRepository employeeRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(String id) {
        return employeeRepository.findById(id);
    }

    // ✅ UPDATED: Filter by status as well
    public List<Employee> getAvailableEmployees() {
        return employeeRepository.findAll()
                .stream()
                .filter(e -> e.getAvailable() &&
                        (e.getStatus() == null || e.getStatus() == EmployeeStatus.AVAILABLE))
                .collect(Collectors.toList());
    }

    // ✅ NEW: Get available employees by department
    public List<Employee> getAvailableEmployeesByDepartment(String departmentId) {
        return employeeRepository.findAll()
                .stream()
                .filter(e -> e.getDepartment() != null &&
                        e.getDepartment().getId().equals(departmentId) &&
                        e.getAvailable() &&
                        (e.getStatus() == null || e.getStatus() == EmployeeStatus.AVAILABLE))
                .collect(Collectors.toList());
    }

    /**
     * Get all employees for a specific department
     * @param departmentId the department ID
     * @return list of employees in the department
     */
    public List<Employee> getEmployeesByDepartment(String departmentId) {
        return employeeRepository.findAll()
                .stream()
                .filter(e -> e.getDepartment() != null &&
                        departmentId.equals(e.getDepartment().getId()))
                .collect(Collectors.toList());
    }

    // ✅ NEW: Assign employees to vehicle
    public boolean assignEmployeesToVehicle(String vehicleId, List<String> employeeIds) {
        if (employeeIds.size() < 2) {
            throw new RuntimeException("At least 2 employees must be assigned to a vehicle");
        }

        List<Employee> employees = employeeRepository.findAllById(employeeIds);

        if (employees.size() < 2) {
            throw new RuntimeException("Could not find all employees");
        }

        // Check if all employees are available
        for (Employee emp : employees) {
            if (!emp.canBeAssigned()) {
                throw new RuntimeException("Employee " + emp.getFullName() + " is not available");
            }
        }

        // ✅ Validate: Must have at least 1 driver and 1 collector
        long driverCount = employees.stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.DRIVER)
                .count();
        long collectorCount = employees.stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.COLLECTOR)
                .count();

        if (driverCount < 1) {
            throw new RuntimeException("At least 1 DRIVER is required to dispatch a vehicle");
        }
        if (collectorCount < 1) {
            throw new RuntimeException("At least 1 COLLECTOR is required to dispatch a vehicle");
        }

        // Assign employees
        for (Employee emp : employees) {
            emp.setStatus(EmployeeStatus.ASSIGNED);
            emp.setAssignedVehicleId(vehicleId);
            employeeRepository.save(emp);
        }

        System.out.println("✅ Assigned " + employees.size() + " employees (drivers: " + driverCount + ", collectors: " + collectorCount + ") to vehicle " + vehicleId);
        return true;
    }

    // ✅ NEW: Update employee status when vehicle starts route
    public void markEmployeesInRoute(String vehicleId) {
        List<Employee> employees = employeeRepository.findAll()
                .stream()
                .filter(e -> vehicleId.equals(e.getAssignedVehicleId()))
                .collect(Collectors.toList());

        for (Employee emp : employees) {
            emp.setStatus(EmployeeStatus.IN_ROUTE);
            employeeRepository.save(emp);
        }

        System.out.println("🚛 " + employees.size() + " employees marked IN_ROUTE for vehicle " + vehicleId);
    }

    // ✅ NEW: Release employees when vehicle completes route
    public void releaseEmployeesFromVehicle(String vehicleId) {
        List<Employee> employees = employeeRepository.findAll()
                .stream()
                .filter(e -> vehicleId.equals(e.getAssignedVehicleId()))
                .collect(Collectors.toList());

        for (Employee emp : employees) {
            emp.setStatus(EmployeeStatus.AVAILABLE);
            emp.setAssignedVehicleId(null);
            employeeRepository.save(emp);
        }

        System.out.println("✅ Released " + employees.size() + " employees from vehicle " + vehicleId);
    }

    // ✅ NEW: Get employees assigned to a vehicle
    public List<Employee> getEmployeesByVehicle(String vehicleId) {
        return employeeRepository.findAll()
                .stream()
                .filter(e -> vehicleId.equals(e.getAssignedVehicleId()))
                .collect(Collectors.toList());
    }

    // ✅ NEW: Check if vehicle has required employees (at least 1 driver + 1 collector)
    public boolean vehicleHasRequiredEmployees(String vehicleId) {
        List<Employee> assigned = employeeRepository.findAll()
                .stream()
                .filter(e -> vehicleId.equals(e.getAssignedVehicleId()) &&
                        e.getStatus() == EmployeeStatus.ASSIGNED)
                .collect(Collectors.toList());

        long driverCount = assigned.stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.DRIVER)
                .count();
        long collectorCount = assigned.stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.COLLECTOR)
                .count();

        return driverCount >= 1 && collectorCount >= 1;
    }

    public Employee saveEmployee(Employee employee) {
        if (employee.getDepartment() != null && employee.getDepartment().getId() != null) {
            Department dep = departmentRepository.findById(employee.getDepartment().getId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(dep);
        }

        // ✅ Initialize status if null
        if (employee.getStatus() == null) {
            employee.setStatus(EmployeeStatus.AVAILABLE);
        }

        // ✅ Default role to COLLECTOR if not set
        if (employee.getRole() == null) {
            employee.setRole(Employee.EmployeeRole.COLLECTOR);
        }

        // ✅ Default available to true if not set
        if (employee.getAvailable() == null) {
            employee.setAvailable(true);
        }

        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(String id, Employee updatedEmployee) {
        Employee existing = employeeRepository.findById(id).orElse(null);
        if (existing == null) return null;

        existing.setFirstName(updatedEmployee.getFirstName());
        existing.setLastName(updatedEmployee.getLastName());
        existing.setAvailable(updatedEmployee.getAvailable());
        existing.setRole(updatedEmployee.getRole());

        if (updatedEmployee.getDepartment() != null && updatedEmployee.getDepartment().getId() != null) {
            Department dep = departmentRepository.findById(updatedEmployee.getDepartment().getId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            existing.setDepartment(dep);
        }

        return employeeRepository.save(existing);
    }

    public void deleteEmployee(String id) {
        employeeRepository.deleteById(id);
    }
}
