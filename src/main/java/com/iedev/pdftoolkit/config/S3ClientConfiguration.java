package com.iedev.pdftoolkit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Slf4j
@Configuration
public class S3ClientConfiguration {

    @Bean
    public S3Client s3Client(
            @Value("${s3.endpoint}") URI uri,
            @Value("${s3.access-key}") String accessKey,
            @Value("${s3.secret-key}") String secretKey,
            @Value("${s3.region}") String region
    ) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        S3Client s3Client = S3Client.builder()
                .endpointOverride(uri)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region))
                .serviceConfiguration(
                        S3Configuration.builder().
                                pathStyleAccessEnabled(true)
                                .build())
                .build();
        log.info("S3Client bean successfully created");

        return s3Client;
    }

// временный для проверки
//    @Bean
//    public CommandLineRunner s3ConnectionTest(S3Client s3Client) {
//        return args -> {
////            var response = s3Client.listBuckets();
//////            log.info("Successfully connected to RustFS. Buckets: {}", response.buckets());
//            var bucketName = "pdf-input";
//
//            s3Client.createBucket(
//                    CreateBucketRequest.builder()
//                            .bucket(bucketName)
//                            .build()
//            );
//            log.info("Bucket {} successfully created", bucketName);
//
//            var buckets = s3Client.listBuckets().buckets();
//            log.info("Available buckets: {}", buckets);
//        };
//    }
}
