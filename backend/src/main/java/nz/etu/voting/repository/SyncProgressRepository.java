package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.SyncProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface SyncProgressRepository extends JpaRepository<SyncProgress, Long> {

    Optional<SyncProgress> findBySyncId(String syncId);

    List<SyncProgress> findByStatusOrderByStartTimeDesc(SyncProgress.SyncStatus status);

    List<SyncProgress> findTop10ByOrderByStartTimeDesc();

    List<SyncProgress> findByEventIdOrderByStartTimeDesc(Long eventId);
}