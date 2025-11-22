package com.municipality.garbagecollectorbackend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "active_routes")
public class ActiveRoute {
    @Id
    private String id;

    private String vehicleId;
    private String departmentId;

    private List<RoutePoint> fullRoutePolyline;
    private List<BinStop> binStops;

    private RoutePoint currentPosition;
    private double animationProgress;
    private int currentBinIndex;

    private String status;
    private LocalDateTime startTime;
    private LocalDateTime lastUpdateTime;
    private LocalDateTime estimatedCompletionTime;

    private double totalDistanceKm;
    private int totalBins;
    private int binsCollected;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }

    public List<RoutePoint> getFullRoutePolyline() { return fullRoutePolyline; }
    public void setFullRoutePolyline(List<RoutePoint> fullRoutePolyline) { this.fullRoutePolyline = fullRoutePolyline; }

    public List<BinStop> getBinStops() { return binStops; }
    public void setBinStops(List<BinStop> binStops) { this.binStops = binStops; }

    public RoutePoint getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(RoutePoint currentPosition) { this.currentPosition = currentPosition; }

    public double getAnimationProgress() { return animationProgress; }
    public void setAnimationProgress(double animationProgress) { this.animationProgress = animationProgress; }

    public int getCurrentBinIndex() { return currentBinIndex; }
    public void setCurrentBinIndex(int currentBinIndex) { this.currentBinIndex = currentBinIndex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }

    public LocalDateTime getEstimatedCompletionTime() { return estimatedCompletionTime; }
    public void setEstimatedCompletionTime(LocalDateTime estimatedCompletionTime) { this.estimatedCompletionTime = estimatedCompletionTime; }

    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public int getTotalBins() { return totalBins; }
    public void setTotalBins(int totalBins) { this.totalBins = totalBins; }

    public int getBinsCollected() { return binsCollected; }
    public void setBinsCollected(int binsCollected) { this.binsCollected = binsCollected; }
}
