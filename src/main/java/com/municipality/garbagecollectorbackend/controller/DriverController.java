package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.ActiveRouteRepository;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Tag(name = "Driver Companion", description = "Endpoints for driver in-cab companion portal, navigation, and one-tap collection")
@RestController
@RequestMapping("/api/driver")
@RequiredArgsConstructor
@Slf4j
public class DriverController {

    private final RouteExecutionService routeExecutionService;
    private final ActiveRouteRepository activeRouteRepository;
    private final BinService binService;
    private final VehicleService vehicleService;
    private final IncidentService incidentService;
    private final SimpMessagingTemplate messagingTemplate;

    @Operation(summary = "Get active driver vehicles", description = "Lists vehicles with active or available routes for driver selection")
    @GetMapping("/active-vehicles")
    public ResponseEntity<List<Map<String, Object>>> getActiveDriverVehicles() {
        List<ActiveRoute> activeRoutes = activeRouteRepository.findByStatus("IN_PROGRESS");
        List<Vehicle> allVehicles = vehicleService.getAllVehicles();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Vehicle v : allVehicles) {
            Optional<ActiveRoute> matchingRoute = activeRoutes.stream()
                    .filter(r -> v.getId().equals(r.getVehicleId()))
                    .findFirst();

            Map<String, Object> map = new HashMap<>();
            map.put("vehicleId", v.getId());
            map.put("reference", v.getReference());
            map.put("plate", v.getPlate());
            map.put("status", v.getStatus());
            map.put("fillLevel", v.getFillLevel());
            map.put("hasActiveRoute", matchingRoute.isPresent());
            if (matchingRoute.isPresent()) {
                ActiveRoute route = matchingRoute.get();
                map.put("routeId", route.getId());
                map.put("totalBins", route.getTotalBins());
                map.put("completedBins", route.getCompletedBins());
                map.put("progressPercent", route.getProgressPercent());
            }
            if (v.getDepartment() != null) {
                map.put("departmentName", v.getDepartment().getName());
                map.put("departmentId", v.getDepartment().getId());
            }
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get driver current task", description = "Retrieves active navigation steps and next bin stop for driver")
    @GetMapping("/vehicle/{vehicleId}/task")
    public ResponseEntity<?> getDriverTask(@PathVariable String vehicleId) {
        ActiveRoute activeRoute = routeExecutionService.getActiveRouteByVehicle(vehicleId);
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId).orElse(null);

        if (activeRoute == null) {
            return ResponseEntity.ok(Map.of(
                    "hasActiveRoute", false,
                    "message", "No active route in progress for this vehicle.",
                    "vehicle", vehicle != null ? vehicle : Map.of()
            ));
        }

        List<BinStop> stops = activeRoute.getBinStops();
        BinStop nextStop = stops.stream()
                .filter(s -> !s.isCollected())
                .findFirst()
                .orElse(null);

        Bin nextBinDetails = null;
        if (nextStop != null) {
            nextBinDetails = binService.getBinById(nextStop.getBinId());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("hasActiveRoute", true);
        response.put("routeId", activeRoute.getId());
        response.put("vehicleId", vehicleId);
        response.put("vehicle", vehicle);
        response.put("totalBins", activeRoute.getTotalBins());
        response.put("completedBins", activeRoute.getCompletedBins());
        response.put("progressPercent", activeRoute.getProgressPercent());
        response.put("currentLat", activeRoute.getCurrentLat());
        response.put("currentLng", activeRoute.getCurrentLng());
        response.put("totalDistanceKm", activeRoute.getTotalDistanceKm());
        response.put("binStops", stops);
        response.put("nextStop", nextStop);
        response.put("nextBinDetails", nextBinDetails);
        response.put("fullRoutePolyline", activeRoute.getFullRoutePolyline());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Driver mark bin emptied", description = "Allows driver to manually record bin pickup and empty load")
    @PostMapping("/vehicle/{vehicleId}/empty-bin/{binId}")
    public ResponseEntity<?> emptyBinManually(
            @PathVariable String vehicleId,
            @PathVariable String binId) {
        try {
            Bin bin = binService.getBinById(binId);
            if (bin != null) {
                bin.setFillLevel(0);
                bin.setStatus("empty");
                bin.setLastEmptied(LocalDateTime.now());
                bin.setLastUpdated(LocalDateTime.now());
                binService.updateBin(bin.getId(), bin);

                // Broadcast bin update
                messagingTemplate.convertAndSend("/topic/bins", bin);
            }

            ActiveRoute activeRoute = routeExecutionService.getActiveRouteByVehicle(vehicleId);
            if (activeRoute != null) {
                List<BinStop> stops = activeRoute.getBinStops();
                for (BinStop stop : stops) {
                    if (binId.equals(stop.getBinId())) {
                        stop.setCollected(true);
                        stop.setCollectedAt(LocalDateTime.now());
                        break;
                    }
                }
                long collectedCount = stops.stream().filter(BinStop::isCollected).count();
                activeRoute.setCompletedBins((int) collectedCount);
                activeRoute.setProgressPercent((int) ((collectedCount * 100.0) / stops.size()));
                activeRouteRepository.save(activeRoute);

                // Broadcast progress
                Map<String, Object> progressUpdate = new HashMap<>();
                progressUpdate.put("vehicleId", vehicleId);
                progressUpdate.put("routeId", activeRoute.getId());
                progressUpdate.put("completedBins", collectedCount);
                progressUpdate.put("totalBins", stops.size());
                progressUpdate.put("progressPercent", activeRoute.getProgressPercent());
                messagingTemplate.convertAndSend("/topic/route-progress", progressUpdate);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "binId", binId,
                    "message", "Bin #" + binId + " marked as emptied successfully!"
            ));
        } catch (Exception e) {
            log.error("Error emptying bin manually", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @Operation(summary = "Driver report road or bin issue", description = "Driver reports road blockage or broken bin on route")
    @PostMapping("/vehicle/{vehicleId}/report-issue")
    public ResponseEntity<?> reportDriverIssue(
            @PathVariable String vehicleId,
            @RequestBody Map<String, Object> payload) {
        try {
            String title = (String) payload.getOrDefault("title", "Driver Reported Incident");
            String description = (String) payload.getOrDefault("description", "Reported by driver of " + vehicleId);
            Double lat = payload.get("latitude") != null ? Double.parseDouble(payload.get("latitude").toString()) : 36.8000;
            Double lng = payload.get("longitude") != null ? Double.parseDouble(payload.get("longitude").toString()) : 10.1800;

            Incident saved = incidentService.reportRoadBlock(
                    lat,
                    lng,
                    0.075,
                    "[Driver Alert " + vehicleId + "] " + title + ": " + description
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "incidentId", saved.getId(),
                    "message", "Issue logged and dispatch alerted!"
            ));
        } catch (Exception e) {
            log.error("Error logging driver issue", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
