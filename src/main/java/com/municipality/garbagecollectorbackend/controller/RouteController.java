package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.routing.DepartmentRoutingService;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import com.municipality.garbagecollectorbackend.dto.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.dto.BinDTO;
import com.municipality.garbagecollectorbackend.service.AutoDispatchService;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.PolylineService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.municipality.garbagecollectorbackend.dto.PreGeneratedRoute;
import com.municipality.garbagecollectorbackend.dto.RouteBin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Routes", description = "Route optimization and execution endpoints")
@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private static final int CRITICAL_BIN_THRESHOLD = 70;

    private final RouteOptimizationService routeOptimizationService;
    private final DepartmentRoutingService departmentRoutingService;
    private final BinService binService;
    private final DepartmentService departmentService;
    private final VehicleService vehicleService;
    private final RouteExecutionService routeExecutionService;
    private final PolylineService polylineService;
    private final AutoDispatchService autoDispatchService;

    @Operation(summary = "Optimize route for vehicle", description = "Returns optimized bins assigned to a specific vehicle")
    @ApiResponse(responseCode = "200", description = "Optimized route bins")
    @GetMapping("/optimize")
    public List<BinDTO> optimizeRoute(
            @RequestParam String departmentId,
            @RequestParam String vehicleId
    ) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return List.of();
        }
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

        if (departmentOpt.isEmpty() || vehicleOpt.isEmpty()) {
            System.out.println("[RouteController] Department or vehicle not found");
            return List.of();
        }

        Department department = departmentOpt.get();

        // Get all critical bins ONLY for THIS department
        List<Bin> bins = binService.getCriticalBinsByDepartment(departmentId, CRITICAL_BIN_THRESHOLD);

        if (bins.isEmpty()) {
            System.out.println("[RouteController] No bins with fillLevel >= " + CRITICAL_BIN_THRESHOLD + " in department " + departmentId);
            return List.of();
        }

        // Get ALL available vehicles in department to calculate fair distribution
        List<Vehicle> allDepartmentVehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId);

        if (allDepartmentVehicles.isEmpty()) {
            System.out.println("[RouteController] No available vehicles in department");
            return List.of();
        }

        System.out.println("[RouteController] Optimizing routes for " +
                allDepartmentVehicles.size() + " vehicles with " + bins.size() + " bins in " + department.getName());

        // Calculate routes for ALL vehicles (fair distribution)
        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        allDepartmentVehicles,
                        bins,
                        30.0,
                        Collections.emptySet()
                );

        // Return ONLY bins assigned to THIS specific vehicleId
        Optional<VehicleRouteResult> thisVehicleRoute = routeResults.stream()
                .filter(r -> vehicleId.equals(r.getVehicleId()))
                .findFirst();

        if (thisVehicleRoute.isEmpty()) {
            System.out.println("[RouteController] No route calculated for vehicle " + vehicleId);
            return List.of();
        }

        List<String> orderedBinIds = thisVehicleRoute.get().getOrderedBinIds();
        System.out.println("[RouteController] Vehicle " + vehicleId +
                " assigned " + orderedBinIds.size() + " bins: " + orderedBinIds);

        // Convert bin IDs to DTOs
        Map<String, Bin> binIdMap = bins.stream()
                .collect(Collectors.toMap(bin -> bin.getId().toString(), bin -> bin));

        List<BinDTO> orderedBins = orderedBinIds.stream()
                .map(binIdMap::get)
                .filter(Objects::nonNull)
                .map(bin -> new BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                .toList();

        System.out.println("[RouteController] Returning " + orderedBins.size() +
                " bins for vehicle " + vehicleId);
        return orderedBins;
    }

    @Operation(summary = "Optimize all routes for department", description = "Calculates optimized routes for all available vehicles in a department")
    @ApiResponse(responseCode = "200", description = "List of optimized routes per vehicle")
    @GetMapping("/optimize/department")
    public List<DepartmentRoutingService.DepartmentRouteDTO> optimizeDepartment(
            @RequestParam String departmentId
    ) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return List.of();
        }
        System.out.println("[RouteController] Optimizing all routes for department " + departmentId);
        return departmentRoutingService.optimizeDepartmentRoutes(departmentId, 30.0);
    }

    @Operation(summary = "Get routes with polylines", description = "Retrieves department routes with OSRM polylines for map visualization")
    @ApiResponse(responseCode = "200", description = "Routes with polyline data")
    @ApiResponse(responseCode = "400", description = "Invalid department")
    @GetMapping("/department-routes-with-polylines")
    public ResponseEntity<List<Map<String, Object>>> getDepartmentRoutesWithPolylines(
            @RequestParam String departmentId
    ) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return ResponseEntity.status(403).build();
        }
        try {
            System.out.println(" Fetching routes with polylines for department: " + departmentId);

            // Get critical bins strictly for this department
            List<Bin> bins = binService.getCriticalBinsByDepartment(departmentId, CRITICAL_BIN_THRESHOLD);

            if (bins.isEmpty()) {
                System.out.println(" No bins with fillLevel >= " + CRITICAL_BIN_THRESHOLD + " in department " + departmentId);
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get available vehicles for this department
            List<Vehicle> availableVehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId);

            if (availableVehicles.isEmpty()) {
                System.out.println(" No available vehicles");
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get department
            Optional<Department> deptOpt = departmentService.getDepartmentById(departmentId);
            if (deptOpt.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // Calculate routes using existing optimization service
            List<VehicleRouteResult> routeResults = routeOptimizationService.optimizeDepartmentRoutes(
                    deptOpt,
                    availableVehicles,
                    bins,
                    30.0,
                    Collections.emptySet()
            );

            List<Map<String, Object>> routesWithPolylines = new ArrayList<>();

            for (VehicleRouteResult result : routeResults) {
                String vehicleId = result.getVehicleId();
                List<String> binIds = result.getOrderedBinIds();

                // Get bin objects
                Map<String, Bin> binIdMap = bins.stream()
                        .collect(Collectors.toMap(Bin::getId, bin -> bin));

                List<Bin> routeBins = binIds.stream()
                        .map(binIdMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                // Build polyline using PolylineService with department
                List<double[]> polyline = polylineService.buildRoutePolyline(routeBins, deptOpt.orElse(null));

                Map<String, Object> routeData = new HashMap<>();
                routeData.put("vehicleId", vehicleId);
                routeData.put("bins", routeBins);
                routeData.put("polyline", polyline);

                routesWithPolylines.add(routeData);
            }

            log.info("Returning {} routes with polylines for department {}", routesWithPolylines.size(), departmentId);
            return ResponseEntity.ok(routesWithPolylines);

        } catch (Exception e) {
            log.error("Failed to fetch routes: {}", e.getMessage(), e);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @Operation(summary = "Optimize routes with specific bins", description = "Optimizes routes for a department using only specified bin IDs")
    @ApiResponse(responseCode = "200", description = "Optimized routes for specified bins")
    @GetMapping("/optimize/department/bins")
    public List<DepartmentRoutingService.DepartmentRouteDTO> optimizeDepartmentWithBins(
            @RequestParam String departmentId,
            @RequestParam List<String> binIds
    ) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return List.of();
        }
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        if (departmentOpt.isEmpty() || binIds == null || binIds.isEmpty()) {
            return List.of();
        }

        // Find only the bins with these IDs belonging to THIS department
        List<Bin> allBins = binService.getBinsByDepartmentId(departmentId);
        Set<String> wanted = new HashSet<>(binIds);
        List<Bin> bins = allBins.stream()
                .filter(b -> wanted.contains(b.getId()))
                .toList();

        if (bins.isEmpty()) return List.of();

        // Only available vehicles of this department
        List<Vehicle> vehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId);

        Department department = departmentOpt.get();

        // Get current overfill bins as IDs
        Set<String> overfillBinIds = bins.stream()
                .filter(b -> b.getFillLevel() >= 100)
                .map(Bin::getId)
                .collect(Collectors.toSet());

        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        vehicles,
                        bins,
                        30.0,
                        overfillBinIds
                );

        // Convert to DTOs
        Map<String, Bin> binIdMap = bins.stream()
                .collect(Collectors.toMap(bin -> bin.getId().toString(), bin -> bin));

        List<DepartmentRoutingService.DepartmentRouteDTO> response = new ArrayList<>();
        for (VehicleRouteResult r : routeResults) {
            String vehicleId = r.getVehicleId();
            List<String> ordered = r.getOrderedBinIds();

            List<BinDTO> binDtos = ordered.stream()
                    .map(binIdMap::get)
                    .filter(Objects::nonNull)
                    .map(bin -> new BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                    .toList();

            DepartmentRoutingService.DepartmentRouteDTO dto =
                    new DepartmentRoutingService.DepartmentRouteDTO(vehicleId, binDtos);
            response.add(dto);
        }

        System.out.println("[RouteController] /optimize/department/bins responding with "
                + response.size() + " routes");
        response.forEach(r -> {
            System.out.println("  vehicleId=" + r.getVehicleId()
                    + " bins=" + r.getBins().stream().map(BinDTO::getId).toList());
        });

        return response;
    }
    @Operation(summary = "Execute all managed routes", description = "Starts routes for all available vehicles in a department")
    @ApiResponse(responseCode = "200", description = "Routes started successfully")
    @ApiResponse(responseCode = "400", description = "No vehicles or bins available")
    @PostMapping("/execute-all-managed")
    public ResponseEntity<Map<String, Object>> executeAllManagedRoutes(
            @RequestParam String departmentId) {

        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return ResponseEntity.status(403).build();
        }

        System.out.println(" Starting routes for ALL vehicles in department " + departmentId);

        try {
            // Get all available vehicles for this department
            List<Vehicle> availableVehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId);

            if (availableVehicles.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No available vehicles");
                return ResponseEntity.badRequest().body(error);
            }

            // Get department
            Optional<Department> deptOpt = departmentService.getDepartmentById(departmentId);
            if (deptOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Department not found");
                return ResponseEntity.badRequest().body(error);
            }

            // Get all critical bins strictly for this department
            List<Bin> criticalBins = binService.getCriticalBinsByDepartment(departmentId, CRITICAL_BIN_THRESHOLD);

            if (criticalBins.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No bins need collection in department " + departmentId);
                return ResponseEntity.badRequest().body(error);
            }

            // Calculate routes for ALL vehicles at once (fair distribution)
            List<VehicleRouteResult> allRoutes = routeOptimizationService.optimizeDepartmentRoutes(
                    deptOpt,
                    availableVehicles,
                    criticalBins,
                    30.0,
                    Collections.emptySet()
            );

 System.out.println(" Calculated " + allRoutes.size() + " unique routes");

            List<Map<String, Object>> routeResults = new ArrayList<>();

            // Start each vehicle's route with PRE-CALCULATED bins
            for (VehicleRouteResult routeResult : allRoutes) {
                try {
                    String vehicleId = routeResult.getVehicleId();
                    List<String> binIds = routeResult.getOrderedBinIds();

 System.out.println(" Starting vehicle " + vehicleId + " with " + binIds.size() + " bins");

                    // Start route with specific bin IDs
                    ActiveRoute activeRoute = routeExecutionService.startRouteWithSpecificBins(
                            departmentId,
                            vehicleId,
                            binIds
                    );

                    Map<String, Object> routeInfo = new HashMap<>();
                    routeInfo.put("vehicleId", activeRoute.getVehicleId());
                    routeInfo.put("totalBins", activeRoute.getTotalBins());
                    routeInfo.put("distanceKm", activeRoute.getTotalDistanceKm());
                    routeInfo.put("status", "started");

                    routeResults.add(routeInfo);

 System.out.println(" Started route for vehicle " + vehicleId);

                } catch (Exception e) {
 System.err.println(" Failed to start route for vehicle " + routeResult.getVehicleId() + ": " + e.getMessage());

                    Map<String, Object> routeInfo = new HashMap<>();
                    routeInfo.put("vehicleId", routeResult.getVehicleId());
                    routeInfo.put("status", "failed");
                    routeInfo.put("error", e.getMessage());

                    routeResults.add(routeInfo);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalVehicles", allRoutes.size());
            response.put("routes", routeResults);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
 System.err.println(" Failed to start routes: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }


    @Operation(summary = "Execute vehicle route", description = "Executes a route for a specific vehicle with given bin IDs")
    @ApiResponse(responseCode = "200", description = "Route execution started")
    @PostMapping("/execute")
    public void executeVehicleRoute(@RequestParam String vehicleId,
                                    @RequestBody List<String> binIds) {
        System.out.println("[RouteController] Executing route for vehicle " + vehicleId +
                " with " + binIds.size() + " bins");
        departmentRoutingService.executeRoute(vehicleId, binIds);
    }



    @Operation(summary = "Execute route step", description = "Executes a single step in a vehicle's route (empty a bin)")
    @ApiResponse(responseCode = "200", description = "Bin emptied successfully")
    @PostMapping("/execute/step")
    public void executeVehicleRouteStep(@RequestParam String vehicleId,
                                        @RequestParam String binId) {
        System.out.println("[RouteController] Executing step for vehicle " + vehicleId +
                " at bin " + binId);
        vehicleService.emptyBin(vehicleId, binId);
    }
    @Operation(summary = "Execute managed route", description = "Starts a managed route with full path calculation for a vehicle")
    @ApiResponse(responseCode = "200", description = "Route started successfully")
    @ApiResponse(responseCode = "400", description = "Failed to start route")
    @PostMapping("/execute-managed")
    public ResponseEntity<Map<String, Object>> executeManagedRoute(
            @RequestParam String departmentId,
            @RequestParam String vehicleId) {

 System.out.println(" Starting managed route for vehicle " + vehicleId);

        try {
 // ADD THIS LINE - Set vehicle to IN_ROUTE
            vehicleService.startRoute(vehicleId);

            ActiveRoute activeRoute = routeExecutionService.startRouteWithFullPath(departmentId, vehicleId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("routeId", activeRoute.getId());
            response.put("vehicleId", activeRoute.getVehicleId());
            response.put("totalBins", activeRoute.getTotalBins());
            response.put("totalDistanceKm", activeRoute.getTotalDistanceKm());
            response.put("status", activeRoute.getStatus());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
 System.err.println(" Failed to start route: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }
    @Operation(summary = "Get available routes", description = "Retrieves pre-generated available routes for a department")
    @ApiResponse(responseCode = "200", description = "List of available routes")
    @GetMapping("/department/{departmentId}/available-routes")
    public ResponseEntity<List<Map<String, Object>>> getAvailableRoutes(@PathVariable String departmentId) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return ResponseEntity.status(403).build();
        }
        List<PreGeneratedRoute> routes = routeOptimizationService.getAvailableRoutes(departmentId);

        List<Map<String, Object>> response = routes.stream().map(route -> {
            Map<String, Object> routeData = new HashMap<>();
            routeData.put("routeId", route.getRouteId());
            routeData.put("binCount", route.getBinCount());
            routeData.put("bins", route.getRouteBins());
 routeData.put("polyline", route.getPolyline()); // ADD THIS
            return routeData;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Get pre-generated routes", description = "Retrieves pre-generated routes with polylines for a department")
    @ApiResponse(responseCode = "200", description = "Pre-generated routes with polyline data")
    @ApiResponse(responseCode = "500", description = "Error fetching routes")
    @GetMapping("/department/{departmentId}/pre-generated")
    public ResponseEntity<?> getPreGeneratedRoutes(@PathVariable String departmentId) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return ResponseEntity.status(403).build();
        }
        try {
            log.info("Fetching pre-generated routes for department: {}", departmentId);

            List<PreGeneratedRoute> preGenRoutes = routeOptimizationService.getAllPreGeneratedRoutes(departmentId);

            if (preGenRoutes.isEmpty()) {
                log.info("No pre-generated routes found, triggering generation...");
                routeOptimizationService.generateRoutesForDepartment(departmentId);
                preGenRoutes = routeOptimizationService.getAllPreGeneratedRoutes(departmentId);
            }

            // Convert to response format with polylines
            List<Map<String, Object>> response = new ArrayList<>();

            for (PreGeneratedRoute route : preGenRoutes) {
                Map<String, Object> routeMap = new HashMap<>();
                routeMap.put("vehicleId", route.getAssignedVehicleId());
                routeMap.put("binCount", route.getBinCount());
                routeMap.put("generatedAt", route.getGeneratedAt());
                routeMap.put("ageMinutes", route.getAgeInMinutes());
                routeMap.put("isStale", route.isStale());

                // Convert RouteBins to Bin objects for OSRM polyline
                List<Bin> bins = new ArrayList<>();
                for (RouteBin routeBin : route.getRouteBins()) {
                    Bin bin = binService.getBinById(routeBin.getId());
                    if (bin != null) {
                        bins.add(bin);
                    }
                }

                // Use the route's pre-calculated polyline if available, or build using PolylineService
                List<double[]> polyline = new ArrayList<>();
                if (route.getPolyline() != null && !route.getPolyline().isEmpty()) {
                    for (RoutePoint rp : route.getPolyline()) {
                        polyline.add(new double[]{rp.getLatitude(), rp.getLongitude()});
                    }
                } else {
                    Department dept = departmentService.getDepartmentById(departmentId).orElse(null);
                    polyline = polylineService.buildRoutePolyline(bins, dept);
                }

                // Convert RouteBins for response
                List<Map<String, Object>> binMaps = new ArrayList<>();
                for (RouteBin bin : route.getRouteBins()) {
                    Map<String, Object> binMap = new HashMap<>();
                    binMap.put("id", bin.getId());
                    binMap.put("latitude", bin.getLatitude());
                    binMap.put("longitude", bin.getLongitude());
                    binMaps.add(binMap);
                }
                routeMap.put("bins", binMaps);
                routeMap.put("polyline", polyline);

                response.add(routeMap);
            }

            log.info("Returning {} pre-generated routes with OSRM polylines", response.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching pre-generated routes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch routes: " + e.getMessage()));
        }
    }
    @Operation(summary = "Assign route to vehicle", description = "Assigns a pre-generated route to a vehicle and starts execution")
    @ApiResponse(responseCode = "200", description = "Route assigned successfully")
    @ApiResponse(responseCode = "404", description = "Route not found")
    @ApiResponse(responseCode = "409", description = "Route already assigned")
    @PostMapping("/assign-route")
    public ResponseEntity<?> assignRouteToVehicle(
            @RequestParam String routeId,
            @RequestParam String vehicleId,
            @RequestParam String departmentId) {

        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            log.info("Assigning route {} to vehicle {}", routeId, vehicleId);

            // Assign route to vehicle
            PreGeneratedRoute route = routeOptimizationService.assignRouteToVehicle(routeId, vehicleId);

            // Get bin IDs from route
            List<String> binIds = route.getRouteBins().stream()
                    .map(RouteBin::getId)
                    .collect(Collectors.toList());

            log.info("Route has {} bins: {}", binIds.size(), binIds);

            // Start execution with specific bins
            ActiveRoute activeRoute = routeExecutionService.startRouteWithSpecificBins(
                    departmentId,
                    vehicleId,
                    binIds
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "routeId", routeId,
                    "vehicleId", vehicleId,
                    "binCount", route.getBinCount()
            ));

        } catch (IllegalArgumentException e) {
            log.error("Route not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));

        } catch (IllegalStateException e) {
            log.error("Route already assigned: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Failed to assign route", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to assign route: " + e.getMessage()));
        }
    }
    @Operation(summary = "Vehicle returned to depot", description = "Called when vehicle completes route and returns to depot")
    @ApiResponse(responseCode = "200", description = "New route generated for returned vehicle")
    @PostMapping("/vehicle-returned")
    public ResponseEntity<?> vehicleReturned(
            @RequestParam String vehicleId,
            @RequestParam String departmentId) {

        try {
            log.info("Vehicle {} returned to depot", vehicleId);

            // Mark vehicle as available (should already be done)
            vehicleService.completeRoute(vehicleId);

            // Generate new route for this vehicle
            routeOptimizationService.generateRouteForReturningVehicle(vehicleId, departmentId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "New route generated for returned vehicle"
            ));

        } catch (Exception e) {
            log.error("Error handling returned vehicle: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Check critical bins", description = "Manually triggers critical bins check and route generation")
    @ApiResponse(responseCode = "200", description = "Critical bins checked successfully")
    @PostMapping("/check-critical-bins")
    public ResponseEntity<?> checkCriticalBins() {
        try {
            log.info("Manual critical bins check triggered");
            routeOptimizationService.checkCriticalBinsAndGenerateRoutes();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Critical bins checked and routes generated if needed"
            ));

        } catch (Exception e) {
            log.error("Error checking critical bins: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    @Operation(summary = "Get active vehicles", description = "Retrieves all currently active vehicles with route info")
    @ApiResponse(responseCode = "200", description = "List of active vehicles")
    @GetMapping("/active-vehicles")
    public ResponseEntity<List<Map<String, Object>>> getActiveVehicles() {
        List<Map<String, Object>> activeVehicles = routeExecutionService.getActiveVehiclesInfo();
        log.info("Found {} active vehicles", activeVehicles.size());
        return ResponseEntity.ok(activeVehicles);
    }



    @Operation(summary = "Get active route", description = "Retrieves the active route for a specific vehicle")
    @ApiResponse(responseCode = "200", description = "Active route found")
    @ApiResponse(responseCode = "404", description = "No active route for vehicle")
    @GetMapping("/active-route/{vehicleId}")
    public ResponseEntity<ActiveRoute> getActiveRoute(@PathVariable String vehicleId) {
        ActiveRoute route = routeExecutionService.getActiveRouteByVehicle(vehicleId);
        if (route != null) {
            log.info("Found active route for vehicle {}", vehicleId);
            return ResponseEntity.ok(route);
        }
        return ResponseEntity.notFound().build();
    }
    @Operation(summary = "Force generate routes", description = "Forces route generation for a department")
    @ApiResponse(responseCode = "200", description = "Routes generated")
    @PostMapping("/generate/{departmentId}")
    public ResponseEntity<?> forceGenerateRoutes(@PathVariable String departmentId) {
        routeOptimizationService.generateRoutesForDepartment(departmentId);
        return ResponseEntity.ok(Map.of(
                "message", "Routes generated",
                "departmentId", departmentId,
                "timestamp", Instant.now().toString()
        ));
    }


    @Operation(summary = "Generate routes", description = "Manually triggers route generation for a department")
    @ApiResponse(responseCode = "200", description = "Routes generated successfully")
    @PostMapping("/department/{departmentId}/generate")
    public ResponseEntity<?> generateRoutes(@PathVariable String departmentId) {
        try {
            log.info("Manual route generation triggered for department: {}", departmentId);
            routeOptimizationService.generateRoutesForDepartment(departmentId);

            List<PreGeneratedRoute> routes = routeOptimizationService.getAllPreGeneratedRoutes(departmentId);

            return ResponseEntity.ok(Map.of(
                    "message", "Routes generated successfully",
                    "routeCount", routes.size(),
                    "generatedAt", LocalDateTime.now()
            ));

        } catch (Exception e) {
            log.error("Error generating routes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate routes: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get route info", description = "Retrieves route freshness and staleness information")
    @ApiResponse(responseCode = "200", description = "Route info returned")
    @GetMapping("/department/{departmentId}/route-info")
    public ResponseEntity<?> getRouteInfo(@PathVariable String departmentId) {
        List<PreGeneratedRoute> routes = routeOptimizationService.getAllPreGeneratedRoutes(departmentId);

        Map<String, Object> info = new HashMap<>();
        info.put("totalRoutes", routes.size());
        info.put("staleRoutes", routes.stream().filter(PreGeneratedRoute::isStale).count());

        if (!routes.isEmpty()) {
            PreGeneratedRoute oldest = routes.stream()
                    .max(Comparator.comparing(PreGeneratedRoute::getAgeInMinutes))
                    .orElse(null);

            if (oldest != null) {
                info.put("oldestRouteAge", oldest.getAgeInMinutes());
                info.put("needsRefresh", oldest.isStale());
            }
        }

        return ResponseEntity.ok(info);
    }

    // ==================== AUTO-DISPATCH ENDPOINTS ====================

    @Operation(summary = "Get auto-dispatch status", description = "Returns the current auto-dispatch configuration and availability")
    @ApiResponse(responseCode = "200", description = "Auto-dispatch status returned")
    @GetMapping("/auto-dispatch/status/{departmentId}")
    public ResponseEntity<?> getAutoDispatchStatus(@PathVariable String departmentId) {
        Map<String, Object> status = autoDispatchService.getAutoDispatchStatus(departmentId);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Trigger auto-dispatch", description = "Manually triggers the auto-dispatch process for a department")
    @ApiResponse(responseCode = "200", description = "Auto-dispatch triggered")
    @PostMapping("/auto-dispatch/trigger/{departmentId}")
    public ResponseEntity<?> triggerAutoDispatch(@PathVariable String departmentId) {
        log.info("Manual auto-dispatch triggered for department: {}", departmentId);
        Map<String, Object> result = autoDispatchService.triggerAutoDispatch(departmentId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Enable auto-dispatch", description = "Enables automatic vehicle dispatching")
    @ApiResponse(responseCode = "200", description = "Auto-dispatch enabled")
    @PostMapping("/auto-dispatch/enable")
    public ResponseEntity<?> enableAutoDispatch() {
        autoDispatchService.setAutoDispatchEnabled(true);
        return ResponseEntity.ok(Map.of(
                "message", "Auto-dispatch enabled",
                "enabled", true,
                "timestamp", Instant.now().toString()
        ));
    }

    @Operation(summary = "Disable auto-dispatch", description = "Disables automatic vehicle dispatching")
    @ApiResponse(responseCode = "200", description = "Auto-dispatch disabled")
    @PostMapping("/auto-dispatch/disable")
    public ResponseEntity<?> disableAutoDispatch() {
        autoDispatchService.setAutoDispatchEnabled(false);
        return ResponseEntity.ok(Map.of(
                "message", "Auto-dispatch disabled",
                "enabled", false,
                "timestamp", Instant.now().toString()
        ));
    }

    @Operation(summary = "Unblock vehicle", description = "Clears the blocked state of a vehicle and triggers rescue reroute")
    @ApiResponse(responseCode = "200", description = "Vehicle unblocked successfully")
    @ApiResponse(responseCode = "404", description = "Vehicle route not found")
    @PostMapping("/unblock/{vehicleId}")
    public ResponseEntity<?> unblockVehicle(@PathVariable String vehicleId) {
        try {
            ActiveRoute route = routeExecutionService.getActiveRouteByVehicle(vehicleId);
            if (route == null) {
                return ResponseEntity.notFound().build();
            }
            
            route.setBlockedByIncident(false);
            route.clearRerouteHistory();
            routeExecutionService.saveRoute(route);
            
            log.info("Vehicle {} unblocked manually, will attempt rescue reroute", vehicleId);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Vehicle unblocked successfully",
                    "vehicleId", vehicleId,
                    "timestamp", Instant.now().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to unblock vehicle {}: {}", vehicleId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

}
