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
@Document(collection = "bins")
@CompoundIndexes({
    @CompoundIndex(name = "dept_fill_idx", def = "{'department.id': 1, 'fillLevel': -1}"),
    @CompoundIndex(name = "dept_status_idx", def = "{'department.id': 1, 'status': 1}"),
    @CompoundIndex(name = "location_idx", def = "{'latitude': 1, 'longitude': 1}")
})
public class Bin implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String id;
    
    private double latitude;
    private double longitude;
    
    @Indexed
    private int fillLevel;
    
    @Indexed
    private String status;
    
    private LocalDateTime lastEmptied;
    
    @Indexed
    private LocalDateTime lastUpdated;
    
    private Department department;

}
