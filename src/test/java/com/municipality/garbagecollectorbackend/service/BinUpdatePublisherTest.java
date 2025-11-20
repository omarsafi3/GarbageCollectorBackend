package com.municipality.garbagecollectorbackend.service;

import com.municipality.garbagecollectorbackend.model.Bin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class BinUpdatePublisherTest {

    private SimpMessagingTemplate messagingTemplate;
    private BinUpdatePublisher publisher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        publisher = new BinUpdatePublisher(messagingTemplate);
    }

    @Test
    void testPublishBinUpdate() {
        Bin bin = new Bin("1", 36.8, 10.1, 50, "normal",
                LocalDateTime.now(), LocalDateTime.now());

        publisher.publishBinUpdate(bin);

        verify(messagingTemplate, times(1))
                .convertAndSend("/topic/bins", bin);
    }
}
