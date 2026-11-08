package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

@Component
public class TxtToPdfConverter implements FileConverter {

    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.TXT;
    }

    @Override
    public void convert(Path source, Path destination) throws IOException {

        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {

            if (fontStream == null) {
                throw new IOException("Шрифт DejaVuSans.ttf не найден");
            }

            PDType0Font font = PDType0Font.load(document, fontStream);

            float margin = 50;
            float fontSize = 12;
            float leading = 15;

            String line;
            boolean endOfFile = false;

            try (BufferedReader reader =
                             Files.newBufferedReader(source, StandardCharsets.UTF_8)) {

                String pendingLine = null;
                float pageWidth = PDRectangle.A4.getWidth();
                float availableWidth = pageWidth - 2 * margin;

                while (!endOfFile) {
                    PDPage page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    document.addPage(page);

                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {

                        contentStream.beginText();
                        contentStream.setFont(font, fontSize);
                        contentStream.newLineAtOffset(margin, page.getMediaBox().getHeight() - margin);

                        float yPosition = page.getMediaBox().getHeight() - margin;
                        float bottomMargin = margin;

                        while (true) {
                            if (pendingLine == null) {
                                line = reader.readLine();
                            } else {
                                line = pendingLine;
                                pendingLine = null;
                            }

                            if (line == null) {
                                endOfFile = true;
                                break;
                            }

                            if (yPosition - leading < bottomMargin) {
                                pendingLine = line;
                                break;
                            }
                            contentStream.showText(line);
                            contentStream.newLineAtOffset(0, -leading);

                            yPosition -= leading;
                        }

                        contentStream.endText();
                    }
                }
                }

        }
    }

    private List<String> wrapLine(
            String line,
            PDFont font,
            float fontSize,
            float maxWidth
    ) throws IOException {

        List<String> result = new ArrayList<>();

        if (line.isEmpty()) {
            result.add("");
            return result;
        }

        StringBuilder currentLine = new StringBuilder();

        for (String word : line.split("\\s+")) {
            String candidate = currentLine.isEmpty()
                    ? word
                    : currentLine + " " + word;

            float textWidth = font.getStringWidth(candidate) / 1000 * fontSize;

            if (textWidth <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
            } else {
                if (!currentLine.isEmpty()) {
                    result.add(currentLine.toString());
                }

                currentLine.setLength(0);
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            result.add(currentLine.toString());
        }

        return result;
    }
}