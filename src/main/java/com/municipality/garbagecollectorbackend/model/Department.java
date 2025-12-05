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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "departments")
@CompoundIndexes({
    @CompoundIndex(name = "location_idx", def = "{'latitude': 1, 'longitude': 1}")
})
public class Department implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String id;
    
    // Note: unique index on name is already in MongoDB from initial setup
    // Not using @Indexed(unique=true) to avoid issues with embedded documents
    private String name;
    
    private Double latitude;
    private Double longitude;

}