package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.DTO.*;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.ActiveRouteRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RouteExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RouteExecutionService.class);
    private static final double DEPOT_LAT = 34.0;
    private static final double DEPOT_LNG = 9.0;
    @Autowired
    private AnalyticsService analyticsService;  // ✅ ADD THIS
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

    /**
     * Start a route with FULL polyline and GPS tracking
     */
    public ActiveRoute startRouteWithFullPath(String departmentId, String vehicleId) {
        Optional<ActiveRoute> existingOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (existingOpt.isPresent()) {
            ActiveRoute existing = existingOpt.get();
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                logger.warn("⚠️ Vehicle {} already has active route. Cancelling it.", vehicleId);
                existing.setStatus("CANCELLED");
                activeRouteRepository.save(existing);
            }
        }

        List<RouteBin> routeBins = routeService.getOptimizedRoute(departmentId, vehicleId);
        List<RoutePoint> fullPolyline = buildCompletePolyline(routeBins);

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

        logger.info("🚀 Started route for vehicle {}: {} bins, {} points, {:.2f} km",
                vehicleId, binStops.size(), fullPolyline.size(), route.getTotalDistanceKm());
        logger.info("🗺️ Route created with {} bin stops:", binStops.size());
        for (BinStop stop : binStops) {
            logger.info("  - Bin {} at ({}, {}) - Fill: {}%",
                    stop.getBinId(),
                    stop.getLatitude(),
                    stop.getLongitude(),
                    stop.getBinFillLevelBefore());
        }

        return savedRoute;
    }

    /**
     * Build complete polyline from depot → bins → depot
     */
    private List<RoutePoint> buildCompletePolyline(List<RouteBin> routeBins) {
        List<RoutePoint> polyline = new ArrayList<>();
        int sequenceNumber = 0;

        List<Location> stops = new ArrayList<>();
        stops.add(new Location(DEPOT_LAT, DEPOT_LNG));
        routeBins.forEach(rb -> stops.add(new Location(rb.getLatitude(), rb.getLongitude())));
        stops.add(new Location(DEPOT_LAT, DEPOT_LNG));

        for (int i = 0; i < stops.size() - 1; i++) {
            Location from = stops.get(i);
            Location to = stops.get(i + 1);

            List<RoutePoint> segment = fetchOSRMPolyline(from, to, sequenceNumber);

            if (i == 0) {
                polyline.addAll(segment);
            } else {
                polyline.addAll(segment.subList(1, segment.size()));
            }

            sequenceNumber += segment.size();
        }

        return polyline;
    }

    /**
     * Fetch road polyline from OSRM
     */
    private List<RoutePoint> fetchOSRMPolyline(Location from, Location to, int startSequence) {
        List<RoutePoint> points = new ArrayList<>();

        try {
            String url = String.format(
                    "https://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
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

                logger.info("✅ OSRM returned {} points", points.size());
            } else {
                points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
                points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
            }

        } catch (Exception e) {
            logger.error("❌ Failed to fetch OSRM: {}", e.getMessage());
            points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
            points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
        }

        return points;
    }

    /**
     * Calculate total distance
     */
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

    /**
     * Haversine distance calculation
     */
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

    /**
     * Update truck positions every 250ms
     */
    @Scheduled(fixedRate = 250)
    public void updateActiveTruckPositions() {
        List<ActiveRoute> activeRoutes = activeRouteRepository.findByStatus("IN_PROGRESS");

        for (ActiveRoute route : activeRoutes) {
            try {
                updateTruckPosition(route);
            } catch (Exception e) {
                logger.error("❌ Error: {}", e.getMessage());
            }
        }
    }

    /**
     * Update truck position along polyline
     */
    private void updateTruckPosition(ActiveRoute route) {
        double currentProgress = route.getAnimationProgress();
        List<RoutePoint> polyline = route.getFullRoutePolyline();

        double progressIncrement = 0.0025;
        double newProgress = Math.min(1.0, currentProgress + progressIncrement);

        int targetIndex = (int) (newProgress * (polyline.size() - 1));
        RoutePoint newPosition = polyline.get(targetIndex);

        route.setAnimationProgress(newProgress);
        route.setCurrentPosition(newPosition);
        route.setLastUpdateTime(LocalDateTime.now());

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

    /**
     * ✅ IMPROVED: Better bin collection detection with "has passed" logic
     */
    /**
     * ✅ SMART DETECTION: Uses closest approach instead of "has passed"
     */
    private void checkBinCollection(ActiveRoute route, RoutePoint currentPos) {
        List<BinStop> binStops = route.getBinStops();
        int currentBinIndex = route.getCurrentBinIndex();

        if (currentBinIndex >= binStops.size()) {
            return;
        }

        BinStop nextBin = binStops.get(currentBinIndex);

        // ✅ Check if currently paused at a bin
        if ("COLLECTING".equals(nextBin.getStatus())) {
            LocalDateTime collectionStart = nextBin.getCollectionTime();
            if (collectionStart != null) {
                long secondsSinceCollection = ChronoUnit.SECONDS.between(collectionStart, LocalDateTime.now());

                if (secondsSinceCollection >= 3) {
                    nextBin.setStatus("COLLECTED");
                    logger.info("✅ Bin {} collected: {}/{}",
                            nextBin.getBinId(),
                            route.getBinsCollected(),
                            route.getTotalBins());
                    route.setCurrentBinIndex(route.getCurrentBinIndex() + 1);
                    activeRouteRepository.save(route);
                }
                return;
            }
        }

        double distance = haversineDistance(
                currentPos.getLatitude(),
                currentPos.getLongitude(),
                nextBin.getLatitude(),
                nextBin.getLongitude()
        );

        // ✅ STORE MINIMUM DISTANCE (track closest approach)
        Double minDistance = nextBin.getMinDistanceReached();
        if (minDistance == null || distance < minDistance) {
            nextBin.setMinDistanceReached(distance);
            minDistance = distance;
        }

        // ✅ DETECTION: Within 2km AND getting closer
        double detectionRadius = 2.0; // 2 km

        if (distance <= detectionRadius &&
                !"COLLECTING".equals(nextBin.getStatus()) &&
                !"COLLECTED".equals(nextBin.getStatus())) {

            logger.info("🎯 Truck at bin {} - Distance: {:.0f}m - COLLECTING!",
                    nextBin.getBinId(),
                    Math.round(distance * 1000));
            startBinCollection(route, nextBin);
        }

        // ✅ SAFETY: If truck is moving away after getting close (missed collection)
        if (minDistance != null && minDistance <= 2.5 && distance > minDistance + 0.5 &&
                !"COLLECTING".equals(nextBin.getStatus()) &&
                !"COLLECTED".equals(nextBin.getStatus())) {

            logger.warn("⚠️ Truck moving away from bin {} after getting within {:.0f}m - Force collecting!",
                    nextBin.getBinId(), Math.round(minDistance * 1000));
            startBinCollection(route, nextBin);
        }
    }


    private void startBinCollection(ActiveRoute route, BinStop binStop) {
        // Mark bin as COLLECTING (pause state)
        binStop.setStatus("COLLECTING");
        binStop.setCollectionTime(LocalDateTime.now());
        activeRouteRepository.save(route);

        // Get bin and vehicle
        Bin bin = binService.getBinById(binStop.getBinId());
        Vehicle vehicle = vehicleService.getVehicleById(route.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // ✅ CORRECT CALCULATION: Truck capacity = 5 bins (500%)
        double binFillLevel = bin.getFillLevel();
        double truckCapacityPerBin = 20.0; // 100% / 5 bins = 20% per full bin
        double truckFillIncrease = (binFillLevel / 100.0) * truckCapacityPerBin;
        double newFillLevel = Math.min(100.0, vehicle.getFillLevel() + truckFillIncrease);

        logger.info("📊 Bin {}% → Truck gains {:.2f}% (new total: {:.2f}%)",
                binFillLevel, truckFillIncrease, newFillLevel);

        // Update bin (empty it)
        bin.setFillLevel(0);
        bin.setStatus("normal");
        binService.saveBin(bin);

        // Update vehicle fill level
        vehicle.setFillLevel(newFillLevel);
        vehicleService.saveVehicle(vehicle);

        // Update route stats
        route.setBinsCollected(route.getBinsCollected() + 1);
        activeRouteRepository.save(route);

        // Send WebSocket updates
        binUpdatePublisher.publishBinUpdate(bin);

        RouteProgressUpdate progressUpdate = new RouteProgressUpdate(
                route.getVehicleId(),
                route.getCurrentBinIndex() + 1,
                route.getTotalBins(),
                binStop.getBinId(),
                newFillLevel
        );
        vehicleUpdatePublisher.publishRouteProgress(progressUpdate);

        logger.info("🛑 Truck paused at bin {} for 3 seconds... (Truck now at {:.2f}%)",
                binStop.getBinId(), newFillLevel);
    }

    /**
     * Complete a route
     */
    private void completeRoute(ActiveRoute route) {
        route.setStatus("COMPLETED");
        route.setEndTime(LocalDateTime.now());
        activeRouteRepository.save(route);

        vehicleService.completeRoute(route.getVehicleId());

        // ✅ NEW: Save to route history
        analyticsService.saveRouteToHistory(route);

        RouteCompletionEvent event = new RouteCompletionEvent(
                route.getVehicleId(),
                route.getBinsCollected()
        );
        vehicleUpdatePublisher.publishRouteCompletion(event);

        logger.info("✅ Route complete: {} bins collected", route.getBinsCollected());
    }

    /**
     * Start route with specific bins
     */
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

        List<RoutePoint> fullPolyline = buildCompletePolyline(routeBins);

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
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));
        vehicle.setAvailable(false);
        vehicleService.saveVehicle(vehicle);

        ActiveRoute savedRoute = activeRouteRepository.save(route);

        logger.info("🚀 Route started: {} bins for vehicle {}", binStops.size(), vehicleId);

        return savedRoute;
    }

    /**
     * Get active route
     */
    public ActiveRoute getActiveRouteByVehicle(String vehicleId) {
        return activeRouteRepository.findByVehicleId(vehicleId)
                .filter(r -> "IN_PROGRESS".equals(r.getStatus()))
                .orElse(null);
    }
}
