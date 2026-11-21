package com.iedev.pdftoolkit.job.worker;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.converter.FileConversionService;
import com.iedev.pdftoolkit.job.exception.LeaseLostException;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversionWorker {

    private final JobService jobService;
    private final S3StorageService s3StorageService;
    private final FileConversionService fileConversionService;
    private final WorkerProperties workerProperties;
    private final ScheduledExecutorService workerScheduler;

    public void processNextJob() {
        jobService.claimNextJob(workerProperties.getLeaseDuration()).ifPresent(
                job -> {
                    UUID lockToken = job.getLockToken();
                    AtomicBoolean leaseLost = new AtomicBoolean(false);
                    ScheduledFuture<?> heartbeat = startHeartbeat(job.getJobId(), lockToken, leaseLost);
                    Path tempDirectory = null;
                    try {
                        tempDirectory = Files.createTempDirectory("pdf-toolkit-");
                        checkLease(leaseLost);
                        String fileName = job.getFileName();
                        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
                            throw new PermanentProcessingException("Invalid file name");
                        }

                        int extensionIndex = fileName.lastIndexOf('.');
                        if (extensionIndex <= 0 || extensionIndex == fileName.length() - 1) {
                            throw new PermanentProcessingException("Invalid file name");
                        }

                        Path source = tempDirectory.resolve(fileName);

                        s3StorageService.download(
                                job.getInputBucket(),
                                job.getInputObjectKey(),
                                source
                        );
                        checkLease(leaseLost);

                        String baseName = fileName.substring(0, extensionIndex);
                        Path destination = tempDirectory.resolve(baseName + ".pdf");

                        fileConversionService.convert(
                                fileName,
                                source,
                                destination
                        );
                        checkLease(leaseLost);

                        String outputObjectKey = "converted/" + job.getJobId() + "/" + baseName + ".pdf";

                        checkLease(leaseLost);
                        s3StorageService.upload(
                                job.getInputBucket(),
                                outputObjectKey,
                                destination
                        );

                        checkLease(leaseLost);
                        jobService.completeJob(job.getJobId(), lockToken);
                    } catch (LeaseLostException exception) {
                        log.warn("Lease lost for job {}", job.getJobId());
                    } catch (PermanentProcessingException exception) {
                        log.error("Permanent failure for job {}", job.getJobId(), exception);
                        handleLeaseLost(job.getJobId(), () ->
                                jobService.failJob(job.getJobId(), lockToken, exception.getMessage())
                        );
                    } catch (RetryableProcessingException exception) {
                        log.warn("Retryable failure for job {}", job.getJobId(), exception);
                        handleLeaseLost(job.getJobId(), () ->
                                retryOrFail(job.getJobId(), lockToken, exception.getMessage())
                        );
                    } catch (IOException exception) {
                        log.warn("I/O failure for job {}", job.getJobId(), exception);
                        handleLeaseLost(job.getJobId(), () ->
                                retryOrFail(job.getJobId(), lockToken, exception.getMessage())
                        );
                    } finally {
                        heartbeat.cancel(false);
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

    private void retryOrFail(UUID jobId, UUID lockToken, String errorMessage) {
        jobService.retryOrFailJob(
                jobId,
                lockToken,
                errorMessage,
                workerProperties.getMaxAttempts(),
                workerProperties.getRetryInitialDelay(),
                workerProperties.getRetryMaxDelay()
        );
    }

    private ScheduledFuture<?> startHeartbeat(
            UUID jobId,
            UUID lockToken,
            AtomicBoolean leaseLost
    ) {
        return workerScheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        boolean renewed = jobService.renewLease(
                                jobId,
                                lockToken,
                                workerProperties.getLeaseDuration()
                        );
                        if (!renewed) {
                            leaseLost.set(true);
                            log.warn("Failed to renew lease for job {}", jobId);
                        }
                    } catch (Exception exception) {
                        leaseLost.set(true);
                        log.error("Error renewing lease for job {}", jobId, exception);
                    }
                },
                workerProperties.getHeartbeatInterval().toMillis(),
                workerProperties.getHeartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private void checkLease(AtomicBoolean leaseLost) {
        if (leaseLost.get()) {
            throw new LeaseLostException("Worker lost job lease");
        }
    }

    private void handleLeaseLost(UUID jobId, Runnable action) {
        try {
            action.run();
        } catch (LeaseLostException exception) {
            log.warn("Lease lost for job {}", jobId);
        }
    }
}