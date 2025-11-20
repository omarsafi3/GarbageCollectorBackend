package com.municipality.garbagecollectorbackend.service;


import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class IncidentService {

    @Autowired
    public IncidentRepository incidentRepository;

    @Autowired
    public IncidentUpdatePublisher publisher;

    public Incident createIncident(Incident incident) {
        incident.setCreatedAt(LocalDateTime.now());
        Incident saved = incidentRepository.save(incident);
        publisher.publishIncidentUpdate(saved);
        return saved;
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    public Incident resolveIncident(String id) {
        return incidentRepository.findById(id)
                .map(i -> {
                    i.setStatus("resolved");
                    i.setResolvedAt(LocalDateTime.now());
                    Incident updated = incidentRepository.save(i);
                    publisher.publishIncidentUpdate(updated);
                    return updated;
                })
                .orElse(null);
    }
    public boolean hasActiveOverflowIncidentForBin(String binId) {
        return incidentRepository.findByBin_IdAndStatus(binId, "active")
                .stream()
                .anyMatch(incident -> "overflow".equals(incident.getType()));
    }
    public List<Incident> getActiveIncidents() {
        return incidentRepository.findByStatus("active");
    }
}
