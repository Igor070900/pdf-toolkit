package com.iedev.pdftoolkit.inbox.service;

import com.iedev.pdftoolkit.inbox.entity.InboxEntity;
import com.iedev.pdftoolkit.inbox.repository.InboxRepository;
import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import com.iedev.pdftoolkit.job.repository.ConversionJobRepository;
import com.iedev.pdftoolkit.kafka.event.ConversionRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final InboxRepository inboxRepository;
    private final ConversionJobRepository conversionJobRepository;

    @Transactional
    public void register(ConversionRequestEvent event) {
        if (inboxRepository.existsByEventId(event.eventId())) {
            return;
        }

        inboxRepository.save(new InboxEntity(event.eventId()));

        conversionJobRepository.save(new ConversionJobEntity(
                event.eventId(),
                event.bucket(),
                event.objectKey(),
                event.fileName()
        ));
    }
}