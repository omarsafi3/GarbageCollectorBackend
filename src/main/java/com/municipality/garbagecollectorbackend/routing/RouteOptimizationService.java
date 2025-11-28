package com.municipality.garbagecollectorbackend.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Solutions;
import com.municipality.garbagecollectorbackend.DTO.RouteBin;
import com.municipality.garbagecollectorbackend.DTO.VehicleRouteResult;
import com.municipality.garbagecollectorbackend.DTO.PreGeneratedRoute;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.service.BinService;
import com.municipality.garbagecollectorbackend.service.DepartmentService;
import com.municipality.garbagecollectorbackend.service.IncidentService;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@Slf4j
public class RouteOptimizationService {

    @Value("${osrm.server.url}")
    private String osrmServerUrl;

    private static final Logger logger = LoggerFactory.getLogger(RouteOptimizationService.class);

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private BinService binService;

    @Autowired
    @Lazy
    private IncidentService incidentService;

    private final Map<String, PreGeneratedRoute> preGeneratedRoutes = new ConcurrentHashMap<>();

    private static final double EPS = 1e-6;
    private static final String DEFAULT_DEPARTMENT_ID = "6920266d0b737026e2496c54";

    public List<RouteBin> getOptimizedRoute(String departmentId, String vehicleId) {
        PreGeneratedRoute preGenRoute = preGeneratedRoutes.get(vehicleId);
        if (preGenRoute != null && !preGenRoute.isStale()) {
            log.info("ðŸ“¦ Using pre-generated route for vehicle {} (age: {} minutes)",
                    vehicleId, preGenRoute.getAgeInMinutes());
            return preGenRoute.getRouteBins();
        }

        log.warn("âš ï¸ No valid pre-generated route for vehicle {}, generating on-demand...", vehicleId);
        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new IllegalArgumentException("Vehicle not found: " + vehicleId));
        List<Vehicle> vehicles = List.of(vehicle);
        List<Bin> bins = binService.getAllBins().stream()
                .filter(b -> b.getFillLevel() >= 80)
                .collect(Collectors.toList());
        Set<String> overfillBinIds = bins.stream()
                .filter(b -> b.getFillLevel() > 100)
                .map(Bin::getId)
                .collect(Collectors.toSet());

        List<VehicleRouteResult> results = optimizeDepartmentRoutes(
                Optional.of(department),
                vehicles,
                bins,
                100.0,
                overfillBinIds
        );

        for (VehicleRouteResult result : results) {
            if (result.getVehicleId().equals(vehicleId)) {
                List<RouteBin> routeBins = new ArrayList<>();
                for (String binId : result.getOrderedBinIds()) {
                    Bin bin = binService.getBinById(binId);
                    if (bin != null) {
                        routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
                    }
                }
                return routeBins;
            }
        }

        return List.of();
    }

    // âœ… FIXED: generateRerouteWithAvoidance now includes department in polyline
    public RouteResponse generateRerouteWithAvoidance(
            String vehicleId,
            double currentLat,
            double currentLng,
            List<String> remainingBinIds,
            String departmentId,
            List<Incident> incidentsToAvoid
    ) {
        log.info("ðŸ”„ Generating reroute for vehicle {} from ({}, {}) with {} bins, avoiding {} incidents",
                vehicleId, currentLat, currentLng, remainingBinIds.size(), incidentsToAvoid.size());

        List<RouteBin> bins = remainingBinIds.stream()
                .map(binId -> {
                    Bin bin = binService.getBinById(binId);
                    return new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude());
                })
                .collect(Collectors.toList());

        if (bins.isEmpty()) {
            log.warn("âš ï¸ No valid bins for reroute");
            return new RouteResponse(vehicleId, new ArrayList<>(), new ArrayList<>(), 0.0, 0);
        }

        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));

        // âœ… FIXED: Build polyline WITH incident avoidance AND include department return
        List<RoutePoint> polyline = buildRouteAvoidingIncidentsWithDepartment(
                currentLat, currentLng, bins, incidentsToAvoid, department
        );

        log.info("âœ… Reroute polyline built: {} points (including return to department)", polyline.size());
        return new RouteResponse(vehicleId, bins, polyline, 0.0, bins.size());
    }

    // âœ… NEW: Build route that includes department as final destination with incident avoidance
    private List<RoutePoint> buildRouteAvoidingIncidentsWithDepartment(
            double startLat, double startLng,
            List<RouteBin> bins,
            List<Incident> incidents,
            Department department
    ) {
        List<RoutePoint> polyline = new ArrayList<>();
        try {
            double currentLat = startLat;
            double currentLng = startLng;

            // 1) Build path through all bins with incident avoidance
            for (RouteBin bin : bins) {
                List<String> waypoints = new ArrayList<>();
                waypoints.add(String.format(Locale.US, "%.6f,%.6f", currentLng, currentLat));

                // Check if this segment is blocked by incident
                boolean blocked = false;
                for (Incident incident : incidents) {
                    if (incident.getLatitude() == null || incident.getLongitude() == null) continue;
                    if (isIncidentBlockingSegment(currentLat, currentLng, bin.getLatitude(), bin.getLongitude(), incident)) {
                        blocked = true;
                        double bearing = calculateBearing(currentLat, currentLng, bin.getLatitude(), bin.getLongitude());
                        double detourBearing = (bearing + 90) % 360;
                        double safeDistanceKm = 0.15;
                        double[] detour = getOffsetCoordinates(incident.getLatitude(), incident.getLongitude(), safeDistanceKm, detourBearing);
                        waypoints.add(String.format(Locale.US, "%.6f,%.6f", detour[1], detour[0]));
                        log.info("ðŸš§ Adding detour waypoint for blocked segment to bin {}", bin.getId());
                        break;
                    }
                }

                waypoints.add(String.format(Locale.US, "%.6f,%.6f", bin.getLongitude(), bin.getLatitude()));

                String waypointsStr = String.join(";", waypoints);
                String url = String.format("%s/route/v1/driving/%s?overview=full&geometries=geojson", osrmServerUrl, waypointsStr);

                RestTemplate restTemplate = new RestTemplate();
                String response = restTemplate.getForObject(url, String.class);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response);

                if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                    JsonNode coordinates = root.path("routes").get(0).path("geometry").path("coordinates");
                    int seq = polyline.isEmpty() ? 0 : polyline.size();
                    boolean skipFirst = !polyline.isEmpty();
                    for (JsonNode coord : coordinates) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        if (skipFirst) {
                            skipFirst = false;
                            continue;
                        }
                        polyline.add(new RoutePoint(lat, lng, seq++));
                    }
                } else {
                    if (polyline.isEmpty()) polyline.add(new RoutePoint(currentLat, currentLng, 0));
                    polyline.add(new RoutePoint(bin.getLatitude(), bin.getLongitude(), polyline.size()));
                }

                currentLat = bin.getLatitude();
                currentLng = bin.getLongitude();
            }

            // âœ… CRITICAL FIX: 2) Add return-to-department segment with incident avoidance
            log.info("ðŸ”„ Adding return-to-department segment from ({}, {}) to ({}, {})",
                    currentLat, currentLng, department.getLatitude(), department.getLongitude());

            List<String> returnWaypoints = new ArrayList<>();
            returnWaypoints.add(String.format(Locale.US, "%.6f,%.6f", currentLng, currentLat));

            // Check if return path is blocked by incident
            for (Incident incident : incidents) {
                if (incident.getLatitude() == null || incident.getLongitude() == null) continue;
                if (isIncidentBlockingSegment(currentLat, currentLng,
                        department.getLatitude(), department.getLongitude(), incident)) {
                    double bearing = calculateBearing(currentLat, currentLng,
                            department.getLatitude(), department.getLongitude());
                    double detourBearing = (bearing + 90) % 360;
                    double safeDistanceKm = 0.15;
                    double[] detour = getOffsetCoordinates(incident.getLatitude(), incident.getLongitude(), safeDistanceKm, detourBearing);
                    returnWaypoints.add(String.format(Locale.US, "%.6f,%.6f", detour[1], detour[0]));
                    log.info("ðŸš§ Adding detour for return path to department");
                    break;
                }
            }

            returnWaypoints.add(String.format(Locale.US, "%.6f,%.6f",
                    department.getLongitude(), department.getLatitude()));

            String returnWaypointsStr = String.join(";", returnWaypoints);
            String returnUrl = String.format("%s/route/v1/driving/%s?overview=full&geometries=geojson", osrmServerUrl, returnWaypointsStr);

            RestTemplate restTemplate = new RestTemplate();
            String returnResponse = restTemplate.getForObject(returnUrl, String.class);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode returnRoot = mapper.readTree(returnResponse);

            if ("Ok".equalsIgnoreCase(returnRoot.path("code").asText())) {
                JsonNode returnCoordinates = returnRoot.path("routes").get(0).path("geometry").path("coordinates");
                int seq = polyline.size();
                boolean skipFirst = true;  // Skip first point (already in polyline)
                for (JsonNode coord : returnCoordinates) {
                    double lng = coord.get(0).asDouble();
                    double lat = coord.get(1).asDouble();
                    if (skipFirst) {
                        skipFirst = false;
                        continue;
                    }
                    polyline.add(new RoutePoint(lat, lng, seq++));
                }
                log.info("âœ… Return segment added: {} points", seq - polyline.size());
            } else {
                // Fallback: straight line to department
                polyline.add(new RoutePoint(department.getLatitude(),
                        department.getLongitude(), polyline.size()));
                log.warn("âš ï¸ Return path OSRM failed, using fallback");
            }

        } catch (Exception e) {
            log.error("âŒ Failed building incident-avoiding polyline with department: {}", e.getMessage(), e);
            polyline.clear();
            polyline.add(new RoutePoint(startLat, startLng, 0));
            int seq = 1;
            for (RouteBin b : bins) polyline.add(new RoutePoint(b.getLatitude(), b.getLongitude(), seq++));
            // Add department as fallback
            polyline.add(new RoutePoint(department.getLatitude(), department.getLongitude(), seq));
        }

        return polyline;
    }

    private boolean isIncidentBlockingSegment(double lat1, double lon1, double lat2, double lon2, Incident incident) {
        double[] closest = getClosestPointOnSegment(lat1, lon1, lat2, lon2, incident.getLatitude(), incident.getLongitude());
        double d = calculateDistanceMeters(incident.getLatitude(), incident.getLongitude(), closest[0], closest[1]);
        double effective = Math.min(incident.getRadiusKm(), 0.08) * 1000.0;
        return d <= effective;
    }

    private double[] getClosestPointOnSegment(double x1, double y1, double x2, double y2, double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) return new double[]{x1, y1};
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        return new double[]{x1 + t * dx, y1 + t * dy};
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double calculateBearing(double fromLat, double fromLon, double toLat, double toLon) {
        double dLon = Math.toRadians(toLon - fromLon);
        double y = Math.sin(dLon) * Math.cos(Math.toRadians(toLat));
        double x = Math.cos(Math.toRadians(fromLat)) * Math.sin(Math.toRadians(toLat))
                - Math.sin(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
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

    @Scheduled(fixedRate = 900000)
    public void autoGenerateRoutes() {
        log.info("ðŸ”„ Auto-generating optimized routes for all vehicles...");
        generateRoutesForDepartment(DEFAULT_DEPARTMENT_ID);
    }

    public void generateRoutesForDepartment(String departmentId) {
        log.info("ðŸ“ Generating shared route pool for department: {}", departmentId);
        try {
            Department department = departmentService.getDepartmentById(departmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found: " + departmentId));
            List<Vehicle> allVehicles = vehicleService.getAllVehicles();
            List<Vehicle> availableVehicles = allVehicles.stream()
                    .filter(v -> v.getDepartment() != null && v.getDepartment().getId().equals(departmentId))
                    .filter(Vehicle::getAvailable)
                    .collect(Collectors.toList());

            if (availableVehicles.isEmpty()) {
                log.warn("âš ï¸ No available vehicles for department {}", departmentId);
                return;
            }

            List<Bin> allBins = binService.getAllBins();
            List<Bin> prioritizedBins = allBins.stream()
                    .filter(b -> b.getFillLevel() >= 80)
                    .collect(Collectors.toList());

            if (prioritizedBins.isEmpty()) {
                log.info("â„¹ï¸ No bins â‰¥80% for department {}", departmentId);
                return;
            }

            Set<String> overfillBinIds = prioritizedBins.stream()
                    .filter(b -> b.getFillLevel() > 100)
                    .map(Bin::getId)
                    .collect(Collectors.toSet());

            log.info("ðŸ“Š Generating routes: {} vehicles, {} bins (including {} overfilled)",
                    availableVehicles.size(), prioritizedBins.size(), overfillBinIds.size());

            List<VehicleRouteResult> results = optimizeDepartmentRoutes(
                    Optional.of(department),
                    availableVehicles,
                    prioritizedBins,
                    100.0,
                    overfillBinIds
            );

            preGeneratedRoutes.entrySet().removeIf(entry ->
                    entry.getValue().getDepartmentId().equals(departmentId)
            );

            LocalDateTime now = LocalDateTime.now();
            int routeNumber = 1;
            for (VehicleRouteResult result : results) {
                List<RouteBin> routeBins = new ArrayList<>();
                for (String binId : result.getOrderedBinIds()) {
                    Bin bin = binService.getBinById(binId);
                    if (bin != null) {
                        routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
                    }
                }
                String routeId = departmentId + "-route-" + routeNumber++;
                List<Incident> activeIncidents = incidentService.getActiveIncidents().stream()
                        .filter(i -> i.getType() == IncidentType.ROAD_BLOCK)
                        .filter(i -> i.getLatitude() != null && i.getLongitude() != null)
                        .collect(Collectors.toList());

                List<RoutePoint> polyline = buildRoutePolyline(routeBins, department.getLatitude(), department.getLongitude(), activeIncidents);
                log.info("ðŸ“ Generated route with {} points, avoiding {} active incidents", polyline.size(), activeIncidents.size());

                PreGeneratedRoute preGenRoute = new PreGeneratedRoute(
                        routeId,
                        departmentId,
                        routeBins,
                        now,
                        routeBins.size(),
                        polyline
                );

                preGeneratedRoutes.put(routeId, preGenRoute);
                log.info("âœ… Generated route {}: {} bins", routeId, routeBins.size());
            }

            log.info("âœ… Successfully generated {} routes for department {}", results.size(), departmentId);

            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "ROUTES_GENERATED");
            notification.put("departmentId", departmentId);
            notification.put("routeCount", results.size());
            notification.put("timestamp", Instant.now().toString());
            messagingTemplate.convertAndSend("/topic/routes", notification);
            log.info("ðŸ“¡ Notified frontend: {} routes generated for department {}", results.size(), departmentId);
        } catch (Exception e) {
            log.error("âŒ Failed to generate routes for department {}", departmentId, e);
        }
    }

    public List<PreGeneratedRoute> getAvailableRoutes(String departmentId) {
        return preGeneratedRoutes.values().stream()
                .filter(route -> route.getDepartmentId().equals(departmentId))
                .filter(PreGeneratedRoute::isAvailable)
                .collect(Collectors.toList());
    }

    public PreGeneratedRoute assignRouteToVehicle(String routeId, String vehicleId) {
        PreGeneratedRoute route = preGeneratedRoutes.get(routeId);
        if (route == null) {
            throw new IllegalArgumentException("Route not found: " + routeId);
        }
        if (!route.isAvailable()) {
            throw new IllegalStateException("Route already assigned to vehicle: " + route.getAssignedVehicleId());
        }
        route.assignToVehicle(vehicleId);
        log.info("âœ… Assigned route {} to vehicle {}", routeId, vehicleId);
        return route;
    }

    public void releaseRoute(String routeId) {
        PreGeneratedRoute route = preGeneratedRoutes.get(routeId);
        if (route != null) {
            route.release();
            log.info("âœ… Released route {}", routeId);
        }
    }

    public PreGeneratedRoute getPreGeneratedRoute(String vehicleId) {
        return preGeneratedRoutes.get(vehicleId);
    }

    public List<PreGeneratedRoute> getAllPreGeneratedRoutes(String departmentId) {
        return preGeneratedRoutes.values().stream()
                .filter(route -> route.getDepartmentId().equals(departmentId))
                .collect(Collectors.toList());
    }

    public void clearPreGeneratedRoute(String vehicleId) {
        preGeneratedRoutes.remove(vehicleId);
        log.info("ðŸ—‘ï¸ Cleared pre-generated route for vehicle {}", vehicleId);
    }

    public List<VehicleRouteResult> optimizeDepartmentRoutes(
            Optional<Department> depot,
            List<Vehicle> vehicles,
            List<Bin> bins,
            double maxRangeKm,
            Set<String> overfillBinIds
    ) {
        if (vehicles == null || vehicles.isEmpty()) {
            throw new IllegalArgumentException("At least one vehicle is required");
        }
        if (overfillBinIds == null) {
            overfillBinIds = Collections.emptySet();
        }

        Department dept = depot.orElseThrow(
                () -> new IllegalArgumentException("Depot (Department) is required")
        );
        Location depotLocation = Location.newInstance(dept.getLatitude(), dept.getLongitude());

        log.debug("=== JSprit Optimization ===");
        log.debug("Department: {} @ lat={}, lon={}", dept.getId(), dept.getLatitude(), dept.getLongitude());
        log.debug("Vehicles count: {}", vehicles.size());
        log.debug("Candidate bins: {}", bins.size());
        log.debug("Overfill bin IDs: {}", overfillBinIds);

        List<VehicleImpl> jspritVehicles = new ArrayList<>();
        for (Vehicle v : vehicles) {
            int maxBinsForThisTruck = calculateVehicleCapacity(v.getFillLevel());
            VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder
                    .newInstance("truckType-" + v.getId())
                    .addCapacityDimension(0, maxBinsForThisTruck)
                    .build();
            VehicleImpl jspritVehicle = VehicleImpl.Builder.newInstance(v.getId())
                    .setType(vehicleType)
                    .setStartLocation(depotLocation)
                    .setEndLocation(depotLocation)
                    .build();
            jspritVehicles.add(jspritVehicle);
            log.debug("Vehicle {} fillLevel={}% -> capacity={} bins",
                    v.getId(), v.getFillLevel(), maxBinsForThisTruck);
        }

        List<Job> jobs = new ArrayList<>();
        for (Bin bin : bins) {
            Service job = Service.Builder.newInstance(bin.getId())
                    .setName(bin.getId())
                    .addSizeDimension(0, 1)
                    .setLocation(Location.newInstance(bin.getLatitude(), bin.getLongitude()))
                    .build();
            jobs.add(job);
        }

        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        jobs.forEach(vrpBuilder::addJob);
        jspritVehicles.forEach(vrpBuilder::addVehicle);
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);
        VehicleRoutingProblem problem = vrpBuilder.build();

        Jsprit.Builder algoBuilder = Jsprit.Builder.newInstance(problem);
        Set<String> overfillIdsFinal = overfillBinIds;
        algoBuilder.setObjectiveFunction(solution -> {
            double baseCost = solution.getCost();
            double penalty = 0.0;
            Collection<Job> unassigned = solution.getUnassignedJobs();
            if (!unassigned.isEmpty() && !overfillIdsFinal.isEmpty()) {
                for (Job unassignedJob : unassigned) {
                    if (overfillIdsFinal.contains(unassignedJob.getId())) {
                        penalty += 1_000.0;
                    }
                }
            }
            return baseCost + penalty;
        });

        VehicleRoutingAlgorithm algorithm = algoBuilder.buildAlgorithm();
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution best = Solutions.bestOf(solutions);

        log.debug("Best solution cost: {}", best.getCost());
        log.debug("Routes count: {}", best.getRoutes().size());
        log.debug("Unassigned jobs: {}", best.getUnassignedJobs().stream().map(Job::getId).collect(Collectors.toList()));

        List<VehicleRouteResult> results = new ArrayList<>();
        for (VehicleRoute vr : best.getRoutes()) {
            String vehicleId = vr.getVehicle().getId();
            List<String> routeBinIds = new ArrayList<>();
            for (TourActivity act : vr.getActivities()) {
                double actLat = act.getLocation().getCoordinate().getX();
                double actLon = act.getLocation().getCoordinate().getY();
                Bin matched = bins.stream()
                        .filter(b -> Math.abs(b.getLatitude() - actLat) < EPS
                                && Math.abs(b.getLongitude() - actLon) < EPS)
                        .findFirst()
                        .orElse(null);
                if (matched != null) {
                    routeBinIds.add(matched.getId());
                }
            }
            results.add(new VehicleRouteResult(vehicleId, routeBinIds));
            log.debug("Vehicle {} assigned {} bins", vehicleId, routeBinIds.size());
        }

        return results;
    }

    private int calculateVehicleCapacity(double fillLevel) {
        if (fillLevel <= 20) return 5;
        if (fillLevel <= 50) return 4;
        if (fillLevel <= 80) return 3;
        if (fillLevel <= 95) return 2;
        return 1;
    }

    private List<RoutePoint> buildRoutePolyline(List<RouteBin> routeBins,
                                                double depotLat,
                                                double depotLng,
                                                List<Incident> incidents) {
        List<RoutePoint> polyline = new ArrayList<>();
        int sequenceNumber = 0;
        List<com.municipality.garbagecollectorbackend.model.Location> stops = new ArrayList<>();
        stops.add(new com.municipality.garbagecollectorbackend.model.Location(depotLat, depotLng));
        routeBins.forEach(rb ->
                stops.add(new com.municipality.garbagecollectorbackend.model.Location(rb.getLatitude(), rb.getLongitude()))
        );
        stops.add(new com.municipality.garbagecollectorbackend.model.Location(depotLat, depotLng));

        for (int i = 0; i < stops.size() - 1; i++) {
            com.municipality.garbagecollectorbackend.model.Location from = stops.get(i);
            com.municipality.garbagecollectorbackend.model.Location to = stops.get(i + 1);
            boolean segmentBlocked = false;
            Incident blockingIncident = null;
            for (Incident incident : incidents) {
                if (incident.getLatitude() == null || incident.getLongitude() == null) {
                    continue;
                }
                double[] closestPoint = getClosestPointOnSegment(
                        from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude(),
                        incident.getLatitude(), incident.getLongitude()
                );
                double distance = calculateDistanceMeters(
                        incident.getLatitude(), incident.getLongitude(),
                        closestPoint[0], closestPoint[1]
                );
                double effectiveRadius = Math.min(incident.getRadiusKm(), 0.08) * 1000.0;
                if (distance <= effectiveRadius) {
                    segmentBlocked = true;
                    blockingIncident = incident;
                    log.info("ðŸš§ Initial route: Segment blocked by incident at ({}, {})",
                            incident.getLatitude(), incident.getLongitude());
                    break;
                }
            }

            List<RoutePoint> segment;
            if (segmentBlocked && blockingIncident != null) {
                double bearing = calculateBearing(
                        from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude()
                );
                double detourBearing = bearing + 90;
                double safeDistance = blockingIncident.getRadiusKm() * 3.5;
                double[] detourCoords = getOffsetCoordinates(
                        blockingIncident.getLatitude(),
                        blockingIncident.getLongitude(),
                        safeDistance,
                        detourBearing
                );
                com.municipality.garbagecollectorbackend.model.Location detourLocation =
                        new com.municipality.garbagecollectorbackend.model.Location(detourCoords[0], detourCoords[1]);
                List<RoutePoint> segmentToDetour = fetchOSRMSegment(from, detourLocation, sequenceNumber);
                List<RoutePoint> detourToDest = fetchOSRMSegment(detourLocation, to,
                        sequenceNumber + segmentToDetour.size());
                segment = new ArrayList<>(segmentToDetour);
                if (detourToDest.size() > 1) {
                    segment.addAll(detourToDest.subList(1, detourToDest.size()));
                }
                log.info("âœ… Initial route: Added detour waypoint at ({}, {})",
                        detourCoords[0], detourCoords[1]);
            } else {
                segment = fetchOSRMSegment(from, to, sequenceNumber);
            }

            if (i == 0) {
                polyline.addAll(segment);
            } else if (segment.size() > 1) {
                polyline.addAll(segment.subList(1, segment.size()));
            }

            sequenceNumber += segment.size();
        }

        log.info("âœ… Built polyline with {} points for route (avoided {} incidents)",
                polyline.size(), incidents.size());
        return polyline;
    }

    private List<RoutePoint> buildRoutePolyline(List<RouteBin> routeBins,
                                                double depotLat,
                                                double depotLng) {
        return buildRoutePolyline(routeBins, depotLat, depotLng, new ArrayList<>());
    }

    private List<RoutePoint> fetchOSRMSegment(
            com.municipality.garbagecollectorbackend.model.Location from,
            com.municipality.garbagecollectorbackend.model.Location to,
            int startSequence) {
        List<RoutePoint> points = new ArrayList<>();
        try {
            String url = String.format(
                    "%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    osrmServerUrl,
                    from.getLongitude(), from.getLatitude(),
                    to.getLongitude(), to.getLatitude()
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
                points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
                points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
            }
        } catch (Exception e) {
            log.error("âŒ Failed to fetch OSRM: {}", e.getMessage());
            points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
            points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
        }
        return points;
    }

    public void checkCriticalBinsAndGenerateRoutes() {
        log.info("ðŸ” Checking for critical bins across all departments...");
        try {
            List<Department> departments = departmentService.getAllDepartments();
            for (Department dept : departments) {
                long criticalCount = binService.getAllBins().stream()
                        .filter(bin -> bin.getFillLevel() > 80.0)
                        .count();
                if (criticalCount == 0) {
                    continue;
                }
                long availableVehicles = vehicleService.getAllVehicles().stream()
                        .filter(v -> v.getDepartment() != null &&
                                dept.getId().equals(v.getDepartment().getId()))
                        .filter(Vehicle::getAvailable)
                        .count();
                if (availableVehicles == 0) {
                    log.warn("âš ï¸ {} critical bins in {} but no available vehicles",
                            criticalCount, dept.getName());
                    continue;
                }
                log.info("ðŸš¨ Generating CRITICAL route for {} bins in {}", criticalCount, dept.getName());
                generateRoutesForDepartment(dept.getId());
            }
        } catch (Exception e) {
            log.error("âŒ Error checking critical bins: {}", e.getMessage());
        }
    }

    public void generateRoutesIfNeeded(String departmentId) {
        List<PreGeneratedRoute> existingRoutes = getAllPreGeneratedRoutes(departmentId);
        if (existingRoutes.isEmpty() ||
                existingRoutes.stream().allMatch(PreGeneratedRoute::isStale)) {
            log.info("ðŸ”„ Generating routes (stale or empty) for department {}", departmentId);
            generateRoutesForDepartment(departmentId);
        } else {
            log.info("â„¹ï¸ Fresh routes exist for department {}", departmentId);
        }
    }

    public void generateRouteForReturningVehicle(String vehicleId, String departmentId) {
        log.info("ðŸš› Vehicle {} returned - generating new route", vehicleId);
        try {
            Optional<Vehicle> vehicleOpt = vehicleService.getVehicleById(vehicleId);
            if (vehicleOpt.isEmpty() || !vehicleOpt.get().getAvailable()) {
                log.warn("âš ï¸ Vehicle {} not available", vehicleId);
                return;
            }

            List<Bin> criticalBins = binService.getAllBins().stream()
                    .filter(bin -> bin.getFillLevel() >= 70)
                    .collect(Collectors.toList());

            if (criticalBins.isEmpty()) {
                log.info("â„¹ï¸ No critical bins for vehicle {}", vehicleId);
                return;
            }

            log.info("ðŸ”„ Generating fresh route for returned vehicle {}", vehicleId);
            generateRoutesForDepartment(departmentId);
        } catch (Exception e) {
            log.error("âŒ Failed to generate route for returning vehicle {}: {}", vehicleId, e.getMessage());
        }
    }
}