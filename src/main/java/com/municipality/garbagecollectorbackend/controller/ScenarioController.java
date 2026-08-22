package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.service.ScenarioSimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Scenario Simulation Sandbox", description = "One-click interactive scenarios for testing emergency detours, surges, and morning shifts")
@RestController
@RequestMapping("/api/simulation/scenario")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioSimulationService scenarioSimulationService;

    @Operation(summary = "Execute simulation scenario", description = "Triggers a pre-built simulation scenario (morning-shift, storm-flood, market-surge, vehicle-breakdown)")
    @PostMapping("/{scenarioName}")
    public ResponseEntity<Map<String, Object>> runScenario(
            @PathVariable String scenarioName,
            @RequestParam(defaultValue = "6a89b334a6bff2d79ad70629") String departmentId) {
        return ResponseEntity.ok(scenarioSimulationService.executeScenario(scenarioName, departmentId));
    }
}
