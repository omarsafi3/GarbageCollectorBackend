package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Employees", description = "Employee management endpoints")
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Operation(summary = "Get all employees", description = "Retrieves a list of all employees")
    @ApiResponse(responseCode = "200", description = "List of all employees")
    @GetMapping
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @Operation(summary = "Get employee by ID", description = "Retrieves an employee by their unique ID")
    @ApiResponse(responseCode = "200", description = "Employee found")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable String id) {
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get available employees", description = "Retrieves employees that are not currently assigned to a vehicle")
    @ApiResponse(responseCode = "200", description = "List of available employees")
    @GetMapping("/available")
    public List<Employee> getAvailableEmployees() {
        return employeeService.getAvailableEmployees();
    }

    @Operation(summary = "Get available employees by department", description = "Retrieves available employees for a specific department")
    @ApiResponse(responseCode = "200", description = "List of available employees in the department")
    @GetMapping("/available/department/{departmentId}")
    public List<Employee> getAvailableEmployeesByDepartment(@PathVariable String departmentId) {
        return employeeService.getAvailableEmployeesByDepartment(departmentId);
    }

    @Operation(summary = "Get employees by vehicle", description = "Retrieves employees assigned to a specific vehicle")
    @ApiResponse(responseCode = "200", description = "List of employees assigned to the vehicle")
    @GetMapping("/vehicle/{vehicleId}")
    public List<Employee> getEmployeesByVehicle(@PathVariable String vehicleId) {
        return employeeService.getEmployeesByVehicle(vehicleId);
    }

    @Operation(summary = "Assign employees to vehicle", description = "Assigns a list of employees (driver + collectors) to a vehicle")
    @ApiResponse(responseCode = "200", description = "Employees assigned successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or assignment failed")
    @PostMapping("/assign/{vehicleId}")
    public ResponseEntity<?> assignEmployeesToVehicle(
            @PathVariable String vehicleId,
            @RequestBody Map<String, List<String>> request) {
        try {
            List<String> employeeIds = request.get("employeeIds");
            if (employeeIds == null || employeeIds.size() < 2) {
                return ResponseEntity.badRequest().body("At least 2 employee IDs required (1 driver + 1 collector)");
            }

            boolean success = employeeService.assignEmployeesToVehicle(vehicleId, employeeIds);

            if (success) {
                List<Employee> assigned = employeeService.getEmployeesByVehicle(vehicleId);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", assigned.size() + " employees assigned to vehicle " + vehicleId,
                        "employees", assigned
                ));
            } else {
                return ResponseEntity.badRequest().body("Failed to assign employees");
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Release employees from vehicle", description = "Releases all employees from a vehicle, making them available again")
    @ApiResponse(responseCode = "200", description = "Employees released successfully")
    @ApiResponse(responseCode = "400", description = "Release failed")
    @PostMapping("/release/{vehicleId}")
    public ResponseEntity<?> releaseEmployeesFromVehicle(@PathVariable String vehicleId) {
        try {
            employeeService.releaseEmployeesFromVehicle(vehicleId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Employees released from vehicle " + vehicleId
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Check required employees", description = "Checks if a vehicle has the required employees (driver + collector) assigned")
    @ApiResponse(responseCode = "200", description = "Check result returned")
    @GetMapping("/check-required/{vehicleId}")
    public ResponseEntity<?> checkRequiredEmployees(@PathVariable String vehicleId) {
        boolean hasRequired = employeeService.vehicleHasRequiredEmployees(vehicleId);
        return ResponseEntity.ok(Map.of(
                "vehicleId", vehicleId,
                "hasRequiredEmployees", hasRequired,
                "assignedEmployees", employeeService.getEmployeesByVehicle(vehicleId)
        ));
    }

    @Operation(summary = "Create employee", description = "Creates a new employee")
    @ApiResponse(responseCode = "200", description = "Employee created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid employee data")
    @PostMapping
    public ResponseEntity<?> createEmployee(@RequestBody Employee employee) {
        try {
            Employee saved = employeeService.saveEmployee(employee);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Update employee", description = "Updates an existing employee by ID")
    @ApiResponse(responseCode = "200", description = "Employee updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid employee data")
    @ApiResponse(responseCode = "404", description = "Employee not found")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable String id, @RequestBody Employee employee) {
        try {
            Employee updated = employeeService.updateEmployee(id, employee);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Delete employee", description = "Deletes an employee by ID")
    @ApiResponse(responseCode = "204", description = "Employee deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable String id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
