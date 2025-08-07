package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByEventMember(EventMember eventMember);

    List<NotificationLog> findByMemberOrderBySentTimeDesc(Member member);

    List<NotificationLog> findByEventMemberOrderBySentTimeDesc(EventMember eventMember);

    List<NotificationLog> findByEvent(Event event);

    List<NotificationLog> findByEventAndNotificationType(Event event, NotificationLog.NotificationType type);

    @Query("SELECT COUNT(nl) FROM NotificationLog nl WHERE nl.event = :event AND nl.notificationType = :type AND nl.isSuccessful = true")
    Long countSuccessfulByEventAndType(@Param("event") Event event, @Param("type") NotificationLog.NotificationType type);

    @Query("SELECT COUNT(nl) FROM NotificationLog nl WHERE nl.event = :event AND nl.notificationType = :type AND nl.isSuccessful = false")
    Long countFailedByEventAndType(@Param("event") Event event, @Param("type") NotificationLog.NotificationType type);

    @Query("SELECT nl FROM NotificationLog nl WHERE nl.sentTime BETWEEN :startTime AND :endTime ORDER BY nl.sentTime DESC")
    List<NotificationLog> findBySentTimeBetween(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<NotificationLog> findByRecipientAndNotificationTypeAndSentTimeAfter(
            String recipient,
            NotificationLog.NotificationType notificationType,
            LocalDateTime sentTime);

    List<NotificationLog> findByRecipientAndNotificationTypeOrderBySentTimeDesc(
            String recipient,
            NotificationLog.NotificationType notificationType);

    List<NotificationLog> findByRecipientOrderBySentTimeDesc(String recipient);

    List<NotificationLog> findByNotificationTypeOrderBySentTimeDesc(NotificationLog.NotificationType notificationType);

}