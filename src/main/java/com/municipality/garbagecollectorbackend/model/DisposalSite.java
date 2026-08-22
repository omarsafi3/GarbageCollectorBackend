package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "disposal_sites")
public class DisposalSite {
    @Id
    private String id;
    private String name;
    private String type; // REGIONAL_LANDFILL, RECYCLING_CENTER, TRANSFER_STATION
    private double latitude;
    private double longitude;
    private double capacityTons;
    private double currentLoadTons;
    private String departmentId; // null if regional/shared across all districts
    private String operatingHours;
    private boolean active;
}
