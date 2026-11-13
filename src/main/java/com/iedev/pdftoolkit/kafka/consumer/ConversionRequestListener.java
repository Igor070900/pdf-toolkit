package com.iedev.pdftoolkit.kafka.consumer;

import com.iedev.pdftoolkit.inbox.service.InboxService;
import com.iedev.pdftoolkit.kafka.event.ConversionRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversionRequestListener {

    private final InboxService inboxService;

    @KafkaListener(topics = "pdf-conversion-requests", groupId = "pdf-toolkit-conversion-workers")
    public void listen(ConversionRequestEvent event) {
        log.info("Received conversion request: {}", event);
        inboxService.register(event);
    }
}