package com.municipality.garbagecollectorbackend.routing;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.municipality.garbagecollectorbackend.DTO.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.DTO.PreGeneratedRoute;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.RouteBin;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@Slf4j
public class RouteOptimizationService {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private BinService binService;

    // ✅ Store pre-generated routes in memory
    private final Map<String, PreGeneratedRoute> preGeneratedRoutes = new ConcurrentHashMap<>();

    private static final double EPS = 1e-6;
    private static final String DEFAULT_DEPARTMENT_ID = "6920266d0b737026e2496c54";

    // ============================================
    // SCHEDULED ROUTE GENERATION
    // ============================================

    /**
     * Auto-generates optimized routes every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void autoGenerateRoutes() {
        log.info("🔄 Auto-generating optimized routes for all vehicles...");
        generateRoutesForDepartment(DEFAULT_DEPARTMENT_ID);
    }

    /**
     * Generate routes for all available vehicles in a department
     */
    /**
     * Generate routes for all available vehicles in a department
     */
    public void generateRoutesForDepartment(String departmentId) {
        log.info("📍 Generating shared route pool for department: {}", departmentId);

        try {
            Department department = departmentService.getDepartmentById(departmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));

            // Get all available vehicles (for capacity planning)
            List<Vehicle> allVehicles = vehicleService.getAllVehicles();
            List<Vehicle> availableVehicles = allVehicles.stream()
                    .filter(v -> v.getDepartment() != null && v.getDepartment().getId().equals(departmentId))
                    .filter(Vehicle::getAvailable)
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                log.warn("⚠️ No available vehicles for department {}", departmentId);
                return;
            }

            // Get bins ≥80%
            List<Bin> allBins = binService.getAllBins();
            List<Bin> prioritizedBins = allBins.stream()
                    .filter(b -> b.getFillLevel() >= 80)
                    .collect(Collectors.toList());

            if (prioritizedBins.isEmpty()) {
                log.info("ℹ️ No bins ≥80% for department {}", departmentId);
                return;
            }

            Set<String> overfillBinIds = prioritizedBins.stream()
                    .filter(b -> b.getFillLevel() > 100)
                    .map(Bin::getId)
                    .collect(Collectors.toSet());

            log.info("📊 Generating routes: {} vehicles, {} bins (including {} overfilled)",
                    availableVehicles.size(), prioritizedBins.size(), overfillBinIds.size());

            // ✅ CHANGED: Generate routes but DON'T assign to vehicles yet
            List<VehicleRouteResult> results = optimizeDepartmentRoutes(
                    Optional.of(department),
                    availableVehicles,
                    prioritizedBins,
                    100.0,
                    overfillBinIds
            );

            // ✅ NEW: Clear old routes for this department
            preGeneratedRoutes.entrySet().removeIf(entry ->
                    entry.getValue().getDepartmentId().equals(departmentId)
            );

            // ✅ CHANGED: Store routes with unique IDs (NOT tied to vehicles)
            LocalDateTime now = LocalDateTime.now();
            int routeNumber = 1;

            for (VehicleRouteResult result : results) {
                List<RouteBin> routeBins = new ArrayList<>();
                for (String binId : result.getOrderedBinIds()) {
                    Bin bin = binService.getBinById(binId);
                    if (bin != null) {
                        routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
                    }
                }

                // ✅ NEW: Generate unique route ID
                String routeId = departmentId + "-route-" + routeNumber++;

                PreGeneratedRoute preGenRoute = new PreGeneratedRoute(
                        routeId,
                        departmentId,
                        routeBins,
                        now,
                        routeBins.size()
                );

                // ✅ CHANGED: Store by routeId, NOT vehicleId
                preGeneratedRoutes.put(routeId, preGenRoute);

                log.info("✅ Generated route {}: {} bins", routeId, routeBins.size());
            }

            log.info("✅ Successfully generated {} routes for department {}", results.size(), departmentId);

        } catch (Exception e) {
            log.error("❌ Failed to generate routes for department {}", departmentId, e);
        }
    }

    // ✅ NEW: Get all AVAILABLE (unassigned) routes
    public List<PreGeneratedRoute> getAvailableRoutes(String departmentId) {
        return preGeneratedRoutes.values().stream()
                .filter(route -> route.getDepartmentId().equals(departmentId))
                .filter(PreGeneratedRoute::isAvailable)
                .collect(Collectors.toList());
    }

    // ✅ NEW: Assign route to vehicle
    public PreGeneratedRoute assignRouteToVehicle(String routeId, String vehicleId) {
        PreGeneratedRoute route = preGeneratedRoutes.get(routeId);

        if (route == null) {
            throw new IllegalArgumentException("Route not found: " + routeId);
        }

        if (!route.isAvailable()) {
            throw new IllegalStateException("Route already assigned to vehicle: " + route.getAssignedVehicleId());
        }

        route.assignToVehicle(vehicleId);
        log.info("✅ Assigned route {} to vehicle {}", routeId, vehicleId);

        return route;
    }

    // ✅ NEW: Release route when vehicle completes/fails
    public void releaseRoute(String routeId) {
        PreGeneratedRoute route = preGeneratedRoutes.get(routeId);
        if (route != null) {
            route.release();
            log.info("✅ Released route {}", routeId);
        }
    }


    // ============================================
    // ROUTE RETRIEVAL
    // ============================================

    /**
     * Get pre-generated route for a specific vehicle
     */
    public PreGeneratedRoute getPreGeneratedRoute(String vehicleId) {
        return preGeneratedRoutes.get(vehicleId);
    }

    /**
     * Get all pre-generated routes for a department
     */
    public List<PreGeneratedRoute> getAllPreGeneratedRoutes(String departmentId) {
        return preGeneratedRoutes.values().stream()
                .filter(route -> route.getDepartmentId().equals(departmentId))
                .collect(Collectors.toList());
    }

    /**
     * Clear pre-generated route after execution
     */
    public void clearPreGeneratedRoute(String vehicleId) {
        preGeneratedRoutes.remove(vehicleId);
        log.info("🗑️ Cleared pre-generated route for vehicle {}", vehicleId);
    }

    /**
     * Get optimized route - uses pre-generated if available, generates on-demand otherwise
     */
    public List<RouteBin> getOptimizedRoute(String departmentId, String vehicleId) {
        // First check if we have a pre-generated route
        PreGeneratedRoute preGenRoute = preGeneratedRoutes.get(vehicleId);
        if (preGenRoute != null && !preGenRoute.isStale()) {
            log.info("📦 Using pre-generated route for vehicle {} (age: {} minutes)",
                    vehicleId, preGenRoute.getAgeInMinutes());
            return preGenRoute.getRouteBins();
        }

        // Fallback: generate on-demand
        log.warn("⚠️ No valid pre-generated route for vehicle {}, generating on-demand...", vehicleId);

        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));

        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));
        List<Vehicle> vehicles = List.of(vehicle);

        List<Bin> bins = binService.getAllBins().stream()
                .filter(b -> b.getFillLevel() >= 80)
                .collect(Collectors.toList());

        Set<String> overfillBinIds = bins.stream()
                .filter(b -> b.getFillLevel() > 100)
                .map(Bin::getId)
                .collect(Collectors.toSet());

        List<VehicleRouteResult> results = optimizeDepartmentRoutes(
                Optional.of(department),
                vehicles,
                bins,
                100.0,
                overfillBinIds
        );

        for (VehicleRouteResult result : results) {
            if (result.getVehicleId().equals(vehicleId)) {
                List<RouteBin> routeBins = new ArrayList<>();
                for (String binId : result.getOrderedBinIds()) {
                    Bin bin = binService.getBinById(binId);
                    if (bin != null) {
                        routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
                    }
                }
                return routeBins;
            }
        }

        return List.of();
    }

    // ============================================
    // JSPRIT OPTIMIZATION ALGORITHM
    // ============================================

    /**
     * Multi-vehicle routing optimization using JSprit
     * - All vehicles start/end at the same depot (department)
     * - Capacity dimension 0 = "bin slots"
     * - Each bin consumes 1 slot
     * - Vehicle capacity depends on current fill level
     * - Prioritizes overfilled bins
     */
    public List<VehicleRouteResult> optimizeDepartmentRoutes(
            Optional<Department> depot,
            List<Vehicle> vehicles,
            List<Bin> bins,
            double maxRangeKm,
            Set<String> overfillBinIds
    ) {
        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("At least one vehicle is required");
        }

        if (overfillBinIds == null) {
            overfillBinIds = Collections.emptySet();
        }

        Department dept = depot.orElseThrow(
                () -> new IllegalArgumentException("Depot (Department) is required")
        );
        Location depotLocation = Location.newInstance(dept.getLatitude(), dept.getLongitude());

        log.debug("=== JSprit Optimization ===");
        log.debug("Department: {} @ lat={}, lon={}", dept.getId(), dept.getLatitude(), dept.getLongitude());
        log.debug("Vehicles count: {}", vehicles.size());
        log.debug("Candidate bins: {}", bins.size());
        log.debug("Overfill bin IDs: {}", overfillBinIds);

        // --- 1. Build vehicles with capacity based on fill level ---
        List<VehicleImpl> jspritVehicles = new ArrayList<>();
        for (Vehicle v : vehicles) {
            int maxBinsForThisTruck = calculateVehicleCapacity(v.getFillLevel());

            VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder
                    .newInstance("truckType-" + v.getId())
                    .addCapacityDimension(0, maxBinsForThisTruck)
                    .build();

            VehicleImpl jspritVehicle = VehicleImpl.Builder.newInstance(v.getId())
                    .setType(vehicleType)
                    .setStartLocation(depotLocation)
                    .setEndLocation(depotLocation)
                    .build();

            jspritVehicles.add(jspritVehicle);

            log.debug("Vehicle {} fillLevel={}% -> capacity={} bins",
                    v.getId(), v.getFillLevel(), maxBinsForThisTruck);
        }

        // --- 2. Create jobs for each bin ---
        List<Service> jobs = new ArrayList<>();
        for (Bin bin : bins) {
            Service job = Service.Builder.newInstance(bin.getId())
                    .setName(bin.getId())
                    .addSizeDimension(0, 1)  // Each bin uses 1 capacity unit
                    .setLocation(Location.newInstance(bin.getLatitude(), bin.getLongitude()))
                    .build();
            jobs.add(job);
        }

        // --- 3. Build VRP ---
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        jobs.forEach(vrpBuilder::addJob);
        jspritVehicles.forEach(vrpBuilder::addVehicle);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        VehicleRoutingProblem problem = vrpBuilder.build();

        // --- 4. Create algorithm with penalty for unserved overfill bins ---
        Jsprit.Builder algoBuilder = Jsprit.Builder.newInstance(problem);

        Set<String> overfillIdsFinal = overfillBinIds;
        algoBuilder.setObjectiveFunction(solution -> {
            double baseCost = solution.getCost();
            double penalty = 0.0;

            Collection<Job> unassigned = solution.getUnassignedJobs();
            if (!unassigned.isEmpty() && !overfillIdsFinal.isEmpty()) {
                for (Job unassignedJob : unassigned) {
                    if (overfillIdsFinal.contains(unassignedJob.getId())) {
                        penalty += 1_000.0; // Heavy penalty for unserved overfill bins
                    }
                }
            }

            return baseCost + penalty;
        });

        VehicleRoutingAlgorithm algorithm = algoBuilder.buildAlgorithm();

        // --- 5. Solve ---
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        log.debug("Best solution cost: {}", best.getCost());
        log.debug("Routes count: {}", best.getRoutes().size());
        log.debug("Unassigned jobs: {}",
                best.getUnassignedJobs().stream().map(Job::getId).collect(Collectors.toList()));

        // --- 6. Extract per-vehicle ordered bin IDs ---
        List<VehicleRouteResult> results = new ArrayList<>();

        for (VehicleRoute vr : best.getRoutes()) {
            String vehicleId = vr.getVehicle().getId();
            List<String> routeBinIds = new ArrayList<>();

            for (TourActivity act : vr.getActivities()) {
                double actLat = act.getLocation().getCoordinate().getX();
                double actLon = act.getLocation().getCoordinate().getY();

                Bin matched = bins.stream()
                        .filter(b -> Math.abs(b.getLatitude() - actLat) < EPS
                                && Math.abs(b.getLongitude() - actLon) < EPS)
                        .findFirst()
                        .orElse(null);

                if (matched != null) {
                    routeBinIds.add(matched.getId());
                }
            }

            results.add(new VehicleRouteResult(vehicleId, routeBinIds));
            log.debug("Vehicle {} assigned {} bins", vehicleId, routeBinIds.size());
        }

        return results;
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Calculate vehicle capacity based on current fill level
     */
    private int calculateVehicleCapacity(double fillLevel) {
        if (fillLevel <= 20) return 5;
        if (fillLevel <= 50) return 4;
        if (fillLevel <= 80) return 3;
        if (fillLevel <= 95) return 2;
        return 1;
    }
}
