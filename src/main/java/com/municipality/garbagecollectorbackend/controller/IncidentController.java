package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.model.IncidentType;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    @Autowired
    private IncidentService incidentService;

    @GetMapping
    public ResponseEntity<List<Incident>> getAllIncidents() {
        return ResponseEntity.ok(incidentService.getAllIncidents());
    }

    @GetMapping("/active")
    public ResponseEntity<List<Incident>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @PostMapping
    public ResponseEntity<Incident> createIncident(@RequestBody Incident incident) {
        Incident created = incidentService.createIncident(incident);
        return ResponseEntity.ok(created);
    }
    @PostMapping("/road-block")
    public ResponseEntity<Incident> reportRoadBlock(@RequestBody RoadBlockRequest request) {
        log.info("🚨 Received road block report at ({}, {}) with radius {}km",
                request.getLatitude(), request.getLongitude(),
                request.getRadiusKm() != null ? request.getRadiusKm() : 0.5);

        // ✅ Call the service method that triggers rerouting!
        Incident incident = incidentService.reportRoadBlock(
                request.getLatitude(),
                request.getLongitude(),
                request.getRadiusKm() != null ? request.getRadiusKm() : 0.5,
                request.getDescription()
        );

        return ResponseEntity.ok(incident);
    }

    // Request DTO
    @Data
    public static class RoadBlockRequest {
        private double latitude;
        private double longitude;
        private Double radiusKm;
        private String description;
    }


    @PostMapping("/{id}/resolve")
    public ResponseEntity<Incident> resolveIncident(@PathVariable String id) {
        Incident resolved = incidentService.resolveIncident(id);
        if (resolved == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resolved);
    }
}
