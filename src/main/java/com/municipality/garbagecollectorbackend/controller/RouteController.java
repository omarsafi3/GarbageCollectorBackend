package com.municipality.garbagecollectorbackend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.routing.DepartmentRoutingService;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import com.municipality.garbagecollectorbackend.routing.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private DepartmentRoutingService departmentRoutingService;

    @Autowired
    private BinService binService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private VehicleService vehicleService;
    @Autowired
    private RouteExecutionService routeExecutionService;

    /**
     * ✅ FIXED: Returns ONLY bins assigned to THIS specific vehicle
     * GET /api/routes/optimize?departmentId=...&vehicleId=...
     */
    @GetMapping("/optimize")
    public List<DTO.BinDTO> optimizeRoute(
            @RequestParam String departmentId,
            @RequestParam String vehicleId
    ) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);

        if (departmentOpt.isEmpty() || vehicleOpt.isEmpty()) {
            System.out.println("[RouteController] Department or vehicle not found");
            return List.of();
        }

        Department department = departmentOpt.get();

        // Get all critical bins for the department
        List<Bin> bins = binService.getAllBins().stream()
                .filter(bin -> bin.getFillLevel() >= 70)
                .toList();

        if (bins.isEmpty()) {
            System.out.println("[RouteController] No bins with fillLevel >= 70");
            return List.of();
        }

        // ✅ FIX: Get ALL available vehicles in department to calculate fair distribution
        List<Vehicle> allDepartmentVehicles = vehicleService.getAllVehicles().stream()
                .filter(v -> v.getDepartment() != null &&
                        departmentId.equals(v.getDepartment().getId()))
                .filter(Vehicle::getAvailable)
                .toList();

        if (allDepartmentVehicles.isEmpty()) {
            System.out.println("[RouteController] No available vehicles in department");
            return List.of();
        }

        System.out.println("[RouteController] Optimizing routes for " +
                allDepartmentVehicles.size() + " vehicles with " + bins.size() + " bins");

        // ✅ Calculate routes for ALL vehicles (fair distribution)
        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        allDepartmentVehicles, // All vehicles for fair distribution
                        bins,
                        30.0,
                        Collections.emptySet()
                );

        // ✅ CRITICAL: Return ONLY bins assigned to THIS specific vehicleId
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

        List<DTO.BinDTO> orderedBins = orderedBinIds.stream()
                .map(binIdMap::get)
                .filter(Objects::nonNull)
                .map(bin -> new DTO.BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                .toList();

        System.out.println("[RouteController] Returning " + orderedBins.size() +
                " bins for vehicle " + vehicleId);
        return orderedBins;
    }

    /**
     * NEW endpoint: optimize routes for a whole department.
     * GET /api/routes/optimize/department?departmentId=...
     */
    @GetMapping("/optimize/department")
    public List<DepartmentRoutingService.DepartmentRouteDTO> optimizeDepartment(
            @RequestParam String departmentId
    ) {
        System.out.println("[RouteController] Optimizing all routes for department " + departmentId);
        return departmentRoutingService.optimizeDepartmentRoutes(departmentId, 30.0);
    }

    /**
     * NEW: Optimize department routes, restricting to only specified bin IDs.
     * GET /api/routes/optimize/department/bins?departmentId=...&binIds=1,2,3,4
     */
    @GetMapping("/department-routes-with-polylines")
    public ResponseEntity<List<Map<String, Object>>> getDepartmentRoutesWithPolylines(
            @RequestParam String departmentId
    ) {
        try {
            System.out.println("📍 Fetching routes with polylines for department: " + departmentId);

            // Get critical bins (fill level >= 70)
            List<Bin> bins = binService.getAllBins().stream()
                    .filter(bin -> bin.getFillLevel() >= 70)
                    .collect(Collectors.toList());

            if (bins.isEmpty()) {
                System.out.println("⚠️ No bins with fillLevel >= 70");
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Get available vehicles for this department
            List<Vehicle> availableVehicles = vehicleService.getAllVehicles().stream()
                    .filter(v -> v.getDepartment() != null &&
                            departmentId.equals(v.getDepartment().getId()))
                    .filter(Vehicle::getAvailable)
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                System.out.println("⚠️ No available vehicles");
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

                // Build polyline
                List<double[]> polyline = buildRoutePolyline(routeBins);

                Map<String, Object> routeData = new HashMap<>();
                routeData.put("vehicleId", vehicleId);
                routeData.put("bins", routeBins);
                routeData.put("polyline", polyline);

                routesWithPolylines.add(routeData);
            }

            System.out.println("✅ Returning " + routesWithPolylines.size() + " routes");
            return ResponseEntity.ok(routesWithPolylines);

        } catch (Exception e) {
            System.err.println("❌ Failed to fetch routes: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/optimize/department/bins")
    public List<DepartmentRoutingService.DepartmentRouteDTO> optimizeDepartmentWithBins(
            @RequestParam String departmentId,
            @RequestParam List<String> binIds
    ) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        if (departmentOpt.isEmpty() || binIds == null || binIds.isEmpty()) {
            return List.of();
        }

        // Find only the bins with these IDs
        List<Bin> allBins = binService.getAllBins();
        Set<String> wanted = new HashSet<>(binIds);
        List<Bin> bins = allBins.stream()
                .filter(b -> wanted.contains(b.getId()))
                .toList();

        if (bins.isEmpty()) return List.of();

        // Only available vehicles of this department
        List<Vehicle> vehicles = vehicleService.getAllVehicles().stream()
                .filter(v -> v.getDepartment() != null &&
                        departmentId.equals(v.getDepartment().getId()))
                .filter(Vehicle::getAvailable)
                .toList();

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

            List<DTO.BinDTO> binDtos = ordered.stream()
                    .map(binIdMap::get)
                    .filter(Objects::nonNull)
                    .map(bin -> new DTO.BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                    .toList();

            DepartmentRoutingService.DepartmentRouteDTO dto =
                    new DepartmentRoutingService.DepartmentRouteDTO(vehicleId, binDtos);
            response.add(dto);
        }

        System.out.println("[RouteController] /optimize/department/bins responding with "
                + response.size() + " routes");
        response.forEach(r -> {
            System.out.println("  vehicleId=" + r.getVehicleId()
                    + " bins=" + r.getBins().stream().map(DTO.BinDTO::getId).toList());
        });

        return response;
    }
    @PostMapping("/execute-all-managed")
    public ResponseEntity<Map<String, Object>> executeAllManagedRoutes(
            @RequestParam String departmentId) {

        System.out.println("🚀 Starting routes for ALL vehicles in department " + departmentId);

        try {
            // Get all available vehicles
            List<Vehicle> availableVehicles = vehicleService.getAllVehicles().stream()
                    .filter(v -> v.getDepartment() != null &&
                            departmentId.equals(v.getDepartment().getId()))
                    .filter(Vehicle::getAvailable)
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No available vehicles");
                return ResponseEntity.badRequest().body(error);
            }

            // ✅ FIX: Get department
            Optional<Department> deptOpt = departmentService.getDepartmentById(departmentId);
            if (deptOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Department not found");
                return ResponseEntity.badRequest().body(error);
            }

            // ✅ FIX: Get all critical bins
            List<Bin> criticalBins = binService.getAllBins().stream()
                    .filter(bin -> bin.getFillLevel() >= 70)
                    .collect(Collectors.toList());

            if (criticalBins.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "No bins need collection");
                return ResponseEntity.badRequest().body(error);
            }

            // ✅ FIX: Calculate routes for ALL vehicles at once (fair distribution)
            List<VehicleRouteResult> allRoutes = routeOptimizationService.optimizeDepartmentRoutes(
                    deptOpt,
                    availableVehicles,
                    criticalBins,
                    30.0,
                    Collections.emptySet()
            );

            System.out.println("✅ Calculated " + allRoutes.size() + " unique routes");

            List<Map<String, Object>> routeResults = new ArrayList<>();

            // ✅ FIX: Now start each vehicle's route with PRE-CALCULATED bins
            for (VehicleRouteResult routeResult : allRoutes) {
                try {
                    String vehicleId = routeResult.getVehicleId();
                    List<String> binIds = routeResult.getOrderedBinIds();

                    System.out.println("🚛 Starting vehicle " + vehicleId + " with " + binIds.size() + " bins");

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

                    System.out.println("✅ Started route for vehicle " + vehicleId);

                } catch (Exception e) {
                    System.err.println("❌ Failed to start route for vehicle " + routeResult.getVehicleId() + ": " + e.getMessage());

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
            System.err.println("❌ Failed to start routes: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }


    private List<double[]> buildRoutePolyline(List<Bin> bins) {
        List<double[]> polyline = new ArrayList<>();

        if (bins.isEmpty()) {
            return polyline;
        }

        // Build coordinates list: depot → bins → depot
        List<String> coordinates = new ArrayList<>();
        coordinates.add("9.0,34.0"); // Depot (lng,lat format for OSRM)

        for (Bin bin : bins) {
            coordinates.add(bin.getLongitude() + "," + bin.getLatitude());
        }

        coordinates.add("9.0,34.0"); // Return to depot

        // Build OSRM URL
        String coordsString = String.join(";", coordinates);
        String osrmUrl = "https://router.project-osrm.org/route/v1/driving/" + coordsString
                + "?overview=full&geometries=geojson";

        try {
            // Call OSRM API
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(osrmUrl, String.class);

            // Parse JSON response
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if ("Ok".equals(root.path("code").asText())) {
                JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");

                for (JsonNode coord : coords) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    polyline.add(new double[]{lat, lng}); // Lat, Lng for Leaflet
                }

                System.out.println("✅ OSRM returned " + polyline.size() + " points for route with " + bins.size() + " bins");
            } else {
                System.err.println("⚠️ OSRM returned code: " + root.path("code").asText());
                // Fallback to straight lines
                return buildStraightLinePolyline(bins);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to fetch OSRM polyline: " + e.getMessage());
            // Fallback to straight lines
            return buildStraightLinePolyline(bins);
        }

        return polyline;
    }
    private List<double[]> buildStraightLinePolyline(List<Bin> bins) {
        List<double[]> polyline = new ArrayList<>();
        polyline.add(new double[]{34.0, 9.0}); // Depot

        for (Bin bin : bins) {
            polyline.add(new double[]{bin.getLatitude(), bin.getLongitude()});
        }

        polyline.add(new double[]{34.0, 9.0}); // Return to depot
        return polyline;
    }



    @PostMapping("/execute")
    public void executeVehicleRoute(@RequestParam String vehicleId,
                                    @RequestBody List<String> binIds) {
        System.out.println("[RouteController] Executing route for vehicle " + vehicleId +
                " with " + binIds.size() + " bins");
        departmentRoutingService.executeRoute(vehicleId, binIds);
    }



    @PostMapping("/execute/step")
    public void executeVehicleRouteStep(@RequestParam String vehicleId,
                                        @RequestParam String binId) {
        System.out.println("[RouteController] Executing step for vehicle " + vehicleId +
                " at bin " + binId);
        vehicleService.emptyBin(vehicleId, binId);
    }
    @PostMapping("/execute-managed")
    public ResponseEntity<Map<String, Object>> executeManagedRoute(
            @RequestParam String departmentId,
            @RequestParam String vehicleId) {

        System.out.println("🚀 Starting managed route for vehicle " + vehicleId);

        try {
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
            System.err.println("❌ Failed to start route: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());

            return ResponseEntity.badRequest().body(error);
        }
    }

}
