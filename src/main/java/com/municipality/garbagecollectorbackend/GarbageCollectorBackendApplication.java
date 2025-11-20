package com.municipality.garbagecollectorbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GarbageCollectorBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GarbageCollectorBackendApplication.class, args);
    }

}
