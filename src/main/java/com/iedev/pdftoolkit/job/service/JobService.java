package com.iedev.pdftoolkit.job.service;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import com.iedev.pdftoolkit.job.exception.LeaseLostException;
import com.iedev.pdftoolkit.job.repository.ConversionJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final ConversionJobRepository conversionJobRepository;
    private final WorkerProperties workerProperties;

    @Transactional
    public Optional<ConversionJobEntity> claimNextJob(Duration leaseDuration) {
        conversionJobRepository.failExhaustedJobs(workerProperties.getMaxAttempts());
        return conversionJobRepository.findNextPendingJobForUpdate()
                .map(job -> {
                    job.markAsProcessing(leaseDuration, UUID.randomUUID());
                    return job;
                });
    }

    @Transactional
    public void completeJob(UUID jobId, UUID lockToken) {
        ConversionJobEntity job = conversionJobRepository.findActiveJobForUpdate(jobId, lockToken)
                .orElseThrow(() -> new LeaseLostException(jobId));

        job.markAsCompleted();
    }

    @Transactional
    public void failJob(UUID jobId, UUID lockToken, String errorMessage) {
        ConversionJobEntity job = conversionJobRepository.findActiveJobForUpdate(jobId, lockToken)
                .orElseThrow(() -> new LeaseLostException(jobId));

        job.markAsFailed(errorMessage);
    }

    @Transactional
    public void retryOrFailJob(
            UUID jobId,
            UUID lockToken,
            String errorMessage,
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay
    ) {
        ConversionJobEntity job = conversionJobRepository.findActiveJobForUpdate(jobId, lockToken)
                .orElseThrow(() -> new LeaseLostException(jobId));

        if (job.getAttemptCount() >= maxAttempts) {
            job.markAsFailed(errorMessage);
            return;
        }

        long multiplier = 1L << (job.getAttemptCount() - 1);
        Duration retryDelay = initialDelay.multipliedBy(multiplier);
        if (retryDelay.compareTo(maxDelay) > 0) {
            retryDelay = maxDelay;
        }

        job.markAsRetry(errorMessage, OffsetDateTime.now().plus(retryDelay));
    }

    @Transactional
    public boolean renewLease(UUID jobId, UUID lockToken, Duration leaseDuration) {
        int updated = conversionJobRepository.renewLease(
                jobId,
                lockToken,
                leaseDuration.toMillis()
        );
        return updated == 1;
    }
}