package edu.cit.franza.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Package-private: purely an implementation detail of how the
 * Notification module stores its activity feed. Nothing outside this
 * package needs to know the notifications table's shape.
 */
@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected NotificationEntity() {
        // required by JPA
    }

    NotificationEntity(NotificationType type, String message) {
        this.type = type;
        this.message = message;
        this.createdAt = OffsetDateTime.now();
    }

    Long getNotificationId() {
        return notificationId;
    }

    NotificationType getType() {
        return type;
    }

    String getMessage() {
        return message;
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
