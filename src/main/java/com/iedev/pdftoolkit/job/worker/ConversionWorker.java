package com.iedev.pdftoolkit.job.worker;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.converter.FileConversionService;
import com.iedev.pdftoolkit.job.service.JobService;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import com.iedev.pdftoolkit.job.worker.exception.RetryableProcessingException;
import com.iedev.pdftoolkit.s3.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversionWorker {

    private final JobService jobService;
    private final S3StorageService s3StorageService;
    private final FileConversionService fileConversionService;
    private final WorkerProperties workerProperties;

    public void processNextJob() {
        jobService.claimNextJob(workerProperties.getLeaseDuration()).ifPresent(
                job -> {
                    Path tempDirectory = null;
                    try {
                        tempDirectory = Files.createTempDirectory("pdf-toolkit-");
                        String fileName = job.getFileName();
                        if (fileName.isBlank() || fileName == null || fileName.contains("/") || fileName.contains("\\")) {
                            throw new PermanentProcessingException("Invalid file name");
                        }
                        Path source = tempDirectory.resolve(fileName);

                        s3StorageService.download(
                                job.getInputBucket(),
                                job.getInputObjectKey(),
                                source
                        );

                        String baseName = fileName
                                .substring(0, fileName.lastIndexOf('.'));
                        Path destination = tempDirectory.resolve(baseName + ".pdf");

                        fileConversionService.convert(
                                fileName,
                                source,
                                destination
                        );

                        String outputObjectKey = "converted/" + job.getJobId() + "/" + baseName + ".pdf";

                        s3StorageService.upload(
                                job.getInputBucket(),
                                outputObjectKey,
                                destination
                        );

                        jobService.completeJob(job.getJobId());
                    } catch (PermanentProcessingException exception) {
                        log.error("Permanent failure for job {}", job.getJobId(), exception);
                        jobService.failJob(job.getJobId(), exception.getMessage());
                    } catch (RetryableProcessingException exception) {
                        log.warn("Retryable failure for job {}", job.getJobId(), exception);
                        retryOrFail(job.getJobId(), exception.getMessage());
                    } catch (IOException exception) {
                        log.warn("I/O failure for job {}", job.getJobId(), exception);
                        retryOrFail(job.getJobId(), exception.getMessage());
                    } finally {
                        if (tempDirectory != null) {
                            try {
                                deleteTempDirectory(tempDirectory);
                            } catch (IOException exception) {
                                log.warn("Failed to delete temporary directory: {}", tempDirectory, exception);
                            }
                        }
                    }
                }
        );
    }

    private void deleteTempDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void retryOrFail(UUID jobId, String errorMessage) {
        jobService.retryOrFailJob(
                jobId,
                errorMessage,
                workerProperties.getMaxAttempts(),
                workerProperties.getRetryInitialDelay(),
                workerProperties.getRetryMaxDelay()
        );
    }
}