package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.CitizenReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CitizenReportRepository extends MongoRepository<CitizenReport, String> {
    List<CitizenReport> findByDepartmentId(String departmentId);
    List<CitizenReport> findByBinId(String binId);
    List<CitizenReport> findByStatus(String status);
}
