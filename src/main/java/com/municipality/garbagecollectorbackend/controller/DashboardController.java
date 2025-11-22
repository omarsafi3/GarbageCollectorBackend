package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    // Get stats for all departments
    @GetMapping("/departments")
    public List<Map<String, Object>> getAllDepartmentStats() {
        return dashboardService.getAllDepartmentStats();
    }

    // Get stats for a specific department
    @GetMapping("/departments/{departmentId}")
    public Map<String, Object> getDepartmentStats(@PathVariable String departmentId) {
        return dashboardService.getDepartmentStats(departmentId);
    }

    // Global system overview
    @GetMapping("/stats")
    public Map<String, Object> getGlobalStats() {
        return dashboardService.calculateGlobalStats();
    }
}
