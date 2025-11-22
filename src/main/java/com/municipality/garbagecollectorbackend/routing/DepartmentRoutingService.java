package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.model.DTO;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.EmployeeService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates department-level routing:
 * - determines which vehicles can leave (2 available employees per truck),
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

    @Autowired
    public DepartmentRoutingService(
            RouteOptimizationService routeOptimizationService,
            BinService binService,
            DepartmentService departmentService,
            VehicleService vehicleService,
            EmployeeService employeeService
    ) {
        this.routeOptimizationService = routeOptimizationService;
        this.binService = binService;
        this.departmentService = departmentService;
        this.vehicleService = vehicleService;
        this.employeeService = employeeService;
    }

    /**
     * Business use-case:
     * Optimize routes for a department, considering:
     * - only bins with fillLevel >= 70,
     * - only vehicles and employees of that department,
     * - dispatch rule: 2 available employees required per active truck.
     *
     * Returns one route per active vehicle.
     */
    public List<DepartmentRouteDTO> optimizeDepartmentRoutes(String departmentId, double maxRangeKm) {
        Optional<Department> departmentOpt = departmentService.getDepartmentById(departmentId);
        if (departmentOpt.isEmpty()) {
            return List.of();
        }
        Department department = departmentOpt.get();

        // 1) Candidate bins: current rule = fillLevel >= 70
        List<Bin> bins = binService.getAllBins().stream()
                .filter(bin -> bin.getFillLevel() >= 70)
                .toList();

        if (bins.isEmpty()) {
            System.out.println("[DeptRouting] No bins with fillLevel >= 70 for department " + departmentId);
            return List.of();
        }

        // 2) Employees of this department
        List<Employee> employeesInDept = employeeService.getAllEmployees().stream()
                .filter(e -> e.getDepartment() != null &&
                        departmentId.equals(e.getDepartment().getId()))
                .toList();

        long availableEmployeesInDept = employeesInDept.stream()
                .filter(Employee::getAvailable)
                .count();

        // 3) Vehicles of this department
        List<Vehicle> vehiclesInDept = vehicleService.getAllVehicles().stream()
                .filter(v -> v.getDepartment() != null &&
                        departmentId.equals(v.getDepartment().getId()))
                .toList();

        List<Vehicle> availableVehiclesInDept = vehiclesInDept.stream()
                .filter(Vehicle::getAvailable)
                .toList();

        int availableEmployees = (int) availableEmployeesInDept;
        int availableVehicles = availableVehiclesInDept.size();

        // 4) Dispatch rule: 2 available employees per truck
        int maxTrucksByStaff = availableEmployees / 2;  // floor
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

        // 5) Select first N available vehicles as active trucks (can refine later)
        List<Vehicle> selectedVehicles = availableVehiclesInDept.stream()
                .limit(maxActiveVehicles)
                .toList();

        System.out.println("[DeptRouting] Selected vehicles for routing:");
        selectedVehicles.forEach(v ->
                System.out.println("  " + v.getId() + " (" + v.getReference() + ")"));

        // 6) Delegate multi-vehicle optimization to RouteOptimizationService
        List<VehicleRouteResult> routeResults =
                routeOptimizationService.optimizeDepartmentRoutes(
                        Optional.of(department),
                        selectedVehicles,
                        bins,
                        maxRangeKm
                );

        // 7) Map binIds back to Bin and then to DTOs per vehicle
        Map<String, Bin> binIdMap = bins.stream()
                .collect(Collectors.toMap(bin -> bin.getId().toString(), bin -> bin));

        List<DepartmentRouteDTO> response = new ArrayList<>();
        for (VehicleRouteResult r : routeResults) {
            String vehicleId = r.getVehicleId();
            List<String> binIds = r.getBinIds();

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

    /**
     * DTO returned to controllers: per-vehicle route for a department.
     */
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
}
