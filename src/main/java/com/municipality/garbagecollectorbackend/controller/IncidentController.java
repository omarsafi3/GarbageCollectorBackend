package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/incidents")
public class IncidentController {

    @Autowired
    private IncidentService incidentService;

    @GetMapping
    public List<Incident> getAllIncidents() {
        return incidentService.getAllIncidents();
    }

    @GetMapping("/active")
    public List<Incident> getActiveIncidents() {
        return incidentService.getActiveIncidents();
    }

    @PostMapping("/{id}/resolve")
    public Incident resolveIncident(@PathVariable String id) {
        return incidentService.resolveIncident(id);
    }
}
