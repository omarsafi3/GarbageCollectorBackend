package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for route progress updates.
 * Sent to track the current status of a vehicle on its route.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteProgressUpdate {
    private String vehicleId;
    private int currentStop;
    private int totalStops;
    private String currentBinId;
    private double vehicleFillLevel;
}
