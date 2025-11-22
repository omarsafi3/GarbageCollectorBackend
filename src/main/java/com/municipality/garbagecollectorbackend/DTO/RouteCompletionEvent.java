package com.municipality.garbagecollectorbackend.DTO;



public class RouteCompletionEvent {
    private String vehicleId;
    private int binsCollected;

    public RouteCompletionEvent() {}

    public RouteCompletionEvent(String vehicleId, int binsCollected) {
        this.vehicleId = vehicleId;
        this.binsCollected = binsCollected;
    }

    // Getters and setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }

    public int getBinsCollected() { return binsCollected; }
    public void setBinsCollected(int binsCollected) { this.binsCollected = binsCollected; }
}
