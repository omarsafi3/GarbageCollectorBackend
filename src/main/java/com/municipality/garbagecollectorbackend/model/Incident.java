package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "incidents")
public class Incident {
    @Id
    private String id;

    private IncidentType type;  // ✅ Changed from String to enum
    private IncidentStatus status;  // ✅ Changed from String to enum

    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    // For road blocks
    private Double latitude;
    private Double longitude;
    private Double radiusKm;

    // For bin overfills
    @DBRef
    private Bin bin;
}
