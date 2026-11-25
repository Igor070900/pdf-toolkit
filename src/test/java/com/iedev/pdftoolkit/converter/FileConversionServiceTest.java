package com.iedev.pdftoolkit.converter;


import com.iedev.pdftoolkit.converter.exception.InvalidFileException;
import com.iedev.pdftoolkit.converter.zip.ZipExtractionResult;
import com.iedev.pdftoolkit.converter.zip.ZipExtractor;
import com.iedev.pdftoolkit.kafka.event.SkippedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FileConversionServiceTest {

    @Mock
    private FileTypeResolver fileTypeResolver;

    @Mock
    private ConverterRegistry converterRegistry;

    @Mock
    private ZipExtractor zipExtractor;

    @Mock
    private FileConverter fileConverter;

    @TempDir
    Path tempDir;

    private FileConversionService fileConversionService;

    @BeforeEach
    void setUp() {
        fileConversionService = new FileConversionService(
                fileTypeResolver,
                converterRegistry,
                zipExtractor
        );
    }

    @Test
    void shouldConvertAllSupportedFilesFromZip() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        Path extractionDirectory = tempDir.resolve("zip-extracted");

        Path textFile = extractionDirectory.resolve("documents/report.txt");
        Path imageFile = extractionDirectory.resolve("images/photo.png");

        Files.createDirectories(textFile.getParent());
        Files.createDirectories(imageFile.getParent());

        Files.writeString(textFile, "Report");
        Files.write(imageFile, new byte[]{1, 2, 3});

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);
        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenReturn(new ZipExtractionResult(
                        List.of(textFile, imageFile),
                        List.of()
                ));

        when(fileTypeResolver.resolve("documents/report.txt")).thenReturn(FileType.TXT);
        when(fileTypeResolver.resolve("images/photo.png")).thenReturn(FileType.PNG);

        when(converterRegistry.getConverter(FileType.TXT)).thenReturn(fileConverter);
        when(converterRegistry.getConverter(FileType.PNG)).thenReturn(fileConverter);

        doAnswer(invocation -> {
            Path output = invocation.getArgument(1);
            Files.createDirectories(output.getParent());
            Files.createFile(output);
            return null;
        }).when(fileConverter).convert(any(Path.class), any(Path.class));

        ConversionResult result = fileConversionService.convert(
                "archive.zip",
                source,
                destination,
                "pdf-bucket",
                "converted/job-123/archive.pdf"
        );

        assertEquals(2, result.files().size());
        assertTrue(result.skippedFiles().isEmpty());

        assertEquals(
                "converted/job-123/documents/report.pdf",
                result.files().get(0).objectKey()
        );
        assertEquals(
                "converted/job-123/images/photo.pdf",
                result.files().get(1).objectKey()
        );

        verify(fileConverter, times(2)).convert(any(Path.class), any(Path.class));
    }

    @Test
    void shouldSkipFileWhenConversionFailsAndContinueProcessing() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        Path extractionDirectory = tempDir.resolve("zip-extracted");
        Path firstFile = extractionDirectory.resolve("documents/broken.txt");
        Path secondFile = extractionDirectory.resolve("documents/report.txt");

        Files.createDirectories(firstFile.getParent());
        Files.writeString(firstFile, "Broken");
        Files.writeString(secondFile, "Report");

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);
        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenReturn(new ZipExtractionResult(
                        List.of(firstFile, secondFile),
                        List.of()
                ));

        when(fileTypeResolver.resolve("documents/broken.txt")).thenReturn(FileType.TXT);
        when(fileTypeResolver.resolve("documents/report.txt")).thenReturn(FileType.TXT);

        when(converterRegistry.getConverter(FileType.TXT)).thenReturn(fileConverter);

        doAnswer(invocation -> {
            Path input = invocation.getArgument(0);

            if (input.equals(firstFile)) {
                throw new MalformedInputException(1);
            }

            Path output = invocation.getArgument(1);
            Files.createDirectories(output.getParent());
            Files.createFile(output);
            return null;
        }).when(fileConverter).convert(any(Path.class), any(Path.class));

        ConversionResult result = fileConversionService.convert(
                "archive.zip",
                source,
                destination,
                "pdf-bucket",
                "converted/job-123/archive.pdf"
        );

        assertEquals(1, result.files().size());
        assertEquals(1, result.skippedFiles().size());

        assertEquals(
                "converted/job-123/documents/report.pdf",
                result.files().get(0).objectKey()
        );

        assertEquals(
                "documents/broken.txt",
                result.skippedFiles().get(0).fileName()
        );

        verify(fileConverter, times(2))
                .convert(any(Path.class), any(Path.class));
    }

    @Test
    void shouldSkipUnsupportedFileAndContinueProcessing() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        Path extractionDirectory = tempDir.resolve("zip-extracted");
        Path unsupportedFile = extractionDirectory.resolve("documents/data.xyz");
        Path supportedFile = extractionDirectory.resolve("documents/report.txt");

        Files.createDirectories(unsupportedFile.getParent());
        Files.writeString(unsupportedFile, "Unsupported");
        Files.writeString(supportedFile, "Report");

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);
        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenReturn(new ZipExtractionResult(
                        List.of(unsupportedFile, supportedFile),
                        List.of()
                ));

        when(fileTypeResolver.resolve("documents/data.xyz"))
                .thenThrow(new InvalidFileException("Неподдерживаемый формат"));

        when(fileTypeResolver.resolve("documents/report.txt"))
                .thenReturn(FileType.TXT);

        when(converterRegistry.getConverter(FileType.TXT))
                .thenReturn(fileConverter);

        doAnswer(invocation -> {
            Path output = invocation.getArgument(1);
            Files.createDirectories(output.getParent());
            Files.createFile(output);
            return null;
        }).when(fileConverter).convert(any(Path.class), any(Path.class));

        ConversionResult result = fileConversionService.convert(
                "archive.zip",
                source,
                destination,
                "pdf-bucket",
                "converted/job-123/archive.pdf"
        );

        assertEquals(1, result.files().size());
        assertEquals(1, result.skippedFiles().size());

        assertEquals(
                "documents/data.xyz",
                result.skippedFiles().get(0).fileName()
        );

        assertEquals(
                "Неподдерживаемый формат",
                result.skippedFiles().get(0).reason()
        );

        assertEquals(
                "converted/job-123/documents/report.pdf",
                result.files().get(0).objectKey()
        );

        verify(fileConverter, times(1))
                .convert(any(Path.class), any(Path.class));
    }

    @Test
    void shouldReturnEmptyFilesWhenAllZipEntriesAreUnsupported() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        Path extractionDirectory = tempDir.resolve("zip-extracted");
        Path firstFile = extractionDirectory.resolve("data.xyz");
        Path secondFile = extractionDirectory.resolve("readme.unknown");

        Files.createDirectories(extractionDirectory);
        Files.writeString(firstFile, "Data");
        Files.writeString(secondFile, "Readme");

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);

        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenReturn(new ZipExtractionResult(
                        List.of(firstFile, secondFile),
                        List.of()
                ));

        when(fileTypeResolver.resolve("data.xyz"))
                .thenThrow(new InvalidFileException("Неподдерживаемый формат"));

        when(fileTypeResolver.resolve("readme.unknown"))
                .thenThrow(new InvalidFileException("Неподдерживаемый формат"));

        ConversionResult result = fileConversionService.convert(
                "archive.zip",
                source,
                destination,
                "pdf-bucket",
                "converted/job-123/archive.pdf"
        );

        assertTrue(result.files().isEmpty());
        assertEquals(2, result.skippedFiles().size());

        verifyNoInteractions(converterRegistry);
        verifyNoInteractions(fileConverter);
    }

    @Test
    void shouldPreserveFilesSkippedDuringExtraction() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        Path extractedFile = tempDir.resolve("zip-extracted/report.txt");
        Files.createDirectories(extractedFile.getParent());
        Files.writeString(extractedFile, "Report");

        SkippedFile skippedFile = new SkippedFile(
                "documents/data.xyz",
                "Неподдерживаемый формат"
        );

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);

        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenReturn(new ZipExtractionResult(
                        List.of(extractedFile),
                        List.of(skippedFile)
                ));

        when(fileTypeResolver.resolve("report.txt")).thenReturn(FileType.TXT);
        when(converterRegistry.getConverter(FileType.TXT)).thenReturn(fileConverter);

        ConversionResult result = fileConversionService.convert(
                "archive.zip",
                source,
                destination,
                "pdf-bucket",
                "converted/job-123/archive.pdf"
        );

        assertEquals(1, result.files().size());
        assertEquals(1, result.skippedFiles().size());
        assertEquals(skippedFile, result.skippedFiles().getFirst());

        verify(fileConverter).convert(eq(extractedFile), any(Path.class));
    }

    @Test
    void shouldPropagateExtractionFailure() throws IOException {
        Path source = tempDir.resolve("archive.zip");
        Path destination = tempDir.resolve("archive.pdf");

        when(fileTypeResolver.resolve("archive.zip")).thenReturn(FileType.ZIP);

        IOException extractionException = new IOException("Corrupted ZIP archive");

        when(zipExtractor.extract(eq(source), any(Path.class)))
                .thenThrow(extractionException);

        IOException exception = assertThrows(
                IOException.class,
                () -> fileConversionService.convert(
                        "archive.zip",
                        source,
                        destination,
                        "pdf-bucket",
                        "converted/job-123/archive.pdf"
                )
        );

        assertSame(extractionException, exception);
        verifyNoInteractions(converterRegistry);
        verifyNoInteractions(fileConverter);
    }
}