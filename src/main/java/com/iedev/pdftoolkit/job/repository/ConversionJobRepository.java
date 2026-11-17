package com.iedev.pdftoolkit.job.repository;

import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
