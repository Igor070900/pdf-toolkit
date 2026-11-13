package com.iedev.pdftoolkit.converter;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class FileTypeResolver {

    private static final Map<String, FileType> EXTENSIONS = Map.of(
            "txt", FileType.TXT,
            "png", FileType.PNG,
            "jpg", FileType.JPG,
            "jpeg", FileType.JPG,
            "zip", FileType.ZIP
    );

    public FileType resolve(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла не должно быть пустым");
        }

        int lastDotIndex = fileName.lastIndexOf(".");

        if (lastDotIndex <= 0 || lastDotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException(
                    "Файл должен иметь правильное расширение" + fileName);
        }

        String extension = fileName.substring(lastDotIndex + 1)
                .toLowerCase(Locale.ROOT);

        FileType fileType = EXTENSIONS.get(extension);

        if (fileType == null) {
            throw new IllegalArgumentException("Неподдерживаемый формат: " + fileType);
        }

        return fileType;
    }
}
