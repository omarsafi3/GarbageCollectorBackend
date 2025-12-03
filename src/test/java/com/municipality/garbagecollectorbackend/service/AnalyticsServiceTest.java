package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.RouteHistoryRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private RouteHistoryRepository routeHistoryRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private ActiveRoute testActiveRoute;
    private Vehicle testVehicle;
    private Department testDepartment;

    @BeforeEach
    void setUp() {
        testDepartment = new Department("dep1", "Test Department", 36.8, 10.1);

        testVehicle = new Vehicle();
        testVehicle.setId("v1");
        testVehicle.setReference("TRUCK-001");

        testActiveRoute = new ActiveRoute();
        testActiveRoute.setId("route1");
        testActiveRoute.setVehicleId("v1");
        testActiveRoute.setDepartmentId("dep1");
        testActiveRoute.setStartTime(LocalDateTime.now().minusHours(2));
        testActiveRoute.setEndTime(LocalDateTime.now());
        testActiveRoute.setTotalBins(10);
        testActiveRoute.setBinsCollected(10);
        testActiveRoute.setTotalDistanceKm(25.5);

        List<BinStop> binStops = new ArrayList<>();
        BinStop stop = new BinStop();
        stop.setBinId("bin1");
        stop.setLatitude(36.8);
        stop.setLongitude(10.1);
        stop.setStatus("COLLECTED");
        stop.setBinFillLevelBefore(80);
        stop.setCollectionTime(LocalDateTime.now().minusHours(1));
        binStops.add(stop);
        testActiveRoute.setBinStops(binStops);
    }

    @Test
    void testSaveRouteToHistory_completed() {
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(routeHistoryRepository.save(any(RouteHistory.class))).thenAnswer(i -> {
            RouteHistory history = i.getArgument(0);
            history.setId("history1");
            return history;
        });

        RouteHistory result = analyticsService.saveRouteToHistory(testActiveRoute);

        assertNotNull(result);
        assertEquals("v1", result.getVehicleId());
        assertEquals("dep1", result.getDepartmentId());
        assertEquals("TRUCK-001", result.getVehicleReference());
        assertEquals("Test Department", result.getDepartmentName());
        assertEquals(10, result.getTotalBins());
        assertEquals(10, result.getBinsCollected());
        assertEquals(25.5, result.getTotalDistanceKm());
        assertEquals("COMPLETED", result.getCompletionStatus());
        assertTrue(result.getCo2EmissionsKg() > 0);
        assertTrue(result.getFuelConsumedLiters() > 0);
        assertTrue(result.getEstimatedCost() > 0);
        assertEquals(120, result.getDurationMinutes()); // 2 hours
    }

    @Test
    void testSaveRouteToHistory_partial() {
        testActiveRoute.setBinsCollected(5); // Only 5 of 10 collected

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(routeHistoryRepository.save(any(RouteHistory.class))).thenAnswer(i -> i.getArgument(0));

        RouteHistory result = analyticsService.saveRouteToHistory(testActiveRoute);

        assertEquals("PARTIAL", result.getCompletionStatus());
    }

    @Test
    void testSaveRouteToHistory_cancelled() {
        testActiveRoute.setBinsCollected(0); // No bins collected

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(routeHistoryRepository.save(any(RouteHistory.class))).thenAnswer(i -> i.getArgument(0));

        RouteHistory result = analyticsService.saveRouteToHistory(testActiveRoute);

        assertEquals("CANCELLED", result.getCompletionStatus());
    }

    @Test
    void testGetDepartmentAnalytics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(30);

        RouteHistory history1 = new RouteHistory();
        history1.setBinsCollected(10);
        history1.setTotalDistanceKm(20.0);
        history1.setCo2EmissionsKg(5.4);
        history1.setFuelConsumedLiters(3.0);
        history1.setEstimatedCost(45.0);

        RouteHistory history2 = new RouteHistory();
        history2.setBinsCollected(15);
        history2.setTotalDistanceKm(30.0);
        history2.setCo2EmissionsKg(8.1);
        history2.setFuelConsumedLiters(4.5);
        history2.setEstimatedCost(65.0);

        when(routeHistoryRepository.findByDepartmentIdAndStartTimeBetween("dep1", startDate, now))
            .thenReturn(List.of(history1, history2));

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics("dep1", startDate, now);

        assertNotNull(analytics);
        assertEquals(2, analytics.get("totalRoutes"));
        assertEquals(25, analytics.get("totalBinsCollected"));
        assertEquals(50.0, analytics.get("totalDistanceKm"));
        assertEquals(13.5, analytics.get("totalCO2EmissionsKg"));
        assertEquals(7.5, analytics.get("totalFuelLiters"));
        assertEquals(110.0, analytics.get("totalCostEuros"));
        
        // Averages
        assertEquals(25.0, analytics.get("avgDistancePerRoute"));
        assertEquals(12.5, analytics.get("avgBinsPerRoute"));
        assertEquals(55.0, analytics.get("avgCostPerRoute"));
    }

    @Test
    void testGetDepartmentAnalytics_noRoutes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now.minusDays(30);

        when(routeHistoryRepository.findByDepartmentIdAndStartTimeBetween("dep1", startDate, now))
            .thenReturn(List.of());

        Map<String, Object> analytics = analyticsService.getDepartmentAnalytics("dep1", startDate, now);

        assertEquals(0, analytics.get("totalRoutes"));
        assertEquals(0, analytics.get("totalBinsCollected"));
        assertEquals(0.0, analytics.get("totalDistanceKm"));
        assertEquals(0, analytics.get("avgDistancePerRoute"));
    }

    @Test
    void testGetRecentRoutes() {
        RouteHistory history1 = new RouteHistory();
        history1.setId("h1");
        RouteHistory history2 = new RouteHistory();
        history2.setId("h2");
        RouteHistory history3 = new RouteHistory();
        history3.setId("h3");

        when(routeHistoryRepository.findByDepartmentIdOrderByStartTimeDesc("dep1"))
            .thenReturn(List.of(history1, history2, history3));

        List<RouteHistory> result = analyticsService.getRecentRoutes("dep1", 2);

        assertEquals(2, result.size());
        assertEquals("h1", result.get(0).getId());
        assertEquals("h2", result.get(1).getId());
    }

    @Test
    void testGetRecentRoutes_lessThanLimit() {
        RouteHistory history1 = new RouteHistory();
        history1.setId("h1");

        when(routeHistoryRepository.findByDepartmentIdOrderByStartTimeDesc("dep1"))
            .thenReturn(List.of(history1));

        List<RouteHistory> result = analyticsService.getRecentRoutes("dep1", 10);

        assertEquals(1, result.size());
    }

    @Test
    void testGetVehiclePerformance() {
        RouteHistory history1 = new RouteHistory();
        history1.setTotalDistanceKm(20.0);
        history1.setCo2EmissionsKg(5.4);
        history1.setBinsCollected(10);

        RouteHistory history2 = new RouteHistory();
        history2.setTotalDistanceKm(30.0);
        history2.setCo2EmissionsKg(8.1);
        history2.setBinsCollected(15);

        when(routeHistoryRepository.findByVehicleId("v1"))
            .thenReturn(List.of(history1, history2));

        Map<String, Object> performance = analyticsService.getVehiclePerformance("v1");

        assertNotNull(performance);
        assertEquals(2, performance.get("totalRoutes"));
        assertEquals(50.0, performance.get("totalDistance"));
        assertEquals(13.5, performance.get("totalCO2"));
        assertEquals(25, performance.get("totalBinsCollected"));
        assertEquals(25.0, performance.get("avgDistancePerRoute"));
    }

    @Test
    void testGetVehiclePerformance_noRoutes() {
        when(routeHistoryRepository.findByVehicleId("v1")).thenReturn(List.of());

        Map<String, Object> performance = analyticsService.getVehiclePerformance("v1");

        assertEquals(0, performance.get("totalRoutes"));
        assertEquals(0.0, performance.get("totalDistance"));
        assertEquals(0.0, performance.get("totalCO2"));
        assertEquals(0, performance.get("totalBinsCollected"));
    }

    @Test
    void testCO2Calculation() {
        // Test that CO2 is calculated correctly: 0.27 kg per km
        testActiveRoute.setTotalDistanceKm(100.0);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(routeHistoryRepository.save(any(RouteHistory.class))).thenAnswer(i -> i.getArgument(0));

        RouteHistory result = analyticsService.saveRouteToHistory(testActiveRoute);

        assertEquals(27.0, result.getCo2EmissionsKg(), 0.01); // 100 km * 0.27 kg/km
    }

    @Test
    void testFuelConsumptionCalculation() {
        // Test that fuel is calculated correctly: 0.15 liters per km
        testActiveRoute.setTotalDistanceKm(100.0);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(routeHistoryRepository.save(any(RouteHistory.class))).thenAnswer(i -> i.getArgument(0));

        RouteHistory result = analyticsService.saveRouteToHistory(testActiveRoute);

        assertEquals(15.0, result.getFuelConsumedLiters(), 0.01); // 100 km * 0.15 L/km
    }
}
