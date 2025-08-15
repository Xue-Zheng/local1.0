package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.BulkNotificationRequest;
import nz.etu.voting.domain.dto.request.CreateTemplateRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.domain.entity.NotificationTemplate;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.repository.NotificationTemplateRepository;
import nz.etu.voting.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import nz.etu.voting.domain.dto.request.BulkSmsRequest;
import java.util.ArrayList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/notifications")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;


    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<NotificationTemplate>>> getAllTemplates() {
        try {
            List<NotificationTemplate> templates = templateRepository.findByIsActiveTrueOrderByName();
            return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
        } catch (Exception e) {
            log.error("Failed to retrieve templates", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<NotificationTemplate>> createTemplate(@RequestBody CreateTemplateRequest request) {
        try {
            NotificationTemplate template = NotificationTemplate.builder()
                    .templateCode(request.getTemplateCode())
                    .name(request.getName())
                    .description(request.getDescription())
                    .templateType(request.getTemplateType())
                    .subject(request.getSubject())
                    .content(request.getContent())
                    .isActive(true)
                    .build();

            NotificationTemplate savedTemplate = templateRepository.save(template);
            return ResponseEntity.ok(ApiResponse.success("Template created successfully", savedTemplate));
        } catch (Exception e) {
            log.error("Failed to create template", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplate>> updateTemplate(
            @PathVariable Long id, @RequestBody CreateTemplateRequest request) {
        try {
            Optional<NotificationTemplate> templateOpt = templateRepository.findById(id);
            if (!templateOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Template not found"));
            }

            NotificationTemplate template = templateOpt.get();
            template.setTemplateCode(request.getTemplateCode());
            template.setName(request.getName());
            template.setDescription(request.getDescription());
            template.setTemplateType(request.getTemplateType());
            template.setSubject(request.getSubject());
            template.setContent(request.getContent());

            NotificationTemplate savedTemplate = templateRepository.save(template);
            return ResponseEntity.ok(ApiResponse.success("Template updated successfully", savedTemplate));
        } catch (Exception e) {
            log.error("Failed to update template", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }


    @PostMapping("/send-bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendBulkNotifications(@RequestBody BulkNotificationRequest request) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(request.getEventId());
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Optional<NotificationTemplate> templateOpt = templateRepository.findById(request.getTemplateId());
            if (!templateOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Template not found"));
            }

            Event event = eventOpt.get();
            NotificationTemplate template = templateOpt.get();

            List<EventMember> targetMembers = getTargetMembers(event, request.getRecipientType());

            Map<String, String> commonVariables = request.getVariables() != null ? request.getVariables() : new HashMap<>();
            notificationService.sendBulkNotifications(event, targetMembers, template, commonVariables, request.getNotificationType());

            Map<String, Object> result = new HashMap<>();
            result.put("totalRecipients", targetMembers.size());
            result.put("eventName", event.getName());
            result.put("templateName", template.getName());

            return ResponseEntity.ok(ApiResponse.success("Bulk notifications queued successfully", result));
        } catch (Exception e) {
            log.error("Failed to send bulk notifications", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/logs/{eventId}")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getNotificationLogs(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            List<NotificationLog> logs = logRepository.findByEvent(eventOpt.get());
            return ResponseEntity.ok(ApiResponse.success("Notification logs retrieved successfully", logs));
        } catch (Exception e) {
            log.error("Failed to retrieve notification logs", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/send-bulk-sms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendBulkSms(@RequestBody BulkSmsRequest request) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(request.getEventId());
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            List<EventMember> targetMembers = getTargetMembers(event, request.getRecipientType());

//            Filter member who only have Phone number
            List<EventMember> membersWithMobile = targetMembers.stream()
                    .filter(member -> member.getHasMobile() && member.getTelephoneMobile() != null)
                    .collect(Collectors.toList());

//            Create temper SMS template
            NotificationTemplate tempTemplate = NotificationTemplate.builder()
                    .templateCode("BULK_SMS_TEMP")
                    .name("Bulk SMS")
                    .content(request.getMessage())
                    .templateType(NotificationTemplate.TemplateType.SMS)
                    .isActive(true)
                    .build();

            Map<String, String> variables = request.getVariables() != null ? request.getVariables() : new HashMap<>();
            variables.put("eventName", event.getName());

//            Send SMS asynchronously via RabbitMQ
            notificationService.sendBulkNotifications(event, membersWithMobile, tempTemplate,
                    variables, NotificationLog.NotificationType.SMS);

            Map<String, Object> result = new HashMap<>();
            result.put("totalRecipients", membersWithMobile.size());
            result.put("eventName", event.getName());
            result.put("totalMembersSelected", targetMembers.size());
            result.put("membersWithoutMobile", targetMembers.size() - membersWithMobile.size());

            return ResponseEntity.ok(ApiResponse.success("SMS messages queued successfully", result));
        } catch (Exception e) {
            log.error("Failed to queue bulk SMS", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @PostMapping("/preview-sms")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewSms(@RequestBody BulkSmsRequest request) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(request.getEventId());
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            List<EventMember> targetMembers = getTargetMembers(event, request.getRecipientType());

//            Filter member only have Phone number and limit views
            List<EventMember> membersWithMobile = targetMembers.stream()
                    .filter(member -> member.getHasMobile() && member.getTelephoneMobile() != null)
                    .limit(5)
                    .collect(Collectors.toList());

            List<Map<String, Object>> previewList = new ArrayList<>();
            for (EventMember member : membersWithMobile) {
                String personalizedMessage = request.getMessage()
                        .replace("{{name}}", member.getName())
                        .replace("{{membershipNumber}}", member.getMembershipNumber())
                        .replace("{{eventName}}", event.getName());

                Map<String, Object> preview = new HashMap<>();
                preview.put("name", member.getName());
                preview.put("membershipNumber", member.getMembershipNumber());
                preview.put("mobilePhone", member.getTelephoneMobile());
                preview.put("personalizedMessage", personalizedMessage);
                previewList.add(preview);
            }

            long totalEligible = targetMembers.stream()
                    .filter(member -> member.getHasMobile() && member.getTelephoneMobile() != null)
                    .count();

            Map<String, Object> result = new HashMap<>();
            result.put("previewMembers", previewList);
            result.put("totalEligibleMembers", totalEligible);
            result.put("totalSelectedMembers", targetMembers.size());
            result.put("eventName", event.getName());

            return ResponseEntity.ok(ApiResponse.success("SMS preview generated successfully", result));
        } catch (Exception e) {
            log.error("Failed to generate SMS preview", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGeneralNotificationStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

//            统计所有通知日志
            long totalEmailSuccess = logRepository.findAll().stream()
                    .filter(log -> (log.getNotificationType() == NotificationLog.NotificationType.EMAIL ||
                            log.getNotificationType() == NotificationLog.NotificationType.AUTO_EMAIL)
                            && log.getIsSuccessful())
                    .count();

            long totalEmailFailed = logRepository.findAll().stream()
                    .filter(log -> (log.getNotificationType() == NotificationLog.NotificationType.EMAIL ||
                            log.getNotificationType() == NotificationLog.NotificationType.AUTO_EMAIL)
                            && !log.getIsSuccessful())
                    .count();

            long totalSmsSuccess = logRepository.findAll().stream()
                    .filter(log -> (log.getNotificationType() == NotificationLog.NotificationType.SMS ||
                            log.getNotificationType() == NotificationLog.NotificationType.AUTO_SMS)
                            && log.getIsSuccessful())
                    .count();

            long totalSmsFailed = logRepository.findAll().stream()
                    .filter(log -> (log.getNotificationType() == NotificationLog.NotificationType.SMS ||
                            log.getNotificationType() == NotificationLog.NotificationType.AUTO_SMS)
                            && !log.getIsSuccessful())
                    .count();

//            计算待发送数量（这里简化为0，实际应该从队列系统获取）
            long emailPending = 0;
            long smsPending = 0;

            stats.put("totalMembers", eventMemberRepository.count());
            stats.put("emailSent", totalEmailSuccess);
            stats.put("smsSent", totalSmsSuccess);
            stats.put("emailPending", emailPending);
            stats.put("smsPending", smsPending);
            stats.put("emailFailed", totalEmailFailed);
            stats.put("smsFailed", totalSmsFailed);

            return ResponseEntity.ok(ApiResponse.success("General notification statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Failed to retrieve general notification statistics", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/stats/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotificationStats(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            Map<String, Object> stats = new HashMap<>();

            Long emailSuccess = logRepository.countSuccessfulByEventAndType(event, NotificationLog.NotificationType.EMAIL);
            Long emailFailed = logRepository.countFailedByEventAndType(event, NotificationLog.NotificationType.EMAIL);
            Long autoEmailSuccess = logRepository.countSuccessfulByEventAndType(event, NotificationLog.NotificationType.AUTO_EMAIL);
            Long autoEmailFailed = logRepository.countFailedByEventAndType(event, NotificationLog.NotificationType.AUTO_EMAIL);

            Long smsSuccess = logRepository.countSuccessfulByEventAndType(event, NotificationLog.NotificationType.SMS);
            Long smsFailed = logRepository.countFailedByEventAndType(event, NotificationLog.NotificationType.SMS);
            Long autoSmsSuccess = logRepository.countSuccessfulByEventAndType(event, NotificationLog.NotificationType.AUTO_SMS);
            Long autoSmsFailed = logRepository.countFailedByEventAndType(event, NotificationLog.NotificationType.AUTO_SMS);

            stats.put("primaryEmail", Map.of(
                    "manual_success", emailSuccess,
                    "manual_failed", emailFailed,
                    "auto_success", autoEmailSuccess,
                    "auto_failed", autoEmailFailed,
                    "total_success", emailSuccess + autoEmailSuccess,
                    "total_failed", emailFailed + autoEmailFailed
            ));

            stats.put("sms", Map.of(
                    "manual_success", smsSuccess,
                    "manual_failed", smsFailed,
                    "auto_success", autoSmsSuccess,
                    "auto_failed", autoSmsFailed,
                    "total_success", smsSuccess + autoSmsSuccess,
                    "total_failed", smsFailed + autoSmsFailed
            ));

            return ResponseEntity.ok(ApiResponse.success("Notification statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Failed to retrieve notification statistics", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private List<EventMember> getTargetMembers(Event event, String recipientType) {
        switch (recipientType) {
            case "all":
                return eventMemberRepository.findByEvent(event);
            case "registered":
                return eventMemberRepository.findByEventAndHasRegisteredTrue(event);
            case "unregistered":
                return eventMemberRepository.findByEventAndHasRegisteredFalse(event);
            case "attending":
                return eventMemberRepository.findByEventAndIsAttendingTrue(event);
            case "special_vote":
                return eventMemberRepository.findByEventAndIsSpecialVoteTrue(event);
            case "voted":
                return eventMemberRepository.findByEventAndHasVotedTrue(event);
            case "checked_in":
                return eventMemberRepository.findByEventAndCheckedInTrue(event);
            case "no_email":
                return eventMemberRepository.findMembersWithoutEmailByEvent(event);
            case "with_email":
                return eventMemberRepository.findMembersWithEmailByEvent(event);
            default:
                return eventMemberRepository.findByEvent(event);
        }
    }
}
