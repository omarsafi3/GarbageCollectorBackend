package com.municipality.garbagecollectorbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VehicleService vehicleService;

    @Autowired
    private ObjectMapper objectMapper;

    private Vehicle testVehicle;
    private Department testDepartment;

    @BeforeEach
    void setUp() {
        testDepartment = new Department("dep1", "Test Department", 36.8, 10.1);

        testVehicle = new Vehicle();
        testVehicle.setId("v1");
        testVehicle.setReference("TRUCK-001");
        testVehicle.setPlate("ABC-123");
        testVehicle.setFillLevel(0.0);
        testVehicle.setAvailable(true);
        testVehicle.setStatus(Vehicle.VehicleStatus.AVAILABLE);
        testVehicle.setDepartment(testDepartment);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAllVehicles() throws Exception {
        when(vehicleService.getAllVehicles()).thenReturn(List.of(testVehicle));

        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("v1"))
                .andExpect(jsonPath("$[0].reference").value("TRUCK-001"))
                .andExpect(jsonPath("$[0].plate").value("ABC-123"));

        verify(vehicleService).getAllVehicles();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetVehicleById_found() throws Exception {
        when(vehicleService.getVehicleById("v1")).thenReturn(Optional.of(testVehicle));

        mockMvc.perform(get("/api/vehicles/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("v1"))
                .andExpect(jsonPath("$.reference").value("TRUCK-001"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetVehicleById_notFound() throws Exception {
        when(vehicleService.getVehicleById("v999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/vehicles/v999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAvailableVehicles() throws Exception {
        when(vehicleService.getAvailableVehicles()).thenReturn(List.of(testVehicle));

        mockMvc.perform(get("/api/vehicles/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testCreateVehicle() throws Exception {
        when(vehicleService.saveVehicle(any(Vehicle.class))).thenReturn(testVehicle);

        mockMvc.perform(post("/api/vehicles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testVehicle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("v1"))
                .andExpect(jsonPath("$.reference").value("TRUCK-001"));

        verify(vehicleService).saveVehicle(any(Vehicle.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testUpdateVehicle_success() throws Exception {
        when(vehicleService.updateVehicle(eq("v1"), any(Vehicle.class))).thenReturn(testVehicle);

        mockMvc.perform(put("/api/vehicles/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testVehicle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("v1"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testDeleteVehicle() throws Exception {
        doNothing().when(vehicleService).deleteVehicle("v1");

        mockMvc.perform(delete("/api/vehicles/v1"))
                .andExpect(status().isNoContent());

        verify(vehicleService).deleteVehicle("v1");
    }

    @Test
    void testGetAllVehicles_unauthorized() throws Exception {
        mockMvc.perform(get("/api/vehicles"))
                .andExpect(status().isForbidden());
    }
}
