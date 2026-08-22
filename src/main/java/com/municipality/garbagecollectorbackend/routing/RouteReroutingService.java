package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.ActiveRouteRepository;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.municipality.garbagecollectorbackend.service.VehicleUpdatePublisher;
import org.springframework.beans.factory.annotation.Value;

/**
 * FIXED: Prevents infinite reroute loops with:
 * 1. Reroute cooldown (30 seconds)
 * 2. Skip rerouted routes check
 * 3. Larger detour radius (to avoid near-incident areas)
 * 4. Only reroute once per 30 seconds
 */
@Slf4j
@Service
public class RouteReroutingService {

    @Value("${osrm.server.url:http://localhost:5000}")
    private String osrmServerUrl;

    @Autowired
    private ActiveRouteRepository activeRouteRepository;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private RouteExecutionService routeExecutionService;

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private VehicleUpdatePublisher vehicleUpdatePublisher;

    private static final double INCIDENT_AVOID_RADIUS_KM = 0.15; // 150 meters
    private static final double REROUTE_TRIGGER_DISTANCE_KM = 3.0; // trigger when within 3km

 // NEW: Reroute cooldown to prevent infinite loops
    private static final long REROUTE_COOLDOWN_MS = 30000; // 30 seconds
    private final Map<String, Long> lastRerouteTime = new ConcurrentHashMap<>();

    @Scheduled(fixedRate = 5000)
    public void checkForIncidentsOnActiveRoutes() {
        List<ActiveRoute> activeRoutes = activeRouteRepository.findByStatus("IN_PROGRESS");
        List<Incident> activeIncidents = incidentService.getActiveIncidents();

        if (activeIncidents.isEmpty()) return;

        List<Incident> roadBlocks = new ArrayList<>();
        for (Incident incident : activeIncidents) {
            if (incident.getType() == IncidentType.ROAD_BLOCK) {
                roadBlocks.add(incident);
            }
        }

        for (ActiveRoute route : activeRoutes) {
 // FIX 1: Skip routes that are already rerouted
            if (route.isRerouted()) {
 log.debug(" Skipping already-rerouted route for vehicle {}", route.getVehicleId());
                continue;
            }

 // FIX 2: Check cooldown - don't reroute if we did it recently
            Long lastReroute = lastRerouteTime.get(route.getVehicleId());
            if (lastReroute != null && System.currentTimeMillis() - lastReroute < REROUTE_COOLDOWN_MS) {
 log.debug(" Vehicle {} in cooldown (rerouted {} ms ago)",
                        route.getVehicleId(),
                        System.currentTimeMillis() - lastReroute);
                continue;
            }

            RoutePoint currentPos = route.getCurrentPosition();
            if (currentPos == null) continue;

            boolean nearIncident = roadBlocks.stream()
                    .anyMatch(incident -> haversineDistance(
                            currentPos.getLatitude(), currentPos.getLongitude(),
                            incident.getLatitude(), incident.getLongitude()
                    ) <= REROUTE_TRIGGER_DISTANCE_KM);

            if (nearIncident) {
 log.warn(" Vehicle {} is near an incident, rerouting...", route.getVehicleId());
                rerouteVehicle(route, roadBlocks);
 // Record the reroute time immediately
                lastRerouteTime.put(route.getVehicleId(), System.currentTimeMillis());
            }
        }
    }

    private void rerouteVehicle(ActiveRoute route, List<Incident> incidents) {
        try {
            int currentBinIndex = Math.max(0, route.getCurrentBinIndex());
            List<BinStop> allStops = route.getBinStops() != null ? route.getBinStops() : new ArrayList<>();
            List<BinStop> remainingStops = allStops.subList(Math.min(currentBinIndex, allStops.size()), allStops.size());

            // Filter out collected bins
            List<BinStop> uncollectedStops = new ArrayList<>();
            for (BinStop s : remainingStops) {
                if (!"COLLECTED".equals(s.getStatus())) uncollectedStops.add(s);
            }

            if (uncollectedStops.isEmpty()) {
 log.info(" No remaining uncollected bins for vehicle {}", route.getVehicleId());
                return;
            }

            RoutePoint currentPos = route.getCurrentPosition();
            if (currentPos == null) {
 log.warn(" Current position unknown for vehicle {}, cannot reroute", route.getVehicleId());
                return;
            }

            String departmentId = route.getDepartmentId();
            Department dept = departmentService.getDepartmentById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

            // Build new polyline avoiding incidents using RouteOptimizationService
            List<String> uncollectedBinIds = uncollectedStops.stream()
                    .map(BinStop::getBinId)
                    .collect(Collectors.toList());

            RouteResponse rerouteResponse = routeOptimizationService.generateRerouteWithAvoidance(
                    route.getVehicleId(),
                    currentPos.getLatitude(),
                    currentPos.getLongitude(),
                    uncollectedBinIds,
                    departmentId,
                    incidents
            );

            List<RoutePoint> newPolyline = rerouteResponse.getPolyline();

            if (newPolyline == null || newPolyline.isEmpty()) {
                log.error("Reroute generated empty polyline for vehicle {}", route.getVehicleId());
                return;
            }

            int previousBinsCollected = route.getBinsCollected();
            route.setFullRoutePolyline(newPolyline);
            route.setAnimationProgress(0.0);
            route.setCurrentPosition(newPolyline.get(0));
            route.setRerouted(true);
            route.setTotalDistanceKm(calculateTotalDistance(newPolyline));

            // Rebuild BinStops from uncollectedStops
            List<BinStop> newBinStops = new ArrayList<>();
            for (int i = 0; i < uncollectedStops.size(); i++) {
                BinStop s = uncollectedStops.get(i);
                BinStop ns = new BinStop(s.getBinId(), s.getLatitude(), s.getLongitude(), i + 1);
                ns.setStatus(s.getStatus());
                ns.setBinFillLevelBefore(s.getBinFillLevelBefore());
                newBinStops.add(ns);
            }
            route.setBinStops(newBinStops);
            route.setCurrentBinIndex(0);
            route.setBinsCollected(previousBinsCollected);
            route.setTotalBins(previousBinsCollected + newBinStops.size());

            activeRouteRepository.save(route);
            vehicleUpdatePublisher.publishRouteUpdate(route.getVehicleId(), rerouteResponse);

            log.info("Vehicle {} rerouted with {} points and {} stops (cooldown: 30s)",
                    route.getVehicleId(), newPolyline.size(), newBinStops.size());

        } catch (Exception e) {
            log.error("Failed to reroute vehicle {}: {}", route.getVehicleId(), e.getMessage(), e);
        }
    }

    private List<RoutePoint> buildReroutedPolyline(RoutePoint currentPos,
                                                   List<BinStop> remainingStops,
                                                   List<Incident> incidents,
                                                   String departmentId) {
        List<RoutePoint> polyline = new ArrayList<>();
        int seq = 0;

        // Get department for return waypoint
        Department dept = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        // Build stops list: current -> remaining bins -> depot
        List<com.municipality.garbagecollectorbackend.model.Location> stops = new ArrayList<>();
        stops.add(new com.municipality.garbagecollectorbackend.model.Location(
                currentPos.getLatitude(), currentPos.getLongitude()));
        for (BinStop s : remainingStops) {
            stops.add(new com.municipality.garbagecollectorbackend.model.Location(
                    s.getLatitude(), s.getLongitude()));
        }
        stops.add(new com.municipality.garbagecollectorbackend.model.Location(
                dept.getLatitude(), dept.getLongitude()));

        for (int i = 0; i < stops.size() - 1; i++) {
            com.municipality.garbagecollectorbackend.model.Location from = stops.get(i);
            com.municipality.garbagecollectorbackend.model.Location to = stops.get(i + 1);

            Incident intersecting = null;
            for (Incident inc : incidents) {
                if (inc.getLatitude() == null || inc.getLongitude() == null) continue;
                if (isIncidentBlockingSegment(from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(), inc)) {
                    intersecting = inc;
                    break;
                }
            }

            List<com.municipality.garbagecollectorbackend.model.Location> waypointList = new ArrayList<>();
            waypointList.add(from);

            if (intersecting != null) {
                double bearing = calculateBearing(from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude());

                double detourDistanceKm = INCIDENT_AVOID_RADIUS_KM * 10.0; // 1.5 km

                double[] detourRight = getOffsetCoordinates(
                        intersecting.getLatitude(), intersecting.getLongitude(),
                        detourDistanceKm, bearing + 90.0);

                waypointList.add(new com.municipality.garbagecollectorbackend.model.Location(
                        detourRight[0], detourRight[1]));

                log.info("Added detour waypoint at ({}, {}) for segment {} (distance: {:.2f} km)",
                        detourRight[0], detourRight[1], i, detourDistanceKm);
            }

            waypointList.add(to);

            List<RoutePoint> segment = fetchOSRMPolylineWithWaypoints(waypointList, seq);

            if (segment.isEmpty()) {
                segment.add(new RoutePoint(from.getLatitude(), from.getLongitude(), seq++));
                segment.add(new RoutePoint(to.getLatitude(), to.getLongitude(), seq++));
            }

            if (i == 0) {
                polyline.addAll(segment);
            } else {
                if (segment.size() > 1) {
                    polyline.addAll(segment.subList(1, segment.size()));
                }
            }

            seq += segment.size();
        }

        return polyline;
    }

    private double calculateBearing(double fromLat, double fromLon, double toLat, double toLon) {
        double dLon = Math.toRadians(toLon - fromLon);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(toLat));
        double x = Math.cos(Math.toRadians(fromLat)) * Math.sin(Math.toRadians(toLat))
                - Math.sin(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    private List<RoutePoint> fetchOSRMPolylineWithWaypoints(
            List<com.municipality.garbagecollectorbackend.model.Location> waypoints,
            int startSequence) {

        List<RoutePoint> points = new ArrayList<>();
        try {
            StringBuilder sb = new StringBuilder();
            for (com.municipality.garbagecollectorbackend.model.Location wp : waypoints) {
                if (sb.length() > 0) sb.append(";");
                sb.append(String.format(Locale.US, "%.6f,%.6f", wp.getLongitude(), wp.getLatitude()));
            }

            String baseUrl = (osrmServerUrl != null && !osrmServerUrl.isEmpty()) ? osrmServerUrl : "https://router.project-osrm.org";
            String url = String.format(
                    "%s/route/v1/driving/%s?overview=full&geometries=geojson",
                    baseUrl,
                    sb.toString()
            );

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "GarbageCollectorBackend/1.0");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);

            RestTemplate restTemplate = new RestTemplate();
            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                JsonNode coords = root.path("routes").get(0).path("geometry").path("coordinates");
                int seq = startSequence;
                for (JsonNode coord : coords) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    points.add(new RoutePoint(lat, lng, seq++));
                }
            } else {
                log.warn("OSRM responded with code={} message={}",
                        root.path("code").asText(), root.path("message").asText());
            }
        } catch (Exception e) {
            log.error("OSRM fetch failed: {}", e.getMessage());
        }
        return points;
    }

    private boolean isIncidentBlockingSegment(double lat1, double lon1,
                                              double lat2, double lon2,
                                              Incident incident) {
        double[] closest = getClosestPointOnSegment(
                lat1, lon1, lat2, lon2,
                incident.getLatitude(), incident.getLongitude());
        double distanceMeters = calculateDistanceMeters(
                incident.getLatitude(), incident.getLongitude(),
                closest[0], closest[1]);
        double effectiveRadiusMeters =
                Math.min(incident.getRadiusKm(), INCIDENT_AVOID_RADIUS_KM) * 1000.0;
        return distanceMeters <= effectiveRadiusMeters;
    }

    private double[] getClosestPointOnSegment(double x1, double y1,
                                              double x2, double y2,
                                              double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) return new double[]{x1, y1};
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[]{x1 + t * dx, y1 + t * dy};
    }

    private double calculateDistanceMeters(double lat1, double lon1,
                                           double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double[] getOffsetCoordinates(double centerLat, double centerLon,
                                          double distanceKm, double bearingDegrees) {
        final double EARTH_RADIUS_KM = 6371.0;
        double bearingRad = Math.toRadians(bearingDegrees);
        double latRad = Math.toRadians(centerLat);
        double lonRad = Math.toRadians(centerLon);
        double angular = distanceKm / EARTH_RADIUS_KM;
        double newLatRad = Math.asin(Math.sin(latRad) * Math.cos(angular)
                + Math.cos(latRad) * Math.sin(angular) * Math.cos(bearingRad));
        double newLonRad = lonRad + Math.atan2(Math.sin(bearingRad) * Math.sin(angular) * Math.cos(latRad),
                Math.cos(angular) - Math.sin(latRad) * Math.sin(newLatRad));
        return new double[]{Math.toDegrees(newLatRad), Math.toDegrees(newLonRad)};
    }

    // Haversine in km
    private double haversineDistance(double lat1, double lon1,
                                     double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double calculateTotalDistance(List<RoutePoint> polyline) {
        double total = 0.0;
        for (int i = 0; i < polyline.size() - 1; i++) {
            RoutePoint p1 = polyline.get(i);
            RoutePoint p2 = polyline.get(i + 1);
            total += haversineDistance(
                    p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
        }
        return total;
    }
}
