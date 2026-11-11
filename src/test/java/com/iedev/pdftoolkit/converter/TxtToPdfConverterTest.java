package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TxtToPdfConverterTest {

    @Test
    void shouldConvertTxtToPdf() throws IOException {
        Path source = Path.of("src/test/resources/files/sample.txt");
        Path destination = Path.of("target/test-output/sample.pdf");
        Files.createDirectories(destination.getParent());

        TxtToPdfConverter converter = new TxtToPdfConverter();

        Files.deleteIfExists(destination);
        converter.convert(source, destination);

        assertTrue(Files.exists(destination));
        assertTrue(Files.size(destination) > 0);

        try (PDDocument document = Loader.loadPDF(destination.toFile())) {

            assertTrue(document.getNumberOfPages() > 0);

            PDFTextStripper textStripper = new PDFTextStripper();
            String extractedText = textStripper.getText(document);

            assertTrue(extractedText.contains("Привет!"));
            assertTrue(extractedText.contains("PDF Toolkit"));
            assertTrue(extractedText.contains("ОченьДлинноеСлово"));
            assertTrue(document.getNumberOfPages() > 1, "PDF должен содержать больше одной страницы");
        }
    }
}
