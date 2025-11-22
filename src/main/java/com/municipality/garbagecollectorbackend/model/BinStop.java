package com.municipality.garbagecollectorbackend.model;

import java.time.LocalDateTime;

public class BinStop {
    private String binId;
    private double latitude;
    private double longitude;
    private int stopNumber;
    private String status;
    private LocalDateTime collectionTime;
    private double binFillLevelBefore;

    public BinStop() {}

    public BinStop(String binId, double latitude, double longitude, int stopNumber) {
        this.binId = binId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopNumber = stopNumber;
        this.status = "PENDING";
    }

    public String getBinId() { return binId; }
    public void setBinId(String binId) { this.binId = binId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getStopNumber() { return stopNumber; }
    public void setStopNumber(int stopNumber) { this.stopNumber = stopNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCollectionTime() { return collectionTime; }
    public void setCollectionTime(LocalDateTime collectionTime) { this.collectionTime = collectionTime; }

    public double getBinFillLevelBefore() { return binFillLevelBefore; }
    public void setBinFillLevelBefore(double binFillLevelBefore) { this.binFillLevelBefore = binFillLevelBefore; }
}
