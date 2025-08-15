package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.CreateEventRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.dto.response.EventSummaryResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventTemplate;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventTemplateRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.service.ExcelExportService;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/events")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class EventController {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventTemplateRepository eventTemplateRepository;
    private final MemberRepository memberRepository;
    private final InformerSyncService informerSyncService;
    private final ExcelExportService excelExportService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventSummaryResponse>>> getAllEvents() {
        try {
            List<Event> events = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc();
            List<EventSummaryResponse> eventSummaries = events.stream()
                    .map(this::buildEventSummary)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success("Successfully retrieved events list", eventSummaries));
        } catch (Exception e) {
            log.error("Failed to retrieve events list", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<EventSummaryResponse>>> getUpcomingEvents() {
        try {
            List<Event> events = eventRepository.findTop10UpcomingEvents(LocalDateTime.now());
            List<EventSummaryResponse> eventSummaries = events.stream()
                    .map(this::buildEventSummary)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success("Successfully retrieved upcoming events", eventSummaries));
        } catch (Exception e) {
            log.error("Failed to retrieve upcoming events", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventSummaryResponse>> getEventById(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            EventSummaryResponse eventSummary = buildEventSummary(eventOpt.get());
            return ResponseEntity.ok(ApiResponse.success("Successfully retrieved event", eventSummary));
        } catch (Exception e) {
            log.error("Failed to retrieve event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Event>> createEvent(@RequestBody CreateEventRequest request) {
        try {
//            处理事件模板
            EventTemplate eventTemplate = null;
            if (request.getEventTemplateId() != null) {
                Optional<EventTemplate> templateOpt = eventTemplateRepository.findById(request.getEventTemplateId());
                if (templateOpt.isPresent()) {
                    eventTemplate = templateOpt.get();
                } else {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Event template not found"));
                }
            }

            Event event = Event.builder()
                    .name(request.getName())
                    .eventCode(request.getEventCode())
                    .datasetId(request.getDatasetId())
                    .attendeeDatasetId(request.getAttendeeDatasetId())
                    .description(request.getDescription())
                    .eventType(request.getEventType() != null ? request.getEventType() : Event.EventType.GENERAL_MEETING)
                    .eventDate(request.getEventDate())
                    .venue(request.getVenue())
                    .eventTemplate(eventTemplate) // 设置事件模板
                    .isActive(true)
                    .isVotingEnabled(request.getIsVotingEnabled() != null ? request.getIsVotingEnabled() : false)
                    .registrationOpen(request.getRegistrationOpen() != null ? request.getRegistrationOpen() : true)
                    .maxAttendees(request.getMaxAttendees())
                    .syncStatus(Event.SyncStatus.PENDING)
                    .memberSyncCount(0)
                    .attendeeSyncCount(0)
                    .build();

            Event savedEvent = eventRepository.save(event);
            return ResponseEntity.ok(ApiResponse.success("Event created successfully", savedEvent));
        } catch (Exception e) {
            log.error("Failed to create event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Event>> updateEvent(@PathVariable Long id, @RequestBody CreateEventRequest request) {
        try {
            Optional<Event> existingEventOpt = eventRepository.findById(id);
            if (!existingEventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event existingEvent = existingEventOpt.get();
//            处理事件模板
            if (request.getEventTemplateId() != null) {
                Optional<EventTemplate> templateOpt = eventTemplateRepository.findById(request.getEventTemplateId());
                if (templateOpt.isPresent()) {
                    existingEvent.setEventTemplate(templateOpt.get());
                } else {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Event template not found"));
                }
            } else {
                existingEvent.setEventTemplate(null);
            }
            existingEvent.setName(request.getName());
            existingEvent.setEventCode(request.getEventCode());
            existingEvent.setDatasetId(request.getDatasetId());
            existingEvent.setAttendeeDatasetId(request.getAttendeeDatasetId());
            existingEvent.setDescription(request.getDescription());
            if (request.getEventType() != null) {
                existingEvent.setEventType(request.getEventType());
            }
            existingEvent.setEventDate(request.getEventDate());
            existingEvent.setVenue(request.getVenue());
            if (request.getIsVotingEnabled() != null) {
                existingEvent.setIsVotingEnabled(request.getIsVotingEnabled());
            }
            if (request.getRegistrationOpen() != null) {
                existingEvent.setRegistrationOpen(request.getRegistrationOpen());
            }
            existingEvent.setMaxAttendees(request.getMaxAttendees());

            Event savedEvent = eventRepository.save(existingEvent);
            return ResponseEntity.ok(ApiResponse.success("Event updated successfully", savedEvent));
        } catch (Exception e) {
            log.error("Failed to update event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<String>> syncEvent(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            informerSyncService.syncEventData(event);

            return ResponseEntity.ok(ApiResponse.success("Event data synchronized successfully", "Synchronization completed"));
        } catch (Exception e) {
            log.error("Failed to synchronize event data", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Synchronization failed: " + e.getMessage()));
        }
    }

    @PostMapping("/sync-all")
    public ResponseEntity<ApiResponse<String>> syncAllEvents() {
        try {
            informerSyncService.syncAllPendingEvents();
            return ResponseEntity.ok(ApiResponse.success("All events synchronized successfully", "Synchronization completed"));
        } catch (Exception e) {
            log.error("Failed to synchronize all events", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Synchronization failed: " + e.getMessage()));
        }
    }

    @GetMapping("/sync-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSyncStats() {
        try {
            List<Object[]> stats = eventRepository.countByStatus();
            Map<String, Object> result = new HashMap<>();

            for (Object[] stat : stats) {
                result.put(stat[0].toString(), stat[1]);
            }

            result.put("total", eventRepository.count());
            result.put("toSync", eventRepository.findEventsToSync().size());

            return ResponseEntity.ok(ApiResponse.success("Successfully retrieved sync statistics", result));
        } catch (Exception e) {
            log.error("Failed to retrieve sync statistics", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteEvent(@PathVariable Long id) {
        try {
            if (!eventRepository.existsById(id)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }


            Event event = eventRepository.findById(id).get();
            event.setIsActive(false);
            eventRepository.save(event);

            return ResponseEntity.ok(ApiResponse.success("Event deleted successfully", "Deletion completed"));
        } catch (Exception e) {
            log.error("Failed to delete event", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}/member-list")
    public ResponseEntity<Map<String, Object>> getEventMembers(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            Event event = eventOpt.get();

            // For now, return all members since we're using Member table for checkin
            // In the future, this could be filtered by event-specific membership
            List<Member> members = memberRepository.findAll();

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", members);
            response.put("eventName", event.getName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get event members", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get event members: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}/checkin/stats")
    public ResponseEntity<Map<String, Object>> getEventCheckinStats(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            Event event = eventOpt.get();

            // 🔧 Performance Fix: Use EventMember table with optimized queries instead of loading all Members
            long totalMembers = eventMemberRepository.countByEvent(event);
            long attending = eventMemberRepository.countByEventAndIsAttendingTrue(event);
            long checkedIn = eventMemberRepository.countByEventAndCheckedInTrue(event);

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalMembers", totalMembers);
            stats.put("attending", attending);
            stats.put("checkedIn", checkedIn);
            stats.put("eventName", event.getName());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", stats);

            log.debug("Checkin stats for event {} - Total: {}, Attending: {}, CheckedIn: {}",
                    event.getName(), totalMembers, attending, checkedIn);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get event checkin stats", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to get checkin statistics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/{id}/export/members")
    public ResponseEntity<byte[]> exportEventMembers(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            Event event = eventOpt.get();
            byte[] excelData = excelExportService.exportEventMembersToExcel(event);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    event.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_members.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("Failed to export event members", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/sync-attendees")
    public ResponseEntity<ApiResponse<String>> syncAttendeeData() {
        try {
            String attendeeUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/d382fc79-1230-4a1d-917a-7bc43aa21a84/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiI2NjI4ZTNkZi1hZmZhLTQ3OWUtOWEzZC1iZTMwZjFhZjI5NzEiLCJpYXQiOjE3NDk2OTgxODcuMjQxfQ.ZR8WC1UbQAtV6r5EyNG083qzQ450pJb1HRze5wFgR50";

            // CRITICAL: 暂时注释attendee同步，专注于BMM事件
            // informerSyncService.syncAttendeeDataFromUrl(attendeeUrl);

            return ResponseEntity.ok(ApiResponse.success(
                    "Attendee data synchronization disabled (BMM focus)",
                    "Attendee synchronization disabled"
            ));
        } catch (Exception e) {
            log.error("Failed to synchronize attendee data", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Attendee synchronization failed: " + e.getMessage())
            );
        }
    }
    @PostMapping("/sync-email-members")
    public ResponseEntity<ApiResponse<String>> syncEmailMembersData() {
        try {
            String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";

            informerSyncService.syncEmailMembersData(emailMembersUrl);

            return ResponseEntity.ok(ApiResponse.success(
                    "Email members data synchronized successfully",
                    "Email members synchronization completed"
            ));
        } catch (Exception e) {
            log.error("Failed to synchronize email members data", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Email members synchronization failed: " + e.getMessage())
            );
        }
    }
    @PostMapping("/sync-sms-members")
    public ResponseEntity<ApiResponse<String>> syncSmsMembersData() {
        try {
            String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";

            informerSyncService.syncSmsMembersData(smsMembersUrl);

            return ResponseEntity.ok(ApiResponse.success(
                    "SMS members data synchronized successfully",
                    "SMS members synchronization completed"
            ));
        } catch (Exception e) {
            log.error("Failed to synchronize SMS members data", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("SMS members synchronization failed: " + e.getMessage())
            );
        }
    }
    @PostMapping("/sync-all-informer-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncAllInformerData() {
        Map<String, Object> results = new HashMap<>();

        try {
//            同步邮箱会员
            try {
                String emailMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJhMWE2ZTczZC0wOTAzLTRhYmYtOWEzNS00NDc4NGZjOTU4NWEiLCJpYXQiOjE3NDk2OTg0MDkuNTI4fQ.xdS5dHdz0cJhKDvrKoYN9Jgu58XAguPExMcHHKOX9SE";
                informerSyncService.syncEmailMembersData(emailMembersUrl);
                results.put("emailMembers", "SUCCESS");
            } catch (Exception e) {
                results.put("emailMembers", "FAILED: " + e.getMessage());
                log.error("Email members sync failed", e);
            }

//            同步短信会员
            try {
                String smsMembersUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/7fb904b4-05c9-4e14-afe9-25296fde8ed7/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJlYmZhYmU3NS0wYzQ5LTQ1N2QtYjVmOC00YzMwMTZjMDU5MjUiLCJpYXQiOjE3NDk2OTg3MjIuNzUzfQ.HeaV8BWiZp-vBLy1FoXGgVDk-30UYr3wPJEgKE2md3g";
                informerSyncService.syncSmsMembersData(smsMembersUrl);
                results.put("smsMembers", "SUCCESS");
            } catch (Exception e) {
                results.put("smsMembers", "FAILED: " + e.getMessage());
                log.error("SMS members sync failed", e);
            }

//            同步参与者数据
            try {
                String attendeeUrl = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/d382fc79-1230-4a1d-917a-7bc43aa21a84/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiI2NjI4ZTNkZi1hZmZhLTQ3OWUtOWEzZC1iZTMwZjFhZjI5NzEiLCJpYXQiOjE3NDk2OTgxODcuMjQxfQ.ZR8WC1UbQAtV6r5EyNG083qzQ450pJb1HRze5wFgR50";
                // CRITICAL: 暂时注释attendee同步，专注于BMM事件
                // informerSyncService.syncAttendeeDataFromUrl(attendeeUrl);
                results.put("attendees", "SUCCESS");
            } catch (Exception e) {
                results.put("attendees", "FAILED: " + e.getMessage());
                log.error("Attendee sync failed", e);
            }

            results.put("syncTime", LocalDateTime.now());

            return ResponseEntity.ok(ApiResponse.success("All Informer data synchronization completed", results));

        } catch (Exception e) {
            log.error("Failed to synchronize all Informer data", e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("Synchronization failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/sync-bmm-event-members/{eventId}")
    public ResponseEntity<ApiResponse<String>> syncBMMEventMembers(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            if (!event.getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This endpoint is only for BMM Voting events"));
            }

//            BMM events are now auto-managed by DataLoader
//            No longer need manual sync as all members are automatically associated

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("BMM event members synchronized successfully for event: %s", event.getName()),
                    "BMM member synchronization completed"
            ));
        } catch (Exception e) {
            log.error("Failed to synchronize BMM event members for event {}: {}", eventId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("BMM member synchronization failed: " + e.getMessage())
            );
        }
    }

//    exportEventAttendees method removed - EventAttendee table no longer exists
//    Use exportEventMembers method instead for all attendee data

    @GetMapping("/{id}/export/notifications")
    public ResponseEntity<byte[]> exportNotificationLogs(@PathVariable Long id) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            Event event = eventOpt.get();
            byte[] excelData = excelExportService.exportNotificationLogsToExcel(event);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    event.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_notifications.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("Failed to export notification logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // New categorized export endpoint
    @GetMapping("/{id}/export/{type}/{category}")
    public ResponseEntity<byte[]> exportCategorizedData(@PathVariable Long id, @PathVariable String type, @PathVariable String category) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);

            // Filter members based on category
            List<EventMember> filteredMembers = filterMembersByCategory(eventMembers, category);

            byte[] excelData;
            String filename;

            if ("members".equals(type)) {
                excelData = excelExportService.exportFilteredEventMembersToExcel(filteredMembers, event);
                filename = String.format("%s_members_%s.xlsx",
                        event.getName().replaceAll("[^a-zA-Z0-9]", "_"), category);
            } else if ("attendees".equals(type)) {
                // For attendees, use the same filtered members but with different filename
                excelData = excelExportService.exportFilteredEventMembersToExcel(filteredMembers, event);
                filename = String.format("%s_attendees_%s.xlsx",
                        event.getName().replaceAll("[^a-zA-Z0-9]", "_"), category);
            } else if ("checkin".equals(type)) {
                // Filter only checked-in members
                List<EventMember> checkedInMembers = eventMembers.stream()
                        .filter(m -> m.getCheckedIn() != null && m.getCheckedIn())
                        .collect(Collectors.toList());
                excelData = excelExportService.exportCheckinDataToExcel(checkedInMembers, event, category);
                filename = String.format("%s_checkin_%s.xlsx",
                        event.getName().replaceAll("[^a-zA-Z0-9]", "_"), category);
            } else {
                return ResponseEntity.badRequest().build();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("Failed to export categorized data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/export/filtered/{category}")
    public ResponseEntity<byte[]> exportFilteredEventMembers(@PathVariable Long id, @PathVariable String category) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);

            // Filter based on category
            List<EventMember> filteredMembers = eventMembers.stream()
                    .filter(member -> {
                        switch (category.toLowerCase()) {
                            case "registered":
                                return member.getHasRegistered() != null && member.getHasRegistered();
                            case "attending":
                                return member.getIsAttending() != null && member.getIsAttending();
                            case "not_attending":
                                return member.getIsAttending() != null && !member.getIsAttending();
                            case "special_vote":
                                return member.getIsSpecialVote() != null && member.getIsSpecialVote();
                            case "voted":
                                return member.getHasVoted() != null && member.getHasVoted();
                            case "checked_in":
                                return member.getCheckedIn() != null && member.getCheckedIn();
                            case "not_checked_in":
                                return member.getCheckedIn() == null || !member.getCheckedIn();
                            case "with_email":
                                return member.getHasEmail() != null && member.getHasEmail();
                            case "with_mobile":
                                return member.getHasMobile() != null && member.getHasMobile();
                            default:
                                return true; // Return all if category not recognized
                        }
                    })
                    .collect(Collectors.toList());

            byte[] excelData = excelExportService.exportFilteredEventMembersToExcel(filteredMembers, event);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    event.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_" + category + "_members.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("Failed to export filtered event members", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/export/checkin/{category}")
    public ResponseEntity<byte[]> exportCheckinData(@PathVariable Long id, @PathVariable String category) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);

            // Filter based on check-in category
            List<EventMember> filteredMembers = eventMembers.stream()
                    .filter(member -> {
                        switch (category.toLowerCase()) {
                            case "all":
                                return true;
                            case "checked_in":
                                return member.getCheckedIn() != null && member.getCheckedIn();
                            case "not_checked_in":
                                return member.getCheckedIn() == null || !member.getCheckedIn();
                            case "registered_checked_in":
                                return (member.getHasRegistered() != null && member.getHasRegistered()) &&
                                        (member.getCheckedIn() != null && member.getCheckedIn());
                            case "registered_not_checked_in":
                                return (member.getHasRegistered() != null && member.getHasRegistered()) &&
                                        (member.getCheckedIn() == null || !member.getCheckedIn());
                            case "attending_checked_in":
                                return (member.getIsAttending() != null && member.getIsAttending()) &&
                                        (member.getCheckedIn() != null && member.getCheckedIn());
                            case "attending_not_checked_in":
                                return (member.getIsAttending() != null && member.getIsAttending()) &&
                                        (member.getCheckedIn() == null || !member.getCheckedIn());
                            default:
                                return true;
                        }
                    })
                    .collect(Collectors.toList());

            byte[] excelData = excelExportService.exportCheckinDataToExcel(filteredMembers, event, category);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    event.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_checkin_" + category + ".xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelData);

        } catch (Exception e) {
            log.error("Failed to export check-in data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<EventMember> filterMembersByCategory(List<EventMember> members, String category) {
        return members.stream().filter(member -> {
            switch (category) {
                case "all":
                    return true;
                case "registered":
                    return member.getHasRegistered() != null && member.getHasRegistered();
                case "attending":
                    return member.getIsAttending() != null && member.getIsAttending();
                case "not_attending":
                    return member.getIsAttending() != null && !member.getIsAttending();
                case "special_vote":
                    return member.getIsSpecialVote() != null && member.getIsSpecialVote();
                case "has_email":
                    return member.getHasEmail() != null && member.getHasEmail();
                case "has_mobile":
                    return member.getHasMobile() != null && member.getHasMobile();
                case "sms_only":
                    return (member.getHasMobile() != null && member.getHasMobile()) &&
                            (member.getHasEmail() == null || !member.getHasEmail());
                case "email_only":
                    return (member.getHasEmail() != null && member.getHasEmail()) &&
                            (member.getHasMobile() == null || !member.getHasMobile());
                case "northern":
                    return member.getRegionDesc() != null && member.getRegionDesc().toLowerCase().contains("northern");
                case "central":
                    return member.getRegionDesc() != null && member.getRegionDesc().toLowerCase().contains("central");
                case "southern":
                    return member.getRegionDesc() != null && member.getRegionDesc().toLowerCase().contains("southern");
                case "checked_in":
                    return member.getCheckedIn() != null && member.getCheckedIn();
                case "not_checked_in":
                    return member.getCheckedIn() == null || !member.getCheckedIn();
                case "voted":
                    return member.getHasVoted() != null && member.getHasVoted();
                case "not_voted":
                    return member.getHasVoted() == null || !member.getHasVoted();
                default:
                    return true;
            }
        }).collect(Collectors.toList());
    }

    @PostMapping("/{id}/checkin/manual")
    public ResponseEntity<Map<String, Object>> manualCheckin(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        log.info("Manual checkin requested for event: {}", id);

        try {
            String membershipNumber = (String) request.get("membershipNumber");
            String token = (String) request.get("token");
            String venue = (String) request.get("venue");
            String location = (String) request.get("location");

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "membershipNumber is required");
                return ResponseEntity.badRequest().body(response);
            }

            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            Event event = eventOpt.get();

            EventMember member = eventMemberRepository.findByEventAndMembershipNumber(event, membershipNumber).orElse(null);

            if (member == null && token != null && !token.trim().isEmpty()) {
                try {
                    UUID tokenUuid = UUID.fromString(token);
                    member = eventMemberRepository.findByToken(tokenUuid).orElse(null);
                    if (member != null && !member.getEvent().getId().equals(id)) {
                        member = null;
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid token format: {}", token);
                }
            }

            if (member == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Member not found");
                return ResponseEntity.badRequest().body(response);
            }

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

            member.setIsAttending(true);
            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            member.setCheckInLocation(location != null ? location : "Manual Check-in");

            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("membershipNumber", member.getMembershipNumber());
            data.put("name", member.getName());
            data.put("primaryEmail", member.getPrimaryEmail());
            data.put("checkinTime", member.getCheckInTime());
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("message", "Check-in successful");
            response.put("data", data);

            log.info("Manual check-in successful for member: {}", member.getMembershipNumber());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Manual check-in failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Manual check-in failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/{id}/checkin/qr")
    public ResponseEntity<Map<String, Object>> qrCheckin(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        log.info("QR checkin requested for event: {}", id);

        try {
            String token = (String) request.get("token");
            String location = (String) request.get("location");

            if (token == null || token.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "token is required");
                return ResponseEntity.badRequest().body(response);
            }

            Optional<Event> eventOpt = eventRepository.findById(id);
            if (!eventOpt.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Event not found");
                return ResponseEntity.badRequest().body(response);
            }

            Event event = eventOpt.get();

            EventMember member;
            try {
                UUID tokenUuid = UUID.fromString(token);
                member = eventMemberRepository.findByToken(tokenUuid).orElse(null);
                if (member != null && !member.getEvent().getId().equals(id)) {
                    member = null;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid token format: {}", token);
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Invalid QR code format");
                return ResponseEntity.badRequest().body(response);
            }

            if (member == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Invalid QR code");
                return ResponseEntity.badRequest().body(response);
            }

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

            member.setIsAttending(true);
            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            member.setCheckInLocation(location != null ? location : "QR Check-in");

            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("membershipNumber", member.getMembershipNumber());
            data.put("name", member.getName());
            data.put("primaryEmail", member.getPrimaryEmail());
            data.put("checkinTime", member.getCheckInTime());
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("message", "Check-in successful");
            response.put("data", data);

            log.info("QR check-in successful for member: {}", member.getMembershipNumber());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("QR check-in failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "QR check-in failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private EventSummaryResponse buildEventSummary(Event event) {
        Long totalMembers = eventMemberRepository.countByEvent(event);
        Long registeredMembers = eventMemberRepository.countRegisteredByEvent(event);
        Long attendingMembers = eventMemberRepository.countAttendingByEvent(event);
        Long specialVoteMembers = eventMemberRepository.countSpecialVoteByEvent(event);
        Long votedMembers = eventMemberRepository.countVotedByEvent(event);
        Long checkedInMembers = eventMemberRepository.countCheckedInByEvent(event);

        return EventSummaryResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .eventCode(event.getEventCode())
                .description(event.getDescription())
                .eventType(event.getEventType())
                .eventDate(event.getEventDate())
                .venue(event.getVenue())
                .isActive(event.getIsActive())
                .isVotingEnabled(event.getIsVotingEnabled())
                .registrationOpen(event.getRegistrationOpen())
                .maxAttendees(event.getMaxAttendees())
                .syncStatus(event.getSyncStatus())
                .lastSyncTime(event.getLastSyncTime())
                .memberSyncCount(event.getMemberSyncCount())
                .attendeeSyncCount(event.getAttendeeSyncCount())
                .totalMembers(totalMembers.intValue())
                .registeredMembers(registeredMembers.intValue())
                .attendingMembers(attendingMembers.intValue())
                .specialVoteMembers(specialVoteMembers.intValue())
                .votedMembers(votedMembers.intValue())
                .checkedInMembers(checkedInMembers.intValue())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}