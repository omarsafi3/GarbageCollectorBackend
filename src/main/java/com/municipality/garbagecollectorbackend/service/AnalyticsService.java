package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.RouteHistory;
import com.municipality.garbagecollectorbackend.model.RouteHistory.BinCollectionDetail;
import com.municipality.garbagecollectorbackend.model.ActiveRoute;
import com.municipality.garbagecollectorbackend.model.BinStop;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.RouteHistoryRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService {

    @Autowired
    private RouteHistoryRepository routeHistoryRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    // ✅ CONSTANTS: Emissions and cost calculations
    private static final double CO2_PER_KM = 0.27; // kg CO2 per km (diesel truck average)
    private static final double FUEL_CONSUMPTION_PER_KM = 0.15; // liters per km
    private static final double FUEL_COST_PER_LITER = 1.5; // euros per liter
    private static final double LABOR_COST_PER_HOUR = 25.0; // euros per hour
    private static final double VEHICLE_COST_PER_KM = 0.5; // euros per km (maintenance, depreciation)

    /**
     * Save completed route to history with all analytics
     */
    public RouteHistory saveRouteToHistory(ActiveRoute activeRoute) {
        RouteHistory history = new RouteHistory();

        // ✅ Basic Info
        history.setVehicleId(activeRoute.getVehicleId());
        history.setDepartmentId(activeRoute.getDepartmentId());
        history.setStartTime(activeRoute.getStartTime());
        history.setEndTime(activeRoute.getEndTime());

        // ✅ Get vehicle and department names
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(activeRoute.getVehicleId());
        if (vehicleOpt.isPresent()) {
            history.setVehicleReference(vehicleOpt.get().getReference());
        }

        Optional<Department> deptOpt = departmentRepository.findById(activeRoute.getDepartmentId());
        if (deptOpt.isPresent()) {
            history.setDepartmentName(deptOpt.get().getName());
        }

        // ✅ Calculate duration
        long durationMinutes = ChronoUnit.MINUTES.between(
                activeRoute.getStartTime(),
                activeRoute.getEndTime()
        );
        history.setDurationMinutes(durationMinutes);

        // ✅ Performance Metrics
        history.setTotalBins(activeRoute.getTotalBins());
        history.setBinsCollected(activeRoute.getBinsCollected());
        history.setTotalDistanceKm(activeRoute.getTotalDistanceKm());

        // Calculate average speed (km/h)
        double durationHours = durationMinutes / 60.0;
        double averageSpeed = durationHours > 0 ? activeRoute.getTotalDistanceKm() / durationHours : 0;
        history.setAverageSpeed(averageSpeed);

        // ✅ Environmental Impact
        double co2Emissions = activeRoute.getTotalDistanceKm() * CO2_PER_KM;
        history.setCo2EmissionsKg(co2Emissions);

        double fuelConsumed = activeRoute.getTotalDistanceKm() * FUEL_CONSUMPTION_PER_KM;
        history.setFuelConsumedLiters(fuelConsumed);

        // ✅ Cost Calculation
        double fuelCost = fuelConsumed * FUEL_COST_PER_LITER;
        double laborCost = durationHours * LABOR_COST_PER_HOUR;
        double vehicleCost = activeRoute.getTotalDistanceKm() * VEHICLE_COST_PER_KM;
        double totalCost = fuelCost + laborCost + vehicleCost;
        history.setEstimatedCost(totalCost);

        // ✅ Bin Details
        List<BinCollectionDetail> binDetails = new ArrayList<>();
        for (BinStop stop : activeRoute.getBinStops()) {
            if ("COLLECTED".equals(stop.getStatus())) {
                BinCollectionDetail detail = new BinCollectionDetail();
                detail.setBinId(stop.getBinId());
                detail.setLatitude(stop.getLatitude());
                detail.setLongitude(stop.getLongitude());
                detail.setFillLevelBefore((int) stop.getBinFillLevelBefore());
                detail.setCollectionTime(stop.getCollectionTime());
                binDetails.add(detail);
            }
        }
        history.setBinDetails(binDetails);

        // ✅ Completion Status
        if (activeRoute.getBinsCollected() == activeRoute.getTotalBins()) {
            history.setCompletionStatus("COMPLETED");
        } else if (activeRoute.getBinsCollected() > 0) {
            history.setCompletionStatus("PARTIAL");
        } else {
            history.setCompletionStatus("CANCELLED");
        }

        // Save to database
        RouteHistory saved = routeHistoryRepository.save(history);
        System.out.println("💾 Saved route history: " + saved.getId() +
                " | Distance: " + String.format("%.2f", saved.getTotalDistanceKm()) + " km" +
                " | CO2: " + String.format("%.2f", saved.getCo2EmissionsKg()) + " kg" +
                " | Cost: €" + String.format("%.2f", saved.getEstimatedCost()));

        return saved;
    }

    /**
     * Get department analytics summary
     */
    public Map<String, Object> getDepartmentAnalytics(String departmentId, LocalDateTime startDate, LocalDateTime endDate) {
        List<RouteHistory> routes = routeHistoryRepository.findByDepartmentIdAndStartTimeBetween(
                departmentId, startDate, endDate
        );

        Map<String, Object> analytics = new HashMap<>();

        // ✅ Totals
        int totalRoutes = routes.size();
        int totalBinsCollected = routes.stream().mapToInt(RouteHistory::getBinsCollected).sum();
        double totalDistance = routes.stream().mapToDouble(RouteHistory::getTotalDistanceKm).sum();
        double totalCO2 = routes.stream().mapToDouble(RouteHistory::getCo2EmissionsKg).sum();
        double totalFuel = routes.stream().mapToDouble(RouteHistory::getFuelConsumedLiters).sum();
        double totalCost = routes.stream().mapToDouble(RouteHistory::getEstimatedCost).sum();

        analytics.put("totalRoutes", totalRoutes);
        analytics.put("totalBinsCollected", totalBinsCollected);
        analytics.put("totalDistanceKm", Math.round(totalDistance * 100.0) / 100.0);
        analytics.put("totalCO2EmissionsKg", Math.round(totalCO2 * 100.0) / 100.0);
        analytics.put("totalFuelLiters", Math.round(totalFuel * 100.0) / 100.0);
        analytics.put("totalCostEuros", Math.round(totalCost * 100.0) / 100.0);

        // ✅ Averages
        if (totalRoutes > 0) {
            analytics.put("avgDistancePerRoute", Math.round((totalDistance / totalRoutes) * 100.0) / 100.0);
            analytics.put("avgBinsPerRoute", Math.round((double) totalBinsCollected / totalRoutes * 100.0) / 100.0);
            analytics.put("avgCostPerRoute", Math.round((totalCost / totalRoutes) * 100.0) / 100.0);
            analytics.put("avgCO2PerRoute", Math.round((totalCO2 / totalRoutes) * 100.0) / 100.0);
        } else {
            analytics.put("avgDistancePerRoute", 0);
            analytics.put("avgBinsPerRoute", 0);
            analytics.put("avgCostPerRoute", 0);
            analytics.put("avgCO2PerRoute", 0);
        }

        // ✅ Environmental Comparison
        double treesEquivalent = totalCO2 / 21.0; // 1 tree absorbs ~21 kg CO2/year
        double carsEquivalent = totalDistance / 15000.0; // Average car drives 15,000 km/year

        analytics.put("treesNeededToOffset", Math.round(treesEquivalent * 100.0) / 100.0);
        analytics.put("equivalentCarYears", Math.round(carsEquivalent * 100.0) / 100.0);

        // ✅ Efficiency Score (0-100)
        double efficiency = totalRoutes > 0 ?
                Math.min(100, (totalBinsCollected / (totalDistance + 1)) * 10) : 0;
        analytics.put("efficiencyScore", Math.round(efficiency));

        return analytics;
    }

    /**
     * Get recent route history
     */
    public List<RouteHistory> getRecentRoutes(String departmentId, int limit) {
        List<RouteHistory> allRoutes = routeHistoryRepository.findByDepartmentIdOrderByStartTimeDesc(departmentId);
        return allRoutes.size() > limit ? allRoutes.subList(0, limit) : allRoutes;
    }

    /**
     * Get vehicle performance
     */
    public Map<String, Object> getVehiclePerformance(String vehicleId) {
        List<RouteHistory> routes = routeHistoryRepository.findByVehicleId(vehicleId);

        Map<String, Object> performance = new HashMap<>();

        int totalRoutes = routes.size();
        double totalDistance = routes.stream().mapToDouble(RouteHistory::getTotalDistanceKm).sum();
        double totalCO2 = routes.stream().mapToDouble(RouteHistory::getCo2EmissionsKg).sum();
        int totalBins = routes.stream().mapToInt(RouteHistory::getBinsCollected).sum();

        performance.put("totalRoutes", totalRoutes);
        performance.put("totalDistance", Math.round(totalDistance * 100.0) / 100.0);
        performance.put("totalCO2", Math.round(totalCO2 * 100.0) / 100.0);
        performance.put("totalBinsCollected", totalBins);

        if (totalRoutes > 0) {
            performance.put("avgDistancePerRoute", Math.round((totalDistance / totalRoutes) * 100.0) / 100.0);
        }

        return performance;
    }
}
