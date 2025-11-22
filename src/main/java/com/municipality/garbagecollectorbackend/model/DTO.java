package com.municipality.garbagecollectorbackend.model;

public class DTO {
    public record BinDTO(String id, double latitude, double longitude) {
        public Object getId() {
            return id;
        }
    }
}
