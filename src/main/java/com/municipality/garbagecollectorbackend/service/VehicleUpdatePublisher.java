package com.municipality.garbagecollectorbackend.service;
import com.municipality.garbagecollectorbackend.DTO.RouteCompletionEvent;
import com.municipality.garbagecollectorbackend.DTO.RouteProgressUpdate;
import com.municipality.garbagecollectorbackend.DTO.TruckPositionUpdate;
import com.municipality.garbagecollectorbackend.model.Vehicle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleUpdatePublisher {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Publish vehicle fill level update
     */
    public void publishVehicleUpdate(Vehicle vehicle) {
        messagingTemplate.convertAndSend("/topic/vehicles", vehicle);
        System.out.println("📡 Sent vehicle update: " + vehicle.getId() +
                " -> " + vehicle.getFillLevel() + "%");
    }

    /**
     * Publish truck position update (GPS coordinates)
     */
    public void publishTruckPosition(TruckPositionUpdate update) {
        messagingTemplate.convertAndSend("/topic/truck-position", update);
        System.out.println("📍 Sent truck position: " + update.getVehicleId() +
                " -> (" + update.getLatitude() + ", " + update.getLongitude() +
                ") Progress: " + String.format("%.1f", update.getProgressPercent()) + "%");
    }

    /**
     * Publish route progress update (bin collection)
     */
    public void publishRouteProgress(RouteProgressUpdate update) {
        messagingTemplate.convertAndSend("/topic/route-progress", update);
        System.out.println("📊 Sent route progress: " + update.getVehicleId() +
                " -> Stop " + update.getCurrentStop() + "/" + update.getTotalStops() +
                " (Fill: " + String.format("%.1f", update.getVehicleFillLevel()) + "%)");
    }

    /**
     * Publish route completion event
     */
    public void publishRouteCompletion(RouteCompletionEvent event) {
        messagingTemplate.convertAndSend("/topic/route-completion", event);
        System.out.println("✅ Sent route completion: " + event.getVehicleId() +
                " -> " + event.getBinsCollected() + " bins collected");
    }
}
