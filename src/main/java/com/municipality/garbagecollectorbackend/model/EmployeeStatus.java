package com.municipality.garbagecollectorbackend.model;

public enum EmployeeStatus {
    AVAILABLE,   // Ready to be assigned
    ASSIGNED,    // Assigned to vehicle but not yet dispatched
    IN_ROUTE     // Currently on a route with vehicle
}
