package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.ActiveRoute;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ActiveRouteRepository extends MongoRepository<ActiveRoute, String> {

    @Query("{ 'vehicleId': ?0, 'status': 'IN_PROGRESS' }")
    Optional<ActiveRoute> findByVehicleId(String vehicleId);

    List<ActiveRoute> findByStatus(String status);
}