package com.iedev.pdftoolkit.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileConversionService {

    private final FileTypeResolver fileTypeResolver;
    private final ConverterRegistry converterRegistry;

    public void convert(String fileName, Path source, Path destination) throws IOException {
        FileType fileType = fileTypeResolver.resolve(fileName);

        if (fileType == FileType.ZIP) {
            throw new IllegalArgumentException("zip файлы обрабатываются отдельно");
        }

        FileConverter converter = converterRegistry.getConverter(fileType);
        converter.convert(source, destination);
    }
}
