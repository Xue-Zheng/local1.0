package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByName(String name);

    //    查找活跃事件
    List<Event> findByIsActiveTrue();

    //    根据事件类型查找活跃事件
    List<Event> findByEventTypeAndIsActiveTrue(Event.EventType eventType);

    //    根据事件类型查找所有事件
    List<Event> findByEventType(Event.EventType eventType);

    @Query("SELECT e FROM Event e WHERE e.syncStatus IN ('PENDING', 'FAILED') AND e.isActive = true ORDER BY e.createdAt ASC")
    List<Event> findEventsToSync();

    @Query("SELECT e FROM Event e WHERE (e.syncStatus = 'PENDING' OR " +
            "(e.syncStatus = 'FAILED' AND e.lastSyncTime < :retryAfter)) AND e.isActive = true")
    List<Event> findEventsToRetry(@Param("retryAfter") LocalDateTime retryAfter);

    Optional<Event> findByEventCode(String eventCode);

    Optional<Event> findByDatasetId(String datasetId);

    List<Event> findBySyncStatus(Event.SyncStatus syncStatus);

    // 限制只查询最近20个事件
    List<Event> findTop20ByIsActiveTrueOrderByEventDateDesc();

    // 限制只查询最近10个即将到来的事件
    @Query("SELECT e FROM Event e WHERE e.isActive = true AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findTop10UpcomingEvents(@Param("now") LocalDateTime now);

    @Query("SELECT e FROM Event e WHERE e.isActive = true AND e.registrationOpen = true ORDER BY e.eventDate ASC")
    List<Event> findActiveEventsWithOpenRegistration();

    @Query("SELECT e.syncStatus, COUNT(e) FROM Event e WHERE e.isActive = true GROUP BY e.syncStatus")
    List<Object[]> countByStatus();

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event.id = :eventId AND em.hasRegistered = true")
    Long countRegisteredMembersByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event.id = :eventId AND em.isAttending = true")
    Long countAttendingMembersByEventId(@Param("eventId") Long eventId);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event.id = :eventId AND em.isSpecialVote = true")
    Long countSpecialVoteMembersByEventId(@Param("eventId") Long eventId);

    Optional<Event> findByEventCodeAndIsActiveTrue(String eventCode);
}