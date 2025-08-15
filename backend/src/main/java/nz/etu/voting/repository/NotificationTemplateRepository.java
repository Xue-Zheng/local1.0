package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTemplateCode(String templateCode);

    List<NotificationTemplate> findByIsActiveTrueOrderByName();

    List<NotificationTemplate> findByTemplateTypeAndIsActiveTrue(NotificationTemplate.TemplateType templateType);

    @Query("SELECT nt FROM NotificationTemplate nt WHERE nt.isActive = true AND " +
            "(nt.templateType = :type OR nt.templateType = 'BOTH') ORDER BY nt.name")
    List<NotificationTemplate> findActiveTemplatesByType(@Param("type") NotificationTemplate.TemplateType type);
}