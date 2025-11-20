package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class BinFillSimulator {

    private final Random random = new Random();

    @Autowired
    public BinService binService;
    @Autowired
    public BinUpdatePublisher publisher;

    // Runs every 10 seconds
    @Scheduled(fixedRate = 10000)
    public void fillBins() {
        List<Bin> bins = binService.getAllBins();
        for (Bin bin : bins) {
            int newLevel = Math.min(100, bin.getFillLevel() + random.nextInt(6) + 1);
            bin.setFillLevel(newLevel);

            binService.updateBin(bin.getId(), bin);

            publisher.publishBinUpdate(bin);
        }
    }
}

