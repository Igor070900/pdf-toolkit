package com.iedev.pdftoolkit.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Client s3Client;

    public void download(String bucket, String objectKey, Path destination) {

        var objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        s3Client.getObject(objectRequest, destination);
    }

    public void upload(String bucket, String objectKey, Path source) {
        var objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        s3Client.putObject(objectRequest, RequestBody.fromFile(source));
    }
}