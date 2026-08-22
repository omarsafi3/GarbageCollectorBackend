package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.*;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioSimulationService {

    private final BinRepository binRepository;
    private final VehicleRepository vehicleRepository;
    private final EmployeeRepository employeeRepository;
    private final IncidentService incidentService;
    private final RouteOptimizationService routeOptimizationService;
    private final SimpMessagingTemplate messagingTemplate;

    public Map<String, Object> executeScenario(String scenarioName, String departmentId) {
        log.info("Executing simulation scenario: {} for department: {}", scenarioName, departmentId);
        
        switch (scenarioName.toLowerCase()) {
            case "morning-shift":
                return runMorningShiftScenario(departmentId);
            case "storm-flood":
                return runStormFloodScenario(departmentId);
            case "market-surge":
                return runMarketSurgeScenario(departmentId);
            case "vehicle-breakdown":
                return runVehicleBreakdownScenario(departmentId);
            default:
                throw new IllegalArgumentException("Unknown scenario: " + scenarioName);
        }
    }

    private Map<String, Object> runMorningShiftScenario(String departmentId) {
        List<Bin> bins = binRepository.findByDepartmentId(departmentId);
        Random rand = new Random();

        // Set realistic high morning fill levels
        for (Bin bin : bins) {
            int fill = 75 + rand.nextInt(25);
            bin.setFillLevel(fill);
            bin.setStatus("critical");
            bin.setLastUpdated(LocalDateTime.now());
            binRepository.save(bin);
            messagingTemplate.convertAndSend("/topic/bins", bin);
        }

        // Reset vehicles to available
        List<Vehicle> vehicles = vehicleRepository.findByDepartmentId(departmentId);
        for (Vehicle v : vehicles) {
            v.setStatus(Vehicle.VehicleStatus.AVAILABLE);
            v.setAvailable(true);
            v.setFillLevel(0.0);
            vehicleRepository.save(v);
            messagingTemplate.convertAndSend("/topic/vehicles", v);
        }

        // Reset employees
        List<Employee> employees = employeeRepository.findByDepartmentId(departmentId);
        for (Employee e : employees) {
            e.setAvailable(true);
            employeeRepository.save(e);
        }

        // Re-generate fresh routes
        routeOptimizationService.generateRoutesForDepartment(departmentId);

        return Map.of(
                "scenario", "morning-shift",
                "message", "Morning Shift initialized: " + bins.size() + " bins set to critical, fleet reset, and routes optimized!",
                "binsUpdated", bins.size(),
                "vehiclesReady", vehicles.size()
        );
    }

    private Map<String, Object> runStormFloodScenario(String departmentId) {
        // Create 2 major flood incidents
        incidentService.reportRoadBlock(
                36.8010,
                10.1830,
                0.15,
                "🚨 Flash Flood: Avenue Habib Bourguiba - Heavy water accumulation"
        );

        incidentService.reportRoadBlock(
                36.8045,
                10.1795,
                0.10,
                "🚧 Fallen Tree & Storm Debris: Rue de Rome"
        );

        // Re-optimize routes avoiding the new incidents
        routeOptimizationService.generateRoutesForDepartment(departmentId);

        return Map.of(
                "scenario", "storm-flood",
                "message", "Heavy storm scenario triggered! 2 flood hazard zones established. Automatic OSRM detours generated.",
                "incidentsCreated", 2
        );
    }

    private Map<String, Object> runMarketSurgeScenario(String departmentId) {
        List<Bin> bins = binRepository.findByDepartmentId(departmentId);
        int updated = 0;

        for (int i = 0; i < Math.min(5, bins.size()); i++) {
            Bin b = bins.get(i);
            b.setFillLevel(100);
            b.setStatus("critical");
            b.setLastUpdated(LocalDateTime.now());
            binRepository.save(b);
            messagingTemplate.convertAndSend("/topic/bins", b);
            updated++;
        }

        // Notify auto-dispatch channel
        messagingTemplate.convertAndSend("/topic/auto-dispatch", Map.of(
                "event", "SURGE_DETECTED",
                "message", "Market waste surge detected in 5 central smart bins. Auto-dispatch suggested."
        ));

        return Map.of(
                "scenario", "market-surge",
                "message", "Market surge active: " + updated + " bins spiked to 100% capacity!",
                "binsSurged", updated
        );
    }

    private Map<String, Object> runVehicleBreakdownScenario(String departmentId) {
        List<Vehicle> vehicles = vehicleRepository.findByDepartmentId(departmentId);
        if (!vehicles.isEmpty()) {
            Vehicle v = vehicles.get(0);
            v.setStatus(Vehicle.VehicleStatus.MAINTENANCE);
            v.setAvailable(false);
            vehicleRepository.save(v);
            messagingTemplate.convertAndSend("/topic/vehicles", v);

            incidentService.reportRoadBlock(
                    36.8020,
                    10.1810,
                    0.05,
                    "⚠️ Mechanical Failure: Unit " + v.getReference() + " (" + v.getPlate() + ")"
            );

            return Map.of(
                    "scenario", "vehicle-breakdown",
                    "message", "Vehicle " + v.getReference() + " marked as broken down. Backup fleet reallocation required.",
                    "affectedVehicle", v.getReference()
            );
        }

        return Map.of("scenario", "vehicle-breakdown", "message", "No vehicles found to simulate breakdown.");
    }
}
