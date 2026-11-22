package com.iedev.pdftoolkit.job.service;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.job.JobStatus;
import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import com.iedev.pdftoolkit.job.exception.LeaseLostException;
import com.iedev.pdftoolkit.job.repository.ConversionJobRepository;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EnableConfigurationProperties(WorkerProperties.class)
@Import(JobService.class)
public class JobServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18.6");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JobService jobService;

    @Autowired
    private ConversionJobRepository conversionJobRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void shouldRenewLease() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        UUID jobId = claimedJob.getJobId();
        UUID lockToken = claimedJob.getLockToken();
        OffsetDateTime originalLockedUntil = claimedJob.getLockedUntil();

        boolean renewed = jobService.renewLease(
                jobId,
                lockToken,
                Duration.ofMinutes(30)
        );

        // Assert
        assertTrue(renewed);

        entityManager.clear();

        ConversionJobEntity updatedJob = conversionJobRepository.findById(jobId)
                .orElseThrow();

        assertTrue(updatedJob.getLockedUntil().isAfter(originalLockedUntil));
    }

    @Test
    public void shouldNotRenewLeaseWithInvalidToken() {
        // Arrange
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        boolean renewed = jobService.renewLease(
                claimedJob.getJobId(),
                UUID.randomUUID(),
                Duration.ofMinutes(30)
        );

        assertFalse(renewed);
    }

    @Test
    public void shouldCompleteJob() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        jobService.completeJob(claimedJob.getJobId(), claimedJob.getLockToken());

        entityManager.clear();

        ConversionJobEntity completedJob = conversionJobRepository
                .findById(claimedJob.getJobId())
                .orElseThrow();

        assertEquals(JobStatus.COMPLETED, completedJob.getStatus());
        assertNotNull(completedJob.getCompletedAt());
        assertNull(completedJob.getLockedUntil());
        assertNull(completedJob.getLockToken());
    }

    @Test
    public void shouldThrowExceptionWhenCompletingJobWithInvalidToken() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        assertThrows(
                LeaseLostException.class,
                () -> jobService.completeJob(claimedJob.getJobId(), UUID.randomUUID())
        );
    }

    @Test
    public void shouldScheduleRetryWhenAttemptsNotExhausted() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        jobService.retryOrFailJob(
                claimedJob.getJobId(),
                claimedJob.getLockToken(),
                "Temporary storage error",
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5)
        );

        entityManager.clear();

        ConversionJobEntity retriedJob = conversionJobRepository
                .findById(claimedJob.getJobId())
                .orElseThrow();

        assertEquals(JobStatus.PENDING, retriedJob.getStatus());
        assertEquals(1, retriedJob.getAttemptCount());
        assertEquals("Temporary storage error", retriedJob.getErrorMessage());
        assertNotNull(retriedJob.getNextAttemptAt());
        assertNull(retriedJob.getCompletedAt());
        assertNull(retriedJob.getLockedUntil());
        assertNull(retriedJob.getLockToken());
    }

    @Test
    public void shouldFailJobWhenAttemptsExhausted() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        jobService.retryOrFailJob(
                claimedJob.getJobId(),
                claimedJob.getLockToken(),
                "Maximum attempts reached",
                1,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5)
        );

        entityManager.clear();

        ConversionJobEntity failedJob = conversionJobRepository
                .findById(claimedJob.getJobId())
                .orElseThrow();

        assertEquals(JobStatus.FAILED, failedJob.getStatus());
        assertNotNull(failedJob.getCompletedAt());
        assertEquals("Maximum attempts reached", failedJob.getErrorMessage());
        assertNull(failedJob.getLockedUntil());
        assertNull(failedJob.getLockToken());
        assertNull(failedJob.getNextAttemptAt());
    }

    @Test
    public void shouldReclaimJobWhenLeaseExpired() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity firstClaim = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        UUID oldLockToken = firstClaim.getLockToken();

        jdbcTemplate.update("""
            UPDATE conversion_jobs
            SET locked_until = NOW() - INTERVAL '1 minute'
            WHERE job_id = ?
            """, firstClaim.getJobId());

        entityManager.clear();

        ConversionJobEntity secondClaim = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        assertEquals(firstClaim.getJobId(), secondClaim.getJobId());
        assertEquals(2, secondClaim.getAttemptCount());
        assertNotEquals(oldLockToken, secondClaim.getLockToken());
        assertEquals(JobStatus.PROCESSING, secondClaim.getStatus());
        assertTrue(secondClaim.getLockedUntil().isAfter(OffsetDateTime.now()));
    }

    @Test
    public void shouldFailJobWhenLeaseExpiredAndAttemptsExhausted() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        jdbcTemplate.update("""
            UPDATE conversion_jobs
            SET locked_until = NOW() - INTERVAL '1 minute'
            WHERE job_id = ?
            """, claimedJob.getJobId());

        entityManager.clear();

        Optional<ConversionJobEntity> nextJob =
                jobService.claimNextJob(Duration.ofMinutes(15));

        assertTrue(nextJob.isEmpty());

        entityManager.clear();

        ConversionJobEntity failedJob = conversionJobRepository
                .findById(claimedJob.getJobId())
                .orElseThrow();

        assertEquals(JobStatus.FAILED, failedJob.getStatus());
        assertNotNull(failedJob.getCompletedAt());
        assertNull(failedJob.getLockedUntil());
        assertNull(failedJob.getLockToken());
    }

    @Test
    public void shouldThrowExceptionWhenRetryingJobWithInvalidToken() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        assertThrows(
                LeaseLostException.class,
                () -> jobService.retryOrFailJob(
                        claimedJob.getJobId(),
                        UUID.randomUUID(),
                        "Temporary error",
                        3,
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(5)
                )
        );
    }

    @Test
    public void shouldThrowExceptionWhenFailingJobWithInvalidToken() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        assertThrows(
                LeaseLostException.class,
                () -> jobService.failJob(
                        claimedJob.getJobId(),
                        UUID.randomUUID(),
                        "Invalid file"
                )
        );
    }

    @Test
    public void shouldRejectCompletionWhenLeaseExpired() {
        ConversionJobEntity job = new ConversionJobEntity(
                UUID.randomUUID(),
                "test-bucket",
                "uploads/sample.txt",
                "sample.txt"
        );
        conversionJobRepository.saveAndFlush(job);

        ConversionJobEntity claimedJob = jobService.claimNextJob(Duration.ofMinutes(15))
                .orElseThrow();

        jdbcTemplate.update("""
            UPDATE conversion_jobs
            SET locked_until = NOW() - INTERVAL '1 minute'
            WHERE job_id = ?
            """, claimedJob.getJobId());

        entityManager.clear();

        assertThrows(
                LeaseLostException.class,
                () -> jobService.completeJob(
                        claimedJob.getJobId(),
                        claimedJob.getLockToken()
                )
        );
    }
}