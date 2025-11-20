package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Incident;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class IncidentUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public IncidentUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishIncidentUpdate(Incident incident) {
        messagingTemplate.convertAndSend("/topic/incidents", incident);
    }
}
