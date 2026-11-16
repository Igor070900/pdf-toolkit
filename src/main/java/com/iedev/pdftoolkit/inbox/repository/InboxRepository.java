package com.iedev.pdftoolkit.inbox.repository;

import com.iedev.pdftoolkit.inbox.entity.InboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InboxRepository extends JpaRepository<InboxEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO inbox (event_id, processed_at)
            VALUES (:eventId, NOW())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId);

}