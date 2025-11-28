package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
@Data
@Document(collection = "active_routes")
public class ActiveRoute {
    @Id
    private String id;
    private boolean rerouted = false;
    private boolean blockedByIncident = false; // ✅ NEW: Track if vehicle is blocked by incident

    private String vehicleId;
    private String departmentId;

    private List<RoutePoint> fullRoutePolyline;
    private List<BinStop> binStops;

    private RoutePoint currentPosition;
    private double animationProgress;
    private int currentBinIndex;

    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime lastUpdateTime;
    private LocalDateTime estimatedCompletionTime;
    private List<String> binIds;
    private int currentStopIndex;
    private double latitude;
    private double longitude;
    private double totalDistanceKm;
    private int totalBins;
    private int binsCollected;
    public List<RoutePoint> getRemainingRoutePoints() {
        if (fullRoutePolyline == null || fullRoutePolyline.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate current index based on animation progress
        int currentIndex = (int) (animationProgress * (fullRoutePolyline.size() - 1));

        if (currentIndex >= fullRoutePolyline.size()) {
            return Collections.emptyList();
        }

        return fullRoutePolyline.subList(currentIndex, fullRoutePolyline.size());
    }

    /**
     * Get remaining bin IDs (uncollected bins)
     */
    public List<String> getRemainingBinIds() {
        if (binStops == null || binStops.isEmpty()) {
            return Collections.emptyList();
        }

        // Get bins that haven't been collected yet
        return binStops.stream()
                .skip(currentBinIndex) // Skip already collected bins
                .filter(stop -> !"COLLECTED".equals(stop.getStatus()))
                .map(BinStop::getBinId)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get current latitude
     */
    public double getCurrentLatitude() {
        return currentPosition != null ? currentPosition.getLatitude() : this.latitude;
    }

    /**
     * Get current longitude (from currentPosition)
     */
    public double getCurrentLongitude() {
        return currentPosition != null ? currentPosition.getLongitude() : this.longitude;
    }

}
