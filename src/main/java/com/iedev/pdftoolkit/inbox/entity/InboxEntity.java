package com.iedev.pdftoolkit.inbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class InboxEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    public InboxEntity(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = OffsetDateTime.now();
    }
}