package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "citizen_reports")
public class CitizenReport {
    @Id
    private String id;
    private String binId;
    private String departmentId;
    private String reportType; // OVERFLOW, DAMAGED, ODOR_HAZARD, VANDALISM, BLOCKED_ACCESS
    private String description;
    private String photoDataUrl;
    private Double latitude;
    private Double longitude;
    private String citizenName;
    private String citizenPhone;
    private LocalDateTime reportedAt;
    private String status; // PENDING, INVESTIGATING, RESOLVED
}
