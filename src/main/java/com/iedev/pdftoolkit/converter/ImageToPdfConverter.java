package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@Component
public class ImageToPdfConverter implements FileConverter {

    private static final float MARGIN = 36f;

    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.JPG || fileType == FileType.PNG;
    }

    @Override
    public void convert(Path source, Path destination) throws IOException {

        try (PDDocument document = new PDDocument()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDImageXObject imageXObject = PDImageXObject.createFromFileByContent(source.toFile(), document);

            int imgWidth = imageXObject.getWidth();
            int imgHeight = imageXObject.getHeight();

            float pageWidth = page.getMediaBox().getWidth() - 2 * MARGIN;
            float pageHeight = page.getMediaBox().getHeight() - 2 * MARGIN;


            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

                float scale = Math.min(
                        1f, Math.min(
                                pageWidth / imgWidth,
                                pageHeight / imgHeight
                        )
                );
                float scaledWidth = imgWidth * scale;
                float scaledHeight = imgHeight * scale;

                float x = MARGIN + (pageWidth - scaledWidth) / 2;
                float y = MARGIN + (pageHeight - scaledHeight) / 2;

                contentStream.drawImage(imageXObject, x, y, scaledWidth, scaledHeight);
            }

            document.save(destination.toFile());
        }
    }
}