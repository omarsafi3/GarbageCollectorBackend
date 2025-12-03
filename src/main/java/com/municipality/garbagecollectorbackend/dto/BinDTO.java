package com.municipality.garbagecollectorbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Bin information.
 * Used for API responses where only basic bin location data is needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BinDTO {
    private String id;
    private double latitude;
    private double longitude;
}
