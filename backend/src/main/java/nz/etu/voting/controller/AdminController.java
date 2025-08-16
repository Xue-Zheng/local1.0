package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.ExcelExportService;
import nz.etu.voting.service.MemberService;

import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.util.VerificationCodeGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Comparator;
import java.util.Date;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://192.168.110.6:3000", "https://events.etu.nz"})
public class AdminController {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final MemberService memberService;
    private final ExcelExportService excelExportService;

    private final VerificationCodeGenerator verificationCodeGenerator;

    //        获取会员统计数据 - Dashboard页面需要
    @GetMapping("/members/stats")
    public ResponseEntity<Map<String, Object>> getMemberStats() {
        log.info("Fetching member statistics for admin dashboard");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> stats = new HashMap<>();

            // 获取当前BMM事件
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            // 基于EventMember表的统计
            List<EventMember> eventMembers = currentBmmEvent != null ?
                    eventMemberRepository.findByEvent(currentBmmEvent) : new ArrayList<>();

            long totalMembers = eventMembers.size();
            long registeredMembers = eventMembers.stream()
                    .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                    .count();
            long attendingMembers = eventMembers.stream()
                    .filter(m -> m.getIsAttending() != null && m.getIsAttending())
                    .count();
            long specialVoteMembers = eventMembers.stream()
                    .filter(m -> m.getSpecialVoteRequested() != null && m.getSpecialVoteRequested())
                    .count();
            long votedMembers = eventMembers.stream()
                    .filter(m -> m.getHasVoted() != null && m.getHasVoted())
                    .count();

            stats.put("totalMembers", totalMembers);
            stats.put("registeredMembers", registeredMembers);
            stats.put("unregisteredMembers", totalMembers - registeredMembers);
            stats.put("attendingMembers", attendingMembers);
            stats.put("specialVoteMembers", specialVoteMembers);
            stats.put("votedMembers", votedMembers);

            // 数据源统计
            long emailMembers = eventMembers.stream()
                    .filter(m -> m.getDataSource() != null && m.getDataSource().contains("EMAIL"))
                    .count();
            long smsMembers = eventMembers.stream()
                    .filter(m -> m.getDataSource() != null && m.getDataSource().contains("SMS"))
                    .count();

            stats.put("emailMembers", emailMembers);
            stats.put("smsMembers", smsMembers);

            response.put("status", "success");
            response.put("data", stats);
            response.put("timestamp", new Date());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch member statistics", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to fetch member statistics: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    //        获取会员列表 - Members页面需要
    @GetMapping("/members")
    public ResponseEntity<Map<String, Object>> getMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dataSource,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Boolean hasRegistered) {

        log.info("Fetching members list - page: {}, size: {}, search: {}", page, size, search);

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("membershipNumber").ascending());

            List<Member> members;
            if (search != null && !search.trim().isEmpty()) {
//                简单搜索实现
                members = memberRepository.findAll().stream()
                        .filter(m ->
                                (m.getName() != null && m.getName().toLowerCase().contains(search.toLowerCase())) ||
                                        (m.getPrimaryEmail() != null && m.getPrimaryEmail().toLowerCase().contains(search.toLowerCase())) ||
                                        (m.getMembershipNumber() != null && m.getMembershipNumber().toLowerCase().contains(search.toLowerCase())))
                        .toList();
            } else {
                members = memberRepository.findAll();
            }

//            应用过滤器
            if (dataSource != null) {
                members = members.stream().filter(m -> dataSource.equals(m.getDataSource())).toList();
            }
            if (region != null) {
                members = members.stream().filter(m -> region.equals(m.getRegionDesc())).toList();
            }
            if (hasRegistered != null) {
                members = members.stream().filter(m -> hasRegistered.equals(m.getHasRegistered())).toList();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Member list retrieved successfully");
            response.put("data", members);
            response.put("totalCount", members.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch members", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get member list: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //        获取事件列表Preview - Dashboard页面需要
    @GetMapping("/events-overview")
    public ResponseEntity<Map<String, Object>> getEvents() {
        log.info("Fetching events overview for admin dashboard");

        try {
            List<Map<String, Object>> events = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc()
                    .stream()
                    .map(event -> {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("id", event.getId());
                        eventData.put("name", event.getName());
                        eventData.put("eventCode", event.getEventCode());
                        eventData.put("eventType", event.getEventType());
                        eventData.put("isActive", event.getIsActive());
                        eventData.put("syncStatus", event.getSyncStatus());
                        eventData.put("totalMembers", event.getMemberSyncCount());
                        eventData.put("registeredMembers", eventMemberRepository.countRegisteredByEvent(event));
                        return eventData;
                    })
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Event preview retrieved successfully");
            response.put("data", events);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch events overview", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get event preview list: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //        获取系统健康状态 - Dashboard页面需要
    @GetMapping("/system/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        log.info("Fetching system health status");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> healthData = new HashMap<>();

//            简单的健康检查
            try {
                memberRepository.count();
                eventRepository.count();
                healthData.put("status", "healthy");
                healthData.put("message", "All systems running normally");
            } catch (Exception e) {
                healthData.put("status", "error");
                healthData.put("message", "Database connection error");
            }

            response.put("status", "success");
            response.put("data", healthData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to check system health", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Health check failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/cleanup/data-sources")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupDataSources() {
        try {
//            统计当前数据源
            long csvImported = memberRepository.countByDataSource("CSV_IMPORT");
            long csvUpdated = memberRepository.countByDataSource("CSV_UPDATED");
            long manualCreated = memberRepository.countByDataSource(null);

//            将所有非Informer来源的会员标记为准备更新
            List<Member> membersToUpdate = memberRepository.findAll().stream()
                    .filter(member -> member.getDataSource() == null ||
                            member.getDataSource().startsWith("CSV_") ||
                            member.getDataSource().equals("MANUAL"))
                    .collect(Collectors.toList());

//            标记这些会员为待Informer更新
            membersToUpdate.forEach(member -> {
                member.setDataSource("READY_FOR_INFORMER_UPDATE");
                // Keep existing token and verification code stable
            });

            memberRepository.saveAll(membersToUpdate);

            Map<String, Object> result = Map.of(
                    "markedForUpdate", membersToUpdate.size(),
                    "previousSources", Map.of(
                            "csvImported", csvImported,
                            "csvUpdated", csvUpdated,
                            "manualCreated", manualCreated
                    ),
                    "message", "Members marked ready for Informer import"
            );

            log.info("Admin cleanup: {} members marked for Informer update", membersToUpdate.size());
            return ResponseEntity.ok(ApiResponse.success("Cleanup completed", result));
        } catch (Exception e) {
            log.error("Error during data source cleanup", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/recovery/informer-priority")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recoverInformerPriority() {
        try {
//            查找所有应急导入的会员
            List<Member> emergencyMembers = memberRepository.findByDataSource("CSV_EMERGENCY");
            List<Member> recoveredMembers = new ArrayList<>();

            for (Member member : emergencyMembers) {
//                标记为等待Informer更新
                member.setDataSource("READY_FOR_INFORMER_RECOVERY");
                // Keep existing token and verification code stable
                recoveredMembers.add(member);
            }

            memberRepository.saveAll(recoveredMembers);

            Map<String, Object> result = Map.of(
                    "recoveredMembers", recoveredMembers.size(),
                    "message", "Emergency imported members marked for Informer recovery",
                    "nextStep", "Run Informer import to restore data source priority",
                    "emergencyMembersFound", emergencyMembers.size()
            );

            log.info("Informer recovery: {} emergency members marked for recovery", recoveredMembers.size());
            return ResponseEntity.ok(ApiResponse.success("Recovery preparation completed", result));

        } catch (Exception e) {
            log.error("Error during Informer recovery preparation", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Recovery failed: " + e.getMessage()));
        }
    }

    @GetMapping("/data-sources/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDataSourceStatus() {
        try {
//            统计各种数据源的会员数量
            Map<String, Long> dataSourceCounts = new HashMap<>();
            dataSourceCounts.put("INFORMER_EMAIL_MEMBERS", memberRepository.countByDataSource("INFORMER_EMAIL_MEMBERS"));
            dataSourceCounts.put("INFORMER_SMS_MEMBERS", memberRepository.countByDataSource("INFORMER_SMS_MEMBERS"));
            dataSourceCounts.put("INFORMER_AUTO_CREATED", memberRepository.countByDataSource("INFORMER_AUTO_CREATED"));
            dataSourceCounts.put("CSV_EMERGENCY", memberRepository.countByDataSource("CSV_EMERGENCY"));
            dataSourceCounts.put("CSV_UPDATED", memberRepository.countByDataSource("CSV_UPDATED"));
            dataSourceCounts.put("CSV_IMPORT", memberRepository.countByDataSource("CSV_IMPORT"));
            dataSourceCounts.put("READY_FOR_INFORMER_RECOVERY", memberRepository.countByDataSource("READY_FOR_INFORMER_RECOVERY"));
            dataSourceCounts.put("MANUAL", memberRepository.countByDataSource("MANUAL"));

//            计算优先级状态
            long informerTotal = dataSourceCounts.get("INFORMER_EMAIL_MEMBERS") +
                    dataSourceCounts.get("INFORMER_SMS_MEMBERS") +
                    dataSourceCounts.get("INFORMER_AUTO_CREATED");
            long emergencyTotal = dataSourceCounts.get("CSV_EMERGENCY");
            long recoveryReady = dataSourceCounts.get("READY_FOR_INFORMER_RECOVERY");

            Map<String, Object> status = new HashMap<>();
            status.put("dataSourceCounts", dataSourceCounts);
            status.put("summary", Map.of(
                    "informerManaged", informerTotal,
                    "emergencyImported", emergencyTotal,
                    "readyForRecovery", recoveryReady,
                    "needsRecovery", emergencyTotal > 0
            ));

            return ResponseEntity.ok(ApiResponse.success("Data source status retrieved", status));

        } catch (Exception e) {
            log.error("Error retrieving data source status", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Status retrieval failed: " + e.getMessage()));
        }
    }

    @GetMapping("/system/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

//             Basic statistics
            long totalMembers = memberRepository.count();
            long totalEvents = eventRepository.count();
//             EventAttendee table removed - use EventMember for statistics
//             long totalEventMembers = eventMemberRepository.count();
            status.put("totalMembers", totalMembers);
            status.put("totalEvents", totalEvents);
//             status.put("totalEventMembers", totalEventMembers);
//             BMM specific checks
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            status.put("bmmEventsCount", bmmEvents.size());
//             if (!bmmEvents.isEmpty()) {
//                 Event bmmEvent = bmmEvents.get(0);
//                 long bmmMembers = eventMemberRepository.countByEvent(bmmEvent);
//                 status.put("bmmMembersCount", bmmMembers);
//                 status.put("bmmEventName", bmmEvent.getName());
//             }
//             Special Conference checks
            List<Event> specialEvents = eventRepository.findByEventType(Event.EventType.SPECIAL_CONFERENCE);
            status.put("specialEventsCount", specialEvents.size());
//             if (!specialEvents.isEmpty()) {
//                 Event specialEvent = specialEvents.get(0);
//                 long specialMembers = eventMemberRepository.countByEvent(specialEvent);
//                 status.put("specialMembersCount", specialMembers);
//             }
//             Legacy special members
            // Special vote count (this is voting rights, not member types)
            long specialVoteMembers = memberRepository.countByIsSpecialVoteTrue();
            status.put("specialVoteMembers", specialVoteMembers);

            status.put("systemHealthy", true);
            status.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("System status retrieved successfully", status));
        } catch (Exception e) {
            log.error("Failed to get system status", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve system status: " + e.getMessage()));
        }
    }

    @PostMapping("/members/export-filtered")
    public ResponseEntity<byte[]> exportFilteredMembers(@RequestBody Map<String, Object> filters) {
        log.info("Exporting filtered members with filters: {}", filters);

        try {
            // Get all members and apply filters
            List<Member> members = memberRepository.findAll();

            // Apply filters
            String search = (String) filters.get("search");
            if (search != null && !search.trim().isEmpty()) {
                String searchLower = search.toLowerCase();
                members = members.stream()
                        .filter(m ->
                                (m.getName() != null && m.getName().toLowerCase().contains(searchLower)) ||
                                        (m.getPrimaryEmail() != null && m.getPrimaryEmail().toLowerCase().contains(searchLower)) ||
                                        (m.getMembershipNumber() != null && m.getMembershipNumber().toLowerCase().contains(searchLower)))
                        .collect(Collectors.toList());
            }

            // Registration status filter
            String registrationStatus = (String) filters.get("registrationStatus");
            if (registrationStatus != null && !registrationStatus.isEmpty()) {
                switch (registrationStatus) {
                    case "registered":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getHasRegistered())).collect(Collectors.toList());
                        break;
                    case "notRegistered":
                        members = members.stream().filter(m -> !Boolean.TRUE.equals(m.getHasRegistered())).collect(Collectors.toList());
                        break;
                }
            }

            // Attendance status filter
            String attendanceStatus = (String) filters.get("attendanceStatus");
            if (attendanceStatus != null && !attendanceStatus.isEmpty()) {
                switch (attendanceStatus) {
                    case "attending":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getIsAttending())).collect(Collectors.toList());
                        break;
                    case "notAttending":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getHasRegistered()) && !Boolean.TRUE.equals(m.getIsAttending())).collect(Collectors.toList());
                        break;
                }
            }

            // Check-in status filter
            String checkinStatus = (String) filters.get("checkinStatus");
            if (checkinStatus != null && !checkinStatus.isEmpty()) {
                switch (checkinStatus) {
                    case "checkedIn":
                        members = members.stream().filter(m -> m.getCheckinTime() != null).collect(Collectors.toList());
                        break;
                    case "notCheckedIn":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getIsAttending()) && m.getCheckinTime() == null).collect(Collectors.toList());
                        break;
                }
            }

            // Contact info filter
            String contactInfo = (String) filters.get("contactInfo");
            if (contactInfo != null && !contactInfo.isEmpty()) {
                switch (contactInfo) {
                    case "hasEmail":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getHasEmail())).collect(Collectors.toList());
                        break;
                    case "hasMobile":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getHasMobile())).collect(Collectors.toList());
                        break;
                    case "hasBoth":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getHasEmail()) && Boolean.TRUE.equals(m.getHasMobile())).collect(Collectors.toList());
                        break;
                    case "hasNone":
                        members = members.stream().filter(m -> !Boolean.TRUE.equals(m.getHasEmail()) && !Boolean.TRUE.equals(m.getHasMobile())).collect(Collectors.toList());
                        break;
                }
            }

            // Special vote filter
            String specialVote = (String) filters.get("specialVote");
            if (specialVote != null && !specialVote.isEmpty()) {
                switch (specialVote) {
                    case "yes":
                        members = members.stream().filter(m -> Boolean.TRUE.equals(m.getIsSpecialVote())).collect(Collectors.toList());
                        break;
                    case "no":
                        members = members.stream().filter(m -> !Boolean.TRUE.equals(m.getIsSpecialVote())).collect(Collectors.toList());
                        break;
                }
            }

            // Region filter
            String region = (String) filters.get("region");
            if (region != null && !region.isEmpty()) {
                members = members.stream().filter(m -> region.equals(m.getRegionDesc())).collect(Collectors.toList());
            }

            // Industry filter
            String industry = (String) filters.get("industry");
            if (industry != null && !industry.isEmpty()) {
                members = members.stream().filter(m -> industry.equals(m.getSiteSubIndustryDesc())).collect(Collectors.toList());
            }

            // Employer filter
            String employer = (String) filters.get("employer");
            if (employer != null && !employer.isEmpty()) {
                members = members.stream().filter(m -> employer.equals(m.getEmployer())).collect(Collectors.toList());
            }

            // Selected members filter (if specific members are selected)
            @SuppressWarnings("unchecked")
            List<Long> selectedMemberIds = (List<Long>) filters.get("selectedMemberIds");
            if (selectedMemberIds != null && !selectedMemberIds.isEmpty()) {
                members = members.stream().filter(m -> selectedMemberIds.contains(m.getId())).collect(Collectors.toList());
            }

            // Generate Excel export
            byte[] excelData = excelExportService.exportFilteredMembersToExcel(members);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "filtered-members.xlsx");

            log.info("Exported {} filtered members to Excel", members.size());
            return ResponseEntity.ok().headers(headers).body(excelData);

        } catch (Exception e) {
            log.error("Failed to export filtered members", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Quick member search and communication endpoints
    @PostMapping("/quick-search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> quickSearchMembers(
            @RequestBody Map<String, String> searchRequest) {
        try {
            String searchTerm = searchRequest.get("searchTerm");
            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Search term is required"));
            }

            // Search in both Member and EventMember tables
            List<Map<String, Object>> results = memberService.quickSearchMembers(searchTerm.trim());

            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (Exception e) {
            log.error("Error in quick member search", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Search failed: " + e.getMessage()));
        }
    }

    @PostMapping("/quick-send-email")
    public ResponseEntity<ApiResponse<String>> quickSendEmail(@RequestBody Map<String, Object> request) {
        try {
            String membershipNumber = (String) request.get("membershipNumber");
            String subject = (String) request.get("subject");
            String content = (String) request.get("content");
            Boolean useVariableReplacement = (Boolean) request.get("useVariableReplacement");
            String provider = (String) request.get("provider");

            if (membershipNumber == null || subject == null || content == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Missing required fields"));
            }

            // Default to true if not specified
            boolean shouldReplaceVariables = useVariableReplacement != null ? useVariableReplacement : true;

            // Default to STRATUM if not specified
            String emailProvider = provider != null ? provider : "STRATUM";

            // Send email to specific member with variable replacement and provider option
            boolean sent = memberService.sendQuickEmailToMember(membershipNumber, subject, content, shouldReplaceVariables, emailProvider);

            if (sent) {
                return ResponseEntity.ok(ApiResponse.success("Email sent successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send email"));
            }
        } catch (Exception e) {
            log.error("Error sending quick email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Email send failed: " + e.getMessage()));
        }
    }

    @PostMapping("/quick-send-sms")
    public ResponseEntity<ApiResponse<String>> quickSendSMS(@RequestBody Map<String, Object> request) {
        try {
            String membershipNumber = (String) request.get("membershipNumber");
            String message = (String) request.get("message");
            Boolean useVariableReplacement = (Boolean) request.get("useVariableReplacement");

            if (membershipNumber == null || message == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Missing required fields"));
            }

            // Default to true if not specified
            boolean shouldReplaceVariables = useVariableReplacement != null ? useVariableReplacement : true;

            // Send SMS to specific member with variable replacement option
            boolean sent = memberService.sendQuickSMSToMember(membershipNumber, message, shouldReplaceVariables);

            if (sent) {
                return ResponseEntity.ok(ApiResponse.success("SMS sent successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send SMS"));
            }
        } catch (Exception e) {
            log.error("Error sending quick SMS", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("SMS send failed: " + e.getMessage()));
        }
    }

    @GetMapping("/auth/verify")
    public ResponseEntity<ApiResponse<String>> verifyAdminToken() {
        log.debug("Admin token verification request");
        try {
            // Token validation is handled by security filters
            // If this endpoint is reached, the token is valid
            return ResponseEntity.ok(ApiResponse.success("Token is valid"));
        } catch (Exception e) {
            log.error("Token verification failed", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Token verification failed"));
        }
    }

    // Get all members for a specific event
    @GetMapping("/events/{eventId}/members")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEventMembers(@PathVariable Long eventId) {
        try {
            log.info("Getting members for event ID: {}", eventId);

            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);

            List<Map<String, Object>> membersList = eventMembers.stream()
                    .map(member -> {
                        Map<String, Object> memberInfo = new HashMap<>();
                        memberInfo.put("id", member.getId());
                        memberInfo.put("membershipNumber", member.getMembershipNumber());
                        memberInfo.put("name", member.getName());
                        memberInfo.put("primaryEmail", member.getPrimaryEmail());
                        memberInfo.put("mobile", member.getTelephoneMobile());
                        memberInfo.put("regionDesc", member.getRegionDesc());
                        memberInfo.put("forumDesc", member.getForumDesc());
                        memberInfo.put("workplace", member.getWorkplace());
                        memberInfo.put("isAttending", member.getIsAttending());
                        memberInfo.put("bmmRegistrationStage", member.getBmmRegistrationStage());
                        memberInfo.put("ticketEmailSent", Boolean.TRUE.equals(member.getQrCodeEmailSent()));
                        memberInfo.put("assignedVenue", member.getAssignedVenue());
                        memberInfo.put("ticketToken", member.getTicketToken());
                        return memberInfo;
                    })
                    .toList();

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Found %d members for event", membersList.size()),
                    membersList));

        } catch (Exception e) {
            log.error("Failed to get event members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get event members: " + e.getMessage()));
        }
    }

    // Get BMM attendance statistics
    @GetMapping("/bmm/attendance-statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBmmAttendanceStatistics(
            @RequestParam(required = false) Long eventId) {
        try {
            log.info("Getting BMM attendance statistics for eventId: {}", eventId);

            Event bmmEvent;
            if (eventId != null) {
                bmmEvent = eventRepository.findById(eventId)
                        .orElseThrow(() -> new IllegalArgumentException("Event not found"));
            } else {
                bmmEvent = eventRepository.findByName("BMM2025")
                        .orElseThrow(() -> new IllegalArgumentException("BMM event not found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);

            // Filter confirmed members (Stage 2)
            List<EventMember> confirmedMembers = allMembers.stream()
                    .filter(member -> member.getBmmRegistrationStage() != null &&
                            (member.getBmmRegistrationStage().equals("ATTENDANCE_CONFIRMED") ||
                                    member.getBmmRegistrationStage().equals("ATTENDANCE_DECLINED")))
                    .toList();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalConfirmations", confirmedMembers.size());

            // Count attending and not attending
            long attendingCount = confirmedMembers.stream()
                    .filter(member -> Boolean.TRUE.equals(member.getIsAttending()))
                    .count();
            long notAttendingCount = confirmedMembers.stream()
                    .filter(member -> Boolean.FALSE.equals(member.getIsAttending()))
                    .count();

            stats.put("attendingCount", attendingCount);
            stats.put("notAttendingCount", notAttendingCount);

            // Count special vote eligible (Central and Southern regions, including Greymouth)
            long specialVoteEligible = confirmedMembers.stream()
                    .filter(member -> {
                        String region = member.getRegionDesc();
                        String forum = member.getForumDesc();
                        // Check if member is from eligible region or cancelled venue
                        return (region != null && (region.contains("Central") || region.contains("Southern"))) ||
                                "Greymouth".equals(forum);
                    })
                    .count();

            stats.put("specialVoteEligible", specialVoteEligible);

            // Count special vote requested (actually applied for special vote) - include ALL members, not just confirmed
            long specialVoteRequested = allMembers.stream()
                    .filter(member -> Boolean.TRUE.equals(member.getSpecialVoteRequested()))
                    .count();

            stats.put("specialVoteRequested", specialVoteRequested);

            // Count tickets sent and checked in
            long ticketsSent = confirmedMembers.stream()
                    .filter(member -> Boolean.TRUE.equals(member.getQrCodeEmailSent()))
                    .count();
            long checkedIn = confirmedMembers.stream()
                    .filter(member -> Boolean.TRUE.equals(member.getCheckedIn()))
                    .count();

            stats.put("ticketsSent", ticketsSent);
            stats.put("checkedIn", checkedIn);

            // Region breakdown
            Map<String, Map<String, Object>> regionsBreakdown = new HashMap<>();
            for (String region : List.of("Northern", "Central", "Southern")) {
                Map<String, Object> regionData = new HashMap<>();
                List<EventMember> regionMembers = confirmedMembers.stream()
                        .filter(member -> region.equals(member.getRegionDesc()))
                        .toList();

                regionData.put("total", regionMembers.size());
                regionData.put("attending", regionMembers.stream()
                        .filter(member -> Boolean.TRUE.equals(member.getIsAttending()))
                        .count());
                regionData.put("notAttending", regionMembers.stream()
                        .filter(member -> Boolean.FALSE.equals(member.getIsAttending()))
                        .count());

                // Special vote only for Central and Southern
                if (region.equals("Central") || region.equals("Southern")) {
                    regionData.put("specialVoteEligible", regionMembers.stream()
                            .filter(member -> Boolean.TRUE.equals(member.getSpecialVoteEligible()))
                            .count());
                    regionData.put("specialVoteRequested", regionMembers.stream()
                            .filter(member -> Boolean.TRUE.equals(member.getSpecialVoteRequested()))
                            .count());
                }

                regionData.put("ticketsSent", regionMembers.stream()
                        .filter(member -> Boolean.TRUE.equals(member.getQrCodeEmailSent()))
                        .count());
                regionData.put("checkedIn", regionMembers.stream()
                        .filter(member -> Boolean.TRUE.equals(member.getCheckedIn()))
                        .count());

                regionsBreakdown.put(region, regionData);
            }
            stats.put("regionsBreakdown", regionsBreakdown);

            // Venue distribution
            Map<String, Long> venueDistribution = confirmedMembers.stream()
                    .filter(member -> Boolean.TRUE.equals(member.getIsAttending()) &&
                            member.getAssignedVenue() != null)
                    .collect(Collectors.groupingBy(EventMember::getAssignedVenue,
                            Collectors.counting()));
            stats.put("venueDistribution", venueDistribution);

            // Session distribution
            Map<String, Long> sessionDistribution = new HashMap<>();
            for (EventMember member : confirmedMembers) {
                if (Boolean.TRUE.equals(member.getIsAttending())) {
                    String session = extractSessionTime(member);
                    if (session != null && !session.isEmpty()) {
                        sessionDistribution.put(session, sessionDistribution.getOrDefault(session, 0L) + 1);
                    }
                }
            }
            stats.put("sessionDistribution", sessionDistribution);

            return ResponseEntity.ok(ApiResponse.success("BMM attendance statistics retrieved", stats));

        } catch (Exception e) {
            log.error("Failed to get BMM attendance statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get attendance statistics: " + e.getMessage()));
        }
    }

    // Get BMM confirmations with filtering
    @GetMapping("/bmm/confirmations")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBmmConfirmations(
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String attendanceStatus,
            @RequestParam(required = false) String specialVote,
            @RequestParam(required = false) String search) {
        try {
            log.info("Getting BMM confirmations with filters for eventId: {}", eventId);

            Event bmmEvent;
            if (eventId != null) {
                bmmEvent = eventRepository.findById(eventId)
                        .orElseThrow(() -> new IllegalArgumentException("Event not found"));
            } else {
                bmmEvent = eventRepository.findByName("BMM2025")
                        .orElseThrow(() -> new IllegalArgumentException("BMM event not found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);

            // Apply filters
            List<EventMember> filteredMembers = allMembers.stream()
                    .filter(member -> {
                        // Stage filter
                        if (stage != null && !stage.isEmpty()) {
                            String[] stages = stage.split(",");
                            boolean matchesStage = false;
                            for (String s : stages) {
                                if (s.equals(member.getBmmRegistrationStage())) {
                                    matchesStage = true;
                                    break;
                                }
                            }
                            if (!matchesStage) return false;
                        }

                        // Region filter
                        if (region != null && !region.isEmpty() && !region.equals("all")) {
                            if (!region.equals(member.getRegionDesc())) return false;
                        }

                        // Venue filter
                        if (venue != null && !venue.isEmpty() && !venue.equals("all")) {
                            if (!venue.equals(member.getAssignedVenue())) return false;
                        }

                        // Attendance status filter - based on bmmRegistrationStage
                        if (attendanceStatus != null && !attendanceStatus.isEmpty() && !attendanceStatus.equals("all")) {
                            if (attendanceStatus.equals("confirmed") && !"ATTENDANCE_CONFIRMED".equals(member.getBmmRegistrationStage())) return false;
                            if (attendanceStatus.equals("declined") && !"ATTENDANCE_DECLINED".equals(member.getBmmRegistrationStage())) return false;
                            // Keep backward compatibility
                            if (attendanceStatus.equals("attending") && !Boolean.TRUE.equals(member.getIsAttending())) return false;
                            if (attendanceStatus.equals("not_attending") && !Boolean.FALSE.equals(member.getIsAttending())) return false;
                        }

                        // Special vote filter (only for Central and Southern regions)
                        if (specialVote != null && !specialVote.isEmpty() && !specialVote.equals("all")) {
                            boolean isEligibleRegion = member.getRegionDesc() != null &&
                                    (member.getRegionDesc().equals("Central") ||
                                            member.getRegionDesc().equals("Southern"));

                            if (specialVote.equals("requested")) {
                                // Check if they have requested special vote
                                if (!Boolean.TRUE.equals(member.getSpecialVoteRequested())) return false;
                            } else if (specialVote.equals("not_requested")) {
                                // Check if they haven't requested special vote
                                if (Boolean.TRUE.equals(member.getSpecialVoteRequested())) return false;
                            } else if (specialVote.equals("eligible")) {
                                if (!isEligibleRegion || !Boolean.TRUE.equals(member.getSpecialVoteEligible())) return false;
                            } else if (specialVote.equals("not_eligible")) {
                                if (!isEligibleRegion || Boolean.TRUE.equals(member.getSpecialVoteEligible())) return false;
                            }
                        }

                        // Search filter
                        if (search != null && !search.isEmpty()) {
                            String searchLower = search.toLowerCase();
                            boolean matches = false;
                            if (member.getName() != null && member.getName().toLowerCase().contains(searchLower)) matches = true;
                            if (member.getMembershipNumber() != null && member.getMembershipNumber().toLowerCase().contains(searchLower)) matches = true;
                            if (!matches) return false;
                        }

                        return true;
                    })
                    .toList();

            // Convert to response format
            List<Map<String, Object>> confirmationsList = filteredMembers.stream()
                    .sorted((a, b) -> {
                        // Sort by confirmation date descending
                        if (a.getUpdatedAt() != null && b.getUpdatedAt() != null) {
                            return b.getUpdatedAt().compareTo(a.getUpdatedAt());
                        }
                        return 0;
                    })
                    .skip(page * size)
                    .limit(size)
                    .map(member -> {
                        Map<String, Object> confirmation = new HashMap<>();
                        confirmation.put("id", member.getId());
                        confirmation.put("name", member.getName());
                        confirmation.put("membershipNumber", member.getMembershipNumber());
                        confirmation.put("email", member.getPrimaryEmail());
                        confirmation.put("mobile", member.getTelephoneMobile());
                        confirmation.put("region", member.getRegionDesc());
                        confirmation.put("isAttending", member.getIsAttending());
                        confirmation.put("bmmRegistrationStage", member.getBmmRegistrationStage());

                        // Special vote eligibility (for Central, Southern regions and cancelled venues)
                        boolean specialVoteEligible = false;
                        String memberRegion = member.getRegionDesc();
                        String forum = member.getForumDesc();

                        // Check if eligible based on region or cancelled venue
                        if ((memberRegion != null && (memberRegion.contains("Central") || memberRegion.contains("Southern"))) ||
                                ("Greymouth".equals(forum) || "Hokitika".equals(forum) || "Reefton".equals(forum))) {
                            // Either use the database value or set it based on region/forum
                            specialVoteEligible = Boolean.TRUE.equals(member.getSpecialVoteEligible()) ||
                                    Boolean.TRUE.equals(member.getSpecialVoteRequested());
                        }

                        confirmation.put("specialVoteEligible", specialVoteEligible);
                        confirmation.put("specialVoteRequested", member.getSpecialVoteRequested());
                        confirmation.put("bmmSpecialVoteStatus", member.getBmmSpecialVoteStatus());
                        confirmation.put("specialVoteApplicationReason", member.getSpecialVoteApplicationReason());

                        confirmation.put("assignedVenue", member.getAssignedVenue());

                        // Calculate assigned session based on preferences
                        String assignedSession = extractSessionTime(member);
                        confirmation.put("assignedSession", assignedSession);
                        confirmation.put("ticketEmailSent", member.getQrCodeEmailSent());
                        confirmation.put("checkedIn", member.getCheckedIn());
                        confirmation.put("checkInTime", member.getCheckInTime());
                        confirmation.put("checkInMethod", member.getCheckInMethod());
                        confirmation.put("checkInAdminName", member.getCheckInAdminName());
                        confirmation.put("checkInVenue", member.getCheckInVenue());
                        confirmation.put("confirmationDate", member.getUpdatedAt());
                        confirmation.put("declinedReason", member.getBmmDeclineReason() != null ? member.getBmmDeclineReason() : member.getNonAttendanceReason());
                        confirmation.put("forumDesc", member.getForumDesc());

                        // Add financial form data
                        confirmation.put("financialFormId", member.getFinancialFormId());
                        confirmation.put("phoneWork", member.getPhoneWork());
                        confirmation.put("phoneHome", member.getPhoneHome());
                        confirmation.put("postalAddress", member.getAddress());
                        confirmation.put("payrollNumber", member.getPayrollNumber());
                        confirmation.put("siteCode", member.getSiteCode());
                        confirmation.put("employmentStatus", member.getEmploymentStatus());
                        confirmation.put("department", member.getDepartment());
                        confirmation.put("jobTitle", member.getJobTitle());
                        confirmation.put("location", member.getLocation());
                        confirmation.put("dateOfBirth", member.getDob());
                        
                        // Additional fields for special vote details
                        confirmation.put("address", member.getAddress());
                        confirmation.put("workplace", member.getWorkplace());
                        confirmation.put("ageOfMember", member.getAgeOfMember());
                        confirmation.put("telephoneMobile", member.getTelephoneMobile());
                        confirmation.put("primaryEmail", member.getPrimaryEmail());

                        return confirmation;
                    })
                    .toList();

            // Create page response
            int totalElements = filteredMembers.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            Map<String, Object> pageData = new HashMap<>();
            pageData.put("content", confirmationsList);
            pageData.put("totalElements", totalElements);
            pageData.put("totalPages", totalPages);
            pageData.put("number", page);
            pageData.put("size", size);

            return ResponseEntity.ok(ApiResponse.success("Confirmations retrieved", pageData));

        } catch (Exception e) {
            log.error("Failed to get BMM confirmations: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get confirmations: " + e.getMessage()));
        }
    }

    private String extractSessionTime(EventMember eventMember) {
        // First check if this is a forumVenueMapping member
        String forumDesc = eventMember.getForumDesc();
        if (forumDesc != null && forumDesc.equals("Greymouth")) {
            // For these special forums, return special instructions
            return "Multiple venues and times available";
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
        return "10:30 AM or 12:30 PM";
    }
}