package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.DisposalSite;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisposalSiteRepository extends MongoRepository<DisposalSite, String> {
    List<DisposalSite> findByActiveTrue();
    List<DisposalSite> findByDepartmentIdOrDepartmentIdIsNull(String departmentId);
}
