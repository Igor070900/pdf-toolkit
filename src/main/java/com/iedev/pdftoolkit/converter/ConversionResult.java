package com.iedev.pdftoolkit.converter;

import com.iedev.pdftoolkit.kafka.event.SkippedFile;

import java.util.List;

public record ConversionResult(
        List<ConvertedFileResult> files,
        List<SkippedFile> skippedFiles
) {
    public ConversionResult {
        files = List.copyOf(files);
        skippedFiles = List.copyOf(skippedFiles);
    }
}