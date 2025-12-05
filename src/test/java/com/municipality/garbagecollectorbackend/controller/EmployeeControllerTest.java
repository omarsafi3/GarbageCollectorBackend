package com.municipality.garbagecollectorbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Employee;
import com.municipality.garbagecollectorbackend.model.EmployeeStatus;
import com.municipality.garbagecollectorbackend.service.EmployeeService;
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
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;

    @Autowired
    private ObjectMapper objectMapper;

    private Employee testDriver;
    private Employee testCollector;
    private Department testDepartment;

    @BeforeEach
    void setUp() {
        testDepartment = new Department("dep1", "Test Department", 36.8, 10.1);

        testDriver = new Employee();
        testDriver.setId("emp1");
        testDriver.setFirstName("John");
        testDriver.setLastName("Driver");
        testDriver.setRole(Employee.EmployeeRole.DRIVER);
        testDriver.setAvailable(true);
        testDriver.setStatus(EmployeeStatus.AVAILABLE);
        testDriver.setDepartment(testDepartment);

        testCollector = new Employee();
        testCollector.setId("emp2");
        testCollector.setFirstName("Jane");
        testCollector.setLastName("Collector");
        testCollector.setRole(Employee.EmployeeRole.COLLECTOR);
        testCollector.setAvailable(true);
        testCollector.setStatus(EmployeeStatus.AVAILABLE);
        testCollector.setDepartment(testDepartment);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAllEmployees() throws Exception {
        when(employeeService.getAllEmployees()).thenReturn(List.of(testDriver, testCollector));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("emp1"))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[1].id").value("emp2"));

        verify(employeeService).getAllEmployees();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetEmployeeById_found() throws Exception {
        when(employeeService.getEmployeeById("emp1")).thenReturn(Optional.of(testDriver));

        mockMvc.perform(get("/api/employees/emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("emp1"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetEmployeeById_notFound() throws Exception {
        when(employeeService.getEmployeeById("emp999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/employees/emp999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAvailableEmployees() throws Exception {
        when(employeeService.getAvailableEmployees()).thenReturn(List.of(testDriver, testCollector));

        mockMvc.perform(get("/api/employees/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetAvailableEmployeesByDepartment() throws Exception {
        when(employeeService.getAvailableEmployeesByDepartment("dep1"))
                .thenReturn(List.of(testDriver, testCollector));

        mockMvc.perform(get("/api/employees/available/department/dep1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testGetEmployeesByVehicle() throws Exception {
        testDriver.setAssignedVehicleId("v1");
        testCollector.setAssignedVehicleId("v1");
        
        when(employeeService.getEmployeesByVehicle("v1"))
                .thenReturn(List.of(testDriver, testCollector));

        mockMvc.perform(get("/api/employees/vehicle/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testAssignEmployeesToVehicle_success() throws Exception {
        when(employeeService.assignEmployeesToVehicle(eq("v1"), any()))
                .thenReturn(true);
        when(employeeService.getEmployeesByVehicle("v1"))
                .thenReturn(List.of(testDriver, testCollector));

        Map<String, List<String>> request = Map.of("employeeIds", List.of("emp1", "emp2"));

        mockMvc.perform(post("/api/employees/assign/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testAssignEmployeesToVehicle_invalidRequest() throws Exception {
        Map<String, List<String>> request = Map.of("employeeIds", List.of("emp1"));

        mockMvc.perform(post("/api/employees/assign/v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testReleaseEmployeesFromVehicle() throws Exception {
        doNothing().when(employeeService).releaseEmployeesFromVehicle("v1");

        mockMvc.perform(post("/api/employees/release/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(employeeService).releaseEmployeesFromVehicle("v1");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testCheckRequiredEmployees() throws Exception {
        when(employeeService.vehicleHasRequiredEmployees("v1")).thenReturn(true);
        when(employeeService.getEmployeesByVehicle("v1"))
                .thenReturn(List.of(testDriver, testCollector));

        mockMvc.perform(get("/api/employees/check-required/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("v1"))
                .andExpect(jsonPath("$.hasRequiredEmployees").value(true));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testCreateEmployee() throws Exception {
        when(employeeService.saveEmployee(any(Employee.class))).thenReturn(testDriver);

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDriver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("emp1"));

        verify(employeeService).saveEmployee(any(Employee.class));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testUpdateEmployee_success() throws Exception {
        when(employeeService.updateEmployee(eq("emp1"), any(Employee.class))).thenReturn(testDriver);

        mockMvc.perform(put("/api/employees/emp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDriver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("emp1"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testUpdateEmployee_notFound() throws Exception {
        when(employeeService.updateEmployee(eq("emp999"), any(Employee.class))).thenReturn(null);

        mockMvc.perform(put("/api/employees/emp999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDriver)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void testDeleteEmployee() throws Exception {
        doNothing().when(employeeService).deleteEmployee("emp1");

        mockMvc.perform(delete("/api/employees/emp1"))
                .andExpect(status().isNoContent());

        verify(employeeService).deleteEmployee("emp1");
    }

    @Test
    void testGetAllEmployees_unauthorized() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isForbidden());
    }
}
