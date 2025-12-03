package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.service.BinService;
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
@RequestMapping("/bins")
@Tag(name = "Bins", description = "Garbage bin management endpoints")
public class BinController {

    @Autowired
    private BinService binService;

    @Operation(summary = "Get all bins", description = "Retrieve a list of all garbage bins")
    @ApiResponse(responseCode = "200", description = "List of bins retrieved successfully")
    @GetMapping
    public List<Bin> getAllBins() {
        return binService.getAllBins();
    }

    @Operation(summary = "Get bin by ID", description = "Retrieve a single bin by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bin found"),
        @ApiResponse(responseCode = "404", description = "Bin not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Bin> getBinById(@Parameter(description = "Bin ID") @PathVariable String id) {
        Bin bin = binService.getBinById(id);
        if (bin == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bin);
    }

    @Operation(summary = "Get bins by department", description = "Retrieve all bins belonging to a specific department")
    @GetMapping("/department/{departmentId}")
    public List<Bin> getBinsByDepartment(@Parameter(description = "Department ID") @PathVariable String departmentId) {
        return binService.getBinsByDepartmentId(departmentId);
    }

    @Operation(summary = "Create a new bin", description = "Add a new garbage bin to the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bin created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid bin data")
    })
    @PostMapping
    public ResponseEntity<?> createBin(@RequestBody Bin bin) {
        try {
            Bin saved = binService.saveBin(bin);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Update a bin", description = "Update an existing bin's information")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Bin updated successfully"),
        @ApiResponse(responseCode = "404", description = "Bin not found"),
        @ApiResponse(responseCode = "400", description = "Invalid bin data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBin(@PathVariable String id, @RequestBody Bin bin) {
        try {
            Bin updated = binService.updateBin(id, bin);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Delete a bin", description = "Remove a bin from the system")
    @ApiResponse(responseCode = "204", description = "Bin deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBin(@PathVariable String id) {
        binService.deleteBin(id);
        return ResponseEntity.noContent().build();
    }
}
