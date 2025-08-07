package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.BulkEmailRequest;
import nz.etu.voting.domain.dto.request.SingleEmailRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.dto.response.EmailResponse;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.EmailService;
import nz.etu.voting.service.EventMemberTargetingService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/admin/email")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;
    private final NotificationLogRepository notificationLogRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final RabbitTemplate rabbitTemplate;
    private final EventMemberTargetingService eventMemberTargetingService;
    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;


    // 重载方法处理EventMember对象
    private String getFirstName(EventMember eventMember) {
        // First try the fore1 field if available
        if (eventMember.getFore1() != null && !eventMember.getFore1().trim().isEmpty()) {
            return eventMember.getFore1().trim();
        }

        // If fore1 not available, extract from full name
        String fullName = eventMember.getName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            fullName = fullName.trim();
            if (fullName.contains(" ")) {
                String[] nameParts = fullName.split("\\s+");
                return nameParts[0]; // First part is the first name
            } else {
                return fullName; // If no space, assume it's just the first name
            }
        }

        // Fallback
        return "Member";
    }

    //    Handle frontend form-based email sending
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendEmailsFromForm(
            @RequestParam(value = "emailType", required = false) String emailType,
            @RequestParam(value = "email", required = false) String singleEmail,
            @RequestParam(value = "name", required = false) String singleName,
            @RequestParam("subject") String subject,
            @RequestParam("content") String content,
            @RequestParam(value = "memberIds", required = false) String memberIdsJson,
            @RequestParam(value = "attachments", required = false) List<MultipartFile> attachments,
            @RequestParam(value = "provider", required = false, defaultValue = "STRATUM") String provider) {
        log.info("=== Form-based email send requested ===");
        log.info("Parameters received - emailType: [{}], singleEmail: [{}], singleName: [{}]", emailType, singleEmail, singleName);
        log.info("Subject: [{}], Content length: {}, Has attachments: {}",
                subject, content != null ? content.length() : 0,
                attachments != null && !attachments.isEmpty());
        log.info("MemberIds JSON: [{}]", memberIdsJson);
        log.info("🔍 PROVIDER RECEIVED: [{}] (type: {})", provider, provider != null ? provider.getClass().getSimpleName() : "null");

        try {
            List<EventMember> eventMembers;

            if (singleEmail != null && !singleEmail.trim().isEmpty()) {
                // Single email sending
                log.info("=== SINGLE EMAIL MODE ACTIVATED ===");
                log.info("Sending single email to: {}", singleEmail);

                // Find EventMember by email
                List<EventMember> foundMembers = eventMemberRepository.findByPrimaryEmail(singleEmail);
                eventMembers = foundMembers.isEmpty() ? new ArrayList<>() : List.of(foundMembers.get(0));

                if (eventMembers.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "No member found with email: " + singleEmail);
                    return ResponseEntity.badRequest().body(response);
                }
            } else if (memberIdsJson != null && !memberIdsJson.trim().isEmpty()) {
                // Selected members sending
                log.info("=== SELECTED MEMBERS MODE ACTIVATED ===");
                log.info("Processing selected members: {}", memberIdsJson);
                try {
                    // Parse member IDs from JSON - these are EventMember IDs
                    String[] memberIds = memberIdsJson.replace("[", "").replace("]", "").replace("\"", "").split(",");
                    eventMembers = new ArrayList<>();
                    for (String memberIdStr : memberIds) {
                        Long eventMemberId = Long.parseLong(memberIdStr.trim());
                        eventMemberRepository.findById(eventMemberId).ifPresent(eventMembers::add);
                    }
                    log.info("Found {} selected EventMembers", eventMembers.size());
                } catch (Exception e) {
                    log.error("Failed to parse memberIds: {}", e.getMessage());
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "Invalid member IDs format");
                    return ResponseEntity.badRequest().body(response);
                }
            } else if (emailType != null && !emailType.trim().isEmpty()) {
                // Bulk email sending by type
                log.info("=== BULK EMAIL MODE ACTIVATED ===");
                log.info("Processing bulk email send for type: {}", emailType);
                eventMembers = getEventMembersByType(emailType);
            } else {
                log.error("No valid sending parameters provided");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Must provide email address, member IDs, or email type");
                return ResponseEntity.badRequest().body(response);
            }

            // Filter EventMembers with valid email addresses
            eventMembers = eventMembers.stream()
                    .filter(em -> em.getPrimaryEmail() != null &&
                            !em.getPrimaryEmail().trim().isEmpty() &&
                            !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .collect(Collectors.toList());

            log.info("Starting email send to {} recipients", eventMembers.size());

            // Send emails
            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : eventMembers) {
                try {
                    String memberEmail = eventMember.getPrimaryEmail();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // Replace template variables with comprehensive BMM data
                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "")
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{actualToken}}", eventMember.getToken() != null ? eventMember.getToken().toString() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{confirmationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "confirmation") : "https://events.etu.nz/")
                            // BMM specific variables from EventMember
                            .replace("{{region}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{workplace}}", eventMember.getWorkplace() != null ? eventMember.getWorkplace() : "")
                            .replace("{{employer}}", eventMember.getEmployer() != null ? eventMember.getEmployer() : "")
                            .replace("{{branch}}", eventMember.getBranch() != null ? eventMember.getBranch() : "")
                            .replace("{{stageLinkUrl}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/");

                    // Add EventMember specific variables
                    personalizedContent = personalizedContent
                            .replace("{{assignedVenue}}", eventMember.getAssignedVenue() != null ? eventMember.getAssignedVenue() : "")
                            .replace("{{assignedDateTime}}", eventMember.getAssignedDateTime() != null ? eventMember.getAssignedDateTime().toString() : "")
                            .replace("{{bmmStage}}", eventMember.getBmmRegistrationStage() != null ? eventMember.getBmmRegistrationStage() : "")
                            .replace("{{region}}", eventMember.getAssignedRegion() != null ?
                                    eventMember.getAssignedRegion() :
                                    (eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : ""))
                            .replace("{{ticketUrl}}", eventMember.getTicketToken() != null ?
                                    "https://events.etu.nz/ticket?token=" + eventMember.getTicketToken() : "");

                    // Send via RabbitMQ queue instead of direct call
                    Map<String, Object> emailData = new HashMap<>();
                    emailData.put("recipient", memberEmail);
                    emailData.put("recipientName", memberName);
                    emailData.put("subject", subject);
                    emailData.put("content", personalizedContent);
                    emailData.put("eventMemberId", eventMember.getId());
                    emailData.put("memberId", eventMember.getId());
                    emailData.put("membershipNumber", eventMember.getMembershipNumber());
                    emailData.put("templateCode", "BULK_EMAIL");
                    emailData.put("notificationType", "EMAIL");
                    emailData.put("provider", provider);
                    rabbitTemplate.convertAndSend(emailQueue, emailData);

                    // Create notification log with correct EventMember association
                    NotificationLog emailLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .eventMember(eventMember) // BMM系统主要关联
                            .notificationType(NotificationLog.NotificationType.EMAIL)
                            .recipient(memberEmail)
                            .recipientName(memberName)
                            .subject(subject)
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("BULK_EMAIL")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(emailLog);
                    successCount++;
                    log.info("Email queued successfully for: {}", memberEmail);
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to queue email for EventMember {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                }
            }

            // Return response in format expected by frontend
            Map<String, Object> data = new HashMap<>();
            data.put("sent", successCount);
            data.put("failed", failCount);
            data.put("total", eventMembers.size());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Email queuing completed: %d queued, %d failed", successCount, failCount));
            response.put("data", data);
            log.info("Email sending completed: {} success, {} failed out of {} total",
                    successCount, failCount, eventMembers.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send emails - Error details:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send emails: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
            // Provide more specific error messages
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                response.put("hint", "Database connection issue - please check database configuration");
            } else if (e.getMessage() != null && e.getMessage().contains("rabbit")) {
                response.put("hint", "RabbitMQ connection issue - please check RabbitMQ service");
            }
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<ApiResponse<EmailResponse>> sendBulkEmail(
            @RequestPart("request") BulkEmailRequest request,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        try {

            List<MultipartFile> attachmentList = attachments != null ? attachments : new ArrayList<>();
            request.setAttachments(attachmentList);

            log.info("Received {} attachments with request", attachmentList.size());
            if (!attachmentList.isEmpty()) {
                for (int i = 0; i < attachmentList.size(); i++) {
                    MultipartFile file = attachmentList.get(i);
                    log.info("Attachment #{}: name={}, size={}, contentType={}",
                            i+1, file.getOriginalFilename(), file.getSize(), file.getContentType());
                }
            }

            EmailResponse response = emailService.sendBulkEmails(request);
            return ResponseEntity.ok(ApiResponse.success("Emails queued for sending", response));
        } catch (Exception e) {
            log.error("Error sending bulk email", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    //Admin Email Sending Interfaces
//    Send single email (Admin format)
    @PostMapping("/send-single")
    public ResponseEntity<Map<String, Object>> sendSingleEmailAdmin(@RequestBody Map<String, Object> request) {
        log.info("=== Single email send requested (Admin format) ===");
        try {
            String email = (String) request.get("email");
            String name = (String) request.get("name");
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            if (email == null || subject == null || content == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Email address, subject and content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            // 🎯 BMM事件逻辑：直接查找EventMember
            EventMember eventMember = eventMemberRepository.findByPrimaryEmail(email).stream()
                    .findFirst()
                    .orElse(null);

            Long eventMemberId = eventMember != null ? eventMember.getId() : null;

//            Send via RabbitMQ queue
            Map<String, Object> emailData = Map.of(
                    "recipient", email,
                    "recipientName", name != null ? name : "User",
                    "subject", subject,
                    "content", content,
                    "eventMemberId", eventMemberId != null ? eventMemberId : 0L,
                    "memberId", eventMember != null ? eventMember.getId() : 0L,
                    "membershipNumber", eventMember != null ? eventMember.getMembershipNumber() : "UNKNOWN",
                    "templateCode", "SINGLE_EMAIL",
                    "notificationType", "EMAIL"
            );
            rabbitTemplate.convertAndSend(emailQueue, emailData);

            // 🎯 Create NotificationLog for BMM event (EventMember-based)
            if (eventMember != null) {
                NotificationLog emailLog = NotificationLog.builder()
                        .eventMember(eventMember)
                        .notificationType(NotificationLog.NotificationType.EMAIL)
                        .recipient(email)
                        .recipientName(name != null ? name : "User")
                        .subject(subject)
                        .content(content)
                        .sentTime(LocalDateTime.now())
                        .isSuccessful(false) // Will be updated by consumer
                        .emailType("SINGLE_EMAIL")
                        .adminId(1L)
                        .adminUsername("admin")
                        .build();
                notificationLogRepository.save(emailLog);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Email queued successfully");
            log.info("Single email queued successfully for: {} (EventMember: {})", email, eventMemberId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send single email", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send email: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Send bulk emails (Admin format)
    @PostMapping("/send-bulk-admin")
    public ResponseEntity<Map<String, Object>> sendBulkEmailsAdmin(@RequestBody Map<String, Object> request) {
        log.info("=== Bulk email send requested (Admin format) ===");
        try {
            String emailType = (String) request.get("emailType");
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            List<Map<String, Object>> recipients = (List<Map<String, Object>>) request.get("recipients");

            if (subject == null || subject.trim().isEmpty() || content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Email subject and content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            List<EventMember> eventMembers;

            // If specific recipients are provided, use them instead of emailType filtering
            if (recipients != null && !recipients.isEmpty()) {
                log.info("Using specific recipients list: {} members", recipients.size());
                eventMembers = new ArrayList<>();

                for (Map<String, Object> recipient : recipients) {
                    String email = (String) recipient.get("email");
                    String membershipNumber = (String) recipient.get("membershipNumber");

                    // Find EventMember by email or membership number
                    EventMember eventMember = null;
                    if (membershipNumber != null && !membershipNumber.trim().isEmpty()) {
                        List<EventMember> membersByNumber = eventMemberRepository.findByMembershipNumber(membershipNumber);
                        if (!membersByNumber.isEmpty()) {
                            // Get the most recent EventMember for this membership number
                            eventMember = membersByNumber.get(0);
                        }
                    }

                    if (eventMember == null && email != null && !email.trim().isEmpty()) {
                        // Try to find by email
                        List<EventMember> membersByEmail = eventMemberRepository.findByPrimaryEmail(email);
                        if (!membersByEmail.isEmpty()) {
                            // Get the most recent EventMember for this email
                            eventMember = membersByEmail.get(0);
                        }
                    }

                    if (eventMember != null) {
                        eventMembers.add(eventMember);
                        log.debug("Found EventMember for recipient: {}", email);
                    } else {
                        log.warn("Could not find EventMember for recipient: {} ({})", email, membershipNumber);
                    }
                }

                log.info("Found {} EventMembers from {} recipients", eventMembers.size(), recipients.size());
            } else {
                // Fall back to original behavior - get all members by type
                log.info("No specific recipients provided, using emailType: {}", emailType);
                eventMembers = getEventMembersByType(emailType);
            }

            // Filter EventMembers with valid email addresses
            eventMembers = eventMembers.stream()
                    .filter(em -> em.getPrimaryEmail() != null &&
                            !em.getPrimaryEmail().trim().isEmpty() &&
                            !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .collect(Collectors.toList());

            log.info("Starting bulk email send to {} recipients", eventMembers.size());

            // Send emails in bulk
            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : eventMembers) {
                try {
                    String memberEmail = eventMember.getPrimaryEmail();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // Replace template variables including firstName
                    String firstName = getFirstName(eventMember);

                    String personalizedContent = content
                            .replace("{{firstName}}", firstName)
                            .replace("{{name}}", memberName)
                            .replace("{{membershipNumber}}", eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "")
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{actualToken}}", eventMember.getToken() != null ? eventMember.getToken().toString() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{confirmationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "confirmation") : "https://events.etu.nz/")
                            .replace("{{specialVoteLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "special-vote") : "https://events.etu.nz/")
                            .replace("{{region}}", eventMember.getAssignedRegion() != null ?
                                    eventMember.getAssignedRegion() :
                                    (eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : ""))
                            .replace("{{assignedVenue}}", eventMember.getAssignedVenue() != null ? eventMember.getAssignedVenue() : "")
                            .replace("{{assignedDateTime}}", eventMember.getAssignedDateTime() != null ? eventMember.getAssignedDateTime().toString() : "")
                            .replace("{{ticketUrl}}", eventMember.getTicketToken() != null ?
                                    "https://events.etu.nz/ticket?token=" + eventMember.getTicketToken() : "");

                    // Send via RabbitMQ queue
                    Map<String, Object> emailData = Map.of(
                            "recipient", memberEmail,
                            "recipientName", memberName,
                            "subject", subject,
                            "content", personalizedContent,
                            "eventMemberId", eventMember.getId(),
                            "memberId", eventMember.getId(),
                            "membershipNumber", eventMember.getMembershipNumber(),
                            "templateCode", "ADMIN_BULK_EMAIL",
                            "notificationType", "EMAIL"
                    );
                    rabbitTemplate.convertAndSend(emailQueue, emailData);

                    // Create NotificationLog for BMM event (EventMember-based)
                    NotificationLog emailLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.EMAIL)
                            .recipient(memberEmail)
                            .recipientName(memberName)
                            .subject(subject)
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("ADMIN_BULK_EMAIL")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(emailLog);

                    successCount++;
                    log.debug("Email queued successfully for: {}", memberEmail);
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to queue email for EventMember {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Bulk email sending completed: %d success, %d failed", successCount, failCount));
            response.put("data", Map.of(
                    "total", eventMembers.size(),
                    "success", successCount,
                    "failed", failCount
            ));
            log.info("Bulk email sending completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send bulk emails", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send bulk emails: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Preview email recipients list
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewEmails(@RequestBody Map<String, Object> request) {
        log.info("=== Preview email recipients requested ===");
        try {
            String emailType = (String) request.get("emailType");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            // If eventId is null, get the current BMM event automatically
            if (eventId == null) {
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                Event currentBmmEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);

                if (currentBmmEvent != null) {
                    eventId = currentBmmEvent.getId();
                    log.info("Using current BMM event: {} (ID: {})", currentBmmEvent.getName(), eventId);
                }
            }

            Map<String, Object> response = new HashMap<>();
            List<EventMember> members;

            if (eventId != null) {
//                Get EventMembers from specific event
                log.info("Getting EventMembers for event ID: {} with type: {}", eventId, emailType);
                members = getMembersByEventAndType(eventId, emailType);
            } else {
//                Get global EventMembers (legacy mode)
                log.info("Getting global EventMembers with type: {}", emailType);
                members = getMembersByType(emailType);
            }

//            Filter members with valid email addresses
            members = members.stream()
                    .filter(m -> m.getPrimaryEmail() != null &&
                            !m.getPrimaryEmail().trim().isEmpty() &&
                            !m.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .collect(Collectors.toList());
            // Convert EventMembers to DTO to avoid deep serialization
            List<Map<String, Object>> memberDTOs = members.stream().map(member -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", member.getId());
                dto.put("name", member.getName());
                dto.put("email", member.getPrimaryEmail());
                dto.put("primaryEmail", member.getPrimaryEmail());
                dto.put("membershipNumber", member.getMembershipNumber());
                dto.put("verificationCode", member.getVerificationCode());
                dto.put("hasRegistered", member.getHasRegistered());
                dto.put("regionDesc", member.getRegionDesc());
                dto.put("siteIndustryDesc", member.getSiteIndustryDesc());
                dto.put("workplaceDesc", member.getWorkplaceDesc());
                dto.put("employer", member.getEmployer());
                return dto;
            }).collect(Collectors.toList());

//            Create data structure that matches frontend expectations
            Map<String, Object> data = new HashMap<>();
            data.put("members", memberDTOs);
            data.put("totalCount", memberDTOs.size());
            response.put("status", "success");
            response.put("message", "Email preview list generated successfully");
            response.put("data", data);
            log.info("Email preview generated: {} recipients for type '{}' (eventId: {})", members.size(), emailType, eventId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to preview emails", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to preview emails: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEmailStatus() {
        try {

            List<EventMember> eventMembers = eventMemberRepository.findAll();
            List<Map<String, Object>> statuses = new ArrayList<>();

            for (EventMember eventMember : eventMembers) {

                List<NotificationLog> logs = notificationLogRepository.findByEventMemberOrderBySentTimeDesc(eventMember);

                Map<String, Object> status = new HashMap<>();
                status.put("memberId", eventMember.getId());
                status.put("name", eventMember.getName());
                status.put("primaryEmail", eventMember.getPrimaryEmail());
                status.put("membershipNumber", eventMember.getMembershipNumber());
                // Special member field removed

                if (!logs.isEmpty()) {
                    status.put("lastEmailSent", logs.get(0).getSentTime());
                    status.put("hasReceivedEmail", true);
                    status.put("emailCount", logs.size());
                } else {
                    status.put("lastEmailSent", null);
                    status.put("hasReceivedEmail", false);
                    status.put("emailCount", 0);
                }

                statuses.add(status);
            }

            return ResponseEntity.ok(ApiResponse.success("Email statuses retrieved", statuses));
        } catch (Exception e) {
            log.error("Error retrieving email statuses", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    Send emails with advanced filtering by industry, region, workplace, etc.
    @PostMapping("/send-by-criteria")
    public ResponseEntity<Map<String, Object>> sendEmailsByCriteria(@RequestBody Map<String, Object> request) {
        log.info("=== Send emails by criteria requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");

            if (criteria == null || criteria.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Filter criteria cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (subject == null || subject.trim().isEmpty() || content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Email subject and content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Sending emails by criteria: {} (eventId: {})", criteria, eventId);

            List<EventMember> validEmailMembers;

            if (eventId != null) {
                // CRITICAL: 使用多表联合查询 - 修复关键问题
                List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

                // Filter EventMembers with valid email addresses
                validEmailMembers = filteredEventMembers.stream()
                        .filter(em -> em.getHasEmail() != null && em.getHasEmail() &&
                                em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());
            } else {
                // For cases without eventId, use old logic but needs improvement
                List<EventMember> eventMembers = getFilteredMembers(criteria, eventId);

                validEmailMembers = eventMembers.stream()
                        .filter(em -> em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());
            }

            // Send emails
            int successCount = 0;
            int failCount = 0;

            for (EventMember eventMember : validEmailMembers) {
                try {
                    String memberEmail = eventMember.getPrimaryEmail();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // Personalize email content
                    String membershipNumber = eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "BULK_" + eventMember.getId();
                    String personalizedSubject = subject
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber);

                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{region}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{regionDesc}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{workplaceDesc}}", eventMember.getWorkplaceDesc() != null ? eventMember.getWorkplaceDesc() : "")
                            .replace("{{employerName}}", eventMember.getEmployer() != null ? eventMember.getEmployer() : "");

                    // Send via RabbitMQ queue
                    Map<String, Object> emailData = Map.of(
                            "recipient", memberEmail,
                            "recipientName", memberName,
                            "subject", personalizedSubject,
                            "content", personalizedContent,
                            "eventMemberId", eventMember.getId(),
                            "memberId", eventMember.getId(),
                            "membershipNumber", membershipNumber,
                            "templateCode", "CRITERIA_FILTERED_EMAIL",
                            "notificationType", "EMAIL",
                            "timestamp", LocalDateTime.now().toString()
                    );

                    rabbitTemplate.convertAndSend(emailQueue, emailData);

                    // 🔧 Create NotificationLog for BMM event consistency
                    NotificationLog emailLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.EMAIL)
                            .recipient(memberEmail)
                            .recipientName(memberName)
                            .subject(personalizedSubject)
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("CRITERIA_FILTERED_EMAIL")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(emailLog);

                    successCount++;
                    log.debug("Email queued successfully for: {} (EventMember: {}, Region: {}, SubIndustry: {})",
                            memberEmail, eventMember.getId(), eventMember.getRegionDesc(), eventMember.getSiteSubIndustryDesc());

                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send email to eventMember {}: {}", eventMember.getId(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("sent", successCount);
            data.put("failed", failCount);
            data.put("total", validEmailMembers.size());

            response.put("status", "success");
            response.put("message", String.format("Emails sent: %d successful, %d failed", successCount, failCount));
            response.put("data", data);

            log.info("Email sending by criteria completed: {} sent, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send emails by criteria", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send emails: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Preview emails with advanced filtering
    @PostMapping("/preview-by-criteria")
    public ResponseEntity<Map<String, Object>> previewEmailsByCriteria(@RequestBody Map<String, Object> request) {
        log.info("=== Preview emails by criteria requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            if (criteria == null || criteria.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Filter criteria cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Applying preview criteria: {}", criteria);

            List<EventMember> validEmailMembers;

            if (eventId != null) {
                // CRITICAL: 使用多表联合查询 - 修复关键问题
                List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

                // Filter EventMembers with valid email addresses and convert to Members
                validEmailMembers = filteredEventMembers.stream()
                        .filter(em -> em.getHasEmail() != null && em.getHasEmail() &&
                                em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());
            } else {
                // For cases without eventId, use old logic but needs improvement
                List<EventMember> eventMembers = getFilteredMembers(criteria, eventId);

                validEmailMembers = eventMembers.stream()
                        .filter(em -> em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());
            }

            // Convert to preview format
            List<Map<String, Object>> memberPreview = validEmailMembers.stream().map(member -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", member.getId());
                info.put("name", member.getName());
                info.put("primaryEmail", member.getPrimaryEmail());
                info.put("membershipNumber", member.getMembershipNumber());
                info.put("regionDesc", member.getRegionDesc());
                info.put("siteIndustryDesc", member.getSiteIndustryDesc());
                info.put("siteSubIndustryDesc", member.getSiteSubIndustryDesc());
                info.put("workplaceDesc", member.getWorkplaceDesc());
                info.put("employerName", member.getEmployer());
                info.put("branchDesc", member.getBranch());
                info.put("forumDesc", member.getForumDesc());
                info.put("membershipTypeDesc", member.getMembershipTypeDesc());
                info.put("genderDesc", member.getGenderDesc());
                info.put("ethnicRegionDesc", member.getEthnicRegionDesc());
                info.put("occupation", member.getOccupation());
                info.put("bargainingGroupDesc", member.getBargainingGroupDesc());
                info.put("verificationCode", member.getVerificationCode());
                // CRITICAL: 添加注册状态信息
                info.put("hasRegistered", member.getHasRegistered());
                info.put("isAttending", member.getIsAttending());
                info.put("hasVoted", member.getHasVoted());
                return info;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();
            data.put("members", memberPreview);
            data.put("totalCount", validEmailMembers.size());
            data.put("appliedCriteria", criteria);

            response.put("status", "success");
            response.put("message", String.format("Preview generated: %d members with valid emails",
                    validEmailMembers.size()));
            response.put("data", data);

            log.info("Preview generated: {} members with valid emails", validEmailMembers.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to preview emails by criteria", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to preview emails: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    CRITICAL: 新增：基于多表联合查询的高级邮件预览方法
    @PostMapping("/preview-advanced")
    public ResponseEntity<Map<String, Object>> previewEmailsAdvanced(@RequestBody Map<String, Object> request) {
        log.info("=== Advanced Email preview with multi-table join query requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            // If eventId is null, get the current BMM event automatically
            if (eventId == null) {
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                Event currentBmmEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);

                if (currentBmmEvent == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "No BMM event found");
                    return ResponseEntity.badRequest().body(response);
                }

                eventId = currentBmmEvent.getId();
                log.info("Using current BMM event: {} (ID: {})", currentBmmEvent.getName(), eventId);
            }

            log.info("Advanced email filtering for eventId: {} with criteria: {}", eventId, criteria);

            // CRITICAL: 使用多表联合查询过滤EventMember
            List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

            // 只保留有有效邮箱的成员用于邮件发送 - 使用EventMember自身字段
            List<EventMember> emailableMembers = filteredEventMembers.stream()
                    .filter(em -> em.getHasEmail() != null && em.getHasEmail() &&
                            em.getPrimaryEmail() != null &&
                            !em.getPrimaryEmail().trim().isEmpty() &&
                            !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .collect(Collectors.toList());

            // Convert to DTO format with richer data for frontend display - 使用EventMember字段
            List<Map<String, Object>> memberDTOs = emailableMembers.stream().map(eventMember -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", eventMember.getId());
                dto.put("eventMemberId", eventMember.getId());
                dto.put("name", eventMember.getName());
                dto.put("primaryEmail", eventMember.getPrimaryEmail());
                dto.put("membershipNumber", eventMember.getMembershipNumber());
                dto.put("verificationCode", eventMember.getVerificationCode());
                // EventMember状态信息
                dto.put("hasRegistered", eventMember.getHasRegistered());
                dto.put("isAttending", eventMember.getIsAttending());
                dto.put("hasVoted", eventMember.getHasVoted());
                dto.put("checkedIn", eventMember.getCheckedIn());
                // EventMember详细信息
                dto.put("regionDesc", eventMember.getRegionDesc());
                dto.put("genderDesc", eventMember.getGenderDesc());
                dto.put("ageOfMember", eventMember.getAgeOfMember());
                dto.put("siteIndustryDesc", eventMember.getSiteIndustryDesc());
                dto.put("siteSubIndustryDesc", eventMember.getSiteSubIndustryDesc());
                dto.put("workplaceDesc", eventMember.getWorkplaceDesc());
                dto.put("employerName", eventMember.getEmployer());
                dto.put("bargainingGroupDesc", eventMember.getBargainingGroupDesc());
                dto.put("employmentStatus", eventMember.getEmploymentStatus());
                dto.put("ethnicRegionDesc", eventMember.getEthnicRegionDesc());
                dto.put("jobTitle", eventMember.getJobTitle());
                dto.put("department", eventMember.getDepartment());
                dto.put("siteNumber", eventMember.getSiteCode());
                // Add mobile fields for SMS page usage
                dto.put("telephoneMobile", eventMember.getTelephoneMobile());
                dto.put("primaryMobile", eventMember.getTelephoneMobile()); // Alias for frontend consistency
                dto.put("hasMobile", eventMember.getHasMobile());
                dto.put("hasEmail", eventMember.getHasEmail());
                return dto;
            }).collect(Collectors.toList());

            // 获取详细的过滤预览统计
            Map<String, Object> previewStats = eventMemberTargetingService.getFilterPreview(eventId, criteria);

            Map<String, Object> data = new HashMap<>();
            data.put("members", memberDTOs);
            data.put("totalCount", emailableMembers.size());
            data.put("totalFiltered", filteredEventMembers.size());
            data.put("emailableCount", emailableMembers.size());
            data.put("nonEmailableCount", filteredEventMembers.size() - emailableMembers.size());
            data.put("stats", previewStats);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Advanced email preview generated successfully");
            response.put("data", data);

            log.info("Advanced email preview generated: {} total filtered, {} email-able recipients",
                    filteredEventMembers.size(), emailableMembers.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate advanced email preview", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to generate advanced email preview: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    CRITICAL: 新增：基于多表联合查询的高级邮件发送
    @PostMapping("/send-advanced")
    public ResponseEntity<Map<String, Object>> sendEmailsAdvanced(@RequestBody Map<String, Object> request) {
        log.info("=== Advanced Email send with multi-table join query requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            String provider = (String) request.get("provider"); // 🔧 添加provider参数

            // 🔍 调试日志
            log.info("🔍 SEND-ADVANCED PROVIDER RECEIVED: [{}]", provider);

            // Default to STRATUM if not specified
            if (provider == null || provider.trim().isEmpty()) {
                provider = "STRATUM";
                log.info("🔧 Provider not specified, defaulting to: {}", provider);
            }

            if (eventId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event ID is required for advanced email sending");
                return ResponseEntity.badRequest().body(response);
            }

            if (subject == null || subject.trim().isEmpty() || content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Email subject and content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Advanced email sending for eventId: {} with criteria: {}", eventId, criteria);

            // Check if specific memberIds are provided
            List<EventMember> emailableMembers;
            @SuppressWarnings("unchecked")
            List<Object> memberIdsList = criteria != null ? (List<Object>) criteria.get("memberIds") : null;

            if (memberIdsList != null && !memberIdsList.isEmpty()) {
                // User selected specific members - ONLY send to these members
                log.info("Using specific memberIds: {}", memberIdsList);
                List<Long> memberIds = memberIdsList.stream()
                        .map(id -> Long.parseLong(id.toString()))
                        .collect(Collectors.toList());

                emailableMembers = memberIds.stream()
                        .map(id -> eventMemberRepository.findById(id).orElse(null))
                        .filter(Objects::nonNull)
                        .filter(em -> em.getEvent().getId().equals(eventId)) // Ensure same event
                        .filter(em -> em.getHasEmail() != null && em.getHasEmail() &&
                                em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());

                log.info("Found {} valid members from {} selected IDs", emailableMembers.size(), memberIds.size());
            } else {
                // No specific members selected - use filter criteria
                log.info("No specific memberIds provided, using filter criteria");
                List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

                emailableMembers = filteredEventMembers.stream()
                        .filter(em -> em.getHasEmail() != null && em.getHasEmail() &&
                                em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                        .collect(Collectors.toList());
            }

            log.info("Starting advanced email send to {} recipients", emailableMembers.size());

            // 发送邮件
            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : emailableMembers) {
                try {
                    String memberEmail = eventMember.getPrimaryEmail();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // 个性化邮件内容
                    String membershipNumber = eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "BULK_" + eventMember.getId();
                    String personalizedSubject = subject
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber);

                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{region}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{regionDesc}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{workplaceDesc}}", eventMember.getWorkplaceDesc() != null ? eventMember.getWorkplaceDesc() : "")
                            .replace("{{employerName}}", eventMember.getEmployer() != null ? eventMember.getEmployer() : "")
                            // BMM specific variables
                            .replace("{{bmmLink}}", eventMember.getToken() != null ? generateBmmLinkBasedOnStage(eventMember) : "https://events.etu.nz/")
                            .replace("{{preferenceLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{confirmationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember, "confirmation") : "https://events.etu.nz/")
                            .replace("{{assignedVenue}}", eventMember.getAssignedVenueFinal() != null ? eventMember.getAssignedVenueFinal() : (eventMember.getAssignedVenue() != null ? eventMember.getAssignedVenue() : ""))
                            .replace("{{assignedDateTime}}", eventMember.getAssignedDatetimeFinal() != null ? eventMember.getAssignedDatetimeFinal().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : (eventMember.getAssignedDateTime() != null ? eventMember.getAssignedDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : ""))
                            .replace("{{assignedSession}}", extractSessionTime(eventMember))
                            .replace("{{ticketUrl}}", eventMember.getTicketToken() != null ? "https://events.etu.nz/ticket?token=" + eventMember.getTicketToken() : "")
                            .replace("{{memberToken}}", eventMember.getToken() != null ? eventMember.getToken().toString() : "");

                    // 通过RabbitMQ队列发送
                    Map<String, Object> emailData = new HashMap<>();
                    emailData.put("recipient", memberEmail);
                    emailData.put("recipientName", memberName);
                    emailData.put("subject", personalizedSubject);
                    emailData.put("content", personalizedContent);
                    emailData.put("eventMemberId", eventMember.getId());
                    emailData.put("memberId", eventMember.getId());
                    emailData.put("membershipNumber", membershipNumber);
                    emailData.put("templateCode", "ADVANCED_FILTERED_EMAIL");
                    emailData.put("notificationType", "EMAIL");
                    emailData.put("provider", provider); // 🔧 添加provider参数！
                    emailData.put("timestamp", LocalDateTime.now().toString());

                    // Debug log before sending to queue
                    log.info("📧 Sending to RabbitMQ - Recipient: {}, Subject: [{}], Content length: {}, Provider: [{}]",
                            memberEmail, personalizedSubject, personalizedContent.length(), provider);

                    rabbitTemplate.convertAndSend(emailQueue, emailData);
                    successCount++;
                    log.debug("Email queued successfully for: {} (Region: {}, Industry: {}, Registered: {})",
                            memberEmail, eventMember.getRegionDesc(), eventMember.getSiteSubIndustryDesc(), eventMember.getHasRegistered());

                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send email to EventMember {}: {}", eventMember.getId(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Advanced email sending completed: %d success, %d failed", successCount, failCount));
            response.put("data", Map.of(
                    "emailableCount", emailableMembers.size(),
                    "successCount", successCount,
                    "failCount", failCount
            ));

            log.info("Advanced email sending completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send advanced emails", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send advanced emails: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //Helper Methods
//    Get member list by type
    private List<EventMember> getMembersByType(String emailType) {
        try {
            // 直接返回EventMembers，不再做映射
            return getEventMembersByType(emailType);
        } catch (Exception e) {
            log.error("Error in getMembersByType for type '{}': {}", emailType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //    Get member list by event and type
    private List<EventMember> getMembersByEventAndType(Long eventId, String emailType) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                log.warn("Event with ID {} not found", eventId);
                return new ArrayList<>();
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers;

            if (emailType == null || emailType.trim().isEmpty()) {
                log.warn("EmailType is null or empty, returning empty list");
                return new ArrayList<>();
            }

            switch (emailType.toLowerCase()) {
                case "all":
                    eventMembers = eventMemberRepository.findByEvent(event);
                    break;
                case "registered":
                    eventMembers = eventMemberRepository.findByEventAndHasRegisteredTrue(event);
                    break;
                case "unregistered":
                    eventMembers = eventMemberRepository.findByEventAndHasRegisteredFalse(event);
                    break;
                case "attending":
                    eventMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);
                    break;
                case "not_attending":
                    eventMembers = eventMemberRepository.findByEvent(event).stream()
                            .filter(em -> !em.getIsAttending())
                            .collect(Collectors.toList());
                    break;
                case "special_vote":
                    eventMembers = eventMemberRepository.findByEventAndIsSpecialVoteTrue(event);
                    break;
                case "voted":
                    eventMembers = eventMemberRepository.findByEventAndHasVotedTrue(event);
                    break;
                case "checked_in":
                    eventMembers = eventMemberRepository.findByEventAndCheckedInTrue(event);
                    break;
                default:
                    log.info("Unknown emailType '{}' for event, returning all event members", emailType);
                    eventMembers = eventMemberRepository.findByEvent(event);
                    break;
            }

//            直接返回EventMember对象
            return eventMembers;

        } catch (Exception e) {
            log.error("Error in getMembersByEventAndType for eventId '{}' and type '{}': {}", eventId, emailType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //    Get filtered members based on criteria
    private List<EventMember> getFilteredMembers(Map<String, Object> criteria, Long eventId) {
        try {
            List<EventMember> eventMembers;

            if (eventId != null) {
                // Get members from specific event
                Optional<Event> eventOpt = eventRepository.findById(eventId);
                if (!eventOpt.isPresent()) {
                    log.warn("Event with ID {} not found", eventId);
                    return new ArrayList<>();
                }
                Event event = eventOpt.get();
                eventMembers = eventMemberRepository.findByEvent(event);
            } else {
                // Get current BMM event by default
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                Event currentBmmEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);

                if (currentBmmEvent == null) {
                    log.warn("No BMM event found, returning empty list");
                    return new ArrayList<>();
                }
                eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            }

            // Apply filters on EventMember data
            Stream<EventMember> filteredStream = eventMembers.stream();

            // Registration status filter
            String registrationStatus = (String) criteria.get("registrationStatus");
            if (registrationStatus != null && !registrationStatus.isEmpty()) {
                switch (registrationStatus) {
                    case "registered":
                        filteredStream = filteredStream.filter(em -> em.getHasRegistered() != null && em.getHasRegistered());
                        break;
                    case "not_registered":
                        filteredStream = filteredStream.filter(em -> em.getHasRegistered() == null || !em.getHasRegistered());
                        break;
                    case "attending":
                        filteredStream = filteredStream.filter(em -> em.getIsAttending() != null && em.getIsAttending());
                        break;
                    case "not_attending":
                        filteredStream = filteredStream.filter(em -> em.getIsAttending() != null && !em.getIsAttending());
                        break;
                    case "special_vote":
                        filteredStream = filteredStream.filter(em -> em.getIsSpecialVote() != null && em.getIsSpecialVote());
                        break;
                }
            }

            // Region filter - 添加region mapping支持
            String region = (String) criteria.get("region");
            if (region != null && !region.isEmpty()) {
                // 🔧 Region mapping: 前端传"Northern Region" -> 数据库可能存"Northern"
                String dbRegionValue = region.contains(" Region") ?
                        region.replace(" Region", "") : region;
                filteredStream = filteredStream.filter(em ->
                        em.getRegionDesc() != null &&
                                (em.getRegionDesc().equals(region) || em.getRegionDesc().equals(dbRegionValue)));
            }

            // Industry filter
            String industry = (String) criteria.get("industry");
            if (industry != null && !industry.isEmpty()) {
                filteredStream = filteredStream.filter(em ->
                        em.getSiteIndustryDesc() != null &&
                                em.getSiteIndustryDesc().contains(industry));
            }

            // Workplace filter
            String workplace = (String) criteria.get("workplace");
            if (workplace != null && !workplace.isEmpty()) {
                filteredStream = filteredStream.filter(em -> workplace.equals(em.getWorkplace()));
            }

            // Employer filter
            String employer = (String) criteria.get("employer");
            if (employer != null && !employer.isEmpty()) {
                filteredStream = filteredStream.filter(em -> employer.equals(em.getEmployer()));
            }

            // 直接返回EventMember对象
            return filteredStream.collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in getFilteredMembers: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private String generateRegistrationLinkWithEvent(EventMember eventMember) {
        return generateRegistrationLinkWithEvent(eventMember, "pre-registration");
    }

    private String generateRegistrationLinkWithEvent(EventMember eventMember, String stage) {
        // 🔧 修复：基于EventMember的token生成链接，确保BMM事件架构一致性
        try {
            if (eventMember.getToken() == null) {
                log.warn("EventMember token is null for member: {}", eventMember.getMembershipNumber());
                return "https://events.etu.nz/";
            }

            // 🔄 Different URLs based on stage
            String basePath = "";
            switch (stage) {
                case "pre-registration":
                    basePath = "/register/bmm-template";  // Direct to preference registration form
                    break;
                case "pre-registration-northern":
                    basePath = "/register/bmm-template";  // Direct to preference registration form
                    break;
                case "pre-registration-central":
                    basePath = "/register/bmm-template";  // Direct to preference registration form
                    break;
                case "pre-registration-southern":
                    basePath = "/register/bmm-template";  // Direct to preference registration form
                    break;
                case "confirmation":
                    basePath = "/bmm/confirmation";  // Direct to confirmation page
                    break;
                case "confirmation-northern":
                    basePath = "/register/confirm-northern";
                    break;
                case "confirmation-central":
                    basePath = "/register/confirm-central";
                    break;
                case "confirmation-southern":
                    basePath = "/register/confirm-southern";
                    break;
                case "special-vote":
                    basePath = "/register/special-vote";
                    break;
                default:
                    basePath = "/register/bmm-template";  // Default to preference registration form
            }

            // For BMM events, use memberToken instead of regular token
            String tokenParam = eventMember.getMemberToken() != null ? eventMember.getMemberToken() : eventMember.getToken().toString();
            String registrationLink = "https://events.etu.nz" + basePath + "?token=" + tokenParam;

            // 简化：不需要额外的参数，每个页面独立处理
            return registrationLink;
        } catch (Exception e) {
            log.error("Failed to generate registration link for member {}: {}", eventMember.getMembershipNumber(), e.getMessage());
            return "https://events.etu.nz/";
        }
    }

    // 🔄 BMM分阶段邮件发送endpoint
    @PostMapping("/send-bmm-stage")
    public ResponseEntity<Map<String, Object>> sendBMMStageEmails(@RequestBody Map<String, Object> request) {
        log.info("=== BMM Stage Email Send Requested ===");

        try {
            String stage = (String) request.get("stage"); // "pre-registration", "confirmation", or "special-vote"
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            String emailType = (String) request.get("emailType"); // e.g., "all", "northern", "central", "southern"

            // Use predefined templates if subject/content not provided
            if (subject == null || content == null) {
                Map<String, String> template = getBMMEmailTemplate(stage, emailType);
                if (subject == null) subject = template.get("subject");
                if (content == null) content = template.get("content");
            }

            if (stage == null || subject == null || content == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Stage, subject and content are required");
                return ResponseEntity.badRequest().body(response);
            }

            // Get members based on email type
            List<EventMember> members = getMembersByType(emailType != null ? emailType : "all");

            // Filter members with valid email addresses
            members = members.stream()
                    .filter(m -> m.getPrimaryEmail() != null &&
                            !m.getPrimaryEmail().trim().isEmpty() &&
                            !m.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .collect(Collectors.toList());

            log.info("Sending {} stage emails to {} recipients", stage, members.size());

            int successCount = 0;
            int failCount = 0;

            for (EventMember member : members) {
                try {
                    String memberEmail = member.getPrimaryEmail();
                    String memberName = member.getName() != null ? member.getName() : "Member";

                    // Generate appropriate link based on stage and region
                    String linkStage = stage;
                    if (member.getRegionDesc() != null) {
                        // Match database regionDesc values: "Northern", "Central", "Southern"
                        switch (member.getRegionDesc()) {
                            case "Northern":
                                if ("pre-registration".equals(stage)) {
                                    linkStage = "pre-registration-northern";
                                } else if ("confirmation".equals(stage)) {
                                    linkStage = "confirmation-northern";
                                }
                                break;
                            case "Central":
                                if ("pre-registration".equals(stage)) {
                                    linkStage = "pre-registration-central";
                                } else if ("confirmation".equals(stage)) {
                                    linkStage = "confirmation-central";
                                }
                                break;
                            case "Southern":
                                if ("pre-registration".equals(stage)) {
                                    linkStage = "pre-registration-southern";
                                } else if ("confirmation".equals(stage)) {
                                    linkStage = "confirmation-southern";
                                }
                                break;
                            default:
                                linkStage = stage;
                        }
                    }

                    // Replace template variables
                    String personalizedContent = content
                            .replace("{{name}}", getFirstName(member))
                            .replace("{{firstName}}", getFirstName(member))
                            .replace("{{membershipNumber}}", member.getMembershipNumber() != null ? member.getMembershipNumber() : "")
                            .replace("{{region}}", member.getRegionDesc() != null ? member.getRegionDesc() : "")
                            .replace("{{stageLinkUrl}}", member.getToken() != null ? generateRegistrationLinkWithEvent(member, linkStage) : "https://events.etu.nz/")
                            .replace("{{preRegistrationLink}}", member.getToken() != null ? generateRegistrationLinkWithEvent(member, "pre-registration") : "https://events.etu.nz/")
                            .replace("{{confirmationLink}}", member.getToken() != null ? generateRegistrationLinkWithEvent(member, linkStage) : "https://events.etu.nz/");

                    // Use EventMember directly
                    Long eventMemberId = member.getId();

                    // Send via RabbitMQ queue
                    Map<String, Object> emailData = Map.of(
                            "recipient", memberEmail,
                            "recipientName", memberName,
                            "subject", subject,
                            "content", personalizedContent,
                            "eventMemberId", eventMemberId,
                            "memberId", member.getId(),
                            "membershipNumber", member.getMembershipNumber(),
                            "templateCode", "BMM_" + stage.toUpperCase(),
                            "notificationType", "EMAIL"
                    );
                    rabbitTemplate.convertAndSend(emailQueue, emailData);

                    // Create notification log
                    NotificationLog emailLog = NotificationLog.builder()
                            .eventMember(member)
                            .notificationType(NotificationLog.NotificationType.EMAIL)
                            .recipient(memberEmail)
                            .recipientName(memberName)
                            .subject(subject)
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("BMM_" + stage.toUpperCase())
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(emailLog);

                    successCount++;
                    log.info("BMM {} email queued successfully for: {}", stage, memberEmail);
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to queue BMM {} email for member {}: {}", stage, member.getMembershipNumber(), e.getMessage());
                }
            }

            // Return response
            Map<String, Object> data = new HashMap<>();
            data.put("sent", successCount);
            data.put("failed", failCount);
            data.put("total", members.size());
            data.put("stage", stage);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("BMM %s emails queued: %d success, %d failed", stage, successCount, failCount));
            response.put("data", data);

            log.info("BMM {} email sending completed: {} success, {} failed out of {} total",
                    stage, successCount, failCount, members.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send BMM stage emails:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send BMM stage emails: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // BMM Email Templates - Three stages x Three regions
    private Map<String, String> getBMMEmailTemplate(String stage, String region) {
        Map<String, String> template = new HashMap<>();

        switch (stage) {
            case "pre-registration":
                template = getPreRegistrationTemplate(region);
                break;
            case "confirmation":
                template = getConfirmationTemplate(region);
                break;
            case "special-vote":
                template = getSpecialVoteTemplate(region);
                break;
            default:
                template = getDefaultTemplate();
        }

        return template;
    }

    // Stage 1: Pre-registration interest collection email template
    private Map<String, String> getPreRegistrationTemplate(String region) {
        Map<String, String> template = new HashMap<>();

        template.put("subject", "Register your interest now for the 2025 E tū Biennial Membership Meetings");

        StringBuilder content = new StringBuilder();
        content.append("=== {{region}} Region ===\n\n");
        content.append("Kia ora {{firstName}},\n\n");

        content.append("We're pleased to invite you to attend the 2025 E tū Biennial Membership Meetings (BMMs) – your opportunity to stay informed, have your say, and help shape the future direction of our union.\n\n");

        content.append("WHAT ARE THE BMMs ABOUT?\n");
        content.append("Held every two years, the BMMs are a vital part of our union democracy. At these meetings, members will:\n");
        content.append("• Hear updates on current issues and union campaigns\n");
        content.append("• Discuss E tū's strategic direction\n");
        content.append("• Vote on any matters referred from the E tū Biennial Conference\n");

        // Southern Region special note
        if ("southern".equals(region) || region.toLowerCase().contains("southern")) {
            content.append("• Elect one National Executive Regional Representative (Southern Region only)\n");
        }
        content.append("\n");

        content.append("MEETING DETAILS\n");
        content.append("The meetings will take place in September 2025. At this stage, 27 meetings have been planned across the country. After reviewing members' pre-registrations, we may be able to add more meetings to make sure everyone has a chance to be involved.\n\n");

        content.append("PAID ATTENDANCE AND TRANSPORT\n");
        content.append("In line with Section 26 of the Employment Relations Act, this is a paid union meeting, and you are entitled to attend during paid work hours. Where possible, we will also help with transport for groups of members from the same workplace.\n\n");

        content.append("PRE-REGISTER NOW\n");
        content.append("We're asking all members to register their interest by 31 July 2025. Once we receive your pre-registration, we'll send you the final meeting details and ask you to confirm that you're coming from 1 August 2025.\n\n");

        content.append("CLICK HERE TO PRE-REGISTER:\n");
        content.append("{{stageLinkUrl}}\n\n");

        content.append("THE REGISTRATION FORM WILL ASK YOU:\n");

        // Region-specific venue counts
        String venueCount = "multiple";
        if ("northern".equals(region) || region.toLowerCase().contains("northern")) {
            venueCount = "12";
        } else if ("central".equals(region) || region.toLowerCase().contains("central")) {
            venueCount = "6";
        } else if ("southern".equals(region) || region.toLowerCase().contains("southern")) {
            venueCount = "9";
        }

        content.append("1. Your preferred venue from " + venueCount + " available locations in the {{region}} Region\n");
        content.append("2. Whether you would like to attend a BMM (Yes/No)\n");
        content.append("3. Your preferred meeting times:\n");
        content.append("   • Morning (9:00 AM - 12:00 PM)\n");
        content.append("   • Lunchtime (12:00 PM - 2:00 PM)\n");
        content.append("   • Afternoon (1:00 PM - 5:00 PM)\n");
        content.append("   • After-work (5:00 PM - 7:00 PM)\n");
        content.append("   • Night-shift (7:00 PM - 9:00 PM)\n");
        content.append("4. If you can't make any of the listed meetings, please let us know:\n");
        content.append("   • Your workplace name and location\n");
        content.append("5. Any special requirements or additional comments\n\n");

        // Southern Region special vote section (exclude Northern and Central)
        if ("southern".equals(region)) {
            content.append("*** SPECIAL VOTE APPLICATIONS (SOUTHERN REGION MEMBERS) ***\n");
            content.append("If you can't attend a meeting due to illness, disability, or other unexpected circumstances, you may apply for a special vote. This application must be made at least 14 days before the meeting where a secret vote is to be held. The Returning Officer will issue a special vote form to eligible members.\n\n");
        }

        content.append("YOUR VOICE MATTERS\n");
        content.append("We encourage all members to take part. These meetings are a key part of making sure our union remains strong, democratic, and member-led.\n\n");

        content.append("If you have any questions, feel free to contact us at support@etu.nz or call 0800 1 UNION (0800 186 466).\n\n");

        content.append("In solidarity,\n");
        content.append("Rachel Mackintosh\n");
        content.append("National Secretary");

        template.put("content", content.toString());
        return template;
    }

    // Stage 2: Attendance confirmation email template
    private Map<String, String> getConfirmationTemplate(String region) {
        Map<String, String> template = new HashMap<>();

        template.put("subject", "Confirm your attendance – 2025 E tu Biennial Membership Meeting");

        StringBuilder content = new StringBuilder();
        content.append("=== {{region}} Region ===\n\n");
        content.append("Kia ora {{firstName}},\n\n");

        content.append("Thank you for pre-registering your interest in attending the 2025 E tu Biennial Membership Meetings (BMMs). We're now asking you to CONFIRM YOUR ATTENDANCE at your selected meeting.\n\n");

        content.append("YOUR MEETING DETAILS\n");
        content.append("Here are the details of the meeting you indicated interest in:\n");
        content.append("• Location: {{location}}\n");
        content.append("• Date: {{date}}\n");
        content.append("• Time: {{time}}\n");
        content.append("• Region: {{region}} Region\n\n");

        content.append("CONFIRM YOUR ATTENDANCE & RECEIVE YOUR TICKET:\n");
        content.append("{{confirmationLink}}\n\n");

        content.append("WHY YOU NEED YOUR TICKET\n");
        content.append("Once you confirm, you will receive your personalised BMM ticket, which you must bring to the meeting (either printed or on your phone). This ticket will be used to register your attendance on the day. WITHOUT A TICKET, YOU MAY NOT BE ABLE TO ENTER THE MEETING.\n\n");

        content.append("YOUR TICKET INFORMATION\n");
        content.append("After confirming your attendance, you will receive:\n");
        content.append("• A personalised ticket with QR code (for members with email addresses)\n");
        content.append("• An SMS ticket confirmation (for members without email addresses)\n");
        content.append("• Meeting location and time details\n");
        content.append("• Check-in instructions\n\n");

        content.append("TICKET ACCESS\n");
        content.append("Your ticket link: {{ticketUrl}}\n");
        content.append("Please bookmark this link or save it to your phone for easy access.\n\n");

        content.append("PAID UNION MEETING\n");
        content.append("This is a paid union meeting under Section 26 of the Employment Relations Act. You are entitled to attend during paid work hours. Please discuss with your employer about your attendance.\n\n");

        // Central and Southern Region voting section
        if ("central".equals(region) || "southern".equals(region) || (region != null && (region.toLowerCase().contains("central") || region.toLowerCase().contains("southern")))) {
            String regionName = "central".equals(region) || (region != null && region.toLowerCase().contains("central")) ? "Central" : "Southern";
            content.append("*** " + regionName.toUpperCase() + " REGION VOTING RIGHTS ***\n");
            content.append("As a " + regionName + " Region member, you will have the opportunity to vote for your regional representative at this meeting. Your attendance and participation are important for our union democracy.\n\n");

            content.append("If you cannot attend but are eligible to vote, you may apply for a special vote. Applications must be submitted at least 14 days before the meeting.\n\n");
        }

        content.append("MEETING AGENDA\n");
        content.append("At this meeting, you will:\n");
        content.append("• Receive updates on current union campaigns and issues\n");
        content.append("• Discuss E tu's strategic direction for the next two years\n");
        content.append("• Vote on matters referred from the E tu Biennial Conference\n");
        if ("southern".equals(region)) {
            content.append("• Vote for the National Executive Southern Region Representative\n");
        }
        content.append("• Have your say on issues that matter to you\n\n");

        content.append("QUESTIONS OR NEED HELP?\n");
        content.append("If you have any questions about the meeting or need assistance, please contact us:\n");
        content.append("Email: support@etu.nz\n");
        content.append("Phone: 0800 1 UNION (0800 186 466)\n\n");

        content.append("We look forward to seeing you at the meeting!\n\n");

        content.append("In solidarity,\n");
        content.append("Rachel Mackintosh\n");
        content.append("National Secretary\n");
        content.append("E tu Union");

        template.put("content", content.toString());
        return template;
    }

    // EMAIL: 阶段三：特殊投票申请邮件模板 (Southern Region)
    private Map<String, String> getSpecialVoteTemplate(String region) {
        Map<String, String> template = new HashMap<>();

        // Special vote for Southern Region only
        if ("northern".equals(region) || "central".equals(region)) {
            return getDefaultTemplate();
        }

        template.put("subject", "Special Vote Application – BMM Election");

        StringBuilder content = new StringBuilder();
        content.append("=== Central and Southern Region Members ===\n\n");
        content.append("Kia ora {{firstName}},\n\n");

        content.append("As a Central or Southern Region member, you have the right to vote for your regional representative at the upcoming Biennial Membership Meeting.\n\n");

        content.append("If you are UNABLE TO ATTEND the BMM in your area but still wish to participate in the election, you may be eligible to apply for a SPECIAL VOTE.\n\n");

        content.append("ELIGIBILITY CRITERIA\n");
        content.append("To qualify for a special vote, one of the following must apply to you:\n");
        content.append("• You have a disability that prevents you from fully participating in the meeting\n");
        content.append("• You are ill or infirm, making attendance impossible\n");
        content.append("• You live more than 32km from the meeting venue\n");
        content.append("• Your employer requires you to work during the time of the meeting\n");
        content.append("• Attending the meeting would cause you serious hardship or major inconvenience\n\n");

        content.append("*** IMPORTANT DEADLINES ***\n");
        content.append("Special vote applications must be made at least 14 days before the start of the BMM at which the secret ballot is to be held.\n\n");
        content.append("If approved, a ballot paper will be issued to you by the Returning Officer.\n\n");

        content.append("HOW TO APPLY\n");
        content.append("To apply for a special vote:\n");
        content.append("1. Complete the special vote application form\n");
        content.append("2. Provide evidence supporting your eligibility (if applicable)\n");
        content.append("3. Submit your application at least 14 days before the meeting\n\n");

        content.append("APPLY FOR SPECIAL VOTE:\n");
        content.append("{{stageLinkUrl}}\n\n");

        content.append("QUESTIONS ABOUT VOTING?\n");
        content.append("If you have any questions about the voting process or special vote eligibility, please contact our Returning Officer at:\n");
        content.append("returningofficer@etu.nz\n\n");

        content.append("For general questions, contact us at:\n");
        content.append("support@etu.nz | 0800 1 UNION (0800 186 466)\n\n");

        content.append("In solidarity,\n");
        content.append("Rachel Mackintosh\n");
        content.append("National Secretary");

        template.put("content", content.toString());
        return template;
    }

    // Default template
    private Map<String, String> getDefaultTemplate() {
        Map<String, String> template = new HashMap<>();
        template.put("subject", "E tū Biennial Membership Meeting – {{region}} Region");
        template.put("content", "Kia ora {{firstName}},\n\nThank you for your interest in the E tū Biennial Membership Meeting.\n\nLink: {{stageLinkUrl}}\n\nIn solidarity,\nRachel Mackintosh\nNational Secretary");
        return template;
    }

    // 📱 SMS模板获取方法 - 完善ticket信息
    public Map<String, String> getBMMSMSTemplate(String stage, String region) {
        Map<String, String> template = new HashMap<>();

        switch (stage) {
            case "pre-registration":
                template.put("content", "Kia ora {{firstName}}! E tu BMM 2025: Register your interest for " + region + " Region biennial membership meeting. Sept 2025, paid union meeting. Deadline: 31 July 2025. Register: {{stageLinkUrl}}");
                break;
            case "confirmation":
                template.put("content", "E tu BMM: Confirm attendance for " + region + " Region. Location: {{location}}, Date: {{date}}, Time: {{time}}. Get ticket: {{confirmationLink}}");
                break;
            case "ticket-sms":
                // SMS ticket version for members without email - NO special characters
                template.put("content", "E tu BMM TICKET: {{name}} - {{membershipNumber}}. Location: {{location}}, Date: {{date}}, Time: {{time}}. Show this SMS at venue. Info: {{ticketUrl}}");
                break;
            case "special-vote":
                if ("central".equals(region) || "southern".equals(region)) {
                    String regionName = "central".equals(region) ? "Central" : "Southern";
                    template.put("content", regionName + " Region BMM: Apply for special vote if you cannot attend. Deadline: 14 days before meeting. {{stageLinkUrl}}");
                } else {
                    template.put("content", "Special vote not applicable for " + region + " Region.");
                }
                break;
            default:
                template.put("content", "E tu BMM 2025: Your union meeting link {{stageLinkUrl}}");
        }

        return template;
    }

    // Helper Methods
    // Get EventMember list by type (BMM优化版本)
    private List<EventMember> getEventMembersByType(String emailType) {
        try {
            if (emailType == null || emailType.trim().isEmpty()) {
                log.warn("EmailType is null or empty, returning empty list");
                return new ArrayList<>();
            }

            // Get current BMM event first
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                log.warn("No BMM event found, returning empty list");
                return new ArrayList<>();
            }

            List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            Stream<EventMember> filteredStream = eventMembers.stream();

            switch (emailType.toLowerCase()) {
                case "all":
                    break; // No additional filtering
                case "registered":
                    filteredStream = filteredStream.filter(em -> em.getHasRegistered() != null && em.getHasRegistered());
                    break;
                case "unregistered":
                    filteredStream = filteredStream.filter(em -> em.getHasRegistered() == null || !em.getHasRegistered());
                    break;
                case "attending":
                    filteredStream = filteredStream.filter(em -> em.getIsAttending() != null && em.getIsAttending());
                    break;
                case "email_failed":
                    filteredStream = filteredStream.filter(em -> "FAILED".equals(em.getEmailSent()));
                    break;
                case "not_sent":
                    filteredStream = filteredStream.filter(em -> em.getEmailSent() == null || "NOT_SENT".equals(em.getEmailSent()));
                    break;
                case "has_email":
                    filteredStream = filteredStream.filter(em -> em.getHasEmail() != null && em.getHasEmail());
                    break;
                case "test_group":
                    filteredStream = filteredStream.filter(em ->
                            em.getEmployer() != null && "foodmanual training".equalsIgnoreCase(em.getEmployer()));
                    break;
                // BMM Stage-specific filters
                case "preference_submitted":
                    filteredStream = filteredStream.filter(em -> em.getHasRegistered() != null && em.getHasRegistered());
                    break;
                case "venue_assigned":
                    filteredStream = filteredStream.filter(em ->
                            em.getHasRegistered() != null && em.getHasRegistered() &&
                                    (em.getIsAttending() == null || !em.getIsAttending()));
                    break;
                case "special_vote":
                    filteredStream = filteredStream.filter(em -> em.getIsSpecialVote() != null && em.getIsSpecialVote());
                    break;
                case "northern":
                case "northern region":
                    filteredStream = filteredStream.filter(em ->
                            em.getRegionDesc() != null && em.getRegionDesc().toLowerCase().contains("northern"));
                    break;
                case "central":
                case "central region":
                    filteredStream = filteredStream.filter(em ->
                            em.getRegionDesc() != null && em.getRegionDesc().toLowerCase().contains("central"));
                    break;
                case "southern":
                case "southern region":
                    filteredStream = filteredStream.filter(em ->
                            em.getRegionDesc() != null && em.getRegionDesc().toLowerCase().contains("southern"));
                    break;
                default:
                    log.info("Unknown emailType '{}', using all BMM event members", emailType);
                    break;
            }

            return filteredStream.collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error in getEventMembersByType for type '{}': {}", emailType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // Generate registration link for EventMember (BMM优化版本)

    /**
     * Get email history with pagination
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse> getEmailHistory(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        try {
            log.info("Fetching email history with limit: {}, offset: {}", limit, offset);

            // Get email notification logs ordered by sent time DESC
            List<NotificationLog> logs = notificationLogRepository
                    .findByNotificationTypeOrderBySentTimeDesc(NotificationLog.NotificationType.EMAIL)
                    .stream()
                    .skip(offset)
                    .limit(limit)
                    .collect(Collectors.toList());

            // Transform to the format expected by frontend
            List<Map<String, Object>> historyData = logs.stream()
                    .map(log -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("sentAt", log.getSentTime());
                        item.put("createdAt", log.getSentTime());
                        item.put("subject", log.getSubject());
                        item.put("recipient", log.getRecipient());
                        item.put("recipientName", log.getRecipientName());
                        item.put("totalSent", 1); // Each log represents one email
                        item.put("sentCount", 1);
                        item.put("status", log.getIsSuccessful() ? "success" : "failed");
                        item.put("successful", log.getIsSuccessful());
                        item.put("failed", log.getIsSuccessful() ? 0 : 1);
                        item.put("emailType", log.getEmailType());
                        item.put("adminId", log.getAdminId());
                        return item;
                    })
                    .collect(Collectors.toList());

            log.info("Retrieved {} email history records", historyData.size());
            return ResponseEntity.ok(ApiResponse.success(historyData));

        } catch (Exception e) {
            log.error("Error fetching email history: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to fetch email history: " + e.getMessage()));
        }
    }

    private String generateBmmLinkBasedOnStage(EventMember eventMember) {
        // Determine the appropriate stage based on BMM stage
        String stage = "pre-registration"; // Default stage

        if (eventMember.getBmmStage() != null) {
            switch (eventMember.getBmmStage()) {
                case "PREFERENCE_SUBMITTED":
                case "VENUE_ASSIGNED":
                case "NOTIFIED":
                    stage = "confirmation";
                    break;
                case "ATTENDANCE_CONFIRMED":
                    stage = "ticket";
                    break;
                case "NOT_ATTENDING":
                    if ("Central Region".equals(eventMember.getRegionDesc()) ||
                            "Southern Region".equals(eventMember.getRegionDesc())) {
                        stage = "special-vote";
                    }
                    break;
                default:
                    stage = "pre-registration";
            }
        }

        return generateRegistrationLinkWithEvent(eventMember, stage);
    }

    // Extract session time based on member preferences
    private String extractSessionTime(EventMember eventMember) {
        // First check if this is a forumVenueMapping member
        String forumDesc = eventMember.getForumDesc();
        if (forumDesc != null && forumDesc.equals("Greymouth")) {
            // For these special forums, return special instructions
            return "Multiple venues and times available - see options below";
        }

        // Check if member has preferred times JSON
        String preferredTimesJson = eventMember.getPreferredTimesJson();
        if (preferredTimesJson != null && !preferredTimesJson.isEmpty()) {
            try {
                // Parse JSON array of preferred times
                if (preferredTimesJson.contains("morning")) {
                    return "10:30 AM";
                } else if (preferredTimesJson.contains("lunchtime")) {
                    return "12:30 PM";
                } else if (preferredTimesJson.contains("afternoon") ||
                        preferredTimesJson.contains("after work") ||
                        preferredTimesJson.contains("night shift")) {
                    return "2:30 PM";
                }
            } catch (Exception e) {
                log.error("Error parsing preferred times JSON: {}", e.getMessage());
            }
        }

        // If no matching preference, show both options
        return "10:30 AM or 12:30 PM (Please choose when you arrive)";
    }
}