package com.municipality.garbagecollectorbackend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestMongoConnection implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        boolean dbExists = mongoTemplate.getDb().getName() != null;
        System.out.println("MongoDB connection successful. Database name: " + mongoTemplate.getDb().getName());
    }
}