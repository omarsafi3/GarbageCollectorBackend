package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.CitizenReport;
import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.model.IncidentStatus;
import com.municipality.garbagecollectorbackend.model.IncidentType;
import com.municipality.garbagecollectorbackend.repository.CitizenReportRepository;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Public Citizen Reporting", description = "Public QR code bin info and citizen incident reporting")
@RestController
@RequestMapping("/api/public/bins")
@RequiredArgsConstructor
@Slf4j
public class PublicBinController {

    private final BinService binService;
    private final CitizenReportRepository citizenReportRepository;
    private final IncidentService incidentService;
    private final SimpMessagingTemplate messagingTemplate;

    @Operation(summary = "Get public bin info", description = "Retrieves public details of a smart bin scanned via QR code")
    @GetMapping("/{binId}/info")
    public ResponseEntity<?> getPublicBinInfo(@PathVariable String binId) {
        Bin bin = binService.getBinById(binId);
        if (bin == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id", bin.getId());
        info.put("latitude", bin.getLatitude());
        info.put("longitude", bin.getLongitude());
        info.put("fillLevel", bin.getFillLevel());
        info.put("status", bin.getStatus());
        info.put("lastUpdated", bin.getLastUpdated());
        info.put("lastEmptied", bin.getLastEmptied());
        if (bin.getDepartment() != null) {
            info.put("departmentName", bin.getDepartment().getName());
            info.put("departmentId", bin.getDepartment().getId());
        }

        return ResponseEntity.ok(info);
    }

    @Operation(summary = "Get all public bins", description = "Retrieves list of all public smart bins for citizen locator")
    @GetMapping
    public ResponseEntity<List<Bin>> getAllPublicBins() {
        return ResponseEntity.ok(binService.getAllBins());
    }

    @Operation(summary = "Submit citizen issue report", description = "Allows citizens to report overflowing, broken, or hazardous bins")
    @PostMapping("/report")
    public ResponseEntity<?> submitCitizenReport(@RequestBody CitizenReport report) {
        try {
            report.setReportedAt(LocalDateTime.now());
            report.setStatus("PENDING");

            Bin bin = null;
            if (report.getBinId() != null && !report.getBinId().isEmpty()) {
                bin = binService.getBinById(report.getBinId());
                if (bin != null && bin.getDepartment() != null) {
                    report.setDepartmentId(bin.getDepartment().getId());
                    if (report.getLatitude() == null || report.getLongitude() == null) {
                        report.setLatitude(bin.getLatitude());
                        report.setLongitude(bin.getLongitude());
                    }
                }
            }

            CitizenReport saved = citizenReportRepository.save(report);

            // If reported as overflowing, update bin fillLevel to 100% and notify
            if (bin != null && ("OVERFLOW".equalsIgnoreCase(report.getReportType()) || "VANDALISM".equalsIgnoreCase(report.getReportType()))) {
                bin.setFillLevel(100);
                bin.setStatus("critical");
                bin.setLastUpdated(LocalDateTime.now());
                binService.updateBin(bin.getId(), bin);
                messagingTemplate.convertAndSend("/topic/bins", bin);
            }

            // Create an Incident so it triggers map avoidance & alerts
            Incident incident = new Incident();
            incident.setType("OVERFLOW".equalsIgnoreCase(report.getReportType()) ? IncidentType.OVERFILL : IncidentType.ROAD_BLOCK);
            incident.setStatus(IncidentStatus.ACTIVE);
            incident.setDescription("Citizen Report: " + report.getReportType() + (bin != null ? " on Bin #" + bin.getId() : ""));
            incident.setLatitude(report.getLatitude() != null ? report.getLatitude() : 36.8000);
            incident.setLongitude(report.getLongitude() != null ? report.getLongitude() : 10.1800);
            incident.setRadiusKm(0.06);
            incident.setCreatedAt(LocalDateTime.now());
            if (bin != null) {
                incident.setBin(bin);
            }

            Incident createdIncident = incidentService.reportRoadBlock(
                    incident.getLatitude(),
                    incident.getLongitude(),
                    incident.getRadiusKm(),
                    incident.getDescription()
            );

            // Broadcast notification via WebSocket
            Map<String, Object> alert = new HashMap<>();
            alert.put("type", "CITIZEN_REPORT");
            alert.put("reportId", saved.getId());
            alert.put("reportType", saved.getReportType());
            alert.put("binId", saved.getBinId());
            alert.put("departmentId", saved.getDepartmentId());
            alert.put("incidentId", createdIncident.getId());
            alert.put("message", "New citizen report received: " + saved.getReportType());
            messagingTemplate.convertAndSend("/topic/incidents", alert);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "reportId", saved.getId(),
                    "message", "Thank you! Your report has been submitted to the municipal fleet dispatch."
            ));
        } catch (Exception e) {
            log.error("Failed to submit citizen report", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}
