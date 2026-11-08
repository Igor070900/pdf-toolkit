package com.iedev.pdftoolkit.kafka.event;

import java.util.UUID;

public record ConversionResultEvent(
        UUID eventId,
        UUID jobId,
        ConversionStatus status,
        String resultBucket,
        String resultObjectKey,
        String errorMessage
) {
}