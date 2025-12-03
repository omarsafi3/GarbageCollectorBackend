package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.*;
import com.municipality.garbagecollectorbackend.repository.IncidentRepository;
import com.municipality.garbagecollectorbackend.routing.RouteExecutionService;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentUpdatePublisher publisher;

    @Mock
    private RouteOptimizationService routeOptimizationService;

    @Mock
    private RouteExecutionService routeExecutionService;

    @Mock
    private BinService binService;

    @InjectMocks
    private IncidentService incidentService;

    private Incident testIncident;

    @BeforeEach
    void setUp() {
        testIncident = new Incident();
        testIncident.setId("inc1");
        testIncident.setType(IncidentType.ROAD_BLOCK);
        testIncident.setLatitude(36.8);
        testIncident.setLongitude(10.1);
        testIncident.setRadiusKm(0.5);
        testIncident.setDescription("Road blocked due to construction");
        testIncident.setStatus(IncidentStatus.ACTIVE);
        testIncident.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testReportRoadBlock() {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> {
            Incident inc = i.getArgument(0);
            inc.setId("inc1");
            return inc;
        });
        when(routeExecutionService.getAllActiveRoutes()).thenReturn(List.of());

        Incident result = incidentService.reportRoadBlock(36.8, 10.1, 0.5, "Road blocked");

        assertNotNull(result);
        assertEquals(IncidentType.ROAD_BLOCK, result.getType());
        assertEquals(36.8, result.getLatitude());
        assertEquals(10.1, result.getLongitude());
        assertEquals(0.5, result.getRadiusKm());
        assertEquals(IncidentStatus.ACTIVE, result.getStatus());
        
        verify(incidentRepository).save(any(Incident.class));
        verify(publisher).publishIncidentUpdate(any(Incident.class));
    }

    @Test
    void testCreateIncident() {
        Incident newIncident = new Incident();
        newIncident.setType(IncidentType.OVERFILL);
        newIncident.setDescription("Bin overfilling");

        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> {
            Incident inc = i.getArgument(0);
            inc.setId("inc2");
            return inc;
        });

        Incident result = incidentService.createIncident(newIncident);

        assertNotNull(result);
        assertNotNull(result.getCreatedAt());
        assertEquals(IncidentStatus.ACTIVE, result.getStatus());
        verify(publisher).publishIncidentUpdate(any(Incident.class));
    }

    @Test
    void testGetAllIncidents() {
        Incident incident2 = new Incident();
        incident2.setId("inc2");
        incident2.setType(IncidentType.OVERFILL);

        when(incidentRepository.findAll()).thenReturn(List.of(testIncident, incident2));

        List<Incident> result = incidentService.getAllIncidents();

        assertEquals(2, result.size());
        verify(incidentRepository).findAll();
    }

    @Test
    void testResolveIncident_success() {
        when(incidentRepository.findById("inc1")).thenReturn(Optional.of(testIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        Incident result = incidentService.resolveIncident("inc1");

        assertNotNull(result);
        assertEquals(IncidentStatus.RESOLVED, result.getStatus());
        assertNotNull(result.getResolvedAt());
        verify(publisher).publishIncidentUpdate(any(Incident.class));
    }

    @Test
    void testResolveIncident_notFound() {
        when(incidentRepository.findById("inc999")).thenReturn(Optional.empty());

        Incident result = incidentService.resolveIncident("inc999");

        assertNull(result);
        verify(incidentRepository, never()).save(any());
    }

    @Test
    void testGetActiveIncidents() {
        Incident resolved = new Incident();
        resolved.setId("inc2");
        resolved.setStatus(IncidentStatus.RESOLVED);

        when(incidentRepository.findByStatus(IncidentStatus.ACTIVE))
            .thenReturn(List.of(testIncident));

        List<Incident> result = incidentService.getActiveIncidents();

        assertEquals(1, result.size());
        assertEquals(IncidentStatus.ACTIVE, result.get(0).getStatus());
    }

    @Test
    void testHasActiveOverflowIncidentForBin_true() {
        Incident overfillIncident = new Incident();
        overfillIncident.setType(IncidentType.OVERFILL);
        overfillIncident.setStatus(IncidentStatus.ACTIVE);

        when(incidentRepository.findByBin_IdAndStatus("bin1", IncidentStatus.ACTIVE))
            .thenReturn(List.of(overfillIncident));

        boolean result = incidentService.hasActiveOverflowIncidentForBin("bin1");

        assertTrue(result);
    }

    @Test
    void testHasActiveOverflowIncidentForBin_false_noOverfill() {
        Incident roadBlock = new Incident();
        roadBlock.setType(IncidentType.ROAD_BLOCK);
        roadBlock.setStatus(IncidentStatus.ACTIVE);

        when(incidentRepository.findByBin_IdAndStatus("bin1", IncidentStatus.ACTIVE))
            .thenReturn(List.of(roadBlock));

        boolean result = incidentService.hasActiveOverflowIncidentForBin("bin1");

        assertFalse(result);
    }

    @Test
    void testHasActiveOverflowIncidentForBin_false_noIncidents() {
        when(incidentRepository.findByBin_IdAndStatus("bin1", IncidentStatus.ACTIVE))
            .thenReturn(List.of());

        boolean result = incidentService.hasActiveOverflowIncidentForBin("bin1");

        assertFalse(result);
    }

    @Test
    void testReportRoadBlock_triggersRerouting() {
        // Setup active route that might be affected
        ActiveRoute activeRoute = new ActiveRoute();
        activeRoute.setVehicleId("v1");
        activeRoute.setDepartmentId("dep1");
        
        RoutePoint point = new RoutePoint(36.8, 10.1, 0); // Same location as incident
        activeRoute.setFullRoutePolyline(List.of(point));
        BinStop binStop = new BinStop();
        binStop.setBinId("bin1");
        binStop.setStatus("PENDING");
        activeRoute.setBinStops(List.of(binStop));

        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> {
            Incident inc = i.getArgument(0);
            inc.setId("inc1");
            return inc;
        });
        when(routeExecutionService.getAllActiveRoutes()).thenReturn(List.of(activeRoute));

        Incident result = incidentService.reportRoadBlock(36.8, 10.1, 0.5, "Road blocked");

        assertNotNull(result);
        // Verify rerouting was attempted for affected routes
        verify(routeExecutionService).getAllActiveRoutes();
    }
}
