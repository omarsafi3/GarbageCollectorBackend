package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.DTO.*;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.ActiveRouteRepository;
import com.municipality.garbagecollectorbackend.service.*;
import com.municipality.garbagecollectorbackend.model.RoutePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RouteExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RouteExecutionService.class);

    @Value("${osrm.server.url}")
    private String osrmServerUrl;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ActiveRouteRepository activeRouteRepository;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private BinService binService;

    @Autowired
    private RouteOptimizationService routeService;

    @Autowired
    private BinUpdatePublisher binUpdatePublisher;

    @Autowired
    private VehicleUpdatePublisher vehicleUpdatePublisher;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    @Lazy
    private IncidentService incidentService;

    private final Map<String, Long> lastRerouteTime = new ConcurrentHashMap<>();
    private static final long REROUTE_COOLDOWN_MS = 4000;

    private final Map<String, Map<String, Object>> activeVehiclesInfo = new ConcurrentHashMap<>();

    public List<Map<String, Object>> getActiveVehiclesInfo() {
        return new ArrayList<>(activeVehiclesInfo.values());
    }

    public ActiveRoute startRouteWithFullPath(String departmentId, String vehicleId) {
        Optional<ActiveRoute> existingOpt = activeRouteRepository.findByVehicleId(vehicleId);
        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
        if (existingOpt.isPresent()) {
            ActiveRoute existing = existingOpt.get();
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                logger.warn("⚠️ Vehicle {} already has active route. Cancelling it.", vehicleId);
                existing.setStatus("CANCELLED");
                activeRouteRepository.save(existing);
            }
        }

        List<PreGeneratedRoute> availableRoutes = routeService.getAvailableRoutes(departmentId);
        List<RouteBin> routeBins;
        List<RoutePoint> fullPolyline;

        if (!availableRoutes.isEmpty()) {
            PreGeneratedRoute preGenRoute = availableRoutes.get(0);
            routeService.assignRouteToVehicle(preGenRoute.getRouteId(), vehicleId);
            routeBins = preGenRoute.getRouteBins();
            fullPolyline = preGenRoute.getPolyline();
            logger.info("✅ Using pre-generated route {} with {} points", preGenRoute.getRouteId(), fullPolyline.size());
        } else {
            logger.warn("⚠️ No pre-generated routes available, building fresh for vehicle {}", vehicleId);
            routeBins = routeService.getOptimizedRoute(departmentId, vehicleId);
            fullPolyline = buildCompletePolyline(routeBins, departmentId);
            fullPolyline = ensureDepartmentReturnLastPoint(fullPolyline, department);
        }

        List<BinStop> binStops = new ArrayList<>();
        for (int i = 0; i < routeBins.size(); i++) {
            RouteBin rb = routeBins.get(i);
            Bin bin = binService.getBinById(rb.getId());
            BinStop stop = new BinStop(rb.getId(), rb.getLatitude(), rb.getLongitude(), i + 1);
            stop.setBinFillLevelBefore(bin.getFillLevel());
            binStops.add(stop);
        }

        ActiveRoute route = new ActiveRoute();
        route.setVehicleId(vehicleId);
        route.setDepartmentId(departmentId);
        route.setFullRoutePolyline(fullPolyline);
        route.setBinStops(binStops);
        route.setCurrentPosition(fullPolyline.get(0));
        route.setAnimationProgress(0.0);
        route.setCurrentBinIndex(0);
        route.setStatus("IN_PROGRESS");
        route.setStartTime(LocalDateTime.now());
        route.setLastUpdateTime(LocalDateTime.now());
        route.setTotalBins(binStops.size());
        route.setBinsCollected(0);
        route.setTotalDistanceKm(calculateTotalDistance(fullPolyline));

        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));
        vehicle.setAvailable(false);
        vehicleService.saveVehicle(vehicle);

        ActiveRoute savedRoute = activeRouteRepository.save(route);

        department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        Map<String, Object> vehicleInfo = new HashMap<>();
        vehicleInfo.put("vehicleId", vehicleId);
        vehicleInfo.put("reference", vehicle.getReference());
        vehicleInfo.put("fillLevel", 0.0);
        vehicleInfo.put("latitude", department.getLatitude());
        vehicleInfo.put("longitude", department.getLongitude());
        vehicleInfo.put("activeRouteId", savedRoute.getId());

        activeVehiclesInfo.put(vehicleId, vehicleInfo);

        logger.info("✅ Registered active vehicle: {} with route ID: {}", vehicleId, savedRoute.getId());
        logger.info("🚀 Started route for vehicle {}: {} bins, {} points, {:.2f} km",
                vehicleId, binStops.size(), fullPolyline.size(), route.getTotalDistanceKm());

        return savedRoute;
    }

    private List<RoutePoint> buildCompletePolyline(List<RouteBin> routeBins, String departmentId) {
        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
        Location startLoc = new Location(department.getLatitude(), department.getLongitude());
        return buildCompletePolylineFromLocation(routeBins, departmentId, startLoc);
    }

    private List<RoutePoint> buildCompletePolylineFromLocation(List<RouteBin> routeBins, String departmentId, Location startLocation) {
        List<RoutePoint> polyline = new ArrayList<>();
        int sequenceNumber = 0;

        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        List<Incident> activeIncidents = incidentService.getActiveIncidents().stream()
                .filter(i -> i.getType() == IncidentType.ROAD_BLOCK)
                .filter(i -> i.getLatitude() != null && i.getLongitude() != null)
                .collect(Collectors.toList());

        List<Location> stops = new ArrayList<>();
        stops.add(startLocation);
        routeBins.forEach(rb -> stops.add(new Location(rb.getLatitude(), rb.getLongitude())));
        stops.add(new Location(department.getLatitude(), department.getLongitude()));

        for (int i = 0; i < stops.size() - 1; i++) {
            Location from = stops.get(i);
            Location to = stops.get(i + 1);

            Incident blockingIncident = findBlockingIncident(from, to, activeIncidents);
            List<RoutePoint> segment = new ArrayList<>();

            if (blockingIncident != null) {
                logger.warn("🚧 Segment {} blocked by incident. Applying Box Detour.", i);
                segment = fetchOSRMAlternativeSafeRoute(from, to, blockingIncident, sequenceNumber);

                if (segment.isEmpty()) {
                    logger.info("⚠️ Alternatives failed. Forcing Box Detour.");
                    segment = generateBoxDetour(from, to, blockingIncident, sequenceNumber);
                }
            } else {
                segment = fetchOSRMPolyline(
                        from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(),
                        sequenceNumber
                );
            }

            if (i == 0) {
                polyline.addAll(segment);
            } else if (segment.size() > 1) {
                polyline.addAll(segment.subList(1, segment.size()));
            }

            if (!segment.isEmpty()) {
                sequenceNumber = segment.get(segment.size() - 1).getSequenceNumber();
            }
        }
        return polyline;
    }

    private Incident findBlockingIncident(Location from, Location to, List<Incident> incidents) {
        for (Incident incident : incidents) {
            double[] closest = getClosestPointOnSegment(from.getLatitude(), from.getLongitude(),
                    to.getLatitude(), to.getLongitude(), incident.getLatitude(), incident.getLongitude());
            double dist = calculateDistance(incident.getLatitude(), incident.getLongitude(), closest[0], closest[1]);

            if (dist < (incident.getRadiusKm() * 1000.0) + 20) {
                return incident;
            }
        }
        return null;
    }

    private List<RoutePoint> fetchOSRMAlternativeSafeRoute(Location from, Location to, Incident incident, int startSeq) {
        try {
            String url = String.format(
                    "%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson&alternatives=true",
                    osrmServerUrl, from.getLongitude(), from.getLatitude(), to.getLongitude(), to.getLatitude()
            );

            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if ("Ok".equals(root.path("code").asText())) {
                JsonNode routes = root.path("routes");
                for (JsonNode route : routes) {
                    List<RoutePoint> candidate = parseOSRMRoute(route, startSeq);
                    if (!isRouteBlockedByIncident(candidate, incident)) {
                        return candidate;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed fetching alternatives", e);
        }
        return new ArrayList<>();
    }

    private List<RoutePoint> generateBoxDetour(Location from, Location to, Incident incident, int startSeq) {
        double[] multipliers = {2.5, 5.0, 8.0, 12.0};

        for (double mult : multipliers) {
            double safeDistKm = Math.max(incident.getRadiusKm() * mult, 0.5);

            Location[] corners = new Location[4];
            corners[0] = getLocationAt(incident, safeDistKm, 0);
            corners[1] = getLocationAt(incident, safeDistKm, 90);
            corners[2] = getLocationAt(incident, safeDistKm, 180);
            corners[3] = getLocationAt(incident, safeDistKm, 270);

            Location c1 = getClosestCorner(from, corners);
            Location c2 = getClosestCorner(to, corners);

            logger.info("🔄 Attempting Box Detour with radius: {} km (Multiplier: {})", String.format("%.2f", safeDistKm), mult);

            List<RoutePoint> segment = fetchOSRMMultiPoint(Arrays.asList(from, c1, c2, to), startSeq);

            if (!segment.isEmpty()) {
                logger.info("✅ Valid ROAD path found with radius {} km", String.format("%.2f", safeDistKm));
                return segment;
            }
            logger.warn("⚠️ Detour too tight or invalid for radius {} km. Expanding search...", String.format("%.2f", safeDistKm));
        }

        logger.error("⛔ All detour attempts failed. No valid roads found surrounding the incident.");
        return new ArrayList<>();
    }

    private List<RoutePoint> fetchOSRMMultiPoint(List<Location> points, int startSeq) {
        return fetchOSRMPolylineWithWaypoints(points, startSeq);
    }

    private List<RoutePoint> fetchOSRMPolylineWithWaypoints(List<Location> waypoints, int startSequence) {
        List<RoutePoint> points = new ArrayList<>();
        try {
            StringBuilder sb = new StringBuilder();
            for (Location wp : waypoints) {
                if (sb.length() > 0) sb.append(";");
                sb.append(wp.getLongitude()).append(",").append(wp.getLatitude());
            }

            String url = String.format(
                    "%s/route/v1/driving/%s?overview=full&geometries=geojson",
                    osrmServerUrl,
                    sb.toString()
            );

            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if ("Ok".equals(root.path("code").asText())) {
                JsonNode coordinates = root.path("routes").get(0).path("geometry").path("coordinates");
                int seq = startSequence;
                for (JsonNode coord : coordinates) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    points.add(new RoutePoint(lat, lng, seq++));
                }
            } else {
                int seq = startSequence;
                for(Location wp : waypoints) {
                    points.add(new RoutePoint(wp.getLatitude(), wp.getLongitude(), seq++));
                }
            }
        } catch (Exception e) {
            logger.error("⛔ Failed to fetch OSRM waypoints: {}", e.getMessage());
        }
        return points;
    }

    private boolean isRouteBlockedByIncident(List<RoutePoint> route, Incident incident) {
        double safeRadius = (incident.getRadiusKm() * 1000.0) + 10.0;
        for (RoutePoint p : route) {
            double d = calculateDistance(p.getLatitude(), p.getLongitude(), incident.getLatitude(), incident.getLongitude());
            if (d <= safeRadius) return true;
        }
        return false;
    }

    private Location getLocationAt(Incident center, double distKm, double bearing) {
        double[] coords = getOffsetCoordinates(center.getLatitude(), center.getLongitude(), distKm, bearing);
        return new Location(coords[0], coords[1]);
    }

    private Location getClosestCorner(Location target, Location[] corners) {
        Location best = corners[0];
        double minD = Double.MAX_VALUE;
        for(Location c : corners) {
            double d = calculateDistance(target.getLatitude(), target.getLongitude(), c.getLatitude(), c.getLongitude());
            if(d < minD) {
                minD = d;
                best = c;
            }
        }
        return best;
    }

    private List<RoutePoint> parseOSRMRoute(JsonNode routeNode, int startSeq) {
        List<RoutePoint> points = new ArrayList<>();
        JsonNode coords = routeNode.path("geometry").path("coordinates");
        int seq = startSeq;
        for (JsonNode coord : coords) {
            points.add(new RoutePoint(coord.get(1).asDouble(), coord.get(0).asDouble(), seq++));
        }
        return points;
    }

    private double[] getClosestPointOnSegment(double x1, double y1, double x2, double y2, double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) return new double[]{x1, y1};
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[]{x1 + t * dx, y1 + t * dy};
    }

    private double[] getOffsetCoordinates(double centerLat, double centerLon, double distanceKm, double bearingDegrees) {
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

    private double calculateBearing(double fromLat, double fromLon, double toLat, double toLon) {
        double dLon = Math.toRadians(toLon - fromLon);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(toLat));
        double x = Math.cos(Math.toRadians(fromLat)) * Math.sin(Math.toRadians(toLat))
                - Math.sin(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    public List<ActiveRoute> getAllActiveRoutes() {
        return activeRouteRepository.findByStatus("IN_PROGRESS");
    }

    public void updateRoute(String vehicleId, RouteResponse newRoute) {
        Optional<ActiveRoute> routeOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (routeOpt.isEmpty()) {
            logger.warn("⚠️ Tried to update route for non-existent vehicle: {}", vehicleId);
            return;
        }
        ActiveRoute route = routeOpt.get();
        route.setFullRoutePolyline(newRoute.getPolyline());
        route.setBinStops(
                newRoute.getBins().stream()
                        .map(b -> new BinStop(b.getId(), b.getLatitude(), b.getLongitude(), 0))
                        .collect(Collectors.toList())
        );
        route.setAnimationProgress(0.0);
        route.setCurrentBinIndex(0);
        route.setBinsCollected(0);
        route.setTotalBins(newRoute.getBins().size());
        route.setTotalDistanceKm(calculateTotalDistance(newRoute.getPolyline()));
        activeRouteRepository.save(route);

        vehicleUpdatePublisher.publishRouteUpdate(vehicleId, newRoute);
        logger.info("🔄 Vehicle {} route updated and pushed to clients", vehicleId);
    }

    public void completeRoute(String vehicleId) {
        Optional<ActiveRoute> routeOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (routeOpt.isEmpty()) {
            logger.warn("⚠️ Tried to complete non-existent route for vehicle: {}", vehicleId);
            return;
        }
        ActiveRoute route = routeOpt.get();
        completeRoute(route);
    }

    private List<RoutePoint> fetchOSRMPolyline(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng,
            int startSequence
    ) {
        List<RoutePoint> points = new ArrayList<>();
        try {
            String url = String.format(
                    "%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    osrmServerUrl, fromLng, fromLat, toLng, toLat
            );

            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response);

            if ("Ok".equals(root.path("code").asText())) {
                JsonNode coordinates = root.path("routes").get(0).path("geometry").path("coordinates");
                int seq = startSequence;
                for (JsonNode coord : coordinates) {
                    points.add(new RoutePoint(coord.get(1).asDouble(), coord.get(0).asDouble(), seq++));
                }
            } else {
                points.add(new RoutePoint(fromLat, fromLng, startSequence));
                points.add(new RoutePoint(toLat, toLng, startSequence + 1));
            }
        } catch (Exception e) {
            log.error("❌ OSRM fetch failed: {}", e.getMessage());
            points.add(new RoutePoint(fromLat, fromLng, startSequence));
            points.add(new RoutePoint(toLat, toLng, startSequence + 1));
        }
        return points;
    }

    private double calculateTotalDistance(List<RoutePoint> polyline) {
        double totalKm = 0.0;
        for (int i = 0; i < polyline.size() - 1; i++) {
            RoutePoint p1 = polyline.get(i);
            RoutePoint p2 = polyline.get(i + 1);
            totalKm += haversineDistance(p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
        }
        return totalKm;
    }

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isNearActiveIncident(double lat, double lng, String vehicleId) {
        Long lastReroute = lastRerouteTime.get(vehicleId);
        if (lastReroute != null && System.currentTimeMillis() - lastReroute < REROUTE_COOLDOWN_MS) {
            return false;
        }

        List<Incident> activeIncidents = incidentService.getActiveIncidents();
        for (Incident incident : activeIncidents) {
            if (incident.getType() != IncidentType.ROAD_BLOCK) continue;
            if (incident.getLatitude() == null || incident.getLongitude() == null) continue;

            double distance = calculateDistance(lat, lng, incident.getLatitude(), incident.getLongitude());
            double maxIncidentRadius = 0.08;
            double effectiveRadius = Math.min(incident.getRadiusKm(), maxIncidentRadius);

            if (distance <= effectiveRadius * 1000) {
                logger.warn("🚧 Vehicle {} approaching incident - Distance: {}m", vehicleId, (int)distance);
                lastRerouteTime.put(vehicleId, System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    private void triggerReroute(ActiveRoute route, RoutePoint currentPosition, List<BinStop> remainingBins) {
        try {
            Set<String> collectedBinIds = route.getBinStops().stream()
                    .filter(s -> "COLLECTED".equals(s.getStatus()))
                    .map(BinStop::getBinId)
                    .collect(Collectors.toSet());

            List<String> remainingBinIds = remainingBins.stream()
                    .filter(binStop -> !"COLLECTED".equals(binStop.getStatus()))
                    .map(BinStop::getBinId)
                    .collect(Collectors.toList());

            if (remainingBinIds.isEmpty()) {
                logger.info("✅ All bins collected. Returning to department.");
                return;
            }

            logger.info("🔄 Generating reroute with {} uncollected bins", remainingBinIds.size());

            List<Incident> incidentsToAvoid = incidentService.getActiveIncidents().stream()
                    .filter(i -> i.getType() == IncidentType.ROAD_BLOCK)
                    .filter(i -> i.getLatitude() != null && i.getLongitude() != null)
                    .collect(Collectors.toList());

            RouteResponse newRoute = routeService.generateRerouteWithAvoidance(
                    route.getVehicleId(),
                    currentPosition.getLatitude(),
                    currentPosition.getLongitude(),
                    remainingBinIds,
                    route.getDepartmentId(),
                    incidentsToAvoid
            );

            if (newRoute.getBins().isEmpty()) {
                logger.info("✅ No valid bins to visit - returning to department.");
                return;
            }

            Location startLoc = new Location(currentPosition.getLatitude(), currentPosition.getLongitude());
            List<RoutePoint> completePolyline = buildCompletePolylineFromLocation(
                    newRoute.getBins(),
                    route.getDepartmentId(),
                    startLoc
            );

            route.setFullRoutePolyline(completePolyline);
            route.setAnimationProgress(0.0);
            double totalDistanceKm = calculateTotalDistance(completePolyline);
            route.setTotalDistanceKm(totalDistanceKm);

            List<BinStop> newBinStops = newRoute.getBins().stream()
                    .filter(b -> !collectedBinIds.contains(b.getId()))
                    .map(b -> new BinStop(b.getId(), b.getLatitude(), b.getLongitude(), 0))
                    .collect(Collectors.toList());

            route.setBinStops(newBinStops);
            route.setCurrentBinIndex(0);
            route.setTotalBins(newBinStops.size());

            activeRouteRepository.save(route);

            newRoute.setPolyline(completePolyline);
            newRoute.setTotalDistanceKm(totalDistanceKm);

            vehicleUpdatePublisher.publishRouteUpdate(route.getVehicleId(), newRoute);

            logger.info("✅ Vehicle {} rerouted successfully with safe return path", route.getVehicleId());

        } catch (Exception e) {
            logger.error("❌ Failed to reroute vehicle {}: {}", route.getVehicleId(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 250)
    public void updateActiveTruckPositions() {
        List<ActiveRoute> activeRoutes = activeRouteRepository.findByStatus("IN_PROGRESS");
        for (ActiveRoute route : activeRoutes) {
            try {
                updateTruckPosition(route);
            } catch (Exception e) {
                logger.error("⛔ Error: {}", e.getMessage());
            }
        }
    }
    /**
     * ✅ CRITICAL: Ensure vehicle returns to EXACT department location
     */
    private List<RoutePoint> ensureDepartmentReturnLastPoint(
            List<RoutePoint> polyline,
            Department department) {

        if (polyline == null || polyline.isEmpty()) {
            return polyline;
        }

        RoutePoint lastPoint = polyline.get(polyline.size() - 1);
        double distToDepartment = calculateDistance(
                lastPoint.getLatitude(), lastPoint.getLongitude(),
                department.getLatitude(), department.getLongitude()
        );

        // If last point is far from department, add it
        if (distToDepartment > 0.05) { // >50 meters
            log.warn("⚠️ Last point is {}km from department - ADDING RETURN",
                    String.format("%.2f", distToDepartment));

            polyline.add(new RoutePoint(
                    department.getLatitude(),
                    department.getLongitude(),
                    polyline.size()
            ));
        } else {
            // Replace with EXACT department coordinates
            polyline.set(polyline.size() - 1, new RoutePoint(
                    department.getLatitude(),
                    department.getLongitude(),
                    lastPoint.getSequenceNumber()
            ));
        }

        return polyline;
    }

    private void updateTruckPosition(ActiveRoute route) {
        double simulationSpeedKmH = 480.0;

        double timeStepHours = 0.25 / 3600.0;
        double distancePerTickKm = simulationSpeedKmH * timeStepHours;

        double totalDistance = route.getTotalDistanceKm();
        double progressIncrement;

        if (totalDistance > 0) {
            progressIncrement = distancePerTickKm / totalDistance;
        } else {
            progressIncrement = 1.0;
        }

        double currentProgress = route.getAnimationProgress();
        double newProgress = Math.min(1.0, currentProgress + progressIncrement);

        List<RoutePoint> polyline = route.getFullRoutePolyline();
        int targetIndex = (int) (newProgress * (polyline.size() - 1));

        if (targetIndex >= polyline.size()) targetIndex = polyline.size() - 1;

        RoutePoint newPosition = polyline.get(targetIndex);

        route.setAnimationProgress(newProgress);
        route.setCurrentPosition(newPosition);
        route.setLastUpdateTime(LocalDateTime.now());

        Map<String, Object> vehicleInfo = activeVehiclesInfo.get(route.getVehicleId());
        if (vehicleInfo != null) {
            vehicleInfo.put("latitude", newPosition.getLatitude());
            vehicleInfo.put("longitude", newPosition.getLongitude());
            vehicleInfo.put("fillLevel", newProgress * 100.0);
        }

        if (isNearActiveIncident(newPosition.getLatitude(), newPosition.getLongitude(), route.getVehicleId())) {
            List<BinStop> remainingBins = route.getBinStops().subList(
                    route.getCurrentBinIndex(),
                    route.getBinStops().size()
            );
            if (!remainingBins.isEmpty()) {
                triggerReroute(route, newPosition, remainingBins);
                return;
            }
        }

        checkBinCollection(route, newPosition);

        if (newProgress >= 1.0) {
            completeRoute(route);
            return;
        }

        activeRouteRepository.save(route);

        TruckPositionUpdate positionUpdate = new TruckPositionUpdate(
                route.getVehicleId(),
                newPosition.getLatitude(),
                newPosition.getLongitude(),
                newProgress * 100.0
        );
        vehicleUpdatePublisher.publishTruckPosition(positionUpdate);
    }

    private void checkBinCollection(ActiveRoute route, RoutePoint currentPos) {
        List<BinStop> binStops = route.getBinStops();
        int currentBinIndex = route.getCurrentBinIndex();

        while (currentBinIndex < binStops.size() && "COLLECTED".equals(binStops.get(currentBinIndex).getStatus())) {
            currentBinIndex++;
            route.setCurrentBinIndex(currentBinIndex);
        }

        if (currentBinIndex >= binStops.size()) return;

        BinStop nextBin = binStops.get(currentBinIndex);

        if ("COLLECTING".equals(nextBin.getStatus())) {
            LocalDateTime collectionStart = nextBin.getCollectionTime();
            if (collectionStart != null) {
                long secondsSinceCollection = ChronoUnit.SECONDS.between(collectionStart, LocalDateTime.now());
                if (secondsSinceCollection >= 3) {
                    nextBin.setStatus("COLLECTED");
                    route.setCurrentBinIndex(route.getCurrentBinIndex() + 1);
                    activeRouteRepository.save(route);
                    logger.info("✅ Bin {} collected: {}/{}", nextBin.getBinId(), route.getBinsCollected() + 1, route.getTotalBins());
                }
            }
            return;
        }

        double distance = haversineDistance(currentPos.getLatitude(), currentPos.getLongitude(), nextBin.getLatitude(), nextBin.getLongitude());
        Double minDistance = nextBin.getMinDistanceReached();

        if (minDistance == null || distance < minDistance) {
            nextBin.setMinDistanceReached(distance);
            minDistance = distance;
        }

        double detectionRadius = 0.1;
        if (distance <= detectionRadius && !"COLLECTING".equals(nextBin.getStatus()) && !"COLLECTED".equals(nextBin.getStatus())) {
            startBinCollection(route, nextBin);
        }
    }

    private void startBinCollection(ActiveRoute route, BinStop binStop) {
        binStop.setStatus("COLLECTING");
        binStop.setCollectionTime(LocalDateTime.now());
        activeRouteRepository.save(route);

        Bin bin = binService.getBinById(binStop.getBinId());
        Vehicle vehicle = vehicleService.getVehicleById(route.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        double binFillLevel = bin.getFillLevel();
        double truckCapacityPerBin = 20.0;
        double truckFillIncrease = (binFillLevel / 100.0) * truckCapacityPerBin;
        double newFillLevel = Math.min(100.0, vehicle.getFillLevel() + truckFillIncrease);

        bin.setFillLevel(0);
        bin.setStatus("normal");
        binService.saveBin(bin);

        vehicle.setFillLevel(newFillLevel);
        vehicleService.saveVehicle(vehicle);

        route.setBinsCollected(route.getBinsCollected() + 1);
        activeRouteRepository.save(route);

        binUpdatePublisher.publishBinUpdate(bin);

        RouteProgressUpdate progressUpdate = new RouteProgressUpdate(
                route.getVehicleId(),
                route.getCurrentBinIndex() + 1,
                route.getTotalBins(),
                binStop.getBinId(),
                newFillLevel
        );
        vehicleUpdatePublisher.publishRouteProgress(progressUpdate);
    }

    public void completeRoute(ActiveRoute route) {
        activeVehiclesInfo.remove(route.getVehicleId());
        logger.info("🗑️ Removed vehicle {} from active tracking", route.getVehicleId());

        route.setStatus("COMPLETED");
        route.setEndTime(LocalDateTime.now());
        activeRouteRepository.save(route);
        vehicleService.completeRoute(route.getVehicleId());
        analyticsService.saveRouteToHistory(route);

        RouteCompletionEvent event = new RouteCompletionEvent(route.getVehicleId(), route.getBinsCollected());
        vehicleUpdatePublisher.publishRouteCompletion(event);
        logger.info("✅ Route complete: {} bins collected", route.getBinsCollected());
    }

    public ActiveRoute startRouteWithSpecificBins(String departmentId, String vehicleId, List<String> binIds) {
        Optional<ActiveRoute> existingOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (existingOpt.isPresent()) {
            ActiveRoute existing = existingOpt.get();
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                existing.setStatus("CANCELLED");
                activeRouteRepository.save(existing);
            }
        }

        List<RouteBin> routeBins = new ArrayList<>();
        for (String binId : binIds) {
            Bin bin = binService.getBinById(binId);
            routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
        }

        List<RoutePoint> fullPolyline = buildCompletePolyline(routeBins, departmentId);

        List<BinStop> binStops = new ArrayList<>();
        for (int i = 0; i < routeBins.size(); i++) {
            RouteBin rb = routeBins.get(i);
            Bin bin = binService.getBinById(rb.getId());
            BinStop stop = new BinStop(rb.getId(), rb.getLatitude(), rb.getLongitude(), i + 1);
            stop.setBinFillLevelBefore(bin.getFillLevel());
            binStops.add(stop);
        }

        ActiveRoute route = new ActiveRoute();
        route.setVehicleId(vehicleId);
        route.setDepartmentId(departmentId);
        route.setFullRoutePolyline(fullPolyline);
        route.setBinStops(binStops);
        route.setCurrentPosition(fullPolyline.get(0));
        route.setAnimationProgress(0.0);
        route.setCurrentBinIndex(0);
        route.setStatus("IN_PROGRESS");
        route.setStartTime(LocalDateTime.now());
        route.setLastUpdateTime(LocalDateTime.now());
        route.setTotalBins(binStops.size());
        route.setBinsCollected(0);
        route.setTotalDistanceKm(calculateTotalDistance(fullPolyline));

        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));
        vehicle.setAvailable(false);
        vehicleService.saveVehicle(vehicle);

        ActiveRoute savedRoute = activeRouteRepository.save(route);

        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        Map<String, Object> vehicleInfo = new HashMap<>();
        vehicleInfo.put("vehicleId", vehicleId);
        vehicleInfo.put("reference", vehicle.getReference());
        vehicleInfo.put("fillLevel", 0.0);
        vehicleInfo.put("latitude", department.getLatitude());
        vehicleInfo.put("longitude", department.getLongitude());
        vehicleInfo.put("activeRouteId", savedRoute.getId());

        activeVehiclesInfo.put(vehicleId, vehicleInfo);

        logger.info("✅ Registered active vehicle: {} with route ID: {}", vehicleId, savedRoute.getId());
        logger.info("🚀 Route started: {} bins for vehicle {}", binStops.size(), vehicleId);

        return savedRoute;
    }

    public ActiveRoute getActiveRouteByVehicle(String vehicleId) {
        return activeRouteRepository.findByVehicleId(vehicleId)
                .filter(r -> "IN_PROGRESS".equals(r.getStatus()))
                .orElse(null);
    }

    private static class Location {
        private final double latitude;
        private final double longitude;

        public Location(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}