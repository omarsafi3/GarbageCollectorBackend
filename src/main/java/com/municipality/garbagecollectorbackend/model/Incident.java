package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "incidents")
@CompoundIndexes({
    @CompoundIndex(name = "type_status_idx", def = "{'type': 1, 'status': 1}"),
    @CompoundIndex(name = "location_idx", def = "{'latitude': 1, 'longitude': 1}")
})
public class Incident implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    private String id;

    @Indexed
    private IncidentType type;  // ✅ Changed from String to enum
    
    @Indexed
    private IncidentStatus status;  // ✅ Changed from String to enum

    private String description;
    
    @Indexed
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
