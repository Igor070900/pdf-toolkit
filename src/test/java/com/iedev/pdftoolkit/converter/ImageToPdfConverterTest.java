package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageToPdfConverterTest {

    @Test
    void shouldConvertImageToPdf() throws IOException {
        Path source = Path.of("src/test/resources/files/sample.png");
        Path destination = Path.of("target/test-output/sample.pdf");
        Files.createDirectories(destination.getParent());

        ImageToPdfConverter converter = new ImageToPdfConverter();

        Files.deleteIfExists(destination);
        converter.convert(source, destination);

        assertTrue(Files.exists(destination));
        assertTrue(Files.size(destination) > 0);

        try (PDDocument document = Loader.loadPDF(destination.toFile())) {

            assertTrue(document.getNumberOfPages() > 0);
        }
    }
}
