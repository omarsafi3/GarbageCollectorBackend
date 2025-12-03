package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@Tag(name = "Vehicles", description = "Fleet vehicle management endpoints")
public class VehicleController {

    @Autowired
    private VehicleService vehicleService;

    @Operation(summary = "Get all vehicles", description = "Retrieve a list of all fleet vehicles")
    @GetMapping
    public List<Vehicle> getAllVehicles() {
        return vehicleService.getAllVehicles();
    }

    @Operation(summary = "Get vehicle by ID", description = "Retrieve a single vehicle by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle found"),
        @ApiResponse(responseCode = "404", description = "Vehicle not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Vehicle> getVehicleById(@Parameter(description = "Vehicle ID") @PathVariable String id) {
        return vehicleService.getVehicleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get available vehicles", description = "Retrieve all vehicles currently available for dispatch")
    @GetMapping("/available")
    public List<Vehicle> getAvailableVehicles() {
        return vehicleService.getAvailableVehicles();
    }

    @Operation(summary = "Create a new vehicle", description = "Add a new vehicle to the fleet")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Vehicle created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid vehicle data")
    })
    @PostMapping
    public ResponseEntity<?> createVehicle(@RequestBody Vehicle vehicle) {
        try {
            Vehicle saved = vehicleService.saveVehicle(vehicle);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Update a vehicle", description = "Update an existing vehicle's information")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateVehicle(@PathVariable String id, @RequestBody Vehicle vehicle) {
        try {
            Vehicle updated = vehicleService.updateVehicle(id, vehicle);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a vehicle", description = "Remove a vehicle from the fleet")
    @ApiResponse(responseCode = "204", description = "Vehicle deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVehicle(@PathVariable String id) {
        vehicleService.deleteVehicle(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Empty a bin", description = "Record a bin being emptied by a vehicle")
    @PutMapping("/{vehicleId}/emptyBin/{binId}")
    public ResponseEntity<?> emptyBin(
            @Parameter(description = "Vehicle ID") @PathVariable String vehicleId,
            @Parameter(description = "Bin ID") @PathVariable String binId) {
        try {
            Vehicle updated = vehicleService.emptyBin(vehicleId, binId);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}