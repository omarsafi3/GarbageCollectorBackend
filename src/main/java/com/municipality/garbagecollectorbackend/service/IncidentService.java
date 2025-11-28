package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import java.util.stream.Collectors;
import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.model.IncidentStatus;
import com.municipality.garbagecollectorbackend.model.IncidentType;
import com.municipality.garbagecollectorbackend.repository.IncidentRepository;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import com.municipality.garbagecollectorbackend.model.RoutePoint;
import com.municipality.garbagecollectorbackend.routing.RouteResponse;
import com.municipality.garbagecollectorbackend.model.ActiveRoute;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Service
@Slf4j
public class IncidentService {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentUpdatePublisher publisher;

    @Autowired
    private RouteOptimizationService routeOptimizationService;

    @Autowired
    private RouteExecutionService routeExecutionService;

    @Autowired
    private BinService binService;

    public Incident reportRoadBlock(double latitude, double longitude, double radiusKm, String description) {
        Incident incident = new Incident();
        incident.setType(IncidentType.ROAD_BLOCK);
        incident.setLatitude(latitude);
        incident.setLongitude(longitude);
        incident.setRadiusKm(radiusKm);
        incident.setDescription(description);
        incident.setStatus(IncidentStatus.ACTIVE);
        incident.setCreatedAt(LocalDateTime.now());

        Incident saved = incidentRepository.save(incident);
        log.info("🚨 Road block reported at ({}, {}) with radius {}km", latitude, longitude, radiusKm);

        publisher.publishIncidentUpdate(saved);
        rerouteAffectedVehicles(saved);

        return saved;
    }

    private void rerouteAffectedVehicles(Incident incident) {
        List<ActiveRoute> activeRoutes = routeExecutionService.getAllActiveRoutes();
        log.info("🔍 Checking {} active routes for incident impact", activeRoutes.size());

        for (ActiveRoute route : activeRoutes) {
            if (isRouteAffectedByIncident(route, incident)) {
                log.warn("⚠️ Route {} is affected by incident - triggering reroute", route.getVehicleId());
                rerouteVehicle(route, incident);
            }
        }
    }

    private boolean isRouteAffectedByIncident(ActiveRoute route, Incident incident) {
        List<RoutePoint> remainingRoute = route.getRemainingRoutePoints();
        if (remainingRoute == null || remainingRoute.isEmpty()) {
            return false;
        }

        for (RoutePoint point : remainingRoute) {
            double distance = calculateDistance(
                    point.getLatitude(), point.getLongitude(),
                    incident.getLatitude(), incident.getLongitude()
            );
            if (distance <= incident.getRadiusKm()) {
                log.info("📍 Waypoint at ({}, {}) is within incident radius ({}km away)",
                        point.getLatitude(), point.getLongitude(), distance);
                return true;
            }
        }
        return false;
    }

    private void rerouteVehicle(ActiveRoute route, Incident incident) {
        try {
            List<String> remainingBinIds = route.getRemainingBinIds();
            if (remainingBinIds == null || remainingBinIds.isEmpty()) {
                log.info("✅ No remaining bins - vehicle {} will return to department", route.getVehicleId());
                return;
            }

            log.info("🔄 Rerouting vehicle {} with {} remaining bins",
                    route.getVehicleId(), remainingBinIds.size());

            List<String> safeBinIds = filterSafeBins(remainingBinIds, incident);

            if (safeBinIds.isEmpty()) {
                log.warn("⚠️ All remaining bins are in incident area - completing route early");
                routeExecutionService.completeRoute(route.getVehicleId());
                return;
            }

            List<Incident> incidentsToAvoid = getActiveIncidents().stream()
                    .filter(i -> i.getType() == IncidentType.ROAD_BLOCK)
                    .filter(i -> i.getLatitude() != null && i.getLongitude() != null)
                    .collect(Collectors.toList());

            String vehicleId = route.getVehicleId();
            String departmentId = route.getDepartmentId();

            RoutePoint currentPosition;
            if (route.getRemainingRoutePoints() != null && !route.getRemainingRoutePoints().isEmpty()) {
                currentPosition = route.getRemainingRoutePoints().get(0);
            } else {
                String firstBinId = safeBinIds.get(0);
                Bin firstBin = binService.getBinById(firstBinId);
                currentPosition = new RoutePoint(firstBin.getLatitude(), firstBin.getLongitude(), 0);
            }

            double currentLat = currentPosition.getLatitude();
            double currentLng = currentPosition.getLongitude();

            RouteResponse newRoute = routeOptimizationService.generateRerouteWithAvoidance(
                    vehicleId,
                    currentLat,
                    currentLng,
                    safeBinIds,
                    departmentId,
                    incidentsToAvoid
            );

            routeExecutionService.updateRoute(vehicleId, newRoute);

            log.info("✅ Vehicle {} successfully rerouted", vehicleId);

        } catch (Exception e) {
            log.error("❌ Failed to reroute vehicle {}: {}", route.getVehicleId(), e.getMessage(), e);
        }
    }

    private List<String> filterSafeBins(List<String> binIds, Incident incident) {
        return binIds.stream()
                .filter(binId -> {
                    Bin bin = binService.getBinById(binId);
                    if (bin == null) return true;

                    double distance = calculateDistance(
                            bin.getLatitude(), bin.getLongitude(),
                            incident.getLatitude(), incident.getLongitude()
                    );
                    boolean isSafe = distance > incident.getRadiusKm();
                    if (!isSafe) {
                        log.info("🚫 Bin {} is within incident area ({}km away)", binId, distance);
                    }
                    return isSafe;
                })
                .collect(Collectors.toList());
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public Incident createIncident(Incident incident) {
        incident.setCreatedAt(LocalDateTime.now());
        incident.setStatus(IncidentStatus.ACTIVE);
        Incident saved = incidentRepository.save(incident);
        publisher.publishIncidentUpdate(saved);
        return saved;
    }

    public List<Incident> getAllIncidents() {
        return incidentRepository.findAll();
    }

    public Incident resolveIncident(String id) {
        return incidentRepository.findById(id)
                .map(i -> {
                    i.setStatus(IncidentStatus.RESOLVED);
                    i.setResolvedAt(LocalDateTime.now());
                    Incident updated = incidentRepository.save(i);
                    publisher.publishIncidentUpdate(updated);
                    return updated;
                })
                .orElse(null);
    }

    public boolean hasActiveOverflowIncidentForBin(String binId) {
        return incidentRepository.findByBin_IdAndStatus(binId, IncidentStatus.ACTIVE)
                .stream()
                .anyMatch(incident -> IncidentType.OVERFILL.equals(incident.getType()));
    }

    public List<Incident> getActiveIncidents() {
        return incidentRepository.findByStatus(IncidentStatus.ACTIVE);
    }
}
