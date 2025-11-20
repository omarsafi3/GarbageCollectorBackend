package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    public List<Employee> getAvailableEmployees() {
        return employeeRepository.findAll()
                .stream()
                .filter(Employee::getAvailable)
                .toList();
    }

    public Employee saveEmployee(Employee employee) {
        if (employee.getDepartment() == null ||
                !departmentRepository.existsById(employee.getDepartment().getId())) {
            throw new RuntimeException("Department does not exist!");
        }
        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(String id, Employee updated) {
        return employeeRepository.findById(id).map(existing -> {

            if (updated.getDepartment() != null &&
                    !departmentRepository.existsById(updated.getDepartment().getId())) {
                throw new RuntimeException("Department does not exist!");
            }

            existing.setFirstName(updated.getFirstName());
            existing.setLastName(updated.getLastName());
            existing.setAvailable(updated.getAvailable());
            existing.setDepartment(updated.getDepartment());

            return employeeRepository.save(existing);
        }).orElse(null);
    }


    public void deleteEmployee(String id) {
        employeeRepository.deleteById(id);
    }

}