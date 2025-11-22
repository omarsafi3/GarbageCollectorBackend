package com.municipality.garbagecollectorbackend.routing;

import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class RouteOptimizationService {

    private static final double EPS = 1e-6;

    /**
     * Multi-vehicle routing:
     * - All vehicles start/end at the same depot (department).
     * - Capacity dimension 0 = number of bins; each bin consumes 1 unit.
     * - Each VehicleRouteResult contains ordered bin IDs for one vehicle.
     */
    public List<VehicleRouteResult> optimizeDepartmentRoutes(
            Optional<Department> depot,
            List<Vehicle> vehicles,
            List<Bin> bins,
            double maxRangeKm
    ) {
        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("At least one vehicle is required");
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

        for (Bin b : bins) {
            System.out.printf(
                    "Bin %s fillLevel=%d, lat=%.5f, lon=%.5f%n",
                    b.getId(), b.getFillLevel(), b.getLatitude(), b.getLongitude()
            );
        }

        // --- 1. Vehicle type with capacity: 5 bins per vehicle ---
        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("truckType")
                .addCapacityDimension(0, 5) // capacity dimension 0 = 5 bins
                .build();

        // --- 2. Build jsprit vehicles for each real Vehicle ---
        List<VehicleImpl> jspritVehicles = new ArrayList<>();
        for (Vehicle v : vehicles) {
            VehicleImpl jspritVehicle = VehicleImpl.Builder.newInstance(v.getId())
                    .setType(vehicleType)
                    .setStartLocation(depotLocation)
                    .setEndLocation(depotLocation)
                    .build();
            jspritVehicles.add(jspritVehicle);

            System.out.println("Added jsprit vehicle: " + v.getId()
                    + " with capacity 5 bins at depot");
        }

        // --- 3. Create jobs for each Bin (each bin uses 1 capacity unit) ---
        List<Service> jobs = new ArrayList<>();
        for (Bin bin : bins) {
            Service job = Service.Builder.newInstance(bin.getId())
                    .setName(bin.getId())
                    .addSizeDimension(0, 1)  // 1 bin-unit
                    .setLocation(Location.newInstance(bin.getLatitude(), bin.getLongitude()))
                    .build();
            jobs.add(job);
        }

        // --- 4. Build VRP with all jobs and all vehicles ---
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        jobs.forEach(vrpBuilder::addJob);
        jspritVehicles.forEach(vrpBuilder::addVehicle);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        VehicleRoutingProblem problem = vrpBuilder.build();

        // --- 5. Solve ---
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        System.out.println("Best solution cost: " + best.getCost());
        System.out.println("Number of routes in best solution: " + best.getRoutes().size());

        // --- 6. Extract per-vehicle ordered bin IDs ---
        List<VehicleRouteResult> results = new ArrayList<>();

        for (VehicleRoute vr : best.getRoutes()) {
            String vehicleId = vr.getVehicle().getId();
            List<String> routeBinIds = new ArrayList<>();

            System.out.println("Route for vehicle " + vehicleId
                    + " has " + vr.getActivities().size() + " activities");

            for (TourActivity act : vr.getActivities()) {
                double actLat = act.getLocation().getCoordinate().getX();
                double actLon = act.getLocation().getCoordinate().getY();

                // Match activity location to a Bin by coordinates
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
                // Optionally, add empty route result:
                // results.add(new VehicleRouteResult(v.getId(), List.of()));
            }
        }

        return results;
    }
}
