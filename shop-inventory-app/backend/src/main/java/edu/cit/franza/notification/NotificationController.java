package edu.cit.franza.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
class NotificationController {

    private final NotificationRepository notificationRepository;

    NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/api/notifications")
    public List<NotificationSummary> listNotifications() {
        return notificationRepository.findAllByOrderByNotificationIdDesc().stream()
                .map(n -> new NotificationSummary(
                        n.getNotificationId(),
                        n.getType().name(),
                        n.getMessage(),
                        n.getCreatedAt()))
                .toList();
    }
}
