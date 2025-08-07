package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.SmsService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Objects;

import nz.etu.voting.service.EventMemberTargetingService;

// Admin SMS sending controller and Supports bulk SMS sending and statistics
@Slf4j
@RestController
@RequestMapping("/api/admin/sms")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})

public class AdminSmsController {

    private final MemberRepository memberRepository;
    private final SmsService smsService;
    private final NotificationLogRepository notificationLogRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final RabbitTemplate rabbitTemplate;
    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;
    private final EventMemberTargetingService eventMemberTargetingService;

    // Get first name from EventMember
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

    //    Preview SMS recipients list
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewSmsRecipients(@RequestBody Map<String, Object> request) {
        log.info("=== Preview SMS recipients requested ===");
        try {
            String smsType = (String) request.get("smsType");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            Map<String, Object> response = new HashMap<>();
            List<EventMember> eventMembers;

            if (eventId != null) {
                // Get EventMembers from specific event
                log.info("Getting EventMembers for event ID: {} with type: {}", eventId, smsType);
                eventMembers = getEventMembersByEventAndType(eventId, smsType);
            } else {
                // Get global EventMembers (legacy mode)
                log.info("Getting global EventMembers with type: {}", smsType);
                eventMembers = getEventMembersBySmsType(smsType);
            }

            // Filter EventMembers with valid mobile numbers only
            eventMembers = eventMembers.stream()
                    .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                    .collect(Collectors.toList());

            // Convert EventMembers to DTO to avoid deep serialization
            List<Map<String, Object>> memberDTOs = eventMembers.stream().map(eventMember -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", eventMember.getId());
                dto.put("name", eventMember.getName());
                dto.put("mobile", eventMember.getTelephoneMobile());
                dto.put("membershipNumber", eventMember.getMembershipNumber());
                dto.put("verificationCode", eventMember.getVerificationCode());
                dto.put("hasRegistered", eventMember.getHasRegistered());
                dto.put("regionDesc", eventMember.getRegionDesc());
                dto.put("workplace", eventMember.getWorkplace());
                dto.put("employer", eventMember.getEmployer());
                return dto;
            }).collect(Collectors.toList());

            // Create data structure that matches frontend expectations
            Map<String, Object> data = new HashMap<>();
            data.put("members", memberDTOs);
            data.put("totalCount", memberDTOs.size());
            response.put("status", "success");
            response.put("message", "SMS preview list generated successfully");
            response.put("data", data);
            log.info("SMS preview generated: {} recipients for type '{}' (eventId: {})", eventMembers.size(), smsType, eventId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to preview SMS recipients", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to preview SMS recipients: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Preview SMS recipients by criteria
    @PostMapping("/preview-by-criteria")
    public ResponseEntity<Map<String, Object>> previewSmsByCriteria(@RequestBody Map<String, Object> request) {
        log.info("=== Preview SMS by criteria requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            if (criteria == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Criteria is required");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Filtering members by criteria: {} (eventId: {})", criteria, eventId);

            List<EventMember> validSmsMembers;

            if (eventId != null) {
                // CRITICAL: 使用多表联合查询 - 修复关键问题
                List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

                // Filter EventMembers with valid mobile numbers and convert to EventMembers
                validSmsMembers = filteredEventMembers.stream()
                        .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                        .collect(Collectors.toList());
            } else {
                // For cases without eventId, use old logic but needs improvement
                List<EventMember> filteredMembers = getFilteredEventMembers(criteria, eventId);

                // Filter EventMembers with valid mobile numbers only
                validSmsMembers = filteredMembers.stream()
                        .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                        .collect(Collectors.toList());
            }

            // Convert EventMembers to DTO to avoid deep serialization
            List<Map<String, Object>> memberDTOs = validSmsMembers.stream().map(eventMember -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", eventMember.getId());
                dto.put("name", eventMember.getName());
                dto.put("mobile", eventMember.getTelephoneMobile());
                dto.put("membershipNumber", eventMember.getMembershipNumber());
                dto.put("verificationCode", eventMember.getVerificationCode());
                dto.put("hasRegistered", eventMember.getHasRegistered());
                dto.put("regionDesc", eventMember.getRegionDesc());
                dto.put("siteIndustryDesc", eventMember.getSiteIndustryDesc());
                dto.put("siteSubIndustryDesc", eventMember.getSiteSubIndustryDesc());
                dto.put("workplace", eventMember.getWorkplace());
                dto.put("employer", eventMember.getEmployer());
                // CRITICAL: 添加更多状态信息
                dto.put("isAttending", eventMember.getIsAttending());
                dto.put("hasVoted", eventMember.getHasVoted());
                dto.put("genderDesc", eventMember.getGenderDesc());
                dto.put("bargainingGroupDesc", eventMember.getBargainingGroupDesc());
                return dto;
            }).collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("members", memberDTOs);
            data.put("totalCount", memberDTOs.size());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "SMS preview by criteria generated successfully");
            response.put("data", data);

            log.info("SMS preview by criteria generated: {} recipients", validSmsMembers.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to preview SMS by criteria", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to preview SMS by criteria: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    CRITICAL: 新增：基于多表联合查询的高级预览方法
    @PostMapping("/preview-advanced")
    public ResponseEntity<Map<String, Object>> previewSmsAdvanced(@RequestBody Map<String, Object> request) {
        log.info("=== Advanced SMS preview with multi-table join query requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            if (eventId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event ID is required for advanced filtering");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Advanced filtering for eventId: {} with criteria: {}", eventId, criteria);

            // CRITICAL: 使用多表联合查询过滤EventMember
            List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

            // Only keep SMS-only members (has mobile but no real email)
            List<EventMember> smsableMembers = filteredEventMembers.stream()
                    .filter(em -> {
                        boolean hasMobile = em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty();
                        boolean hasRealEmail = em.getPrimaryEmail() != null &&
                                !em.getPrimaryEmail().trim().isEmpty() &&
                                !em.getPrimaryEmail().contains("@temp-email.etu.nz");
                        // Only include if they have mobile and no real email
                        return hasMobile && !hasRealEmail;
                    })
                    .collect(Collectors.toList());

            // Convert to DTO format with richer data for frontend display
            List<Map<String, Object>> memberDTOs = smsableMembers.stream().map(eventMember -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", eventMember.getId());
                dto.put("eventMemberId", eventMember.getId());
                dto.put("name", eventMember.getName());
                dto.put("mobile", eventMember.getTelephoneMobile());
                dto.put("membershipNumber", eventMember.getMembershipNumber());
                dto.put("verificationCode", eventMember.getVerificationCode());
                // EventMember状态信息
                dto.put("hasRegistered", eventMember.getHasRegistered());
                dto.put("isAttending", eventMember.getIsAttending());
                dto.put("hasVoted", eventMember.getHasVoted());
                dto.put("checkedIn", eventMember.getCheckedIn());
                // Member详细信息
                dto.put("regionDesc", eventMember.getRegionDesc());
                dto.put("genderDesc", eventMember.getGenderDesc());
                dto.put("ageOfMember", eventMember.getAgeOfMember());
                dto.put("siteSubIndustryDesc", eventMember.getSiteSubIndustryDesc());
                dto.put("workplace", eventMember.getWorkplace());
                dto.put("employer", eventMember.getEmployer());
                dto.put("bargainingGroupDesc", eventMember.getBargainingGroupDesc());
                dto.put("employmentStatus", eventMember.getEmploymentStatus());
                dto.put("ethnicRegionDesc", eventMember.getEthnicRegionDesc());
                dto.put("jobTitle", eventMember.getJobTitle());
                dto.put("department", eventMember.getDepartment());
                dto.put("siteCode", eventMember.getSiteCode());
                // Add email and mobile fields for frontend filtering
                dto.put("primaryEmail", eventMember.getPrimaryEmail());
                dto.put("primaryMobile", eventMember.getTelephoneMobile());
                dto.put("hasMobile", eventMember.getHasMobile());
                dto.put("hasEmail", eventMember.getHasEmail());
                dto.put("siteIndustryDesc", eventMember.getSiteIndustryDesc());
                return dto;
            }).collect(Collectors.toList());

            // 获取详细的过滤预览统计
            Map<String, Object> previewStats = eventMemberTargetingService.getFilterPreview(eventId, criteria);

            Map<String, Object> data = new HashMap<>();
            data.put("members", memberDTOs);
            data.put("totalCount", smsableMembers.size());
            data.put("totalFiltered", filteredEventMembers.size());
            data.put("smsableCount", smsableMembers.size());
            data.put("nonSmsableCount", filteredEventMembers.size() - smsableMembers.size());
            data.put("stats", previewStats);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Advanced SMS preview generated successfully");
            response.put("data", data);

            log.info("Advanced SMS preview generated: {} total filtered, {} SMS-able recipients",
                    filteredEventMembers.size(), smsableMembers.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate advanced SMS preview", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to generate advanced SMS preview: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Unified SMS sending interface (supports single, selected, bulk sending)
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendSmsFromForm(
            @RequestParam(value = "smsType", required = false) String smsType,
            @RequestParam(value = "mobile", required = false) String singleMobile,
            @RequestParam(value = "name", required = false) String singleName,
            @RequestParam("content") String content,
            @RequestParam(value = "memberIds", required = false) String memberIdsJson) {
        log.info("=== Form-based SMS send requested ===");
        log.info("Parameters received - smsType: [{}], singleMobile: [{}], singleName: [{}]", smsType, singleMobile, singleName);
        log.info("Content length: {}, MemberIds JSON: [{}]",
                content != null ? content.length() : 0, memberIdsJson);
        try {
            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "SMS content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }
// Handle different sending modes
            List<EventMember> members;
            if (singleMobile != null && !singleMobile.trim().isEmpty()) {
// Single SMS sending
                log.info("=== SINGLE SMS MODE ACTIVATED ===");
                log.info("Processing single SMS send to: {}", singleMobile);
                EventMember singleMember = EventMember.builder()
                        .telephoneMobile(singleMobile)
                        .name(singleName != null ? singleName : "User")
                        .id(0L) // Temporary ID for single SMS
                        .build();
                members = List.of(singleMember);
            } else if (memberIdsJson != null && !memberIdsJson.trim().isEmpty()) {
// Selected members sending
                log.info("=== SELECTED MEMBERS SMS MODE ACTIVATED ===");
                log.info("Processing selected members: {}", memberIdsJson);
                try {
// Parse member IDs from JSON - handle both array and string formats
                    members = new ArrayList<>();
                    if (memberIdsJson.startsWith("[") && memberIdsJson.endsWith("]")) {
                        // JSON array format: [1,2,3] or ["1","2","3"]
                        String[] memberIds = memberIdsJson.replace("[", "").replace("]", "").replace("\"", "").split(",");
                        for (String memberIdStr : memberIds) {
                            if (memberIdStr.trim().isEmpty()) continue;
                            try {
                                Long memberId = Long.parseLong(memberIdStr.trim());
                                eventMemberRepository.findById(memberId).ifPresent(members::add);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid member ID format: {}", memberIdStr);
                            }
                        }
                    } else {
//                        Simple comma-separated format: 1,2,3
                        String[] memberIds = memberIdsJson.split(",");
                        for (String memberIdStr : memberIds) {
                            if (memberIdStr.trim().isEmpty()) continue;
                            try {
                                Long memberId = Long.parseLong(memberIdStr.trim());
                                eventMemberRepository.findById(memberId).ifPresent(members::add);
                            } catch (NumberFormatException e) {
                                log.warn("Invalid member ID format: {}", memberIdStr);
                            }
                        }
                    }
                    log.info("Found {} selected members", members.size());
                } catch (Exception e) {
                    log.error("Failed to parse memberIds: {}", e.getMessage());
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "error");
                    response.put("message", "Invalid member IDs format: " + e.getMessage());
                    return ResponseEntity.badRequest().body(response);
                }
            } else if (smsType != null && !smsType.trim().isEmpty()) {
// Bulk SMS sending by type
                log.info("=== BULK SMS MODE ACTIVATED ===");
                log.info("Processing bulk SMS send for type: {}", smsType);
                members = getEventMembersBySmsType(smsType);
            } else {
                log.error("No valid sending parameters provided");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Must provide mobile number, member IDs, or SMS type");
                return ResponseEntity.badRequest().body(response);
            }
// Filter members with valid mobile numbers
            members = members.stream()
                    .filter(m -> m.getTelephoneMobile() != null && !m.getTelephoneMobile().trim().isEmpty())
                    .collect(Collectors.toList());
            log.info("Starting SMS send to {} recipients", members.size());
// Send SMS
            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : members) {
                try {
                    String memberMobile = eventMember.getTelephoneMobile();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";
                    String membershipNumber = eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "BULK_" + eventMember.getId();

                    // Replace template variables
                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember) : "https://events.etu.nz/");

                    // Send SMS via RabbitMQ queue
                    Map<String, Object> smsData = Map.of(
                            "recipient", memberMobile,
                            "recipientName", memberName,
                            "content", personalizedContent,
                            "eventMemberId", eventMember.getId(),
                            "memberId", eventMember.getId(),
                            "membershipNumber", membershipNumber,
                            "templateCode", "BULK_SMS",
                            "notificationType", "SMS"
                    );
                    rabbitTemplate.convertAndSend(smsQueue, smsData);

                    // 🔧 Create NotificationLog for BMM event consistency
                    NotificationLog smsLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.SMS)
                            .recipient(memberMobile)
                            .recipientName(memberName)
                            .subject("SMS")
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("BULK_SMS")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(smsLog);

                    successCount++;
                    log.debug("SMS queued successfully for: {} (EventMember: {})", memberMobile, eventMember.getId());
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send SMS to eventMember {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                }
            }
// Return response in format expected by frontend
            Map<String, Object> data = new HashMap<>();
            data.put("sent", successCount);
            data.put("failed", failCount);
            data.put("total", members.size());
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("SMS queuing completed: %d queued, %d failed", successCount, failCount));
            response.put("data", data);
            log.info("SMS sending completed: {} success, {} failed out of {} total",
                    successCount, failCount, members.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to send SMS - Error details:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send SMS: " + e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
// Provide more specific error messages
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                response.put("hint", "Database connection issue - please check database configuration");
            } else if (e.getMessage() != null && e.getMessage().contains("rabbit")) {
                response.put("hint", "RabbitMQ connection issue - please check RabbitMQ service");
            } else if (e.getMessage() != null && e.getMessage().contains("memberIds")) {
                response.put("hint", "Member selection issue - please check selected members");
            } else if (e.getMessage() != null && e.getMessage().contains("content")) {
                response.put("hint", "SMS content issue - please check message content");
            }
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Send SMS by criteria
    @PostMapping("/send-by-criteria")
    public ResponseEntity<Map<String, Object>> sendSmsByCriteria(@RequestBody Map<String, Object> request) {
        log.info("=== Send SMS by criteria requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;
            String content = (String) request.get("content");

            if (criteria == null || criteria.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Filter criteria cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "SMS content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            log.info("Sending SMS by criteria: {} (eventId: {})", criteria, eventId);

            List<EventMember> validMobileEventMembers;

            // Get filtered EventMembers based on criteria using EventMemberTargetingService
            List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

            // Filter EventMembers with valid mobile numbers
            validMobileEventMembers = filteredEventMembers.stream()
                    .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                    .collect(Collectors.toList());

            log.info("Found {} EventMembers with valid mobile numbers after filtering", validMobileEventMembers.size());

            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : validMobileEventMembers) {
                try {
                    String memberMobile = eventMember.getTelephoneMobile();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // Replace template variables
                    String membershipNumber = eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "BULK_" + eventMember.getId();
                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember) : "https://events.etu.nz/")
                            .replace("{{regionDesc}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                            .replace("{{workplace}}", eventMember.getWorkplace() != null ? eventMember.getWorkplace() : "")
                            .replace("{{employer}}", eventMember.getEmployer() != null ? eventMember.getEmployer() : "");

                    // Send via RabbitMQ queue
                    Map<String, Object> smsData = Map.of(
                            "recipient", memberMobile,
                            "recipientName", memberName,
                            "content", personalizedContent,
                            "eventMemberId", eventMember.getId(),
                            "memberId", eventMember.getId(),
                            "membershipNumber", membershipNumber,
                            "templateCode", "BULK_SMS",
                            "notificationType", "SMS"
                    );
                    rabbitTemplate.convertAndSend(smsQueue, smsData);

                    // Create NotificationLog for BMM event consistency
                    NotificationLog smsLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.SMS)
                            .recipient(memberMobile)
                            .recipientName(memberName)
                            .subject("SMS")
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("BULK_SMS")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(smsLog);

                    successCount++;
                    log.debug("SMS queued successfully for: {} (EventMember: {})", memberMobile, eventMember.getId());
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send SMS to EventMember {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("SMS sending completed: %d success, %d failed", successCount, failCount));
            response.put("data", Map.of(
                    "totalFiltered", filteredEventMembers.size(),
                    "smsableCount", validMobileEventMembers.size(),
                    "successCount", successCount,
                    "failCount", failCount,
                    "nonSmsableCount", filteredEventMembers.size() - validMobileEventMembers.size()
            ));

            log.info("SMS sending completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send SMS by criteria", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send SMS by criteria: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Send bulk SMS (backward compatibility)
    @PostMapping("/send-bulk")
    public ResponseEntity<Map<String, Object>> sendBulkSms(@RequestBody Map<String, Object> request) {
        log.info("=== Bulk SMS send requested ===");

        try {
            String smsType = (String) request.get("smsType");
            String content = (String) request.get("content");

            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "SMS content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

// Get target member list (reuse preview logic)
            List<EventMember> members = getEventMembersBySmsType(smsType);

// Filter members with valid mobile numbers only
            members = members.stream()
                    .filter(m -> m.getTelephoneMobile() != null && !m.getTelephoneMobile().trim().isEmpty())
                    .collect(Collectors.toList());

            log.info("Starting bulk SMS send to {} recipients", members.size());

// Send SMS in bulk
            int successCount = 0;
            int failCount = 0;

            for (EventMember member : members) {
                try {
// Replace template variables
                    String memberName = member.getName() != null ? member.getName() : "Member";
                    String membershipNumber = member.getMembershipNumber() != null ? member.getMembershipNumber() : "BULK_" + member.getId();
                    String personalizedContent = content
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(member))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", member.getVerificationCode() != null ? member.getVerificationCode() : "")
                            .replace("{{registrationLink}}", member.getToken() != null ? generateRegistrationLinkWithEvent(member) : "https://events.etu.nz/");

                    // 🔧 BMM事件逻辑：基于EventMember处理
                    List<EventMember> memberEventMembers = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber());
                    EventMember eventMember = memberEventMembers.stream().findFirst().orElse(null);

                    if (eventMember == null) {
                        log.warn("No EventMember found for member: {}", member.getMembershipNumber());
                        failCount++;
                        continue;
                    }

// Send SMS
                    Map<String, Object> smsData = Map.of(
                            "recipient", member.getTelephoneMobile(),
                            "recipientName", memberName,
                            "content", personalizedContent,
                            "eventMemberId", eventMember.getId(), // 🔧 使用正确的EventMember ID
                            "memberId", member.getId(),
                            "membershipNumber", membershipNumber,
                            "templateCode", "BULK_SMS",
                            "notificationType", "SMS"
                    );
                    rabbitTemplate.convertAndSend(smsQueue, smsData);

                    // 🔧 Create NotificationLog for BMM event consistency
                    NotificationLog smsLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.SMS)
                            .recipient(eventMember.getTelephoneMobile())
                            .recipientName(memberName)
                            .subject("SMS")
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(false) // Will be updated by consumer
                            .emailType("BULK_SMS")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(smsLog);

                    successCount++;
                    log.debug("SMS sent successfully to: {} (EventMember: {})", member.getTelephoneMobile(), eventMember.getId());

                } catch (Exception e) {
                    log.error("Failed to send SMS to: {}", member.getTelephoneMobile(), e);
                    failCount++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("SMS sending completed: %d successful, %d failed", successCount, failCount));

            Map<String, Object> stats = new HashMap<>();
            stats.put("total", members.size());
            stats.put("success", successCount);
            stats.put("failed", failCount);
            response.put("data", stats);

            log.info("Bulk SMS send completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Bulk SMS send failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Bulk SMS sending failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get SMS sending statistics
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSmsStats() {
        log.info("Fetching SMS sending statistics");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> stats = new HashMap<>();

            long totalMembers = memberRepository.count();
            long smsSentSuccess = memberRepository.findAll().stream()
                    .filter(m -> "SUCCESS".equals(m.getSmsSentStatus()))
                    .count();
            long smsSentFailed = memberRepository.findAll().stream()
                    .filter(m -> "FAILED".equals(m.getSmsSentStatus()))
                    .count();
            long smsNotSent = memberRepository.findAll().stream()
                    .filter(m -> "NOT_SENT".equals(m.getSmsSentStatus()))
                    .count();
            long hasMobileCount = memberRepository.findAll().stream()
                    .filter(m -> m.getTelephoneMobile() != null && !m.getTelephoneMobile().trim().isEmpty())
                    .count();

            stats.put("totalMembers", totalMembers);
            stats.put("hasMobileCount", hasMobileCount);
            stats.put("smsSentSuccess", smsSentSuccess);
            stats.put("smsSentFailed", smsSentFailed);
            stats.put("smsNotSent", smsNotSent);
            stats.put("smsSendRate", hasMobileCount > 0 ? (smsSentSuccess * 100.0 / hasMobileCount) : 0);

            response.put("status", "success");
            response.put("data", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch SMS statistics", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get SMS statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Helper Methods
//    Get member list by type (unified method)
    private List<EventMember> getEventMembersBySmsType(String smsType) {
        log.info("Getting members by SMS type: {}", smsType);
        try {
            if (smsType == null || smsType.trim().isEmpty()) {
                log.warn("SmsType is null or empty, returning empty list");
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

            switch (smsType.toLowerCase()) {
                case "all":
                    log.info("Fetching all members");
                    break;
                case "registered":
                    log.info("Fetching registered members");
                    filteredStream = filteredStream.filter(em -> em.getHasRegistered() != null && em.getHasRegistered());
                    break;
                case "unregistered":
                    log.info("Fetching unregistered members");
                    filteredStream = filteredStream.filter(em -> em.getHasRegistered() == null || !em.getHasRegistered());
                    break;
                case "attending":
                    log.info("Fetching attending members");
                    filteredStream = filteredStream.filter(em -> em.getIsAttending() != null && em.getIsAttending());
                    break;
                case "has_mobile":
                    log.info("Fetching members with mobile");
                    filteredStream = filteredStream.filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty());
                    break;
                case "sms_only":
                    log.info("Fetching SMS only members");
                    filteredStream = filteredStream.filter(em ->
                            (em.getPrimaryEmail() == null || em.getPrimaryEmail().trim().isEmpty() || em.getPrimaryEmail().contains("@temp-email.etu.nz")) &&
                                    em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty());
                    break;
                case "test_group":
                    log.info("Fetching test group members");
                    filteredStream = filteredStream.filter(em ->
                            "foodmanual training".equalsIgnoreCase(em.getEmployer()));
                    break;
                case "test_tiny":
                    filteredStream = filteredStream.limit(3);
                    break;
                case "test_small":
                    filteredStream = filteredStream.limit(15);
                    break;
                case "northern":
                    filteredStream = filteredStream.filter(em -> "Northern".equalsIgnoreCase(em.getRegionDesc()));
                    break;
                case "central":
                    filteredStream = filteredStream.filter(em -> "Central".equalsIgnoreCase(em.getRegionDesc()));
                    break;
                case "southern":
                    filteredStream = filteredStream.filter(em -> "Southern".equalsIgnoreCase(em.getRegionDesc()));
                    break;
                default:
                    log.info("Unknown smsType '{}', using all BMM event members", smsType);
                    break;
            }

            // Return EventMember objects directly
            List<EventMember> result = filteredStream
                    .collect(Collectors.toList());

            log.info("Found {} EventMembers for SMS type '{}'", result.size(), smsType);
            return result;
        } catch (Exception e) {
            log.error("Error in getMembersBySmsType for type '{}': {}", smsType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //    Get member list by event and type
    private List<EventMember> getEventMembersByEventAndType(Long eventId, String smsType) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                log.warn("Event with ID {} not found", eventId);
                return new ArrayList<>();
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers;

            if (smsType == null || smsType.trim().isEmpty()) {
                log.warn("SmsType is null or empty, returning empty list");
                return new ArrayList<>();
            }

            switch (smsType.toLowerCase()) {
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
                case "has_mobile":
                    eventMembers = eventMemberRepository.findByEvent(event).stream()
                            .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                            .collect(Collectors.toList());
                    break;
                case "sms_only":
                    eventMembers = eventMemberRepository.findByEvent(event).stream()
                            .filter(em -> {
                                return (em.getPrimaryEmail() == null || em.getPrimaryEmail().trim().isEmpty()) &&
                                        em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty();
                            })
                            .collect(Collectors.toList());
                    break;
                default:
                    log.info("Unknown smsType '{}' for event, returning all event members", smsType);
                    eventMembers = eventMemberRepository.findByEvent(event);
                    break;
            }

//            Return EventMember objects directly
            return eventMembers;

        } catch (Exception e) {
            log.error("Error in getMembersByEventAndType for eventId '{}' and type '{}': {}", eventId, smsType, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //    Get filtered members based on criteria
    private List<EventMember> getFilteredEventMembers(Map<String, Object> criteria, Long eventId) {
        try {
            List<EventMember> allMembers;

            if (eventId != null) {
//                Get EventMembers from specific event directly
                Optional<Event> eventOpt = eventRepository.findById(eventId);
                if (!eventOpt.isPresent()) {
                    log.warn("Event with ID {} not found", eventId);
                    return new ArrayList<>();
                }

                Event event = eventOpt.get();
                allMembers = eventMemberRepository.findByEvent(event);
            } else {
//                Get all EventMembers from all events
                allMembers = eventMemberRepository.findAll();
            }

//            Apply filters
            Stream<EventMember> filteredStream = allMembers.stream();

//            Registration status filter
            String registrationStatus = (String) criteria.get("registrationStatus");
            if (registrationStatus != null && !registrationStatus.isEmpty()) {
                filteredStream = filteredStream.filter(m -> {
                    switch (registrationStatus) {
                        case "registered":
                            return m.getHasRegistered() != null && m.getHasRegistered();
                        case "not_registered":
                            return m.getHasRegistered() == null || !m.getHasRegistered();
                        case "attending":
                            return m.getIsAttending() != null && m.getIsAttending();
                        case "not_attending":
                            return m.getIsAttending() != null && !m.getIsAttending();
                        case "special_vote":
                            return m.getIsSpecialVote() != null && m.getIsSpecialVote();
                        default:
                            return true;
                    }
                });
            }

//            Region filter - 添加region mapping支持
            String region = (String) criteria.get("region");
            if (region != null && !region.isEmpty()) {
                // 🔧 Region mapping: 前端传"Northern Region" -> 数据库可能存"Northern"
                String dbRegionValue = region.contains(" Region") ?
                        region.replace(" Region", "") : region;
                filteredStream = filteredStream.filter(m ->
                        m.getRegionDesc() != null &&
                                (m.getRegionDesc().equals(region) || m.getRegionDesc().equals(dbRegionValue)));
            }

//            Industry filter - support both field names
            String industry = (String) (criteria.get("siteIndustryDesc") != null ?
                    criteria.get("siteIndustryDesc") : criteria.get("industry"));
            if (industry != null && !industry.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getSiteIndustryDesc() != null && m.getSiteIndustryDesc().equals(industry));
            }

//            Workplace filter
            String workplace = (String) criteria.get("workplace");
            if (workplace != null && !workplace.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getWorkplace() != null && m.getWorkplace().equals(workplace));
            }

//            Employer filter
            String employer = (String) criteria.get("employer");
            if (employer != null && !employer.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getEmployer() != null && m.getEmployer().equals(employer));
            }

//            Branch filter - support both field names
            String branch = (String) (criteria.get("branchDesc") != null ?
                    criteria.get("branchDesc") : criteria.get("branch"));
            if (branch != null && !branch.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getBranch() != null && m.getBranch().equals(branch));
            }

//            Forum filter - support both field names
            String forum = (String) (criteria.get("forumDesc") != null ?
                    criteria.get("forumDesc") : criteria.get("forum"));
            if (forum != null && !forum.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getForumDesc() != null && m.getForumDesc().equals(forum));
            }

//            Membership type filter - support both field names
            String membershipType = (String) (criteria.get("membershipTypeDesc") != null ?
                    criteria.get("membershipTypeDesc") : criteria.get("membershipType"));
            if (membershipType != null && !membershipType.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getMembershipTypeDesc() != null && m.getMembershipTypeDesc().equals(membershipType));
            }

//            Gender filter - support both field names
            String gender = (String) (criteria.get("genderDesc") != null ?
                    criteria.get("genderDesc") : criteria.get("gender"));
            if (gender != null && !gender.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getGenderDesc() != null && m.getGenderDesc().equals(gender));
            }

//            Ethnic region filter - support both field names
            String ethnicRegion = (String) (criteria.get("ethnicRegionDesc") != null ?
                    criteria.get("ethnicRegionDesc") : criteria.get("ethnicRegion"));
            if (ethnicRegion != null && !ethnicRegion.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getEthnicRegionDesc() != null && m.getEthnicRegionDesc().equals(ethnicRegion));
            }

//            Occupation filter
            String occupation = (String) criteria.get("occupation");
            if (occupation != null && !occupation.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getOccupation() != null && m.getOccupation().equals(occupation));
            }

//            Bargaining group filter - support both field names
            String bargainingGroup = (String) (criteria.get("bargainingGroupDesc") != null ?
                    criteria.get("bargainingGroupDesc") : criteria.get("bargainingGroup"));
            if (bargainingGroup != null && !bargainingGroup.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getBargainingGroupDesc() != null && m.getBargainingGroupDesc().equals(bargainingGroup));
            }

//            Sub industry filter (separate from main industry) - support both field names
            String subIndustry = (String) (criteria.get("siteSubIndustryDesc") != null ?
                    criteria.get("siteSubIndustryDesc") : criteria.get("subIndustry"));
            if (subIndustry != null && !subIndustry.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getSiteSubIndustryDesc() != null && m.getSiteSubIndustryDesc().equals(subIndustry));
            }

//            hasMobile filter - 有手机号筛选
            Boolean hasMobile = (Boolean) criteria.get("hasMobile");
            if (hasMobile != null) {
                if (hasMobile) {
                    filteredStream = filteredStream.filter(m ->
                            m.getTelephoneMobile() != null && !m.getTelephoneMobile().trim().isEmpty());
                } else {
                    filteredStream = filteredStream.filter(m ->
                            m.getTelephoneMobile() == null || m.getTelephoneMobile().trim().isEmpty());
                }
            }

//            hasEmail filter - 邮箱状态筛选 (SMS Only = hasEmail: false)
            // 注意：临时邮箱(@temp-email.etu.nz)被视为"没有邮箱"，因为它们不是真实可用的邮箱地址
            Boolean hasEmail = (Boolean) criteria.get("hasEmail");
            if (hasEmail != null) {
                if (hasEmail) {
                    // 有真实邮箱：非空且不是系统生成的临时邮箱
                    filteredStream = filteredStream.filter(m ->
                            m.getPrimaryEmail() != null &&
                                    !m.getPrimaryEmail().trim().isEmpty() &&
                                    !m.getPrimaryEmail().contains("@temp-email.etu.nz"));
                } else {
                    // 没有真实邮箱：空值或系统生成的临时邮箱 (适合SMS推送)
                    filteredStream = filteredStream.filter(m ->
                            m.getPrimaryEmail() == null ||
                                    m.getPrimaryEmail().trim().isEmpty() ||
                                    m.getPrimaryEmail().contains("@temp-email.etu.nz"));
                }
            }

            List<EventMember> filteredMembers = filteredStream.collect(Collectors.toList());

            log.debug("Applied filters to {} members, result: {} members", allMembers.size(), filteredMembers.size());
            return filteredMembers;

        } catch (Exception e) {
            log.error("Error in getFilteredMembers: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //    CRITICAL: 新增：基于多表联合查询的高级短信发送
    @PostMapping("/send-advanced")
    public ResponseEntity<Map<String, Object>> sendSmsAdvanced(@RequestBody Map<String, Object> request) {
        log.info("=== Advanced SMS send with multi-table join query requested ===");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> criteria = (Map<String, Object>) request.get("criteria");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;
            String message = (String) request.get("message");
            String smsType = (String) request.get("smsType");

            if (eventId == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event ID is required for advanced SMS sending");
                return ResponseEntity.badRequest().body(response);
            }

            if (message == null || message.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "SMS message cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Handle memberIds if provided (specific selection)
            List<Object> memberIdsList = null;
            if (criteria != null && criteria.get("memberIds") != null) {
                memberIdsList = (List<Object>) criteria.get("memberIds");
            }

            log.info("Advanced SMS sending for eventId: {} with criteria: {}, memberIds: {}",
                    eventId, criteria, memberIdsList != null ? memberIdsList.size() : "none");

            List<EventMember> smsableMembers;

            if (memberIdsList != null && !memberIdsList.isEmpty()) {
                // User selected specific members - ONLY send to these members
                log.info("Using specific memberIds: {}", memberIdsList);
                List<Long> memberIds = memberIdsList.stream()
                        .map(id -> Long.parseLong(id.toString()))
                        .collect(Collectors.toList());

                smsableMembers = memberIds.stream()
                        .map(id -> eventMemberRepository.findById(id).orElse(null))
                        .filter(Objects::nonNull)
                        .filter(em -> em.getEvent().getId().equals(eventId))
                        .filter(em -> em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty())
                        .collect(Collectors.toList());

                log.info("Found {} SMS-able members from {} selected memberIds", smsableMembers.size(), memberIds.size());
            } else {
                // No specific selection - use filter criteria
                // CRITICAL: 使用多表联合查询获取目标成员
                List<EventMember> filteredEventMembers = eventMemberTargetingService.getFilteredEventMembers(eventId, criteria);

                // For SMS, filter to only members with mobile but no real email
                smsableMembers = filteredEventMembers.stream()
                        .filter(em -> {
                            boolean hasMobile = em.getTelephoneMobile() != null && !em.getTelephoneMobile().trim().isEmpty();
                            boolean hasRealEmail = em.getPrimaryEmail() != null &&
                                    !em.getPrimaryEmail().trim().isEmpty() &&
                                    !em.getPrimaryEmail().contains("@temp-email.etu.nz");
                            // Only include if they have mobile and no real email
                            return hasMobile && !hasRealEmail;
                        })
                        .collect(Collectors.toList());

                log.info("Filtered {} SMS-only members from {} total members",
                        smsableMembers.size(), filteredEventMembers.size());
            }

            // 发送短信
            int successCount = 0;
            int failCount = 0;
            for (EventMember eventMember : smsableMembers) {
                try {
                    String memberMobile = eventMember.getTelephoneMobile();
                    String memberName = eventMember.getName() != null ? eventMember.getName() : "Member";

                    // 个性化短信内容
                    String membershipNumber = eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "BULK_" + eventMember.getId();
                    String personalizedContent = message
                            .replace("{{name}}", memberName)
                            .replace("{{firstName}}", getFirstName(eventMember))
                            .replace("{{membershipNumber}}", membershipNumber)
                            .replace("{{verificationCode}}", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "")
                            .replace("{{registrationLink}}", eventMember.getToken() != null ? generateRegistrationLinkWithEvent(eventMember) : "https://events.etu.nz/")
                            .replace("{{region}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "");

                    // 通过RabbitMQ队列发送
                    Map<String, Object> smsData = new HashMap<>();
                    smsData.put("recipient", memberMobile);
                    smsData.put("content", personalizedContent);
                    smsData.put("recipientName", memberName);
                    smsData.put("eventMemberId", eventMember.getId());
                    smsData.put("membershipNumber", membershipNumber);
                    smsData.put("templateCode", smsType != null ? smsType : "BMM_CUSTOM");
                    smsData.put("notificationType", "SMS");

                    rabbitTemplate.convertAndSend(smsQueue, smsData);

                    // Create NotificationLog
                    NotificationLog smsLog = NotificationLog.builder()
                            .eventMember(eventMember)
                            .notificationType(NotificationLog.NotificationType.SMS)
                            .recipient(memberMobile)
                            .recipientName(memberName)
                            .subject("SMS")
                            .content(personalizedContent)
                            .sentTime(LocalDateTime.now())
                            .isSuccessful(null) // Will be updated by consumer
                            .emailType(smsType != null ? smsType : "BMM_CUSTOM")
                            .adminId(1L)
                            .adminUsername("admin")
                            .build();
                    notificationLogRepository.save(smsLog);

                    successCount++;
                    log.debug("SMS queued successfully for: {} (Region: {}, Industry: {}, Registered: {})",
                            memberMobile, eventMember.getRegionDesc(), eventMember.getSiteIndustryDesc(), eventMember.getHasRegistered());

                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to send SMS to EventMember {}: {}", eventMember.getId(), e.getMessage());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("SMS queued for %d recipients", successCount));
            response.put("data", Map.of(
                    "sent", successCount,
                    "failed", failCount,
                    "total", smsableMembers.size()
            ));

            log.info("Advanced SMS sending completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send advanced SMS", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send SMS: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private String generateRegistrationLinkWithEvent(Member member) {
        // 🔧 修复：基于EventMember的token生成链接，确保BMM事件架构一致性
        try {
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber());
            if (!eventMembers.isEmpty()) {
                EventMember eventMember = eventMembers.get(0);
                if (eventMember.getToken() == null) {
                    log.warn("EventMember token is null for member: {}", member.getMembershipNumber());
                    return "https://events.etu.nz/";
                }

                String registrationLink = "https://events.etu.nz/?token=" + eventMember.getToken();
                // 添加事件ID参数
                if (eventMember.getEvent() != null) {
                    registrationLink += "&event=" + eventMember.getEvent().getId();
                }
                return registrationLink;
            } else {
                log.warn("No EventMember found for member: {}", member.getMembershipNumber());
                return "https://events.etu.nz/";
            }
        } catch (Exception e) {
            log.error("Failed to generate registration link for member {}: {}", member.getMembershipNumber(), e.getMessage());
            return "https://events.etu.nz/";
        }
    }

    // 重载方法处理EventMember对象
    private String generateRegistrationLinkWithEvent(EventMember eventMember) {
        try {
            if (eventMember.getToken() == null) {
                log.warn("EventMember token is null for member: {}", eventMember.getMembershipNumber());
                return "https://events.etu.nz/";
            }

            String registrationLink = "https://events.etu.nz/?token=" + eventMember.getToken();
            // 添加事件ID参数
            if (eventMember.getEvent() != null) {
                registrationLink += "&event=" + eventMember.getEvent().getId();
            }
            return registrationLink;
        } catch (Exception e) {
            log.error("Failed to generate registration link for EventMember {}: {}", eventMember.getMembershipNumber(), e.getMessage());
            return "https://events.etu.nz/";
        }
    }
}