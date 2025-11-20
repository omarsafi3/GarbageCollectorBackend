package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Incident;
import com.municipality.garbagecollectorbackend.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentServiceTest {

    private IncidentRepository incidentRepository;
    private IncidentUpdatePublisher publisher;
    private IncidentService incidentService;
    private BinService binService;
    private BinFillSimulator simulator;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(IncidentRepository.class);
        publisher = mock(IncidentUpdatePublisher.class);
        incidentService = new IncidentService();
        incidentService.incidentRepository = incidentRepository;
        incidentService.publisher = publisher;
        simulator = new BinFillSimulator();
        simulator.binService = binService;
        simulator.incidentService = incidentService;
    }

    @Test
    void testCreateIncident() {
        Bin bin = new Bin();
        bin.setId("bin1");

        Incident incident = new Incident();
        incident.setType("overflow");
        incident.setStatus("active");
        incident.setBin(bin);
        incident.setCreatedAt(LocalDateTime.now());

        when(incidentRepository.save(incident)).thenReturn(incident);

        Incident saved = incidentService.createIncident(incident);

        assertEquals(incident, saved);
        verify(incidentRepository, times(1)).save(incident);
        verify(publisher, times(1)).publishIncidentUpdate(incident);
    }

    @Test
    void testGetActiveIncidents() {
        Incident inc1 = new Incident();
        inc1.setStatus("active");

        Incident inc2 = new Incident();
        inc2.setStatus("active");

        when(incidentRepository.findByStatus("active")).thenReturn(List.of(inc1, inc2));

        List<Incident> activeIncidents = incidentService.getActiveIncidents();

        assertEquals(2, activeIncidents.size());
        assertTrue(activeIncidents.contains(inc1));
        assertTrue(activeIncidents.contains(inc2));
    }

    @Test
    void testResolveIncident() {
        Incident incident = new Incident();
        incident.setId("inc1");
        incident.setStatus("active");

        when(incidentRepository.findById("inc1")).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any())).thenReturn(incident);

        Incident resolved = incidentService.resolveIncident("inc1");

        assertEquals("resolved", resolved.getStatus());
        verify(incidentRepository).save(incident);
    }
    @Test
    void testOverflowTriggersIncident() {
        // Mock dependencies
        BinService mockBinService = mock(BinService.class);
        BinUpdatePublisher mockBinPublisher = mock(BinUpdatePublisher.class);
        IncidentService mockIncidentService = mock(IncidentService.class);
        IncidentUpdatePublisher mockIncidentUpdatePublisher = mock(IncidentUpdatePublisher.class);

        // Create a bin that will trigger overflow
        Bin bin = new Bin();
        bin.setId("bin1");
        bin.setFillLevel(100);

        // Mock binService behavior
        when(mockBinService.getAllBins()).thenReturn(List.of(bin));
        when(mockBinService.updateBin(any(), any())).thenReturn(bin);
        when(mockIncidentService.hasActiveOverflowIncidentForBin(bin.getId())).thenReturn(false);

        // Setup simulator
        BinFillSimulator simulator = new BinFillSimulator();
        simulator.binService = mockBinService;
        simulator.publisher = mockBinPublisher;
        simulator.incidentService = mockIncidentService;
        simulator.incidentUpdatePublisher = mockIncidentUpdatePublisher; // <- inject this too

        // Run the method
        simulator.fillBins();

        // Verify that incident was created and both publishers were called
        verify(mockIncidentService, times(1)).createIncident(any());
        verify(mockIncidentUpdatePublisher, times(1)).publishIncidentUpdate(any());
        verify(mockBinPublisher, times(1)).publishBinUpdate(bin);
    }



}