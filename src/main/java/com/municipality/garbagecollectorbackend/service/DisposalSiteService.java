package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.DisposalSite;
import com.municipality.garbagecollectorbackend.repository.DisposalSiteRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisposalSiteService {

    private final DisposalSiteRepository disposalSiteRepository;

    @PostConstruct
    public void initDefaultDisposalSites() {
        if (disposalSiteRepository.count() == 0) {
            log.info("Seeding default disposal sites and landfills...");
            List<DisposalSite> defaults = List.of(
                    DisposalSite.builder()
                            .name("Borj Chakir Regional Landfill")
                            .type("REGIONAL_LANDFILL")
                            .latitude(36.7550)
                            .longitude(10.0950)
                            .capacityTons(5000.0)
                            .currentLoadTons(1240.0)
                            .departmentId(null) // Shared
                            .operatingHours("06:00 - 22:00")
                            .active(true)
                            .build(),
                    DisposalSite.builder()
                            .name("Ariana Green Waste Recycling Center")
                            .type("RECYCLING_CENTER")
                            .latitude(36.8720)
                            .longitude(10.1650)
                            .capacityTons(800.0)
                            .currentLoadTons(210.0)
                            .departmentId("6a89b335a6bff2d79ad7063d")
                            .operatingHours("07:00 - 19:00")
                            .active(true)
                            .build(),
                    DisposalSite.builder()
                            .name("Carthage-Marsa Coastal Transfer Station")
                            .type("TRANSFER_STATION")
                            .latitude(36.8700)
                            .longitude(10.3150)
                            .capacityTons(1200.0)
                            .currentLoadTons(350.0)
                            .departmentId("6a89b336a6bff2d79ad7064c")
                            .operatingHours("06:00 - 20:00")
                            .active(true)
                            .build()
            );
            disposalSiteRepository.saveAll(defaults);
        }
    }

    public List<DisposalSite> getAllDisposalSites() {
        return disposalSiteRepository.findByActiveTrue();
    }

    public List<DisposalSite> getDisposalSitesForDepartment(String departmentId) {
        return disposalSiteRepository.findByDepartmentIdOrDepartmentIdIsNull(departmentId);
    }

    public Optional<DisposalSite> getNearestDisposalSite(double lat, double lng) {
        return disposalSiteRepository.findByActiveTrue().stream()
                .min((a, b) -> {
                    double distA = Math.hypot(a.getLatitude() - lat, a.getLongitude() - lng);
                    double distB = Math.hypot(b.getLatitude() - lat, b.getLongitude() - lng);
                    return Double.compare(distA, distB);
                });
    }

    public DisposalSite saveDisposalSite(DisposalSite site) {
        return disposalSiteRepository.save(site);
    }

    public void deleteDisposalSite(String id) {
        disposalSiteRepository.deleteById(id);
    }
}
