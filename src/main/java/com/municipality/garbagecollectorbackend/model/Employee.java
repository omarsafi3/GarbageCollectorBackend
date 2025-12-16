package com.municipality.garbagecollectorbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "employees")
@JsonIgnoreProperties(ignoreUnknown = true)
@CompoundIndexes({
    @CompoundIndex(name = "dept_role_idx", def = "{'department.id': 1, 'role': 1}"),
    @CompoundIndex(name = "dept_available_idx", def = "{'department.id': 1, 'available': 1}"),
    @CompoundIndex(name = "dept_status_idx", def = "{'department.id': 1, 'status': 1}")
})
public class Employee implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum EmployeeRole {
        DRIVER,
        COLLECTOR
    }

    @Id
    private String id;

    @Indexed
    private String firstName;

    @Indexed
    private String lastName;

    @Indexed
    private Boolean available;

    @Indexed
    private EmployeeRole role;  // DRIVER or COLLECTOR

    private Department department;

    // ✅ NEW: Track employee status
    @Indexed
    private EmployeeStatus status; // AVAILABLE, ASSIGNED, IN_ROUTE

    // ✅ NEW: Track which vehicle employee is assigned to
    @Indexed
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
