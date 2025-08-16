package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.service.TicketEmailService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Slf4j
@RestController
@RequestMapping("/api/admin/bmm")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
public class AdminBmmController {

    @PersistenceContext
    private EntityManager entityManager;

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final MemberRepository memberRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final TicketEmailService ticketEmailService;
    private final RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;

    // BMM Regional Management Dashboard
    @GetMapping("/regional-dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBmmRegionalDashboard() {
        log.info("Fetching BMM regional dashboard data");

        try {
            Map<String, Object> dashboardData = new HashMap<>();

            // Get current BMM event and members
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.ok(ApiResponse.success("No BMM events found",
                        Map.of("regions", new HashMap<>(), "summary", new HashMap<>())));
            }

            List<EventMember> allBmmMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            if (allBmmMembers.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success("No BMM members found",
                        Map.of("regions", new HashMap<>(), "summary", new HashMap<>())));
            }

            // Regional Analysis
            Map<String, Map<String, Object>> regionalData = new HashMap<>();

            // Map database values to display values
            Map<String, String> regionMapping = new HashMap<>();
            regionMapping.put("Northern Region", "Northern");
            regionMapping.put("Central Region", "Central");
            regionMapping.put("Southern Region", "Southern");

            List<String> regions = Arrays.asList("Northern Region", "Central Region", "Southern Region");

            for (String region : regions) {
                String dbRegionValue = regionMapping.get(region);
                List<EventMember> regionMembers = allBmmMembers.stream()
                        .filter(m -> dbRegionValue.equals(m.getRegionDesc()))
                        .collect(Collectors.toList());

                Map<String, Object> regionStats = new HashMap<>();
                regionStats.put("totalMembers", regionMembers.size());

                // 🎯 使用统一的BMM注册状态逻辑 - 与筛选API保持一致
                // 完全注册：hasRegistered = true AND registrationData不为空
                regionStats.put("registeredMembers", regionMembers.stream()
                        .filter(m -> m.getHasRegistered() != null && m.getHasRegistered() &&
                                m.getRegistrationData() != null && !m.getRegistrationData().trim().isEmpty())
                        .count());

                // Stage1用户：有registrationData但hasRegistered = false
                regionStats.put("stage1Members", regionMembers.stream()
                        .filter(m -> (m.getHasRegistered() == null || !m.getHasRegistered()) &&
                                m.getRegistrationData() != null && !m.getRegistrationData().trim().isEmpty())
                        .count());

                // 完全未注册：hasRegistered = false AND registrationData为空
                regionStats.put("notRegisteredMembers", regionMembers.stream()
                        .filter(m -> (m.getHasRegistered() == null || !m.getHasRegistered()) &&
                                (m.getRegistrationData() == null || m.getRegistrationData().trim().isEmpty()))
                        .count());

                regionStats.put("attendingMembers", regionMembers.stream()
                        .filter(m -> m.getHasRegistered() != null && m.getHasRegistered() &&
                                m.getIsAttending() != null && m.getIsAttending())
                        .count());
                regionStats.put("specialVoteMembers", regionMembers.stream()
                        .filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote())
                        .count());

                // 遗留字段保持兼容性
                regionStats.put("noResponseMembers", regionMembers.stream()
                        .filter(m -> (m.getHasRegistered() == null || !m.getHasRegistered()) &&
                                (m.getRegistrationData() == null || m.getRegistrationData().trim().isEmpty()))
                        .count());
                regionStats.put("smsOnlyMembers", regionMembers.stream()
                        .filter(m -> (m.getPrimaryEmail() == null ||
                                m.getPrimaryEmail().trim().isEmpty() ||
                                m.getPrimaryEmail().contains("@temp-email.etu.nz")) &&
                                m.getTelephoneMobile() != null &&
                                !m.getTelephoneMobile().trim().isEmpty())
                        .count());
                regionStats.put("emailSentCount", regionMembers.stream()
                        .filter(m -> m.getEmailSent() != null && m.getEmailSent())
                        .count());
                regionStats.put("smsSentCount", regionMembers.stream()
                        .filter(m -> m.getSmsSent() != null && m.getSmsSent())
                        .count());

                // Venue preferences analysis for this region
                Map<String, Integer> venuePreferences = new HashMap<>();
                regionMembers.stream()
                        .filter(m -> m.getRegistrationData() != null)
                        .forEach(m -> {
                            try {
                                Map<String, Object> regData = objectMapper.readValue(m.getRegistrationData(), Map.class);
                                if (regData.containsKey("preferredVenues")) {
                                    List<String> venues = (List<String>) regData.get("preferredVenues");
                                    venues.forEach(venue ->
                                            venuePreferences.merge(venue, 1, Integer::sum));
                                }
                            } catch (Exception e) {
                                log.warn("Failed to parse registration data for member {}: {}",
                                        m.getMembershipNumber(), e.getMessage());
                            }
                        });
                regionStats.put("venuePreferences", venuePreferences);

                regionalData.put(region, regionStats);
            }

            // Overall Summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalMembers", allBmmMembers.size());
            summary.put("totalRegistered", allBmmMembers.stream()
                    .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                    .count());
            summary.put("totalAttending", allBmmMembers.stream()
                    .filter(m -> m.getIsAttending() != null && m.getIsAttending())
                    .count());
            summary.put("totalSpecialVote", allBmmMembers.stream()
                    .filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote())
                    .count());
            summary.put("totalNoResponse", allBmmMembers.stream()
                    .filter(m -> m.getHasRegistered() == null || !m.getHasRegistered())
                    .count());
            summary.put("eventName", currentBmmEvent.getName());
            summary.put("eventId", currentBmmEvent.getId());

            dashboardData.put("regions", regionalData);
            dashboardData.put("summary", summary);
            dashboardData.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("BMM regional dashboard data retrieved", dashboardData));

        } catch (Exception e) {
            log.error("Failed to get BMM regional dashboard: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get dashboard data: " + e.getMessage()));
        }
    }

    // Get members by region and status
    @GetMapping("/region/{region}/members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRegionMembers(
            @PathVariable String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching members for region: {}, status: {}", region, status);

        try {
            // Get current BMM event and members
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Map display region to database value
            final String dbRegionValue = region.contains(" Region") ?
                    region.replace(" Region", "") : region;

            // Filter by region
            List<EventMember> regionMembers = allMembers.stream()
                    .filter(m -> dbRegionValue.equals(m.getRegionDesc()))
                    .collect(Collectors.toList());

            // Filter by status if provided
            if (status != null && !status.isEmpty()) {
                switch (status) {
                    case "registered":
                        regionMembers = regionMembers.stream()
                                .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                                .collect(Collectors.toList());
                        break;
                    case "not_registered":
                        regionMembers = regionMembers.stream()
                                .filter(m -> m.getHasRegistered() == null || !m.getHasRegistered())
                                .collect(Collectors.toList());
                        break;
                    case "attending":
                        regionMembers = regionMembers.stream()
                                .filter(m -> m.getIsAttending() != null && m.getIsAttending())
                                .collect(Collectors.toList());
                        break;
                    case "not_attending":
                        regionMembers = regionMembers.stream()
                                .filter(m -> m.getIsAttending() != null && !m.getIsAttending())
                                .collect(Collectors.toList());
                        break;
                    case "special_vote":
                        regionMembers = regionMembers.stream()
                                .filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote())
                                .collect(Collectors.toList());
                        break;
                    case "sms_only":
                        regionMembers = regionMembers.stream()
                                .filter(m -> (m.getPrimaryEmail() == null ||
                                        m.getPrimaryEmail().trim().isEmpty() ||
                                        m.getPrimaryEmail().contains("@temp-email.etu.nz")) &&
                                        m.getTelephoneMobile() != null &&
                                        !m.getTelephoneMobile().trim().isEmpty())
                                .collect(Collectors.toList());
                        break;
                }
            }

            // Sort by name
            regionMembers.sort(Comparator.comparing(EventMember::getName));

            // Pagination
            int totalElements = regionMembers.size();
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalElements);

            List<EventMember> pagedMembers = regionMembers.subList(startIndex, endIndex);

            // Convert to response format
            List<Map<String, Object>> memberList = pagedMembers.stream()
                    .map(this::convertMemberToMap)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("members", memberList);
            response.put("totalElements", totalElements);
            response.put("totalPages", (int) Math.ceil((double) totalElements / size));
            response.put("currentPage", page);
            response.put("size", size);
            response.put("region", region);
            response.put("status", status);

            return ResponseEntity.ok(ApiResponse.success("Region members retrieved", response));

        } catch (Exception e) {
            log.error("Failed to get region members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get members: " + e.getMessage()));
        }
    }

    // Get member's BMM preferences
    @GetMapping("/member/{membershipNumber}/preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberBmmPreferences(
            @PathVariable String membershipNumber) {

        log.info("Fetching BMM preferences for member: {}", membershipNumber);

        try {
            EventMember eventMember = eventMemberRepository
                    .findByMembershipNumber(membershipNumber)
                    .stream()
                    .findFirst()
                    .orElse(null);

            if (eventMember == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            Map<String, Object> memberData = convertMemberToMap(eventMember);

            // Parse BMM preferences
            Map<String, Object> preferences = new HashMap<>();
            if (eventMember.getRegistrationData() != null) {
                try {
                    preferences = objectMapper.readValue(eventMember.getRegistrationData(), Map.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse registration data for member {}: {}",
                            membershipNumber, e.getMessage());
                }
            }

            memberData.put("bmmPreferences", preferences);

            return ResponseEntity.ok(ApiResponse.success("Member preferences retrieved", memberData));

        } catch (Exception e) {
            log.error("Failed to get member preferences: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get preferences: " + e.getMessage()));
        }
    }

    // Venue analytics
    @GetMapping("/venue-analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVenueAnalytics() {
        log.info("Fetching BMM venue analytics");

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            Map<String, Map<String, Integer>> venueByRegion = new HashMap<>();
            Map<String, Integer> overallVenues = new HashMap<>();
            Map<String, Integer> timePreferences = new HashMap<>();

            allMembers.stream()
                    .filter(m -> m.getRegistrationData() != null)
                    .forEach(m -> {
                        try {
                            Map<String, Object> regData = objectMapper.readValue(m.getRegistrationData(), Map.class);
                            String region = m.getRegionDesc() != null ? m.getRegionDesc() : "Unknown";

                            // Venue preferences
                            if (regData.containsKey("preferredVenues")) {
                                List<String> venues = (List<String>) regData.get("preferredVenues");
                                venues.forEach(venue -> {
                                    overallVenues.merge(venue, 1, Integer::sum);
                                    venueByRegion.computeIfAbsent(region, k -> new HashMap<>())
                                            .merge(venue, 1, Integer::sum);
                                });
                            }

                            // Time preferences
                            if (regData.containsKey("preferredTimes")) {
                                List<String> times = (List<String>) regData.get("preferredTimes");
                                times.forEach(time -> timePreferences.merge(time, 1, Integer::sum));
                            }

                        } catch (Exception e) {
                            log.warn("Failed to parse registration data for member {}: {}",
                                    m.getMembershipNumber(), e.getMessage());
                        }
                    });

            Map<String, Object> analytics = new HashMap<>();
            analytics.put("venuesByRegion", venueByRegion);
            analytics.put("overallVenues", overallVenues);
            analytics.put("timePreferences", timePreferences);
            analytics.put("totalResponses", allMembers.stream()
                    .filter(m -> m.getRegistrationData() != null)
                    .count());

            return ResponseEntity.ok(ApiResponse.success("Venue analytics retrieved", analytics));

        } catch (Exception e) {
            log.error("Failed to get venue analytics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get analytics: " + e.getMessage()));
        }
    }

    @GetMapping("/members/filtered")
    public ResponseEntity<ApiResponse> getFilteredMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contactMethod,
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) String timePreference,
            @RequestParam(required = false) String workplace,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String subIndustry,
            @RequestParam(required = false) String emailSent,
            @RequestParam(required = false) String smsSent,
            @RequestParam(required = false) String search
    ) {
        try {
            // Get BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event bmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);
            if (bmmEvent == null) {
                return ResponseEntity.ok(ApiResponse.error("No BMM event found"));
            }
            Pageable pageable = PageRequest.of(page, size);

            // Build dynamic query based on filters
            Page<EventMember> eventMembersPage = getFilteredEventMembers(
                    bmmEvent.getId(), region, status, contactMethod, venue, timePreference,
                    workplace, industry, subIndustry, emailSent, smsSent, search, pageable
            );

            List<Map<String, Object>> membersData = eventMembersPage.getContent().stream()
                    .map(this::convertMemberToMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("members", membersData);
            result.put("totalPages", eventMembersPage.getTotalPages());
            result.put("totalElements", eventMembersPage.getTotalElements());
            result.put("currentPage", page);
            result.put("pageSize", size);

            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("Error fetching filtered members", e);
            return ResponseEntity.ok(ApiResponse.error("Failed to fetch filtered members: " + e.getMessage()));
        }
    }

    private Page<EventMember> getFilteredEventMembers(
            Long eventId, String region, String status, String contactMethod,
            String venue, String timePreference, String workplace,
            String industry, String subIndustry,
            String emailSent, String smsSent, String search, Pageable pageable) {

        // Build JPQL query dynamically based on provided filters
        StringBuilder jpql = new StringBuilder("SELECT em FROM EventMember em WHERE em.event.id = :eventId");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("eventId", eventId);

        // Region filter - map display region to database value
        if (region != null && !region.trim().isEmpty()) {
            String dbRegionValue = region.contains(" Region") ?
                    region.replace(" Region", "") : region;
            conditions.add("em.regionDesc = :region");
            parameters.put("region", dbRegionValue);
        }

        // Search filter (name or membership number)
        if (search != null && !search.trim().isEmpty()) {
            conditions.add("(em.name LIKE :search OR em.membershipNumber LIKE :search)");
            parameters.put("search", "%" + search + "%");
        }

        // Workplace filter
        if (workplace != null && !workplace.trim().isEmpty()) {
            conditions.add("em.workplace LIKE :workplace");
            parameters.put("workplace", "%" + workplace + "%");
        }

        // Industry filter
        if (industry != null && !industry.trim().isEmpty()) {
            conditions.add("em.siteIndustryDesc LIKE :industry");
            parameters.put("industry", "%" + industry + "%");
        }

        // Sub-Industry filter
        if (subIndustry != null && !subIndustry.trim().isEmpty()) {
            conditions.add("em.siteSubIndustryDesc LIKE :subIndustry");
            parameters.put("subIndustry", "%" + subIndustry + "%");
        }

        // Status-based filters - 使用统一的BMM注册状态逻辑
        if (status != null && !status.trim().isEmpty()) {
            switch (status) {
                case "registered":
                    // 完全注册：hasRegistered = true AND registrationData不为空
                    conditions.add("em.hasRegistered = true AND em.registrationData IS NOT NULL");
                    break;
                case "not_registered":
                    // 完全未注册：hasRegistered = false AND registrationData为空
                    conditions.add("(em.hasRegistered = false OR em.hasRegistered IS NULL) AND em.registrationData IS NULL");
                    break;
                case "stage1":
                    // Stage1用户：有registrationData但hasRegistered = false
                    conditions.add("em.registrationData IS NOT NULL AND (em.hasRegistered = false OR em.hasRegistered IS NULL)");
                    break;
                case "attending":
                    conditions.add("em.hasRegistered = true AND JSON_EXTRACT(em.registrationData, '$.attendanceWillingness') = 'yes'");
                    break;
                case "not_attending":
                    conditions.add("em.hasRegistered = true AND JSON_EXTRACT(em.registrationData, '$.attendanceWillingness') = 'no'");
                    break;
                case "special_vote":
                    conditions.add("JSON_EXTRACT(em.registrationData, '$.specialVoteInterest') = 'yes'");
                    break;
                case "sms_only":
                    conditions.add("(em.primaryEmail IS NULL OR em.primaryEmail = '') AND em.telephoneMobile IS NOT NULL");
                    break;
            }
        }

        // Contact method filter
        if (contactMethod != null && !contactMethod.trim().isEmpty()) {
            switch (contactMethod) {
                case "both":
                    conditions.add("em.primaryEmail IS NOT NULL AND em.primaryEmail != '' AND em.telephoneMobile IS NOT NULL AND em.telephoneMobile != ''");
                    break;
                case "email_only":
                    conditions.add("em.primaryEmail IS NOT NULL AND em.primaryEmail != '' AND (em.telephoneMobile IS NULL OR em.telephoneMobile = '')");
                    break;
                case "sms_only":
                    conditions.add("(em.primaryEmail IS NULL OR em.primaryEmail = '') AND em.telephoneMobile IS NOT NULL AND em.telephoneMobile != ''");
                    break;
                case "no_contact":
                    conditions.add("(em.primaryEmail IS NULL OR em.primaryEmail = '') AND (em.telephoneMobile IS NULL OR em.telephoneMobile = '')");
                    break;
            }
        }

        // Email sent filter
        if (emailSent != null && !emailSent.trim().isEmpty()) {
            if ("true".equals(emailSent)) {
                conditions.add("em.emailSent = true");
            } else if ("false".equals(emailSent)) {
                conditions.add("(em.emailSent = false OR em.emailSent IS NULL)");
            }
        }

        // SMS sent filter
        if (smsSent != null && !smsSent.trim().isEmpty()) {
            if ("true".equals(smsSent)) {
                conditions.add("em.smsSent = true");
            } else if ("false".equals(smsSent)) {
                conditions.add("(em.smsSent = false OR em.smsSent IS NULL)");
            }
        }

        // Venue preference filter (requires JSON parsing)
        if (venue != null && !venue.trim().isEmpty()) {
            conditions.add("JSON_CONTAINS(JSON_EXTRACT(em.registrationData, '$.preferredVenues'), :venue)");
            parameters.put("venue", "\"" + venue + "\"");
        }

        // Time preference filter (requires JSON parsing)
        if (timePreference != null && !timePreference.trim().isEmpty()) {
            conditions.add("JSON_CONTAINS(JSON_EXTRACT(em.registrationData, '$.preferredTimes'), :timePreference)");
            parameters.put("timePreference", "\"" + timePreference + "\"");
        }

        // Add all conditions to the query
        if (!conditions.isEmpty()) {
            jpql.append(" AND ").append(String.join(" AND ", conditions));
        }

        jpql.append(" ORDER BY em.name ASC");

        // Execute the query using EntityManager
        jakarta.persistence.Query query = entityManager.createQuery(jpql.toString());
        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            query.setParameter(param.getKey(), param.getValue());
        }

        // Handle pagination
        query.setFirstResult(pageable.getPageNumber() * pageable.getPageSize());
        query.setMaxResults(pageable.getPageSize());

        List<EventMember> members = query.getResultList();

        // Get total count for pagination
        String countJpql = jpql.toString().replaceFirst("SELECT em", "SELECT COUNT(em)").replaceAll("ORDER BY.*", "");
        jakarta.persistence.Query countQuery = entityManager.createQuery(countJpql);
        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            countQuery.setParameter(param.getKey(), param.getValue());
        }
        Long totalCount = (Long) countQuery.getSingleResult();

        return new PageImpl<>(members, pageable, totalCount);
    }

    private Map<String, Object> convertMemberToMap(EventMember member) {
        Map<String, Object> memberMap = new HashMap<>();

        memberMap.put("id", member.getId());
        memberMap.put("membershipNumber", member.getMembershipNumber());
        memberMap.put("name", member.getName());
        memberMap.put("primaryEmail", member.getPrimaryEmail());
        memberMap.put("telephoneMobile", member.getTelephoneMobile());
        memberMap.put("regionDesc", member.getRegionDesc());
        memberMap.put("workplace", member.getWorkplace());
        memberMap.put("employer", member.getEmployer());
        memberMap.put("hasRegistered", member.getHasRegistered());
        memberMap.put("isAttending", member.getIsAttending());
        memberMap.put("isSpecialVote", member.getIsSpecialVote());
        memberMap.put("absenceReason", member.getAbsenceReason());
        memberMap.put("emailSent", member.getEmailSent());
        memberMap.put("smsSent", member.getSmsSent());
        memberMap.put("hasEmail", member.getHasEmail());
        memberMap.put("hasMobile", member.getHasMobile());
        memberMap.put("checkedIn", member.getCheckedIn());
        memberMap.put("checkInTime", member.getCheckInTime());
        memberMap.put("formSubmissionTime", member.getFormSubmissionTime());
        memberMap.put("createdAt", member.getCreatedAt());

        // Contact status
        boolean hasValidEmail = member.getPrimaryEmail() != null &&
                !member.getPrimaryEmail().trim().isEmpty() &&
                !member.getPrimaryEmail().contains("@temp-email.etu.nz");
        boolean hasValidMobile = member.getTelephoneMobile() != null &&
                !member.getTelephoneMobile().trim().isEmpty();

        memberMap.put("hasValidEmail", hasValidEmail);
        memberMap.put("hasValidMobile", hasValidMobile);
        memberMap.put("contactMethod", hasValidEmail ? (hasValidMobile ? "both" : "email_only") :
                (hasValidMobile ? "sms_only" : "no_contact"));

        return memberMap;
    }

    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBmmTimeline(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false, defaultValue = "7") int days) {
        try {
            Map<String, Object> timeline = new HashMap<>();

            // Get BMM event
            Event bmmEvent = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active BMM event found"));

            // Define time range
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(days);

            // Get all BMM-related notification logs
            List<NotificationLog> allLogs = notificationLogRepository.findBySentTimeBetween(startTime, endTime);
            List<NotificationLog> bmmLogs = allLogs.stream()
                    .filter(log -> log.getEventMember() != null &&
                            log.getEventMember().getEvent().getId().equals(bmmEvent.getId()))
                    .collect(Collectors.toList());

            // Filter by region if specified
            if (region != null && !region.isEmpty()) {
                bmmLogs = bmmLogs.stream()
                        .filter(log -> region.equals(log.getEventMember().getRegion()))
                        .collect(Collectors.toList());
            }

            // Group by stage and date
            Map<String, List<NotificationLog>> logsByStage = new HashMap<>();
            logsByStage.put("pre_registration", new ArrayList<>());
            logsByStage.put("confirmation", new ArrayList<>());
            logsByStage.put("special_vote", new ArrayList<>());
            logsByStage.put("other", new ArrayList<>());

            for (NotificationLog log : bmmLogs) {
                String emailType = log.getEmailType() != null ? log.getEmailType().toLowerCase() : "";
                String templateCode = log.getTemplateCode() != null ? log.getTemplateCode().toLowerCase() : "";

                if (emailType.contains("pre_registration") || templateCode.contains("pre_registration")) {
                    logsByStage.get("pre_registration").add(log);
                } else if (emailType.contains("confirmation") || templateCode.contains("confirmation")) {
                    logsByStage.get("confirmation").add(log);
                } else if (emailType.contains("special_vote") || templateCode.contains("special_vote")) {
                    logsByStage.get("special_vote").add(log);
                } else {
                    logsByStage.get("other").add(log);
                }
            }

            // Create timeline events
            List<Map<String, Object>> timelineEvents = new ArrayList<>();

            for (Map.Entry<String, List<NotificationLog>> entry : logsByStage.entrySet()) {
                String stageKey = entry.getKey();
                List<NotificationLog> stageLogs = entry.getValue();

                // Skip if stage filter is specified and doesn't match
                if (stage != null && !stage.isEmpty() && !stage.equals(stageKey)) {
                    continue;
                }

                // Group by date
                Map<LocalDate, List<NotificationLog>> logsByDate = stageLogs.stream()
                        .collect(Collectors.groupingBy(log -> log.getSentTime().toLocalDate()));

                for (Map.Entry<LocalDate, List<NotificationLog>> dateEntry : logsByDate.entrySet()) {
                    LocalDate date = dateEntry.getKey();
                    List<NotificationLog> dateLogs = dateEntry.getValue();

                    long successCount = dateLogs.stream().filter(log -> log.getIsSuccessful()).count();
                    long failureCount = dateLogs.size() - successCount;

                    // Count by region
                    Map<String, Long> regionCounts = dateLogs.stream()
                            .collect(Collectors.groupingBy(
                                    log -> log.getEventMember().getRegion() != null ? log.getEventMember().getRegion() : "Unknown",
                                    Collectors.counting()
                            ));

                    Map<String, Object> timelineEvent = new HashMap<>();
                    timelineEvent.put("date", date);
                    timelineEvent.put("stage", stageKey);
                    timelineEvent.put("stageName", getStageName(stageKey));
                    timelineEvent.put("totalSent", dateLogs.size());
                    timelineEvent.put("successCount", successCount);
                    timelineEvent.put("failureCount", failureCount);
                    timelineEvent.put("regionBreakdown", regionCounts);
                    timelineEvent.put("notificationTypes", dateLogs.stream()
                            .collect(Collectors.groupingBy(
                                    log -> log.getNotificationType().name(),
                                    Collectors.counting()
                            )));

                    timelineEvents.add(timelineEvent);
                }
            }

            // Sort timeline events by date
            timelineEvents.sort((a, b) ->
                    ((LocalDate) b.get("date")).compareTo((LocalDate) a.get("date")));

            // Get registration progress by stage
            Map<String, Object> stageProgress = getStageProgress(bmmEvent);

            // Get recent activity summary
            Map<String, Object> recentActivity = getRecentActivity(bmmEvent, 24); // Last 24 hours

            timeline.put("events", timelineEvents);
            timeline.put("stageProgress", stageProgress);
            timeline.put("recentActivity", recentActivity);
            timeline.put("dateRange", Map.of(
                    "start", startTime,
                    "end", endTime
            ));
            timeline.put("totalEvents", timelineEvents.size());

            return ResponseEntity.ok(ApiResponse.success("BMM timeline retrieved successfully", timeline));

        } catch (Exception e) {
            log.error("Failed to get BMM timeline", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve BMM timeline: " + e.getMessage()));
        }
    }

    @GetMapping("/stage-completion")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStageCompletionStats() {
        try {
            Event bmmEvent = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active BMM event found"));

            Map<String, Object> completion = new HashMap<>();

            // Get all event members
            List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);

            // Group by region
            Map<String, List<EventMember>> membersByRegion = allMembers.stream()
                    .collect(Collectors.groupingBy(member ->
                            member.getRegion() != null ? member.getRegion() : "Unknown"));

            Map<String, Object> regionStats = new HashMap<>();

            for (Map.Entry<String, List<EventMember>> entry : membersByRegion.entrySet()) {
                String region = entry.getKey();
                List<EventMember> members = entry.getValue();

                Map<String, Object> regionCompletion = new HashMap<>();

                // Stage 1: Pre-registration
                long preRegistered = members.stream()
                        .filter(member -> member.getRegistrationData() != null)
                        .count();

                // Stage 2: Confirmation
                long confirmed = members.stream()
                        .filter(member -> member.getIsAttending() != null)
                        .count();

                long attending = members.stream()
                        .filter(member -> Boolean.TRUE.equals(member.getIsAttending()))
                        .count();

                // Stage 3: Special vote (Southern only)
                long specialVoteEligible = 0;
                long specialVoteApplied = 0;

                if ("Southern Region".equals(region)) {
                    specialVoteEligible = members.stream()
                            .filter(member -> Boolean.FALSE.equals(member.getIsAttending()))
                            .count();

                    specialVoteApplied = members.stream()
                            .filter(member -> Boolean.TRUE.equals(member.getIsSpecialVote()))
                            .count();
                }

                regionCompletion.put("totalMembers", members.size());
                regionCompletion.put("stage1_preRegistered", preRegistered);
                regionCompletion.put("stage1_completion", members.size() > 0 ? (preRegistered * 100.0 / members.size()) : 0);
                regionCompletion.put("stage2_confirmed", confirmed);
                regionCompletion.put("stage2_completion", members.size() > 0 ? (confirmed * 100.0 / members.size()) : 0);
                regionCompletion.put("stage2_attending", attending);
                regionCompletion.put("stage3_eligible", specialVoteEligible);
                regionCompletion.put("stage3_applied", specialVoteApplied);
                regionCompletion.put("stage3_completion", specialVoteEligible > 0 ? (specialVoteApplied * 100.0 / specialVoteEligible) : 0);

                regionStats.put(region, regionCompletion);
            }

            completion.put("byRegion", regionStats);
            completion.put("generatedAt", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("Stage completion stats retrieved successfully", completion));

        } catch (Exception e) {
            log.error("Failed to get stage completion stats", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve stage completion stats: " + e.getMessage()));
        }
    }

    private String getStageName(String stageKey) {
        switch (stageKey) {
            case "pre_registration": return "Pre-Registration";
            case "confirmation": return "Attendance Confirmation";
            case "special_vote": return "Special Vote Application";
            default: return "Other";
        }
    }

    private Map<String, Object> getStageProgress(Event bmmEvent) {
        List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);

        Map<String, Object> progress = new HashMap<>();

        // Stage 1: Pre-registration
        long stage1Total = allMembers.size();
        long stage1Completed = allMembers.stream()
                .filter(member -> member.getRegistrationData() != null)
                .count();

        // Stage 2: Confirmation
        long stage2Completed = allMembers.stream()
                .filter(member -> member.getIsAttending() != null)
                .count();

        // Stage 3: Special vote (Southern only)
        long stage3Eligible = allMembers.stream()
                .filter(member -> "Southern Region".equals(member.getRegion()) &&
                        Boolean.FALSE.equals(member.getIsAttending()))
                .count();

        long stage3Completed = allMembers.stream()
                .filter(member -> Boolean.TRUE.equals(member.getIsSpecialVote()))
                .count();

        progress.put("stage1", Map.of(
                "name", "Pre-Registration",
                "total", stage1Total,
                "completed", stage1Completed,
                "percentage", stage1Total > 0 ? (stage1Completed * 100.0 / stage1Total) : 0
        ));

        progress.put("stage2", Map.of(
                "name", "Attendance Confirmation",
                "total", stage1Total,
                "completed", stage2Completed,
                "percentage", stage1Total > 0 ? (stage2Completed * 100.0 / stage1Total) : 0
        ));

        progress.put("stage3", Map.of(
                "name", "Special Vote Application",
                "total", stage3Eligible,
                "completed", stage3Completed,
                "percentage", stage3Eligible > 0 ? (stage3Completed * 100.0 / stage3Eligible) : 0
        ));

        return progress;
    }

    private Map<String, Object> getRecentActivity(Event bmmEvent, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);

        // Get recent registrations
        List<EventMember> recentRegistrations = eventMemberRepository.findByEvent(bmmEvent)
                .stream()
                .filter(member -> member.getRegistrationData() != null &&
                        member.getCreatedAt() != null &&
                        member.getCreatedAt().isAfter(since))
                .collect(Collectors.toList());

        // Get recent confirmations
        List<EventMember> recentConfirmations = eventMemberRepository.findByEvent(bmmEvent)
                .stream()
                .filter(member -> member.getIsAttending() != null &&
                        member.getUpdatedAt() != null &&
                        member.getUpdatedAt().isAfter(since))
                .collect(Collectors.toList());

        // Get recent notifications
        List<NotificationLog> recentNotifications = notificationLogRepository.findBySentTimeBetween(since, LocalDateTime.now())
                .stream()
                .filter(log -> log.getEventMember() != null &&
                        log.getEventMember().getEvent().getId().equals(bmmEvent.getId()))
                .collect(Collectors.toList());

        Map<String, Object> activity = new HashMap<>();
        activity.put("registrations", recentRegistrations.size());
        activity.put("confirmations", recentConfirmations.size());
        activity.put("notifications", recentNotifications.size());
        activity.put("period", hours + " hours");
        activity.put("since", since);

        return activity;
    }

    // 🎯 BMM Two-Stage Process Management

    @PostMapping("/assign-meeting-details")
    public ResponseEntity<ApiResponse<Map<String, Object>>> assignMeetingDetails(
            @RequestBody Map<String, Object> request) {
        log.info("Assigning BMM meeting details");

        try {
            List<String> membershipNumbers = (List<String>) request.get("membershipNumbers");
            String assignedVenue = (String) request.get("assignedVenue");
            String assignedRegion = (String) request.get("assignedRegion");
            String assignedDateTime = (String) request.get("assignedDateTime");

            if (membershipNumbers == null || membershipNumbers.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No membership numbers provided"));
            }

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> membersToUpdate = new ArrayList<>();

            for (String membershipNumber : membershipNumbers) {
                Optional<EventMember> memberOpt = eventMemberRepository
                        .findByMembershipNumberAndEvent(membershipNumber, currentBmmEvent);

                if (memberOpt.isPresent()) {
                    EventMember member = memberOpt.get();

                    // 只更新已提交偏好的成员
                    if ("PREFERENCE_SUBMITTED".equals(member.getBmmRegistrationStage())) {
                        member.setAssignedVenue(assignedVenue);
                        member.setAssignedRegion(assignedRegion);

                        if (assignedDateTime != null && !assignedDateTime.isEmpty()) {
                            try {
                                member.setAssignedDateTime(LocalDateTime.parse(assignedDateTime));
                            } catch (Exception e) {
                                log.warn("Failed to parse assigned date time for member {}: {}",
                                        membershipNumber, assignedDateTime);
                            }
                        }

                        member.setBmmRegistrationStage("ATTENDANCE_PENDING");
                        member.setUpdatedAt(LocalDateTime.now());
                        member.setBmmLastInteractionAt(LocalDateTime.now());

                        membersToUpdate.add(member);
                    }
                }
            }

            if (!membersToUpdate.isEmpty()) {
                eventMemberRepository.saveAll(membersToUpdate);

                Map<String, Object> response = new HashMap<>();
                response.put("updatedMembers", membersToUpdate.size());
                response.put("assignedVenue", assignedVenue);
                response.put("assignedRegion", assignedRegion);
                response.put("assignedDateTime", assignedDateTime);

                return ResponseEntity.ok(ApiResponse.success("Meeting details assigned successfully", response));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("No eligible members found for assignment"));
            }

        } catch (Exception e) {
            log.error("Failed to assign meeting details: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to assign meeting details: " + e.getMessage()));
        }
    }

    @PostMapping("/transition-to-stage-two")
    public ResponseEntity<ApiResponse<Map<String, Object>>> transitionToStageTwo(
            @RequestBody Map<String, Object> request) {
        log.info("Transitioning BMM members to Stage Two");

        try {
            String region = (String) request.get("region");
            List<String> membershipNumbers = (List<String>) request.get("membershipNumbers");

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> membersToUpdate = new ArrayList<>();

            if (membershipNumbers != null && !membershipNumbers.isEmpty()) {
                // 特定成员列表
                for (String membershipNumber : membershipNumbers) {
                    Optional<EventMember> memberOpt = eventMemberRepository
                            .findByMembershipNumberAndEvent(membershipNumber, currentBmmEvent);

                    if (memberOpt.isPresent()) {
                        EventMember member = memberOpt.get();
                        if ("PREFERENCE_SUBMITTED".equals(member.getBmmRegistrationStage())) {
                            member.setBmmRegistrationStage("ATTENDANCE_PENDING");
                            member.setBmmConfirmationRequestSent(true);
                            member.setBmmConfirmationRequestSentAt(LocalDateTime.now());
                            member.setBmmLastInteractionAt(LocalDateTime.now());
                            membersToUpdate.add(member);
                        }
                    }
                }
            } else if (region != null && !region.isEmpty()) {
                // 按地区批量转换
                String dbRegionValue = region.replace(" Region", "");
                List<EventMember> regionMembers = eventMemberRepository.findByEvent(currentBmmEvent)
                        .stream()
                        .filter(m -> dbRegionValue.equals(m.getRegionDesc()))
                        .filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage()))
                        .collect(Collectors.toList());

                regionMembers.forEach(member -> {
                    member.setBmmRegistrationStage("ATTENDANCE_PENDING");
                    member.setBmmConfirmationRequestSent(true);
                    member.setBmmConfirmationRequestSentAt(LocalDateTime.now());
                    member.setBmmLastInteractionAt(LocalDateTime.now());
                });

                membersToUpdate.addAll(regionMembers);
            }

            if (!membersToUpdate.isEmpty()) {
                eventMemberRepository.saveAll(membersToUpdate);

                Map<String, Object> response = new HashMap<>();
                response.put("transitionedMembers", membersToUpdate.size());
                response.put("region", region);
                response.put("newStage", "ATTENDANCE_PENDING");

                return ResponseEntity.ok(ApiResponse.success("Members transitioned to Stage Two successfully", response));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("No eligible members found for transition"));
            }

        } catch (Exception e) {
            log.error("Failed to transition members to Stage Two: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to transition members: " + e.getMessage()));
        }
    }

    @GetMapping("/stage-overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBmmStageOverview() {
        log.info("Fetching BMM stage overview");

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            Map<String, Object> overview = new HashMap<>();

            // Stage Statistics
            Map<String, Long> stageStats = new HashMap<>();
            stageStats.put("PENDING", allMembers.stream()
                    .filter(m -> "PENDING".equals(m.getBmmRegistrationStage()))
                    .count());
            stageStats.put("PREFERENCE_SUBMITTED", allMembers.stream()
                    .filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage()))
                    .count());
            stageStats.put("ATTENDANCE_PENDING", allMembers.stream()
                    .filter(m -> "ATTENDANCE_PENDING".equals(m.getBmmRegistrationStage()))
                    .count());
            stageStats.put("ATTENDANCE_CONFIRMED", allMembers.stream()
                    .filter(m -> "ATTENDANCE_CONFIRMED".equals(m.getBmmRegistrationStage()))
                    .count());
            stageStats.put("ATTENDANCE_DECLINED", allMembers.stream()
                    .filter(m -> "ATTENDANCE_DECLINED".equals(m.getBmmRegistrationStage()))
                    .count());

            overview.put("stageStatistics", stageStats);

            // Regional Stage Breakdown
            Map<String, Map<String, Long>> regionalBreakdown = new HashMap<>();
            List<String> regions = Arrays.asList("Northern", "Central", "Southern");

            for (String region : regions) {
                Map<String, Long> regionStageStats = new HashMap<>();
                List<EventMember> regionMembers = allMembers.stream()
                        .filter(m -> region.equals(m.getRegionDesc()))
                        .collect(Collectors.toList());

                regionStageStats.put("PENDING", regionMembers.stream()
                        .filter(m -> "PENDING".equals(m.getBmmRegistrationStage()))
                        .count());
                regionStageStats.put("PREFERENCE_SUBMITTED", regionMembers.stream()
                        .filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage()))
                        .count());
                regionStageStats.put("ATTENDANCE_PENDING", regionMembers.stream()
                        .filter(m -> "ATTENDANCE_PENDING".equals(m.getBmmRegistrationStage()))
                        .count());
                regionStageStats.put("ATTENDANCE_CONFIRMED", regionMembers.stream()
                        .filter(m -> "ATTENDANCE_CONFIRMED".equals(m.getBmmRegistrationStage()))
                        .count());
                regionStageStats.put("ATTENDANCE_DECLINED", regionMembers.stream()
                        .filter(m -> "ATTENDANCE_DECLINED".equals(m.getBmmRegistrationStage()))
                        .count());

                regionalBreakdown.put(region + " Region", regionStageStats);
            }

            overview.put("regionalBreakdown", regionalBreakdown);

            // Special Vote Overview
            Map<String, Object> specialVoteOverview = new HashMap<>();
            specialVoteOverview.put("totalEligible", allMembers.stream()
                    .filter(m -> m.getSpecialVoteEligible() != null && m.getSpecialVoteEligible())
                    .count());
            specialVoteOverview.put("pending", allMembers.stream()
                    .filter(m -> "PENDING".equals(m.getBmmSpecialVoteStatus()))
                    .count());
            specialVoteOverview.put("approved", allMembers.stream()
                    .filter(m -> "APPROVED".equals(m.getBmmSpecialVoteStatus()))
                    .count());
            specialVoteOverview.put("declined", allMembers.stream()
                    .filter(m -> "DECLINED".equals(m.getBmmSpecialVoteStatus()))
                    .count());

            overview.put("specialVoteOverview", specialVoteOverview);

            // Ticket Status
            Map<String, Long> ticketStats = new HashMap<>();
            ticketStats.put("PENDING", allMembers.stream()
                    .filter(m -> "PENDING".equals(m.getTicketStatus()))
                    .count());
            ticketStats.put("EMAIL_SENT", allMembers.stream()
                    .filter(m -> "EMAIL_SENT".equals(m.getTicketStatus()))
                    .count());
            ticketStats.put("DOWNLOAD_READY", allMembers.stream()
                    .filter(m -> "DOWNLOAD_READY".equals(m.getTicketStatus()))
                    .count());
            ticketStats.put("NOT_REQUIRED", allMembers.stream()
                    .filter(m -> "NOT_REQUIRED".equals(m.getTicketStatus()))
                    .count());

            overview.put("ticketStatistics", ticketStats);

            overview.put("totalMembers", allMembers.size());
            overview.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("BMM stage overview retrieved successfully", overview));

        } catch (Exception e) {
            log.error("Failed to get BMM stage overview: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get overview: " + e.getMessage()));
        }
    }

    @PostMapping("/manage-special-vote")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manageSpecialVote(
            @RequestBody Map<String, Object> request) {
        log.info("Managing special vote application");

        try {
            String membershipNumber = (String) request.get("membershipNumber");
            String decision = (String) request.get("decision"); // APPROVED, DECLINED
            String decisionBy = (String) request.get("decisionBy");
            String notes = (String) request.get("notes");

            if (membershipNumber == null || decision == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Membership number and decision are required"));
            }

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            Optional<EventMember> memberOpt = eventMemberRepository
                    .findByMembershipNumberAndEvent(membershipNumber, currentBmmEvent);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            member.setBmmSpecialVoteStatus(decision);
            member.setSpecialVoteDecisionDate(LocalDateTime.now());
            member.setSpecialVoteDecisionBy(decisionBy);
            member.setBmmNotes(notes);
            member.setBmmLastInteractionAt(LocalDateTime.now());

            if ("APPROVED".equals(decision)) {
                member.setIsSpecialVote(true);
                member.setSpecialVoteStatus("APPROVED");
            } else if ("DECLINED".equals(decision)) {
                member.setIsSpecialVote(false);
                member.setSpecialVoteStatus("REJECTED");
            }

            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", membershipNumber);
            response.put("decision", decision);
            response.put("decisionBy", decisionBy);
            response.put("decisionDate", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("Special vote decision updated successfully", response));

        } catch (Exception e) {
            log.error("Failed to manage special vote: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to manage special vote: " + e.getMessage()));
        }
    }

    // 🎯 详细成员管理界面 - 显示所有BMM字段
    @GetMapping("/detailed-members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDetailedMembers(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Fetching detailed BMM members data - region: {}, stage: {}, status: {}", region, stage, status);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Apply filters
            List<EventMember> filteredMembers = allMembers.stream()
                    .filter(member -> {
                        if (region != null && !region.isEmpty()) {
                            String dbRegion = region.replace(" Region", "");
                            return dbRegion.equals(member.getRegionDesc());
                        }
                        return true;
                    })
                    .filter(member -> {
                        if (stage != null && !stage.isEmpty()) {
                            return stage.equals(member.getBmmRegistrationStage());
                        }
                        return true;
                    })
                    .filter(member -> {
                        if (status != null && !status.isEmpty()) {
                            switch (status) {
                                case "registered":
                                    return Boolean.TRUE.equals(member.getHasRegistered());
                                case "attending":
                                    return Boolean.TRUE.equals(member.getIsAttending());
                                case "declined":
                                    return Boolean.FALSE.equals(member.getIsAttending());
                                case "special_vote":
                                    return Boolean.TRUE.equals(member.getSpecialVoteEligible());
                                case "no_response":
                                    return member.getHasRegistered() == null || !member.getHasRegistered();
                            }
                        }
                        return true;
                    })
                    .filter(member -> {
                        if (search != null && !search.isEmpty()) {
                            String searchLower = search.toLowerCase();
                            return (member.getName() != null && member.getName().toLowerCase().contains(searchLower)) ||
                                    (member.getMembershipNumber() != null && member.getMembershipNumber().toLowerCase().contains(searchLower)) ||
                                    (member.getPrimaryEmail() != null && member.getPrimaryEmail().toLowerCase().contains(searchLower)) ||
                                    (member.getWorkplace() != null && member.getWorkplace().toLowerCase().contains(searchLower));
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // Pagination
            int start = page * size;
            int end = Math.min(start + size, filteredMembers.size());
            List<EventMember> paginatedMembers = filteredMembers.subList(start, end);

            // Build detailed response
            List<Map<String, Object>> detailedMemberList = paginatedMembers.stream()
                    .map(this::buildDetailedMemberInfo)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("members", detailedMemberList);
            response.put("totalMembers", filteredMembers.size());
            response.put("currentPage", page);
            response.put("pageSize", size);
            response.put("totalPages", (int) Math.ceil((double) filteredMembers.size() / size));
            response.put("filters", Map.of(
                    "region", region != null ? region : "",
                    "stage", stage != null ? stage : "",
                    "status", status != null ? status : "",
                    "search", search != null ? search : ""
            ));

            return ResponseEntity.ok(ApiResponse.success("Detailed members retrieved successfully", response));

        } catch (Exception e) {
            log.error("Failed to get detailed members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get detailed members: " + e.getMessage()));
        }
    }

    // 🎯 构建详细成员信息
    private Map<String, Object> buildDetailedMemberInfo(EventMember member) {
        Map<String, Object> memberInfo = new HashMap<>();

        // 基本信息
        memberInfo.put("id", member.getId());
        memberInfo.put("membershipNumber", member.getMembershipNumber());
        memberInfo.put("name", member.getName());
        memberInfo.put("primaryEmail", member.getPrimaryEmail());
        memberInfo.put("telephoneMobile", member.getTelephoneMobile());
        memberInfo.put("region", member.getRegionDesc());
        memberInfo.put("workplace", member.getWorkplace());
        memberInfo.put("employer", member.getEmployer());
        memberInfo.put("branch", member.getBranch());

        // BMM 注册状态
        memberInfo.put("hasRegistered", member.getHasRegistered());
        memberInfo.put("isAttending", member.getIsAttending());
        memberInfo.put("bmmRegistrationStage", member.getBmmRegistrationStage());
        memberInfo.put("attendanceWillingness", member.getAttendanceWillingness());
        memberInfo.put("attendanceLikelihoodScore", member.getAttendanceLikelihoodScore());

        // BMM 偏好信息
        memberInfo.put("preferredVenues", member.getPreferredVenues());
        memberInfo.put("preferredTimes", member.getPreferredTimes());
        memberInfo.put("meetingFormat", member.getMeetingFormat());
        memberInfo.put("workplaceInfo", member.getWorkplaceInfo());
        memberInfo.put("additionalComments", member.getAdditionalComments());
        memberInfo.put("suggestedVenue", member.getSuggestedVenue());

        // 分配信息
        memberInfo.put("assignedVenue", member.getAssignedVenue());
        memberInfo.put("assignedDateTime", member.getAssignedDateTime());
        memberInfo.put("assignedRegion", member.getAssignedRegion());

        // 票据信息
        memberInfo.put("ticketStatus", member.getTicketStatus());
        memberInfo.put("ticketToken", member.getTicketToken());
        memberInfo.put("ticketGeneratedAt", member.getTicketGeneratedAt());
        memberInfo.put("ticketSentAt", member.getTicketSentAt());
        memberInfo.put("ticketSentMethod", member.getTicketSentMethod());

        // Special Vote信息
        memberInfo.put("specialVoteEligible", member.getSpecialVoteEligible());
        memberInfo.put("specialVotePreference", member.getSpecialVotePreference());
        memberInfo.put("bmmSpecialVoteStatus", member.getBmmSpecialVoteStatus());
        memberInfo.put("specialVoteApplicationDate", member.getSpecialVoteApplicationDate());
        memberInfo.put("specialVoteDecisionDate", member.getSpecialVoteDecisionDate());
        memberInfo.put("specialVoteDecisionBy", member.getSpecialVoteDecisionBy());

        // 通信记录
        memberInfo.put("bmmInvitationSent", member.getBmmInvitationSent());
        memberInfo.put("bmmInvitationSentAt", member.getBmmInvitationSentAt());
        memberInfo.put("bmmConfirmationRequestSent", member.getBmmConfirmationRequestSent());
        memberInfo.put("bmmConfirmationRequestSentAt", member.getBmmConfirmationRequestSentAt());
        memberInfo.put("emailSent", member.getEmailSent());
        memberInfo.put("emailSentAt", member.getEmailSentAt());
        memberInfo.put("smsSent", member.getSmsSent());
        memberInfo.put("smsSentAt", member.getSmsSentAt());

        // 时间戳
        memberInfo.put("formSubmissionTime", member.getFormSubmissionTime());
        memberInfo.put("bmmAttendanceConfirmedAt", member.getBmmAttendanceConfirmedAt());
        memberInfo.put("bmmAttendanceDeclinedAt", member.getBmmAttendanceDeclinedAt());
        memberInfo.put("bmmLastInteractionAt", member.getBmmLastInteractionAt());
        memberInfo.put("createdAt", member.getCreatedAt());
        memberInfo.put("updatedAt", member.getUpdatedAt());

        // 管理备注
        memberInfo.put("bmmNotes", member.getBmmNotes());
        memberInfo.put("bmmDeclineReason", member.getBmmDeclineReason());

        // Checkin信息
        memberInfo.put("checkedIn", member.getCheckedIn());
        memberInfo.put("checkInTime", member.getCheckInTime());
        memberInfo.put("checkInLocation", member.getCheckInLocation());
        memberInfo.put("checkInAdminUsername", member.getCheckInAdminUsername());
        memberInfo.put("checkInMethod", member.getCheckInMethod());
        memberInfo.put("checkInVenue", member.getCheckInVenue());

        // 联系方式状态
        memberInfo.put("hasEmail", member.getHasEmail());
        memberInfo.put("hasMobile", member.getHasMobile());

        return memberInfo;
    }

    // 🎯 通信历史记录
    @GetMapping("/member/{membershipNumber}/communication-history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberCommunicationHistory(
            @PathVariable String membershipNumber) {
        log.info("Fetching communication history for member: {}", membershipNumber);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            Optional<EventMember> memberOpt = eventMemberRepository
                    .findByMembershipNumberAndEvent(membershipNumber, currentBmmEvent);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            // Get notification logs for this member
            List<NotificationLog> notifications = notificationLogRepository.findByEventMember(member);

            List<Map<String, Object>> communicationHistory = notifications.stream()
                    .map(notification -> {
                        Map<String, Object> commRecord = new HashMap<>();
                        commRecord.put("id", notification.getId());
                        commRecord.put("type", notification.getNotificationType().toString());
                        commRecord.put("subject", notification.getSubject());
                        commRecord.put("content", notification.getContent());
                        commRecord.put("recipient", notification.getRecipient());
                        commRecord.put("sentTime", notification.getSentTime());
                        commRecord.put("isSuccessful", notification.getIsSuccessful());
                        commRecord.put("emailType", notification.getEmailType());
                        commRecord.put("adminUsername", notification.getAdminUsername());
                        commRecord.put("errorMessage", notification.getErrorMessage());
                        return commRecord;
                    })
                    .sorted((a, b) -> ((LocalDateTime) b.get("sentTime")).compareTo((LocalDateTime) a.get("sentTime")))
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", membershipNumber);
            response.put("memberName", member.getName());
            response.put("communicationHistory", communicationHistory);
            response.put("totalCommunications", communicationHistory.size());

            // Communication summary
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalEmails", communicationHistory.stream()
                    .filter(c -> "EMAIL".equals(c.get("type"))).count());
            summary.put("totalSMS", communicationHistory.stream()
                    .filter(c -> "SMS".equals(c.get("type"))).count());
            summary.put("successfulCommunications", communicationHistory.stream()
                    .filter(c -> Boolean.TRUE.equals(c.get("isSuccessful"))).count());
            summary.put("failedCommunications", communicationHistory.stream()
                    .filter(c -> Boolean.FALSE.equals(c.get("isSuccessful"))).count());

            response.put("summary", summary);

            return ResponseEntity.ok(ApiResponse.success("Communication history retrieved successfully", response));

        } catch (Exception e) {
            log.error("Failed to get communication history: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get communication history: " + e.getMessage()));
        }
    }

    // 🎯 BMM 动态图表数据
    @GetMapping("/dashboard-charts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMDashboardCharts() {
        log.info("Fetching BMM dashboard charts data");

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            Map<String, Object> chartsData = new HashMap<>();

            // 📊 阶段进度图表
            Map<String, Long> stageChart = Map.of(
                    "PENDING", allMembers.stream().filter(m -> "PENDING".equals(m.getBmmRegistrationStage())).count(),
                    "PREFERENCE_SUBMITTED", allMembers.stream().filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage())).count(),
                    "ATTENDANCE_PENDING", allMembers.stream().filter(m -> "ATTENDANCE_PENDING".equals(m.getBmmRegistrationStage())).count(),
                    "ATTENDANCE_CONFIRMED", allMembers.stream().filter(m -> "ATTENDANCE_CONFIRMED".equals(m.getBmmRegistrationStage())).count(),
                    "ATTENDANCE_DECLINED", allMembers.stream().filter(m -> "ATTENDANCE_DECLINED".equals(m.getBmmRegistrationStage())).count()
            );
            chartsData.put("stageProgressChart", stageChart);

            // 📊 地区分布图表
            Map<String, Map<String, Long>> regionChart = new HashMap<>();
            List<String> regions = Arrays.asList("Northern", "Central", "Southern");

            for (String region : regions) {
                Map<String, Long> regionData = new HashMap<>();
                List<EventMember> regionMembers = allMembers.stream()
                        .filter(m -> region.equals(m.getRegionDesc()))
                        .collect(Collectors.toList());

                regionData.put("total", (long) regionMembers.size());
                regionData.put("registered", regionMembers.stream().filter(m -> Boolean.TRUE.equals(m.getHasRegistered())).count());
                regionData.put("attending", regionMembers.stream().filter(m -> Boolean.TRUE.equals(m.getIsAttending())).count());
                regionData.put("declined", regionMembers.stream().filter(m -> Boolean.FALSE.equals(m.getIsAttending())).count());
                regionData.put("specialVote", regionMembers.stream().filter(m -> Boolean.TRUE.equals(m.getSpecialVoteEligible())).count());

                regionChart.put(region + " Region", regionData);
            }
            chartsData.put("regionDistributionChart", regionChart);

            // 📊 通信状态图表
            Map<String, Long> communicationChart = Map.of(
                    "emailSent", allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getBmmInvitationSent())).count(),
                    "confirmationSent", allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getBmmConfirmationRequestSent())).count(),
                    "smsSent", allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getSmsSent())).count(),
                    "noContact", allMembers.stream().filter(m ->
                            !Boolean.TRUE.equals(m.getBmmInvitationSent()) &&
                                    !Boolean.TRUE.equals(m.getSmsSent())).count()
            );
            chartsData.put("communicationChart", communicationChart);

            // 📊 票据状态图表
            Map<String, Long> ticketChart = Map.of(
                    "PENDING", allMembers.stream().filter(m -> "PENDING".equals(m.getTicketStatus())).count(),
                    "EMAIL_SENT", allMembers.stream().filter(m -> "EMAIL_SENT".equals(m.getTicketStatus())).count(),
                    "SMS_SENT", allMembers.stream().filter(m -> "SMS_SENT".equals(m.getTicketStatus())).count(),
                    "DOWNLOAD_READY", allMembers.stream().filter(m -> "DOWNLOAD_READY".equals(m.getTicketStatus())).count(),
                    "NOT_REQUIRED", allMembers.stream().filter(m -> "NOT_REQUIRED".equals(m.getTicketStatus())).count()
            );
            chartsData.put("ticketStatusChart", ticketChart);

            // 📊 时间线数据（最近7天的活动）
            LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
            Map<String, List<Map<String, Object>>> timelineData = new HashMap<>();

            for (int i = 6; i >= 0; i--) {
                LocalDateTime dayStart = LocalDateTime.now().minusDays(i).toLocalDate().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1);

                List<EventMember> dayRegistrations = allMembers.stream()
                        .filter(m -> m.getFormSubmissionTime() != null &&
                                m.getFormSubmissionTime().isAfter(dayStart) &&
                                m.getFormSubmissionTime().isBefore(dayEnd))
                        .collect(Collectors.toList());

                List<EventMember> dayConfirmations = allMembers.stream()
                        .filter(m -> m.getBmmAttendanceConfirmedAt() != null &&
                                m.getBmmAttendanceConfirmedAt().isAfter(dayStart) &&
                                m.getBmmAttendanceConfirmedAt().isBefore(dayEnd))
                        .collect(Collectors.toList());

                Map<String, Object> dayData = Map.of(
                        "date", dayStart.toLocalDate().toString(),
                        "registrations", dayRegistrations.size(),
                        "confirmations", dayConfirmations.size(),
                        "totalActivity", dayRegistrations.size() + dayConfirmations.size()
                );

                timelineData.computeIfAbsent("daily", k -> new ArrayList<>()).add(dayData);
            }
            chartsData.put("timelineChart", timelineData);

            // 📊 出席意愿分析
            Map<String, Long> attendanceWillingnessChart = Map.of(
                    "yes", allMembers.stream().filter(m -> "yes".equals(m.getAttendanceWillingness())).count(),
                    "no", allMembers.stream().filter(m -> "no".equals(m.getAttendanceWillingness())).count(),
                    "maybe", allMembers.stream().filter(m -> "maybe".equals(m.getAttendanceWillingness())).count(),
                    "unknown", allMembers.stream().filter(m -> m.getAttendanceWillingness() == null || m.getAttendanceWillingness().isEmpty()).count()
            );
            chartsData.put("attendanceWillingnessChart", attendanceWillingnessChart);

            // 📊 场地偏好分析
            Map<String, Integer> venuePreferences = new HashMap<>();
            allMembers.stream()
                    .filter(m -> m.getPreferredVenues() != null && !m.getPreferredVenues().isEmpty())
                    .forEach(m -> {
                        String[] venues = m.getPreferredVenues().split(",");
                        for (String venue : venues) {
                            venuePreferences.merge(venue.trim(), 1, Integer::sum);
                        }
                    });
            chartsData.put("venuePreferencesChart", venuePreferences);

            // 📊 时间偏好分析
            Map<String, Integer> timePreferences = new HashMap<>();
            allMembers.stream()
                    .filter(m -> m.getPreferredTimes() != null && !m.getPreferredTimes().isEmpty())
                    .forEach(m -> {
                        String[] times = m.getPreferredTimes().split(",");
                        for (String time : times) {
                            timePreferences.merge(time.trim(), 1, Integer::sum);
                        }
                    });
            chartsData.put("timePreferencesChart", timePreferences);

            // 📊 总体统计
            Map<String, Object> overallStats = Map.of(
                    "totalMembers", allMembers.size(),
                    "registrationRate", allMembers.size() > 0 ?
                            (allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getHasRegistered())).count() * 100.0 / allMembers.size()) : 0,
                    "attendanceRate", allMembers.size() > 0 ?
                            (allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getIsAttending())).count() * 100.0 / allMembers.size()) : 0,
                    "specialVoteRate", allMembers.size() > 0 ?
                            (allMembers.stream().filter(m -> Boolean.TRUE.equals(m.getSpecialVoteEligible())).count() * 100.0 / allMembers.size()) : 0,
                    "lastUpdated", LocalDateTime.now()
            );
            chartsData.put("overallStats", overallStats);

            return ResponseEntity.ok(ApiResponse.success("BMM dashboard charts data retrieved successfully", chartsData));

        } catch (Exception e) {
            log.error("Failed to get BMM dashboard charts: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get charts data: " + e.getMessage()));
        }
    }

    // 🎯 实时刷新统计数据
    @GetMapping("/real-time-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRealTimeStats() {
        log.info("Fetching real-time BMM statistics");

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastHour = now.minusHours(1);
            LocalDateTime today = now.toLocalDate().atStartOfDay();

            Map<String, Object> realTimeStats = new HashMap<>();

            // 实时计数
            realTimeStats.put("totalMembers", allMembers.size());
            realTimeStats.put("registeredToday", allMembers.stream()
                    .filter(m -> m.getFormSubmissionTime() != null && m.getFormSubmissionTime().isAfter(today))
                    .count());
            realTimeStats.put("confirmedToday", allMembers.stream()
                    .filter(m -> m.getBmmAttendanceConfirmedAt() != null && m.getBmmAttendanceConfirmedAt().isAfter(today))
                    .count());
            realTimeStats.put("lastHourActivity", allMembers.stream()
                    .filter(m -> m.getBmmLastInteractionAt() != null && m.getBmmLastInteractionAt().isAfter(lastHour))
                    .count());

            // 阶段分布
            Map<String, Long> currentStageDistribution = new HashMap<>();
            for (EventMember member : allMembers) {
                String stage = member.getBmmRegistrationStage() != null ? member.getBmmRegistrationStage() : "PENDING";
                currentStageDistribution.merge(stage, 1L, Long::sum);
            }
            realTimeStats.put("stageDistribution", currentStageDistribution);

            // 地区进度
            Map<String, Map<String, Object>> regionProgress = new HashMap<>();
            List<String> regions = Arrays.asList("Northern", "Central", "Southern");

            for (String region : regions) {
                List<EventMember> regionMembers = allMembers.stream()
                        .filter(m -> region.equals(m.getRegionDesc()))
                        .collect(Collectors.toList());

                Map<String, Object> progress = new HashMap<>();
                progress.put("total", regionMembers.size());
                progress.put("stage1Complete", regionMembers.stream()
                        .filter(m -> !"PENDING".equals(m.getBmmRegistrationStage())).count());
                progress.put("stage2Complete", regionMembers.stream()
                        .filter(m -> "ATTENDANCE_CONFIRMED".equals(m.getBmmRegistrationStage()) ||
                                "ATTENDANCE_DECLINED".equals(m.getBmmRegistrationStage())).count());
                progress.put("progressPercentage", regionMembers.size() > 0 ?
                        (regionMembers.stream().filter(m -> !"PENDING".equals(m.getBmmRegistrationStage())).count() * 100.0 / regionMembers.size()) : 0);

                regionProgress.put(region, progress);
            }
            realTimeStats.put("regionProgress", regionProgress);

            // 近期趋势
            Map<String, Object> trends = new HashMap<>();
            LocalDateTime yesterday = now.minusDays(1);
            trends.put("registrationsLast24h", allMembers.stream()
                    .filter(m -> m.getFormSubmissionTime() != null && m.getFormSubmissionTime().isAfter(yesterday))
                    .count());
            trends.put("confirmationsLast24h", allMembers.stream()
                    .filter(m -> m.getBmmAttendanceConfirmedAt() != null && m.getBmmAttendanceConfirmedAt().isAfter(yesterday))
                    .count());
            trends.put("emailsSentLast24h", allMembers.stream()
                    .filter(m -> m.getBmmInvitationSentAt() != null && m.getBmmInvitationSentAt().isAfter(yesterday))
                    .count());

            realTimeStats.put("trends", trends);
            realTimeStats.put("timestamp", now);
            realTimeStats.put("eventName", currentBmmEvent.getName());

            return ResponseEntity.ok(ApiResponse.success("Real-time statistics retrieved successfully", realTimeStats));

        } catch (Exception e) {
            log.error("Failed to get real-time stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get real-time stats: " + e.getMessage()));
        }
    }

    // TICKET: BMM票据批量发送
    @PostMapping("/send-bmm-tickets")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendBmmTicketsBatch(@RequestBody Map<String, Object> request) {
        try {
            log.info("=== BMM Tickets Batch Send Requested ===");

            // Get BMM event
            Event bmmEvent = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active BMM event found"));

            String sendType = (String) request.get("sendType"); // "all_confirmed", "selected_members", "missing_tickets"
            List<String> selectedMemberIds = (List<String>) request.getOrDefault("selectedMembers", new ArrayList<>());

            List<EventMember> targetMembers = new ArrayList<>();

            switch (sendType) {
                case "all_confirmed":
                    // 所有确认出席但没收到票据的会员
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> em.getTicketStatus() == null ||
                                    (!em.getTicketStatus().equals("EMAIL_SENT") &&
                                            !em.getTicketStatus().equals("SMS_SENT")))
                            .collect(Collectors.toList());
                    break;

                case "selected_members":
                    // 指定的会员列表
                    targetMembers = selectedMemberIds.stream()
                            .map(idStr -> eventMemberRepository.findById(Long.valueOf(idStr)))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(em -> em.getEvent().getId().equals(bmmEvent.getId()))
                            .collect(Collectors.toList());
                    break;

                case "missing_tickets":
                    // 所有确认出席但票据发送失败的会员
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> em.getTicketStatus() != null &&
                                    (em.getTicketStatus().equals("FAILED") ||
                                            em.getTicketStatus().equals("EMAIL_FAILED") ||
                                            em.getTicketStatus().equals("SMS_FAILED")))
                            .collect(Collectors.toList());
                    break;

                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid sendType: " + sendType));
            }

            log.info("Found {} members for BMM ticket sending (type: {})", targetMembers.size(), sendType);

            // 发送票据
            int emailsSent = 0;
            int smsSent = 0;
            int failed = 0;

            for (EventMember member : targetMembers) {
                try {
                    ticketEmailService.sendBMMTicketOnConfirmation(member);

                    // 检查发送状态
                    String status = member.getTicketStatus();
                    if ("EMAIL_SENT".equals(status)) {
                        emailsSent++;
                    } else if ("SMS_SENT".equals(status)) {
                        smsSent++;
                    } else {
                        failed++;
                    }

                    // 每10个暂停一秒避免过载
                    if ((emailsSent + smsSent) % 10 == 0) {
                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    log.error("Failed to send BMM ticket to member {}: {}",
                            member.getMembershipNumber(), e.getMessage());
                    failed++;
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("eventName", bmmEvent.getName());
            response.put("sendType", sendType);
            response.put("totalTargeted", targetMembers.size());
            response.put("emailsSent", emailsSent);
            response.put("smsSent", smsSent);
            response.put("failed", failed);
            response.put("successRate", targetMembers.size() > 0 ?
                    String.format("%.1f%%", ((emailsSent + smsSent) * 100.0 / targetMembers.size())) : "0%");

            log.info("BMM tickets batch send completed: {} emails, {} SMS, {} failed out of {} total",
                    emailsSent, smsSent, failed, targetMembers.size());

            return ResponseEntity.ok(ApiResponse.success("BMM tickets sent successfully", response));

        } catch (Exception e) {
            log.error("Failed to send BMM tickets batch: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send BMM tickets: " + e.getMessage()));
        }
    }

    // TICKET: 获取票据发送统计
    @GetMapping("/ticket-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTicketStats() {
        try {
            Event bmmEvent = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active BMM event found"));

            List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);
            List<EventMember> confirmedMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                    bmmEvent, "ATTENDANCE_CONFIRMED");

            long ticketsEmailSent = confirmedMembers.stream()
                    .filter(em -> "EMAIL_SENT".equals(em.getTicketStatus()))
                    .count();

            long ticketsSmsSent = confirmedMembers.stream()
                    .filter(em -> "SMS_SENT".equals(em.getTicketStatus()))
                    .count();

            long ticketsFailed = confirmedMembers.stream()
                    .filter(em -> em.getTicketStatus() != null &&
                            (em.getTicketStatus().contains("FAILED") ||
                                    "NO_CONTACT_METHOD".equals(em.getTicketStatus())))
                    .count();

            long pendingTickets = confirmedMembers.stream()
                    .filter(em -> em.getTicketStatus() == null ||
                            "PENDING".equals(em.getTicketStatus()) ||
                            "GENERATED".equals(em.getTicketStatus()))
                    .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalMembers", allMembers.size());
            stats.put("confirmedAttendance", confirmedMembers.size());
            stats.put("ticketsEmailSent", ticketsEmailSent);
            stats.put("ticketsSmsSent", ticketsSmsSent);
            stats.put("totalTicketsSent", ticketsEmailSent + ticketsSmsSent);
            stats.put("ticketsFailed", ticketsFailed);
            stats.put("pendingTickets", pendingTickets);
            stats.put("deliveryRate", confirmedMembers.size() > 0 ?
                    String.format("%.1f%%", ((ticketsEmailSent + ticketsSmsSent) * 100.0 / confirmedMembers.size())) : "0%");

            return ResponseEntity.ok(ApiResponse.success("Ticket statistics retrieved successfully", stats));

        } catch (Exception e) {
            log.error("Failed to get ticket statistics: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get ticket statistics: " + e.getMessage()));
        }
    }

    // TICKET: 预览票据发送对象
    @PostMapping("/preview-ticket-recipients")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewTicketRecipients(@RequestBody Map<String, Object> request) {
        try {
            Event bmmEvent = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING)
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active BMM event found"));

            String previewType = (String) request.get("previewType");
            List<EventMember> targetMembers = new ArrayList<>();

            switch (previewType) {
                case "all_confirmed":
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> em.getTicketStatus() == null ||
                                    (!em.getTicketStatus().equals("EMAIL_SENT") &&
                                            !em.getTicketStatus().equals("SMS_SENT")))
                            .collect(Collectors.toList());
                    break;

                case "missing_tickets":
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> em.getTicketStatus() != null &&
                                    (em.getTicketStatus().equals("FAILED") ||
                                            em.getTicketStatus().equals("EMAIL_FAILED") ||
                                            em.getTicketStatus().equals("SMS_FAILED")))
                            .collect(Collectors.toList());
                    break;

                case "email_recipients":
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> em.getPrimaryEmail() != null &&
                                    !em.getPrimaryEmail().trim().isEmpty() &&
                                    !em.getPrimaryEmail().contains("@temp-email.etu.nz"))
                            .collect(Collectors.toList());
                    break;

                case "sms_recipients":
                    targetMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                                    bmmEvent, "ATTENDANCE_CONFIRMED").stream()
                            .filter(em -> (em.getPrimaryEmail() == null ||
                                    em.getPrimaryEmail().trim().isEmpty() ||
                                    em.getPrimaryEmail().contains("@temp-email.etu.nz")) &&
                                    em.getTelephoneMobile() != null &&
                                    !em.getTelephoneMobile().trim().isEmpty())
                            .collect(Collectors.toList());
                    break;

                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid previewType: " + previewType));
            }

            // 构建预览数据
            List<Map<String, Object>> membersData = targetMembers.stream()
                    .map(this::buildMemberMapForTicketPreview)
                    .collect(Collectors.toList());

            Map<String, Object> preview = new HashMap<>();
            preview.put("previewType", previewType);
            preview.put("totalCount", targetMembers.size());
            preview.put("members", membersData);
            preview.put("emailRecipients", membersData.stream()
                    .filter(m -> "email".equals(m.get("deliveryMethod")) || "both".equals(m.get("deliveryMethod")))
                    .count());
            preview.put("smsRecipients", membersData.stream()
                    .filter(m -> "sms".equals(m.get("deliveryMethod")) || "both".equals(m.get("deliveryMethod")))
                    .count());

            return ResponseEntity.ok(ApiResponse.success("Ticket recipients preview generated", preview));

        } catch (Exception e) {
            log.error("Failed to preview ticket recipients: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to preview: " + e.getMessage()));
        }
    }

    // Helper method for ticket preview
    private Map<String, Object> buildMemberMapForTicketPreview(EventMember eventMember) {
        Map<String, Object> memberMap = new HashMap<>();
        memberMap.put("id", eventMember.getId());
        memberMap.put("membershipNumber", eventMember.getMembershipNumber());
        memberMap.put("name", eventMember.getName());
        memberMap.put("primaryEmail", eventMember.getPrimaryEmail());
        memberMap.put("telephoneMobile", eventMember.getTelephoneMobile());
        memberMap.put("regionDesc", eventMember.getRegionDesc());
        memberMap.put("assignedVenue", eventMember.getAssignedVenue());
        memberMap.put("assignedDateTime", eventMember.getAssignedDateTime());
        memberMap.put("ticketStatus", eventMember.getTicketStatus());
        memberMap.put("hasTicketToken", eventMember.getTicketToken() != null);

        // 确定发送方式
        boolean hasValidEmail = eventMember.getPrimaryEmail() != null &&
                !eventMember.getPrimaryEmail().trim().isEmpty() &&
                !eventMember.getPrimaryEmail().contains("@temp-email.etu.nz");
        boolean hasValidMobile = eventMember.getTelephoneMobile() != null &&
                !eventMember.getTelephoneMobile().trim().isEmpty();

        if (hasValidEmail && hasValidMobile) {
            memberMap.put("deliveryMethod", "email"); // 优先邮件
        } else if (hasValidEmail) {
            memberMap.put("deliveryMethod", "email");
        } else if (hasValidMobile) {
            memberMap.put("deliveryMethod", "sms");
        } else {
            memberMap.put("deliveryMethod", "none");
        }

        return memberMap;
    }

    // TESTING AND VALIDATION ENDPOINTS

    // Test BMM system end-to-end
    @GetMapping("/test-system/{membershipNumber}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testBMMSystem(
            @PathVariable String membershipNumber) {
        try {
            log.info("Testing BMM system for member: {}", membershipNumber);

            // Find member in BMM event
            Optional<Event> bmmEventOpt = eventRepository.findByEventCodeAndIsActiveTrue("BMM2025");
            if (!bmmEventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("BMM event not found"));
            }

            Event bmmEvent = bmmEventOpt.get();
            Optional<EventMember> memberOpt = eventMemberRepository.findByMembershipNumberAndEvent(membershipNumber, bmmEvent);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found in BMM event"));
            }

            EventMember member = memberOpt.get();

            Map<String, Object> testResults = new HashMap<>();
            testResults.put("membershipNumber", membershipNumber);
            testResults.put("memberName", member.getName());
            testResults.put("eventId", bmmEvent.getId());
            testResults.put("eventName", bmmEvent.getName());

            // Test 1: Check registration stage
            testResults.put("registrationStage", member.getBmmRegistrationStage());
            testResults.put("stageValid", member.getBmmRegistrationStage() != null);

            // Test 2: Check ticket token
            testResults.put("hasTicketToken", member.getTicketToken() != null);
            testResults.put("ticketToken", member.getTicketToken());
            testResults.put("ticketStatus", member.getTicketStatus());

            // Test 3: Check assigned details
            testResults.put("assignedVenue", member.getAssignedVenue());
            testResults.put("assignedDateTime", member.getAssignedDateTime());
            testResults.put("assignedRegion", member.getAssignedRegion());

            // Test 4: Generate test URLs
            if (member.getTicketToken() != null) {
                String baseUrl = "https://events.etu.nz";
                testResults.put("ticketUrl", baseUrl + "/ticket?token=" + member.getTicketToken());
                testResults.put("qrCodeUrl", baseUrl + "/api/admin/ticket-emails/bmm-ticket/" + member.getTicketToken() + "/qrcode");
            }

            // Test 5: Check communication status
            testResults.put("invitationSent", member.getBmmInvitationSent());
            testResults.put("confirmationRequestSent", member.getBmmConfirmationRequestSent());
            testResults.put("ticketSent", member.getQrCodeEmailSent() || member.getSmsSent());

            // Test 6: Check special vote eligibility
            testResults.put("specialVoteEligible", member.getSpecialVoteEligible());
            testResults.put("region", member.getRegionDesc());

            // Test 7: Check check-in status
            testResults.put("checkedIn", member.getCheckedIn());
            testResults.put("checkInTime", member.getCheckInTime());

            // Overall system health check
            boolean systemHealthy = member.getBmmRegistrationStage() != null &&
                    member.getTicketToken() != null &&
                    member.getAssignedVenue() != null;
            testResults.put("systemHealthy", systemHealthy);

            return ResponseEntity.ok(ApiResponse.success("BMM system test completed", testResults));

        } catch (Exception e) {
            log.error("BMM system test failed for member {}: {}", membershipNumber, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("System test failed: " + e.getMessage()));
        }
    }

    // Validate QR code data
    @PostMapping("/validate-qrcode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateQRCode(@RequestBody Map<String, String> qrData) {
        try {
            log.info("Validating QR code data");

            Map<String, Object> validation = new HashMap<>();
            validation.put("isValid", false);
            validation.put("errors", new ArrayList<String>());

            String membershipNumber = qrData.get("membershipNumber");
            String token = qrData.get("token");
            String type = qrData.get("type");

            List<String> errors = new ArrayList<>();

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                errors.add("Missing membership number");
            }

            if (token == null || token.trim().isEmpty()) {
                errors.add("Missing token");
            }

            if (type == null || !type.equals("bmm_checkin")) {
                errors.add("Invalid or missing type (should be 'bmm_checkin')");
            }

            if (errors.isEmpty()) {
                // Validate token exists in database
                try {
                    UUID ticketToken = UUID.fromString(token);
                    Optional<EventMember> memberOpt = eventMemberRepository.findByTicketToken(ticketToken);

                    if (memberOpt.isPresent()) {
                        EventMember member = memberOpt.get();

                        if (member.getMembershipNumber().equals(membershipNumber)) {
                            validation.put("isValid", true);
                            validation.put("memberFound", true);
                            validation.put("memberName", member.getName());
                            validation.put("registrationStage", member.getBmmRegistrationStage());
                            validation.put("canCheckIn", "ATTENDANCE_CONFIRMED".equals(member.getBmmRegistrationStage()));
                        } else {
                            errors.add("Membership number mismatch");
                        }
                    } else {
                        errors.add("Invalid ticket token");
                    }
                } catch (IllegalArgumentException e) {
                    errors.add("Invalid token format");
                }
            }

            validation.put("errors", errors);

            return ResponseEntity.ok(ApiResponse.success("QR code validation completed", validation));

        } catch (Exception e) {
            log.error("QR code validation failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("QR code validation failed: " + e.getMessage()));
        }
    }

    // Test check-in process
    @PostMapping("/test-checkin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testCheckIn(@RequestBody Map<String, String> request) {
        try {
            String membershipNumber = request.get("membershipNumber");
            String token = request.get("token");

            log.info("Testing check-in for member: {}", membershipNumber);

            Map<String, Object> testResult = new HashMap<>();
            testResult.put("membershipNumber", membershipNumber);
            testResult.put("token", token);

            // Find member
            EventMember member = null;

            if (token != null) {
                try {
                    UUID ticketToken = UUID.fromString(token);
                    Optional<EventMember> memberOpt = eventMemberRepository.findByTicketToken(ticketToken);
                    if (memberOpt.isPresent()) {
                        member = memberOpt.get();
                    }
                } catch (IllegalArgumentException e) {
                    testResult.put("error", "Invalid token format");
                }
            }

            if (member == null && membershipNumber != null) {
                Optional<Event> bmmEventOpt = eventRepository.findByEventCodeAndIsActiveTrue("BMM2025");
                if (bmmEventOpt.isPresent()) {
                    Optional<EventMember> memberOpt = eventMemberRepository.findByMembershipNumberAndEvent(
                            membershipNumber, bmmEventOpt.get());
                    if (memberOpt.isPresent()) {
                        member = memberOpt.get();
                    }
                }
            }

            if (member == null) {
                testResult.put("success", false);
                testResult.put("error", "Member not found");
                return ResponseEntity.ok(ApiResponse.success("Check-in test completed", testResult));
            }

            // Check eligibility
            testResult.put("memberFound", true);
            testResult.put("memberName", member.getName());
            testResult.put("registrationStage", member.getBmmRegistrationStage());
            testResult.put("canCheckIn", "ATTENDANCE_CONFIRMED".equals(member.getBmmRegistrationStage()));
            testResult.put("alreadyCheckedIn", member.getCheckedIn());
            testResult.put("checkInTime", member.getCheckInTime());

            // Simulate check-in (without actually checking in)
            if ("ATTENDANCE_CONFIRMED".equals(member.getBmmRegistrationStage())) {
                testResult.put("success", true);
                testResult.put("message", member.getCheckedIn() ? "Already checked in" : "Ready for check-in");
            } else {
                testResult.put("success", false);
                testResult.put("message", "Not eligible for check-in - attendance not confirmed");
            }

            return ResponseEntity.ok(ApiResponse.success("Check-in test completed", testResult));

        } catch (Exception e) {
            log.error("Check-in test failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Check-in test failed: " + e.getMessage()));
        }
    }

    // Get system health status
    @GetMapping("/system-health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        try {
            log.info("Checking BMM system health");

            Optional<Event> bmmEventOpt = eventRepository.findByEventCodeAndIsActiveTrue("BMM2025");
            if (!bmmEventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("BMM event not found"));
            }

            Event bmmEvent = bmmEventOpt.get();
            List<EventMember> allMembers = eventMemberRepository.findByEvent(bmmEvent);

            Map<String, Object> health = new HashMap<>();
            health.put("eventId", bmmEvent.getId());
            health.put("eventName", bmmEvent.getName());
            health.put("totalMembers", allMembers.size());

            // Count by stage
            Map<String, Long> stageCount = allMembers.stream()
                    .collect(Collectors.groupingBy(
                            em -> em.getBmmRegistrationStage() != null ? em.getBmmRegistrationStage() : "NULL",
                            Collectors.counting()
                    ));
            health.put("stageDistribution", stageCount);

            // Count ticket status
            long membersWithTickets = allMembers.stream()
                    .filter(em -> em.getTicketToken() != null)
                    .count();
            health.put("membersWithTickets", membersWithTickets);

            // Count check-ins
            long checkedInMembers = allMembers.stream()
                    .filter(em -> em.getCheckedIn() != null && em.getCheckedIn())
                    .count();
            health.put("checkedInMembers", checkedInMembers);

            // Count communication status
            long invitationsSent = allMembers.stream()
                    .filter(em -> em.getBmmInvitationSent() != null && em.getBmmInvitationSent())
                    .count();
            health.put("invitationsSent", invitationsSent);

            long confirmationRequestsSent = allMembers.stream()
                    .filter(em -> em.getBmmConfirmationRequestSent() != null && em.getBmmConfirmationRequestSent())
                    .count();
            health.put("confirmationRequestsSent", confirmationRequestsSent);

            long ticketsSent = allMembers.stream()
                    .filter(em -> (em.getQrCodeEmailSent() != null && em.getQrCodeEmailSent()) ||
                            (em.getSmsSent() != null && em.getSmsSent()))
                    .count();
            health.put("ticketsSent", ticketsSent);

            // System health indicators
            boolean healthy = bmmEvent.getIsActive() &&
                    allMembers.size() > 0 &&
                    membersWithTickets > 0;
            health.put("systemHealthy", healthy);

            return ResponseEntity.ok(ApiResponse.success("System health check completed", health));

        } catch (Exception e) {
            log.error("System health check failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Health check failed: " + e.getMessage()));
        }
    }

    // Get all BMM members with filtering support for venue assignment page
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMMembers(
            @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        try {
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> members = eventMemberRepository.findByEvent(currentBmmEvent);

            // Filter by stage if provided
            if (stage != null && !stage.isEmpty()) {
                switch (stage) {
                    case "PREFERENCE_SUBMITTED":
                        members = members.stream()
                                .filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage()))
                                .collect(Collectors.toList());
                        break;
                    case "VENUE_ASSIGNED":
                        members = members.stream()
                                .filter(m -> "VENUE_ASSIGNED".equals(m.getBmmRegistrationStage()))
                                .collect(Collectors.toList());
                        break;
                    case "CONFIRMED_ATTENDANCE":
                        members = members.stream()
                                .filter(m -> "CONFIRMED_ATTENDANCE".equals(m.getBmmRegistrationStage()))
                                .collect(Collectors.toList());
                        break;
                }
            }

            // Apply pagination
            int totalElements = members.size();
            int totalPages = (int) Math.ceil((double) totalElements / size);
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, totalElements);

            List<EventMember> pageMembers = startIndex < totalElements ?
                    members.subList(startIndex, endIndex) : new ArrayList<>();

            Map<String, Object> result = new HashMap<>();
            result.put("content", pageMembers);
            result.put("totalElements", totalElements);
            result.put("totalPages", totalPages);
            result.put("currentPage", page);
            result.put("pageSize", size);

            return ResponseEntity.ok(ApiResponse.success("BMM members retrieved successfully", result));

        } catch (Exception e) {
            log.error("Failed to get BMM members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve members: " + e.getMessage()));
        }
    }

    // Get venues with capacity information for venue assignment
    @GetMapping("/venues-with-capacity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVenuesWithCapacity() {
        try {
            // Load venue configuration
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/bmm-venues-config.json");

            if (is == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Venue configuration not found"));
            }

            JsonNode venueConfig = mapper.readTree(is);
            Map<String, Object> venueData = new HashMap<>();

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Process each region
            JsonNode regions = venueConfig.get("regions");
            if (regions != null && regions.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> regionIter = regions.fields();

                while (regionIter.hasNext()) {
                    Map.Entry<String, JsonNode> regionEntry = regionIter.next();
                    String regionName = regionEntry.getKey();
                    JsonNode regionVenues = regionEntry.getValue().get("venues");

                    List<Map<String, Object>> venueList = new ArrayList<>();

                    if (regionVenues != null && regionVenues.isArray()) {
                        for (JsonNode venue : regionVenues) {
                            Map<String, Object> venueInfo = new HashMap<>();
                            String venueName = venue.get("name").asText();
                            JsonNode capacityNode = venue.get("capacity");
                            int capacity = (capacityNode != null && !capacityNode.isNull()) ? capacityNode.asInt() : 0;

                            // Count assigned members for this venue
                            long assignedCount = allMembers.stream()
                                    .filter(m -> venueName.equals(m.getAssignedVenueFinal()))
                                    .count();

                            venueInfo.put("name", venueName);
                            venueInfo.put("capacity", capacity);
                            venueInfo.put("assigned", assignedCount);
                            venueInfo.put("available", capacity - assignedCount);
                            venueInfo.put("utilizationRate", capacity > 0 ? (double) assignedCount / capacity * 100 : 0);

                            venueList.add(venueInfo);
                        }
                    }

                    venueData.put(regionName, venueList);
                }
            }

            return ResponseEntity.ok(ApiResponse.success("Venues with capacity retrieved successfully", venueData));

        } catch (Exception e) {
            log.error("Failed to get venues with capacity: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve venue data: " + e.getMessage()));
        }
    }

    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getBmmFilterOptions() {
        log.info("Fetching BMM filter options");

        try {
            // Get current BMM event and use EventMember data
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                // Return empty options if no BMM event
                Map<String, Object> emptyOptions = new HashMap<>();
                emptyOptions.put("regions", new ArrayList<>());
                emptyOptions.put("industries", new ArrayList<>());
                emptyOptions.put("subIndustries", new ArrayList<>());
                emptyOptions.put("workplaces", new ArrayList<>());
                emptyOptions.put("employers", new ArrayList<>());

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", emptyOptions);
                return ResponseEntity.ok(response);
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            log.info("Found {} total members for BMM filter options", allMembers.size());

            // Extract unique filter options from EventMember data
            Set<String> regions = allMembers.stream()
                    .map(EventMember::getRegionDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            // Get industries directly from EventMember fields
            Set<String> industries = allMembers.stream()
                    .map(EventMember::getSiteIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> subIndustries = allMembers.stream()
                    .map(EventMember::getSiteSubIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> workplaces = allMembers.stream()
                    .map(EventMember::getWorkplace)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> employers = allMembers.stream()
                    .map(EventMember::getEmployer)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            // Convert to sorted lists
            List<String> sortedRegions = regions.stream().sorted().collect(Collectors.toList());
            List<String> sortedIndustries = industries.stream().sorted().collect(Collectors.toList());
            List<String> sortedSubIndustries = subIndustries.stream().sorted().collect(Collectors.toList());
            List<String> sortedWorkplaces = workplaces.stream().sorted().collect(Collectors.toList());
            List<String> sortedEmployers = employers.stream().sorted().collect(Collectors.toList());

            Map<String, Object> filterOptions = new HashMap<>();
            filterOptions.put("regions", sortedRegions);
            filterOptions.put("industries", sortedIndustries);
            filterOptions.put("subIndustries", sortedSubIndustries);
            filterOptions.put("workplaces", sortedWorkplaces);
            filterOptions.put("employers", sortedEmployers);

            log.info("Filter options generated: {} regions, {} industries, {} sub-industries, {} workplaces, {} employers",
                    sortedRegions.size(), sortedIndustries.size(), sortedSubIndustries.size(), sortedWorkplaces.size(), sortedEmployers.size());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", filterOptions);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get BMM filter options: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to retrieve filter options: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Auto-assign venues based on preferences and availability
    @PostMapping("/auto-assign-venues")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoAssignVenues(@RequestBody Map<String, Object> request) {
        log.info("Auto-assigning venues for BMM members");

        try {
            String region = (String) request.get("region");

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM event found"));
            }

            // Get members awaiting venue assignment
            List<EventMember> membersToAssign = eventMemberRepository.findByEvent(currentBmmEvent).stream()
                    .filter(m -> "PREFERENCE_SUBMITTED".equals(m.getBmmRegistrationStage()))
                    .filter(m -> region == null || region.equals(m.getRegionDesc()))
                    .collect(Collectors.toList());

            List<Map<String, Object>> assignments = new ArrayList<>();

            // Simple assignment logic - assign to first available venue that matches preferences
            for (EventMember member : membersToAssign) {
                try {
                    String preferredVenuesJson = member.getPreferredVenuesJson();
                    if (preferredVenuesJson != null) {
                        ObjectMapper mapper = new ObjectMapper();
                        List<String> preferredVenues = mapper.readValue(preferredVenuesJson, List.class);

                        for (String venueName : preferredVenues) {
                            // Assign to first preferred venue (simplified logic)
                            member.setAssignedVenueFinal(venueName);
                            // 不设置默认时间，保持为null直到管理员分配
                            // member.setAssignedDatetimeFinal(LocalDateTime.of(2025, 3, 15, 10, 0));
                            member.setBmmRegistrationStage("VENUE_ASSIGNED");
                            eventMemberRepository.save(member);

                            Map<String, Object> assignment = new HashMap<>();
                            assignment.put("memberId", member.getId());
                            assignment.put("venueName", venueName);
                            assignment.put("datetime", null); // 不设置默认时间
                            assignments.add(assignment);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to auto-assign venue for member {}: {}", member.getId(), e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("assignments", assignments);
            result.put("totalAssigned", assignments.size());

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Failed to auto-assign venues: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Auto-assignment failed: " + e.getMessage()));
        }
    }

    // Manual venue assignment for individual member
    @PostMapping("/manual-assign-venue")
    public ResponseEntity<ApiResponse<String>> manualAssignVenue(@RequestBody Map<String, Object> request) {
        log.info("Manual venue assignment request");

        try {
            Long memberId = Long.valueOf(request.get("memberId").toString());
            String venueName = (String) request.get("venueName");
            String datetime = (String) request.get("datetime");

            Optional<EventMember> memberOpt = eventMemberRepository.findById(memberId);
            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();
            member.setAssignedVenueFinal(venueName);
            member.setAssignedDatetimeFinal(LocalDateTime.parse(datetime));
            member.setBmmRegistrationStage("VENUE_ASSIGNED");
            eventMemberRepository.save(member);

            log.info("Successfully assigned member {} to venue {} at {}", memberId, venueName, datetime);
            return ResponseEntity.ok(ApiResponse.success("Member assigned successfully"));

        } catch (Exception e) {
            log.error("Failed to manually assign venue: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Manual assignment failed: " + e.getMessage()));
        }
    }

    // Bulk venue assignment
    @PostMapping("/bulk-assign-venues")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkAssignVenues(@RequestBody Map<String, Object> request) {
        log.info("Bulk venue assignment request");

        try {
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) request.get("assignments");
            int successCount = 0;
            int failCount = 0;

            for (Map<String, Object> assignment : assignments) {
                try {
                    Long memberId = Long.valueOf(assignment.get("memberId").toString());
                    String venueName = (String) assignment.get("venueName");
                    String datetime = (String) assignment.get("datetime");

                    Optional<EventMember> memberOpt = eventMemberRepository.findById(memberId);
                    if (memberOpt.isPresent()) {
                        EventMember member = memberOpt.get();
                        member.setAssignedVenueFinal(venueName);
                        member.setAssignedDatetimeFinal(LocalDateTime.parse(datetime));
                        member.setBmmRegistrationStage("VENUE_ASSIGNED");
                        eventMemberRepository.save(member);
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to assign venue for assignment: {}", assignment, e);
                    failCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalProcessed", assignments.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);

            log.info("Bulk venue assignment completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Failed to bulk assign venues: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Bulk assignment failed: " + e.getMessage()));
        }
    }

    // BMM Preference Statistics Endpoint
    @GetMapping("/preference-statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPreferenceStatistics() {
        log.info("Fetching BMM preference statistics");

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Filter members who have submitted preferences
            List<EventMember> membersWithPreferences = allMembers.stream()
                    .filter(m -> m.getPreferenceSubmittedAt() != null ||
                            "PREFERENCE_SUBMITTED".equals(m.getBmmStage()) ||
                            "VENUE_ASSIGNED".equals(m.getBmmStage()))
                    .collect(Collectors.toList());

            Map<String, Object> stats = new HashMap<>();

            // Total responses
            stats.put("totalResponses", membersWithPreferences.size());

            // Attendance intention stats
            long attendingYes = membersWithPreferences.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getPreferredAttending()))
                    .count();
            long attendingNo = membersWithPreferences.stream()
                    .filter(m -> Boolean.FALSE.equals(m.getPreferredAttending()))
                    .count();

            stats.put("attendingYes", attendingYes);
            stats.put("attendingNo", attendingNo);

            // Special vote stats (Central and Southern regions only)
            List<EventMember> eligibleForSpecialVote = membersWithPreferences.stream()
                    .filter(m -> "Central Region".equals(m.getRegionDesc()) ||
                            "Southern Region".equals(m.getRegionDesc()) ||
                            "Central".equals(m.getRegionDesc()) ||
                            "Southern".equals(m.getRegionDesc()))
                    .collect(Collectors.toList());

            long specialVoteYes = eligibleForSpecialVote.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getPreferenceSpecialVote()))
                    .count();
            long specialVoteNo = eligibleForSpecialVote.stream()
                    .filter(m -> Boolean.FALSE.equals(m.getPreferenceSpecialVote()))
                    .count();
            long specialVoteNotSure = eligibleForSpecialVote.stream()
                    .filter(m -> m.getPreferenceSpecialVote() == null)
                    .count();

            stats.put("specialVoteYes", specialVoteYes);
            stats.put("specialVoteNo", specialVoteNo);
            stats.put("specialVoteNotSure", specialVoteNotSure);

            // Venue distribution
            Map<String, Integer> venueDistribution = new HashMap<>();
            for (EventMember member : membersWithPreferences) {
                if (member.getPreferredVenuesJson() != null) {
                    try {
                        List<String> venues = objectMapper.readValue(member.getPreferredVenuesJson(), List.class);
                        if (!venues.isEmpty()) {
                            String venue = venues.get(0); // Primary venue
                            venueDistribution.put(venue, venueDistribution.getOrDefault(venue, 0) + 1);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse venue JSON for member {}", member.getMembershipNumber());
                    }
                }
            }
            stats.put("venueDistribution", venueDistribution);

            // Time preferences
            Map<String, Integer> timePreferences = new HashMap<>();
            for (EventMember member : membersWithPreferences) {
                if (member.getPreferredTimesJson() != null) {
                    try {
                        List<String> times = objectMapper.readValue(member.getPreferredTimesJson(), List.class);
                        for (String time : times) {
                            timePreferences.put(time, timePreferences.getOrDefault(time, 0) + 1);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse times JSON for member {}", member.getMembershipNumber());
                    }
                }
            }
            stats.put("timePreferences", timePreferences);

            // Regional breakdown
            Map<String, Map<String, Object>> regionsBreakdown = new HashMap<>();
            for (String region : Arrays.asList("Northern Region", "Central Region", "Southern Region")) {
                Map<String, Object> regionStats = new HashMap<>();
                // Support both formats: "Central" and "Central Region"
                String shortRegion = region.replace(" Region", "");
                List<EventMember> regionMembers = membersWithPreferences.stream()
                        .filter(m -> region.equals(m.getRegionDesc()) || shortRegion.equals(m.getRegionDesc()))
                        .collect(Collectors.toList());

                regionStats.put("total", regionMembers.size());
                regionStats.put("attendingYes", regionMembers.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getPreferredAttending()))
                        .count());
                regionStats.put("attendingNo", regionMembers.stream()
                        .filter(m -> Boolean.FALSE.equals(m.getPreferredAttending()))
                        .count());

                // Special vote stats for Central and Southern
                if ("Central Region".equals(region) || "Southern Region".equals(region)) {
                    Map<String, Long> specialVoteStats = new HashMap<>();
                    specialVoteStats.put("yes", regionMembers.stream()
                            .filter(m -> Boolean.TRUE.equals(m.getPreferenceSpecialVote()))
                            .count());
                    specialVoteStats.put("no", regionMembers.stream()
                            .filter(m -> Boolean.FALSE.equals(m.getPreferenceSpecialVote()))
                            .count());
                    specialVoteStats.put("notSure", regionMembers.stream()
                            .filter(m -> m.getPreferenceSpecialVote() == null)
                            .count());
                    regionStats.put("specialVoteStats", specialVoteStats);
                }

                regionsBreakdown.put(region, regionStats);
            }
            stats.put("regionsBreakdown", regionsBreakdown);

            return ResponseEntity.ok(ApiResponse.success("Preference statistics retrieved successfully", stats));

        } catch (Exception e) {
            log.error("Failed to get preference statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get preference statistics: " + e.getMessage()));
        }
    }

    // Get individual member preferences with details
    @GetMapping("/member/{membershipNumber}/preferences/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberPreferences(@PathVariable String membershipNumber) {
        log.info("Fetching preferences for member: {}", membershipNumber);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            Optional<EventMember> memberOpt = eventMemberRepository
                    .findByMembershipNumberAndEvent(membershipNumber, currentBmmEvent);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();
            Map<String, Object> memberData = new HashMap<>();

            // Basic info
            memberData.put("id", member.getId());
            memberData.put("name", member.getName());
            memberData.put("membershipNumber", member.getMembershipNumber());
            memberData.put("primaryEmail", member.getPrimaryEmail());
            memberData.put("telephoneMobile", member.getTelephoneMobile());
            memberData.put("regionDesc", member.getRegionDesc());
            memberData.put("workplace", member.getWorkplace());
            memberData.put("employer", member.getEmployer());
            memberData.put("bmmStage", member.getBmmStage());

            // Preferences
            memberData.put("preferredAttending", member.getPreferredAttending());
            memberData.put("preferenceSpecialVote", member.getPreferenceSpecialVote());
            memberData.put("workplaceInfo", member.getWorkplaceInfo());
            memberData.put("additionalComments", member.getAdditionalComments());
            memberData.put("suggestedVenue", member.getSuggestedVenue());
            memberData.put("preferenceSubmittedAt", member.getPreferenceSubmittedAt());

            // Parse JSON preferences
            Map<String, Object> bmmPreferences = new HashMap<>();
            if (member.getPreferredVenuesJson() != null) {
                try {
                    bmmPreferences.put("preferredVenues", objectMapper.readValue(member.getPreferredVenuesJson(), List.class));
                } catch (Exception e) {
                    log.warn("Failed to parse venues JSON");
                }
            }
            if (member.getPreferredTimesJson() != null) {
                try {
                    bmmPreferences.put("preferredTimes", objectMapper.readValue(member.getPreferredTimesJson(), List.class));
                } catch (Exception e) {
                    log.warn("Failed to parse times JSON");
                }
            }
            bmmPreferences.put("workplaceInfo", member.getWorkplaceInfo());
            bmmPreferences.put("additionalComments", member.getAdditionalComments());
            bmmPreferences.put("suggestedVenue", member.getSuggestedVenue());
            bmmPreferences.put("preferenceSpecialVote", member.getPreferenceSpecialVote());

            memberData.put("bmmPreferences", bmmPreferences);

            return ResponseEntity.ok(ApiResponse.success("Member preferences retrieved successfully", memberData));

        } catch (Exception e) {
            log.error("Failed to get member preferences: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get member preferences: " + e.getMessage()));
        }
    }

    // Get paginated preferences list
    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPreferencesList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String attendance,
            @RequestParam(required = false) String timePreference,
            @RequestParam(required = false) String specialVote,
            @RequestParam(required = false) String contactFilter,
            @RequestParam(required = false) String search) {

        log.info("Fetching preferences list with filters - region: {}, attendance: {}, time: {}, specialVote: {}, contactFilter: {}, search: {}",
                region, attendance, timePreference, specialVote, contactFilter, search);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No BMM events found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Filter members with preferences
            List<EventMember> filteredMembers = allMembers.stream()
                    .filter(m -> m.getPreferenceSubmittedAt() != null ||
                            "PREFERENCE_SUBMITTED".equals(m.getBmmStage()) ||
                            "VENUE_ASSIGNED".equals(m.getBmmStage()))
                    .filter(m -> {
                        if (region == null || region.equals("all")) return true;
                        // Support both formats
                        String shortRegion = region.replace(" Region", "");
                        return region.equals(m.getRegionDesc()) || shortRegion.equals(m.getRegionDesc());
                    })
                    .filter(m -> {
                        if (attendance == null || attendance.equals("all")) return true;
                        if ("yes".equals(attendance)) return Boolean.TRUE.equals(m.getPreferredAttending());
                        if ("no".equals(attendance)) return Boolean.FALSE.equals(m.getPreferredAttending());
                        if ("undecided".equals(attendance)) return m.getPreferredAttending() == null;
                        return true;
                    })
                    .filter(m -> {
                        if (timePreference == null || timePreference.equals("all")) return true;
                        if (m.getPreferredTimesJson() != null) {
                            try {
                                List<String> times = objectMapper.readValue(m.getPreferredTimesJson(), List.class);
                                return times.contains(timePreference);
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        return false;
                    })
                    .filter(m -> {
                        if (specialVote == null || specialVote.equals("all")) return true;
                        // Only filter Central and Southern regions
                        if ("Central Region".equals(m.getRegionDesc()) || "Southern Region".equals(m.getRegionDesc()) ||
                                "Central".equals(m.getRegionDesc()) || "Southern".equals(m.getRegionDesc())) {
                            if ("yes".equals(specialVote)) return Boolean.TRUE.equals(m.getPreferenceSpecialVote());
                            if ("no".equals(specialVote)) return Boolean.FALSE.equals(m.getPreferenceSpecialVote());
                            if ("notSure".equals(specialVote)) return m.getPreferenceSpecialVote() == null;
                        }
                        return true;
                    })
                    .filter(m -> {
                        if (search == null || search.isEmpty()) return true;
                        String searchLower = search.toLowerCase();
                        return (m.getName() != null && m.getName().toLowerCase().contains(searchLower)) ||
                                (m.getMembershipNumber() != null && m.getMembershipNumber().toLowerCase().contains(searchLower)) ||
                                (m.getWorkplaceInfo() != null && m.getWorkplaceInfo().toLowerCase().contains(searchLower));
                    })
                    .filter(m -> {
                        if (contactFilter == null || contactFilter.equals("all")) return true;
                        boolean hasEmail = m.getPrimaryEmail() != null && !m.getPrimaryEmail().isEmpty();
                        boolean hasPhone = m.getTelephoneMobile() != null && !m.getTelephoneMobile().isEmpty();

                        if ("email_only".equals(contactFilter)) return hasEmail && !hasPhone;
                        if ("phone_only".equals(contactFilter)) return hasPhone && !hasEmail;
                        if ("both".equals(contactFilter)) return hasEmail && hasPhone;
                        return true;
                    })
                    .collect(Collectors.toList());

            // Convert to map format
            List<Map<String, Object>> preferencesList = filteredMembers.stream()
                    .map(member -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("id", member.getId());
                        data.put("name", member.getName());
                        data.put("membershipNumber", member.getMembershipNumber());
                        data.put("email", member.getPrimaryEmail());
                        data.put("mobile", member.getTelephoneMobile());
                        data.put("region", member.getRegionDesc());
                        data.put("preferredAttending", member.getPreferredAttending());
                        data.put("preferenceSpecialVote", member.getPreferenceSpecialVote());
                        data.put("workplaceInfo", member.getWorkplaceInfo());
                        data.put("suggestedVenue", member.getSuggestedVenue());
                        data.put("additionalComments", member.getAdditionalComments());
                        data.put("submittedAt", member.getPreferenceSubmittedAt());

                        // Parse venues
                        if (member.getPreferredVenuesJson() != null) {
                            try {
                                List<String> venues = objectMapper.readValue(member.getPreferredVenuesJson(), List.class);
                                data.put("preferredVenue", venues.isEmpty() ? null : venues.get(0));
                            } catch (Exception e) {
                                data.put("preferredVenue", null);
                            }
                        }

                        // Parse times
                        if (member.getPreferredTimesJson() != null) {
                            try {
                                List<String> times = objectMapper.readValue(member.getPreferredTimesJson(), List.class);
                                data.put("preferredTimes", times);
                            } catch (Exception e) {
                                data.put("preferredTimes", new ArrayList<>());
                            }
                        } else {
                            data.put("preferredTimes", new ArrayList<>());
                        }

                        return data;
                    })
                    .sorted((a, b) -> {
                        LocalDateTime aTime = (LocalDateTime) a.get("submittedAt");
                        LocalDateTime bTime = (LocalDateTime) b.get("submittedAt");
                        if (aTime == null && bTime == null) return 0;
                        if (aTime == null) return 1;
                        if (bTime == null) return -1;
                        return bTime.compareTo(aTime); // Most recent first
                    })
                    .collect(Collectors.toList());

            // Create page
            int start = page * size;
            int end = Math.min(start + size, preferencesList.size());
            List<Map<String, Object>> pageContent = preferencesList.subList(start, end);

            // Return as a map instead of Page object to avoid serialization issues
            Map<String, Object> result = new HashMap<>();
            result.put("content", pageContent);
            result.put("totalElements", preferencesList.size());
            result.put("totalPages", (int) Math.ceil((double) preferencesList.size() / size));
            result.put("size", size);
            result.put("number", page);

            return ResponseEntity.ok(ApiResponse.success("Preferences retrieved successfully", result));

        } catch (Exception e) {
            log.error("Failed to get preferences list: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get preferences list: " + e.getMessage()));
        }
    }
}