package edu.cit.franza.notification;

import java.time.OffsetDateTime;

/**
 * External shape for GET /api/notifications. Public even though its
 * sibling types (NotificationEntity, NotificationType) are
 * package-private - this record is the module's deliberate REST
 * contract, not an internal detail.
 */
public record NotificationSummary(Long notificationId, String type, String message, OffsetDateTime createdAt) {
}
