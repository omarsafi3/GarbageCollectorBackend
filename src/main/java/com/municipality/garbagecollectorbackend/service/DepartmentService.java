package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {

    @Autowired
    public DepartmentRepository departmentRepository;

    @Cacheable(value = "departments", key = "'all'")
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Cacheable(value = "department", key = "#id")
    public Optional<Department> getDepartmentById(String id) {
        return departmentRepository.findById(id);
    }

    @Caching(evict = {
        @CacheEvict(value = "departments", allEntries = true),
        @CacheEvict(value = "department", key = "#result.id", condition = "#result != null")
    })
    public Department saveDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Caching(evict = {
        @CacheEvict(value = "departments", allEntries = true),
        @CacheEvict(value = "department", key = "#id")
    })
    public Department updateDepartment(String id, Department updated) {
        return departmentRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setLatitude(updated.getLatitude());
            existing.setLongitude(updated.getLongitude());
            return departmentRepository.save(existing);
        }).orElse(null);
    }

    @Caching(evict = {
        @CacheEvict(value = "departments", allEntries = true),
        @CacheEvict(value = "department", key = "#id")
    })
    public void deleteDepartment(String id) {
        departmentRepository.deleteById(id);
    }
}