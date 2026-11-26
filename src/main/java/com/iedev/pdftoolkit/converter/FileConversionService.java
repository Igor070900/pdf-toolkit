package com.iedev.pdftoolkit.converter;

import com.iedev.pdftoolkit.converter.exception.InvalidFileException;
import com.iedev.pdftoolkit.converter.exception.InvalidImageException;
import com.iedev.pdftoolkit.converter.zip.ZipExtractionResult;
import com.iedev.pdftoolkit.converter.zip.ZipExtractor;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import com.iedev.pdftoolkit.kafka.event.SkippedFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileConversionService {

    private final FileTypeResolver fileTypeResolver;
    private final ConverterRegistry converterRegistry;
    private final ZipExtractor zipExtractor;

    public ConversionResult convert(
            String fileName,
            Path source,
            Path destination,
            String bucket,
            String outputObjectKey
    ) throws IOException {

        FileType fileType;

        try {
            fileType = fileTypeResolver.resolve(fileName);
        } catch (InvalidFileException exception) {
            throw new PermanentProcessingException(exception.getMessage(), exception);
        }

        if (fileType == FileType.ZIP) {
            return convertZip(source, destination, bucket, outputObjectKey);
        }

        FileConverter converter = converterRegistry.getConverter(fileType);
        try {
            converter.convert(source, destination);
        } catch (InvalidImageException exception) {
            throw new PermanentProcessingException(exception.getMessage(), exception);
        } catch (MalformedInputException exception) {
            throw new PermanentProcessingException("Проблемная кодирока файла", exception);
        }

        return new ConversionResult(
                List.of(new ConvertedFileResult(
                        destination,
                        bucket,
                        outputObjectKey,
                        destination.getFileName().toString()
                )),
                List.of()
        );
    }

    private ConversionResult convertZip(
            Path source,
            Path destination,
            String bucket,
            String outputObjectKey
    ) throws IOException {
        Path workingDirectory = destination.toAbsolutePath().getParent();
        Path extractionDirectory = workingDirectory.resolve("zip-extracted");
        Path convertedDirectory = workingDirectory.resolve("zip-converted");

        Files.createDirectories(extractionDirectory);
        Files.createDirectories(convertedDirectory);

        ZipExtractionResult extractionResult = zipExtractor.extract(source, extractionDirectory);

        List<ConvertedFileResult> convertedFiles = new ArrayList<>();
        List<SkippedFile> skippedFiles = new ArrayList<>(extractionResult.skippedFiles());
        Set<String> usedOutputPaths = new HashSet<>();

        String outputPrefix = outputObjectKey.substring(0, outputObjectKey.lastIndexOf('/') + 1);

        for (Path extractedFile : extractionResult.extractedFiles()) {

            Path relativePath = extractionDirectory.relativize(extractedFile);

            String relativeFileName = relativePath.toString().replace('\\', '/');

            try {
                FileType fileType = fileTypeResolver.resolve(relativeFileName);

                if (fileType == FileType.ZIP) {
                    throw new InvalidFileException("Nested ZIP was not fully extracted");
                }

                FileConverter converter = converterRegistry.getConverter(fileType);

                Path relativePdfPath = resolveUniquePdfPath(
                        relativePath,
                        convertedDirectory,
                        usedOutputPaths
                );

                String pdfFileName = relativePdfPath.getFileName().toString();

                Path pdfPath = convertedDirectory.resolve(relativePdfPath);

                Files.createDirectories(pdfPath.getParent());

                try {
                    converter.convert(extractedFile, pdfPath);
                    usedOutputPaths.add(relativePdfPath.toString()
                            .replace('\\', '/')
                            .toLowerCase(Locale.ROOT));
                } catch (InvalidImageException | MalformedInputException exception) {
                    Files.deleteIfExists(pdfPath);

                    skippedFiles.add(new SkippedFile(
                            relativeFileName,
                            exception.getMessage()
                    ));
                    continue;
                }

                String pdfObjectKey = outputPrefix
                                      + relativePdfPath.toString().replace('\\', '/');

                convertedFiles.add(new ConvertedFileResult(
                        pdfPath,
                        bucket,
                        pdfObjectKey,
                        pdfFileName
                ));

            } catch (InvalidFileException exception) {
                skippedFiles.add(new SkippedFile(
                        relativeFileName,
                        exception.getMessage()
                ));
            }
        }

        return new ConversionResult(convertedFiles, skippedFiles);
    }

    private Path resolveUniquePdfPath(
            Path relativePath,
            Path convertedDirectory,
            Set<String> usedOutputPaths
    ) {
        String originalFileName = relativePath.getFileName().toString();
        String baseName = originalFileName.replaceFirst("(?i)\\.[^.]+$", "");

        int suffix = 1;

        while (true) {
            String pdfFileName = suffix == 1
                    ? baseName + ".pdf"
                    : baseName + "-" + suffix + ".pdf";

            Path candidate = relativePath.resolveSibling(pdfFileName);

            String normalizedPath = candidate.toString()
                    .replace('\\', '/')
                    .toLowerCase(Locale.ROOT);

            if (!usedOutputPaths.contains(normalizedPath)
                && !Files.exists(convertedDirectory.resolve(candidate))) {
                return candidate;
            }

            suffix++;
        }
    }
}