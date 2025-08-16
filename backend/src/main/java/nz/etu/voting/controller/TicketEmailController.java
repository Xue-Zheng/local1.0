package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.TicketEmailService;
import nz.etu.voting.service.QRCodeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.io.InputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

// Ticket Email Controller - Manages ticket email sending and statistics
@Slf4j
@RestController
@RequestMapping("/api/admin/ticket-emails")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://192.168.110.6:3000", "https://events.etu.nz"})
public class TicketEmailController {

    private final TicketEmailService ticketEmailService;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final QRCodeService qrCodeService;

    // Send ticket emails to all confirmed attendees for a specific event
    @PostMapping("/event/{eventId}/send-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTicketEmailsForEvent(
            @PathVariable Long eventId) {
        try {
            log.info("Sending ticket emails for event ID: {}", eventId);

            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            int sentCount = ticketEmailService.sendTicketEmailsToAttendingMembers(event);

            Map<String, Object> result = new HashMap<>();
            result.put("eventId", eventId);
            result.put("eventName", event.getName());
            result.put("emailsSent", sentCount);
            result.put("message", String.format("Successfully sent %d ticket emails", sentCount));

            return ResponseEntity.ok(ApiResponse.success("Ticket emails sent successfully", result));

        } catch (Exception e) {
            log.error("Failed to send ticket emails for event {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send ticket emails: " + e.getMessage()));
        }
    }

    // Send ticket email to a single member
    @PostMapping("/member/{eventMemberId}/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTicketEmailToMember(
            @PathVariable Long eventMemberId) {
        try {
            log.info("Sending ticket email to event member ID: {}", eventMemberId);

            EventMember eventMember = eventMemberRepository.findById(eventMemberId)
                    .orElseThrow(() -> new IllegalArgumentException("Event member not found"));

            if (!eventMember.getIsAttending()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Cannot send ticket to member who is not attending"));
            }

            if (eventMember.getPrimaryEmail() == null || eventMember.getPrimaryEmail().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.error("Member has no email address"));
            }

            ticketEmailService.sendTicketEmail(eventMember);

            Map<String, Object> result = new HashMap<>();
            result.put("eventMemberId", eventMemberId);
            result.put("membershipNumber", eventMember.getMembershipNumber());
            result.put("memberName", eventMember.getName());
            result.put("primaryEmail", eventMember.getPrimaryEmail());
            result.put("eventName", eventMember.getEvent().getName());
            result.put("message", "Ticket email sent successfully");

            return ResponseEntity.ok(ApiResponse.success("Ticket email sent successfully", result));

        } catch (Exception e) {
            log.error("Failed to send ticket email to member {}: {}", eventMemberId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to send ticket email: " + e.getMessage()));
        }
    }

    // Get ticket email sending statistics
    @GetMapping("/event/{eventId}/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTicketEmailStats(
            @PathVariable Long eventId) {
        try {
            log.info("Getting ticket email stats for event ID: {}", eventId);

            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            List<EventMember> attendingMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);
            long totalAttending = attendingMembers.size();

            long ticketEmailsSent = attendingMembers.stream()
                    .mapToLong(em -> em.getQrCodeEmailSent() ? 1L : 0L)
                    .sum();

            long pendingTicketEmails = attendingMembers.stream()
                    .filter(em -> !em.getQrCodeEmailSent() && em.getPrimaryEmail() != null && !em.getPrimaryEmail().isEmpty())
                    .count();

            long noEmailAddress = attendingMembers.stream()
                    .filter(em -> em.getPrimaryEmail() == null || em.getPrimaryEmail().isEmpty())
                    .count();

            Map<String, Object> stats = new HashMap<>();
            stats.put("eventId", eventId);
            stats.put("eventName", event.getName());
            stats.put("totalAttendingMembers", totalAttending);
            stats.put("ticketEmailsSent", ticketEmailsSent);
            stats.put("pendingTicketEmails", pendingTicketEmails);
            stats.put("membersWithoutEmail", noEmailAddress);
            stats.put("emailDeliveryRate", totalAttending > 0 ?
                    String.format("%.1f%%", (ticketEmailsSent * 100.0 / totalAttending)) : "0%");

            return ResponseEntity.ok(ApiResponse.success("Ticket email stats retrieved successfully", stats));

        } catch (Exception e) {
            log.error("Failed to get ticket email stats for event {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get stats: " + e.getMessage()));
        }
    }

    // Get list of members who need ticket emails
    @GetMapping("/event/{eventId}/pending")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPendingTicketEmails(
            @PathVariable Long eventId) {
        try {
            log.info("Getting pending ticket emails for event ID: {}", eventId);

            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            List<EventMember> attendingMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);
            List<Map<String, Object>> pendingMembers = attendingMembers.stream()
                    .filter(em -> !em.getQrCodeEmailSent() && em.getPrimaryEmail() != null && !em.getPrimaryEmail().isEmpty())
                    .map(em -> {
                        Map<String, Object> memberInfo = new HashMap<>();
                        memberInfo.put("id", em.getId());
                        memberInfo.put("membershipNumber", em.getMembershipNumber());
                        memberInfo.put("name", em.getName());
                        memberInfo.put("primaryEmail", em.getPrimaryEmail());
                        memberInfo.put("regionDesc", em.getRegionDesc());
                        return memberInfo;
                    })
                    .toList();

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Found %d members pending ticket emails", pendingMembers.size()),
                    pendingMembers));

        } catch (Exception e) {
            log.error("Failed to get pending ticket emails for event {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get pending emails: " + e.getMessage()));
        }
    }

    // Get ticket email statistics overview for all events
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTicketEmailOverview() {
        try {
            log.info("Getting ticket email overview for all events");

            List<Event> activeEvents = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc();
            List<Map<String, Object>> overview = activeEvents.stream()
                    .map(event -> {
                        List<EventMember> attendingMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);
                        long totalAttending = attendingMembers.size();
                        long ticketsSent = attendingMembers.stream()
                                .mapToLong(em -> Boolean.TRUE.equals(em.getQrCodeEmailSent()) ? 1L : 0L)
                                .sum();

                        Map<String, Object> eventStats = new HashMap<>();
                        eventStats.put("eventId", event.getId());
                        eventStats.put("eventName", event.getName());
                        eventStats.put("eventCode", event.getEventCode());
                        eventStats.put("eventType", event.getEventType());
                        eventStats.put("totalAttending", totalAttending);
                        eventStats.put("ticketsSent", ticketsSent);
                        eventStats.put("pending", totalAttending - ticketsSent);
                        eventStats.put("completionRate", totalAttending > 0 ?
                                String.format("%.1f%%", (ticketsSent * 100.0 / totalAttending)) : "0%");

                        return eventStats;
                    })
                    .toList();

            return ResponseEntity.ok(ApiResponse.success("Ticket email overview retrieved successfully", overview));

        } catch (Exception e) {
            log.error("Failed to get ticket email overview: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get overview: " + e.getMessage()));
        }
    }

    // NEW: BMM specific endpoints

    // Get BMM ticket by token (for the ticket URL in emails)
    @GetMapping("/bmm-ticket/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMTicket(@PathVariable String token) {
        try {
            log.info("Fetching BMM ticket for token: {}", token);
            UUID ticketToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByTicketToken(ticketToken);

            if (!memberOpt.isPresent()) {
                log.warn("No member found for ticket token: {}", token);
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid ticket token"));
            }

            EventMember member = memberOpt.get();
            log.info("Found member: {} with BMM stage: {}", member.getMembershipNumber(), member.getBmmRegistrationStage());

            // Check if member has valid BMM registration stage
            String stage = member.getBmmRegistrationStage();
            if (stage == null || (!"ATTENDANCE_CONFIRMED".equals(stage) && !"TICKET_ONLY".equals(stage))) {
                log.warn("Member {} has invalid BMM stage: {}", member.getMembershipNumber(), stage);
                return ResponseEntity.badRequest().body(ApiResponse.error("Ticket not available - invalid registration stage"));
            }

            Map<String, Object> ticketData = new HashMap<>();
            ticketData.put("name", member.getName());
            ticketData.put("membershipNumber", member.getMembershipNumber());
            ticketData.put("region", member.getAssignedRegion() != null ? member.getAssignedRegion() : member.getRegionDesc());
            // Use assignedVenueFinal if assignedVenue is null
            String venue = member.getAssignedVenue() != null ? member.getAssignedVenue() : member.getAssignedVenueFinal();

            // Safeguard: If venue is stored as JSON object, extract the venue name
            if (venue != null && venue.trim().startsWith("{")) {
                try {
                    // Parse JSON object and extract venue field
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> venueObj = objectMapper.readValue(venue, Map.class);

                    // Try to get venue name from different possible fields
                    Object venueName = venueObj.get("venue");
                    if (venueName == null) {
                        venueName = venueObj.get("name");
                    }

                    if (venueName != null) {
                        venue = venueName.toString();
                        log.warn("Extracted venue name from JSON object: {}", venue);
                    } else {
                        log.error("No venue or name field found in JSON object: {}", venueObj);
                        venue = "Venue to be confirmed";
                    }
                } catch (Exception e) {
                    log.error("Failed to parse venue JSON: {}", venue, e);
                    venue = "Venue to be confirmed";
                }
            }

            // If venue is still null, use forumDesc as fallback
            if (venue == null || venue.isEmpty()) {
                venue = member.getForumDesc();
                log.info("Using forumDesc as venue: {}", venue);
            }

            ticketData.put("assignedVenue", venue != null ? venue : "Venue to be confirmed");

            // Get venue details based on assigned data or forum mapping
            String venueAddress = null;
            String assignedDate = null;

            // First check if member has assignedVenueFinal and assignedDatetimeFinal
            if (member.getAssignedVenueFinal() != null && !member.getAssignedVenueFinal().isEmpty()) {
                venue = member.getAssignedVenueFinal();
                ticketData.put("assignedVenue", venue);
            }

            if (member.getAssignedDatetimeFinal() != null) {
                // Format the date properly
                assignedDate = formatDateForDisplay(member.getAssignedDatetimeFinal());
                ticketData.put("assignedDate", assignedDate);
            }

            // Get venue address from BMM venue config
            if (venue != null && !venue.equals("Venue to be confirmed")) {
                try {
                    // Load venue config from resources
                    ObjectMapper objectMapper = new ObjectMapper();
                    InputStream is = getClass().getClassLoader().getResourceAsStream("bmm-venues-config.json");
                    if (is != null) {
                        Map<String, Object> venuesConfig = objectMapper.readValue(is, Map.class);
                        List<Map<String, Object>> venues = (List<Map<String, Object>>) venuesConfig.get("venues");

                        // Find matching venue by forumDesc first, then by venue name
                        for (Map<String, Object> venueObj : venues) {
                            String venueForumDesc = (String) venueObj.get("forumDesc");
                            String venueName = (String) venueObj.get("venue");

                            // Try to match by forumDesc or venue name
                            if (venue.equals(venueForumDesc) || venue.equals(venueName) ||
                                    (member.getForumDesc() != null && member.getForumDesc().equals(venueForumDesc))) {

                                // Get full venue details
                                Object addressObj = venueObj.get("address");
                                if (addressObj != null) {
                                    venueAddress = addressObj.toString();
                                }

                                // Also get the proper venue name if we matched by forumDesc
                                if (venue.equals(venueForumDesc) && venueName != null) {
                                    venue = venueName;
                                    ticketData.put("assignedVenue", venueName);
                                }

                                // Only use date from config if we don't have assignedDatetimeFinal
                                if (assignedDate == null) {
                                    Object dateObj = venueObj.get("date");
                                    if (dateObj != null) {
                                        ticketData.put("assignedDate", dateObj.toString());
                                    }
                                }

                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to get venue address for venue: {}", venue, e);
                }
            }
            ticketData.put("venueAddress", venueAddress);

            // Use assignedDatetimeFinal if assignedDateTime is null
            Object dateTime = member.getAssignedDateTime() != null ? member.getAssignedDateTime() : member.getAssignedDatetimeFinal();
            ticketData.put("assignedDateTime", dateTime != null ? dateTime.toString() : "TBA");

            // Add session time based on member preferences
            String sessionTime = extractSessionTime(member);
            ticketData.put("assignedSession", sessionTime);

            // Add timeSpan information if we have venue details
            if (venue != null && !venue.equals("Venue to be confirmed")) {
                try {
                    String timeSpan = getTimeSpanForSession(sessionTime);
                    if (timeSpan != null) {
                        ticketData.put("timeSpan", timeSpan);
                    }
                } catch (Exception e) {
                    log.error("Failed to get timeSpan for session: {}", sessionTime, e);
                }
            }
            // Ensure ticket token exists
            if (member.getTicketToken() == null) {
                member.setTicketToken(UUID.randomUUID());
                member.setTicketStatus("GENERATED");
                member = eventMemberRepository.save(member);
                log.info("Generated ticket token for member {} on ticket access", member.getMembershipNumber());
            }
            ticketData.put("ticketToken", member.getTicketToken());
            ticketData.put("memberToken", member.getToken()); // Add member token for QR code scanning
            ticketData.put("eventName", member.getEvent().getName());
            ticketData.put("ticketStatus", member.getTicketStatus());
            ticketData.put("specialVoteEligible", member.getSpecialVoteEligible());
            ticketData.put("forumDesc", member.getForumDesc());

            // Add Stage 1 preferences if available
            ticketData.put("preferredTimesJson", member.getPreferredTimesJson());
            ticketData.put("workplaceInfo", member.getWorkplaceInfo());
            ticketData.put("additionalComments", member.getAdditionalComments());
            ticketData.put("suggestedVenue", member.getSuggestedVenue());
            ticketData.put("preferredAttending", member.getPreferredAttending());

            return ResponseEntity.ok(ApiResponse.success("BMM ticket retrieved successfully", ticketData));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token format"));
        } catch (Exception e) {
            log.error("Failed to get BMM ticket: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve ticket: " + e.getMessage()));
        }
    }

    // Generate QR code for BMM ticket
    @GetMapping("/bmm-ticket/{token}/qrcode")
    public ResponseEntity<byte[]> getBMMTicketQRCode(@PathVariable String token) {
        try {
            UUID ticketToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByTicketToken(ticketToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            EventMember member = memberOpt.get();

            if (!"ATTENDANCE_CONFIRMED".equals(member.getBmmRegistrationStage())) {
                return ResponseEntity.badRequest().build();
            }

            // Generate QR code data for BMM check-in
            String qrData = String.format(
                    "{\"type\":\"bmm_checkin\",\"membershipNumber\":\"%s\",\"name\":\"%s\",\"token\":\"%s\",\"eventId\":%d,\"venue\":\"%s\",\"datetime\":\"%s\"}",
                    member.getMembershipNumber(),
                    member.getName(),
                    member.getTicketToken(),
                    member.getEvent().getId(),
                    member.getAssignedVenue() != null ? member.getAssignedVenue() :
                            (member.getForumDesc() != null ? member.getForumDesc() : "TBA"),
                    member.getAssignedDateTime() != null ? member.getAssignedDateTime().toString() : "TBA"
            );

            byte[] qrCode = qrCodeService.generateQRCodeImage(qrData, 300, 300);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(qrCode);

        } catch (Exception e) {
            log.error("Failed to generate BMM QR code: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // BMM check-in via QR code scan
    @PostMapping("/bmm-checkin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bmmCheckin(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String membershipNumber = request.get("membershipNumber");

            if (token == null && membershipNumber == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Token or membership number required"));
            }

            EventMember member = null;

            // Try to find by ticket token first
            if (token != null) {
                try {
                    UUID ticketToken = UUID.fromString(token);
                    Optional<EventMember> memberOpt = eventMemberRepository.findByTicketToken(ticketToken);
                    if (memberOpt.isPresent()) {
                        member = memberOpt.get();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid ticket token format: {}", token);
                }
            }

            // If token search failed, try membership number
            if (member == null && membershipNumber != null) {
                List<EventMember> members = eventMemberRepository.findByMembershipNumber(membershipNumber);
                // Find the BMM member (with any valid registration stage)
                Optional<EventMember> bmmMember = members.stream()
                        .filter(m -> m.getBmmRegistrationStage() != null &&
                                ("ATTENDANCE_CONFIRMED".equals(m.getBmmRegistrationStage()) ||
                                        "TICKET_ONLY".equals(m.getBmmRegistrationStage())))
                        .findFirst();
                if (bmmMember.isPresent()) {
                    member = bmmMember.get();
                }
            }

            if (member == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found or not eligible for BMM check-in"));
            }

            // Perform check-in
            boolean alreadyCheckedIn = member.getCheckedIn();

            if (!alreadyCheckedIn) {
                member.setCheckedIn(true);
                member.setCheckInTime(java.time.LocalDateTime.now());
                member.setCheckInMethod("QR_SCAN");
                eventMemberRepository.save(member);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("alreadyCheckedIn", alreadyCheckedIn);
            result.put("membershipNumber", member.getMembershipNumber());
            result.put("name", member.getName());
            result.put("checkInTime", member.getCheckInTime());
            result.put("venue", member.getAssignedVenue());
            result.put("specialVoteEligible", member.getSpecialVoteEligible());

            String message = alreadyCheckedIn ? "Member already checked in for BMM" : "BMM check-in successful";

            return ResponseEntity.ok(ApiResponse.success(message, result));

        } catch (Exception e) {
            log.error("BMM check-in failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Check-in failed: " + e.getMessage()));
        }
    }

    // Get list of checked-in members for BMM
    @GetMapping("/bmm-checkin/list")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMCheckedInMembers() {
        try {
            log.info("Fetching BMM checked-in members list");

            // Find all EventMembers who are checked in and have BMM attendance confirmed
            List<EventMember> checkedInMembers = eventMemberRepository.findByCheckedInTrueAndBmmRegistrationStage("ATTENDANCE_CONFIRMED");

            List<Map<String, Object>> membersList = checkedInMembers.stream()
                    .map(member -> {
                        Map<String, Object> memberInfo = new HashMap<>();
                        memberInfo.put("id", member.getId());
                        memberInfo.put("membershipNumber", member.getMembershipNumber());
                        memberInfo.put("name", member.getName());
                        memberInfo.put("primaryEmail", member.getPrimaryEmail());
                        memberInfo.put("regionDesc", member.getRegionDesc());
                        memberInfo.put("assignedVenue", member.getAssignedVenue());
                        memberInfo.put("checkInTime", member.getCheckInTime());
                        memberInfo.put("specialVoteEligible", member.getSpecialVoteEligible());
                        memberInfo.put("ticketToken", member.getTicketToken());

                        // Format check-in time for display
                        if (member.getCheckInTime() != null) {
                            memberInfo.put("checkInTimeFormatted", member.getCheckInTime().toString());
                        }

                        return memberInfo;
                    })
                    .sorted((m1, m2) -> {
                        // Sort by check-in time, most recent first
                        java.time.LocalDateTime time1 = (java.time.LocalDateTime) ((Map<String, Object>) m1).get("checkInTime");
                        java.time.LocalDateTime time2 = (java.time.LocalDateTime) ((Map<String, Object>) m2).get("checkInTime");
                        if (time1 == null && time2 == null) return 0;
                        if (time1 == null) return 1;
                        if (time2 == null) return -1;
                        return time2.compareTo(time1);
                    })
                    .toList();

            // Get statistics
            long totalCheckedIn = membersList.size();
            long totalConfirmed = eventMemberRepository.countByBmmRegistrationStage("ATTENDANCE_CONFIRMED");

            Map<String, Object> result = new HashMap<>();
            result.put("checkedInMembers", membersList);
            result.put("totalCheckedIn", totalCheckedIn);
            result.put("totalConfirmed", totalConfirmed);
            result.put("checkInRate", totalConfirmed > 0 ?
                    String.format("%.1f%%", (totalCheckedIn * 100.0 / totalConfirmed)) : "0%");

            return ResponseEntity.ok(ApiResponse.success("BMM checked-in members retrieved successfully", result));

        } catch (Exception e) {
            log.error("Failed to get BMM checked-in members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get checked-in members: " + e.getMessage()));
        }
    }

    // Generate and send ticket for a member (even without Stage 1/2 registration)
    @PostMapping("/member/{eventMemberId}/generate-and-send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateAndSendTicket(
            @PathVariable Long eventMemberId) {
        try {
            log.info("Generating and sending ticket for event member ID: {}", eventMemberId);

            EventMember eventMember = eventMemberRepository.findById(eventMemberId)
                    .orElseThrow(() -> new IllegalArgumentException("Event member not found"));

            // Allow ticket generation even without email (for SMS export)
            boolean hasEmail = eventMember.getPrimaryEmail() != null && !eventMember.getPrimaryEmail().isEmpty();

            // Generate ticket token if not exists
            if (eventMember.getTicketToken() == null) {
                eventMember.setTicketToken(UUID.randomUUID());
            }

            // For bulk ticket sending, mark as TICKET_ONLY (no specific time/venue)
            // This ensures the same ticket is used regardless of when it's sent
            if (eventMember.getBmmRegistrationStage() == null ||
                    eventMember.getBmmRegistrationStage().isEmpty() ||
                    eventMember.getBmmRegistrationStage().equals("TICKET_ONLY")) {
                eventMember.setTicketStatus("TICKET_ONLY");
            } else {
                eventMember.setTicketStatus("GENERATED");
            }

            // Mark as attending if not already (for bulk ticket sending)
            if (!eventMember.getIsAttending()) {
                eventMember.setIsAttending(true);
            }

            // Ensure member can check in even without full registration
            if (eventMember.getBmmRegistrationStage() == null || eventMember.getBmmRegistrationStage().isEmpty()) {
                eventMember.setBmmRegistrationStage("TICKET_ONLY");
            }

            eventMember = eventMemberRepository.save(eventMember);

            // Send ticket email only if email exists
            if (hasEmail) {
                ticketEmailService.sendTicketEmail(eventMember);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("eventMemberId", eventMemberId);
            result.put("membershipNumber", eventMember.getMembershipNumber());
            result.put("memberName", eventMember.getName());
            result.put("primaryEmail", eventMember.getPrimaryEmail());
            result.put("hasMobile", eventMember.getTelephoneMobile() != null && !eventMember.getTelephoneMobile().isEmpty());
            result.put("mobile", eventMember.getTelephoneMobile());
            result.put("eventName", eventMember.getEvent().getName());
            result.put("ticketToken", eventMember.getTicketToken());
            result.put("ticketUrl", "https://events.etu.nz/ticket?token=" + eventMember.getTicketToken());

            String message = hasEmail ? "Ticket generated and email sent successfully" :
                    "Ticket generated successfully (no email - ready for SMS export)";
            result.put("message", message);

            return ResponseEntity.ok(ApiResponse.success(message, result));

        } catch (Exception e) {
            log.error("Failed to generate and send ticket for member {}: {}", eventMemberId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to generate and send ticket: " + e.getMessage()));
        }
    }

    // Extract session time based on member preferences and venue constraints
    private String extractSessionTime(EventMember eventMember) {
        String forumDesc = eventMember.getForumDesc();

        // First check if this is a forumVenueMapping member
        if (forumDesc != null && forumDesc.equals("Greymouth")) {
            // For these special forums, return special instructions
            return "Multiple venues and times available - see options below";
        }

        // Check single session venues
        if ("Gisborne".equals(forumDesc) || "Nelson".equals(forumDesc)) {
            return "12:30 PM";  // Only lunchtime available
        }

        // Check if member has preferred times JSON
        String preferredTimesJson = eventMember.getPreferredTimesJson();
        if (preferredTimesJson != null && !preferredTimesJson.isEmpty()) {
            try {
                // Check venue-specific constraints
                if ("Napier".equals(forumDesc)) {
                    // Napier only has morning (10:30) and afternoon (2:30), no lunchtime
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        return "2:30 PM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        // Lunchtime preference but Napier doesn't have it, default to morning
                        return "10:30 AM";
                    }
                    return "10:30 AM"; // Default for Napier
                } else if ("Auckland North Shore".equals(forumDesc) || "Auckland West".equals(forumDesc) ||
                        "Manukau 3".equals(forumDesc) || "Pukekohe".equals(forumDesc) || 
                        "Whangarei".equals(forumDesc) || "Christchurch 2".equals(forumDesc) ||
                        "Rotorua".equals(forumDesc) || "Hamilton 1".equals(forumDesc) ||
                        "Hamilton 2".equals(forumDesc) || "Tauranga".equals(forumDesc) ||
                        "Dunedin".equals(forumDesc) || "Invercargill".equals(forumDesc) ||
                        "New Plymouth".equals(forumDesc) || "Timaru".equals(forumDesc) ||
                        "Christchurch 1".equals(forumDesc) || "Wellington 1".equals(forumDesc) ||
                        "Palmerston North".equals(forumDesc) || "Wellington 2".equals(forumDesc)) {
                    // Venues with only morning + lunchtime (no afternoon)
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        return "12:30 PM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        // Afternoon preference but not available, default to lunchtime
                        return "12:30 PM";
                    }
                    return "10:30 AM"; // Default for 2-session venues
                } else {
                    // Three-session venues (Manukau 1, Manukau 2, Auckland Central)
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        return "12:30 PM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        return "2:30 PM";
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing preferred times JSON: {}", e.getMessage());
            }
        }

        // Default based on venue type
        if ("Napier".equals(forumDesc)) {
            return "10:30 AM";  // Default to morning for Napier
        } else if ("Auckland North Shore".equals(forumDesc) || "Auckland West".equals(forumDesc) ||
                "Manukau 3".equals(forumDesc) || "Pukekohe".equals(forumDesc) || 
                "Whangarei".equals(forumDesc) || "Christchurch 2".equals(forumDesc) ||
                "Rotorua".equals(forumDesc) || "Hamilton 1".equals(forumDesc) ||
                "Hamilton 2".equals(forumDesc) || "Tauranga".equals(forumDesc) ||
                "Dunedin".equals(forumDesc) || "Invercargill".equals(forumDesc) ||
                "New Plymouth".equals(forumDesc) || "Timaru".equals(forumDesc) ||
                "Christchurch 1".equals(forumDesc) || "Wellington 1".equals(forumDesc) ||
                "Palmerston North".equals(forumDesc) || "Wellington 2".equals(forumDesc)) {
            return "10:30 AM";  // Default to morning for 2-session venues
        }

        // Default for three-session venues
        return "10:30 AM";  // Default to morning
    }

    // Get timeSpan for a given session time
    private String getTimeSpanForSession(String sessionTime) {
        if (sessionTime == null) return null;

        if (sessionTime.contains("10:30 AM")) {
            return "10:00 AM - 12:00 PM";
        } else if (sessionTime.contains("12:30 PM")) {
            return "12:00 PM - 2:00 PM";
        } else if (sessionTime.contains("2:30 PM")) {
            return "2:00 PM - 4:00 PM";
        }

        return null;
    }

    // Format date for display (e.g., "Monday 1 September")
    private String formatDateForDisplay(java.time.LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"));
    }
}