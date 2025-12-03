package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for route completion events.
 * Sent when a vehicle completes its collection route.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteCompletionEvent {
    private String vehicleId;
    private int binsCollected;
}
