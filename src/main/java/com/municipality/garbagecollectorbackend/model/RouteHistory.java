package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "route_history")
@CompoundIndexes({
    @CompoundIndex(name = "dept_time_idx", def = "{'departmentId': 1, 'startTime': -1}"),
    @CompoundIndex(name = "vehicle_time_idx", def = "{'vehicleId': 1, 'startTime': -1}")
})
public class RouteHistory {

    @Id
    private String id;

    @Indexed
    private String vehicleId;
    private String vehicleReference;
    
    @Indexed
    private String departmentId;
    private String departmentName;

    // Route Info
    @Indexed
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMinutes;

    // Performance Metrics
    private int totalBins;
    private int binsCollected;
    private double totalDistanceKm;
    private double averageSpeed; // km/h

    // Environmental Impact
    private double co2EmissionsKg;
    private double fuelConsumedLiters;

    // Bin Details
    private List<BinCollectionDetail> binDetails;

    // Cost
    private double estimatedCost;

    // Status
    private String completionStatus; // COMPLETED, CANCELLED, PARTIAL

    // ✅ FIXED: Added @Data annotation to nested class
    @Data
    public static class BinCollectionDetail {
        private String binId;
        private double latitude;
        private double longitude;
        private int fillLevelBefore;
        private LocalDateTime collectionTime;
    }
}
