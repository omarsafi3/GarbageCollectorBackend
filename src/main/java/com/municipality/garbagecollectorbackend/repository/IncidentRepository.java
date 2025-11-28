package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.model.IncidentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends MongoRepository<Incident, String> {

    List<Incident> findByStatus(IncidentStatus status);  // ✅ Changed parameter type

    List<Incident> findByBin_IdAndStatus(String binId, IncidentStatus status);  // ✅ Changed parameter type
}
