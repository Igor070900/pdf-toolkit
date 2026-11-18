package com.iedev.pdftoolkit.converter;

import com.iedev.pdftoolkit.converter.exception.InvalidFileException;
import com.iedev.pdftoolkit.converter.exception.InvalidImageException;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileConversionService {

    private final FileTypeResolver fileTypeResolver;
    private final ConverterRegistry converterRegistry;

    public void convert(String fileName, Path source, Path destination) throws IOException {
        FileType fileType;

        try {
            fileType = fileTypeResolver.resolve(fileName);
        } catch (InvalidFileException exception) {
            throw new PermanentProcessingException(exception.getMessage(), exception);
        }

        if (fileType == FileType.ZIP) {
            throw new PermanentProcessingException("zip обработка пока не реализована");
        }

        FileConverter converter = converterRegistry.getConverter(fileType);
        try {
            converter.convert(source, destination);
        } catch (InvalidImageException exception) {
            throw new PermanentProcessingException(exception.getMessage(), exception);
        } catch (MalformedInputException exception) {
            throw new PermanentProcessingException("Проблемная кодирока файла", exception);
        }
    }
}
