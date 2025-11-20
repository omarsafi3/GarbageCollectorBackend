package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Employee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
}