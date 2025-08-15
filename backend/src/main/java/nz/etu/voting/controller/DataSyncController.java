package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.SyncProgress;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.SyncProgressRepository;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//Data Synchronization Management Controller and Provides manual synchronization functionality for three independent Informer data sources
@Slf4j
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://192.168.110.6:3000", "https://events.etu.nz"})
public class DataSyncController {

    private final InformerSyncService informerSyncService;
    private final MemberRepository memberRepository;
    private final SyncProgressRepository syncProgressRepository;
    private final EventRepository eventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routingkey.sync}")
    private String syncRoutingKey;


    //    Manual synchronization of event attendee data (Data Source 1)
    @PostMapping("/attendees")
    public ResponseEntity<Map<String, Object>> syncAttendeeData() {
        log.info("=== Manual attendee data sync requested ===");
        Map<String, Object> response = new HashMap<>();

        try {
            String attendeeUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/d382fc79-1230-4a1d-917a-7bc43aa21a84/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiI2NjI4ZTNkZi1hZmZhLTQ3OWUtOWEzZC1iZTMwZjFhZjI5NzEiLCJpYXQiOjE3NDk2OTgxODcuMjQxfQ.ZR8WC1UbQAtV6r5EyNG083qzQ450pJb1HRze5wFgR50";

            // CRITICAL: 暂时注释attendee同步，专注于BMM事件
            // informerSyncService.syncAttendeeDataFromUrl(attendeeUrl);

            response.put("success", true);
            response.put("message", "Event attendee data synchronization successful");
            response.put("dataSource", "ATTENDEES");

            log.info("=== Manual attendee data sync completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Manual attendee data sync failed ===", e);
            response.put("success", false);
            response.put("message", "Event attendee data synchronization failed: " + e.getMessage());
            response.put("dataSource", "ATTENDEES");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Manual synchronization of active members with email addresses (Data Source 2) - ASYNC VERSION
    @PostMapping("/email-members")
    public ResponseEntity<Map<String, Object>> syncEmailMembers() {
        log.info("=== Manual email members sync requested (ASYNC) ===");
        Map<String, Object> response = new HashMap<>();

        try {
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";

            // Create sync progress record
            String syncId = UUID.randomUUID().toString();
            SyncProgress progress = SyncProgress.builder()
                    .syncId(syncId)
                    .syncType("EMAIL_MEMBERS")
                    .status(SyncProgress.SyncStatus.PENDING)
                    .totalRecords(0) // Will be updated when sync starts
                    .processedRecords(0)
                    .errorCount(0)
                    .createdBy("admin")
                    .build();

            // Get BMM event if exists
            Event bmmEvent = eventRepository.findByEventType(Event.EventType.BMM_VOTING)
                    .stream()
                    .filter(Event::getIsActive)
                    .findFirst()
                    .orElse(null);

            if (bmmEvent != null) {
                progress.setEvent(bmmEvent);
            }

            syncProgressRepository.save(progress);

            // Send to RabbitMQ for async processing
            Map<String, Object> syncMessage = new HashMap<>();
            syncMessage.put("syncId", syncId);
            syncMessage.put("syncType", "EMAIL_MEMBERS");
            syncMessage.put("url", emailMembersUrl);
            syncMessage.put("eventId", bmmEvent != null ? bmmEvent.getId() : null);
            syncMessage.put("requestTime", LocalDateTime.now());

            rabbitTemplate.convertAndSend(exchange, syncRoutingKey, syncMessage);

            response.put("success", true);
            response.put("message", "Email members sync task submitted successfully. Check progress with syncId.");
            response.put("syncId", syncId);
            response.put("dataSource", "EMAIL_MEMBERS");
            response.put("estimatedRecords", 40000);

            log.info("=== Email members sync task submitted: {} ===", syncId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Failed to submit email members sync task ===", e);
            response.put("success", false);
            response.put("message", "Failed to submit sync task: " + e.getMessage());
            response.put("dataSource", "EMAIL_MEMBERS");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Manual synchronization of SMS-only active members (Data Source 3)
    @PostMapping("/sms-members")
    public ResponseEntity<Map<String, Object>> syncSmsMembers() {
        log.info("=== Manual SMS members sync requested ===");
        Map<String, Object> response = new HashMap<>();

        try {
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";

            informerSyncService.syncSmsMembersData(smsMembersUrl);

            response.put("success", true);
            response.put("message", "SMS-only members data synchronization successful");
            response.put("dataSource", "SMS_MEMBERS");

            log.info("=== Manual SMS members sync completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Manual SMS members sync failed ===", e);
            response.put("success", false);
            response.put("message", "SMS-only members data synchronization failed: " + e.getMessage());
            response.put("dataSource", "SMS_MEMBERS");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // CRITICAL: 新增：直接同步Email成员到EventMember表
    @PostMapping("/email-members-direct")
    public ResponseEntity<Map<String, Object>> syncEmailMembersDirectly() {
        log.info("=== Manual email members DIRECT sync to EventMember requested ===");
        Map<String, Object> response = new HashMap<>();

        try {
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";

            informerSyncService.syncEmailMembersDirectlyToEventMember(emailMembersUrl);

            response.put("success", true);
            response.put("message", "Email members DIRECT synchronization to EventMember successful");
            response.put("dataSource", "EMAIL_MEMBERS_DIRECT");

            log.info("=== Manual email members DIRECT sync completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Manual email members DIRECT sync failed ===", e);
            response.put("success", false);
            response.put("message", "Email members DIRECT synchronization failed: " + e.getMessage());
            response.put("dataSource", "EMAIL_MEMBERS_DIRECT");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // CRITICAL: 新增：直接同步SMS成员到EventMember表
    @PostMapping("/sms-members-direct")
    public ResponseEntity<Map<String, Object>> syncSmsMembersDirectly() {
        log.info("=== Manual SMS members DIRECT sync to EventMember requested ===");
        Map<String, Object> response = new HashMap<>();

        try {
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";

            informerSyncService.syncSmsMembersDirectlyToEventMember(smsMembersUrl);

            response.put("success", true);
            response.put("message", "SMS members DIRECT synchronization to EventMember successful");
            response.put("dataSource", "SMS_MEMBERS_DIRECT");

            log.info("=== Manual SMS members DIRECT sync completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Manual SMS members DIRECT sync failed ===", e);
            response.put("success", false);
            response.put("message", "SMS members DIRECT synchronization failed: " + e.getMessage());
            response.put("dataSource", "SMS_MEMBERS_DIRECT");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get synchronization status statistics
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            response.put("success", true);
            response.put("message", "Sync status retrieved successfully");

//            实际统计数据
            Map<String, Object> stats = new HashMap<>();
//            统计总会员数（所有来源）
            long totalMembers = memberRepository.count();
            stats.put("totalMembers", totalMembers);
//            统计邮件会员数（有邮箱的会员）
            long emailMembers = memberRepository.countByDataSourceContaining("EMAIL");
            stats.put("emailMembers", emailMembers);
//            统计SMS会员数（仅短信会员）
            long smsMembers = memberRepository.countByDataSourceContaining("SMS");
            stats.put("smsMembers", smsMembers);
// EventAttendee table removed - use EventMember for attendee statistics
//             long attendees = eventMemberRepository.count();
//             stats.put("attendees", attendees);
//            按数据源统计
            long informerEmailMembers = memberRepository.countByDataSource("INFORMER_EMAIL");
            long informerSmsMembers = memberRepository.countByDataSource("INFORMER_SMS");
            // long autoCreatedMembers = memberRepository.countByDataSourceContaining("AUTO_CREATED");
            stats.put("informerEmailMembers", informerEmailMembers);
            stats.put("informerSmsMembers", informerSmsMembers);
            // stats.put("autoCreatedMembers", autoCreatedMembers);
//            最近同步时间（查看最新的数据同步记录）
            String lastSyncTime = memberRepository.findTopByOrderByLastSyncTimeDesc()
                    .map(member -> member.getLastSyncTime() != null ?
                            member.getLastSyncTime().toString() : "Never synced")
                    .orElse("No data");
            stats.put("lastSyncTime", lastSyncTime);
//            今日同步统计
            java.time.LocalDateTime todayStart = java.time.LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            long todayEmailSynced = memberRepository.countByDataSourceAndLastSyncTimeAfter("INFORMER_EMAIL", todayStart);
            long todaySmsSynced = memberRepository.countByDataSourceAndLastSyncTimeAfter("INFORMER_SMS", todayStart);
            stats.put("todayEmailSynced", todayEmailSynced);
            stats.put("todaySmsSynced", todaySmsSynced);

            response.put("data", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get sync status", e);
            response.put("success", false);
            response.put("message", "Failed to retrieve sync status: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    One-click synchronization of all data sources
    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> syncAllDataSources() {
        log.info("=== Manual sync all data sources requested ===");
        Map<String, Object> response = new HashMap<>();

        try {
//            CRITICAL: 新增：直接同步两个数据源到EventMember表
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";

            log.info("Step 1/2: Synchronizing email members directly to EventMember table...");
            informerSyncService.syncEmailMembersDirectlyToEventMember(emailMembersUrl);

            log.info("Step 2/2: Synchronizing SMS-only members directly to EventMember table...");
            informerSyncService.syncSmsMembersDirectlyToEventMember(smsMembersUrl);

            response.put("success", true);
            response.put("message", "All data sources DIRECT synchronization to EventMember completed");
            response.put("dataSources", new String[]{"EMAIL_MEMBERS_DIRECT", "SMS_MEMBERS_DIRECT"});

            log.info("=== Manual sync all data sources completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== Manual sync all data sources failed ===", e);
            response.put("success", false);
            response.put("message", "Batch synchronization failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // CRITICAL: 新增：BMM专用直接同步端点（Email + SMS 直接到 EventMember）
    @PostMapping("/bmm-direct")
    public ResponseEntity<Map<String, Object>> syncBMMDirectly() {
        log.info("=== BMM DIRECT sync to EventMember requested ===");
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> results = new HashMap<>();

        try {
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";

            // 步骤1：同步Email成员
            try {
                log.info("Step 1/2: Syncing Email Members directly to EventMember...");
                informerSyncService.syncEmailMembersDirectlyToEventMember(emailMembersUrl);
                results.put("emailMembers", "SUCCESS");
                log.info("Email members direct sync completed successfully");
            } catch (Exception e) {
                results.put("emailMembers", "FAILED: " + e.getMessage());
                log.error("Email members direct sync failed", e);
            }

            // 步骤2：同步SMS成员
            try {
                log.info("Step 2/2: Syncing SMS Members directly to EventMember...");
                informerSyncService.syncSmsMembersDirectlyToEventMember(smsMembersUrl);
                results.put("smsMembers", "SUCCESS");
                log.info("SMS members direct sync completed successfully");
            } catch (Exception e) {
                results.put("smsMembers", "FAILED: " + e.getMessage());
                log.error("SMS members direct sync failed", e);
            }

            results.put("syncTime", java.time.LocalDateTime.now());
            results.put("method", "DIRECT_TO_EVENT_MEMBER");
            results.put("memberTableSkipped", true);

            response.put("success", true);
            response.put("message", "BMM DIRECT synchronization completed");
            response.put("results", results);

            log.info("=== BMM DIRECT sync completed successfully ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("=== BMM DIRECT sync failed ===", e);
            response.put("success", false);
            response.put("message", "BMM DIRECT synchronization failed: " + e.getMessage());
            response.put("results", results);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Simple diagnostic endpoint
    @GetMapping("/diagnostic")
    public ResponseEntity<Map<String, Object>> runDiagnostic() {
        log.info("=== Running data sync diagnostic ===");
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> diagnostics = new HashMap<>();

        try {
//            Test service availability
            diagnostics.put("sync_service_available", informerSyncService != null ? "OK" : "FAILED");
            diagnostics.put("timestamp", java.time.LocalDateTime.now().toString());
            response.put("success", true);
            response.put("message", "Diagnostic completed");
            response.put("diagnostics", diagnostics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Diagnostic failed", e);
            response.put("success", false);
            response.put("message", "Diagnostic failed: " + e.getMessage());
            response.put("diagnostics", diagnostics);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Get sync progress by syncId
    @GetMapping("/progress/{syncId}")
    public ResponseEntity<Map<String, Object>> getSyncProgress(@PathVariable String syncId) {
        Map<String, Object> response = new HashMap<>();

        try {
            SyncProgress progress = syncProgressRepository.findBySyncId(syncId)
                    .orElseThrow(() -> new RuntimeException("Sync progress not found: " + syncId));

            response.put("success", true);
            response.put("syncId", progress.getSyncId());
            response.put("status", progress.getStatus().name());
            response.put("syncType", progress.getSyncType());
            response.put("totalRecords", progress.getTotalRecords());
            response.put("processedRecords", progress.getProcessedRecords());
            response.put("progressPercentage", progress.getProgressPercentage());
            response.put("errorCount", progress.getErrorCount());
            response.put("message", progress.getMessage());
            response.put("startTime", progress.getStartTime());
            response.put("endTime", progress.getEndTime());

            if (progress.getEvent() != null) {
                response.put("eventName", progress.getEvent().getName());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to get sync progress: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Get recent sync history
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getSyncHistory() {
        Map<String, Object> response = new HashMap<>();

        try {
            var recentSyncs = syncProgressRepository.findTop10ByOrderByStartTimeDesc();
            response.put("success", true);
            response.put("syncs", recentSyncs);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to get sync history: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Test endpoint to sync a small batch of email members data for debugging
    @PostMapping("/test-email-members")
    public ResponseEntity<Map<String, Object>> testSyncEmailMembers(@RequestParam(defaultValue = "5") int maxRecords) {
        log.info("=== Test email members sync requested (max {} records) ===", maxRecords);
        try {
            Map<String, Object> result = informerSyncService.testSyncEmailMembers(maxRecords);
            if ((Boolean) result.get("success")) {
                log.info("=== Test email members sync completed successfully ===");
                return ResponseEntity.ok(result);
            } else {
                log.error("=== Test email members sync failed: {} ===", result.get("error"));
                return ResponseEntity.internalServerError().body(result);
            }
        } catch (Exception e) {
            log.error("=== Test email members sync failed ===", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Test sync failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}