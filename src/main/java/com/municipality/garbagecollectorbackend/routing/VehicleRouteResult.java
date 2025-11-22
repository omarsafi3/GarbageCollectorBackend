
package com.municipality.garbagecollectorbackend.routing;

import java.util.List;

public class VehicleRouteResult {
    private String vehicleId;
    private List<String> binIds;

    public VehicleRouteResult(String vehicleId, List<String> binIds) {
        this.vehicleId = vehicleId;
        this.binIds = binIds;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public List<String> getBinIds() {
        return binIds;
    }

    public void setBinIds(List<String> binIds) {
        this.binIds = binIds;
    }
}
