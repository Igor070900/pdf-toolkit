package com.iedev.pdftoolkit.converter.zip;

import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import lombok.Getter;

@Getter
public class ZipExtractionContext {

    private int extractedFileCount;
    private long totalExtractedSize;

    public void registerFile(long fileSize, ZipProcessingProperties properties) {
        if (fileSize < 0) {
            throw new PermanentProcessingException("Invalid extracted file size");
        }
        if (extractedFileCount >= properties.getMaxFileCount()) {
            throw new PermanentProcessingException("ZIP contains too many files");
        }
        if (fileSize > properties.getMaxFileSizeBytes()) {
            throw new PermanentProcessingException("Extracted file exceeds maximum allowed size");
        }
        if (fileSize > properties.getMaxTotalSizeBytes() - totalExtractedSize) {
            throw new PermanentProcessingException("ZIP exceeds maximum total extracted size");
        }

        extractedFileCount++;
        totalExtractedSize += fileSize;
    }

    public void validateChunk(long currentFileSize, long chunkSize, ZipProcessingProperties properties) {
        if (chunkSize < 0 || currentFileSize < 0) {
            throw new PermanentProcessingException("Invalid extracted file size");
        }
        if (chunkSize > properties.getMaxFileSizeBytes() - currentFileSize) {
            throw new PermanentProcessingException("Extracted file exceeds maximum allowed size");
        }
    }
}
