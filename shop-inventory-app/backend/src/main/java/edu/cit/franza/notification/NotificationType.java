package edu.cit.franza.notification;

/**
 * Package-private: an implementation/API detail of the Notification
 * module's own storage and response shape, not something other modules
 * need to know about (they publish domain events, not notification
 * types).
 */
enum NotificationType {
    ORDER_CONFIRMED,
    ORDER_REJECTED,
    LOW_STOCK
}
