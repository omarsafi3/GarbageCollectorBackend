package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vehicles")
@CompoundIndexes({
    @CompoundIndex(name = "dept_status_idx", def = "{'department.id': 1, 'status': 1}"),
    @CompoundIndex(name = "dept_available_idx", def = "{'department.id': 1, 'available': 1}")
})
public class Vehicle implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum VehicleStatus {
        AVAILABLE,      // Ready for new route
        IN_ROUTE,       // Currently collecting bins
        RETURNING,      // Going back to depot
        UNLOADING       // Emptying at depot
    }


    @Id
    private String id;
    
    @Indexed
    private String reference;
    
    @Indexed(unique = true)
    private String plate;
    
    private Double fillLevel;
    
    @Indexed
    private Boolean available;
    
    private Department department;

    @Indexed
    private VehicleStatus status = VehicleStatus.AVAILABLE;  // Default to AVAILABLE
    private LocalDateTime statusUpdatedAt;  // Track when status changed

}