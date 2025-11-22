package com.municipality.garbagecollectorbackend.DTO;

public class TruckPositionUpdate {
    private String vehicleId;
    private double latitude;
    private double longitude;
    private double progressPercent; // 0.0 to 100.0

    public TruckPositionUpdate() {}

    public TruckPositionUpdate(String vehicleId, double latitude, double longitude, double progressPercent) {
        this.vehicleId = vehicleId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.progressPercent = progressPercent;
    }

    // Getters and setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getProgressPercent() { return progressPercent; }
    public void setProgressPercent(double progressPercent) { this.progressPercent = progressPercent; }
}
