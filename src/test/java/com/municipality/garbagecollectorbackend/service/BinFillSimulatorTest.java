package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

class BinFillSimulatorTest {

    private BinService binService;
    private BinUpdatePublisher publisher;
    private BinFillSimulator simulator;

    @BeforeEach
    void setUp() {
        binService = mock(BinService.class);
        publisher = mock(BinUpdatePublisher.class);

        simulator = new BinFillSimulator();
        simulator.binService = binService;
        simulator.publisher = publisher;
    }

    @Test
    void testFillBins() {
        Bin bin = new Bin("1", 36.8, 10.1, 90, "normal",
                LocalDateTime.now(), LocalDateTime.now());

        when(binService.getAllBins()).thenReturn(List.of(bin));

        simulator.fillBins();

        // updateBin should be called
        verify(binService, times(1)).updateBin(eq(bin.getId()), any(Bin.class));

        // publishBinUpdate should be called
        verify(publisher, times(1)).publishBinUpdate(any(Bin.class));
    }
}