package com.iedev.pdftoolkit.converter.zip;

import com.iedev.pdftoolkit.converter.FileType;
import com.iedev.pdftoolkit.converter.FileTypeResolver;
import com.iedev.pdftoolkit.converter.exception.InvalidFileException;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ZipExtractorTest {

    @TempDir
    Path tempDir;

    @Mock
    FileTypeResolver fileTypeResolver;

    private ZipExtractor zipExtractor;
    private ZipProcessingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ZipProcessingProperties();
        zipExtractor = new ZipExtractor(properties, fileTypeResolver);
    }

    @Test
    void shouldExtractSupportedFilesFromArchive() throws IOException {
        Path archive = tempDir.resolve("input.zip");
        Path destination = tempDir.resolve("output");

        byte[] textContent = "Hello PDF Toolkit".getBytes();
        byte[] imageContent = {1, 2, 3, 4};

        Files.write(archive, createZipArchive(
                "docs/readme.txt", textContent,
                "images/photo.png", imageContent
        ));

        when(fileTypeResolver.resolve("docs/readme.txt")).thenReturn(FileType.TXT);
        when(fileTypeResolver.resolve("images/photo.png")).thenReturn(FileType.PNG);

        ZipExtractionResult result = zipExtractor.extract(archive, destination);

        assertEquals(2, result.extractedFiles().size());
        assertTrue(result.skippedFiles().isEmpty());

        Path extractedText = destination.resolve("input.zip/docs/readme.txt");
        Path extractedImage = destination.resolve("input.zip/images/photo.png");

        assertTrue(Files.exists(extractedText));
        assertTrue(Files.exists(extractedImage));

        assertEquals("Hello PDF Toolkit", Files.readString(extractedText));
        assertArrayEquals(imageContent, Files.readAllBytes(extractedImage));
    }

    @Test
    void shouldExtractFilesFromNestedArchive() throws IOException {
        Path archive = tempDir.resolve("input.zip");
        Path destination = tempDir.resolve("output");

        byte[] fileContent = "Nested archive content".getBytes();
        byte[] nestedArchive = createSingleEntryZipArchive("docs/readme.txt", fileContent);

        Files.write(archive, createZipArchive("nested.zip", nestedArchive));

        when(fileTypeResolver.resolve("docs/readme.txt"))
                .thenReturn(FileType.TXT);

        ZipExtractionResult result = zipExtractor.extract(archive, destination);

        assertEquals(1, result.extractedFiles().size());
        assertTrue(result.skippedFiles().isEmpty());

        Path extractedFile = destination.resolve("input.zip/nested.zip/docs/readme.txt");

        assertTrue(Files.exists(extractedFile));
        assertEquals("Nested archive content", Files.readString(extractedFile));
    }

    @Test
    void shouldSkipUnsupportedFiles() throws IOException {
        Path archive = tempDir.resolve("unsupported.zip");
        Path destination = tempDir.resolve("output");

        Files.write(archive, createZipArchive(
                "document.xyz",
                "unsupported content".getBytes(StandardCharsets.UTF_8)
        ));

        when(fileTypeResolver.resolve("document.xyz"))
                .thenThrow(InvalidFileException.class);

        ZipExtractionResult result = zipExtractor.extract(archive, destination);

        assertTrue(result.extractedFiles().isEmpty());
        assertEquals(1, result.skippedFiles().size());
    }

    @Test
    void shouldRejectFileExceedingMaxFileSize() throws IOException {
        properties.setMaxFileSizeBytes(10);

        Path archive = tempDir.resolve("large-file.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "This content exceeds the limit".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive("document.txt", content));

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectArchiveExceedingMaxTotalSize() throws IOException {
        properties.setMaxFileSizeBytes(10);
        properties.setMaxTotalSizeBytes(10);

        Path archive = tempDir.resolve("large-total.zip");
        Path destination = tempDir.resolve("output");

        byte[] firstFile = "123456".getBytes(StandardCharsets.UTF_8);
        byte[] secondFile = "abcdef".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive(
                "first.txt", firstFile,
                "second.txt", secondFile
        ));

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectNestedArchiveExceedingMaxTotalSize() throws IOException {
        byte[] firstFile = "123456".getBytes(StandardCharsets.UTF_8);
        byte[] secondFile = "abcdef".getBytes(StandardCharsets.UTF_8);

        byte[] nestedArchive = createZipArchive(
                "first.txt", firstFile,
                "second.txt", secondFile
        );

        Path archive = tempDir.resolve("nested-large-total.zip");
        Path destination = tempDir.resolve("output");

        Files.write(archive, createZipArchive("nested.zip", nestedArchive));

        properties.setMaxFileSizeBytes(nestedArchive.length);
        properties.setMaxTotalSizeBytes(
                nestedArchive.length + firstFile.length + secondFile.length - 1
        );

        when(fileTypeResolver.resolve("first.txt")).thenReturn(FileType.TXT);

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectArchiveExceedingMaxFileCount() throws IOException {
        properties.setMaxFileCount(1);

        Path archive = tempDir.resolve("many-files.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "test".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive(
                "first.txt", content,
                "second.txt", content
        ));

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectZipSlipEntry() throws IOException {
        Path archive = tempDir.resolve("malicious.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "malicious content".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive(
                "../../outside.txt", content
        ));

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );

        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void shouldRejectArchiveWithValidSignatureButCorruptedStructure() throws IOException {
        Path archive = tempDir.resolve("corrupted-structure.zip");
        Path destination = tempDir.resolve("output");

        byte[] corruptedContent = {0x50, 0x4B, 0x03, 0x04};

        Files.write(archive, corruptedContent);

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldExtractFilesWithSameNameFromDifferentDirectories() throws IOException {
        Path archive = tempDir.resolve("duplicates.zip");
        Path destination = tempDir.resolve("output");

        byte[] firstContent = "first file".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second file".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive(
                "docs/readme.txt", firstContent,
                "backup/readme.txt", secondContent
        ));

        when(fileTypeResolver.resolve("docs/readme.txt")).thenReturn(FileType.TXT);
        when(fileTypeResolver.resolve("backup/readme.txt")).thenReturn(FileType.TXT);

        ZipExtractionResult result = zipExtractor.extract(archive, destination);

        Path firstFile = destination.resolve("duplicates.zip/docs/readme.txt");
        Path secondFile = destination.resolve("duplicates.zip/backup/readme.txt");

        assertEquals(2, result.extractedFiles().size());
        assertArrayEquals(firstContent, Files.readAllBytes(firstFile));
        assertArrayEquals(secondContent, Files.readAllBytes(secondFile));
    }

    @Test
    void shouldCountUnsupportedFilesTowardMaxFileCount() throws IOException {
        properties.setMaxFileCount(1);

        Path archive = tempDir.resolve("unsupported.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive(
                "first.xyz", content,
                "second.xyz", content
        ));

        when(fileTypeResolver.resolve("first.xyz")).thenThrow(InvalidFileException.class);

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectArchiveExceedingMaxDepth() throws IOException {
        properties.setMaxDepth(1);

        Path archive = tempDir.resolve("nested.zip");
        Path destination = tempDir.resolve("output");

        byte[] innerArchive = createZipArchive(
                "document.txt",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        Files.write(archive, createZipArchive("inner.zip", innerArchive));

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldExtractArchiveAtMaxDepth() throws IOException {
        properties.setMaxDepth(2);

        Path archive = tempDir.resolve("nested.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "nested document".getBytes(StandardCharsets.UTF_8);
        byte[] innerArchive = createZipArchive("document.txt", content);

        Files.write(archive, createZipArchive("inner.zip", innerArchive));

        when(fileTypeResolver.resolve("document.txt")).thenReturn(FileType.TXT);

        ZipExtractionResult result = zipExtractor.extract(archive, destination);

        assertEquals(1, result.extractedFiles().size());
        assertArrayEquals(
                content,
                Files.readAllBytes(destination.resolve("nested.zip/inner.zip/document.txt"))
        );
    }

    @Test
    void shouldRejectFileWithZipExtensionButInvalidContent() throws IOException {
        Path archive = tempDir.resolve("outer.zip");
        Path destination = tempDir.resolve("output");

        byte[] invalidZipContent = "this is not a zip archive"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(archive, createZipArchive("inner.zip", invalidZipContent));

        when(fileTypeResolver.resolve("inner.zip")).thenReturn(FileType.ZIP);

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldRejectArchiveWithInvalidEntryCrc() throws IOException {
        Path archive = tempDir.resolve("corrupted-crc.zip");
        Path destination = tempDir.resolve("output");

        byte[] content = "content for crc validation".getBytes(StandardCharsets.UTF_8);
        byte[] archiveBytes = createStoredZipArchive("document.txt", content);

        int fileNameLength = ByteBuffer.wrap(archiveBytes, 26, 2)
                                     .order(ByteOrder.LITTLE_ENDIAN)
                                     .getShort() & 0xFFFF;

        int extraFieldLength = ByteBuffer.wrap(archiveBytes, 28, 2)
                                       .order(ByteOrder.LITTLE_ENDIAN)
                                       .getShort() & 0xFFFF;

        int dataOffset = 30 + fileNameLength + extraFieldLength;

        archiveBytes[dataOffset] ^= 1;

        Files.write(archive, archiveBytes);

        assertThrows(
                PermanentProcessingException.class,
                () -> zipExtractor.extract(archive, destination)
        );
    }

    @Test
    void shouldCountNestedZipFilesTowardsMaxFileCount() throws IOException {
        byte[] nestedArchive = createZipArchive(
                "first.txt", "first content".getBytes(),
                "second.txt", "second content".getBytes()
        );

        Path source = tempDir.resolve("outer.zip");
        Files.write(source, createZipArchive("nested.zip", nestedArchive));

        ZipProcessingProperties testProperties = new ZipProcessingProperties();
        testProperties.setMaxFileCount(2);

        ZipExtractor testExtractor = new ZipExtractor(
                testProperties,
                fileTypeResolver
        );

        when(fileTypeResolver.resolve("first.txt")).thenReturn(FileType.TXT);

        assertThrows(
                PermanentProcessingException.class,
                () -> testExtractor.extract(
                        source,
                        tempDir.resolve("extracted")
                )
        );
    }

    private byte[] createZipArchive(String... entries) throws IOException {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Entries must be name/content pairs");
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {

            for (int i = 0; i < entries.length; i += 2) {
                zipOutput.putNextEntry(new ZipEntry(entries[i]));
                zipOutput.write(entries[i + 1].getBytes());
                zipOutput.closeEntry();
            }

            return output.toByteArray();
        }
    }

    private byte[] createZipArchive(String firstName, byte[] firstContent,
                                    String secondName, byte[] secondContent) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutput = new ZipOutputStream(output)) {

            zipOutput.putNextEntry(new ZipEntry(firstName));
            zipOutput.write(firstContent);
            zipOutput.closeEntry();

            zipOutput.putNextEntry(new ZipEntry(secondName));
            zipOutput.write(secondContent);
            zipOutput.closeEntry();

            return output.toByteArray();
        }
    }

    private byte[] createZipArchive(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry(entryName));
            zipOutput.write(content);
            zipOutput.closeEntry();
        }

        return output.toByteArray();
    }

    private byte[] createSingleEntryZipArchive(String entryName, byte[] content) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry(entryName));
            zipOutput.write(content);
            zipOutput.closeEntry();
        }

        return output.toByteArray();
    }

    private byte[] createStoredZipArchive(String fileName, byte[] content) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(content);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            ZipEntry entry = new ZipEntry(fileName);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            entry.setCrc(crc32.getValue());

            zipOutput.putNextEntry(entry);
            zipOutput.write(content);
            zipOutput.closeEntry();
        }

        return output.toByteArray();
    }
}
