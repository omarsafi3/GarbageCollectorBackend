package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Incident;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends MongoRepository<Incident, String> {
    List<Incident> findByBin_IdAndStatus(String binId, String status);
    List<Incident> findByStatus(String status);
}
