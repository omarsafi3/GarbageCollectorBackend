package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VehicleService {

    @Autowired
    public VehicleRepository vehicleRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    @Autowired
    public BinRepository binRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public List<Vehicle> getAllVehicles() {
        return vehicleRepository.findAll();
    }

    public Optional<Vehicle> getVehicleById(String id) {
        return vehicleRepository.findById(id);
    }

    public List<Vehicle> getAvailableVehicles() {
        return vehicleRepository.findAll()
                .stream()
                .filter(Vehicle::getAvailable)
                .toList();
    }

    public Vehicle saveVehicle(Vehicle vehicle) {
        if (vehicle.getDepartment() != null) {
            String depId = vehicle.getDepartment().getId();
            Department fullDep = departmentRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            vehicle.setDepartment(fullDep);
        }
        return vehicleRepository.save(vehicle);
    }

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

    public void deleteVehicle(String id) {
        vehicleRepository.deleteById(id);
    }

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

        // 🔥 ADD THESE 7 LINES - WebSocket update:
        Map<String, Object> update = new HashMap<>();
        update.put("vehicleId", vehicleId);
        update.put("fillLevel", updatedVehicle.getFillLevel());
        update.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend("/topic/vehicles", update);
        System.out.println("📡 Sent vehicle update: " + vehicleId + " -> " + updatedVehicle.getFillLevel() + "%");

        return updatedVehicle;
    }






}