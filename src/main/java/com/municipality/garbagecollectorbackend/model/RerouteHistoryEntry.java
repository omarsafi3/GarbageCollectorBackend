package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a single reroute attempt with location and incident info
 * Used to detect reroute loops where vehicle keeps bouncing between incidents
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerouteHistoryEntry {
    private double latitude;
    private double longitude;
    private String incidentId;
    private LocalDateTime timestamp;
}
