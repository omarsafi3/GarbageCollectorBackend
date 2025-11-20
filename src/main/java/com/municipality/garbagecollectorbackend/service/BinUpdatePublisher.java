package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class BinUpdatePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public BinUpdatePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishBinUpdate(Bin bin) {
        messagingTemplate.convertAndSend("/topic/bins", bin);
    }
}
