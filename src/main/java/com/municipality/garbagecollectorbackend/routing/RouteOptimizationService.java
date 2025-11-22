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
import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.RouteBin;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class RouteOptimizationService {
    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private BinService binService;

    private static final double EPS = 1e-6;

    /**
     * Multi-vehicle routing:
     * - All vehicles start/end at the same depot (department).
     * - Capacity dimension 0 = "bin slots".
     *   Each bin consumes 1 slot.
     *   Each vehicle's max slots depend on its current fillLevel.
     * - overfillBinIds: ids of bins that have an active OVERFILL incident (high priority).
     *
     * NOTE: This only controls which bins to VISIT.
     * Partial loading (truck not emptying a bin completely) is handled
     * after routing in your own domain logic.
     */
    // ✅ ADD THIS TO RouteOptimizationService.java (NOT RouteExecutionService)
    public List<RouteBin> getOptimizedRoute(String departmentId, String vehicleId) {
        // Get department
        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));

        // Get vehicle
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));
        List<Vehicle> vehicles = List.of(vehicle);

        // Get all bins ≥80% full
        List<Bin> bins = binService.getAllBins().stream()
                .filter(b -> b.getFillLevel() >= 80)
                .toList();

        // Overfill bins
        Set<String> overfillBinIds = new HashSet<>();
        double maxRangeKm = 100;

        // Compute routes
        List<VehicleRouteResult> results = optimizeDepartmentRoutes(
                Optional.of(department),
                vehicles,
                bins,
                maxRangeKm,
                overfillBinIds
        );

        // Find route for this vehicle
        for (VehicleRouteResult result : results) {
            if (result.getVehicleId().equals(vehicleId)) {
                // Convert bin IDs to RouteBin
                List<RouteBin> routeBins = new ArrayList<>();
                for (String binId : result.getOrderedBinIds()) {  // ✅ Fixed method name
                    Bin bin = binService.getBinById(binId);
                    routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
                }
                return routeBins;
            }
        }

        return List.of(); // Empty route if vehicle not assigned
    }


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

        System.out.println("=== optimizeDepartmentRoutes ===");
        System.out.println("Department: " + dept.getId()
                + " @ lat=" + dept.getLatitude()
                + ", lon=" + dept.getLongitude());
        System.out.println("Vehicles count: " + vehicles.size());
        System.out.println("Candidate bins (before any filtering): " + bins.size());
        System.out.println("Overfill bin IDs: " + overfillBinIds);

        for (Bin b : bins) {
            System.out.printf(
                    "Bin %s fillLevel=%d, lat=%.5f, lon=%.5f%n",
                    b.getId(), b.getFillLevel(), b.getLatitude(), b.getLongitude()
            );
        }

        // --- 1+2. Build a VehicleType and Vehicle for each real Vehicle, with capacity in "bin slots" ---
        final int MAX_BINS_WHEN_EMPTY = 5;

        List<VehicleImpl> jspritVehicles = new ArrayList<>();
        for (Vehicle v : vehicles) {
            int maxBinsForThisTruck;
            double fill = v.getFillLevel();

            if (fill <= 20) {          // 0–20% full
                maxBinsForThisTruck = 5;
            } else if (fill <= 50) {   // 20–50% full
                maxBinsForThisTruck = 4;
            } else if (fill <= 80) {   // 50–80% full
                maxBinsForThisTruck = 3;
            } else if (fill <= 95) {   // 80–95% full
                maxBinsForThisTruck = 2;
            } else {                   // >95% full
                maxBinsForThisTruck = 1;
            }

            VehicleTypeImpl vehicleTypeForThisTruck = VehicleTypeImpl.Builder
                    .newInstance("truckType-" + v.getId())
                    .addCapacityDimension(0, maxBinsForThisTruck)
                    .build();

            VehicleImpl jspritVehicle = VehicleImpl.Builder.newInstance(v.getId())
                    .setType(vehicleTypeForThisTruck)
                    .setStartLocation(depotLocation)
                    .setEndLocation(depotLocation)
                    .build();

            jspritVehicles.add(jspritVehicle);

            System.out.println("Added jsprit vehicle " + v.getId()
                    + " fillLevel=" + fill
                    + "% -> maxBins=" + maxBinsForThisTruck);
        }


        // --- 3. Create jobs for each Bin (each bin uses 1 slot) ---
        List<Service> jobs = new ArrayList<>();
        for (Bin bin : bins) {
            Service job = Service.Builder.newInstance(bin.getId())
                    .setName(bin.getId())
                    .addSizeDimension(0, 1)  // 1 bin-unit
                    .setLocation(Location.newInstance(bin.getLatitude(), bin.getLongitude()))
                    .build();
            jobs.add(job);

            System.out.println("Added job for bin " + bin.getId()
                    + " (fillLevel=" + bin.getFillLevel() + "%)");
        }

        // --- 4. Build VRP with all jobs and all vehicles ---
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        jobs.forEach(vrpBuilder::addJob);
        jspritVehicles.forEach(vrpBuilder::addVehicle);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        VehicleRoutingProblem problem = vrpBuilder.build();

        // --- 5. Create algorithm with custom objective: penalize unserved overfill bins ---
        Jsprit.Builder algoBuilder = Jsprit.Builder.newInstance(problem);

        Set<String> overfillIdsFinal = overfillBinIds; // effectively final

        algoBuilder.setObjectiveFunction(solution -> {
            double baseCost = solution.getCost();
            double penalty = 0.0;

            Collection<Job> unassigned = solution.getUnassignedJobs();
            if (!unassigned.isEmpty() && !overfillIdsFinal.isEmpty()) {
                for (Job unassignedJob : unassigned) {
                    if (overfillIdsFinal.contains(unassignedJob.getId())) {
                        // Big penalty per unserved overfill bin
                        penalty += 1_000.0;
                    }
                }
            }

            return baseCost + penalty;
        });

        VehicleRoutingAlgorithm algorithm = algoBuilder.buildAlgorithm();

        // --- 6. Solve ---
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        System.out.println("Best solution cost (with penalty): " + best.getCost());
        System.out.println("Number of routes in best solution: " + best.getRoutes().size());
        System.out.println("Unassigned jobs: " +
                best.getUnassignedJobs().stream()
                        .map(Job::getId)
                        .collect(Collectors.toList()));

        // --- 7. Extract per-vehicle ordered bin IDs ---
        List<VehicleRouteResult> results = new ArrayList<>();

        for (VehicleRoute vr : best.getRoutes()) {
            String vehicleId = vr.getVehicle().getId();
            List<String> routeBinIds = new ArrayList<>();

            System.out.println("Route for vehicle " + vehicleId
                    + " has " + vr.getActivities().size() + " activities");

            for (TourActivity act : vr.getActivities()) {
                double actLat = act.getLocation().getCoordinate().getX();
                double actLon = act.getLocation().getCoordinate().getY();

                Bin matched = bins.stream()
                        .filter(b ->
                                Math.abs(b.getLatitude() - actLat) < EPS &&
                                        Math.abs(b.getLongitude() - actLon) < EPS)
                        .findFirst()
                        .orElse(null);

                if (matched != null) {
                    routeBinIds.add(matched.getId());
                    System.out.printf(
                            "  Matched activity at (%.6f, %.6f) to Bin %s%n",
                            actLat, actLon, matched.getId()
                    );
                } else {
                    System.out.printf(
                            "  No bin matched activity at lat=%.6f lon=%.6f%n",
                            actLat, actLon
                    );
                }
            }

            System.out.println("Vehicle " + vehicleId + " orderedBinIds: " + routeBinIds);
            results.add(new VehicleRouteResult(vehicleId, routeBinIds));

        }

        // Debug: vehicles without a route (unused trucks)
        for (Vehicle v : vehicles) {
            boolean hasRoute = results.stream()
                    .anyMatch(r -> r.getVehicleId().equals(v.getId()));
            if (!hasRoute) {
                System.out.println("Vehicle " + v.getId() + " was unused in solution (no route).");
            }
        }

        return results;
    }

}
