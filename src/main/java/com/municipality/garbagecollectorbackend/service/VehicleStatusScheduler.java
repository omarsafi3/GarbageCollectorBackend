package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class VehicleStatusScheduler {

    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private VehicleService vehicleService;

    /**
     * Runs every 10 SECONDS (faster for testing)
     */
    @Scheduled(fixedRate = 10000) // ✅ Changed from 30000 to 10000 (10 seconds)
    public void handleVehicleStateTransitions() {
        handleReturningVehicles();
        handleUnloadingVehicles();
    }

    /**
     * RETURNING → UNLOADING
     * After 30 SECONDS (faster for testing)
     */
    private void handleReturningVehicles() {
        List<Vehicle> returningVehicles = vehicleRepository.findAll().stream()
                .filter(v -> v.getStatus() == Vehicle.VehicleStatus.RETURNING)
                .toList();

        for (Vehicle vehicle : returningVehicles) {
            LocalDateTime statusTime = vehicle.getStatusUpdatedAt();
            if (statusTime == null) continue;

            long secondsSinceReturn = ChronoUnit.SECONDS.between(statusTime, LocalDateTime.now());  // ✅ SECONDS not MINUTES

            // After 30 seconds, vehicle arrives at depot (faster for testing)
            if (secondsSinceReturn >= 30) {  // ✅ Changed from 5 minutes to 30 seconds
                vehicle.setStatus(Vehicle.VehicleStatus.UNLOADING);
                vehicle.setStatusUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);

                // Ensure caches reflect the status change
                vehicleService.evictVehicleCaches();

                System.out.println("🏢 Vehicle " + vehicle.getId() + " arrived at depot - Status: UNLOADING");
            }
        }
    }

    /**
     * UNLOADING → AVAILABLE
     * Decrease fill level by 10% every 10 SECONDS (faster for testing)
     */
    private void handleUnloadingVehicles() {
        List<Vehicle> unloadingVehicles = vehicleRepository.findAll().stream()
                .filter(v -> v.getStatus() == Vehicle.VehicleStatus.UNLOADING)
                .toList();

        for (Vehicle vehicle : unloadingVehicles) {
            double currentFill = vehicle.getFillLevel() != null ? vehicle.getFillLevel() : 0.0;

            if (currentFill <= 0) {
                // Unloading complete!
                vehicle.setFillLevel(0.0);
                vehicle.setStatus(Vehicle.VehicleStatus.AVAILABLE);
                vehicle.setAvailable(true);
                vehicle.setStatusUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);

                // Ensure caches reflect the status change
                vehicleService.evictVehicleCaches();

                System.out.println("✅ Vehicle " + vehicle.getId() + " ready for new route - Status: AVAILABLE");
            } else {
                // Decrease by 10% every 10 seconds (faster for testing)
                vehicle.setFillLevel(Math.max(0, currentFill - 10));  // ✅ Direct 10% decrease
                vehicle.setStatusUpdatedAt(LocalDateTime.now());
                vehicleRepository.save(vehicle);

                // Ensure caches reflect the fill level update
                vehicleService.evictVehicleCaches();

                System.out.println("🔄 Vehicle " + vehicle.getId() + " unloading: " + vehicle.getFillLevel() + "%");
            }
        }
    }
}

