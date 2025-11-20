package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Vehicle;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VehicleRepository extends MongoRepository<Vehicle, String> {}
