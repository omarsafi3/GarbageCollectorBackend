package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.RouteHistory;
import com.municipality.garbagecollectorbackend.model.RoutePoint;
import com.municipality.garbagecollectorbackend.repository.RouteHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Tag(name = "Route History & Replay", description = "Endpoints for historical route auditing, replay scrubber, and timeline telemetry")
@RestController
@RequestMapping("/api/analytics/routes/history")
@RequiredArgsConstructor
public class RouteHistoryController {

    private final RouteHistoryRepository routeHistoryRepository;

    @Operation(summary = "Get historical routes", description = "Retrieves completed routes for timeline replay and auditing")
    @GetMapping
    public List<RouteHistory> getHistoricalRoutes(@RequestParam(required = false) String departmentId) {
        if (departmentId != null && !departmentId.isEmpty()) {
            return routeHistoryRepository.findByDepartmentId(departmentId);
        }
        return routeHistoryRepository.findAll();
    }

    @Operation(summary = "Get historical route by ID", description = "Retrieves full trajectory and stop timestamps for a single route")
    @GetMapping("/{id}")
    public ResponseEntity<RouteHistory> getRouteHistoryById(@PathVariable String id) {
        return routeHistoryRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Seed demo route history", description = "Seeds realistic historical routes with timestamps for instant replay testing")
    @PostMapping("/seed-demo")
    public ResponseEntity<?> seedDemoRouteHistory(@RequestParam(defaultValue = "6a89b334a6bff2d79ad70629") String departmentId) {
        LocalDateTime now = LocalDateTime.now().minusHours(2);
        
        RouteHistory demo = new RouteHistory();
        demo.setVehicleId("6a89b334a6bff2d79ad7062a");
        demo.setVehicleReference("TUNIS-TRUCK-01");
        demo.setDepartmentId(departmentId);
        demo.setDepartmentName("Tunis Medina Central Hub");
        demo.setStartTime(now);
        demo.setEndTime(now.plusMinutes(42));
        demo.setDurationMinutes(42);
        demo.setTotalBins(5);
        demo.setBinsCollected(5);
        demo.setTotalDistanceKm(8.4);
        demo.setAverageSpeed(22.5);
        demo.setCo2EmissionsKg(2.18);
        demo.setFuelConsumedLiters(1.9);
        demo.setCompletionStatus("COMPLETED");

        List<RouteHistory.BinCollectionDetail> binDetails = new ArrayList<>();
        List<RoutePoint> polyline = List.of(
                new RoutePoint(36.8000, 10.1800, 0),
                new RoutePoint(36.8005, 10.1860, 1),
                new RoutePoint(36.8040, 10.1810, 2),
                new RoutePoint(36.8090, 10.1825, 3),
                new RoutePoint(36.8060, 10.1790, 4),
                new RoutePoint(36.7975, 10.1805, 5),
                new RoutePoint(36.8000, 10.1800, 6)
        );

        for (int i = 1; i <= 5; i++) {
            RouteHistory.BinCollectionDetail b = new RouteHistory.BinCollectionDetail();
            b.setBinId("demo-bin-" + i);
            b.setLatitude(polyline.get(i).getLatitude());
            b.setLongitude(polyline.get(i).getLongitude());
            b.setFillLevelBefore(85 + (i * 3));
            b.setCollectionTime(now.plusMinutes(i * 7));
            binDetails.add(b);
        }

        demo.setBinDetails(binDetails);
        demo.setFullRoutePolyline(polyline);

        RouteHistory saved = routeHistoryRepository.save(demo);
        return ResponseEntity.ok(saved);
    }
}
