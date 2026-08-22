package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.DisposalSite;
import com.municipality.garbagecollectorbackend.service.DisposalSiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Disposal Sites & Landfills", description = "Endpoints for managing landfill dump sites and eco-centers")
@RestController
@RequestMapping("/api/disposal-sites")
@RequiredArgsConstructor
public class DisposalSiteController {

    private final DisposalSiteService disposalSiteService;

    @Operation(summary = "Get all disposal sites", description = "Returns active landfills, recycling centers, and transfer stations")
    @GetMapping
    public List<DisposalSite> getAllDisposalSites() {
        return disposalSiteService.getAllDisposalSites();
    }

    @Operation(summary = "Get disposal sites by department", description = "Returns disposal facilities available to a department")
    @GetMapping("/department/{departmentId}")
    public List<DisposalSite> getDisposalSitesForDepartment(@PathVariable String departmentId) {
        return disposalSiteService.getDisposalSitesForDepartment(departmentId);
    }

    @Operation(summary = "Save disposal site", description = "Creates or updates a landfill/recycling center")
    @PostMapping
    public ResponseEntity<DisposalSite> saveDisposalSite(@RequestBody DisposalSite site) {
        return ResponseEntity.ok(disposalSiteService.saveDisposalSite(site));
    }
}
