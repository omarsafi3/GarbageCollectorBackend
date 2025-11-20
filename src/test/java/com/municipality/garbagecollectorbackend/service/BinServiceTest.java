package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BinServiceTest {

    private BinRepository binRepository;
    private BinService binService;

    @BeforeEach
    void setup() {
        binRepository = mock(BinRepository.class);
        binService = new BinService();
        binService.binRepository = binRepository;
    }


    @Test
    void testSaveBin() {
        Bin bin = new Bin();
        bin.setLatitude(36.8);
        bin.setLongitude(10.1);
        bin.setFillLevel(0);
        bin.setStatus("normal");
        bin.setLastEmptied(LocalDateTime.now());
        when(binRepository.save(bin)).thenReturn(bin);

        Bin saved = binService.saveBin(bin);

        assertNotNull(saved);
        verify(binRepository, times(1)).save(bin);
    }

    @Test
    void testGetBinById() {
        Bin bin = new Bin();
        bin.setLatitude(36.8);
        bin.setLongitude(10.1);
        bin.setFillLevel(0);
        bin.setStatus("normal");
        bin.setLastEmptied(LocalDateTime.now());
        when(binRepository.findById("123")).thenReturn(Optional.of(bin));

        Bin result = binService.getBinById("123");

        assertEquals(bin, result);
        verify(binRepository, times(1)).findById("123");
    }

    @Test
    void testDeleteBin() {
        binService.deleteBin("456");
        verify(binRepository, times(1)).deleteById("456");
    }

    @Test
    void testUpdateBin() {
        Bin existing = new Bin();
        existing.setId("999");
        existing.setLatitude(36.8);
        existing.setLongitude(10.1);
        existing.setFillLevel(10);
        existing.setStatus("normal");
        existing.setLastEmptied(LocalDateTime.now());
        existing.setLastUpdated(LocalDateTime.now());

        Bin update = new Bin();
        update.setLatitude(37.0);
        update.setLongitude(11.0);
        update.setFillLevel(80);
        update.setStatus("full");
        update.setLastEmptied(LocalDateTime.now());
        update.setLastUpdated(LocalDateTime.now());

        when(binRepository.findById("999")).thenReturn(Optional.of(existing));
        when(binRepository.save(existing)).thenReturn(existing);

        Bin updated = binService.updateBin("999", update);

        assertNotNull(updated);
        assertEquals(37.0, updated.getLatitude());
        assertEquals(11.0, updated.getLongitude());
        assertEquals(80, updated.getFillLevel());
        assertEquals("full", updated.getStatus());

        verify(binRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateBin_notFound() {
        when(binRepository.findById("000")).thenReturn(Optional.empty());

        Bin result = binService.updateBin("000", new Bin());

        assertNull(result);
        verify(binRepository, never()).save(any());
    }
}
