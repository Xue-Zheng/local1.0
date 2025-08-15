package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Comparator;

// Admin reports controller for generating various statistical reports and data analysis
@Slf4j
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})

public class AdminReportsController {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    //    Get member overview report
    @GetMapping("/members/overview")
    public ResponseEntity<Map<String, Object>> getMemberOverviewReport() {
        log.info("Generating member overview report");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent != null) {
                List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);

                // Basic statistics
                data.put("totalMembers", eventMembers.size());
                data.put("registeredMembers", eventMembers.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count());
                data.put("attendingMembers", eventMembers.stream().filter(m -> m.getIsAttending() != null && m.getIsAttending()).count());
                data.put("checkedInMembers", eventMembers.stream().filter(m -> m.getCheckedIn() != null && m.getCheckedIn()).count());
                data.put("specialVoteMembers", eventMembers.stream().filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote()).count());

                // Contact method statistics
                data.put("hasEmail", eventMembers.stream().filter(m -> m.getHasEmail() != null && m.getHasEmail()).count());
                data.put("hasMobile", eventMembers.stream().filter(m -> m.getHasMobile() != null && m.getHasMobile()).count());
                data.put("bothContacts", eventMembers.stream().filter(m ->
                        (m.getHasEmail() != null && m.getHasEmail()) &&
                                (m.getHasMobile() != null && m.getHasMobile())).count());

                // Communication status statistics
                long emailSentSuccess = eventMembers.stream().filter(m -> m.getEmailSent() != null && m.getEmailSent()).count();
                long smsSentSuccess = eventMembers.stream().filter(m -> m.getSmsSent() != null && m.getSmsSent()).count();

                data.put("emailSentSuccess", emailSentSuccess);
                data.put("smsSentSuccess", smsSentSuccess);

                // Region breakdown
                Map<String, Long> regionStats = eventMembers.stream()
                        .filter(m -> m.getRegionDesc() != null && !m.getRegionDesc().trim().isEmpty())
                        .collect(Collectors.groupingBy(EventMember::getRegionDesc, Collectors.counting()));
                data.put("regionBreakdown", regionStats);

                // Workplace breakdown
                Map<String, Long> workplaceStats = eventMembers.stream()
                        .filter(m -> m.getWorkplace() != null && !m.getWorkplace().trim().isEmpty())
                        .collect(Collectors.groupingBy(EventMember::getWorkplace, Collectors.counting()));
                data.put("workplaceBreakdown", workplaceStats);

                data.put("currentBmmEvent", currentBmmEvent.getName());
            } else {
                // No BMM event found
                data.put("totalMembers", 0);
                data.put("registeredMembers", 0);
                data.put("attendingMembers", 0);
                data.put("checkedInMembers", 0);
                data.put("specialVoteMembers", 0);
                data.put("hasEmail", 0);
                data.put("hasMobile", 0);
                data.put("bothContacts", 0);
                data.put("emailSentSuccess", 0);
                data.put("smsSentSuccess", 0);
                data.put("regionBreakdown", Map.of());
                data.put("workplaceBreakdown", Map.of());
                data.put("currentBmmEvent", "No BMM event found");
            }

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate member overview report", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to generate member overview report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get event report
    @GetMapping("/events/overview")
    public ResponseEntity<Map<String, Object>> getEventOverviewReport() {
        log.info("Generating event overview report");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            List<Event> allEvents = eventRepository.findAll();

            // Event statistics
            data.put("totalEvents", allEvents.size());
            data.put("activeEvents", allEvents.stream().filter(e -> e.getIsActive() != null && e.getIsActive()).count());
            data.put("bmmEvents", allEvents.stream().filter(e -> e.getEventType() == Event.EventType.BMM_VOTING).count());

            // Get current BMM event registration statistics
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent != null) {
                List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);

                long totalRegistrations = eventMembers.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count();
                long totalAttending = eventMembers.stream().filter(m -> m.getIsAttending() != null && m.getIsAttending()).count();

                data.put("totalRegistrations", totalRegistrations);
                data.put("totalAttending", totalAttending);
                data.put("attendanceRate", totalRegistrations > 0 ? (totalAttending * 100.0 / totalRegistrations) : 0);
                data.put("currentBmmEvent", currentBmmEvent.getName());
            } else {
                data.put("totalRegistrations", 0);
                data.put("totalAttending", 0);
                data.put("attendanceRate", 0);
                data.put("currentBmmEvent", "No BMM event found");
            }

            // Venue breakdown
            Map<String, Long> venueStats = allEvents.stream()
                    .filter(e -> e.getVenue() != null && !e.getVenue().trim().isEmpty())
                    .collect(Collectors.groupingBy(Event::getVenue, Collectors.counting()));
            data.put("venueBreakdown", venueStats);

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate event overview report", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to generate event report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get data quality report
    @GetMapping("/data-quality")
    public ResponseEntity<Map<String, Object>> getDataQualityReport() {
        log.info("Generating data quality report");

        try {
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent != null) {
                List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);

                // Data completeness statistics
                long missingEmail = eventMembers.stream().filter(m ->
                        m.getPrimaryEmail() == null || m.getPrimaryEmail().trim().isEmpty() ||
                                m.getPrimaryEmail().contains("@temp-email.etu.nz")).count();
                long missingMobile = eventMembers.stream().filter(m ->
                        m.getTelephoneMobile() == null || m.getTelephoneMobile().trim().isEmpty()).count();
                long missingName = eventMembers.stream().filter(m ->
                        m.getName() == null || m.getName().trim().isEmpty()).count();
                long missingMembershipNumber = eventMembers.stream().filter(m ->
                        m.getMembershipNumber() == null || m.getMembershipNumber().trim().isEmpty()).count();
                long missingRegion = eventMembers.stream().filter(m ->
                        m.getRegionDesc() == null || m.getRegionDesc().trim().isEmpty()).count();

                data.put("missingEmail", missingEmail);
                data.put("missingMobile", missingMobile);
                data.put("missingName", missingName);
                data.put("missingMembershipNumber", missingMembershipNumber);
                data.put("missingRegion", missingRegion);

                // Data source statistics
                Map<String, Long> dataSourceStats = eventMembers.stream()
                        .filter(m -> m.getDataSource() != null && !m.getDataSource().trim().isEmpty())
                        .collect(Collectors.groupingBy(EventMember::getDataSource, Collectors.counting()));
                data.put("dataSourceBreakdown", dataSourceStats);

                // Registration completion statistics
                long incompleteRegistrations = eventMembers.stream().filter(m ->
                        m.getHasRegistered() != null && m.getHasRegistered() &&
                                (m.getIsAttending() == null ||
                                        (m.getIsAttending() != null && !m.getIsAttending() &&
                                                (m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty())))).count();

                data.put("incompleteRegistrations", incompleteRegistrations);
                data.put("totalMembers", eventMembers.size());
                data.put("currentBmmEvent", currentBmmEvent.getName());
            } else {
                data.put("missingEmail", 0);
                data.put("missingMobile", 0);
                data.put("missingName", 0);
                data.put("missingMembershipNumber", 0);
                data.put("missingRegion", 0);
                data.put("dataSourceBreakdown", Map.of());
                data.put("incompleteRegistrations", 0);
                data.put("totalMembers", 0);
                data.put("currentBmmEvent", "No BMM event found");
            }

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to generate data quality report", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to generate data quality report: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    导出会员数据报告 (CSV格式)
    @GetMapping("/export/members")
    public ResponseEntity<Map<String, Object>> exportMembersReport(@RequestParam(defaultValue = "all") String type) {
        log.info("Exporting members report of type: {}", type);

        try {
            // Get current BMM event
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "No BMM event found"));
            }

            List<EventMember> eventMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            List<EventMember> members;

            switch (type) {
                case "registered":
                    members = eventMembers.stream()
                            .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                            .collect(Collectors.toList());
                    break;
                case "attending":
                    members = eventMembers.stream()
                            .filter(m -> m.getIsAttending() != null && m.getIsAttending())
                            .collect(Collectors.toList());
                    break;
                case "checked_in":
                    members = eventMembers.stream()
                            .filter(m -> m.getCheckedIn() != null && m.getCheckedIn())
                            .collect(Collectors.toList());
                    break;
                case "special_vote":
                    members = eventMembers.stream()
                            .filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote())
                            .collect(Collectors.toList());
                    break;
                case "email_sent":
                    members = eventMembers.stream()
                            .filter(m -> m.getEmailSent() != null && m.getEmailSent())
                            .collect(Collectors.toList());
                    break;
                case "sms_sent":
                    members = eventMembers.stream()
                            .filter(m -> m.getSmsSent() != null && m.getSmsSent())
                            .collect(Collectors.toList());
                    break;
                default:
                    members = eventMembers;
            }

            // Generate CSV data
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("MemberNumber,Name,Email,Mobile,Region,Workplace,Employer,Registered,Attending,CheckedIn,SpecialVote,EmailSent,SmsSent,DataSource,CreatedAt\n");

            for (EventMember member : members) {
                csvContent.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        member.getMembershipNumber() != null ? member.getMembershipNumber() : "",
                        member.getName() != null ? member.getName() : "",
                        member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "",
                        member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "",
                        member.getRegionDesc() != null ? member.getRegionDesc() : "",
                        member.getWorkplace() != null ? member.getWorkplace() : "",
                        member.getEmployer() != null ? member.getEmployer() : "",
                        member.getHasRegistered() != null ? member.getHasRegistered().toString() : "false",
                        member.getIsAttending() != null ? member.getIsAttending().toString() : "false",
                        member.getCheckedIn() != null ? member.getCheckedIn().toString() : "false",
                        member.getIsSpecialVote() != null ? member.getIsSpecialVote().toString() : "false",
                        member.getEmailSent() != null ? member.getEmailSent().toString() : "false",
                        member.getSmsSent() != null ? member.getSmsSent().toString() : "false",
                        member.getDataSource() != null ? member.getDataSource() : "",
                        member.getCreatedAt() != null ? member.getCreatedAt().toString() : ""
                ));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Member data export successful");

            Map<String, Object> data = new HashMap<>();
            data.put("content", csvContent.toString());
            data.put("filename", "bmm_members_report_" + type + "_" + LocalDate.now() + ".csv");
            data.put("totalRecords", members.size());
            data.put("eventName", currentBmmEvent.getName());

            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to export members report", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to export member data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/export/checkin-details/{eventId}")
    public ResponseEntity<Map<String, Object>> exportCheckinDetails(@PathVariable Long eventId) {
        log.info("Exporting detailed checkin report for event: {}", eventId);

        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Event not found"));
            }

            Event event = eventOpt.get();
            List<EventMember> checkedInMembers = eventMemberRepository.findByEventAndCheckedInTrue(event);

            // Generate detailed CSV data including admin information
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("MembershipNumber,Name,Email,Mobile,Region,Workplace,Employer,CheckedIn,CheckinTime,CheckinLocation,CheckedInByAdmin,AdminEmail,AdminToken,ScanVenue,Timestamp\n");

            for (EventMember member : checkedInMembers) {
                // Parse admin checkin information
                String checkedInByAdmin = "";
                String adminEmail = "";
                String adminToken = "";
                String scanVenue = "";
                String adminTimestamp = "";

                if (member.getRegistrationData() != null && member.getRegistrationData().contains("adminCheckinInfo")) {
                    try {
                        String registrationData = member.getRegistrationData();
                        // Simple JSON parsing (should use Jackson in production)
                        if (registrationData.contains("checkedInByAdmin")) {
                            checkedInByAdmin = extractJsonValue(registrationData, "checkedInByAdmin");
                        }
                        if (registrationData.contains("adminEmail")) {
                            adminEmail = extractJsonValue(registrationData, "adminEmail");
                        }
                        if (registrationData.contains("adminToken")) {
                            adminToken = extractJsonValue(registrationData, "adminToken");
                        }
                        if (registrationData.contains("checkinVenue")) {
                            scanVenue = extractJsonValue(registrationData, "checkinVenue");
                        }
                        if (registrationData.contains("checkinTimestamp")) {
                            adminTimestamp = extractJsonValue(registrationData, "checkinTimestamp");
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse admin checkin info for member {}: {}", member.getMembershipNumber(), e.getMessage());
                    }
                }

                csvContent.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                        member.getMembershipNumber() != null ? member.getMembershipNumber() : "",
                        member.getName() != null ? member.getName() : "",
                        member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "",
                        member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "",
                        member.getRegionDesc() != null ? member.getRegionDesc() : "",
                        member.getWorkplace() != null ? member.getWorkplace() : "",
                        member.getEmployer() != null ? member.getEmployer() : "",
                        member.getCheckedIn() ? "Yes" : "No",
                        member.getCheckInTime() != null ? member.getCheckInTime().toString() : "",
                        member.getCheckInLocation() != null ? member.getCheckInLocation() : "",
                        checkedInByAdmin,
                        adminEmail,
                        adminToken.length() > 10 ? adminToken.substring(0, 10) + "..." : adminToken, // Privacy protection: only show partial token
                        scanVenue,
                        adminTimestamp
                ));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Detailed checkin data export successful");

            Map<String, Object> data = new HashMap<>();
            data.put("content", csvContent.toString());
            data.put("filename", "checkin_details_" + event.getEventCode() + "_" + LocalDate.now() + ".csv");
            data.put("totalRecords", checkedInMembers.size());
            data.put("eventName", event.getName());

            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to export detailed checkin data", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to export checkin data: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Simple JSON value extraction helper method
    private String extractJsonValue(String jsonStr, String key) {
        try {
            int keyIndex = jsonStr.indexOf("\"" + key + "\"");
            if (keyIndex == -1) return "";

            int colonIndex = jsonStr.indexOf(":", keyIndex);
            if (colonIndex == -1) return "";

            int startIndex = jsonStr.indexOf("\"", colonIndex) + 1;
            if (startIndex == 0) return "";

            int endIndex = jsonStr.indexOf("\"", startIndex);
            if (endIndex == -1) return "";

            return jsonStr.substring(startIndex, endIndex);
        } catch (Exception e) {
            return "";
        }
    }
}