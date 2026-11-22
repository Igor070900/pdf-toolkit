package com.iedev.pdftoolkit.s3.storage;

import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import com.iedev.pdftoolkit.job.worker.exception.RetryableProcessingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

        try {
            s3Client.getObject(objectRequest, destination);
        } catch (SdkClientException e) {
            throw new RetryableProcessingException("S3 download failed", e);
        } catch (S3Exception e) {
            int statusCode = e.statusCode();
            if (statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429) {
                throw new PermanentProcessingException("S3 object download rejected", e);
            }
            throw new RetryableProcessingException("S3 download failed", e);
        }
    }

    public void upload(String bucket, String objectKey, Path source) {
        var objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.putObject(objectRequest, RequestBody.fromFile(source));
        } catch (SdkClientException e) {
            throw new RetryableProcessingException("S3 upload failed", e);
        } catch (S3Exception e) {
            int statusCode = e.statusCode();
            if (statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429) {
                throw new PermanentProcessingException("S3 upload rejected", e);
            }
            throw new RetryableProcessingException("S3 upload failed", e);
        }
    }
}