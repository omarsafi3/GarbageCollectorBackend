package com.municipality.garbagecollectorbackend.routing;

import java.util.List;

public class VehicleRouteResult {
    private String vehicleId;
    private List<String> orderedBinIds;  // ✅ Field name

    public VehicleRouteResult() {}

    public VehicleRouteResult(String vehicleId, List<String> orderedBinIds) {
        this.vehicleId = vehicleId;
        this.orderedBinIds = orderedBinIds;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public List<String> getOrderedBinIds() {  // ✅ Getter name matches field
        return orderedBinIds;
    }

    public void setOrderedBinIds(List<String> orderedBinIds) {
        this.orderedBinIds = orderedBinIds;
    }
}
