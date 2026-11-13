package com.iedev.pdftoolkit.kafka.event;

import java.util.List;
import java.util.UUID;

public record ConversionResultEvent(
        UUID eventId,
        UUID correlationId,
        ConversionStatus status,
        List<ConvertedFile> files,
        List<SkippedFile> skippedFiles,
        String errorMessage
) {
    public ConversionResultEvent {
        files = List.copyOf(files);
        skippedFiles = List.copyOf(skippedFiles);
    }
}