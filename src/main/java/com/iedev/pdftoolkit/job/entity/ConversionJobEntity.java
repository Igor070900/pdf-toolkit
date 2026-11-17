package com.iedev.pdftoolkit.job.entity;

import com.iedev.pdftoolkit.job.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "conversion_jobs")
public class ConversionJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID jobId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "input_bucket", nullable = false)
    private String inputBucket;

    @Column(name = "input_object_key", nullable = false)
    private String inputObjectKey;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    public ConversionJobEntity(
            UUID eventId,
            String inputBucket,
            String inputObjectKey,
            String fileName
    ) {
        this.eventId = eventId;
        this.inputBucket = inputBucket;
        this.inputObjectKey = inputObjectKey;
        this.fileName = fileName;
    }

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void markAsProcessing(Duration leaseDuration) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = JobStatus.PROCESSING;
        this.attemptCount++;
        this.startedAt = now;
        this.updatedAt = now;
        this.lockedUntil = now.plus(leaseDuration);
        this.nextAttemptAt = null;
    }

    public void markAsCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now();
        this.updatedAt = this.completedAt;
        this.errorMessage = null;
        this.lockedUntil = null;
        this.nextAttemptAt = null;
    }

    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.completedAt = OffsetDateTime.now();
        this.updatedAt = this.completedAt;
        this.errorMessage = errorMessage;
        this.lockedUntil = null;
        this.nextAttemptAt = null;
    }
}