package com.iedev.pdftoolkit.job.service;

import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import com.iedev.pdftoolkit.job.repository.ConversionJobRepository;
import jakarta.persistence.EntityNotFoundException;
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

    @Transactional
    public Optional<ConversionJobEntity> claimNextJob(Duration leaseDuration) {
        return conversionJobRepository.findNextPendingJobForUpdate()
                .map(job -> {
                    job.markAsProcessing(leaseDuration);
                    return job;
                });
    }

    @Transactional
    public void completeJob(UUID jobId) {
        ConversionJobEntity job = conversionJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Conversion job not found: " + jobId
                ));

        job.markAsCompleted();
    }

    @Transactional
    public void failJob(UUID jobId, String errorMessage) {
        ConversionJobEntity job = conversionJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Conversion job not found: " + jobId
                ));

        job.markAsFailed(errorMessage);
    }

    @Transactional
    public void retryOrFailJob(
            UUID jobId,
            String errorMessage,
            int maxAttempts,
            Duration initialDelay,
            Duration maxDelay
    ) {
        ConversionJobEntity job = conversionJobRepository.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Conversion job not found: " + jobId
                ));

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
}