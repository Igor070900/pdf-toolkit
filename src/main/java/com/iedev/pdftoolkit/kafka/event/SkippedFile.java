package com.iedev.pdftoolkit.kafka.event;

public record SkippedFile (
        String fileName,
        String reason
) {
}
