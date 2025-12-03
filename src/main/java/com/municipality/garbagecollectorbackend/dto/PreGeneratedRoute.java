package com.municipality.garbagecollectorbackend.dto;

import com.municipality.garbagecollectorbackend.model.RoutePoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DTO representing a pre-generated route for a department.
 * Routes are generated ahead of time and can be assigned to vehicles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreGeneratedRoute {
    private String routeId;
    private String departmentId;
    private List<RouteBin> routeBins;
    private LocalDateTime generatedAt;
    private int binCount;
    private String assignedVehicleId;
    private LocalDateTime assignedAt;
    private List<RoutePoint> polyline;

    /**
     * Constructor for initial generation (without assignment)
     */
    public PreGeneratedRoute(String routeId, String departmentId, List<RouteBin> routeBins,
                             LocalDateTime generatedAt, int binCount, List<RoutePoint> polyline) {
        this.routeId = routeId;
        this.departmentId = departmentId;
        this.routeBins = routeBins;
        this.generatedAt = generatedAt;
        this.binCount = binCount;
        this.assignedVehicleId = null;
        this.assignedAt = null;
        this.polyline = polyline;
    }

    /**
     * Get the age of this route in minutes
     */
    public long getAgeInMinutes() {
        return ChronoUnit.MINUTES.between(generatedAt, LocalDateTime.now());
    }

    /**
     * Check if route is stale (older than 30 minutes)
     */
    public boolean isStale() {
        return getAgeInMinutes() > 30;
    }

    /**
     * Check if route is available (not assigned to any vehicle)
     */
    public boolean isAvailable() {
        return assignedVehicleId == null;
    }

    /**
     * Assign route to a vehicle
     */
    public void assignToVehicle(String vehicleId) {
        this.assignedVehicleId = vehicleId;
        this.assignedAt = LocalDateTime.now();
    }

    /**
     * Release route (when vehicle completes or fails)
     */
    public void release() {
        this.assignedVehicleId = null;
        this.assignedAt = null;
    }
}
