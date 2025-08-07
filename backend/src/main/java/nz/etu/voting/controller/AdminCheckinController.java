package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Comparator;

@Slf4j
@RestController
@RequestMapping("/api/admin/checkin")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})

public class AdminCheckinController {

    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCheckinStats() {
        log.info("Fetching checkin statistics");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> stats = new HashMap<>();

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent != null) {
                List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);

                long totalRegistered = eventMembers.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count();
                long totalCheckedIn = eventMembers.stream().filter(m -> m.getCheckedIn() != null && m.getCheckedIn()).count();
                long checkinRate = totalRegistered > 0 ? (totalCheckedIn * 100 / totalRegistered) : 0;

                stats.put("totalRegistered", totalRegistered);
                stats.put("totalCheckedIn", totalCheckedIn);
                stats.put("checkinRate", checkinRate);
                stats.put("currentBmmEvent", currentBmmEvent.getName());
            } else {
                stats.put("totalRegistered", 0);
                stats.put("totalCheckedIn", 0);
                stats.put("checkinRate", 0);
                stats.put("currentBmmEvent", "No BMM event found");
            }

            List<Event> allEvents = eventRepository.findAll();
            stats.put("totalEvents", allEvents.size());
            stats.put("activeEvents", allEvents.stream().filter(e -> e.getIsActive() != null && e.getIsActive()).count());

            // Event stats breakdown
            Map<String, Object> eventStats = new HashMap<>();
            for (Event event : allEvents) {
                if (event.getIsActive() != null && event.getIsActive()) {
                    Map<String, Object> eventInfo = new HashMap<>();
                    eventInfo.put("eventName", event.getName());
                    eventInfo.put("venue", event.getVenue());
                    eventInfo.put("eventDate", event.getEventDate());
                    eventInfo.put("qrScanEnabled", event.getQrScanEnabled());
                    eventStats.put(event.getEventCode(), eventInfo);
                }
            }
            stats.put("eventBreakdown", eventStats);

            response.put("status", "success");
            response.put("data", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch checkin statistics", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get check-in statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/qr/generate/{eventId}")
    public ResponseEntity<Map<String, Object>> generateCheckinQR(@PathVariable Long eventId) {
        log.info("Generating checkin QR code for event: {}", eventId);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "event not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate OrganizerScanToken
            String scanToken = UUID.randomUUID().toString();
            event.setOrganizerScanToken(scanToken);
            event.setQrScanEnabled(true);
            event.setScanLinkExpiresAt(LocalDateTime.now().plusHours(24)); // 24 hours validity

            eventRepository.save(event);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            String qrCodeUrl = String.format("https://events.etu.nz/admin/checkin/scan?token=%s&eventId=%d",
                    scanToken, eventId);

            data.put("qrCodeUrl", qrCodeUrl);
            data.put("scanToken", scanToken);
            data.put("eventName", event.getName());
            data.put("venue", event.getVenue());
            data.put("expiresAt", event.getScanLinkExpiresAt());

            response.put("status", "success");
            response.put("message", "QR code generated successfully");
            response.put("data", data);

            log.info("QR code generated for event: {} with token: {}", event.getName(), scanToken);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate checkin QR code", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "QR code generated failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Manual checkin
    @PostMapping("/manual")
    public ResponseEntity<Map<String, Object>> manualCheckin(@RequestBody Map<String, Object> request) {
        log.info("Manual checkin requested");

        try {
            String membershipNumber = (String) request.get("membershipNumber");
            String token = (String) request.get("token");
            Long eventId = Long.valueOf(request.get("eventId").toString());
            String venue = (String) request.get("venue");
            String location = (String) request.get("location");

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "membershipNumber is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Verify event
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "event not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Search EventMember
            EventMember member = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber).orElse(null);

            if (member == null) {
                // Try by token search
                if (token != null && !token.trim().isEmpty()) {
                    try {
                        UUID tokenUuid = UUID.fromString(token);
                        member = eventMemberRepository.findByToken(tokenUuid).orElse(null);
                        // Verify the member belongs to the event
                        if (member != null && !member.getEvent().getId().equals(eventId)) {
                            member = null;
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid token format: {}", token);
                    }
                }
            }

            if (member == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Member not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if already checked in
            if (member.getCheckedIn() != null && member.getCheckedIn()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "warning");
                response.put("message", "Member already checked in");

                Map<String, Object> data = new HashMap<>();
                data.put("membershipNumber", member.getMembershipNumber());
                data.put("name", member.getName());
                data.put("primaryEmail", member.getPrimaryEmail());
                data.put("previousCheckinTime", member.getCheckInTime());
                data.put("eventName", event.getName());
                data.put("alreadyCheckedIn", true);
                response.put("data", data);

                return ResponseEntity.ok(response);
            }

            // Perform checkin
            member.setIsAttending(true);
            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            member.setCheckInLocation(location != null ? location : "Manual Check-in");
            member.setCheckInMethod("MANUAL");

            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("membershipNumber", member.getMembershipNumber());
            data.put("name", member.getName());
            data.put("primaryEmail", member.getPrimaryEmail());
            data.put("checkinTime", member.getCheckInTime());
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("message", "checkin successful");
            response.put("data", data);

            log.info("Manual checkin successful for member: {}", member.getMembershipNumber());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Manual checkin failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "manual checkin failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Bulk checkin
    @PostMapping("/bulk")
    public ResponseEntity<Map<String, Object>> bulkCheckin(@RequestBody Map<String, Object> request) {
        log.info("Bulk checkin requested");

        try {
            @SuppressWarnings("unchecked")
            List<String> membershipNumbers = (List<String>) request.get("membershipNumbers");
            Long eventId = Long.valueOf(request.get("eventId").toString());

            if (membershipNumbers == null || membershipNumbers.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Member number list cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Verify event
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "event not found");
                return ResponseEntity.badRequest().body(response);
            }

            int successCount = 0;
            int failCount = 0;

            for (String membershipNumber : membershipNumbers) {
                try {
                    EventMember member = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber.trim()).orElse(null);
                    if (member != null) {
                        member.setIsAttending(true);
                        member.setCheckedIn(true);
                        member.setCheckInTime(LocalDateTime.now());
                        member.setCheckInLocation("Bulk Check-in");
                        member.setCheckInMethod("BULK");

                        eventMemberRepository.save(member);
                        successCount++;
                    } else {
                        failCount++;
                        log.warn("Member not found for bulk checkin: {}", membershipNumber);
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("Failed to checkin member: {}", membershipNumber, e);
                }
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("total", membershipNumbers.size());
            data.put("success", successCount);
            data.put("failed", failCount);
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("message", String.format("bulk checkin completed: success %d, failed %d ", successCount, failCount));
            response.put("data", data);

            log.info("Bulk checkin completed: {} success, {} failed", successCount, failCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Bulk checkin failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "bulk checkin failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Get checked-in members list
    @GetMapping("/attendees")
    public ResponseEntity<Map<String, Object>> getCheckedInMembers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching checked-in members list - page: {}, size: {}", page, size);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            List<EventMember> checkedInMembers;
            if (currentBmmEvent != null) {
                checkedInMembers = eventMemberRepository.findByEvent(currentBmmEvent).stream()
                        .filter(m -> m.getCheckedIn() != null && m.getCheckedIn())
                        .sorted((m1, m2) -> {
                            if (m1.getCheckInTime() == null && m2.getCheckInTime() == null) return 0;
                            if (m1.getCheckInTime() == null) return 1;
                            if (m2.getCheckInTime() == null) return -1;
                            return m2.getCheckInTime().compareTo(m1.getCheckInTime()); // newest first
                        })
                        .collect(Collectors.toList());
            } else {
                checkedInMembers = List.of();
            }

            // Paginate
            int start = page * size;
            int end = Math.min(start + size, checkedInMembers.size());
            List<EventMember> pageMembers = checkedInMembers.subList(start, end);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("members", pageMembers);
            data.put("totalCount", checkedInMembers.size());
            data.put("page", page);
            data.put("size", size);
            data.put("totalPages", (checkedInMembers.size() + size - 1) / size);
            data.put("currentBmmEvent", currentBmmEvent != null ? currentBmmEvent.getName() : "No BMM event");

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch checked-in members", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "get checkin member list failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Cancel checkin
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelCheckin(@RequestBody Map<String, Object> request) {
        log.info("Cancel checkin requested");

        try {
            String membershipNumber = (String) request.get("membershipNumber");
            Long eventId = request.get("eventId") != null ? Long.valueOf(request.get("eventId").toString()) : null;

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "membershipNumber can not be empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Find the member in the specific event or current BMM event
            EventMember member = null;
            if (eventId != null) {
                Event event = eventRepository.findById(eventId).orElse(null);
                if (event != null) {
                    member = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber).orElse(null);
                }
            } else {
                // Find in current BMM event
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                Event currentBmmEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);
                if (currentBmmEvent != null) {
                    member = eventMemberRepository.findByEventAndMembershipNumber(currentBmmEvent, membershipNumber).orElse(null);
                }
            }

            if (member == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "not found this member");
                return ResponseEntity.badRequest().body(response);
            }

            member.setCheckedIn(false);
            member.setCheckInTime(null);
            member.setCheckInLocation(null);
            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "cancel checkin successful");

            Map<String, Object> data = new HashMap<>();
            data.put("membershipNumber", member.getMembershipNumber());
            data.put("name", member.getName());
            response.put("data", data);

            log.info("Checkin cancelled for member: {}", member.getMembershipNumber());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Cancel checkin failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "cancel checkin failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // QR Code scanning endpoint for events
    @PostMapping("/events/{eventId}/checkin/qr")
    public ResponseEntity<Map<String, Object>> processQRScan(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> request) {
        log.info("QR scan checkin requested for event: {}", eventId);

        try {
            String qrData = (String) request.get("qrData");
            String location = (String) request.get("location");
            String scanMethod = (String) request.get("scanMethod");

            if (qrData == null || qrData.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "QR data cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            // Find the event
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            EventMember member = null;

            // Try to parse QR data as JSON
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedData = (Map<String, Object>) request.get("parsedData");

                if (parsedData != null) {
                    // Look for member by token first
                    String token = (String) parsedData.get("token");
                    if (token != null) {
                        try {
                            UUID memberToken = UUID.fromString(token);
                            member = eventMemberRepository.findByToken(memberToken).orElse(null);
                            // Verify the member belongs to the event
                            if (member != null && !member.getEvent().getId().equals(eventId)) {
                                member = null;
                            }
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid token format in QR data: {}", token);
                        }
                    }

                    // If not found by token, try membership number
                    if (member == null) {
                        String membershipNumber = (String) parsedData.get("membershipNumber");
                        if (membershipNumber != null) {
                            member = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber).orElse(null);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse QR data as JSON, trying as plain text: {}", e.getMessage());
            }

            // If still not found, try the raw QR data as membership number or token
            if (member == null) {
                // Try as membership number
                member = eventMemberRepository.findByEventAndMembershipNumber(event, qrData.trim()).orElse(null);

                // Try as token
                if (member == null) {
                    try {
                        UUID memberToken = UUID.fromString(qrData.trim());
                        member = eventMemberRepository.findByToken(memberToken).orElse(null);
                        // Verify the member belongs to the event
                        if (member != null && !member.getEvent().getId().equals(eventId)) {
                            member = null;
                        }
                    } catch (IllegalArgumentException e) {
                        // Not a valid UUID, ignore
                    }
                }
            }

            if (member == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Member not found for this QR code");
                return ResponseEntity.badRequest().body(response);
            }

            // Check if member is already checked in
            if (member.getCheckedIn() != null && member.getCheckedIn()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "warning");
                response.put("message", "Member already checked in");

                Map<String, Object> data = new HashMap<>();
                data.put("memberName", member.getName());
                data.put("membershipNumber", member.getMembershipNumber());
                data.put("previousCheckinTime", member.getCheckInTime());
                data.put("previousCheckinLocation", member.getCheckInLocation());
                data.put("alreadyCheckedIn", true);
                response.put("data", data);

                return ResponseEntity.ok(response);
            }

            // Perform check-in
            member.setIsAttending(true);
            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            member.setCheckInMethod("QR_SCAN");

            // Save scan location
            if (location != null && !location.trim().isEmpty()) {
                member.setCheckInLocation(location);
            }

            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Member checked in successfully via QR scan");

            Map<String, Object> data = new HashMap<>();
            data.put("memberName", member.getName());
            data.put("membershipNumber", member.getMembershipNumber());
            data.put("primaryEmail", member.getPrimaryEmail());
            data.put("checkinTime", member.getCheckInTime());
            data.put("checkinLocation", member.getCheckInLocation());
            data.put("eventName", event.getName());
            data.put("scanMethod", scanMethod);
            response.put("data", data);

            log.info("QR scan checkin successful for member: {} in event: {}",
                    member.getMembershipNumber(), event.getName());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("QR scan checkin failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "QR scan checkin failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Get incomplete registrations
    @GetMapping("/events/{eventId}/incomplete-registrations")
    public ResponseEntity<Map<String, Object>> getIncompleteRegistrations(@PathVariable Long eventId) {
        log.info("Fetching incomplete registrations for event: {}", eventId);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Find EventMembers who registered but haven't made clear attendance choice
            List<EventMember> incompleteMembers = eventMemberRepository.findByEvent(event).stream()
                    .filter(m -> m.getHasRegistered() != null && m.getHasRegistered()) // Already registered
                    .filter(m -> m.getIsAttending() == null ||
                            (m.getIsAttending() != null && !m.getIsAttending() &&
                                    (m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty()))) // No attendance choice or no absence reason
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            List<Map<String, Object>> memberList = incompleteMembers.stream().map(member -> {
                Map<String, Object> memberInfo = new HashMap<>();
                memberInfo.put("membershipNumber", member.getMembershipNumber());
                memberInfo.put("name", member.getName());
                memberInfo.put("primaryEmail", member.getPrimaryEmail());
                memberInfo.put("registrationTime", member.getCreatedAt());
                memberInfo.put("hasRegistered", member.getHasRegistered());
                memberInfo.put("isAttending", member.getIsAttending());
                memberInfo.put("absenceReason", member.getAbsenceReason());
                memberInfo.put("isSpecialVote", member.getIsSpecialVote());
                return memberInfo;
            }).collect(Collectors.toList());

            data.put("incompleteMembers", memberList);
            data.put("totalCount", memberList.size());
            data.put("eventName", event.getName());
            data.put("description", "Members who registered but may not have completed the attendance choice");

            response.put("status", "success");
            response.put("data", data);

            log.info("Found {} members with potentially incomplete registrations for event: {}",
                    memberList.size(), event.getName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch incomplete registrations", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to fetch incomplete registrations: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}