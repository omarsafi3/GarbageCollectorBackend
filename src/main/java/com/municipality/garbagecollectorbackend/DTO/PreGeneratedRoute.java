package com.municipality.garbagecollectorbackend.DTO;

import com.municipality.garbagecollectorbackend.model.RoutePoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreGeneratedRoute {
    private String routeId;  // ✅ Unique route identifier
    private String departmentId;
    private List<RouteBin> routeBins;
    private LocalDateTime generatedAt;
    private int binCount;

    // ✅ NEW: Assignment tracking
    private String assignedVehicleId;  // null = available, otherwise = claimed
    private LocalDateTime assignedAt;
    private List<RoutePoint> polyline;

    // ✅ Constructor for initial generation (without assignment)
    public PreGeneratedRoute(String routeId, String departmentId, List<RouteBin> routeBins,
                             LocalDateTime generatedAt, int binCount, List<RoutePoint> polyline) {
        this.routeId = routeId;
        this.departmentId = departmentId;
        this.routeBins = routeBins;
        this.generatedAt = generatedAt;
        this.binCount = binCount;
        this.assignedVehicleId = null;  // Initially unassigned
        this.assignedAt = null;
        this.polyline = polyline;
    }

    // ✅ Helper method for route age
    public long getAgeInMinutes() {
        return ChronoUnit.MINUTES.between(generatedAt, LocalDateTime.now());
    }

    // ✅ Check if route is stale (older than 30 minutes)
    public boolean isStale() {
        return getAgeInMinutes() > 30;
    }

    // ✅ NEW: Check if route is available (not assigned to any vehicle)
    public boolean isAvailable() {
        return assignedVehicleId == null;
    }

    // ✅ NEW: Assign route to a vehicle
    public void assignToVehicle(String vehicleId) {
        this.assignedVehicleId = vehicleId;
        this.assignedAt = LocalDateTime.now();
    }

    // ✅ NEW: Release route (when vehicle completes or fails)
    public void release() {
        this.assignedVehicleId = null;
        this.assignedAt = null;
    }
    public List<RoutePoint> getPolyline() {
        return polyline;
    }
}
