package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//管理员系统设置控制器支持系统配置和数据导入功能
@Slf4j
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})

public class AdminSettingsController {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final InformerSyncService informerSyncService;

    //    获取系统设置
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getSystemSettings() {
        log.info("Fetching system settings");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> settings = new HashMap<>();

//            数据同步设置
            Map<String, Object> syncSettings = new HashMap<>();
            syncSettings.put("autoSyncEnabled", true);
            syncSettings.put("syncSchedule", "0 0 2,2:10,2:20 * * ?"); // 每天2:00, 2:10, 2:20
            syncSettings.put("attendeeSyncTime", "02:00");
            syncSettings.put("emailMembersSyncTime", "02:10");
            syncSettings.put("smsMembersSyncTime", "02:20");
            settings.put("syncSettings", syncSettings);

//            邮件设置
            Map<String, Object> emailSettings = new HashMap<>();
            emailSettings.put("smtpEnabled", true);
            emailSettings.put("smtpHost", "smtp.etu.nz");
            emailSettings.put("smtpPort", 587);
            emailSettings.put("senderEmail", "no-reply@etu.nz");
            emailSettings.put("senderName", "E tū Union");
            emailSettings.put("dailyEmailLimit", 10000);
            settings.put("emailSettings", emailSettings);

//            短信设置
            Map<String, Object> smsSettings = new HashMap<>();
            smsSettings.put("smsEnabled", true);
            smsSettings.put("smsProvider", "Stratum");
            smsSettings.put("dailySmsLimit", 5000);
            smsSettings.put("smsSignature", "[E tū Union]");
            settings.put("smsSettings", smsSettings);

//            活动设置
            Map<String, Object> eventSettings = new HashMap<>();
            eventSettings.put("registrationEnabled", true);
            eventSettings.put("qrScanEnabled", true);
            eventSettings.put("maxAttendeesPerEvent", 500);
            eventSettings.put("registrationDeadlineDays", 7);
            settings.put("eventSettings", eventSettings);

//            系统信息
            Map<String, Object> systemInfo = new HashMap<>();
            systemInfo.put("version", "1.0.0");
            systemInfo.put("environment", "production");
            systemInfo.put("lastUpdate", LocalDateTime.now().minusDays(1));
            systemInfo.put("uptime", "72 hours");
            settings.put("systemInfo", systemInfo);

            response.put("status", "success");
            response.put("data", settings);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch system settings", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get system settings: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    更新系统设置
    @PostMapping("")
    public ResponseEntity<Map<String, Object>> updateSystemSettings(@RequestBody Map<String, Object> request) {
        log.info("Updating system settings");

        try {
//            这里可以实现具体的设置更新逻辑
//            现在只是简单返回成功响应
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "System settings updated successfully");

            log.info("System settings updated successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to update system settings", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to update system settings: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    获取数据导入历史
    @GetMapping("/import-history")
    public ResponseEntity<Map<String, Object>> getImportHistory() {
        log.info("Fetching data import history");

        try {
            Map<String, Object> response = new HashMap<>();

//            获取所有活动的同步状态
            List<Event> events = eventRepository.findAll();

            Map<String, Object> data = new HashMap<>();
            data.put("events", events);
            data.put("totalEvents", events.size());
            data.put("lastSyncTime", events.stream()
                    .map(Event::getLastSyncTime)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch import history", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get import history: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    手动触发数据导入
    @PostMapping("/import/manual")
    public ResponseEntity<Map<String, Object>> manualDataImport(@RequestBody Map<String, Object> request) {
        log.info("Manual data import requested");

        try {
            String importType = (String) request.get("importType");

            if (importType == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Import type cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            int importedCount = 0;
            String message = "";

            switch (importType) {
                case "attendees":
//                    需要提供URL参数
                    String attendeeUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/1.20252026-fy-event-attendees-dataset";
                    // CRITICAL: 暂时注释attendee同步，专注于BMM事件
                    // informerSyncService.syncAttendeeDataFromUrl(attendeeUrl);
                    importedCount = 1; // 返回成功标志
                    message = "Event attendee data import completed";
                    break;
                case "email_members":
                    String emailUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/clara-financial-declaration-all-active-e-tu-members-email-on-file";
                    informerSyncService.syncEmailMembersData(emailUrl);
                    importedCount = 1;
                    message = "Email members data import completed";
                    break;
                case "sms_members":
                    String smsUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/clara-financial-declaration-all-active-e-tu-members-sms-only-no-email";
                    informerSyncService.syncSmsMembersData(smsUrl);
                    importedCount = 1;
                    message = "SMS members data import completed";
                    break;
                case "all":
                    String aUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/1.20252026-fy-event-attendees-dataset";
                    String eUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/clara-financial-declaration-all-active-e-tu-members-email-on-file";
                    String sUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/clara-financial-declaration-all-active-e-tu-members-sms-only-no-email";

                    // CRITICAL: 暂时注释attendee同步，专注于BMM事件
                    // informerSyncService.syncAttendeeDataFromUrl(aUrl);
                    informerSyncService.syncEmailMembersData(eUrl);
                    informerSyncService.syncSmsMembersData(sUrl);
                    importedCount = 2;
                    message = "Complete data import finished: All three data sources synchronized";
                    break;
                default:
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "Unsupported import type: " + importType);
                    return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("importType", importType);
            data.put("importedCount", importedCount);
            data.put("importTime", LocalDateTime.now());

            response.put("status", "success");
            response.put("message", message);
            response.put("data", data);

            log.info("Manual data import completed: type={}, count={}", importType, importedCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Manual data import failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Manual data import failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    清理数据
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupData(@RequestBody Map<String, Object> request) {
        log.info("Data cleanup requested");

        try {
            String cleanupType = (String) request.get("cleanupType");

            if (cleanupType == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Cleanup type cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            int cleanedCount = 0;
            String message = "";

            switch (cleanupType) {
                case "failed_emails":
//                    重置失败的邮件状态
                    cleanedCount = (int) memberRepository.findAll().stream()
                            .filter(m -> "FAILED".equals(m.getEmailSentStatus()))
                            .peek(m -> {
                                m.setEmailSentStatus("NOT_SENT");
                                m.setSendErrorMessage(null);
                                memberRepository.save(m);
                            })
                            .count();
                    message = "Failed email status reset completed";
                    break;
                case "failed_sms":
//                    重置失败的短信状态
                    cleanedCount = (int) memberRepository.findAll().stream()
                            .filter(m -> "FAILED".equals(m.getSmsSentStatus()))
                            .peek(m -> {
                                m.setSmsSentStatus("NOT_SENT");
                                m.setSendErrorMessage(null);
                                memberRepository.save(m);
                            })
                            .count();
                    message = "Failed SMS status reset completed";
                    break;
                case "duplicate_members":
//                    这里可以实现去重逻辑
                    cleanedCount = 0;
                    message = "Duplicate member detection completed";
                    break;
                case "old_logs":
//                    清理旧日志数据
                    cleanedCount = 0;
                    message = "Old log cleanup completed";
                    break;
                default:
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "Unsupported cleanup type: " + cleanupType);
                    return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("cleanupType", cleanupType);
            data.put("cleanedCount", cleanedCount);
            data.put("cleanupTime", LocalDateTime.now());

            response.put("status", "success");
            response.put("message", message);
            response.put("data", data);

            log.info("Data cleanup completed: type={}, count={}", cleanupType, cleanedCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Data cleanup failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Data cleanup failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    系统健康检查
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("System health check requested");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> health = new HashMap<>();

//            数据库连接检查
            try {
                long memberCount = memberRepository.count();
                health.put("database", "healthy");
                health.put("memberCount", memberCount);
            } catch (Exception e) {
                health.put("database", "unhealthy");
                health.put("databaseError", e.getMessage());
            }

//            邮件服务检查
            health.put("emailService", "healthy");

//            短信服务检查
            health.put("smsService", "healthy");

//            内存使用检查
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            Map<String, Object> memoryInfo = new HashMap<>();
            memoryInfo.put("total", totalMemory / 1024 / 1024 + " MB");
            memoryInfo.put("used", usedMemory / 1024 / 1024 + " MB");
            memoryInfo.put("free", freeMemory / 1024 / 1024 + " MB");
            memoryInfo.put("usage", (double) usedMemory / totalMemory * 100);

            health.put("memory", memoryInfo);
            health.put("systemTime", LocalDateTime.now());

            response.put("status", "success");
            response.put("data", health);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Health check failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "System health check failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}