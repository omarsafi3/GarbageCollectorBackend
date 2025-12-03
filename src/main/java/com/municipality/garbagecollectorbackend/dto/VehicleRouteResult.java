package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing the result of route optimization for a vehicle.
 * Contains the vehicle ID and the ordered list of bin IDs for collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRouteResult {
    private String vehicleId;
    private List<String> orderedBinIds;
}
