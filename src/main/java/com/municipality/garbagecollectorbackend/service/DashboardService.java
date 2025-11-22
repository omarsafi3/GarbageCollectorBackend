package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.EmployeeRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    public BinRepository binRepository;

    @Autowired
    public VehicleRepository vehicleRepository;

    @Autowired
    public EmployeeRepository employeeRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    // Get stats for all departments
    public List<Map<String, Object>> getAllDepartmentStats() {
        return departmentRepository.findAll().stream()
                .map(dept -> getDepartmentStats(dept.getId()))
                .collect(Collectors.toList());
    }

    // Get stats for a specific department
    public Map<String, Object> getDepartmentStats(String departmentId) {
        Map<String, Object> stats = new HashMap<>();

        // Department info
        Department department = departmentRepository.findById(departmentId).orElse(null);
        if (department == null) {
            return stats;
        }

        stats.put("departmentId", departmentId);
        stats.put("departmentName", department.getName());

        // Vehicles for this department
        long totalVehicles = vehicleRepository.findAll().stream()
                .filter(v -> v.getDepartment() != null && departmentId.equals(v.getDepartment().getId()))
                .count();

        long availableVehicles = vehicleRepository.findAll().stream()
                .filter(v -> v.getDepartment() != null && departmentId.equals(v.getDepartment().getId()) && v.getAvailable())
                .count();

        long activeVehicles = totalVehicles - availableVehicles;

        stats.put("totalVehicles", totalVehicles);
        stats.put("availableVehicles", availableVehicles);
        stats.put("activeVehicles", activeVehicles);

        // Employees for this department
        long totalEmployees = employeeRepository.findAll().stream()
                .filter(e -> e.getDepartment() != null && departmentId.equals(e.getDepartment().getId()))
                .count();

        long availableEmployees = employeeRepository.findAll().stream()
                .filter(e -> e.getDepartment() != null && departmentId.equals(e.getDepartment().getId()) && e.getAvailable())
                .count();

        stats.put("totalEmployees", totalEmployees);
        stats.put("availableEmployees", availableEmployees);

        // Bins in system (all bins for now - you can filter by proximity later)
        long totalBins = binRepository.count();

        long criticalBins = binRepository.findAll().stream()
                .filter(bin -> bin.getFillLevel() > 80)
                .count();

        double avgFillLevel = binRepository.findAll().stream()
                .mapToInt(bin -> bin.getFillLevel())
                .average()
                .orElse(0.0);

        stats.put("totalBins", totalBins);
        stats.put("criticalBins", criticalBins);
        stats.put("averageFillLevel", Math.round(avgFillLevel * 10) / 10.0);

        // Performance metrics
        long binsCollectedToday = binRepository.findAll().stream()
                .filter(bin -> bin.getFillLevel() < 10)
                .count();

        stats.put("binsCollectedToday", binsCollectedToday);

        // CO2 saved estimation
        double estimatedCO2Saved = binsCollectedToday * 0.2 * 50 * 0.3 * 2.3;
        stats.put("co2Saved", Math.round(estimatedCO2Saved * 10) / 10.0);

        return stats;
    }

    // Global stats across all departments
    public Map<String, Object> calculateGlobalStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalDepartments", departmentRepository.count());
        stats.put("totalBins", binRepository.count());
        stats.put("totalVehicles", vehicleRepository.count());
        stats.put("totalEmployees", employeeRepository.count());

        long activeTrucks = vehicleRepository.findAll().stream()
                .filter(v -> !v.getAvailable())
                .count();
        stats.put("activeTrucks", activeTrucks);

        double avgFillLevel = binRepository.findAll().stream()
                .mapToInt(bin -> bin.getFillLevel())
                .average()
                .orElse(0.0);
        stats.put("averageFillLevel", Math.round(avgFillLevel * 10) / 10.0);

        long criticalBins = binRepository.findAll().stream()
                .filter(bin -> bin.getFillLevel() > 80)
                .count();
        stats.put("criticalBins", criticalBins);

        return stats;
    }
}
