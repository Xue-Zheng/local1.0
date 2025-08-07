package nz.etu.voting.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sync.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class DataSyncScheduler {

    private final InformerSyncService informerSyncService;
    //    凌晨 1:00 自动同步有邮箱会员（提前1小时，避开高峰期）- DISABLED
    // @Scheduled(cron = "0 0 1 * * ?")
    public void syncEmailMembersData() {
        log.info("=== Starting email members DIRECT sync at 1:00 AM (45000+ records) ===");
        try {
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";
            // 使用优化的直接同步方法
            informerSyncService.syncEmailMembersDirectlyToEventMember(emailMembersUrl);
            log.info("=== Email members DIRECT sync completed ===");
        } catch (Exception e) {
            log.error("=== Email members DIRECT sync failed ===", e);
        }
    }

    //    凌晨 2:00 自动同步仅短信会员（间隔1小时，确保上一个任务完成）- DISABLED
    // @Scheduled(cron = "0 0 2 * * ?")
    public void syncSmsMembersData() {
        log.info("=== Starting SMS members DIRECT sync at 2:00 AM ===");
        try {
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";
            // 使用优化的直接同步方法
            informerSyncService.syncSmsMembersDirectlyToEventMember(smsMembersUrl);
            log.info("=== SMS members DIRECT sync completed ===");
        } catch (Exception e) {
            log.error("=== SMS members DIRECT sync failed ===", e);
        }
    }

    //    凌晨 3:00 自动同步事件参与者（间隔1小时，确保上一个任务完成）
    // CRITICAL: 完全禁用Attendee自动同步 - BMM专用
    // @Scheduled(cron = "0 0 3 * * ?")
    public void syncAttendeeData_DISABLED() {
        log.info("=== Attendee sync DISABLED for BMM focus ===");
        // CRITICAL: 已完全禁用attendee同步，专注于BMM事件
        // String attendeeUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/d382fc79-1230-4a1d-917a-7bc43aa21a84/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiI2NjI4ZTNkZi1hZmZhLTQ3OWUtOWEzZC1iZTMwZjFhZjI5NzEiLCJpYXQiOjE3NDk2OTgxODcuMjQxfQ.ZR8WC1UbQAtV6r5EyNG083qzQ450pJb1HRze5wFgR50";
        // informerSyncService.syncAttendeeDataFromUrl(attendeeUrl);
        log.info("=== Attendee sync DISABLED ===");
    }

    // @Scheduled(fixedRateString = "${sync.schedule.interval:7200000}", // Default 2 hours (increased interval to reduce server load)
    //         initialDelayString = "${sync.schedule.initial-delay:300000}") // Starts 5 minutes after launch (allow full startup) - DISABLED
    public void syncInformerData() {
        log.info("=== Starting scheduled data sync task ===");
        log.info("Current time: {}", LocalDateTime.now());

        try {
            informerSyncService.syncAllPendingEvents();
            log.info("=== Scheduled data sync task completed ===");
        } catch (Exception e) {
            log.error("=== Scheduled data sync task failed ===", e);
        }
    }

    //    Every day 4:00 full sync check (在所有单独同步完成后进行) - DISABLED
    // @Scheduled(cron = "${sync.schedule.daily-cron:0 0 4 * * ?}")
    public void dailyFullSync() {
        log.info("=== Starting daily full sync check at 4:00 AM ===");

        try {
            informerSyncService.syncAllPendingEvents();
            log.info("=== Daily full sync check completed ===");
        } catch (Exception e) {
            log.error("=== Daily full sync check failed ===", e);
        }
    }
}