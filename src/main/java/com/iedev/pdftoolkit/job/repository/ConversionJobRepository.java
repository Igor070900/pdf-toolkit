package com.iedev.pdftoolkit.job.repository;

import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversionJobRepository extends JpaRepository<ConversionJobEntity, UUID> {

    @Query(value = """
            select *
            from conversion_jobs
            where (
                status = 'PENDING'
                and (next_attempt_at is null or next_attempt_at <= NOW())
            )
            or (
                status = 'PROCESSING'
                and (locked_until is null or locked_until <= NOW())
            )
            order by created_at, job_id
            limit 1
            for update skip locked""", nativeQuery = true)
    Optional<ConversionJobEntity> findNextPendingJobForUpdate();

    @Modifying
    @Query(value = """
            UPDATE conversion_jobs
            SET status = 'FAILED',
                error_message = 'Maximum processing attempts exceeded',
                completed_at = NOW(),
                updated_at = NOW(),
                locked_until = NULL,
                next_attempt_at = NULL
            WHERE status = 'PROCESSING'
              AND attempt_count >= :maxAttempts
              AND (locked_until IS NULL OR locked_until <= NOW())
            """, nativeQuery = true)
    int failExhaustedJobs(@Param("maxAttempts") int maxAttempts);
}
