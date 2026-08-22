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

    @Value("${osrm.server.url:http://localhost:5000}")
    private String osrmServerUrl;

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
        return buildRoutePolyline(bins, null);
    }

    public List<double[]> buildRoutePolyline(List<Bin> bins, Department department) {
        if (bins == null || bins.isEmpty()) {
            return new ArrayList<>();
        }

        double depLat = depotLatitude;
        double depLng = depotLongitude;
        if (department != null && department.getLatitude() != 0.0) {
            depLat = department.getLatitude();
            depLng = department.getLongitude();
        } else if (bins.get(0).getDepartment() != null && bins.get(0).getDepartment().getLatitude() != 0.0) {
            depLat = bins.get(0).getDepartment().getLatitude();
            depLng = bins.get(0).getDepartment().getLongitude();
        }

        // Build coordinates list: depot → bins → depot
        List<String> coordinates = new ArrayList<>();
        coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", depLng, depLat)); // OSRM uses lng,lat format

        for (Bin bin : bins) {
            if (bin != null) {
                coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", bin.getLongitude(), bin.getLatitude()));
            }
        }

        coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", depLng, depLat)); // Return to depot

        return fetchOsrmPolyline(coordinates, bins, depLat, depLng);
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
        coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", startLng, startLat));

        if (bins != null) {
            for (Bin bin : bins) {
                if (bin != null) {
                    coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", bin.getLongitude(), bin.getLatitude()));
                }
            }
        }

        double endLat = (department != null && department.getLatitude() != 0.0) ? department.getLatitude() : depotLatitude;
        double endLng = (department != null && department.getLongitude() != 0.0) ? department.getLongitude() : depotLongitude;
        coordinates.add(String.format(java.util.Locale.US, "%.6f,%.6f", endLng, endLat));

        List<double[]> polyline = fetchOsrmPolyline(coordinates, bins, startLat, startLng);
        
        // Convert to RoutePoint list
        List<RoutePoint> routePoints = new ArrayList<>();
        int index = 0;
        for (double[] point : polyline) {
            routePoints.add(new RoutePoint(point[0], point[1], index++));
        }
        
        return routePoints;
    }

    /**
     * Fetches polyline from OSRM API with automatic local Docker + remote fallback.
     */
    private List<double[]> fetchOsrmPolyline(List<String> coordinates, List<Bin> bins, double fallbackDepotLat, double fallbackDepotLng) {
        String coordsString = String.join(";", coordinates);
        String primaryUrl = (osrmServerUrl != null && !osrmServerUrl.isEmpty()) ? osrmServerUrl : "http://localhost:5000";
        
        List<double[]> result = tryFetchFromUrl(primaryUrl, coordsString, bins);
        if (result != null) {
            return result;
        }

        // If primary was local and failed, try public OSRM router as backup
        if (!primaryUrl.contains("project-osrm.org")) {
            log.info("Primary OSRM ({}) unreachable, attempting backup router (https://router.project-osrm.org)...", primaryUrl);
            result = tryFetchFromUrl("https://router.project-osrm.org", coordsString, bins);
            if (result != null) {
                return result;
            }
        }

        log.warn("All OSRM endpoints failed, falling back to straight-line polyline");
        return buildStraightLinePolyline(bins, fallbackDepotLat, fallbackDepotLng);
    }

    private List<double[]> tryFetchFromUrl(String baseUrl, String coordsString, List<Bin> bins) {
        String osrmUrl = baseUrl + "/route/v1/driving/" + coordsString + "?overview=full&geometries=geojson";
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "GarbageCollectorBackend/1.0");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    osrmUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");

                List<double[]> polyline = new ArrayList<>();
                for (JsonNode coord : coords) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    polyline.add(new double[]{lat, lng}); // Lat, Lng for Leaflet
                }

                log.info("OSRM ({}) returned {} points for route with {} bins", 
                        baseUrl, polyline.size(), bins != null ? bins.size() : 0);
                return polyline;
            }
        } catch (Exception e) {
            log.debug("OSRM fetch from {} failed: {}", baseUrl, e.getMessage());
        }
        return null;
    }

    /**
     * Fallback method to build straight-line polyline when OSRM fails.
     */
    public List<double[]> buildStraightLinePolyline(List<Bin> bins, double depLat, double depLng) {
        List<double[]> polyline = new ArrayList<>();
        polyline.add(new double[]{depLat, depLng}); // Depot

        if (bins != null) {
            for (Bin bin : bins) {
                polyline.add(new double[]{bin.getLatitude(), bin.getLongitude()});
            }
        }

        polyline.add(new double[]{depLat, depLng}); // Return to depot
        return polyline;
    }

    public List<double[]> buildStraightLinePolyline(List<Bin> bins) {
        return buildStraightLinePolyline(bins, depotLatitude, depotLongitude);
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
