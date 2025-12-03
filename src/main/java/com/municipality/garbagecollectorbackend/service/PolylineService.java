package com.municipality.garbagecollectorbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.RoutePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for building route polylines using OSRM API.
 * Encapsulates all OSRM-related operations.
 */
@Service
@Slf4j
public class PolylineService {

    @Value("${route.depot.latitude:34.0}")
    private double depotLatitude;

    @Value("${route.depot.longitude:9.0}")
    private double depotLongitude;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PolylineService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Builds a route polyline from depot through bins and back to depot.
     *
     * @param bins List of bins to include in the route
     * @return List of [lat, lng] coordinates for the polyline
     */
    public List<double[]> buildRoutePolyline(List<Bin> bins) {
        if (bins == null || bins.isEmpty()) {
            return new ArrayList<>();
        }

        // Build coordinates list: depot → bins → depot
        List<String> coordinates = new ArrayList<>();
        coordinates.add(depotLongitude + "," + depotLatitude); // OSRM uses lng,lat format

        for (Bin bin : bins) {
            coordinates.add(bin.getLongitude() + "," + bin.getLatitude());
        }

        coordinates.add(depotLongitude + "," + depotLatitude); // Return to depot

        return fetchOsrmPolyline(coordinates, bins);
    }

    /**
     * Builds a route polyline from a starting point through bins to a department.
     *
     * @param startLat Starting latitude
     * @param startLng Starting longitude
     * @param bins List of bins to include in the route
     * @param department Destination department
     * @return List of RoutePoint for the polyline
     */
    public List<RoutePoint> buildRoutePolylineWithDepartment(
            double startLat, double startLng, 
            List<Bin> bins, 
            Department department) {
        
        List<String> coordinates = new ArrayList<>();
        coordinates.add(startLng + "," + startLat);

        for (Bin bin : bins) {
            coordinates.add(bin.getLongitude() + "," + bin.getLatitude());
        }

        if (department != null) {
            coordinates.add(department.getLongitude() + "," + department.getLatitude());
        }

        List<double[]> polyline = fetchOsrmPolyline(coordinates, bins);
        
        // Convert to RoutePoint list
        List<RoutePoint> routePoints = new ArrayList<>();
        int index = 0;
        for (double[] point : polyline) {
            routePoints.add(new RoutePoint(point[0], point[1], index++));
        }
        
        return routePoints;
    }

    /**
     * Fetches polyline from OSRM API.
     */
    private List<double[]> fetchOsrmPolyline(List<String> coordinates, List<Bin> bins) {
        String coordsString = String.join(";", coordinates);
        String osrmUrl = "https://router.project-osrm.org/route/v1/driving/" + coordsString
                + "?overview=full&geometries=geojson";

        try {
            String response = restTemplate.getForObject(osrmUrl, String.class);
            JsonNode root = objectMapper.readTree(response);

            if ("Ok".equals(root.path("code").asText())) {
                JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");

                List<double[]> polyline = new ArrayList<>();
                for (JsonNode coord : coords) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    polyline.add(new double[]{lat, lng}); // Lat, Lng for Leaflet
                }

                log.info("✅ OSRM returned {} points for route with {} bins", 
                        polyline.size(), bins != null ? bins.size() : 0);
                return polyline;
            } else {
                log.warn("⚠️ OSRM returned code: {}", root.path("code").asText());
                return buildStraightLinePolyline(bins);
            }

        } catch (Exception e) {
            log.error("❌ Failed to fetch OSRM polyline: {}", e.getMessage());
            return buildStraightLinePolyline(bins);
        }
    }

    /**
     * Fallback method to build straight-line polyline when OSRM fails.
     */
    public List<double[]> buildStraightLinePolyline(List<Bin> bins) {
        List<double[]> polyline = new ArrayList<>();
        polyline.add(new double[]{depotLatitude, depotLongitude}); // Depot

        if (bins != null) {
            for (Bin bin : bins) {
                polyline.add(new double[]{bin.getLatitude(), bin.getLongitude()});
            }
        }

        polyline.add(new double[]{depotLatitude, depotLongitude}); // Return to depot
        return polyline;
    }

    /**
     * Gets the depot latitude.
     */
    public double getDepotLatitude() {
        return depotLatitude;
    }

    /**
     * Gets the depot longitude.
     */
    public double getDepotLongitude() {
        return depotLongitude;
    }
}
