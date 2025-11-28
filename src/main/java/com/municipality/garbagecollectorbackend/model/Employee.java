package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "employees")
public class Employee {

    @Id
    private String id;

    private String firstName;

    private String lastName;

    private Boolean available;

    private Department department;

    // ✅ NEW: Track employee status
    private EmployeeStatus status; // AVAILABLE, ASSIGNED, IN_ROUTE

    // ✅ NEW: Track which vehicle employee is assigned to
    private String assignedVehicleId;

    // ✅ Helper method to get full name
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // ✅ Helper method to check if employee can be assigned
    public boolean canBeAssigned() {
        return available && (status == null || status == EmployeeStatus.AVAILABLE);
    }
}
