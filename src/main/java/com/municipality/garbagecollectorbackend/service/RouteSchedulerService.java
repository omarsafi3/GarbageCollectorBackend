package com.municipality.garbagecollectorbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;

@Service
@Slf4j
@RequiredArgsConstructor
public class RouteSchedulerService {

    private final RouteOptimizationService routeOptimizationService;

    /**
     * ✅ Check critical bins every 5 minutes
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes = 300,000 ms
    public void checkCriticalBins() {
        log.info("⏰ Scheduled: Checking critical bins...");
        routeOptimizationService.checkCriticalBinsAndGenerateRoutes();
    }

    /**
     * ✅ Refresh stale routes every 30 minutes
     * (Keeps your existing 15-min logic if you want)
     */
    @Scheduled(fixedDelay = 1800000) // 30 minutes = 1,800,000 ms
    public void refreshStaleRoutes() {
        log.info("⏰ Scheduled: Checking for stale routes...");
        // This will call your existing scheduled generation if needed
    }
}
