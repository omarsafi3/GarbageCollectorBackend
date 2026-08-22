package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Dashboard", description = "Dashboard statistics endpoints")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @Operation(summary = "Get all department stats", description = "Retrieves statistics for all accessible departments")
    @ApiResponse(responseCode = "200", description = "List of department statistics")
    @GetMapping("/departments")
    public List<Map<String, Object>> getAllDepartmentStats() {
        if (com.municipality.garbagecollectorbackend.util.SecurityUtil.isSuperAdmin()) {
            return dashboardService.getAllDepartmentStats();
        }
        String deptId = com.municipality.garbagecollectorbackend.util.SecurityUtil.getCurrentDepartmentId();
        if (deptId != null) {
            return List.of(dashboardService.getDepartmentStats(deptId));
        }
        return List.of();
    }

    @Operation(summary = "Get department stats", description = "Retrieves statistics for a specific department")
    @ApiResponse(responseCode = "200", description = "Department statistics")
    @GetMapping("/departments/{departmentId}")
    public Map<String, Object> getDepartmentStats(@PathVariable String departmentId) {
        if (!com.municipality.garbagecollectorbackend.util.SecurityUtil.canAccessDepartment(departmentId)) {
            return Map.of("error", "Access denied to department: " + departmentId);
        }
        return dashboardService.getDepartmentStats(departmentId);
    }

    @Operation(summary = "Get global stats", description = "Retrieves global system overview statistics")
    @ApiResponse(responseCode = "200", description = "Global statistics")
    @GetMapping("/stats")
    public Map<String, Object> getGlobalStats() {
        if (com.municipality.garbagecollectorbackend.util.SecurityUtil.isSuperAdmin()) {
            return dashboardService.calculateGlobalStats();
        }
        String deptId = com.municipality.garbagecollectorbackend.util.SecurityUtil.getCurrentDepartmentId();
        if (deptId != null) {
            return dashboardService.getDepartmentStats(deptId);
        }
        return Map.of();
    }
}
