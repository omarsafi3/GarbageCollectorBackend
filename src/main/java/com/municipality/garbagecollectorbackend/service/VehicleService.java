package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VehicleService {


    @Autowired
    @Lazy
    private RouteOptimizationService routeOptimizationService;
    @Autowired
    public VehicleRepository vehicleRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    @Autowired
    public BinRepository binRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ✅ NEW: Inject EmployeeService for employee management
    @Autowired
    private EmployeeService employeeService;

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }
    public Optional<Vehicle> getVehicleById(String id) {
        return vehicleRepository.findById(id);
    }

    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findAll().stream()
                .filter(v -> v.getStatus() == Vehicle.VehicleStatus.AVAILABLE)
                .collect(Collectors.toList());
    }

    /**
     * Get all available vehicles for a specific department
     * @param departmentId the department ID
     * @return list of available vehicles in the department
     */
    public List<Vehicle> getAvailableVehiclesByDepartment(String departmentId) {
        return vehicleRepository.findAll().stream()
                .filter(v -> v.getStatus() == Vehicle.VehicleStatus.AVAILABLE)
                .filter(v -> v.getDepartment() != null && departmentId.equals(v.getDepartment().getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get all vehicles for a specific department
     * @param departmentId the department ID
     * @return list of vehicles in the department
     */
    public List<Vehicle> getVehiclesByDepartment(String departmentId) {
        return vehicleRepository.findAll().stream()
                .filter(v -> v.getDepartment() != null && departmentId.equals(v.getDepartment().getId()))
                .collect(Collectors.toList());
    }

    @Caching(evict = {
        @CacheEvict(value = "vehicles", allEntries = true),
        @CacheEvict(value = "vehiclesByDepartment", allEntries = true)
    })
    public void evictVehicleCaches() {
        // Method used to programmatically evict vehicle-related caches after status changes
    }

    @Caching(evict = {
        @CacheEvict(value = "vehicles", allEntries = true),
        @CacheEvict(value = "vehiclesByDepartment", allEntries = true)
    })
    public Vehicle saveVehicle(Vehicle vehicle) {
        if (vehicle.getDepartment() != null) {
            String depId = vehicle.getDepartment().getId();
            Department fullDep = departmentRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            vehicle.setDepartment(fullDep);
        }
        return vehicleRepository.save(vehicle);
    }

    @Caching(evict = {
        @CacheEvict(value = "vehicles", allEntries = true),
        @CacheEvict(value = "vehiclesByDepartment", allEntries = true)
    })
    public Vehicle updateVehicle(String id, Vehicle updated) {
        Vehicle existing = vehicleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        existing.setReference(updated.getReference());
        existing.setPlate(updated.getPlate());
        existing.setFillLevel(updated.getFillLevel());
        existing.setAvailable(updated.getAvailable());

        if (updated.getDepartment() != null) {
            String depId = updated.getDepartment().getId();
            Department fullDep = departmentRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            existing.setDepartment(fullDep);
        }

        return vehicleRepository.save(existing);
    }

    @Caching(evict = {
        @CacheEvict(value = "vehicles", allEntries = true),
        @CacheEvict(value = "vehiclesByDepartment", allEntries = true)
    })
    public void deleteVehicle(String id) {
        vehicleRepository.deleteById(id);
    }

    @Caching(evict = {
        @CacheEvict(value = "vehicles", allEntries = true),
        @CacheEvict(value = "vehiclesByDepartment", allEntries = true),
        @CacheEvict(value = "bins", allEntries = true),
        @CacheEvict(value = "binsByDepartment", allEntries = true)
    })
    public Vehicle emptyBin(String vehicleId, String binId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        Bin bin = binRepository.findById(binId)
                .orElseThrow(() -> new RuntimeException("Bin not found"));

        double vehicleLevel = vehicle.getFillLevel() == null ? 0.0 : vehicle.getFillLevel();
        int binLevel = bin.getFillLevel();

        double vehicleRemaining = 100.0 - vehicleLevel;
        double truckGainForFullBin = 20.0;
        double binFullness = binLevel / 100.0;
        double potentialTruckGain = binFullness * truckGainForFullBin;
        double actualTruckGain = Math.min(vehicleRemaining, potentialTruckGain);
        double binReduction = (actualTruckGain / truckGainForFullBin) * 100.0;

        vehicle.setFillLevel(vehicleLevel + actualTruckGain);
        bin.setFillLevel((int) Math.round(binLevel - binReduction));

        if (bin.getFillLevel() < 100) {
            bin.setStatus("active");
        }
        if (vehicle.getFillLevel() >= 100.0) {
            vehicle.setAvailable(false);
        }

        bin.setLastUpdated(LocalDateTime.now());

        binRepository.save(bin);
        Vehicle updatedVehicle = vehicleRepository.save(vehicle);

        Map<String, Object> update = new HashMap<>();
        update.put("vehicleId", vehicleId);
        update.put("fillLevel", updatedVehicle.getFillLevel());
        update.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/vehicles", update);
        System.out.println("📡 Sent vehicle update: " + vehicleId + " -> " + updatedVehicle.getFillLevel() + "%");

        return updatedVehicle;
    }

    // ✅ UPDATED: Check for employees before starting route
    public Vehicle startRoute(String vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // ✅ NEW: Check if vehicle is available
        if (vehicle.getStatus() != Vehicle.VehicleStatus.AVAILABLE) {
            throw new RuntimeException("Vehicle is not available (status: " + vehicle.getStatus() + ")");
        }

        // ✅ NEW: Check if vehicle has 2 employees assigned
        if (!employeeService.vehicleHasRequiredEmployees(vehicleId)) {
            throw new RuntimeException("Vehicle must have 2 employees assigned before starting route");
        }

        // ✅ NEW: Mark employees as IN_ROUTE
        employeeService.markEmployeesInRoute(vehicleId);

        // Set vehicle to IN_ROUTE
        vehicle.setStatus(Vehicle.VehicleStatus.IN_ROUTE);
        vehicle.setAvailable(false);
        vehicle.setStatusUpdatedAt(LocalDateTime.now());

        Vehicle updated = vehicleRepository.save(vehicle);

        // Evict caches after changing vehicle status
        evictVehicleCaches();

        System.out.println("🚀 Vehicle " + vehicleId + " started route with 2 employees - Status: IN_ROUTE");

        return updated;
    }

    public Vehicle completeRoute(String vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (vehicle.getStatus() != Vehicle.VehicleStatus.IN_ROUTE) {
            System.out.println("⚠️ Vehicle " + vehicleId + " was not IN_ROUTE (status: " + vehicle.getStatus() + ")");
        }

        // Set to RETURNING (going back to depot)
        vehicle.setStatus(Vehicle.VehicleStatus.RETURNING);
        vehicle.setStatusUpdatedAt(LocalDateTime.now());
        vehicleRepository.save(vehicle);

        // Evict caches after status change
        evictVehicleCaches();

        System.out.println("🏁 Vehicle " + vehicleId + " completed route - Status: RETURNING to depot");

        // ✅ CAPTURE DEPARTMENT ID BEFORE THREAD
        String departmentId = vehicle.getDepartment() != null ? vehicle.getDepartment().getId() : null;

        // Start unloading process in a separate thread
        new Thread(() -> {
            try {
                Thread.sleep(3000);

                Vehicle v = vehicleRepository.findById(vehicleId).orElse(null);
                if (v != null) {
                    v.setStatus(Vehicle.VehicleStatus.UNLOADING);
                    v.setStatusUpdatedAt(LocalDateTime.now());
                    vehicleRepository.save(v);

                    // Evict caches after status update
                    evictVehicleCaches();

                    Map<String, Object> update = new HashMap<>();
                    update.put("vehicleId", vehicleId);
                    update.put("status", "UNLOADING");
                    update.put("fillLevel", v.getFillLevel());
                    update.put("available", false);
                    update.put("timestamp", Instant.now().toString());
                    messagingTemplate.convertAndSend("/topic/vehicles", update);

                    System.out.println("🏢 Vehicle " + vehicleId + " arrived at depot - Status: UNLOADING");

                    double startFill = v.getFillLevel();
                    for (int i = 10; i >= 0; i--) {
                        Thread.sleep(300);

                        double currentFill = startFill * (i / 10.0);
                        v.setFillLevel(currentFill);
                        vehicleRepository.save(v);

                        update = new HashMap<>();
                        update.put("vehicleId", vehicleId);
                        update.put("status", "UNLOADING");
                        update.put("fillLevel", currentFill);
                        update.put("available", false);
                        update.put("timestamp", Instant.now().toString());
                        messagingTemplate.convertAndSend("/topic/vehicles", update);

                        System.out.println("🔄 Vehicle " + vehicleId + " unloading: " +
                                String.format("%.1f", currentFill) + "%");
                    }

                    // Release employees when unloading complete
                    employeeService.releaseEmployeesFromVehicle(vehicleId);

                    // Mark as AVAILABLE
                    v.setStatus(Vehicle.VehicleStatus.AVAILABLE);
                    v.setFillLevel(0.0);
                    v.setAvailable(true);
                    v.setStatusUpdatedAt(LocalDateTime.now());
                    vehicleRepository.save(v);

                    // Evict caches after vehicle becomes available
                    evictVehicleCaches();

                    update = new HashMap<>();
                    update.put("vehicleId", vehicleId);
                    update.put("status", "AVAILABLE");
                    update.put("fillLevel", 0.0);
                    update.put("available", true);
                    update.put("timestamp", Instant.now().toString());
                    messagingTemplate.convertAndSend("/topic/vehicles", update);

                    System.out.println("✅ Vehicle " + vehicleId + " ready for new route - Status: AVAILABLE, employees released");

                    // ✅ NEW: Generate new route for this vehicle immediately
                    if (departmentId != null) {
                        try {
                            System.out.println("🔄 Triggering route generation for returned vehicle " + vehicleId);
                            routeOptimizationService.generateRouteForReturningVehicle(vehicleId, departmentId);
                        } catch (Exception e) {
                            System.err.println("❌ Failed to generate route for returned vehicle: " + e.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return vehicle;
    }
}
