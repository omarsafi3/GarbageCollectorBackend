package com.municipality.garbagecollectorbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "incidents")
public class Incident {

    @Id
    private String id;

    private String type;
    private String status;
    private Bin bin;
    private Location location;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
