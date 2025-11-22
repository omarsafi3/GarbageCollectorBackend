package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.DTO;
import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.routing.DepartmentRoutingService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import com.municipality.garbagecollectorbackend.routing.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    /**
     * Existing endpoint: optimize route for a single selected vehicle.
     * Frontend you already built can keep using this.
     *
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
            return List.of();
        }

        Department department = departmentOpt.get();
        Vehicle vehicle = vehicleOpt.get();

        // Bins with fillLevel >= 70
        List<Bin> bins = binService.getAllBins().stream()
                .filter(bin -> bin.getFillLevel() >= 70)
                .toList();

        if (bins.isEmpty()) {
            System.out.println("[RouteController] No bins with fillLevel >= 70");
            return List.of();
        }

        // Single-vehicle list
        List<Vehicle> vehicles = List.of(vehicle);

        // Use multi-vehicle optimizer, but only with one vehicle
        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        vehicles,
                        bins,
                        30.0
                );

        Optional<VehicleRouteResult> maybeSelectedRoute = routeResults.stream()
                .filter(r -> vehicleId.equals(r.getVehicleId()))
                .findFirst();

        if (maybeSelectedRoute.isEmpty()) {
            System.out.println("[RouteController] No route found for vehicle " + vehicleId);
            return List.of();
        }

        List<String> orderedBinIds = maybeSelectedRoute.get().getBinIds();

        System.out.println("[RouteController] orderedBinIds: " + orderedBinIds);
        bins.forEach(b -> System.out.println("bin.getId(): " + b.getId() + " class: " + b.getId().getClass()));

        Map<String, Bin> binIdMap = bins.stream()
                .collect(Collectors.toMap(bin -> bin.getId().toString(), bin -> bin));
        for (String oid : orderedBinIds) {
            System.out.println("lookup id: " + oid + " present? " + binIdMap.containsKey(oid));
        }

        List<DTO.BinDTO> orderedBins = orderedBinIds.stream()
                .map(binIdMap::get)
                .filter(Objects::nonNull)
                .map(bin -> new DTO.BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                .toList();

        System.out.println("[RouteController] Returning " + orderedBins.size() + " bins");
        return orderedBins;
    }

    /**
     * NEW endpoint: optimize routes for a whole department.
     *
     * Rules applied in DepartmentRoutingService:
     * - only bins with fillLevel >= 70,
     * - only vehicles + employees of that department,
     * - 2 available employees required per active truck.
     *
     * GET /api/routes/optimize/department?departmentId=...
     */
    @GetMapping("/optimize/department")
    public List<DepartmentRoutingService.DepartmentRouteDTO> optimizeDepartment(
            @RequestParam String departmentId
    ) {
        // Controller stays thin: delegate to domain service
        return departmentRoutingService.optimizeDepartmentRoutes(departmentId, 30.0);
    }
}
