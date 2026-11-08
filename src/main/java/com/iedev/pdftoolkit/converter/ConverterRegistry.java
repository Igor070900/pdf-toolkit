package com.iedev.pdftoolkit.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConverterRegistry {

    private final List<FileConverter> converters;

    public FileConverter getConverter(FileType fileType) {
        return converters.stream()
                .filter(converter -> converter.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Такого конвертера не найдено для типа: " + fileType
                ));
    }
}
