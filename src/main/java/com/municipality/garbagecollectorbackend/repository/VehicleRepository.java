package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {
    List<Vehicle> findByDepartmentId(String departmentId);
}
