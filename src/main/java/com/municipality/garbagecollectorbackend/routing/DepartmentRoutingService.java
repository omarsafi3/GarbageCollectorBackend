package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.DTO.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.EmployeeService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates department-level routing:
 * - determines which vehicles can leave (2 available employees per truck),
 * - selects and prioritizes bins (OVERFILL first, then fillLevel >= 70),
 * - delegates VRP to RouteOptimizationService,
 * - maps routes to DTOs.
 */
@Service
public class DepartmentRoutingService {

    private final RouteOptimizationService routeOptimizationService;
    private final BinService binService;
    private final DepartmentService departmentService;
    private final VehicleService vehicleService;
    private final EmployeeService employeeService;
    private final IncidentService incidentService;

    @Autowired
    public DepartmentRoutingService(
            RouteOptimizationService routeOptimizationService,
            BinService binService,
            DepartmentService departmentService,
            VehicleService vehicleService,
            EmployeeService employeeService,
            IncidentService incidentService
    ) {
        this.routeOptimizationService = routeOptimizationService;
        this.binService = binService;
        this.departmentService = departmentService;
        this.vehicleService = vehicleService;
        this.employeeService = employeeService;
        this.incidentService = incidentService;
    }

    /**
     * Optimize routes for a department considering:
     * - bins prioritized by incidents (OVERFILL first, then fillLevel >= 70),
     * - only vehicles and employees of that department,
     * - dispatch rule: 2 available employees per active truck.
     */
    public List<DepartmentRouteDTO> optimizeDepartmentRoutes(String departmentId, double maxRangeKm) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        if (departmentOpt.isEmpty()) {
            return List.of();
        }
        Department department = departmentOpt.get();

        // ---------- 1) BINS WITH PRIORITY (OVERFILL FIRST) ----------

        // all bins (no direct department reference on Bin)
        List<Bin> allBins = binService.getAllBins();

        if (allBins.isEmpty()) {
            System.out.println("[DeptRouting] No bins in system");
            return List.of();
        }

        // ACTIVE incidents
        List<Incident> activeIncidents = incidentService.getActiveIncidents();

        // ACTIVE OVERFILL incidents (bin != null)
        List<Incident> activeOverfillIncidents = activeIncidents.stream()
                .filter(i -> i.getType() == IncidentType.OVERFILL
                        && i.getBin() != null)
                .toList();
        // IDs of bins that have an OVERFILL incident
        Set<String> overfillBinIds = activeOverfillIncidents.stream()
                .map(i -> i.getBin().getId())
                .collect(Collectors.toSet());

        // overfilled bins (priority)
        List<Bin> overfillBins = allBins.stream()
                .filter(b -> overfillBinIds.contains(b.getId()))
                .toList();

        // normal high-fill bins (>= 70) without overfill incident
        List<Bin> normalHighBins = allBins.stream()
                .filter(b -> b.getFillLevel() >= 70 && !overfillBinIds.contains(b.getId()))
                .toList();

        // final candidate bins: overfill first, then high-fill
        List<Bin> bins = new ArrayList<>();
        bins.addAll(overfillBins);
        bins.addAll(normalHighBins);

        if (bins.isEmpty()) {
            System.out.println("[DeptRouting] No candidate bins (no OVERFILL and no bin >= 70%)");
            return List.of();
        }

        System.out.println("[DeptRouting] Bins summary:");
        System.out.println("  overfillBins=" + overfillBins.size()
                + ", normalHighBins=" + normalHighBins.size()
                + ", total=" + bins.size());

        // ---------- 2) EMPLOYEES & VEHICLES (DISPATCH RULE) ----------

        // employees of this department
        List<Employee> employeesInDept = employeeService.getAllEmployees().stream()
                .filter(e -> e.getDepartment() != null &&
                        departmentId.equals(e.getDepartment().getId()))
                .toList();

        long availableEmployeesInDept = employeesInDept.stream()
                .filter(Employee::getAvailable)
                .count();

        // vehicles of this department
        List<Vehicle> vehiclesInDept = vehicleService.getAllVehicles().stream()
                .filter(v -> v.getDepartment() != null &&
                        departmentId.equals(v.getDepartment().getId()))
                .toList();

        List<Vehicle> availableVehiclesInDept = vehiclesInDept.stream()
                .filter(Vehicle::getAvailable)
                .toList();

        int availableEmployees = (int) availableEmployeesInDept;
        int availableVehicles = availableVehiclesInDept.size();

        // rule: 2 available employees per truck
        int maxTrucksByStaff = availableEmployees / 2;
        int maxActiveVehicles = Math.min(maxTrucksByStaff, availableVehicles);

        System.out.println("[DeptRouting] Department " + departmentId + " dispatch info:");
        System.out.println("  employeesInDept=" + employeesInDept.size()
                + ", availableEmployees=" + availableEmployees);
        System.out.println("  vehiclesInDept=" + vehiclesInDept.size()
                + ", availableVehicles=" + availableVehicles);
        System.out.println("  maxTrucksByStaff=" + maxTrucksByStaff
                + ", maxActiveVehicles=" + maxActiveVehicles);

        if (maxActiveVehicles <= 0) {
            System.out.println("[DeptRouting] No trucks can be dispatched (not enough staff or vehicles).");
            return List.of();
        }

        // select first N available vehicles as active trucks
        List<Vehicle> selectedVehicles = availableVehiclesInDept.stream()
                .limit(maxActiveVehicles)
                .toList();

        System.out.println("[DeptRouting] Selected vehicles for routing:");
        selectedVehicles.forEach(v ->
                System.out.println("  " + v.getId() + " (" + v.getReference() + ")"));

        // ---------- 3) DELEGATE TO VRP SOLVER ----------

        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        selectedVehicles,
                        bins,
                        maxRangeKm,
                        overfillBinIds
                );

        // ---------- 4) MAP TO DTOs ----------

        Map<String, Bin> binIdMap = bins.stream()
                .collect(Collectors.toMap(bin -> bin.getId().toString(), bin -> bin));

        List<DepartmentRouteDTO> response = new ArrayList<>();
        for (VehicleRouteResult r : routeResults) {
            String vehicleId = r.getVehicleId();
            List<String> binIds = r.getOrderedBinIds();

            List<DTO.BinDTO> binDtos = binIds.stream()
                    .map(binIdMap::get)
                    .filter(Objects::nonNull)
                    .map(bin -> new DTO.BinDTO(bin.getId(), bin.getLatitude(), bin.getLongitude()))
                    .toList();

            DepartmentRouteDTO dto = new DepartmentRouteDTO(vehicleId, binDtos);
            response.add(dto);

            System.out.println("[DeptRouting] Vehicle " + vehicleId + " route bins: " + binIds);
        }

        return response;
    }

    /** DTO returned to controllers: per-vehicle route for a department. */
    public static class DepartmentRouteDTO {
        private String vehicleId;
        private List<DTO.BinDTO> bins;

        public DepartmentRouteDTO(String vehicleId, List<DTO.BinDTO> bins) {
            this.vehicleId = vehicleId;
            this.bins = bins;
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public void setVehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
        }

        public List<DTO.BinDTO> getBins() {
            return bins;
        }

        public void setBins(List<DTO.BinDTO> bins) {
            this.bins = bins;
        }
    }
    public void executeRoute(String vehicleId, List<String> binIdsInOrder) {
        for (String binId : binIdsInOrder) {
            Vehicle v = vehicleService.getVehicleById(vehicleId)
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));

            if (!Boolean.TRUE.equals(v.getAvailable()) || v.getFillLevel() >= 100.0) {
                break; // truck is full, stop loading more bins
            }

            vehicleService.emptyBin(vehicleId, binId);
        }
    }
}
