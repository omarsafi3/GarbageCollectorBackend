package com.municipality.garbagecollectorbackend.DTO;

public class RouteProgressUpdate {
    private String vehicleId;
    private int currentStop;
    private int totalStops;
    private String currentBinId;
    private double vehicleFillLevel;

    public RouteProgressUpdate() {}

    public RouteProgressUpdate(String vehicleId, int currentStop, int totalStops,
                               String currentBinId, double vehicleFillLevel) {
        this.vehicleId = vehicleId;
        this.currentStop = currentStop;
        this.totalStops = totalStops;
        this.currentBinId = currentBinId;
        this.vehicleFillLevel = vehicleFillLevel;
    }

    // Getters and setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public int getCurrentStop() { return currentStop; }
    public void setCurrentStop(int currentStop) { this.currentStop = currentStop; }

    public int getTotalStops() { return totalStops; }
    public void setTotalStops(int totalStops) { this.totalStops = totalStops; }

    public String getCurrentBinId() { return currentBinId; }
    public void setCurrentBinId(String currentBinId) { this.currentBinId = currentBinId; }

    public double getVehicleFillLevel() { return vehicleFillLevel; }
    public void setVehicleFillLevel(double vehicleFillLevel) { this.vehicleFillLevel = vehicleFillLevel; }
}