package nz.etu.voting.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.BmmPreferenceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.NonAttendanceRequest;
import nz.etu.voting.domain.dto.response.BmmAssignmentResponse;
import nz.etu.voting.domain.dto.response.BmmStatistics;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.FinancialForm;
import nz.etu.voting.domain.entity.NotificationTemplate;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.FinancialFormRepository;
import nz.etu.voting.repository.NotificationTemplateRepository;
import nz.etu.voting.service.BmmService;
import nz.etu.voting.service.NotificationService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BmmServiceImpl implements BmmService {

    private final EventMemberRepository eventMemberRepository;
    private final FinancialFormRepository financialFormRepository;
    private final EventRepository eventRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;

    @Value("${app.bmm.northern-region}")
    private String northernRegion = "Northern Region";

    @Value("${app.bmm.central-region}")
    private String centralRegion = "Central Region";

    @Value("${app.bmm.southern-region}")
    private String southernRegion = "Southern Region";

    @Override
    public EventMember findEventMemberByToken(String token) {
        log.info("Finding EventMember by token: {}", token);
        try {
            UUID tokenUuid = UUID.fromString(token);
            return eventMemberRepository.findByToken(tokenUuid)
                    .orElseThrow(() -> new RuntimeException("EventMember not found with token: " + token));
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for token: {}", token);
            throw new RuntimeException("Invalid token format: " + token);
        }
    }

    /**
     * Generate unique member token for BMM access
     * This should be called during EventMember initialization
     */
    public String generateUniqueMemberToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        } while (eventMemberRepository.findByMemberToken(token).isPresent());
        return token;
    }

    @Override
    @Transactional
    public void savePreferencesToEventMember(BmmPreferenceRequest request) {
        log.info("Saving BMM preferences for token: {}", request.getToken());

        try {
            EventMember eventMember = findEventMemberByToken(request.getToken());

            // Validate that at most one venue is selected (can be 0 if no venue assigned)
            if (request.getPreferredVenues().size() > 1) {
                throw new IllegalArgumentException("At most one venue can be selected");
            }

            // Save preferences as JSON
            eventMember.setPreferredVenuesJson(objectMapper.writeValueAsString(request.getPreferredVenues()));
            eventMember.setPreferredDatesJson(objectMapper.writeValueAsString(request.getPreferredDates()));
            eventMember.setPreferredTimesJson(objectMapper.writeValueAsString(request.getPreferredTimes()));

            // Save initial attendance intention (not final decision)
            if (request.getIntendToAttend() != null) {
                eventMember.setPreferredAttending(request.getIntendToAttend());
            }

            // Save workplace info
            if (request.getWorkplaceInfo() != null) {
                eventMember.setWorkplaceInfo(request.getWorkplaceInfo());
            }

            // Save suggested venue
            if (request.getSuggestedVenue() != null) {
                eventMember.setSuggestedVenue(request.getSuggestedVenue());
            }

            // Update additional comments if provided
            if (request.getAdditionalComments() != null) {
                eventMember.setAdditionalComments(request.getAdditionalComments());
            }

            // Save preference special vote (pre-registration phase special vote interest)
            if (request.getPreferenceSpecialVote() != null) {
                eventMember.setPreferenceSpecialVote(request.getPreferenceSpecialVote());
            }

            // Update stage and timestamps
            // 直接设置为VENUE_ASSIGNED，跳过等待分配的步骤
            eventMember.setBmmStage("VENUE_ASSIGNED");
            eventMember.setPreferenceSubmittedAt(LocalDateTime.now());
            eventMember.setVenueAssignedAt(LocalDateTime.now());

            // 设置默认的场地和时间（可以后续在第二阶段让用户选择或确认）
            if (eventMember.getAssignedVenueFinal() == null) {
                // 使用用户的第一个偏好场地，如果有的话
                List<String> preferredVenues = objectMapper.readValue(eventMember.getPreferredVenuesJson(), List.class);
                if (!preferredVenues.isEmpty()) {
                    eventMember.setAssignedVenueFinal(preferredVenues.get(0));
                }
                // 不设置默认时间，保持为null直到管理员分配
                // eventMember.setAssignedDatetimeFinal(LocalDateTime.of(2025, 9, 15, 14, 0));
            }
            eventMember.setLastActivityAt(LocalDateTime.now());

            eventMemberRepository.save(eventMember);

            log.info("Successfully saved preferences for EventMember ID: {}", eventMember.getId());

            // 不立即发送第二阶段邮件，等待管理员在合适的时间批量发送
            // 第一阶段（7月13日）和第二阶段（8月1日）之间有时间间隔

        } catch (JsonProcessingException e) {
            log.error("Error serializing preferences to JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to save preferences", e);
        }
    }

    @Override
    public BmmAssignmentResponse getAssignmentByToken(String token) {
        log.info("Getting assignment for token: {}", token);

        EventMember eventMember = findEventMemberByToken(token);

        return BmmAssignmentResponse.builder()
                .memberName(eventMember.getName())
                .membershipNumber(eventMember.getMembershipNumber())
                .memberToken(eventMember.getMemberToken())
                .assignedVenue(eventMember.getAssignedVenueFinal())
                .assignedDateTime(eventMember.getAssignedDatetimeFinal())
                .assignedRegion(eventMember.getAssignedRegion())
                .bmmStage(eventMember.getBmmStage())
                .ticketStatus(eventMember.getTicketStatus())
                .registrationStatus(eventMember.getRegistrationStatus())
                .venueAssignedAt(eventMember.getVenueAssignedAt())
                .venueAssignedBy(eventMember.getVenueAssignedBy())
                .ticketToken(eventMember.getTicketToken() != null ? eventMember.getTicketToken().toString() : null)
                .ticketGeneratedAt(eventMember.getTicketGeneratedAt())
                .ticketPdfPath(eventMember.getTicketPdfPath())
                .specialVoteEligible(eventMember.getSpecialVoteEligible())
                .specialVotePreference(eventMember.getSpecialVotePreference())
                .bmmSpecialVoteStatus(eventMember.getBmmSpecialVoteStatus())
                .hasEmail(eventMember.getHasEmail())
                .hasMobile(eventMember.getHasMobile())
                .primaryEmail(eventMember.getPrimaryEmail())
                .telephoneMobile(eventMember.getTelephoneMobile())
                .regionDesc(eventMember.getRegionDesc())
                .workplace(eventMember.getWorkplace())
                .employer(eventMember.getEmployer())
                .build();
    }

    @Override
    @Transactional
    public void updateEventMemberFinancialInfo(FinancialFormRequest request) {
        log.info("Updating financial information for member: {}", request.getEmail());

        // Find EventMember by email
        List<EventMember> eventMembers = eventMemberRepository.findByPrimaryEmail(request.getEmail());
        if (eventMembers.isEmpty()) {
            throw new RuntimeException("EventMember not found with email: " + request.getEmail());
        }

        EventMember eventMember = eventMembers.get(0); // Take first match

        try {
            // Capture before data for audit trail
            String beforeData = objectMapper.writeValueAsString(createEventMemberSnapshot(eventMember));

            // Update EventMember with financial information
            updateEventMemberFromFinancialForm(eventMember, request);

            // Save updated EventMember
            eventMemberRepository.save(eventMember);

            // Capture after data for audit trail
            String afterData = objectMapper.writeValueAsString(createEventMemberSnapshot(eventMember));

            // Create FinancialForm audit record
            FinancialForm financialForm = FinancialForm.builder()
                    .eventMemberId(eventMember.getId())
                    .eventId(eventMember.getEvent().getId())
                    .memberName(eventMember.getName())
                    .membershipNumber(eventMember.getMembershipNumber())
                    .primaryEmail(eventMember.getPrimaryEmail())
                    .telephoneMobile(eventMember.getTelephoneMobile())
                    .beforeData(beforeData)
                    .afterData(afterData)
                    .updatedFields(determineUpdatedFields(request))
                    .formType("BMM_REGISTRATION")
                    .updateSource("WEB")
                    .updatedBy("SYSTEM")
                    .stratumSyncStatus("PENDING")
                    .approvalStatus("APPROVED")
                    .build();

            financialFormRepository.save(financialForm);

            // Update EventMember with FinancialForm reference
            eventMember.setFinancialFormId(financialForm.getId());
            eventMemberRepository.save(eventMember);

            log.info("Successfully updated financial information for EventMember ID: {}", eventMember.getId());

        } catch (JsonProcessingException e) {
            log.error("Error processing financial form data: {}", e.getMessage());
            throw new RuntimeException("Failed to update financial information", e);
        }
    }

    @Override
    @Transactional
    public void generateAndSendTicket(Long eventMemberId) {
        log.info("Generating and sending ticket for EventMember ID: {}", eventMemberId);

        EventMember eventMember = eventMemberRepository.findById(eventMemberId)
                .orElseThrow(() -> new RuntimeException("EventMember not found with ID: " + eventMemberId));

        // Generate unique ticket token
        eventMember.setTicketToken(UUID.randomUUID());
        eventMember.setTicketGeneratedAt(LocalDateTime.now());
        eventMember.setTicketStatus("GENERATED");
        eventMember.setBmmStage("TICKET_ISSUED");

        // Generate ticket PDF path (this would integrate with PDF generation service)
        String ticketPdfPath = generateTicketPdf(eventMember);
        eventMember.setTicketPdfPath(ticketPdfPath);

        eventMemberRepository.save(eventMember);

        // Send ticket via appropriate channel
        sendTicketNotification(eventMember);

        log.info("Successfully generated and sent ticket for EventMember ID: {}", eventMemberId);
    }

    @Override
    @Transactional
    public void recordNonAttendance(Long eventMemberId) {
        log.info("Recording non-attendance for EventMember ID: {}", eventMemberId);

        EventMember eventMember = eventMemberRepository.findById(eventMemberId)
                .orElseThrow(() -> new RuntimeException("EventMember not found with ID: " + eventMemberId));

        eventMember.setAttendanceConfirmed(false);
        eventMember.setIsAttending(false);
        eventMember.setAttendanceDecisionAt(LocalDateTime.now());
        eventMember.setBmmStage("ATTENDANCE_DECLINED");

        eventMemberRepository.save(eventMember);

        log.info("Successfully recorded non-attendance for EventMember ID: {}", eventMemberId);
    }

    @Override
    @Transactional
    public void processNonAttendanceWithSpecialVote(NonAttendanceRequest request) {
        log.info("Processing non-attendance with special vote for EventMember ID: {}", request.getEventMemberId());

        EventMember eventMember = eventMemberRepository.findById(request.getEventMemberId())
                .orElseThrow(() -> new RuntimeException("EventMember not found with ID: " + request.getEventMemberId()));

        // Validate that special vote is only available for Central/Southern regions
        if (!isCentralOrSouthernRegion(eventMember.getRegionDesc())) {
            throw new IllegalArgumentException("Special vote is only available for Central and Southern regions");
        }

        // Record non-attendance
        recordNonAttendance(request.getEventMemberId());

        // Process special vote request if requested
        if (Boolean.TRUE.equals(request.getRequestSpecialVote())) {
            processSpecialVoteRequest(eventMember, request);
        }

        log.info("Successfully processed non-attendance with special vote for EventMember ID: {}", request.getEventMemberId());
    }

    @Override
    @Transactional
    public void sendStage1InvitationsByRegion(String regionDesc) {
        log.info("Sending Stage 1 invitations for region: {}", regionDesc);

        // Find all EventMembers in the region who haven't received invitations
        List<EventMember> members = eventMemberRepository.findAll().stream()
                .filter(em -> regionDesc.equals(em.getRegionDesc()))
                .filter(em -> !Boolean.TRUE.equals(em.getBmmInvitationSent()))
                .collect(Collectors.toList());

        log.info("Found {} members to invite in region: {}", members.size(), regionDesc);

        // Get or create invitation template
        NotificationTemplate template = getOrCreateBmmInvitationTemplate();

        // Send invitations
        for (EventMember member : members) {
            try {
                sendBmmInvitation(member, template);

                // Update invitation tracking
                member.setBmmInvitationSent(true);
                member.setBmmInvitationSentAt(LocalDateTime.now());
                eventMemberRepository.save(member);

            } catch (Exception e) {
                log.error("Failed to send invitation to member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
        }

        log.info("Successfully sent Stage 1 invitations for region: {}", regionDesc);
    }

    @Override
    @Transactional
    public void sendStage2ConfirmationEmails() {
        log.info("Sending Stage 2 confirmation emails");

        // Find all EventMembers who have venue assignments but haven't received confirmation requests
        List<EventMember> members = eventMemberRepository.findAll().stream()
                .filter(em -> "VENUE_ASSIGNED".equals(em.getBmmStage()))
                .filter(em -> !Boolean.TRUE.equals(em.getBmmConfirmationRequestSent()))
                .collect(Collectors.toList());

        log.info("Found {} members to send Stage 2 confirmations", members.size());

        // Get or create confirmation template
        NotificationTemplate template = getOrCreateBmmConfirmationTemplate();

        // Send confirmations
        for (EventMember member : members) {
            try {
                sendBmmConfirmation(member, template);

                // Update confirmation tracking
                member.setBmmConfirmationRequestSent(true);
                member.setBmmConfirmationRequestSentAt(LocalDateTime.now());
                eventMemberRepository.save(member);

            } catch (Exception e) {
                log.error("Failed to send confirmation to member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
        }

        log.info("Successfully sent Stage 2 confirmation emails");
    }

    @Override
    @Transactional
    public void sendSpecialVoteLinks() {
        log.info("Sending special vote links");

        // Find all EventMembers who are eligible for special vote and haven't received links
        List<EventMember> members = eventMemberRepository.findAll().stream()
                .filter(em -> Boolean.TRUE.equals(em.getSpecialVoteEligible()))
                .filter(em -> "APPROVED".equals(em.getBmmSpecialVoteStatus()))
                .filter(em -> em.getSpecialVoteSentAt() == null)
                .collect(Collectors.toList());

        log.info("Found {} members to send special vote links", members.size());

        // Get or create special vote template
        NotificationTemplate template = getOrCreateSpecialVoteTemplate();

        // Send special vote links
        for (EventMember member : members) {
            try {
                sendSpecialVoteLink(member, template);

                // Update special vote tracking
                member.setSpecialVoteSentAt(LocalDateTime.now());
                eventMemberRepository.save(member);

            } catch (Exception e) {
                log.error("Failed to send special vote link to member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
        }

        log.info("Successfully sent special vote links");
    }

    @Override
    @Transactional
    public void assignVenuesToMembers(Long eventId) {
        log.info("Assigning venues to members for event ID: {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + eventId));

        // Find all EventMembers with submitted preferences who haven't been assigned venues
        List<EventMember> members = eventMemberRepository.findByEventAndBmmRegistrationStage(event, "PREFERENCE_SUBMITTED");

        log.info("Found {} members with submitted preferences to assign venues", members.size());

        // Group members by region for venue assignment
        Map<String, List<EventMember>> membersByRegion = members.stream()
                .collect(Collectors.groupingBy(EventMember::getRegionDesc));

        // Assign venues for each region
        for (Map.Entry<String, List<EventMember>> entry : membersByRegion.entrySet()) {
            String region = entry.getKey();
            List<EventMember> regionMembers = entry.getValue();

            log.info("Assigning venues for {} members in region: {}", regionMembers.size(), region);
            assignVenuesForRegion(regionMembers, region);
        }

        log.info("Successfully assigned venues to members for event ID: {}", eventId);
    }

    @Override
    public BmmStatistics getBmmStatistics(Long eventId) {
        log.info("Getting BMM statistics for event ID: {}", eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found with ID: " + eventId));

        List<EventMember> allMembers = eventMemberRepository.findByEvent(event);

        // Calculate overall statistics
        BmmStatistics statistics = BmmStatistics.builder()
                .totalMembers((long) allMembers.size())
                .preferenceSubmitted(countByStage(allMembers, "PREFERENCE_SUBMITTED"))
                .venueAssigned(countByStage(allMembers, "VENUE_ASSIGNED"))
                .attendanceConfirmed(countByField(allMembers, em -> Boolean.TRUE.equals(em.getAttendanceConfirmed())))
                .attendanceDeclined(countByField(allMembers, em -> Boolean.FALSE.equals(em.getAttendanceConfirmed())))
                .ticketsIssued(countByStage(allMembers, "TICKET_ISSUED"))
                .checkedIn(countByStage(allMembers, "CHECKED_IN"))
                .pendingInvitations(countByStage(allMembers, "INVITED"))
                .pendingAssignments(countByStage(allMembers, "PREFERENCE_SUBMITTED"))
                .pendingConfirmations(countByStage(allMembers, "VENUE_ASSIGNED"))
                .pendingTickets(countByStage(allMembers, "ATTENDANCE_CONFIRMED"))
                .specialVoteEligible(countByField(allMembers, em -> Boolean.TRUE.equals(em.getSpecialVoteEligible())))
                .specialVoteRequested(countByField(allMembers, em -> Boolean.TRUE.equals(em.getSpecialVoteRequested())))
                .specialVoteApproved(countByField(allMembers, em -> "APPROVED".equals(em.getBmmSpecialVoteStatus())))
                .specialVoteCompleted(countByField(allMembers, em -> em.getSpecialVoteCompletedAt() != null))
                .invitationEmailsSent(countByField(allMembers, em -> Boolean.TRUE.equals(em.getBmmInvitationSent())))
                .confirmationRequestsSent(countByField(allMembers, em -> Boolean.TRUE.equals(em.getBmmConfirmationRequestSent())))
                .ticketEmailsSent(countByField(allMembers, em -> em.getTicketEmailSentAt() != null))
                .specialVoteLinksSent(countByField(allMembers, em -> em.getSpecialVoteSentAt() != null))
                .build();

        // Calculate regional statistics
        Map<String, BmmStatistics.RegionalStats> regionalStats = calculateRegionalStats(allMembers);
        statistics.setRegionalStats(regionalStats);

        // Calculate venue statistics
        Map<String, BmmStatistics.VenueStats> venueStats = calculateVenueStats(allMembers);
        statistics.setVenueStats(venueStats);

        log.info("Successfully calculated BMM statistics for event ID: {}", eventId);
        return statistics;
    }

    // Helper methods

    private boolean isCentralOrSouthernRegion(String regionDesc) {
        return centralRegion.equals(regionDesc) || southernRegion.equals(regionDesc);
    }

    private void processSpecialVoteRequest(EventMember eventMember, NonAttendanceRequest request) {
        eventMember.setSpecialVoteRequested(true);
        eventMember.setSpecialVoteReason(request.getSpecialVoteReason());
        eventMember.setSpecialVoteEligibilityReason(request.getSpecialVoteEligibilityReason());
        eventMember.setDistanceFromVenue(request.getDistanceFromVenue());
        eventMember.setEmployerWorkRequirement(request.getEmployerWorkRequirement());
        eventMember.setMedicalCertificateProvided(request.getMedicalCertificateProvided());
        eventMember.setSpecialVoteApplicationReason(request.getAdditionalDetails());
        eventMember.setSpecialVoteApplicationDate(LocalDateTime.now());
        eventMember.setBmmSpecialVoteStatus("PENDING");

        eventMemberRepository.save(eventMember);
    }

    private Map<String, Object> createEventMemberSnapshot(EventMember eventMember) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", eventMember.getId());
        snapshot.put("name", eventMember.getName());
        snapshot.put("primaryEmail", eventMember.getPrimaryEmail());
        snapshot.put("telephoneMobile", eventMember.getTelephoneMobile());
        snapshot.put("address", eventMember.getAddress());
        snapshot.put("employer", eventMember.getEmployer());
        snapshot.put("payrollNumber", eventMember.getPayrollNumber());
        snapshot.put("siteCode", eventMember.getSiteCode());
        snapshot.put("employmentStatus", eventMember.getEmploymentStatus());
        snapshot.put("department", eventMember.getDepartment());
        snapshot.put("jobTitle", eventMember.getJobTitle());
        snapshot.put("location", eventMember.getLocation());
        snapshot.put("phoneHome", eventMember.getPhoneHome());
        snapshot.put("phoneWork", eventMember.getPhoneWork());
        return snapshot;
    }

    private void updateEventMemberFromFinancialForm(EventMember eventMember, FinancialFormRequest request) {
        eventMember.setName(request.getName());
        eventMember.setPrimaryEmail(request.getEmail());
        eventMember.setTelephoneMobile(request.getTelephoneMobile());
        eventMember.setAddress(request.getAddress());
        eventMember.setEmployer(request.getEmployer());
        eventMember.setPayrollNumber(request.getPayrollNumber());
        eventMember.setSiteCode(request.getSiteCode());
        eventMember.setEmploymentStatus(request.getEmploymentStatus());
        eventMember.setDepartment(request.getDepartment());
        eventMember.setJobTitle(request.getJobTitle());
        eventMember.setLocation(request.getLocation());
        eventMember.setPhoneHome(request.getPhoneHome());
        eventMember.setPhoneWork(request.getPhoneWork());
        eventMember.setLastActivityAt(LocalDateTime.now());

        // Update BMM preferences if provided
        if (request.getBmmPreferences() != null) {
            updateBmmPreferences(eventMember, request.getBmmPreferences());
        }

        // Update BMM stage to indicate profile update
        if ("INVITED".equals(eventMember.getBmmStage())) {
            eventMember.setBmmStage("PROFILE_UPDATED");
        }
    }

    private void updateBmmPreferences(EventMember eventMember, FinancialFormRequest.BmmPreferences preferences) {
        try {
            if (preferences.getPreferredVenues() != null) {
                eventMember.setPreferredVenuesJson(objectMapper.writeValueAsString(preferences.getPreferredVenues()));
            }
            if (preferences.getPreferredTimes() != null) {
                eventMember.setPreferredTimesJson(objectMapper.writeValueAsString(preferences.getPreferredTimes()));
            }
            eventMember.setAttendanceWillingness(preferences.getAttendanceWillingness());
            eventMember.setWorkplaceInfo(preferences.getWorkplaceInfo());
            eventMember.setMeetingFormat(preferences.getMeetingFormat());
            eventMember.setAdditionalComments(preferences.getAdditionalComments());
            eventMember.setSuggestedVenue(preferences.getSuggestedVenue());
        } catch (JsonProcessingException e) {
            log.error("Error updating BMM preferences: {}", e.getMessage());
        }
    }

    private String determineUpdatedFields(FinancialFormRequest request) {
        List<String> updatedFields = new ArrayList<>();
        updatedFields.add("name");
        updatedFields.add("email");
        updatedFields.add("telephoneMobile");
        updatedFields.add("address");
        updatedFields.add("employer");
        updatedFields.add("payrollNumber");
        updatedFields.add("siteCode");
        updatedFields.add("employmentStatus");
        updatedFields.add("department");
        updatedFields.add("jobTitle");
        updatedFields.add("location");
        updatedFields.add("phoneHome");
        updatedFields.add("phoneWork");

        if (request.getBmmPreferences() != null) {
            updatedFields.add("bmmPreferences");
        }

        return String.join(",", updatedFields);
    }

    private String generateTicketPdf(EventMember eventMember) {
        // This would integrate with a PDF generation service
        // For now, return a placeholder path
        return String.format("/tickets/bmm-%s-%s.pdf",
                eventMember.getEvent().getId(),
                eventMember.getTicketToken());
    }

    private void sendTicketNotification(EventMember eventMember) {
        try {
            NotificationTemplate template = getOrCreateTicketTemplate();

            // 格式化日期时间为用户友好格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String formattedDateTime = eventMember.getAssignedDatetimeFinal() != null ?
                    eventMember.getAssignedDatetimeFinal().format(formatter) : "TBD";

            Map<String, String> variables = Map.of(
                    "name", eventMember.getName(),
                    "membershipNumber", eventMember.getMembershipNumber(),
                    "venue", eventMember.getAssignedVenueFinal() != null ? eventMember.getAssignedVenueFinal() : "TBD",
                    "dateTime", formattedDateTime,
                    "ticketToken", eventMember.getTicketToken().toString(),
                    "ticketLink", generateTicketLink(eventMember)
            );

            if (eventMember.getHasEmail()) {
                sendEmailNotification(eventMember, template, variables);
                eventMember.setTicketEmailSentAt(LocalDateTime.now());
            }

            if (eventMember.getHasMobile() && !eventMember.getHasEmail()) {
                sendSmsNotification(eventMember, template, variables);
                eventMember.setTicketSmsSentAt(LocalDateTime.now());
            }

            eventMember.setTicketStatus("SENT");
            eventMemberRepository.save(eventMember);

        } catch (Exception e) {
            log.error("Failed to send ticket notification: {}", e.getMessage());
            eventMember.setTicketStatus("FAILED");
            eventMemberRepository.save(eventMember);
        }
    }

    private String generateTicketLink(EventMember eventMember) {
        return String.format("https://events.etu.nz/tickets/bmm?token=%s", eventMember.getTicketToken());
    }

    private void sendBmmInvitation(EventMember eventMember, NotificationTemplate template) {
        Map<String, String> variables = Map.of(
                "name", eventMember.getName(),
                "membershipNumber", eventMember.getMembershipNumber(),
                "eventName", eventMember.getEvent().getName(),
                "regionDesc", eventMember.getRegionDesc(),
                "bmmLink", generateBmmLink(eventMember)
        );

        if (eventMember.getHasEmail()) {
            sendEmailNotification(eventMember, template, variables);
        }

        if (eventMember.getHasMobile() && !eventMember.getHasEmail()) {
            sendSmsNotification(eventMember, template, variables);
        }
    }

    private void sendBmmConfirmation(EventMember eventMember, NotificationTemplate template) {
        Map<String, String> variables = Map.of(
                "name", eventMember.getName(),
                "membershipNumber", eventMember.getMembershipNumber(),
                "venue", eventMember.getAssignedVenueFinal() != null ? eventMember.getAssignedVenueFinal() : "TBD",
                "dateTime", eventMember.getAssignedDatetimeFinal() != null ? eventMember.getAssignedDatetimeFinal().toString() : "TBD",
                "confirmationLink", generateConfirmationLink(eventMember)
        );

        if (eventMember.getHasEmail()) {
            sendEmailNotification(eventMember, template, variables);
        }

        if (eventMember.getHasMobile() && !eventMember.getHasEmail()) {
            sendSmsNotification(eventMember, template, variables);
        }
    }

    private void sendSpecialVoteLink(EventMember eventMember, NotificationTemplate template) {
        Map<String, String> variables = Map.of(
                "name", eventMember.getName(),
                "membershipNumber", eventMember.getMembershipNumber(),
                "specialVoteLink", generateSpecialVoteLink(eventMember)
        );

        if (eventMember.getHasEmail()) {
            sendEmailNotification(eventMember, template, variables);
        }

        if (eventMember.getHasMobile() && !eventMember.getHasEmail()) {
            sendSmsNotification(eventMember, template, variables);
        }
    }

    private String generateBmmLink(EventMember eventMember) {
        return String.format("https://events.etu.nz/bmm/preferences?token=%s", eventMember.getMemberToken());
    }

    private String generateConfirmationLink(EventMember eventMember) {
        // 使用/bmm入口，系统会根据stage自动跳转到正确的页面
        return String.format("https://events.etu.nz/bmm?token=%s", eventMember.getMemberToken());
    }

    private String generateSpecialVoteLink(EventMember eventMember) {
        return String.format("https://events.etu.nz/bmm/special-vote?token=%s", eventMember.getMemberToken());
    }

    private void sendEmailNotification(EventMember eventMember, NotificationTemplate template, Map<String, String> variables) {
        try {
            String personalizedSubject = replaceVariables(template.getSubject(), variables);
            String personalizedContent = replaceVariables(template.getContent(), variables);

            Map<String, Object> emailData = Map.of(
                    "recipient", eventMember.getPrimaryEmail(),
                    "recipientName", eventMember.getName(),
                    "subject", personalizedSubject,
                    "content", personalizedContent,
                    "eventMemberId", eventMember.getId(),
                    "templateCode", template.getTemplateCode(),
                    "notificationType", "BMM_EMAIL"
            );

            rabbitTemplate.convertAndSend(emailQueue, emailData);

        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            throw e;
        }
    }

    private void sendSmsNotification(EventMember eventMember, NotificationTemplate template, Map<String, String> variables) {
        try {
            String personalizedContent = replaceVariables(template.getContent(), variables);

            Map<String, Object> smsData = Map.of(
                    "recipient", eventMember.getTelephoneMobile(),
                    "recipientName", eventMember.getName(),
                    "content", personalizedContent,
                    "eventMemberId", eventMember.getId(),
                    "templateCode", template.getTemplateCode(),
                    "notificationType", "BMM_SMS"
            );

            rabbitTemplate.convertAndSend(smsQueue, smsData);

        } catch (Exception e) {
            log.error("Failed to send SMS notification: {}", e.getMessage());
            throw e;
        }
    }

    private String replaceVariables(String content, Map<String, String> variables) {
        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private void assignVenuesForRegion(List<EventMember> members, String region) {
        // This would implement the venue assignment algorithm
        // For now, we'll assign venues based on preferences

        for (EventMember member : members) {
            try {
                // Parse preferred venues from JSON
                String preferredVenuesJson = member.getPreferredVenuesJson();
                if (preferredVenuesJson != null) {
                    List<String> preferredVenues = objectMapper.readValue(preferredVenuesJson, List.class);
                    if (!preferredVenues.isEmpty()) {
                        // Assign the first preferred venue (could be more sophisticated)
                        member.setAssignedVenueFinal(preferredVenues.get(0));
                        member.setAssignedRegion(region);
                        member.setVenueAssignedAt(LocalDateTime.now());
                        member.setVenueAssignedBy("SYSTEM");
                        member.setBmmStage("VENUE_ASSIGNED");

                        // 不设置默认时间，保持为null直到管理员分配
                        // member.setAssignedDatetimeFinal(LocalDateTime.now().plusDays(30));

                        eventMemberRepository.save(member);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to assign venue for member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
        }
    }

    private Long countByStage(List<EventMember> members, String stage) {
        return members.stream()
                .filter(em -> stage.equals(em.getBmmStage()))
                .count();
    }

    private Long countByField(List<EventMember> members, java.util.function.Predicate<EventMember> condition) {
        return members.stream()
                .filter(condition)
                .count();
    }

    private Map<String, BmmStatistics.RegionalStats> calculateRegionalStats(List<EventMember> allMembers) {
        Map<String, BmmStatistics.RegionalStats> regionalStats = new HashMap<>();

        Map<String, List<EventMember>> membersByRegion = allMembers.stream()
                .collect(Collectors.groupingBy(EventMember::getRegionDesc));

        for (Map.Entry<String, List<EventMember>> entry : membersByRegion.entrySet()) {
            String region = entry.getKey();
            List<EventMember> members = entry.getValue();

            BmmStatistics.RegionalStats stats = BmmStatistics.RegionalStats.builder()
                    .region(region)
                    .totalMembers((long) members.size())
                    .preferenceSubmitted(countByStage(members, "PREFERENCE_SUBMITTED"))
                    .venueAssigned(countByStage(members, "VENUE_ASSIGNED"))
                    .attendanceConfirmed(countByField(members, em -> Boolean.TRUE.equals(em.getAttendanceConfirmed())))
                    .attendanceDeclined(countByField(members, em -> Boolean.FALSE.equals(em.getAttendanceConfirmed())))
                    .ticketsIssued(countByStage(members, "TICKET_ISSUED"))
                    .checkedIn(countByStage(members, "CHECKED_IN"))
                    .specialVoteEligible(countByField(members, em -> Boolean.TRUE.equals(em.getSpecialVoteEligible())))
                    .specialVoteRequested(countByField(members, em -> Boolean.TRUE.equals(em.getSpecialVoteRequested())))
                    .build();

            regionalStats.put(region, stats);
        }

        return regionalStats;
    }

    private Map<String, BmmStatistics.VenueStats> calculateVenueStats(List<EventMember> allMembers) {
        Map<String, BmmStatistics.VenueStats> venueStats = new HashMap<>();

        Map<String, List<EventMember>> membersByVenue = allMembers.stream()
                .filter(em -> em.getAssignedVenueFinal() != null)
                .collect(Collectors.groupingBy(EventMember::getAssignedVenueFinal));

        for (Map.Entry<String, List<EventMember>> entry : membersByVenue.entrySet()) {
            String venue = entry.getKey();
            List<EventMember> members = entry.getValue();

            BmmStatistics.VenueStats stats = BmmStatistics.VenueStats.builder()
                    .venue(venue)
                    .assigned((long) members.size())
                    .confirmed(countByField(members, em -> Boolean.TRUE.equals(em.getAttendanceConfirmed())))
                    .declined(countByField(members, em -> Boolean.FALSE.equals(em.getAttendanceConfirmed())))
                    .checkedIn(countByStage(members, "CHECKED_IN"))
                    .capacity(100) // Default capacity - would come from venue configuration
                    .utilizationRate(members.size() / 100.0) // Utilization rate based on capacity
                    .build();

            venueStats.put(venue, stats);
        }

        return venueStats;
    }

    private NotificationTemplate getOrCreateBmmInvitationTemplate() {
        return notificationTemplateRepository.findByTemplateCode("BMM_INVITATION")
                .orElseGet(() -> createDefaultBmmInvitationTemplate());
    }

    private NotificationTemplate getOrCreateBmmConfirmationTemplate() {
        return notificationTemplateRepository.findByTemplateCode("BMM_CONFIRMATION")
                .orElseGet(() -> createDefaultBmmConfirmationTemplate());
    }

    private NotificationTemplate getOrCreateSpecialVoteTemplate() {
        return notificationTemplateRepository.findByTemplateCode("BMM_SPECIAL_VOTE")
                .orElseGet(() -> createDefaultSpecialVoteTemplate());
    }

    private NotificationTemplate getOrCreateTicketTemplate() {
        return notificationTemplateRepository.findByTemplateCode("BMM_TICKET")
                .orElseGet(() -> createDefaultTicketTemplate());
    }

    private NotificationTemplate createDefaultBmmInvitationTemplate() {
        return NotificationTemplate.builder()
                .templateCode("BMM_INVITATION")
                .name("BMM Stage 1 Invitation")
                .templateType(NotificationTemplate.TemplateType.BOTH)
                .subject("BMM Meeting Invitation - {{eventName}} ({{regionDesc}})")
                .content("Dear {{name}},\n\nYou are invited to participate in the BMM meeting for {{regionDesc}}.\n\nPlease visit the following link to submit your preferences:\n{{bmmLink}}\n\nBest regards,\nETU Team")
                .isActive(true)
                .build();
    }

    private NotificationTemplate createDefaultBmmConfirmationTemplate() {
        return NotificationTemplate.builder()
                .templateCode("BMM_CONFIRMATION")
                .name("BMM Stage 2 Confirmation")
                .templateType(NotificationTemplate.TemplateType.BOTH)
                .subject("BMM Meeting Venue Assignment - Please Confirm")
                .content("Dear {{name}},\n\nYou have been assigned to the following BMM meeting:\n\nVenue: {{venue}}\nDate & Time: {{dateTime}}\n\nPlease confirm your attendance:\n{{confirmationLink}}\n\nBest regards,\nETU Team")
                .isActive(true)
                .build();
    }

    private NotificationTemplate createDefaultSpecialVoteTemplate() {
        return NotificationTemplate.builder()
                .templateCode("BMM_SPECIAL_VOTE")
                .name("BMM Special Vote Link")
                .templateType(NotificationTemplate.TemplateType.BOTH)
                .subject("BMM Special Vote - Access Link")
                .content("Dear {{name}},\n\nYour special vote request has been approved. Please use the following link to cast your vote:\n{{specialVoteLink}}\n\nBest regards,\nETU Team")
                .isActive(true)
                .build();
    }

    private NotificationTemplate createDefaultTicketTemplate() {
        return NotificationTemplate.builder()
                .templateCode("BMM_TICKET")
                .name("BMM Meeting Ticket")
                .templateType(NotificationTemplate.TemplateType.BOTH)
                .subject("BMM Meeting Ticket - {{venue}}")
                .content("Dear {{name}},\n\nYour BMM meeting ticket is ready:\n\nVenue: {{venue}}\nDate & Time: {{dateTime}}\nTicket ID: {{ticketToken}}\n\nDownload your ticket: {{ticketLink}}\n\nBest regards,\nETU Team")
                .isActive(true)
                .build();
    }
}