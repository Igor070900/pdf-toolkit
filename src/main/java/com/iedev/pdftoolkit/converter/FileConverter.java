package com.iedev.pdftoolkit.converter;

import java.io.IOException;
import java.nio.file.Path;

public interface FileConverter {

    boolean supports(FileType fileType);

    void convert(Path source, Path destination) throws IOException;
}
