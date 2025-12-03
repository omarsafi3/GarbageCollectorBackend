package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for truck position updates.
 * Used for real-time tracking of vehicle positions on the map.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TruckPositionUpdate {
    private String vehicleId;
    private double latitude;
    private double longitude;
    private double progressPercent;
}
