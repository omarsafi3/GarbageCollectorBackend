package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a bin in a route.
 * Contains the bin ID and its coordinates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteBin {
    private String id;
    private double latitude;
    private double longitude;
}
