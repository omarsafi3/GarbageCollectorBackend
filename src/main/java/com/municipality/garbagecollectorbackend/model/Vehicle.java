package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vehicles")
public class Vehicle {
    public enum VehicleStatus {
        AVAILABLE,      // Ready for new route
        IN_ROUTE,       // Currently collecting bins
        RETURNING,      // Going back to depot
        UNLOADING       // Emptying at depot
    }


    @Id
    private String id ;
    private String reference;
    private String plate;
    private Double fillLevel;
    private Boolean available;
    private Department department;

    private VehicleStatus status = VehicleStatus.AVAILABLE;  // Default to AVAILABLE
    private LocalDateTime statusUpdatedAt;  // Track when status changed

}