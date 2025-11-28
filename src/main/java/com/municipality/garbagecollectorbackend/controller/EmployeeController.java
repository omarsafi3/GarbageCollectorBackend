package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @GetMapping
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable String id) {
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/available")
    public List<Employee> getAvailableEmployees() {
        return employeeService.getAvailableEmployees();
    }

    // ✅ NEW: Get available employees by department
    @GetMapping("/available/department/{departmentId}")
    public List<Employee> getAvailableEmployeesByDepartment(@PathVariable String departmentId) {
        return employeeService.getAvailableEmployeesByDepartment(departmentId);
    }

    // ✅ NEW: Get employees assigned to a vehicle
    @GetMapping("/vehicle/{vehicleId}")
    public List<Employee> getEmployeesByVehicle(@PathVariable String vehicleId) {
        return employeeService.getEmployeesByVehicle(vehicleId);
    }

    // ✅ NEW: Assign employees to vehicle
    @PostMapping("/assign/{vehicleId}")
    public ResponseEntity<?> assignEmployeesToVehicle(
            @PathVariable String vehicleId,
            @RequestBody Map<String, List<String>> request) {
        try {
            List<String> employeeIds = request.get("employeeIds");
            if (employeeIds == null || employeeIds.size() != 2) {
                return ResponseEntity.badRequest().body("Exactly 2 employee IDs required");
            }

            boolean success = employeeService.assignEmployeesToVehicle(vehicleId, employeeIds);

            if (success) {
                List<Employee> assigned = employeeService.getEmployeesByVehicle(vehicleId);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "2 employees assigned to vehicle " + vehicleId,
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

    // ✅ NEW: Release employees from vehicle
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

    // ✅ NEW: Check if vehicle has required employees
    @GetMapping("/check-required/{vehicleId}")
    public ResponseEntity<?> checkRequiredEmployees(@PathVariable String vehicleId) {
        boolean hasRequired = employeeService.vehicleHasRequiredEmployees(vehicleId);
        return ResponseEntity.ok(Map.of(
                "vehicleId", vehicleId,
                "hasRequiredEmployees", hasRequired,
                "assignedEmployees", employeeService.getEmployeesByVehicle(vehicleId)
        ));
    }

    @PostMapping
    public ResponseEntity<?> createEmployee(@RequestBody Employee employee) {
        try {
            Employee saved = employeeService.saveEmployee(employee);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable String id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
