package com.iedev.pdftoolkit.inbox.repository;

import com.iedev.pdftoolkit.inbox.entity.InboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxRepository extends JpaRepository<InboxEntity, UUID> {

    boolean existsByEventId(UUID eventId);
}