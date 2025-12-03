package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.model.IncidentType;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Incidents", description = "Incident reporting and management endpoints")
@Slf4j
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    @Autowired
    private IncidentService incidentService;

    @Operation(summary = "Get all incidents", description = "Retrieves a list of all incidents")
    @ApiResponse(responseCode = "200", description = "List of all incidents")
    @GetMapping
    public ResponseEntity<List<Incident>> getAllIncidents() {
        return ResponseEntity.ok(incidentService.getAllIncidents());
    }

    @Operation(summary = "Get active incidents", description = "Retrieves only active (unresolved) incidents")
    @ApiResponse(responseCode = "200", description = "List of active incidents")
    @GetMapping("/active")
    public ResponseEntity<List<Incident>> getActiveIncidents() {
        return ResponseEntity.ok(incidentService.getActiveIncidents());
    }

    @Operation(summary = "Create incident", description = "Creates a new incident report")
    @ApiResponse(responseCode = "200", description = "Incident created successfully")
    @PostMapping
    public ResponseEntity<Incident> createIncident(@RequestBody Incident incident) {
        Incident created = incidentService.createIncident(incident);
        return ResponseEntity.ok(created);
    }
    @Operation(summary = "Report road block", description = "Reports a road block incident that may affect route optimization")
    @ApiResponse(responseCode = "200", description = "Road block reported and routes recalculated")
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


    @Operation(summary = "Resolve incident", description = "Marks an incident as resolved")
    @ApiResponse(responseCode = "200", description = "Incident resolved successfully")
    @ApiResponse(responseCode = "404", description = "Incident not found")
    @PostMapping("/{id}/resolve")
    public ResponseEntity<Incident> resolveIncident(@PathVariable String id) {
        Incident resolved = incidentService.resolveIncident(id);
        if (resolved == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resolved);
    }
}
