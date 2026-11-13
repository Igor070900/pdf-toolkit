package com.iedev.pdftoolkit.kafka.event;

import java.util.UUID;

public record ConversionRequestEvent(
        UUID eventId,
        UUID userId,
        String bucket,
        String objectKey,
        String fileName
) {}
