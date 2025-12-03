package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/departments")
@Tag(name = "Departments", description = "Department management endpoints")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Operation(summary = "Get all departments", description = "Retrieve a list of all departments")
    @GetMapping
    public List<Department> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @Operation(summary = "Get department by ID", description = "Retrieve a single department by its ID")
    @GetMapping("/{id}")
    public Optional<Department> getDepartmentById(@Parameter(description = "Department ID") @PathVariable String id) {
        return departmentService.getDepartmentById(id);
    }

    @Operation(summary = "Create a new department", description = "Add a new department to the system")
    @ApiResponse(responseCode = "200", description = "Department created successfully")
    @PostMapping
    public Department createDepartment(@RequestBody Department department) {
        return departmentService.saveDepartment(department);
    }

    @Operation(summary = "Update a department", description = "Update an existing department's information")
    @PutMapping("/{id}")
    public Department updateDepartment(@PathVariable String id, @RequestBody Department department) {
        return departmentService.updateDepartment(id, department);
    }

    @Operation(summary = "Delete a department", description = "Remove a department from the system")
    @DeleteMapping("/{id}")
    public void deleteDepartment(@PathVariable String id) {
        departmentService.deleteDepartment(id);
    }

    @Operation(summary = "Get department employees", description = "Retrieve all employees belonging to a department")
    @GetMapping("/{id}/employees")
    public List<Employee> getDepartmentEmployees(@PathVariable String id) {
        return employeeRepository.findByDepartmentId(id);
    }

    @Operation(summary = "Get department vehicles", description = "Retrieve all vehicles belonging to a department")
    @GetMapping("/{id}/vehicles")
    public List<Vehicle> getDepartmentVehicles(@PathVariable String id) {
        return vehicleRepository.findByDepartmentId(id);
    }
}
