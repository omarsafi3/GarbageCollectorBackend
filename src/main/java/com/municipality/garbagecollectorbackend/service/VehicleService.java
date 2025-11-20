package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class VehicleService {

    @Autowired
    public VehicleRepository vehicleRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    @Autowired
    public BinRepository binRepository;

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
            if (!departmentRepository.existsById(depId)) {
                throw new RuntimeException("Department not found ");
            }
        }
        return vehicleRepository.save(vehicle);
    }

    public Vehicle updateVehicle(String id, Vehicle updated) {

        Optional<Vehicle> opt = vehicleRepository.findById(id);
        if (opt.isEmpty()) return null;

        Vehicle existing = opt.get();

        if (updated.getDepartment() != null) {
            String depId = updated.getDepartment().getId();
            if (!departmentRepository.existsById(depId)) {
                throw new RuntimeException("Department not found with id: " + depId);
            }
            existing.setDepartment(updated.getDepartment());
        }

        existing.setReference(updated.getReference());
        existing.setPlate(updated.getPlate());
        existing.setFillLevel(updated.getFillLevel());
        existing.setAvailable(updated.getAvailable());

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

        double vehicleLevel = vehicle.getFillLevel();
        int binLevel = bin.getFillLevel();

        double vehicleRemaining = 100 - vehicleLevel;
        double binContribution = binLevel / 10.0;

        if (vehicleRemaining >= binContribution) {
            vehicle.setFillLevel(vehicleLevel + binContribution);
            bin.setFillLevel(0);
        } else {
            double amountBinCanGive = vehicleRemaining * 10;
            bin.setFillLevel((int) Math.round(binLevel - amountBinCanGive));
            vehicle.setFillLevel(100.0);
        }

        if (bin.getFillLevel() < 100) {
            bin.setStatus("active");
        }

        bin.setLastUpdated(LocalDateTime.now());

        binRepository.save(bin);
        return vehicleRepository.save(vehicle);
    }

}