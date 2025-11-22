package com.municipality.garbagecollectorbackend.routing;

import com.municipality.garbagecollectorbackend.DTO.*;
import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.ActiveRouteRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository; // ✅ ADD THIS
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional; // ✅ ADD THIS
import java.util.Set;


@Service
public class RouteExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RouteExecutionService.class);
    private static final double DEPOT_LAT = 34.0;
    private static final double DEPOT_LNG = 9.0;

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
    private DepartmentRepository departmentRepository; // ✅ ADD THIS

    @Autowired
    private DepartmentService departmentService; // ✅ ADD THIS (or use repository directly)
    /**
     * Start a route with FULL polyline and GPS tracking
     */
    public ActiveRoute startRouteWithFullPath(String departmentId, String vehicleId) {
        // ✅ Cancel any existing active routes for this vehicle
        Optional<ActiveRoute> existingOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (existingOpt.isPresent()) {
            ActiveRoute existing = existingOpt.get();
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                logger.warn("⚠️ Vehicle {} already has active route. Cancelling it.", vehicleId);
                existing.setStatus("CANCELLED");
                activeRouteRepository.save(existing);
            }
        }

        // Get optimized bin order
        List<RouteBin> routeBins = routeService.getOptimizedRoute(departmentId, vehicleId);

        // ✅ Build complete route polyline with OSRM
        List<RoutePoint> fullPolyline = buildCompletePolyline(routeBins);

        // ✅ Create bin stops with details
        List<BinStop> binStops = new ArrayList<>();
        for (int i = 0; i < routeBins.size(); i++) {
            RouteBin rb = routeBins.get(i);
            Bin bin = binService.getBinById(rb.getId());
            BinStop stop = new BinStop(rb.getId(), rb.getLatitude(), rb.getLongitude(), i + 1);
            stop.setBinFillLevelBefore(bin.getFillLevel());
            binStops.add(stop);
        }

        // Create ActiveRoute with full data
        ActiveRoute route = new ActiveRoute();
        route.setVehicleId(vehicleId);
        route.setDepartmentId(departmentId);
        route.setFullRoutePolyline(fullPolyline);
        route.setBinStops(binStops);
        route.setCurrentPosition(fullPolyline.get(0)); // Start at depot
        route.setAnimationProgress(0.0);
        route.setCurrentBinIndex(0);
        route.setStatus("IN_PROGRESS");
        route.setStartTime(LocalDateTime.now());
        route.setLastUpdateTime(LocalDateTime.now());
        route.setTotalBins(binStops.size());
        route.setBinsCollected(0);
        route.setTotalDistanceKm(calculateTotalDistance(fullPolyline));

        // Mark vehicle as unavailable
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));
        vehicle.setAvailable(false);
        vehicleService.saveVehicle(vehicle);

        ActiveRoute savedRoute = activeRouteRepository.save(route);

        logger.info("🚀 Started route for vehicle {}: {} bins, {} points, {:.2f} km",
                vehicleId, binStops.size(), fullPolyline.size(), route.getTotalDistanceKm());

        return savedRoute;
    }


    /**
     * Build complete polyline from depot → bins → depot using OSRM
     */
    private List<RoutePoint> buildCompletePolyline(List<RouteBin> routeBins) {
        List<RoutePoint> polyline = new ArrayList<>();
        int sequenceNumber = 0;

        // Build stops: depot → bins → depot
        List<Location> stops = new ArrayList<>();
        stops.add(new Location(DEPOT_LAT, DEPOT_LNG));
        routeBins.forEach(rb -> stops.add(new Location(rb.getLatitude(), rb.getLongitude())));
        stops.add(new Location(DEPOT_LAT, DEPOT_LNG));

        // Get road polyline between each stop
        for (int i = 0; i < stops.size() - 1; i++) {
            Location from = stops.get(i);
            Location to = stops.get(i + 1);

            List<RoutePoint> segment = fetchOSRMPolyline(from, to, sequenceNumber);

            if (i == 0) {
                polyline.addAll(segment); // First segment: add all points
            } else {
                polyline.addAll(segment.subList(1, segment.size())); // Skip duplicate first point
            }

            sequenceNumber += segment.size();
        }

        return polyline;
    }

    /**
     * Fetch road polyline from OSRM
     */
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

            // ✅ REAL OSRM API CALL
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);

            // Parse JSON response
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

                logger.info("✅ OSRM returned {} points from ({}, {}) to ({}, {})",
                        points.size(), from.getLatitude(), from.getLongitude(),
                        to.getLatitude(), to.getLongitude());
            } else {
                logger.warn("⚠️ OSRM returned code: {}", root.path("code").asText());
                // Fallback to straight line
                points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
                points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
            }

        } catch (Exception e) {
            logger.error("❌ Failed to fetch OSRM polyline: {}", e.getMessage());
            // Fallback: straight line
            points.add(new RoutePoint(from.getLatitude(), from.getLongitude(), startSequence));
            points.add(new RoutePoint(to.getLatitude(), to.getLongitude(), startSequence + 1));
        }

        return points;
    }


    /**
     * Calculate total distance from polyline
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
     * Haversine formula for distance between GPS coordinates
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Scheduled task: Update truck positions and collect bins
     */
    @Scheduled(fixedRate = 250) // Keep 250ms (smooth + light)
    public void updateActiveTruckPositions() {
        List<ActiveRoute> activeRoutes = activeRouteRepository.findByStatus("IN_PROGRESS");

        for (ActiveRoute route : activeRoutes) {
            try {
                updateTruckPosition(route);
            } catch (Exception e) {
                logger.error("❌ Error updating route {}: {}", route.getId(), e.getMessage());
            }
        }
    }


    /**
     * Update truck position along polyline
     */
    private void updateTruckPosition(ActiveRoute route) {
        double currentProgress = route.getAnimationProgress();
        List<RoutePoint> polyline = route.getFullRoutePolyline();

        // ✅ SLOWER: 0.0625% per 250ms = 400 seconds (6 minutes 40 seconds)
        double progressIncrement = 0.000625; // ✅ Changed for 5+ minute duration
        double newProgress = Math.min(1.0, currentProgress + progressIncrement);

        // Rest stays the same...
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






    public ActiveRoute startRouteWithSpecificBins(String departmentId, String vehicleId, List<String> binIds) {
        // ✅ Cancel any existing active routes for this vehicle
        Optional<ActiveRoute> existingOpt = activeRouteRepository.findByVehicleId(vehicleId);
        if (existingOpt.isPresent()) {
            ActiveRoute existing = existingOpt.get();
            if ("IN_PROGRESS".equals(existing.getStatus())) {
                logger.warn("⚠️ Vehicle {} already has active route. Cancelling it.", vehicleId);
                existing.setStatus("CANCELLED");
                activeRouteRepository.save(existing);
            }
        }

        // Get bin objects from IDs
        List<RouteBin> routeBins = new ArrayList<>();
        for (String binId : binIds) {
            Bin bin = binService.getBinById(binId);
            routeBins.add(new RouteBin(bin.getId(), bin.getLatitude(), bin.getLongitude()));
        }

        // ✅ Build complete route polyline with OSRM
        List<RoutePoint> fullPolyline = buildCompletePolyline(routeBins);

        // ✅ Create bin stops with details
        List<BinStop> binStops = new ArrayList<>();
        for (int i = 0; i < routeBins.size(); i++) {
            RouteBin rb = routeBins.get(i);
            Bin bin = binService.getBinById(rb.getId());
            BinStop stop = new BinStop(rb.getId(), rb.getLatitude(), rb.getLongitude(), i + 1);
            stop.setBinFillLevelBefore(bin.getFillLevel());
            binStops.add(stop);
        }

        // Create ActiveRoute with full data
        ActiveRoute route = new ActiveRoute();
        route.setVehicleId(vehicleId);
        route.setDepartmentId(departmentId);
        route.setFullRoutePolyline(fullPolyline);
        route.setBinStops(binStops);
        route.setCurrentPosition(fullPolyline.get(0)); // Start at depot
        route.setAnimationProgress(0.0);
        route.setCurrentBinIndex(0);
        route.setStatus("IN_PROGRESS");
        route.setStartTime(LocalDateTime.now());
        route.setLastUpdateTime(LocalDateTime.now());
        route.setTotalBins(binStops.size());
        route.setBinsCollected(0);
        route.setTotalDistanceKm(calculateTotalDistance(fullPolyline));

        // Mark vehicle as unavailable
        Vehicle vehicle = vehicleService.getVehicleById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));
        vehicle.setAvailable(false);
        vehicleService.saveVehicle(vehicle);

        ActiveRoute savedRoute = activeRouteRepository.save(route);

        logger.info("🚀 Started route for vehicle {}: {} bins, {} points, {:.2f} km",
                vehicleId, binStops.size(), fullPolyline.size(), route.getTotalDistanceKm());

        return savedRoute;
    }

    /**
     * Check if truck reached a bin
     */
    /**
     * ✅ Check if truck reached a bin (by GPS distance, not progress %)
     */
    private void checkBinCollection(ActiveRoute route, RoutePoint currentPos) {
        List<BinStop> binStops = route.getBinStops();
        int currentBinIndex = route.getCurrentBinIndex();

        if (currentBinIndex >= binStops.size()) {
            return; // All bins collected
        }

        BinStop nextBin = binStops.get(currentBinIndex);

        // ✅ FIX: Check by GPS distance (within 50 meters = collected)
        double distance = haversineDistance(
                currentPos.getLatitude(),
                currentPos.getLongitude(),
                nextBin.getLatitude(),
                nextBin.getLongitude()
        );

        // ✅ If within 50 meters, collect the bin IMMEDIATELY
        if (distance <= 0.05 && !"COLLECTED".equals(nextBin.getStatus())) { // 0.05 km = 50 meters
            logger.info("📍 Truck reached bin {} at distance {:.0f}m - COLLECTING NOW!",
                    nextBin.getBinId(), distance * 1000);
            collectBin(route, nextBin);
        }
    }


    /**
     * Collect a bin
     */
    private void collectBin(ActiveRoute route, BinStop binStop) {
        // Mark bin as collected
        binStop.setStatus("COLLECTED");
        binStop.setCollectionTime(LocalDateTime.now());

        // Update bin in database
        Bin bin = binService.getBinById(binStop.getBinId());
        bin.setFillLevel(0);
        bin.setStatus("normal");
        binService.saveBin(bin);

        // Update vehicle fill level
        Vehicle vehicle = vehicleService.getVehicleById(route.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + route.getVehicleId()));

        int totalBins = route.getTotalBins();
        double newFillLevel = Math.min(100.0, vehicle.getFillLevel() + (100.0 / totalBins));
        vehicle.setFillLevel(newFillLevel);
        vehicleService.saveVehicle(vehicle);

        // Update route progress
        route.setCurrentBinIndex(route.getCurrentBinIndex() + 1);
        route.setBinsCollected(route.getBinsCollected() + 1);
        activeRouteRepository.save(route);

        // Send WebSocket updates
        binUpdatePublisher.publishBinUpdate(bin);

        RouteProgressUpdate progressUpdate = new RouteProgressUpdate(
                route.getVehicleId(),
                route.getCurrentBinIndex(),
                totalBins,
                binStop.getBinId(),
                newFillLevel
        );
        vehicleUpdatePublisher.publishRouteProgress(progressUpdate);

        logger.info("📍 Vehicle {} collected bin {} ({}/{})",
                route.getVehicleId(), binStop.getBinId(),
                route.getBinsCollected(), totalBins);
    }

    /**
     * Complete a route
     */

    private void completeRoute(ActiveRoute route) {
        route.setStatus("COMPLETED");
        activeRouteRepository.save(route);

        Vehicle vehicle = vehicleService.getVehicleById(route.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + route.getVehicleId()));

        vehicle.setAvailable(true);
        vehicleService.saveVehicle(vehicle);

        RouteCompletionEvent event = new RouteCompletionEvent(
                route.getVehicleId(),
                route.getBinsCollected()
        );
        vehicleUpdatePublisher.publishRouteCompletion(event);

        logger.info("✅ Route completed for vehicle {}: {} bins, {:.2f} km",
                route.getVehicleId(), route.getBinsCollected(), route.getTotalDistanceKm());
    }

    /**
     * Get active route for vehicle (for dashboard to load on refresh)
     */
    public ActiveRoute getActiveRouteByVehicle(String vehicleId) {
        return activeRouteRepository.findByVehicleId(vehicleId)
                .filter(r -> "IN_PROGRESS".equals(r.getStatus()))
                .orElse(null);
    }
}
