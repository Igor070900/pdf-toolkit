package com.iedev.pdftoolkit.converter;

import java.nio.file.Path;

public record ConvertedFileResult(
        Path path,
        String bucket,
        String objectKey,
        String fileName
) {
}