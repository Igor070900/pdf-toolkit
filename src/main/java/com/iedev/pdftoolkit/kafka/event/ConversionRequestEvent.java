package com.iedev.pdftoolkit.kafka.event;

import java.util.UUID;

public record ConversionRequestEvent(
        UUID eventId,
        String bucket,
        String objectKey,
        String fileName
) {}
