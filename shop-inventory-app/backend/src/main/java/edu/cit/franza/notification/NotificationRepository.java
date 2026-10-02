package edu.cit.franza.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Package-private, in the same package as NotificationEntity and the
 * listener/controller that use it - consistent with how the Inventory
 * module keeps its repository compiler-invisible outside its own
 * package.
 */
interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findAllByOrderByNotificationIdDesc();
}
