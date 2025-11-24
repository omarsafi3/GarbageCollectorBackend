package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.RouteHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RouteHistoryRepository extends MongoRepository<RouteHistory, String> {

    List<RouteHistory> findByDepartmentId(String departmentId);

    List<RouteHistory> findByVehicleId(String vehicleId);

    List<RouteHistory> findByDepartmentIdAndStartTimeBetween(
            String departmentId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<RouteHistory> findByDepartmentIdOrderByStartTimeDesc(String departmentId);
}
