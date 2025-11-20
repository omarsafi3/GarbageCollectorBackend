package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {

    @Autowired
    public DepartmentRepository departmentRepository;

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Optional<Department> getDepartmentById(String id) {
        return departmentRepository.findById(id);
    }

    public Department saveDepartment(Department department) {
        return departmentRepository.save(department);
    }

    public Department updateDepartment(String id, Department updated) {
        return departmentRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());
            return departmentRepository.save(existing);
        }).orElse(null);
    }

    public void deleteDepartment(String id) {
        departmentRepository.deleteById(id);
    }
}