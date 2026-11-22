package com.iedev.pdftoolkit.kafka.event;

public record ConvertedFile (
        String bucket,
        String objectKey,
        String fileName
) {
}
