package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Data
@Document(collection = "active_routes")
public class ActiveRoute {
    @Id
    private String id;
    private boolean rerouted = false;
    private boolean blockedByIncident = false;

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
    
    // ✅ NEW: Track all incidents that have been avoided during this route to prevent loops
    private Set<String> avoidedIncidentIds = new HashSet<>();
    
    // ✅ NEW: Track reroute attempts to detect infinite loops
    private int rerouteAttempts = 0;
    private static final int MAX_REROUTE_ATTEMPTS = 5;
    
    // ✅ NEW: Track last reroute positions to detect loops
    private List<RerouteHistoryEntry> rerouteHistory = new ArrayList<>();
    
    /**
     * Add an incident ID to the avoided list
     */
    public void addAvoidedIncident(String incidentId) {
        if (avoidedIncidentIds == null) {
            avoidedIncidentIds = new HashSet<>();
        }
        avoidedIncidentIds.add(incidentId);
    }
    
    /**
     * Check if we've exceeded max reroute attempts
     */
    public boolean hasExceededRerouteLimit() {
        return rerouteAttempts >= MAX_REROUTE_ATTEMPTS;
    }
    
    /**
     * Record a reroute attempt
     */
    public void recordRerouteAttempt(double lat, double lng, String incidentId) {
        rerouteAttempts++;
        if (rerouteHistory == null) {
            rerouteHistory = new ArrayList<>();
        }
        rerouteHistory.add(new RerouteHistoryEntry(lat, lng, incidentId, LocalDateTime.now()));
    }
    
    /**
     * Check if vehicle is in a reroute loop (keeps hitting same incidents)
     */
    public boolean isInRerouteLoop() {
        if (rerouteHistory == null || rerouteHistory.size() < 3) {
            return false;
        }
        // Check if the same incident appears multiple times in recent history
        List<RerouteHistoryEntry> recent = rerouteHistory.subList(
                Math.max(0, rerouteHistory.size() - 4), 
                rerouteHistory.size()
        );
        Set<String> recentIncidents = new HashSet<>();
        for (RerouteHistoryEntry entry : recent) {
            if (entry.getIncidentId() != null && !recentIncidents.add(entry.getIncidentId())) {
                return true; // Same incident encountered twice in recent history = loop
            }
        }
        return false;
    }
    
    /**
     * Get all avoided incident IDs
     */
    public Set<String> getAvoidedIncidentIds() {
        return avoidedIncidentIds != null ? avoidedIncidentIds : new HashSet<>();
    }
    
    /**
     * Clear reroute history and reset attempts - used for rescue reroutes
     */
    public void clearRerouteHistory() {
        this.rerouteAttempts = 0;
        if (this.rerouteHistory != null) {
            this.rerouteHistory.clear();
        }
        // Note: We keep avoidedIncidentIds to still avoid known incidents
    }
    
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
