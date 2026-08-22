package com.municipality.garbagecollectorbackend.model;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class BinStop {
    private String binId;
    private double latitude;
    private double longitude;
    private int stopNumber;
    private String status;
    private LocalDateTime collectionTime;
    private double binFillLevelBefore;
    private Double minDistanceReached;

    public BinStop() {
        this.status = "PENDING";
    }

    public BinStop(String binId, double latitude, double longitude, int stopNumber) {
        this.binId = binId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopNumber = stopNumber;
        this.status = "PENDING";
    }

    public boolean isCollected() {
        return "COLLECTED".equalsIgnoreCase(status);
    }

    public void setCollected(boolean collected) {
        this.status = collected ? "COLLECTED" : "PENDING";
    }

    public LocalDateTime getCollectedAt() {
        return collectionTime;
    }

    public void setCollectedAt(LocalDateTime t) {
        this.collectionTime = t;
    }
}
