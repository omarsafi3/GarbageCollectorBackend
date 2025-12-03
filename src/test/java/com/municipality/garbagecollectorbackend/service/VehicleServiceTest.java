package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.routing.RouteOptimizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private BinRepository binRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private RouteOptimizationService routeOptimizationService;

    @InjectMocks
    private VehicleService vehicleService;

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
    void testGetAllVehicles() {
        List<Vehicle> vehicles = List.of(testVehicle);
        when(vehicleRepository.findAll()).thenReturn(vehicles);

        List<Vehicle> result = vehicleService.getAllVehicles();

        assertEquals(1, result.size());
        assertEquals("TRUCK-001", result.get(0).getReference());
        verify(vehicleRepository).findAll();
    }

    @Test
    void testGetVehicleById_found() {
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));

        Optional<Vehicle> result = vehicleService.getVehicleById("v1");

        assertTrue(result.isPresent());
        assertEquals("TRUCK-001", result.get().getReference());
        verify(vehicleRepository).findById("v1");
    }

    @Test
    void testGetVehicleById_notFound() {
        when(vehicleRepository.findById("v999")).thenReturn(Optional.empty());

        Optional<Vehicle> result = vehicleService.getVehicleById("v999");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetAvailableVehicles() {
        Vehicle unavailableVehicle = new Vehicle();
        unavailableVehicle.setId("v2");
        unavailableVehicle.setStatus(Vehicle.VehicleStatus.IN_ROUTE);

        when(vehicleRepository.findAll()).thenReturn(List.of(testVehicle, unavailableVehicle));

        List<Vehicle> result = vehicleService.getAvailableVehicles();

        assertEquals(1, result.size());
        assertEquals("v1", result.get(0).getId());
    }

    @Test
    void testSaveVehicle_withDepartment() {
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(testVehicle);

        Vehicle result = vehicleService.saveVehicle(testVehicle);

        assertNotNull(result);
        assertEquals("TRUCK-001", result.getReference());
        verify(departmentRepository).findById("dep1");
        verify(vehicleRepository).save(testVehicle);
    }

    @Test
    void testSaveVehicle_departmentNotFound() {
        testVehicle.setDepartment(new Department("dep999", null, 0.0, 0.0));
        when(departmentRepository.findById("dep999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> vehicleService.saveVehicle(testVehicle));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testUpdateVehicle_success() {
        Vehicle updated = new Vehicle();
        updated.setReference("TRUCK-002");
        updated.setPlate("XYZ-789");
        updated.setFillLevel(50.0);
        updated.setAvailable(false);
        updated.setDepartment(testDepartment);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(departmentRepository.findById("dep1")).thenReturn(Optional.of(testDepartment));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.updateVehicle("v1", updated);

        assertEquals("TRUCK-002", result.getReference());
        assertEquals("XYZ-789", result.getPlate());
        assertEquals(50.0, result.getFillLevel());
        assertFalse(result.getAvailable());
    }

    @Test
    void testUpdateVehicle_notFound() {
        when(vehicleRepository.findById("v999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
            () -> vehicleService.updateVehicle("v999", new Vehicle()));
    }

    @Test
    void testDeleteVehicle() {
        vehicleService.deleteVehicle("v1");
        verify(vehicleRepository).deleteById("v1");
    }

    @Test
    void testEmptyBin_success() {
        Bin bin = new Bin();
        bin.setId("bin1");
        bin.setFillLevel(80);
        bin.setStatus("full");

        testVehicle.setFillLevel(30.0);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(binRepository.findById("bin1")).thenReturn(Optional.of(bin));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));
        when(binRepository.save(any(Bin.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.emptyBin("v1", "bin1");

        assertNotNull(result);
        assertTrue(result.getFillLevel() > 30.0); // Vehicle fill level increased
        verify(binRepository).save(any(Bin.class));
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), any(Map.class));
    }

    @Test
    void testEmptyBin_vehicleNotFound() {
        when(vehicleRepository.findById("v999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
            () -> vehicleService.emptyBin("v999", "bin1"));
    }

    @Test
    void testEmptyBin_binNotFound() {
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(binRepository.findById("bin999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
            () -> vehicleService.emptyBin("v1", "bin999"));
    }

    @Test
    void testStartRoute_success() {
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(employeeService.vehicleHasRequiredEmployees("v1")).thenReturn(true);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.startRoute("v1");

        assertEquals(Vehicle.VehicleStatus.IN_ROUTE, result.getStatus());
        assertFalse(result.getAvailable());
        verify(employeeService).markEmployeesInRoute("v1");
    }

    @Test
    void testStartRoute_vehicleNotAvailable() {
        testVehicle.setStatus(Vehicle.VehicleStatus.IN_ROUTE);
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));

        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> vehicleService.startRoute("v1"));

        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void testStartRoute_noEmployeesAssigned() {
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(employeeService.vehicleHasRequiredEmployees("v1")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, 
            () -> vehicleService.startRoute("v1"));

        assertTrue(ex.getMessage().contains("2 employees"));
    }

    @Test
    void testCompleteRoute_success() {
        testVehicle.setStatus(Vehicle.VehicleStatus.IN_ROUTE);
        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(testVehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(i -> i.getArgument(0));

        Vehicle result = vehicleService.completeRoute("v1");

        assertEquals(Vehicle.VehicleStatus.RETURNING, result.getStatus());
        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void testCompleteRoute_vehicleNotFound() {
        when(vehicleRepository.findById("v999")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
            () -> vehicleService.completeRoute("v999"));
    }
}
