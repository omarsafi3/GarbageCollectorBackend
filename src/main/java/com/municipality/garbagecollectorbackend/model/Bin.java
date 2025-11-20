package com.municipality.garbagecollectorbackend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "bins")
public class Bin {

    @Id
    private String id;
    private double latitude;
    private double longitude;
    private int fillLevel;
    private String status;
    private LocalDateTime lastEmptied;
    private LocalDateTime lastUpdated;


}
