package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventTemplateRepository extends JpaRepository<EventTemplate, Long> {

    Optional<EventTemplate> findByTemplateName(String templateName);

    List<EventTemplate> findByEventType(Event.EventType eventType);

    List<EventTemplate> findByIsActiveTrue();

    Optional<EventTemplate> findByEventTypeAndIsDefaultTemplateTrue(Event.EventType eventType);

    @Query("SELECT t FROM EventTemplate t WHERE t.isActive = true ORDER BY t.eventType, t.templateName")
    List<EventTemplate> findAllActiveTemplatesOrdered();

    @Query("SELECT COUNT(e) FROM Event e WHERE e.eventTemplate.id = :templateId")
    Long countEventsByTemplateId(@Param("templateId") Long templateId);
}