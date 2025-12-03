package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.dto.PreGeneratedRoute;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for automated vehicle dispatching.
 * Automatically assigns available vehicles to routes when:
 * 1. Routes are available and vehicles are idle
 * 2. Critical bins are detected (fill level >= threshold)
 * 3. Employees (driver + collector) are available
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutoDispatchService {

    private final RouteOptimizationService routeOptimizationService;
    private final RouteExecutionService routeExecutionService;
    private final VehicleService vehicleService;
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${autodispatch.enabled:true}")
    private boolean autoDispatchEnabled;

    @Value("${autodispatch.critical-threshold:85}")
    private int criticalThreshold;

    @Value("${autodispatch.min-bins-for-route:3}")
    private int minBinsForRoute;

    // Track recently dispatched vehicles to prevent rapid re-dispatch
    private final Map<String, Instant> recentlyDispatched = new ConcurrentHashMap<>();
    private static final long DISPATCH_COOLDOWN_SECONDS = 60;

    /**
     * Main auto-dispatch scheduler - runs every 30 seconds
     * Checks for available routes and vehicles, then dispatches automatically
     */
    @Scheduled(fixedRate = 30000)
    public void autoDispatchVehicles() {
        if (!autoDispatchEnabled) {
            return;
        }

        log.debug("🤖 Auto-dispatch check running...");

        try {
            List<Department> departments = departmentService.getAllDepartments();
            
            for (Department department : departments) {
                autoDispatchForDepartment(department.getId());
            }
        } catch (Exception e) {
            log.error("❌ Auto-dispatch error: {}", e.getMessage(), e);
        }
    }

    /**
     * Auto-dispatch vehicles for a specific department
     */
    public void autoDispatchForDepartment(String departmentId) {
        if (!autoDispatchEnabled) {
            log.debug("Auto-dispatch is disabled");
            return;
        }

        // Get available routes for this department
        List<PreGeneratedRoute> availableRoutes = routeOptimizationService.getAvailableRoutes(departmentId);
        
        if (availableRoutes.isEmpty()) {
            log.debug("No available routes for department {}", departmentId);
            return;
        }

        // Get available vehicles for this department
        List<Vehicle> availableVehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId)
                .stream()
                .filter(v -> !isRecentlyDispatched(v.getId()))
                .filter(v -> !routeExecutionService.isVehicleActive(v.getId()))
                .collect(Collectors.toList());

        if (availableVehicles.isEmpty()) {
            log.debug("No available vehicles for department {}", departmentId);
            return;
        }

        // Get available employees for this department
        List<Employee> availableDrivers = employeeService.getEmployeesByDepartment(departmentId)
                .stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.DRIVER)
                .filter(e -> Boolean.TRUE.equals(e.getAvailable()))
                .filter(e -> e.getAssignedVehicleId() == null)
                .collect(Collectors.toList());

        List<Employee> availableCollectors = employeeService.getEmployeesByDepartment(departmentId)
                .stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.COLLECTOR)
                .filter(e -> Boolean.TRUE.equals(e.getAvailable()))
                .filter(e -> e.getAssignedVehicleId() == null)
                .collect(Collectors.toList());

        // Calculate how many vehicles we can dispatch
        int maxDispatchable = Math.min(
                Math.min(availableVehicles.size(), availableRoutes.size()),
                Math.min(availableDrivers.size(), availableCollectors.size())
        );

        if (maxDispatchable == 0) {
            log.debug("Cannot dispatch: vehicles={}, routes={}, drivers={}, collectors={}",
                    availableVehicles.size(), availableRoutes.size(),
                    availableDrivers.size(), availableCollectors.size());
            return;
        }

        log.info("🤖 Auto-dispatching {} vehicle(s) for department {}", maxDispatchable, departmentId);

        // Sort routes by priority (more bins = higher priority)
        availableRoutes.sort((a, b) -> Integer.compare(b.getBinCount(), a.getBinCount()));

        // Dispatch vehicles
        for (int i = 0; i < maxDispatchable; i++) {
            Vehicle vehicle = availableVehicles.get(i);
            PreGeneratedRoute route = availableRoutes.get(i);
            Employee driver = availableDrivers.get(i);
            Employee collector = availableCollectors.get(i);

            try {
                dispatchVehicle(vehicle, route, driver, collector, departmentId);
            } catch (Exception e) {
                log.error("❌ Failed to auto-dispatch vehicle {}: {}", vehicle.getId(), e.getMessage());
            }
        }
    }

    /**
     * Dispatch a single vehicle with assigned employees
     */
    private void dispatchVehicle(Vehicle vehicle, PreGeneratedRoute route, 
                                  Employee driver, Employee collector, String departmentId) {
        log.info("🚛 Auto-dispatching vehicle {} with route {} ({} bins)",
                vehicle.getReference(), route.getRouteId(), route.getBinCount());

        // Assign employees to vehicle
        List<String> employeeIds = Arrays.asList(driver.getId(), collector.getId());
        boolean employeesAssigned = employeeService.assignEmployeesToVehicle(vehicle.getId(), employeeIds);
        
        if (!employeesAssigned) {
            log.warn("⚠️ Failed to assign employees to vehicle {}", vehicle.getId());
            return;
        }

        // Assign route to vehicle
        routeOptimizationService.assignRouteToVehicle(route.getRouteId(), vehicle.getId());

        // Start route execution
        routeExecutionService.startRoute(vehicle.getId(), departmentId, route);

        // Mark as recently dispatched
        recentlyDispatched.put(vehicle.getId(), Instant.now());

        // Notify frontend
        notifyAutoDispatch(vehicle, route, driver, collector, departmentId);

        log.info("✅ Auto-dispatched vehicle {} on route {} with driver {} and collector {}",
                vehicle.getReference(), route.getRouteId(), 
                driver.getFirstName(), collector.getFirstName());
    }

    /**
     * Check if a vehicle was recently dispatched (cooldown period)
     */
    private boolean isRecentlyDispatched(String vehicleId) {
        Instant lastDispatch = recentlyDispatched.get(vehicleId);
        if (lastDispatch == null) {
            return false;
        }
        return Instant.now().minusSeconds(DISPATCH_COOLDOWN_SECONDS).isBefore(lastDispatch);
    }

    /**
     * Clear cooldown for a vehicle (called when vehicle completes route)
     */
    public void clearCooldown(String vehicleId) {
        recentlyDispatched.remove(vehicleId);
    }

    /**
     * Notify frontend about auto-dispatch event
     */
    private void notifyAutoDispatch(Vehicle vehicle, PreGeneratedRoute route,
                                     Employee driver, Employee collector, String departmentId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("event", "AUTO_DISPATCH");
        notification.put("vehicleId", vehicle.getId());
        notification.put("vehicleReference", vehicle.getReference());
        notification.put("routeId", route.getRouteId());
        notification.put("binCount", route.getBinCount());
        notification.put("driverName", driver.getFirstName() + " " + driver.getLastName());
        notification.put("collectorName", collector.getFirstName() + " " + collector.getLastName());
        notification.put("departmentId", departmentId);
        notification.put("timestamp", Instant.now().toString());

        messagingTemplate.convertAndSend("/topic/auto-dispatch", notification);
        log.info("📡 Notified frontend: Auto-dispatched {} on route {}", 
                vehicle.getReference(), route.getRouteId());
    }

    /**
     * Trigger immediate auto-dispatch check (can be called manually)
     */
    public Map<String, Object> triggerAutoDispatch(String departmentId) {
        Map<String, Object> result = new HashMap<>();
        
        if (!autoDispatchEnabled) {
            result.put("success", false);
            result.put("message", "Auto-dispatch is disabled");
            return result;
        }

        int beforeCount = routeOptimizationService.getAvailableRoutes(departmentId).size();
        autoDispatchForDepartment(departmentId);
        int afterCount = routeOptimizationService.getAvailableRoutes(departmentId).size();
        int dispatched = beforeCount - afterCount;

        result.put("success", true);
        result.put("vehiclesDispatched", dispatched);
        result.put("remainingRoutes", afterCount);
        result.put("message", dispatched > 0 
                ? "Auto-dispatched " + dispatched + " vehicle(s)"
                : "No vehicles dispatched (no available vehicles/employees/routes)");
        
        return result;
    }

    /**
     * Get current auto-dispatch status
     */
    public Map<String, Object> getAutoDispatchStatus(String departmentId) {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", autoDispatchEnabled);
        status.put("criticalThreshold", criticalThreshold);
        status.put("minBinsForRoute", minBinsForRoute);
        
        List<PreGeneratedRoute> routes = routeOptimizationService.getAvailableRoutes(departmentId);
        List<Vehicle> vehicles = vehicleService.getAvailableVehiclesByDepartment(departmentId);
        List<Employee> drivers = employeeService.getEmployeesByDepartment(departmentId)
                .stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.DRIVER && Boolean.TRUE.equals(e.getAvailable()))
                .collect(Collectors.toList());
        List<Employee> collectors = employeeService.getEmployeesByDepartment(departmentId)
                .stream()
                .filter(e -> e.getRole() == Employee.EmployeeRole.COLLECTOR && Boolean.TRUE.equals(e.getAvailable()))
                .collect(Collectors.toList());

        status.put("availableRoutes", routes.size());
        status.put("availableVehicles", vehicles.size());
        status.put("availableDrivers", drivers.size());
        status.put("availableCollectors", collectors.size());
        status.put("canDispatch", !routes.isEmpty() && !vehicles.isEmpty() 
                && !drivers.isEmpty() && !collectors.isEmpty());

        return status;
    }

    /**
     * Enable/disable auto-dispatch
     */
    public void setAutoDispatchEnabled(boolean enabled) {
        this.autoDispatchEnabled = enabled;
        log.info("🤖 Auto-dispatch {}", enabled ? "ENABLED" : "DISABLED");
    }

    public boolean isAutoDispatchEnabled() {
        return autoDispatchEnabled;
    }
}
