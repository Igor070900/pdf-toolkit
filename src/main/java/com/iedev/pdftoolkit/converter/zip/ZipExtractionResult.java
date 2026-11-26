package com.iedev.pdftoolkit.converter.zip;

import com.iedev.pdftoolkit.kafka.event.SkippedFile;

import java.nio.file.Path;
import java.util.List;

public record ZipExtractionResult(
        List<Path> extractedFiles,
        List<SkippedFile> skippedFiles
) {
    public ZipExtractionResult {
        extractedFiles = List.copyOf(extractedFiles);
        skippedFiles = List.copyOf(skippedFiles);
    }
}
