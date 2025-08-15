package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.OrganizerToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizerTokenRepository extends JpaRepository<OrganizerToken, Long> {

    /**
     * Find all tokens for a specific event
     */
    List<OrganizerToken> findByEvent(Event event);

    /**
     * Find all active tokens for a specific event
     */
    List<OrganizerToken> findByEventAndIsActiveTrue(Event event);

    /**
     * Find token by token string
     */
    Optional<OrganizerToken> findByToken(String token);

    /**
     * Find active token by token string
     */
    Optional<OrganizerToken> findByTokenAndIsActiveTrue(String token);

    /**
     * Find all tokens for a specific event and organizer
     */
    List<OrganizerToken> findByEventAndOrganizerName(Event event, String organizerName);

    /**
     * Find all tokens for a specific event and organizer email
     */
    List<OrganizerToken> findByEventAndOrganizerEmail(Event event, String organizerEmail);

    /**
     * Find all expired tokens
     */
    @Query("SELECT ot FROM OrganizerToken ot WHERE ot.expiresAt < :now")
    List<OrganizerToken> findExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * Find all active tokens that haven't expired
     */
    @Query("SELECT ot FROM OrganizerToken ot WHERE ot.isActive = true AND ot.expiresAt > :now")
    List<OrganizerToken> findActiveValidTokens(@Param("now") LocalDateTime now);

    /**
     * Count active tokens for an event
     */
    long countByEventAndIsActiveTrue(Event event);

    /**
     * Find venue-specific tokens (assuming venue info is stored in organizer name)
     */
    @Query("SELECT ot FROM OrganizerToken ot WHERE ot.event = :event AND ot.organizerName LIKE %:venueName%")
    List<OrganizerToken> findByEventAndVenueName(@Param("event") Event event, @Param("venueName") String venueName);
}