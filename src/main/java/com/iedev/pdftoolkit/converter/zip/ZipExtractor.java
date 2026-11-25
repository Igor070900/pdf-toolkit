
package com.iedev.pdftoolkit.converter.zip;

import com.iedev.pdftoolkit.converter.FileType;
import com.iedev.pdftoolkit.converter.FileTypeResolver;
import com.iedev.pdftoolkit.converter.exception.InvalidFileException;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import com.iedev.pdftoolkit.kafka.event.SkippedFile;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class ZipExtractor {

    private final ZipProcessingProperties properties;
    private final FileTypeResolver fileTypeResolver;

    public ZipExtractionResult extract(Path archive, Path destinationDirectory) throws IOException {

        Files.createDirectories(destinationDirectory);

        ZipExtractionContext context = new ZipExtractionContext();
        List<Path> extractedFiles = new ArrayList<>();
        List<SkippedFile> skippedFiles = new ArrayList<>();

        extractArchive(
                archive,
                destinationDirectory,
                archive.getFileName().toString(),
                1,
                context,
                extractedFiles,
                skippedFiles
        );

        return new ZipExtractionResult(extractedFiles, skippedFiles);
    }

    private void extractArchive(
            Path archive,
            Path destinationDirectory,
            String archivePrefix,
            int depth,
            ZipExtractionContext context,
            List<Path> extractedFiles,
            List<SkippedFile> skippedFiles
    ) throws IOException {
        if (!isZipArchive(archive)) {
            throw new PermanentProcessingException("File is not a valid ZIP archive");
        }
        if (depth > properties.getMaxDepth()) {
            throw new PermanentProcessingException("Maximum ZIP nesting depth exceeded");
        }

        try (InputStream input = new BufferedInputStream(Files.newInputStream(archive));
             ZipInputStream zipInput = new ZipInputStream(input)) {

            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zipInput.closeEntry();
                    continue;
                }

                resolveEntryPath(destinationDirectory, entry.getName());

                String relativeName = archivePrefix.isBlank()
                        ? entry.getName()
                        : archivePrefix + "/" + entry.getName();

                Path tempFile = Files.createTempFile(destinationDirectory, "zip-entry-", ".tmp");

                try {
                    Files.delete(tempFile);

                    extractEntry(zipInput, tempFile, context);

                    if (isZipArchive(tempFile)) {
                        extractArchive(
                                tempFile,
                                destinationDirectory,
                                relativeName,
                                depth + 1,
                                context,
                                extractedFiles,
                                skippedFiles
                        );
                    } else {
                        context.registerFile(Files.size(tempFile), properties);

                        try {
                            FileType fileType = fileTypeResolver.resolve(entry.getName());

                            if (fileType == FileType.ZIP) {
                                throw new PermanentProcessingException(
                                        "File has ZIP extension but is not a valid ZIP archive"
                                );
                            }

                            Path outputPath = resolveEntryPath(destinationDirectory, relativeName);

                            Files.createDirectories(outputPath.getParent());
                            Files.move(tempFile, outputPath);

                            extractedFiles.add(outputPath);
                        } catch (InvalidFileException exception) {
                            skippedFiles.add(new SkippedFile(relativeName, "Unsupported file type"));
                        }
                    }
                } finally {
                    Files.deleteIfExists(tempFile);
                }

                zipInput.closeEntry();
            }
        } catch (ZipException exception) {
            throw new PermanentProcessingException("Invalid or corrupted ZIP archive", exception);
        }
    }

    private void extractEntry(InputStream input, Path destination, ZipExtractionContext context) throws IOException {

        Files.createDirectories(destination.getParent());

        byte[] buffer = new byte[8192];
        long currentFileSize = 0;

        try (OutputStream output = Files.newOutputStream(
                destination,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                context.validateChunk(
                        currentFileSize,
                        bytesRead,
                        properties
                );

                output.write(buffer, 0, bytesRead);
                currentFileSize += bytesRead;
            }
        }
    }

    private Path resolveEntryPath(Path destinationDirectory,String entryName) {

        Path normalizedRoot = destinationDirectory.toAbsolutePath().normalize();
        Path resolvedPath = normalizedRoot.resolve(entryName).normalize();

        if (!resolvedPath.startsWith(normalizedRoot)) {
            throw new PermanentProcessingException("ZIP entry attempts to escape destination directory");
        }

        return resolvedPath;
    }

    private boolean isZipArchive(Path file) throws IOException {
        if (Files.size(file) < 22) {
            return false;
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] signature = input.readNBytes(4);

            return ZipArchiveInputStream.matches(signature, signature.length);
        }
    }
}