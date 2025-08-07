package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.service.SmartNotificationService;
import nz.etu.voting.service.SmartNotificationService.MemberFilterCriteria;
import nz.etu.voting.service.SmartNotificationService.NotificationResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/smart-notifications")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
@Slf4j
public class SmartNotificationController {

    private final SmartNotificationService smartNotificationService;

    @PostMapping("/{eventId}/initial-invitations")
    public ResponseEntity<ApiResponse<NotificationResult>> sendInitialInvitations(
            @PathVariable Long eventId,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendInitialInvitations(
                    eventId, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Initial invitations sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send initial invitations for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/registration-confirmations")
    public ResponseEntity<ApiResponse<NotificationResult>> sendRegistrationConfirmations(
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendRegistrationConfirmations(
                    eventId, since, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Registration confirmations sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send registration confirmations for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/attendance-confirmations")
    public ResponseEntity<ApiResponse<NotificationResult>> sendAttendanceConfirmations(
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendAttendanceConfirmations(
                    eventId, since, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Attendance confirmations sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send attendance confirmations for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/qr-code-emails")
    public ResponseEntity<ApiResponse<NotificationResult>> sendQRCodeEmails(
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendQRCodeEmails(
                    eventId, since, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("QR code emails sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send QR code emails for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/follow-up-reminders")
    public ResponseEntity<ApiResponse<NotificationResult>> sendFollowUpReminders(
            @PathVariable Long eventId,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendFollowUpReminders(
                    eventId, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Follow-up reminders sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send follow-up reminders for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/manufacturing-food-survey")
    public ResponseEntity<ApiResponse<NotificationResult>> sendManufacturingFoodSurvey(
            @PathVariable Long eventId,
            @RequestBody EmailRequest request) {
        try {
            NotificationResult result = smartNotificationService.sendManufacturingFoodSurvey(
                    eventId, request.getSubject(), request.getContent());
            return ResponseEntity.ok(ApiResponse.success("Manufacturing food survey sent successfully", result));
        } catch (Exception e) {
            log.error("Failed to send manufacturing food survey for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{eventId}/preview-recipients")
    public ResponseEntity<ApiResponse<List<EventMember>>> previewEmailRecipients(
            @PathVariable Long eventId,
            @RequestParam String emailType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        try {
            List<EventMember> recipients = smartNotificationService.previewEmailRecipients(eventId, emailType, since);
            return ResponseEntity.ok(ApiResponse.success("Email recipients retrieved successfully", recipients));
        } catch (Exception e) {
            log.error("Failed to preview email recipients for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{eventId}/filter-stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getMemberFilterStats(
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        try {
            Map<String, Long> stats = smartNotificationService.getMemberFilterStats(eventId, since);
            return ResponseEntity.ok(ApiResponse.success("Member filter stats retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Failed to get filter stats for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{eventId}/filter-members")
    public ResponseEntity<ApiResponse<List<EventMember>>> filterMembers(
            @PathVariable Long eventId,
            @RequestBody MemberFilterCriteria criteria) {
        try {
            List<EventMember> members = smartNotificationService.filterMembers(eventId, criteria);
            return ResponseEntity.ok(ApiResponse.success("Members filtered successfully", members));
        } catch (Exception e) {
            log.error("Failed to filter members for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{eventId}/recent-timepoints")
    public ResponseEntity<ApiResponse<List<LocalDateTime>>> getRecentActivityTimepoints(
            @PathVariable Long eventId) {
        try {
            List<LocalDateTime> timepoints = smartNotificationService.getRecentActivityTimepoints(eventId);
            return ResponseEntity.ok(ApiResponse.success("Recent activity timepoints retrieved successfully", timepoints));
        } catch (Exception e) {
            log.error("Failed to get recent timepoints for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    DTO classes
    public static class EmailRequest {
        private String subject;
        private String content;

        public EmailRequest() {}

        public EmailRequest(String subject, String content) {
            this.subject = subject;
            this.content = content;
        }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}