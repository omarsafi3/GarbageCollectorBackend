package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @GetMapping
    public List<Department> getAllDepartments() {
        return departmentService.getAllDepartments();
    }

    @GetMapping("/{id}")
    public Optional<Department> getDepartmentById(@PathVariable String id) {
        return departmentService.getDepartmentById(id);
    }

    @PostMapping
    public Department createDepartment(@RequestBody Department department) {
        return departmentService.saveDepartment(department);
    }

    @PutMapping("/{id}")
    public Department updateDepartment(@PathVariable String id, @RequestBody Department department) {
        return departmentService.updateDepartment(id, department);
    }

    @DeleteMapping("/{id}")
    public void deleteDepartment(@PathVariable String id) {
        departmentService.deleteDepartment(id);
    }
    @GetMapping("/{id}/employees")
    public List<Employee> getDepartmentEmployees(@PathVariable String id) {
        return employeeRepository.findByDepartmentId(id);
    }

    @GetMapping("/{id}/vehicles")
    public List<Vehicle> getDepartmentVehicles(@PathVariable String id) {
        return vehicleRepository.findByDepartmentId(id);
    }
}
