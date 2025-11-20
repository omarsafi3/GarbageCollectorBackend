package com.municipality.garbagecollectorbackend.repository;

import com.municipality.garbagecollectorbackend.model.Bin;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BinRepository extends MongoRepository<Bin, String> {
}
