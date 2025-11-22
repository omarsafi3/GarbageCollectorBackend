package com.municipality.garbagecollectorbackend.model;

public class RoutePoint {
    private double latitude;
    private double longitude;
    private int sequenceNumber;

    public RoutePoint() {}

    public RoutePoint(double latitude, double longitude, int sequenceNumber) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.sequenceNumber = sequenceNumber;
    }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}
