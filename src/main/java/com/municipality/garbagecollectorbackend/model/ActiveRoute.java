package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;
@Data
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
    private LocalDateTime endTime;
    private LocalDateTime lastUpdateTime;
    private LocalDateTime estimatedCompletionTime;

    private double totalDistanceKm;
    private int totalBins;
    private int binsCollected;

    // Getters and setters

}
