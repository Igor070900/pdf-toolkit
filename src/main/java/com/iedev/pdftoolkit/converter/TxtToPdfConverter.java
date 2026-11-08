package com.iedev.pdftoolkit.converter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

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
                throw new RuntimeException("Шрифт DejaVuSans.ttf не найден");
            }

            PDType0Font font = PDType0Font.load(document, fontStream);
        }
    }
}
