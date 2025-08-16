package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.OrganizerToken;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.OrganizerTokenRepository;
import nz.etu.voting.service.QRCodeService;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

//Enhanced BMM Multi-venue QR code check-in controller + Provides region-specific password-free QR scan links for admins across all 5 BMM regions
@Slf4j
@RestController
@RequestMapping("/api/venue/checkin")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class VenueCheckinController {

    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;
    private final OrganizerTokenRepository organizerTokenRepository;
    private final QRCodeService qrCodeService;
    private final org.springframework.web.client.RestTemplate restTemplate;

    @Value("${app.api.baseUrl:http://localhost:8080}")
    private String apiBaseUrl;

    private static final List<String> BMM_REGIONS = Arrays.asList(
            "Northern Region", "Central Region", "Southern Region"
    );

    // 支持所有具体的venue名称，不仅限于region
    private static final boolean ALLOW_ALL_VENUES = true;

    //    Generate password-free QR scan link for specific BMM venue/region
    @PostMapping("/generate-venue-link/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateVenueCheckinLink(
            @PathVariable Long eventId,
            @RequestBody Map<String, String> request) {
        try {
            String venueName = request.get("venueName");
            String adminName = request.get("adminName");
            String adminEmail = request.get("adminEmail");

            if (venueName == null || venueName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Venue name is required"));
            }

            // 允许所有venue名称，不仅限于region
            if (!ALLOW_ALL_VENUES && !BMM_REGIONS.contains(venueName)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid BMM region. Must be one of: " + String.join(", ", BMM_REGIONS)));
            }

            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            if (!event.getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This function is only available for BMM Voting events"));
            }

//            Generate unique admin token for this venue
            String adminToken = UUID.randomUUID().toString();
            String timestamp = String.valueOf(System.currentTimeMillis());

//            Generate direct checkin URL - 对venue名称进行URL编码
            String encodedVenueName = java.net.URLEncoder.encode(venueName, java.nio.charset.StandardCharsets.UTF_8);
            String checkinUrl = String.format("https://events.etu.nz/venue/bmm-checkin?token=%s&eventId=%d&venue=%s&timestamp=%s",
                    adminToken, eventId, encodedVenueName, timestamp);

//            Generate QR code data for venue access
            String qrCodeData = qrCodeService.generateVenueCheckinQRCode(eventId, venueName, adminToken);

//            CRITICAL: Save venue link to database
            OrganizerToken organizerToken = OrganizerToken.builder()
                    .event(event)
                    .organizerName(String.format("%s - %s", venueName, adminName))
                    .organizerEmail(adminEmail)
                    .token(adminToken)
                    .isActive(true)
                    .expiresAt(LocalDateTime.now().plusYears(100)) // Permanent validity (100 years)
                    .build();

            organizerTokenRepository.save(organizerToken);

//            Get venue statistics
            // 使用forumDesc统计具体venue的人数
            // 需要根据venueName找到对应的forumDesc
            String forumDesc = getForumDescForVenue(venueName);
            long totalMembersInVenue = 0;
            long checkedInInVenue = 0;

            if (forumDesc != null) {
                totalMembersInVenue = eventMemberRepository.countByEventAndForumDesc(event, forumDesc);
                checkedInInVenue = eventMemberRepository.countByEventAndForumDescAndCheckedInTrue(event, forumDesc);
            } else {
                // 如果没找到对应的forumDesc，尝试直接使用venueName作为forumDesc
                totalMembersInVenue = eventMemberRepository.countByEventAndForumDesc(event, venueName);
                checkedInInVenue = eventMemberRepository.countByEventAndForumDescAndCheckedInTrue(event, venueName);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("checkinUrl", checkinUrl);
            response.put("qrCodeData", qrCodeData);
            response.put("adminToken", adminToken);
            response.put("venueName", venueName);
            response.put("eventName", event.getName());
            response.put("adminName", adminName);
            response.put("adminEmail", adminEmail);
            response.put("generatedAt", organizerToken.getCreatedAt());
            response.put("expiresAt", organizerToken.getExpiresAt());
            response.put("venueStats", Map.of(
                    "totalMembers", totalMembersInVenue,
                    "checkedIn", checkedInInVenue,
                    "remaining", totalMembersInVenue - checkedInInVenue
            ));

            log.info("Generated BMM venue checkin link for {} region, Event: {}, Admin: {}",
                    venueName, event.getName(), adminName);

            // Auto-send checkin link to admin email
            if (adminEmail != null && !adminEmail.trim().isEmpty()) {
                sendCheckinLinkToAdmin(adminEmail, adminName, venueName, checkinUrl, qrCodeData, event);
            }

            return ResponseEntity.ok(ApiResponse.success("Venue checkin link generated successfully", response));

        } catch (Exception e) {
            log.error("Failed to generate venue checkin link: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to generate venue link: " + e.getMessage()));
        }
    }

    //    Get all saved venue links for an event
    @GetMapping("/saved-links/{eventId}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSavedVenueLinks(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            List<OrganizerToken> savedTokens = organizerTokenRepository.findByEventAndIsActiveTrue(event);

            List<Map<String, Object>> venueLinks = new ArrayList<>();

            for (OrganizerToken token : savedTokens) {
                // Extract venue name from organizerName (format: "Northern Region - AdminName")
                String venueName = token.getOrganizerName().split(" - ")[0];
                String adminName = token.getOrganizerName().contains(" - ") ?
                        token.getOrganizerName().split(" - ", 2)[1] : "Unknown";

                String checkinUrl = String.format("https://events.etu.nz/venue/bmm-checkin?token=%s&eventId=%d&venue=%s&timestamp=%s",
                        token.getToken(), eventId, venueName, System.currentTimeMillis());

                String qrCodeData = qrCodeService.generateVenueCheckinQRCode(eventId, venueName, token.getToken());

                Map<String, Object> linkData = new HashMap<>();
                linkData.put("checkinUrl", checkinUrl);
                linkData.put("qrCodeData", qrCodeData);
                linkData.put("adminToken", token.getToken());
                linkData.put("venueName", venueName);
                linkData.put("eventName", event.getName());
                linkData.put("adminName", adminName);
                linkData.put("adminEmail", token.getOrganizerEmail());
                linkData.put("generatedAt", token.getCreatedAt());
                linkData.put("expiresAt", token.getExpiresAt());
                linkData.put("isActive", token.getIsActive());

                venueLinks.add(linkData);
            }

            return ResponseEntity.ok(ApiResponse.success("Saved venue links retrieved successfully", venueLinks));

        } catch (Exception e) {
            log.error("Failed to get saved venue links: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve saved venue links: " + e.getMessage()));
        }
    }

    // Get all available forums for an event
    @GetMapping("/available-forums/{eventId}")
    public ResponseEntity<ApiResponse<List<String>>> getAvailableForums(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            // Get all forums from bmm-venues-config.json
            List<String> allForums = new ArrayList<>();

            try {
                // Load venues from configuration file
                java.io.InputStream inputStream = getClass().getResourceAsStream("/bmm-venues-config.json");
                if (inputStream != null) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> config = mapper.readValue(inputStream, Map.class);

                    // Get all venues from venues array
                    List<Map<String, Object>> venues = (List<Map<String, Object>>) config.get("venues");
                    if (venues != null) {
                        for (Map<String, Object> venue : venues) {
                            String forumDesc = (String) venue.get("forumDesc");
                            if (forumDesc != null && !allForums.contains(forumDesc)) {
                                allForums.add(forumDesc);
                            }
                        }
                    }

                    // Also add venue names from forumVenueMapping as independent forums
                    // This allows venues like Kaitaia to be selected directly
                    Map<String, List<Map<String, Object>>> forumMapping =
                            (Map<String, List<Map<String, Object>>>) config.get("forumVenueMapping");
                    if (forumMapping != null) {
                        for (Map.Entry<String, List<Map<String, Object>>> entry : forumMapping.entrySet()) {
                            List<Map<String, Object>> mappedVenues = entry.getValue();
                            if (mappedVenues != null) {
                                for (Map<String, Object> mappedVenue : mappedVenues) {
                                    String venueName = (String) mappedVenue.get("venueName");
                                    if (venueName != null && !allForums.contains(venueName)) {
                                        allForums.add(venueName); // Add Kaitaia, Hokitika, Reefton as independent forums
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load forums from config: {}", e.getMessage());
            }

            // Sort alphabetically
            Collections.sort(allForums);

            log.info("Found {} forums from config: {}", allForums.size(), allForums);

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Found %d forums", allForums.size()),
                    allForums
            ));

        } catch (Exception e) {
            log.error("Failed to get available forums: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get forums: " + e.getMessage()));
        }
    }

    //    Generate links for all forums/venues at once
    @PostMapping("/generate-all-bmm-links/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateAllBMMVenueLinks(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> request) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            if (!event.getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This function is only available for BMM Voting events"));
            }

            // Get all distinct forums from database
            List<String> allForums = eventMemberRepository.findDistinctForumDescByEvent(event);

            if (allForums.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No forums found for this event"));
            }

            Map<String, Object> allForumLinks = new HashMap<>();

            for (String forum : allForums) {
                String adminToken = UUID.randomUUID().toString();
                String timestamp = String.valueOf(System.currentTimeMillis());

                // Encode forum name for URL
                String encodedForumName = java.net.URLEncoder.encode(forum, java.nio.charset.StandardCharsets.UTF_8);
                String checkinUrl = String.format("https://events.etu.nz/venue/bmm-checkin?token=%s&eventId=%d&venue=%s&timestamp=%s",
                        adminToken, eventId, encodedForumName, timestamp);

                String qrCodeData = qrCodeService.generateVenueCheckinQRCode(eventId, forum, adminToken);

                // Use forumDesc for statistics
                long totalMembersInForum = eventMemberRepository.countByEventAndForumDesc(event, forum);
                long checkedInInForum = eventMemberRepository.countByEventAndForumDescAndCheckedInTrue(event, forum);

                Map<String, Object> forumData = new HashMap<>();
                forumData.put("checkinUrl", checkinUrl);
                forumData.put("qrCodeData", qrCodeData);
                forumData.put("adminToken", adminToken);
                forumData.put("stats", Map.of(
                        "totalMembers", totalMembersInForum,
                        "checkedIn", checkedInInForum,
                        "remaining", totalMembersInForum - checkedInInForum
                ));

                allForumLinks.put(forum, forumData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("eventName", event.getName());
            response.put("eventId", eventId);
            response.put("generatedAt", LocalDateTime.now().toString());
            response.put("forums", allForumLinks);
            response.put("totalForums", allForums.size());

            log.info("Generated BMM venue checkin links for all {} forums for event: {}", allForums.size(), event.getName());

            return ResponseEntity.ok(ApiResponse.success("All BMM venue links generated successfully", response));

        } catch (Exception e) {
            log.error("Failed to generate all BMM venue links: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to generate venue links: " + e.getMessage()));
        }
    }

    //    Get member information by QR scan for venue admin preview
    @PostMapping("/preview-member/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> previewMemberInfo(
            @PathVariable Long eventId,
            @RequestParam String adminToken,
            @RequestParam String venue,
            @RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String membershipNumber = request.get("membershipNumber");

            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            EventMember member = null;

//            Search by token first
            if (token != null && !token.trim().isEmpty()) {
                try {
                    UUID memberToken = UUID.fromString(token);
                    Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);
                    if (memberOpt.isPresent()) {
                        member = memberOpt.get();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid token format: {}", token);
                }
            }

//            If token search fails, search by membership number
            if (member == null && membershipNumber != null && !membershipNumber.trim().isEmpty()) {
                Optional<EventMember> memberOpt = eventMemberRepository.findByEventAndMembershipNumber(
                        eventOpt.get(), membershipNumber);
                if (memberOpt.isPresent()) {
                    member = memberOpt.get();
                }
            }

            if (member == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found with provided information"));
            }

            if (!member.getEvent().getId().equals(eventId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member does not belong to this event"));
            }

//            Build member information preview
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> memberInfo = buildDetailedMemberInfo(member);

            response.put("memberInfo", memberInfo);
            response.put("alreadyCheckedIn", member.getCheckedIn());
            response.put("currentVenue", venue);
            response.put("isAttending", member.getIsAttending());

            if (member.getCheckedIn()) {
                response.put("checkInTime", member.getCheckInTime());
                response.put("previousVenue", member.getCheckInLocation());
                response.put("status", "ALREADY_CHECKED_IN");
                response.put("message", String.format("Member already checked in at %s (%s)",
                        member.getCheckInLocation() != null ? member.getCheckInLocation() : "Unknown location",
                        member.getCheckInTime()));
            } else if (!member.getIsAttending()) {
                response.put("status", "NOT_ATTENDING");
                response.put("message", "Member has not confirmed attendance for this event");
            } else {
                response.put("status", "READY_TO_CHECKIN");
                response.put("message", "Member information confirmed, ready to check in");
            }

            return ResponseEntity.ok(ApiResponse.success("Member information retrieved", response));

        } catch (Exception e) {
            log.error("Failed to preview member info: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve member information"));
        }
    }

    //    Enhanced BMM venue admin QR scan check-in endpoint with region validation
    @PostMapping("/scan/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> venueCheckin(
            @PathVariable Long eventId,
            @RequestParam String adminToken,
            @RequestParam String venue,
            @RequestBody Map<String, String> request) {
        try {
            String qrData = request.get("qrData");
            String location = request.get("location");
            String adminName = request.get("adminName");      // 管理员姓名
            String adminEmail = request.get("adminEmail");    // 管理员邮箱

            if (qrData == null || qrData.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("QR data cannot be empty"));
            }

            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            if (!event.getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This function is only available for BMM Voting events"));
            }

            EventMember member = null;

//            Try to parse QR data and find member
            try {
//                Try JSON parsing first
                if (qrData.startsWith("{")) {
//                    Parse as JSON
                    String membershipNumber = extractFromJson(qrData, "membershipNumber");
                    String token = extractFromJson(qrData, "token");

                    if (token != null) {
                        try {
                            UUID memberToken = UUID.fromString(token);
                            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);
                            if (memberOpt.isPresent()) {
                                member = memberOpt.get();
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid token format in QR data: {}", token);
                        }
                    }

                    if (member == null && membershipNumber != null) {
                        Optional<EventMember> memberOpt2 = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber);
                        if (memberOpt2.isPresent()) {
                            member = memberOpt2.get();
                        }
                    }
                } else {
//                    Try as plain membership number or token
                    Optional<EventMember> memberOpt3 = eventMemberRepository.findByEventAndMembershipNumber(event, qrData.trim());
                    if (memberOpt3.isPresent()) {
                        member = memberOpt3.get();
                    }

                    if (member == null) {
                        try {
                            UUID memberToken = UUID.fromString(qrData.trim());
                            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);
                            if (memberOpt.isPresent() && memberOpt.get().getEvent().getId().equals(eventId)) {
                                member = memberOpt.get();
                            }
                        } catch (IllegalArgumentException e) {
//                            Not a valid UUID, ignore
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse QR data: {}", e.getMessage());
            }

            if (member == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found for this QR code"));
            }

//            Check if member is already checked in
            if (member.getCheckedIn()) {
                String previousVenue = member.getCheckInLocation();
                log.warn("Member {} already checked in at {} (scanning at {})",
                        member.getMembershipNumber(), previousVenue, venue);

                Map<String, Object> response = new HashMap<>();
                response.put("memberName", member.getName());
                response.put("membershipNumber", member.getMembershipNumber());
                response.put("previousCheckinTime", member.getCheckInTime());
                response.put("previousCheckinLocation", previousVenue);
                response.put("currentVenue", venue);
                response.put("alreadyCheckedIn", true);

                // Create custom warning response
                ApiResponse<Map<String, Object>> warningResponse = ApiResponse.<Map<String, Object>>builder()
                        .status("warning")
                        .message("Member already checked in at " + (previousVenue != null ? previousVenue : "unknown location"))
                        .data(response)
                        .timestamp(LocalDateTime.now())
                        .build();
                return ResponseEntity.ok(warningResponse);
            }

//            Perform check-in with admin tracking
            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            member.setCheckInLocation(venue);
            if (location != null && !location.trim().isEmpty()) {
                member.setCheckInLocation(location);
            }

            // CRITICAL: 新增：记录管理员信息
            if (adminName != null && !adminName.trim().isEmpty()) {
                // 使用registrationData JSON字段存储管理员信息
                try {
                    String adminInfo = String.format(
                            "{\"checkedInByAdmin\":\"%s\",\"adminEmail\":\"%s\",\"adminToken\":\"%s\",\"checkinVenue\":\"%s\",\"checkinTimestamp\":\"%s\"}",
                            adminName,
                            adminEmail != null ? adminEmail : "",
                            adminToken,
                            venue,
                            LocalDateTime.now().toString()
                    );

                    // 如果已有registrationData，则合并信息
                    String existingData = member.getRegistrationData();
                    if (existingData != null && !existingData.trim().isEmpty()) {
                        // 简单合并：在现有JSON最后添加管理员信息
                        if (existingData.endsWith("}")) {
                            existingData = existingData.substring(0, existingData.length() - 1) +
                                    ",\"adminCheckinInfo\":" + adminInfo + "}";
                        } else {
                            existingData = "{\"adminCheckinInfo\":" + adminInfo + "}";
                        }
                        member.setRegistrationData(existingData);
                    } else {
                        member.setRegistrationData("{\"adminCheckinInfo\":" + adminInfo + "}");
                    }

                    log.info("Admin checkin info recorded: {} scanned member {} at {} venue",
                            adminName, member.getMembershipNumber(), venue);
                } catch (Exception e) {
                    log.warn("Failed to record admin checkin info: {}", e.getMessage());
                }
            }

            eventMemberRepository.save(member);

            Map<String, Object> response = buildDetailedMemberInfo(member);
            response.put("venue", venue);
            response.put("checkinLocation", member.getCheckInLocation());
            response.put("scanMethod", "venue_scanner");
            response.put("adminName", adminName);        // 返回管理员信息供前端显示
            response.put("checkinTime", member.getCheckInTime());

            log.info("BMM venue checkin successful for member: {} at {} region by admin: {}",
                    member.getMembershipNumber(), venue, adminName != null ? adminName : "Unknown");

            return ResponseEntity.ok(ApiResponse.success("Member checked in successfully at " + venue, response));

        } catch (Exception e) {
            log.error("BMM venue checkin failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Venue checkin failed: " + e.getMessage()));
        }
    }

    //    Validate token endpoint
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(
            @RequestParam String token,
            @RequestParam Long eventId,
            @RequestParam String venue) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            if (!event.getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This function is only available for BMM Voting events"));
            }

            // 允许所有venue名称，不仅限于region
            if (!ALLOW_ALL_VENUES && !BMM_REGIONS.contains(venue)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid BMM region"));
            }

//            验证token是否存在于数据库中
            if (token == null || token.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token"));
            }

            Optional<OrganizerToken> organizerTokenOpt = organizerTokenRepository.findByTokenAndIsActiveTrue(token);
            if (!organizerTokenOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired token"));
            }

            OrganizerToken organizerToken = organizerTokenOpt.get();
            if (!organizerToken.getEvent().getId().equals(eventId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Token does not belong to this event"));
            }

//            Get venue statistics - using forumDesc instead of regionDesc
            long totalMembersInRegion = eventMemberRepository.countByEventAndForumDesc(event, venue);
            long checkedInInRegion = eventMemberRepository.countByEventAndForumDescAndCheckedInTrue(event, venue);

            Map<String, Object> response = new HashMap<>();
            response.put("event", Map.of(
                    "id", event.getId(),
                    "name", event.getName(),
                    "eventCode", event.getEventCode(),
                    "venue", event.getVenue()
            ));
            response.put("venue", venue);
            response.put("stats", Map.of(
                    "total", totalMembersInRegion,
                    "checkedIn", checkedInInRegion,
                    "remaining", totalMembersInRegion - checkedInInRegion
            ));

            return ResponseEntity.ok(ApiResponse.success("Token validated successfully", response));

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Token validation failed"));
        }
    }

    //    Helper method to extract values from JSON string
    private String extractFromJson(String jsonStr, String key) {
        try {
            int keyIndex = jsonStr.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return null;

            int colonIndex = jsonStr.indexOf(":", keyIndex);
            if (colonIndex == -1) return null;

            int startIndex = jsonStr.indexOf("\"", colonIndex) + 1;
            if (startIndex == 0) return null;

            int endIndex = jsonStr.indexOf("\"", startIndex);
            if (endIndex == -1) return null;

            return jsonStr.substring(startIndex, endIndex);
        } catch (Exception e) {
            return null;
        }
    }

    //    Get BMM check-in statistics for all regions
    @GetMapping("/bmm-stats/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMCheckinStats(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            Map<String, Object> stats = new HashMap<>();

//            Overall stats
            long totalMembers = eventMemberRepository.countByEvent(event);
            long totalAttending = eventMemberRepository.countByEventAndIsAttendingTrue(event);
            long totalCheckedIn = eventMemberRepository.countByEventAndCheckedInTrue(event);
            long totalSpecialVote = eventMemberRepository.countByEventAndIsSpecialVoteTrue(event);

            stats.put("overall", Map.of(
                    "totalMembers", totalMembers,
                    "attending", totalAttending,
                    "checkedIn", totalCheckedIn,
                    "specialVote", totalSpecialVote,
                    "checkinRate", totalAttending > 0 ? Math.round((double) totalCheckedIn / totalAttending * 100) : 0
            ));

//            Regional breakdown
            Map<String, Object> regionStats = new HashMap<>();
            for (String region : BMM_REGIONS) {
                long regionTotal = eventMemberRepository.countByEventAndRegionDesc(event, region);
                long regionAttending = eventMemberRepository.countByEventAndRegionDescAndIsAttendingTrue(event, region);
                long regionCheckedIn = eventMemberRepository.countByEventAndRegionDescAndCheckedInTrue(event, region);
                long regionSpecialVote = eventMemberRepository.countByEventAndRegionDescAndIsSpecialVoteTrue(event, region);

                regionStats.put(region, Map.of(
                        "total", regionTotal,
                        "attending", regionAttending,
                        "checkedIn", regionCheckedIn,
                        "specialVote", regionSpecialVote,
                        "remaining", regionAttending - regionCheckedIn,
                        "checkinRate", regionAttending > 0 ? Math.round((double) regionCheckedIn / regionAttending * 100) : 0
                ));
            }

            stats.put("regions", regionStats);
            stats.put("eventName", event.getName());
            stats.put("lastUpdated", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("BMM statistics retrieved successfully", stats));

        } catch (Exception e) {
            log.error("Failed to get BMM stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    //    Build detailed member information for display
    private Map<String, Object> buildDetailedMemberInfo(EventMember member) {
        Map<String, Object> memberInfo = new HashMap<>();
        memberInfo.put("id", member.getId());
        memberInfo.put("membershipNumber", member.getMembershipNumber());
        memberInfo.put("name", member.getName());
        memberInfo.put("primaryEmail", member.getPrimaryEmail());
        memberInfo.put("region", member.getRegionDesc());
        memberInfo.put("workplace", member.getWorkplace());
        memberInfo.put("employer", member.getEmployer());
        memberInfo.put("isAttending", member.getIsAttending());
        memberInfo.put("isSpecialVote", member.getIsSpecialVote());
        memberInfo.put("checkedIn", member.getCheckedIn());
        memberInfo.put("checkInTime", member.getCheckInTime());
        memberInfo.put("checkInLocation", member.getCheckInLocation());
        memberInfo.put("registrationStatus", member.getRegistrationStatus());

        return memberInfo;
    }

    // Helper method to get forumDesc for a given venue name
    private String getForumDescForVenue(String venueName) {
        try {
            // Load venues from configuration file
            java.io.InputStream inputStream = getClass().getResourceAsStream("/bmm-venues-config.json");
            if (inputStream != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> config = mapper.readValue(inputStream, Map.class);

                // Get all venues from venues array
                List<Map<String, Object>> venues = (List<Map<String, Object>>) config.get("venues");
                if (venues != null) {
                    for (Map<String, Object> venue : venues) {
                        String venueNameInConfig = (String) venue.get("venue");
                        if (venueNameInConfig != null && venueNameInConfig.equals(venueName)) {
                            return (String) venue.get("forumDesc");
                        }
                    }
                }

                // Check forumVenueMapping for special cases
                Map<String, List<Map<String, Object>>> forumVenueMapping =
                        (Map<String, List<Map<String, Object>>>) config.get("forumVenueMapping");
                if (forumVenueMapping != null) {
                    for (Map.Entry<String, List<Map<String, Object>>> entry : forumVenueMapping.entrySet()) {
                        List<Map<String, Object>> mappedVenues = entry.getValue();
                        for (Map<String, Object> mappedVenue : mappedVenues) {
                            String mappedVenueName = (String) mappedVenue.get("venueName");
                            if (mappedVenueName != null && mappedVenueName.equals(venueName)) {
                                // For special venues like Kaitaia, return the venueName as forumDesc
                                return venueName;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get forumDesc for venue {}: {}", venueName, e.getMessage());
        }
        return null;
    }

    // Send checkin link email to admin using send-advanced API
    private void sendCheckinLinkToAdmin(String adminEmail, String adminName, String venueName,
                                        String checkinUrl, String qrCodeData, Event event) {
        try {
            String subject = String.format("BMM Check-in Link for %s", venueName);

            String content = String.format(
                    "Dear %s,\n\n" +
                            "Your BMM check-in link for %s has been generated successfully.\n\n" +
                            "Check-in URL: %s\n\n" +
                            "This link provides passwordless access to the BMM check-in system for your venue. " +
                            "You can use this link on any device to check in attendees.\n\n" +
                            "Important Instructions:\n" +
                            "• Save this link securely - it provides admin access\n" +
                            "• The link never expires and can be reused\n" +
                            "• You can share this link with other check-in staff at your venue\n" +
                            "• Attendees can be checked in from any venue (not restricted by region)\n\n" +
                            "Event: %s\n" +
                            "Venue: %s\n\n" +
                            "If you have any questions, please contact the event administrator.\n\n" +
                            "E tū Events Team",
                    adminName != null ? adminName : "Admin",
                    venueName,
                    checkinUrl,
                    event.getName(),
                    venueName
            );

            // Use send-advanced API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("eventId", event.getId());
            requestBody.put("subject", subject);
            requestBody.put("content", content);
            requestBody.put("emailType", "VENUE_CHECKIN_LINK");
            requestBody.put("provider", "STRATUM");

            // Send to specific admin email
            Map<String, Object> criteria = new HashMap<>();
            criteria.put("customEmails", Arrays.asList(adminEmail));
            requestBody.put("criteria", criteria);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiBaseUrl + "/api/admin/email/send-advanced",
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Checkin link email sent successfully to admin: {} for venue: {}", adminEmail, venueName);
            } else {
                log.error("Failed to send checkin link email. Response: {}", response.getBody());
            }

        } catch (Exception e) {
            log.error("Error sending checkin link to admin {}: {}", adminEmail, e.getMessage(), e);
        }
    }
}