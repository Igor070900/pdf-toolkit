package com.iedev.pdftoolkit.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@Slf4j
@RequiredArgsConstructor
public class S3StorageTestRunner implements CommandLineRunner {

    private final S3StorageService storageService;

    @Override
    public void run(String... args){
        String bucket = "pdf-input";
        String objectKey = "test/s3-test.txt";

        Path source = Path.of("/tmp/pdf-toolkit-test/s3-test.txt");
        Path destination = Path.of("/tmp/pdf-toolkit-test/s3-test-downloaded.txt");

        storageService.upload(bucket, objectKey, source);
        log.info("File uploaded successfully");

        storageService.download(bucket, objectKey, destination);
        log.info("File downloaded successfully");
    }
}
