package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.RouteHistory;
import com.municipality.garbagecollectorbackend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Analytics", description = "Analytics and reporting endpoints")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @Operation(summary = "Get department analytics summary", description = "Retrieves analytics summary for a department within a date range")
    @ApiResponse(responseCode = "200", description = "Department analytics summary")
    @GetMapping("/department/{departmentId}/summary")
    public ResponseEntity<Map<String, Object>> getDepartmentAnalytics(
            @PathVariable String departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        // ✅ Default to last 30 days if no dates provided
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics(departmentId, startDate, endDate);

        return ResponseEntity.ok(analytics);
    }

    @Operation(summary = "Get recent routes", description = "Retrieves recent routes for a department with optional limit")
    @ApiResponse(responseCode = "200", description = "List of recent routes")
    @GetMapping("/department/{departmentId}/recent")
    public ResponseEntity<List<RouteHistory>> getRecentRoutes(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<RouteHistory> routes = analyticsService.getRecentRoutes(departmentId, limit);
        return ResponseEntity.ok(routes);
    }

    @Operation(summary = "Get vehicle performance", description = "Retrieves performance statistics for a specific vehicle")
    @ApiResponse(responseCode = "200", description = "Vehicle performance statistics")
    @GetMapping("/vehicle/{vehicleId}/performance")
    public ResponseEntity<Map<String, Object>> getVehiclePerformance(
            @PathVariable String vehicleId
    ) {
        Map<String, Object> performance = analyticsService.getVehiclePerformance(vehicleId);
        return ResponseEntity.ok(performance);
    }

    @Operation(summary = "Get department routes", description = "Retrieves all route history for a department with optional date filter")
    @ApiResponse(responseCode = "200", description = "List of routes")
    @GetMapping("/department/{departmentId}/routes")
    public ResponseEntity<List<RouteHistory>> getDepartmentRoutes(
            @PathVariable String departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) {
        // Default to last 30 days if no dates provided
        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        // ✅ Create final variables for lambda
        final LocalDateTime finalStartDate = startDate;
        final LocalDateTime finalEndDate = endDate;

        List<RouteHistory> routes = analyticsService.getRecentRoutes(departmentId, 100); // Get up to 100 routes

        // Filter by date range
        List<RouteHistory> filteredRoutes = routes.stream()
                .filter(r -> r.getStartTime().isAfter(finalStartDate) && r.getStartTime().isBefore(finalEndDate))
                .toList();

        return ResponseEntity.ok(filteredRoutes);
    }

    @Operation(summary = "Get CO2 emissions trend", description = "Retrieves daily CO2 emissions aggregation for a department")
    @ApiResponse(responseCode = "200", description = "CO2 emissions trend data")
    @GetMapping("/department/{departmentId}/co2-trend")
    public ResponseEntity<Map<String, Object>> getCO2Trend(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "7") int days
    ) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics(departmentId, startDate, endDate);

        return ResponseEntity.ok(Map.of(
                "period", days + " days",
                "totalCO2Kg", analytics.get("totalCO2EmissionsKg"),
                "avgCO2PerRoute", analytics.get("avgCO2PerRoute"),
                "treesNeededToOffset", analytics.get("treesNeededToOffset")
        ));
    }

    @Operation(summary = "Get cost summary", description = "Retrieves cost summary for a department over a period")
    @ApiResponse(responseCode = "200", description = "Cost summary data")
    @GetMapping("/department/{departmentId}/cost-summary")
    public ResponseEntity<Map<String, Object>> getCostSummary(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "30") int days
    ) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics(departmentId, startDate, endDate);

        return ResponseEntity.ok(Map.of(
                "period", days + " days",
                "totalCostEuros", analytics.get("totalCostEuros"),
                "avgCostPerRoute", analytics.get("avgCostPerRoute"),
                "totalFuelLiters", analytics.get("totalFuelLiters"),
                "totalDistanceKm", analytics.get("totalDistanceKm")
        ));
    }

    @Operation(summary = "Get efficiency metrics", description = "Retrieves efficiency metrics for a department")
    @ApiResponse(responseCode = "200", description = "Efficiency metrics data")
    @GetMapping("/department/{departmentId}/efficiency")
    public ResponseEntity<Map<String, Object>> getEfficiencyMetrics(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "30") int days
    ) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics(departmentId, startDate, endDate);

        return ResponseEntity.ok(Map.of(
                "period", days + " days",
                "efficiencyScore", analytics.get("efficiencyScore"),
                "totalRoutes", analytics.get("totalRoutes"),
                "totalBinsCollected", analytics.get("totalBinsCollected"),
                "avgBinsPerRoute", analytics.get("avgBinsPerRoute"),
                "avgDistancePerRoute", analytics.get("avgDistancePerRoute")
        ));
    }
}
