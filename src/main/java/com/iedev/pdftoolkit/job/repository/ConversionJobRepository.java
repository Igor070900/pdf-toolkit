package com.iedev.pdftoolkit.job.repository;

import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConversionJobRepository extends JpaRepository<ConversionJobEntity, UUID> {
}
