package nz.etu.voting.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.SyncProgress;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.SyncProgressRepository;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SyncTaskConsumer {

    private final InformerSyncService informerSyncService;
    private final SyncProgressRepository syncProgressRepository;
    private final EventRepository eventRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.sync}")
    public void processSyncTask(Map<String, Object> syncData) {
        String syncId = (String) syncData.get("syncId");
        String syncType = (String) syncData.get("syncType");

        log.info("Processing sync task: syncId={}, type={}", syncId, syncType);

        // Update progress to IN_PROGRESS
        SyncProgress progress = syncProgressRepository.findBySyncId(syncId)
                .orElseThrow(() -> new RuntimeException("Sync progress not found: " + syncId));

        progress.setStatus(SyncProgress.SyncStatus.IN_PROGRESS);
        progress.setStartTime(LocalDateTime.now());
        syncProgressRepository.save(progress);

        try {
            switch (syncType) {
                case "EMAIL_MEMBERS":
                    processSyncEmailMembers(syncData, progress);
                    break;
                case "SMS_MEMBERS":
                    processSyncSmsMembers(syncData, progress);
                    break;
                case "ALL":
                    processSyncAll(syncData, progress);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown sync type: " + syncType);
            }

            // Mark as completed
            progress.setStatus(SyncProgress.SyncStatus.COMPLETED);
            progress.setEndTime(LocalDateTime.now());
            progress.setMessage("Sync completed successfully");

        } catch (Exception e) {
            log.error("Sync task failed: syncId={}, error={}", syncId, e.getMessage(), e);
            progress.setStatus(SyncProgress.SyncStatus.FAILED);
            progress.setEndTime(LocalDateTime.now());
            progress.setMessage("Sync failed: " + e.getMessage());
        }

        syncProgressRepository.save(progress);
        log.info("Sync task completed: syncId={}, status={}", syncId, progress.getStatus());
    }

    private void processSyncEmailMembers(Map<String, Object> syncData, SyncProgress progress) {
        String url = (String) syncData.get("url");
        Long eventId = ((Number) syncData.get("eventId")).longValue();

        log.info("Starting EMAIL_MEMBERS sync for eventId={}", eventId);

        // Create a custom sync service that updates progress
        InformerSyncProgressWrapper wrapper = new InformerSyncProgressWrapper(
                informerSyncService, syncProgressRepository, progress
        );

        wrapper.syncEmailMembersWithProgress(url);
    }

    private void processSyncSmsMembers(Map<String, Object> syncData, SyncProgress progress) {
        String url = (String) syncData.get("url");
        Long eventId = ((Number) syncData.get("eventId")).longValue();

        log.info("Starting SMS_MEMBERS sync for eventId={}", eventId);

        InformerSyncProgressWrapper wrapper = new InformerSyncProgressWrapper(
                informerSyncService, syncProgressRepository, progress
        );

        wrapper.syncSmsMembersWithProgress(url);
    }

    private void processSyncAll(Map<String, Object> syncData, SyncProgress progress) {
        log.info("Starting ALL data sources sync");

        // First sync email members
        String emailUrl = (String) syncData.get("emailUrl");
        String smsUrl = (String) syncData.get("smsUrl");

        InformerSyncProgressWrapper wrapper = new InformerSyncProgressWrapper(
                informerSyncService, syncProgressRepository, progress
        );

        // Update progress message
        progress.setMessage("Syncing email members...");
        syncProgressRepository.save(progress);

        wrapper.syncEmailMembersWithProgress(emailUrl);

        progress.setMessage("Syncing SMS members...");
        syncProgressRepository.save(progress);

        wrapper.syncSmsMembersWithProgress(smsUrl);
    }

    /**
     * Wrapper class to add progress tracking to sync operations
     */
    private static class InformerSyncProgressWrapper {
        private final InformerSyncService syncService;
        private final SyncProgressRepository progressRepository;
        private final SyncProgress progress;

        public InformerSyncProgressWrapper(InformerSyncService syncService,
                                           SyncProgressRepository progressRepository,
                                           SyncProgress progress) {
            this.syncService = syncService;
            this.progressRepository = progressRepository;
            this.progress = progress;
        }

        public void syncEmailMembersWithProgress(String url) {
            // Call the existing sync method
            // The actual progress tracking would need to be implemented
            // inside InformerSyncService by injecting progress updates
            syncService.syncEmailMembersDirectlyToEventMember(url);

            // For now, just mark as complete
            progress.setProcessedRecords(progress.getTotalRecords());
            progressRepository.save(progress);
        }

        public void syncSmsMembersWithProgress(String url) {
            syncService.syncSmsMembersDirectlyToEventMember(url);
            progress.setProcessedRecords(progress.getTotalRecords());
            progressRepository.save(progress);
        }
    }
}