package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.RouteHistory;
import com.municipality.garbagecollectorbackend.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    /**
     * Get department analytics summary with date range
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/summary?startDate=2025-11-01T00:00:00&endDate=2025-11-30T23:59:59
     */
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

    /**
     * Get recent routes for a department
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/recent?limit=10
     */
    @GetMapping("/department/{departmentId}/recent")
    public ResponseEntity<List<RouteHistory>> getRecentRoutes(
            @PathVariable String departmentId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<RouteHistory> routes = analyticsService.getRecentRoutes(departmentId, limit);
        return ResponseEntity.ok(routes);
    }

    /**
     * Get vehicle performance statistics
     *
     * Example: GET /api/analytics/vehicle/692029d616c4ef2ac1b745a5/performance
     */
    @GetMapping("/vehicle/{vehicleId}/performance")
    public ResponseEntity<Map<String, Object>> getVehiclePerformance(
            @PathVariable String vehicleId
    ) {
        Map<String, Object> performance = analyticsService.getVehiclePerformance(vehicleId);
        return ResponseEntity.ok(performance);
    }

    /**
     * Get all route history for a department (with optional date filter)
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/routes?startDate=2025-11-01T00:00:00
     */
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

    /**
     * Get CO2 emissions trend (daily aggregation)
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/co2-trend?days=7
     */
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

    /**
     * Get cost summary
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/cost-summary?days=30
     */
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

    /**
     * Get efficiency metrics
     *
     * Example: GET /api/analytics/department/6920266d0b737026e2496c54/efficiency
     */
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
