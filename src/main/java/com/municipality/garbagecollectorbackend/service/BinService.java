package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BinService {

    @Autowired
    public BinRepository binRepository;

    public List<Bin> getAllBins() {
        return binRepository.findAll();
    }

    public Bin getBinById(String id) {
        return binRepository.findById(id).orElse(null);
    }

    public Bin saveBin(Bin bin) {
        return binRepository.save(bin);
    }

    public void deleteBin(String id) {
        binRepository.deleteById(id);
    }

    public Bin updateBin(String id, Bin updatedBin) {
        return binRepository.findById(id)
                .map(existingBin -> {

                    existingBin.setLatitude(updatedBin.getLatitude());
                    existingBin.setLongitude(updatedBin.getLongitude());
                    existingBin.setFillLevel(updatedBin.getFillLevel());
                    existingBin.setStatus(updatedBin.getStatus());
                    existingBin.setLastUpdated(LocalDateTime.now());

                    return binRepository.save(existingBin);
                })
                .orElse(null);
    }



}
