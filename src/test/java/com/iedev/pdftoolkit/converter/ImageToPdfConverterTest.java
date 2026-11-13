package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImageToPdfConverterTest {

    @Test
    void shouldConvertPngToPdf() throws IOException {
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

            PDResources resources = document.getPage(0).getResources();

            boolean hasImage = false;

            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    hasImage = true;
                    break;
                }
            }

            assertTrue(hasImage, "PDF должен содержать изображение");
        }
    }

    @Test
    void shouldConvertJpgToPdf() throws IOException{
        Path source = Path.of("src/test/resources/files/sample.jpg");
        Path destination = Path.of("target/test-output/sample.pdf");
        Files.createDirectories(destination.getParent());

        ImageToPdfConverter converter = new ImageToPdfConverter();

        Files.deleteIfExists(destination);
        converter.convert(source, destination);

        assertTrue(Files.exists(destination));
        assertTrue(Files.size(destination) > 0);

        try (PDDocument document = Loader.loadPDF(destination.toFile())) {

            assertTrue(document.getNumberOfPages() > 0);

            PDResources resources = document.getPage(0).getResources();

            boolean hasImage = false;

            for (COSName name : resources.getXObjectNames()) {
                if (resources.isImageXObject(name)) {
                    hasImage = true;
                    break;
                }
            }

            assertTrue(hasImage, "PDF должен содержать изображение");
        }
    }
}
