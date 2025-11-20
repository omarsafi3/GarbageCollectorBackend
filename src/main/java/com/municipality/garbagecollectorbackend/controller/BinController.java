package com.municipality.garbagecollectorbackend.controller;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.service.BinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bins")
public class BinController {

    @Autowired
    private BinService binService;

    // GET /bins → get all bins
    @GetMapping
    public List<Bin> getAllBins() {
        return binService.getAllBins();
    }

    // GET /bins/{id} → get a single bin by ID
    @GetMapping("/{id}")
    public Bin getBinById(@PathVariable String id) {
        return binService.getBinById(id);
    }

    // POST /bins → create a new bin
    @PostMapping
    public Bin createBin(@RequestBody Bin bin) {
        return binService.saveBin(bin);
    }

    // PUT /bins/{id} → update bin
    @PutMapping("/{id}")
    public Bin updateBin(@PathVariable String id, @RequestBody Bin bin) {
        return binService.updateBin(id, bin);
    }

    // DELETE /bins/{id} → delete bin
    @DeleteMapping("/{id}")
    public void deleteBin(@PathVariable String id) {
        binService.deleteBin(id);
    }
}
