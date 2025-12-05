package com.municipality.garbagecollectorbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.service.BinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class BinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BinService binService;

    @Autowired
    private ObjectMapper objectMapper;

    private Bin testBin;

    @BeforeEach
    void setUp() {
        testBin = new Bin();
        testBin.setId("bin1");
        testBin.setLatitude(36.8);
        testBin.setLongitude(10.1);
        testBin.setFillLevel(50);
        testBin.setStatus("active");
        testBin.setLastEmptied(LocalDateTime.now());
        testBin.setLastUpdated(LocalDateTime.now());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAllBins() throws Exception {
        when(binService.getAllBins()).thenReturn(List.of(testBin));

        mockMvc.perform(get("/bins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bin1"))
                .andExpect(jsonPath("$[0].fillLevel").value(50));

        verify(binService).getAllBins();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetBinById_found() throws Exception {
        when(binService.getBinById("bin1")).thenReturn(testBin);

        mockMvc.perform(get("/bins/bin1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bin1"))
                .andExpect(jsonPath("$.latitude").value(36.8))
                .andExpect(jsonPath("$.longitude").value(10.1));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetBinById_notFound() throws Exception {
        when(binService.getBinById("bin999")).thenReturn(null);

        mockMvc.perform(get("/bins/bin999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testCreateBin() throws Exception {
        when(binService.saveBin(any(Bin.class))).thenReturn(testBin);

        mockMvc.perform(post("/bins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testBin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bin1"));

        verify(binService).saveBin(any(Bin.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testUpdateBin_success() throws Exception {
        when(binService.updateBin(eq("bin1"), any(Bin.class))).thenReturn(testBin);

        mockMvc.perform(put("/bins/bin1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testBin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bin1"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testUpdateBin_notFound() throws Exception {
        when(binService.updateBin(eq("bin999"), any(Bin.class))).thenReturn(null);

        mockMvc.perform(put("/bins/bin999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testBin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testDeleteBin() throws Exception {
        doNothing().when(binService).deleteBin("bin1");

        mockMvc.perform(delete("/bins/bin1"))
                .andExpect(status().isNoContent());

        verify(binService).deleteBin("bin1");
    }

    @Test
    void testGetAllBins_unauthorized() throws Exception {
        mockMvc.perform(get("/bins"))
                .andExpect(status().isForbidden());
    }
}
