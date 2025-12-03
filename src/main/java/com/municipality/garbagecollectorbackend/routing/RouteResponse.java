package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.dto.RouteBin;
import com.municipality.garbagecollectorbackend.model.RoutePoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private String routeId;
    private List<RouteBin> bins;
    private List<RoutePoint> polyline;
    private double totalDistanceKm;
    private int binCount;
}
