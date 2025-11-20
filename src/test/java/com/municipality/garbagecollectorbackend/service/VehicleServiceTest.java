package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import com.municipality.garbagecollectorbackend.model.Department;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import com.municipality.garbagecollectorbackend.repository.BinRepository;
import com.municipality.garbagecollectorbackend.repository.VehicleRepository;
import com.municipality.garbagecollectorbackend.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleServiceTest {

    private VehicleRepository vehicleRepository;
    private DepartmentRepository departmentRepository;
    private BinRepository binRepository;
    private VehicleService vehicleService;

    @BeforeEach
    void setup() {
        vehicleRepository = mock(VehicleRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        binRepository = mock(BinRepository.class);

        vehicleService = new VehicleService();
        vehicleService.vehicleRepository = vehicleRepository;
        vehicleService.departmentRepository = departmentRepository;
        vehicleService.binRepository = binRepository;
    }

    @Test
    void testGetAllVehicles() {
        List<Vehicle> list = List.of(
                new Vehicle("1", "Ref1", "ABC-123", 20.0, true, null),
                new Vehicle("2", "Ref2", "XYZ-789", 50.0, false, null)
        );

        when(vehicleRepository.findAll()).thenReturn(list);

        List<Vehicle> result = vehicleService.getAllVehicles();

        assertEquals(2, result.size());
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void testGetVehicleById() {
        Vehicle v = new Vehicle("123", "Ref1", "CAR-111", 10.0, true, null);

        when(vehicleRepository.findById("123")).thenReturn(Optional.of(v));

        Optional<Vehicle> result = vehicleService.getVehicleById("123");

        assertTrue(result.isPresent());
        assertEquals(v, result.get());
        verify(vehicleRepository, times(1)).findById("123");
    }

    @Test
    void testGetAvailableVehicles() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("1", "Ref1", "CAR-111", 10.0, true, null),
                new Vehicle("2", "Ref2", "CAR-222", 60.0, false, null),
                new Vehicle("3", "Ref3", "CAR-333", 12.0, true, null)
        );

        when(vehicleRepository.findAll()).thenReturn(vehicles);

        List<Vehicle> available = vehicleService.getAvailableVehicles();

        assertEquals(2, available.size());
        assertTrue(available.stream().allMatch(Vehicle::getAvailable));
        verify(vehicleRepository, times(1)).findAll();
    }

    @Test
    void testSaveVehicle_withExistingDepartment() {
        Department dep = new Department("d1", "Route 1", 10.1, 36.8);
        Vehicle v = new Vehicle(null, "Ref1", "CAR-111", 10.0, true, dep);

        when(departmentRepository.existsById("d1")).thenReturn(true);
        when(vehicleRepository.save(v)).thenReturn(v);

        Vehicle saved = vehicleService.saveVehicle(v);

        assertNotNull(saved);
        verify(vehicleRepository, times(1)).save(v);
    }

    @Test
    void testSaveVehicle_withNonExistingDepartment() {
        Department dep = new Department("d999", "X", 0.0, 0.0);
        Vehicle v = new Vehicle(null, "Ref1", "CAR-111", 10.0, true, dep);

        when(departmentRepository.existsById("d999")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> vehicleService.saveVehicle(v));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testUpdateVehicle_withExistingDepartment() {
        Department dep = new Department("d1", "Route 1", 10.1, 36.8);

        Vehicle existing = new Vehicle("123", "OldRef", "OLD-000", 40.0, true, dep);
        Vehicle update = new Vehicle(null, "NewRef", "NEW-111", 15.0, false, dep);

        when(vehicleRepository.findById("123")).thenReturn(Optional.of(existing));
        when(departmentRepository.existsById("d1")).thenReturn(true);
        when(vehicleRepository.save(existing)).thenReturn(existing);

        Vehicle result = vehicleService.updateVehicle("123", update);

        assertNotNull(result);
        assertEquals("NewRef", result.getReference());
        assertEquals("NEW-111", result.getPlate());
        assertEquals(15.0, result.getFillLevel());
        assertFalse(result.getAvailable());
        assertEquals(dep, result.getDepartment());

        verify(vehicleRepository, times(1)).save(existing);
    }

    @Test
    void testUpdateVehicle_withNonExistingDepartment() {
        Department dep = new Department("d999", "X", 0.0, 0.0);
        Vehicle existing = new Vehicle("123", "OldRef", "OLD-000", 40.0, true, dep);
        Vehicle update = new Vehicle(null, "Ref", "Plate", 15.0, false, dep);

        when(vehicleRepository.findById("123")).thenReturn(Optional.of(existing));
        when(departmentRepository.existsById("d999")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> vehicleService.updateVehicle("123", update));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testUpdateVehicle_notFound() {
        when(vehicleRepository.findById("999")).thenReturn(Optional.empty());

        Vehicle result = vehicleService.updateVehicle("999", new Vehicle());

        assertNull(result);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void testDeleteVehicle() {
        vehicleService.deleteVehicle("456");
        verify(vehicleRepository, times(1)).deleteById("456");
    }

    @Test
    void testEmptyBin_fullEmptying() {
        Vehicle vehicle = new Vehicle("v1", "Ref1", "ABC123", 0.0, true, new Department());
        Bin bin = new Bin("b1", 10.0, 20.0, 50, "FULL", null, null);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(vehicle));
        when(binRepository.findById("b1")).thenReturn(Optional.of(bin));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(binRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Vehicle updatedVehicle = vehicleService.emptyBin("v1", "b1");

        assertNotNull(updatedVehicle);
        assertEquals(5, updatedVehicle.getFillLevel());
        assertEquals(0, bin.getFillLevel());
        assertEquals("active", bin.getStatus());
    }

    @Test
    void testEmptyBin_partialEmptying() {
        Vehicle vehicle = new Vehicle("v1", "Ref1", "ABC123", 95.0, true, new Department());
        Bin bin = new Bin("b1", 10.0, 20.0, 100, "FULL", null, null);

        when(vehicleRepository.findById("v1")).thenReturn(Optional.of(vehicle));
        when(binRepository.findById("b1")).thenReturn(Optional.of(bin));
        when(vehicleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(binRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Vehicle updatedVehicle = vehicleService.emptyBin("v1", "b1");

        assertNotNull(updatedVehicle);
        assertEquals(100, updatedVehicle.getFillLevel());

        assertEquals(50, bin.getFillLevel());
        assertEquals("active", bin.getStatus());
    }

    @Test
    void testEmptyBin_vehicleNotFound() {
        Bin bin = new Bin("B1", 0, 0, 50, "active", null, null);
        when(binRepository.findById("B1")).thenReturn(Optional.of(bin));

        when(vehicleRepository.findById("V999")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vehicleService.emptyBin("V999", "B1"));

        assertEquals("Vehicle not found", exception.getMessage());
    }

    @Test
    void testEmptyBin_binNotFound() {
        Vehicle vehicle = new Vehicle("V1", "Ref1", "ABC-123", 0.0, true, null);
        when(vehicleRepository.findById("V1")).thenReturn(Optional.of(vehicle));

        when(binRepository.findById("B999")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> vehicleService.emptyBin("V1", "B999"));

        assertEquals("Bin not found", exception.getMessage());
    }

}
