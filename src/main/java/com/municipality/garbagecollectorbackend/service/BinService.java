package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BinService {

    @Autowired
    public BinRepository binRepository;

    @Autowired
    public DepartmentRepository departmentRepository;

    @Cacheable(value = "bins", key = "'all'")
    public List<Bin> getAllBins() {
        return binRepository.findAll();
    }

    @Cacheable(value = "bins", key = "#id")
    public Bin getBinById(String id) {
        return binRepository.findById(id).orElse(null);
    }

    @Cacheable(value = "binsByDepartment", key = "#departmentId")
    public List<Bin> getBinsByDepartmentId(String departmentId) {
        return binRepository.findByDepartmentId(departmentId);
    }

    @Caching(evict = {
        @CacheEvict(value = "bins", allEntries = true),
        @CacheEvict(value = "binsByDepartment", allEntries = true)
    })
    public Bin saveBin(Bin bin) {
        if (bin.getDepartment() != null && bin.getDepartment().getId() != null) {
            String depId = bin.getDepartment().getId();
            Department fullDep = departmentRepository.findById(depId)
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            bin.setDepartment(fullDep);
        }
        bin.setLastUpdated(LocalDateTime.now());
        if (bin.getStatus() == null) {
            bin.setStatus("active");
        }
        if (bin.getFillLevel() == 0) {
            bin.setFillLevel(0);
        }
        return binRepository.save(bin);
    }

    @Caching(evict = {
        @CacheEvict(value = "bins", allEntries = true),
        @CacheEvict(value = "binsByDepartment", allEntries = true)
    })
    public void deleteBin(String id) {
        binRepository.deleteById(id);
    }

    public List<Bin> getBinsByIds(List<String> binIds) {
        return binRepository.findAllById(binIds);
    }

    @Caching(evict = {
        @CacheEvict(value = "bins", allEntries = true),
        @CacheEvict(value = "binsByDepartment", allEntries = true)
    })
    public Bin updateBin(String id, Bin updatedBin) {
        return binRepository.findById(id)
                .map(existingBin -> {
                    existingBin.setLatitude(updatedBin.getLatitude());
                    existingBin.setLongitude(updatedBin.getLongitude());
                    existingBin.setFillLevel(updatedBin.getFillLevel());
                    existingBin.setStatus(updatedBin.getStatus());
                    existingBin.setLastUpdated(LocalDateTime.now());

                    if (updatedBin.getDepartment() != null && updatedBin.getDepartment().getId() != null) {
                        String depId = updatedBin.getDepartment().getId();
                        Department fullDep = departmentRepository.findById(depId)
                                .orElseThrow(() -> new RuntimeException("Department not found"));
                        existingBin.setDepartment(fullDep);
                    }

                    return binRepository.save(existingBin);
                })
                .orElse(null);
    }

    /**
     * Get all bins with fill level >= threshold (critical bins that need collection)
     * @param threshold the minimum fill level percentage (default: 70)
     * @return list of bins above the threshold
     */
    public List<Bin> getCriticalBins(int threshold) {
        return binRepository.findAll().stream()
                .filter(bin -> bin.getFillLevel() >= threshold)
                .toList();
    }

    /**
     * Get critical bins for a specific department
     * @param departmentId the department ID
     * @param threshold the minimum fill level percentage
     * @return list of critical bins in the department
     */
    public List<Bin> getCriticalBinsByDepartment(String departmentId, int threshold) {
        return binRepository.findByDepartmentId(departmentId).stream()
                .filter(bin -> bin.getFillLevel() >= threshold)
                .toList();
    }
}
