package nz.etu.voting.service.impl;
import lombok.RequiredArgsConstructor;
import nz.etu.voting.domain.dto.request.AttendanceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.VerificationRequest;
import nz.etu.voting.domain.dto.response.MemberResponse;
import nz.etu.voting.domain.entity.FinancialForm;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.exception.ResourceNotFoundException;
import nz.etu.voting.repository.FinancialFormRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.service.MemberService;
import nz.etu.voting.service.StratumService;
import nz.etu.voting.service.TicketEmailService;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import nz.etu.voting.service.EmailService;
import nz.etu.voting.service.SmsService;
import nz.etu.voting.util.VerificationCodeGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final FinancialFormRepository financialFormRepository;
    private final StratumService stratumService;
    private final TicketEmailService ticketEmailService;
    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;
    private final EmailService emailService;
    private final SmsService smsService;

    // Helper method to extract first name from Member
    private String getFirstName(Member member) {
        // First try the fore1 field if available
        if (member.getFore1() != null && !member.getFore1().trim().isEmpty()) {
            return member.getFore1().trim();
        }

        // If fore1 not available, extract from full name
        String fullName = member.getName();
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

    @Override
    public Member findByToken(UUID token) {
        return memberRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found for the token"));
    }
    @Override
    @Transactional
    public MemberResponse verifyMember(VerificationRequest request) {
        UUID token = UUID.fromString(request.getToken());
        Member member = memberRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid token"));
        if (!member.getMembershipNumber().equals(request.getMembershipNumber()) ||
                !member.getVerificationCode().equals(request.getVerificationCode())) {
            throw new ResourceNotFoundException("Membership number or verification code does not match");
        }
        return mapToMemberResponse(member);
    }
    @Override
    public MemberResponse getMemberByToken(UUID token) {
        Member member = findByToken(token);
        return mapToMemberResponse(member);
    }
    @Override
    @Transactional
    public MemberResponse updateMemberFinancialForm(UUID token, FinancialFormRequest request) {
        log.info("🔄 Updating member financial form for token: {}", token);

        Member member = findByToken(token);
        log.info("📋 Found member: {} ({})", member.getName(), member.getMembershipNumber());

        try {
            // 注释：FinancialForm现在应该基于EventMember，不再基于Member
            // BMM流程应该使用BmmService处理Financial表单
            // 这里暂时注释掉旧代码，保持向后兼容
            /*
            FinancialForm form = FinancialForm.builder()
                    .member(member)  // 现在应该是eventMemberId
                    // ... 其他字段
                    .build();
            */

            log.info("📝 MemberServiceImpl: Financial form now handled by BMM process based on EventMember");

            // 直接更新Member信息，不创建FinancialForm
            boolean hasUpdated = !request.getName().equals(member.getName())
                    || !request.getEmail().equals(member.getPrimaryEmail())
                    || (request.getDob() != null && !request.getDob().equals(member.getDob()))
                    || (request.getAddress() != null && !request.getAddress().equals(member.getAddress()))
                    || (request.getPhoneHome() != null && !request.getPhoneHome().equals(member.getPhoneHome()))
                    || (request.getTelephoneMobile() != null && !request.getTelephoneMobile().equals(member.getTelephoneMobile()))
                    || (request.getPhoneWork() != null && !request.getPhoneWork().equals(member.getPhoneWork()))
                    || (request.getEmployer() != null && !request.getEmployer().equals(member.getEmployer()))
                    || (request.getPayrollNumber() != null && !request.getPayrollNumber().equals(member.getPayrollNumber()))
                    || (request.getSiteNumber() != null && !request.getSiteNumber().equals(member.getSiteNumber()))
                    || (request.getEmploymentStatus() != null && !request.getEmploymentStatus().equals(member.getEmploymentStatus()))
                    || (request.getDepartment() != null && !request.getDepartment().equals(member.getDepartment()))
                    || (request.getJobTitle() != null && !request.getJobTitle().equals(member.getJobTitle()))
                    || (request.getLocation() != null && !request.getLocation().equals(member.getLocation()));

            log.info("📝 Member information hasUpdated: {}", hasUpdated);

            // 🔄 Update Member information
            log.info("🔄 Updating Member information...");
            member.setName(request.getName());
            member.setPrimaryEmail(request.getEmail());
            if (request.getDob() != null) {
                member.setDob(request.getDob().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                member.setDobLegacy(request.getDob());
            }
            member.setAddress(request.getAddress());
            member.setPhoneHome(request.getPhoneHome());
            member.setTelephoneMobile(request.getTelephoneMobile());
            if (request.getTelephoneMobile() != null && !request.getTelephoneMobile().trim().isEmpty()) {
                member.setHasMobile(true);
            } else {
                member.setHasMobile(false);
            }
            log.info("📱 Updated mobile phone for member {}: {}",
                    member.getMembershipNumber(), request.getTelephoneMobile());

            member.setPhoneWork(request.getPhoneWork());
            member.setEmployer(request.getEmployer());
            member.setPayrollNumber(request.getPayrollNumber());
            member.setSiteNumber(request.getSiteNumber());
            member.setEmploymentStatus(request.getEmploymentStatus());
            member.setDepartment(request.getDepartment());
            member.setJobTitle(request.getJobTitle());
            member.setLocation(request.getLocation());
            member.setHasRegistered(true);
            member.setUpdatedAt(LocalDateTime.now());

            // 💾 Save Member information
            Member savedMember = memberRepository.save(member);
            log.info("✅ Member information updated successfully: {}", savedMember.getMembershipNumber());

            // 注释：不再需要从Member同步到EventMember，应该直接使用EventMember表
            // syncFinancialDataToEventMembers(savedMember);

            // 🗳️ Handle BMM preferences if provided
            if (request.getBmmPreferences() != null) {
                handleBmmPreferences(savedMember, request.getBmmPreferences());
            }

            //  Sync to Stratum (separate transaction to avoid rollback)
            try {
                boolean syncResult = stratumService.syncMemberToStratum(savedMember);
                if (syncResult) {
                    log.info("✅ Member information successfully synchronized to Stratum: {}",
                            savedMember.getMembershipNumber());
                } else {
                    log.warn("⚠️ Failed to synchronize to Stratum, but local database has been updated: {}",
                            savedMember.getMembershipNumber());
                }
            } catch (Exception stratumError) {
                log.error("❌ Failed to synchronize to Stratum (local data saved successfully): {}",
                        stratumError.getMessage(), stratumError);
                // Don't throw exception here - local data is already saved
            }

            log.info("🎯 Member financial form update completed successfully for: {}",
                    savedMember.getMembershipNumber());
            return mapToMemberResponse(savedMember);

        } catch (Exception e) {
            log.error("❌ Failed to update member financial form for {}: {}",
                    member.getMembershipNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to update member financial form: " + e.getMessage(), e);
        }
    }
    @Override
    @Transactional
    public MemberResponse updateAttendanceChoice(UUID token, AttendanceRequest request) {
        Member member = findByToken(token);
        if (!member.getHasRegistered()) {
            throw new IllegalStateException("Member has not completed registration, cannot update attendance choice");
        }

//        防止意外的"不出席"状态：确保这是明确的选择
        log.info("Updating attendance for member {}: isAttending={}, hasAbsenceReason={}",
                member.getMembershipNumber(),
                request.getIsAttending(),
                request.getAbsenceReason() != null);

        if (!request.getIsAttending() && request.getAbsenceReason() != null) {
            member.setAbsenceReason(request.getAbsenceReason());
        }

        // isSpecialMember functionality removed - all members use regular flow
        {

            if (request.getIsAttending()) {
                member.setIsAttending(true);
                member.setIsSpecialVote(false);
            } else {
                member.setIsAttending(false);
                member.setIsSpecialVote(request.getIsSpecialVote() != null && request.getIsSpecialVote());
            }
            log.info("Member attendance updated: isAttending={}, isSpecialVote={}",
                    request.getIsAttending(),
                    request.getIsSpecialVote() != null ? request.getIsSpecialVote() : false);
        }

        memberRepository.save(member);
        return mapToMemberResponse(member);
    }
    private MemberResponse mapToMemberResponse(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .name(member.getName())
                .primaryEmail(member.getPrimaryEmail())
                .membershipNumber(member.getMembershipNumber())
                .dob(member.getDobLegacy())
                .address(member.getAddress())
                .phoneHome(member.getPhoneHome())
                .telephoneMobile(member.getTelephoneMobile())
                .phoneWork(member.getPhoneWork())
                .employer(member.getEmployer())
                .payrollNumber(member.getPayrollNumber())
                .siteNumber(member.getSiteNumber())
                .employmentStatus(member.getEmploymentStatus())
                .department(member.getDepartment())
                .jobTitle(member.getJobTitle())
                .location(member.getLocation())
                .hasRegistered(member.getHasRegistered())
                .isAttending(member.getIsAttending())
                .isSpecialVote(member.getIsSpecialVote())
                .checkinTime(member.getCheckinTime())
                .hasEmail(member.getHasEmail())
                .hasMobile(member.getHasMobile())
                .verificationCode(member.getVerificationCode())
                .token(member.getToken() != null ? member.getToken().toString() : null)
                .absenceReason(member.getAbsenceReason())
                .build();
    }

    /**
     * Sync financial data from Member to EventMember table
     * This ensures EventMember table has the latest financial information for BMM process
     */
    private void syncFinancialDataToEventMembers(Member member) {
        try {
            log.info("🔄 Syncing financial data from Member to EventMember for: {}", member.getMembershipNumber());

            // Find all EventMembers for this member
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber());

            for (EventMember em : eventMembers) {
                // Sync all financial fields from Member to EventMember
                em.setName(member.getName());
                em.setPrimaryEmail(member.getPrimaryEmail());
                em.setTelephoneMobile(member.getTelephoneMobile());
                em.setAddress(member.getAddress());
                em.setPhoneHome(member.getPhoneHome());
                em.setPhoneWork(member.getPhoneWork());
                em.setEmployer(member.getEmployer());
                em.setPayrollNumber(member.getPayrollNumber());
                em.setSiteCode(member.getSiteNumber());
                em.setEmploymentStatus(member.getEmploymentStatus());
                em.setDepartment(member.getDepartment());
                em.setJobTitle(member.getJobTitle());
                em.setLocation(member.getLocation());
                em.setUpdatedAt(LocalDateTime.now());

                // Save updated EventMember
                eventMemberRepository.save(em);
                log.info("✅ Financial data synced to EventMember for event: {} member: {}",
                        em.getEvent().getId(), member.getMembershipNumber());
            }

        } catch (Exception e) {
            log.error("❌ Failed to sync financial data to EventMember for member {}: {}",
                    member.getMembershipNumber(), e.getMessage(), e);
            // Don't throw exception - this is supplementary sync
        }
    }

    /**
     * Handle BMM preferences and save to EventMember registrationData
     */
    private void handleBmmPreferences(Member member, FinancialFormRequest.BmmPreferences preferences) {
        try {
            log.info("🗳️ Processing BMM preferences for member: {}", member.getMembershipNumber());

            // Find EventMember for this member (assuming BMM event)
            EventMember eventMember = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber())
                    .stream()
                    .filter(em -> em.getEvent().getEventType() == Event.EventType.BMM_VOTING)
                    .findFirst()
                    .orElse(null);

            if (eventMember != null) {
                ObjectMapper objectMapper = new ObjectMapper();

                // Create preferences object
                Map<String, Object> preferencesData = new HashMap<>();
                preferencesData.put("preferredVenues", preferences.getPreferredVenues());
                preferencesData.put("preferredTimes", preferences.getPreferredTimes());
                preferencesData.put("attendanceWillingness", preferences.getAttendanceWillingness());
                preferencesData.put("workplaceInfo", preferences.getWorkplaceInfo());
                preferencesData.put("meetingFormat", preferences.getMeetingFormat());
                preferencesData.put("additionalComments", preferences.getAdditionalComments());
                preferencesData.put("suggestedVenue", preferences.getSuggestedVenue());
                preferencesData.put("submittedAt", LocalDateTime.now().toString());
                preferencesData.put("memberRegion", eventMember.getRegionDesc()); // Store member's actual region

                // Save to registrationData JSON field
                String preferencesJson = objectMapper.writeValueAsString(preferencesData);
                eventMember.setRegistrationData(preferencesJson);
                eventMember.setRegistrationStep("PREFERENCES_COLLECTED");
                eventMember.setUpdatedAt(LocalDateTime.now());

                eventMemberRepository.save(eventMember);

                log.info("✅ BMM preferences saved for member: {} in region: {} with venues: {}",
                        member.getMembershipNumber(), eventMember.getRegionDesc(), preferences.getPreferredVenues());
            } else {
                log.warn("⚠️ No BMM EventMember found for member: {}", member.getMembershipNumber());
            }

        } catch (Exception e) {
            log.error("❌ Failed to save BMM preferences for member {}: {}",
                    member.getMembershipNumber(), e.getMessage(), e);
            // Don't throw exception - this is supplementary data
        }
    }

    @Override
    public List<Map<String, Object>> quickSearchMembers(String searchTerm) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Search in Member table first
        List<Member> members = memberRepository.findAll().stream()
                .filter(member ->
                        (member.getMembershipNumber() != null && member.getMembershipNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (member.getFore1() != null && member.getFore1().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (member.getSurname() != null && member.getSurname().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (member.getPrimaryEmail() != null && member.getPrimaryEmail().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (member.getTelephoneMobile() != null && member.getTelephoneMobile().contains(searchTerm))
                )
                .limit(10)
                .collect(Collectors.toList());

        for (Member member : members) {
            Map<String, Object> result = new HashMap<>();
            result.put("membershipNumber", member.getMembershipNumber());
            result.put("name", (member.getFore1() != null ? member.getFore1() : "") + " " +
                    (member.getSurname() != null ? member.getSurname() : ""));
            result.put("email", member.getPrimaryEmail());
            result.put("mobile", member.getTelephoneMobile());
            result.put("region", member.getRegionDesc());
            result.put("hasEmail", member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty());
            result.put("hasMobile", member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty());
            result.put("source", "Member");
            results.add(result);
        }

        // Also search in EventMember table for BMM-specific data
        List<EventMember> eventMembers = eventMemberRepository.findAll().stream()
                .filter(em ->
                        (em.getMembershipNumber() != null && em.getMembershipNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (em.getName() != null && em.getName().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (em.getPrimaryEmail() != null && em.getPrimaryEmail().toLowerCase().contains(searchTerm.toLowerCase())) ||
                                (em.getTelephoneMobile() != null && em.getTelephoneMobile().contains(searchTerm))
                )
                .limit(5)
                .collect(Collectors.toList());

        for (EventMember eventMember : eventMembers) {
            // Avoid duplicates
            boolean exists = results.stream()
                    .anyMatch(r -> r.get("membershipNumber") != null &&
                            r.get("membershipNumber").equals(eventMember.getMembershipNumber()));

            if (!exists) {
                Map<String, Object> result = new HashMap<>();
                result.put("membershipNumber", eventMember.getMembershipNumber());
                result.put("name", eventMember.getName());
                result.put("email", eventMember.getPrimaryEmail());
                result.put("mobile", eventMember.getTelephoneMobile());
                result.put("region", eventMember.getRegion());
                result.put("hasEmail", eventMember.getPrimaryEmail() != null && !eventMember.getPrimaryEmail().trim().isEmpty());
                result.put("hasMobile", eventMember.getTelephoneMobile() != null && !eventMember.getTelephoneMobile().trim().isEmpty());
                result.put("source", "EventMember");
                results.add(result);
            }
        }

        return results;
    }

    @Override
    public boolean sendQuickEmailToMember(String membershipNumber, String subject, String content) {
        try {
            // Find EventMember by membership number (BMM context)
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(membershipNumber);
            if (eventMembers.isEmpty()) {
                log.warn("EventMember {} not found", membershipNumber);
                return false;
            }

            EventMember eventMember = eventMembers.get(0);
            if (eventMember.getPrimaryEmail() == null || eventMember.getPrimaryEmail().trim().isEmpty()) {
                log.warn("EventMember {} has no email", membershipNumber);
                return false;
            }

            // Send email using email service with correct parameters
            emailService.sendSimpleEmail(
                    eventMember.getPrimaryEmail(),
                    eventMember.getName(),
                    subject,
                    content
            );

            log.info("Quick email sent to EventMember {}: {}", membershipNumber, subject);
            return true;

        } catch (Exception e) {
            log.error("Failed to send quick email to EventMember {}", membershipNumber, e);
            return false;
        }
    }

    @Override
    public boolean sendQuickSMSToMember(String membershipNumber, String message) {
        // Call the new method with useVariableReplacement=false for backward compatibility
        return sendQuickSMSToMember(membershipNumber, message, false);
    }

    /**
     * Send quick email to member with variable replacement support
     * @param membershipNumber Member's membership number
     * @param subject Email subject
     * @param content Email content (may contain variables like {{name}}, {{membershipNumber}}, etc.)
     * @param useVariableReplacement Whether to perform variable replacement
     * @return true if email sent successfully, false otherwise
     */
    @Override
    public boolean sendQuickEmailToMember(String membershipNumber, String subject, String content, boolean useVariableReplacement) {
        try {
            // Find EventMember by membership number (BMM context)
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(membershipNumber);
            if (eventMembers.isEmpty()) {
                log.warn("EventMember {} not found", membershipNumber);
                return false;
            }

            EventMember eventMember = eventMembers.get(0);
            if (eventMember.getPrimaryEmail() == null || eventMember.getPrimaryEmail().trim().isEmpty()) {
                log.warn("EventMember {} has no email", membershipNumber);
                return false;
            }

            String processedSubject = subject;
            String processedContent = content;

            // Perform variable replacement if requested
            if (useVariableReplacement) {
                Map<String, String> variables = buildVariableMapFromEventMember(eventMember);
                processedSubject = replaceVariables(subject, variables);
                processedContent = replaceVariables(content, variables);
            }

            // Send email directly through email service (Stratum)
            emailService.sendSimpleEmail(
                    eventMember.getPrimaryEmail(),
                    eventMember.getName(),
                    processedSubject,
                    processedContent
            );

            log.info("Quick email sent to EventMember {}: {}", membershipNumber, processedSubject);
            return true;

        } catch (Exception e) {
            log.error("Failed to send quick email to EventMember {}", membershipNumber, e);
            return false;
        }
    }

    /**
     * Send quick email to member with variable replacement and provider support
     * @param membershipNumber Member's membership number
     * @param subject Email subject
     * @param content Email content
     * @param useVariableReplacement Whether to perform variable replacement
     * @param provider Email provider (STRATUM or MAILJET)
     * @return true if email sent successfully, false otherwise
     */
    @Override
    public boolean sendQuickEmailToMember(String membershipNumber, String subject, String content,
                                          boolean useVariableReplacement, String provider) {
        try {
            // Find EventMember by membership number (BMM context)
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(membershipNumber);
            if (eventMembers.isEmpty()) {
                log.warn("EventMember {} not found", membershipNumber);
                return false;
            }

            EventMember eventMember = eventMembers.get(0);
            if (eventMember.getPrimaryEmail() == null || eventMember.getPrimaryEmail().trim().isEmpty()) {
                log.warn("EventMember {} has no email", membershipNumber);
                return false;
            }

            String processedSubject = subject;
            String processedContent = content;

            // Perform variable replacement if requested
            if (useVariableReplacement) {
                Map<String, String> variables = buildVariableMapFromEventMember(eventMember);
                processedSubject = replaceVariables(subject, variables);
                processedContent = replaceVariables(content, variables);
            }

            // Send email through email service with provider selection
            emailService.sendEmailWithProvider(
                    eventMember.getPrimaryEmail(),
                    eventMember.getName(),
                    processedSubject,
                    processedContent,
                    provider
            );

            log.info("Quick email sent to EventMember {} via {}: {}", membershipNumber, provider, processedSubject);
            return true;

        } catch (Exception e) {
            log.error("Failed to send quick email to EventMember {}", membershipNumber, e);
            return false;
        }
    }

    /**
     * Send quick SMS to member with variable replacement support
     * @param membershipNumber Member's membership number
     * @param message SMS message (may contain variables like {{name}}, {{membershipNumber}}, etc.)
     * @param useVariableReplacement Whether to perform variable replacement
     * @return true if SMS sent successfully, false otherwise
     */
    @Override
    public boolean sendQuickSMSToMember(String membershipNumber, String message, boolean useVariableReplacement) {
        try {
            // Find EventMember by membership number (BMM context)
            List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(membershipNumber);
            if (eventMembers.isEmpty()) {
                log.warn("EventMember {} not found", membershipNumber);
                return false;
            }

            EventMember eventMember = eventMembers.get(0);
            if (eventMember.getTelephoneMobile() == null || eventMember.getTelephoneMobile().trim().isEmpty()) {
                log.warn("EventMember {} has no mobile number", membershipNumber);
                return false;
            }

            String processedMessage = message;

            // Perform variable replacement if requested
            if (useVariableReplacement) {
                Map<String, String> variables = buildVariableMapFromEventMember(eventMember);
                processedMessage = replaceVariables(message, variables);
            }

            // Send SMS using SMS service with Stratum XML format
            smsService.sendSms(
                    eventMember.getTelephoneMobile(),
                    membershipNumber,
                    processedMessage
            );

            log.info("Quick SMS sent to EventMember {}: {}", membershipNumber, processedMessage);
            return true;

        } catch (Exception e) {
            log.error("Failed to send quick SMS to EventMember {}", membershipNumber, e);
            return false;
        }
    }

    /**
     * Build variable map for member with all available variables
     * @param member Member entity
     * @return Map containing all variable replacements
     */
    private Map<String, String> buildVariableMap(Member member) {
        Map<String, String> variables = new HashMap<>();

        // Basic member information
        variables.put("name", member.getName() != null ? member.getName() : "");
        variables.put("firstName", getFirstName(member));
        variables.put("membershipNumber", member.getMembershipNumber() != null ? member.getMembershipNumber() : "");
        variables.put("email", member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "");
        variables.put("mobile", member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "");
        variables.put("verificationCode", member.getVerificationCode() != null ? member.getVerificationCode() : "");
        variables.put("region", member.getRegionDesc() != null ? member.getRegionDesc() : "");

        // Generate BMM registration link with token
        if (member.getToken() != null) {
            String registrationLink = "https://events.etu.nz/?token=" + member.getToken();
            try {
                // Try to get event information for the registration link
                List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber());
                if (!eventMembers.isEmpty()) {
                    Long eventId = eventMembers.get(0).getEvent().getId();
                    registrationLink += "&event=" + eventId;
                }
            } catch (Exception e) {
                log.warn("Failed to get event for member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
            variables.put("registrationLink", registrationLink);
        } else {
            variables.put("registrationLink", "https://events.etu.nz/");
        }

        // Additional member status variables
        variables.put("isAttending", String.valueOf(member.getIsAttending() != null ? member.getIsAttending() : false));
        variables.put("isSpecialVote", String.valueOf(member.getIsSpecialVote() != null ? member.getIsSpecialVote() : false));
        variables.put("hasRegistered", String.valueOf(member.getHasRegistered() != null ? member.getHasRegistered() : false));

        return variables;
    }

    /**
     * Build variable map for EventMember with all available variables
     * @param eventMember EventMember entity
     * @return Map containing all variable replacements
     */
    private Map<String, String> buildVariableMapFromEventMember(EventMember eventMember) {
        Map<String, String> variables = new HashMap<>();

        // Basic member information from EventMember
        variables.put("name", eventMember.getName() != null ? eventMember.getName() : "");
        variables.put("firstName", getFirstNameFromEventMember(eventMember));
        variables.put("membershipNumber", eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "");
        variables.put("email", eventMember.getPrimaryEmail() != null ? eventMember.getPrimaryEmail() : "");
        variables.put("mobile", eventMember.getTelephoneMobile() != null ? eventMember.getTelephoneMobile() : "");
        variables.put("verificationCode", eventMember.getVerificationCode() != null ? eventMember.getVerificationCode() : "");
        variables.put("region", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "");

        // Generate BMM registration link with token
        if (eventMember.getToken() != null) {
            String registrationLink = "https://events.etu.nz/?token=" + eventMember.getToken();
            if (eventMember.getEvent() != null) {
                registrationLink += "&event=" + eventMember.getEvent().getId();
            }
            variables.put("registrationLink", registrationLink);
        } else {
            variables.put("registrationLink", "https://events.etu.nz/");
        }

        // Generate BMM preferences result summary
        variables.put("preferencesResult", buildPreferencesResultSummary(eventMember));

        // BMM specific preferences
        variables.put("preferredVenues", eventMember.getPreferredVenues() != null ? eventMember.getPreferredVenues() : "");
        variables.put("preferredDates", eventMember.getPreferredDatesJson() != null ? eventMember.getPreferredDatesJson() : "");
        variables.put("preferredTimes", eventMember.getPreferredTimes() != null ? eventMember.getPreferredTimes() : "");

        // BMM assigned venue and time
        variables.put("assignedVenue", eventMember.getAssignedVenueFinal() != null ? eventMember.getAssignedVenueFinal() :
                (eventMember.getAssignedVenue() != null ? eventMember.getAssignedVenue() : ""));

        if (eventMember.getAssignedDatetimeFinal() != null) {
            variables.put("assignedDateTime", eventMember.getAssignedDatetimeFinal().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        } else if (eventMember.getAssignedDateTime() != null) {
            variables.put("assignedDateTime", eventMember.getAssignedDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        } else {
            variables.put("assignedDateTime", "");
        }

        // BMM Stage
        variables.put("bmmStage", eventMember.getBmmStage() != null ? eventMember.getBmmStage() : "");

        // Generate BMM link based on stage
        variables.put("bmmLink", generateBmmLinkBasedOnStage(eventMember));

        // Generate confirmation link - use memberToken for BMM
        if (eventMember.getMemberToken() != null) {
            variables.put("confirmationLink", "https://events.etu.nz/bmm?token=" + eventMember.getMemberToken());
        } else {
            variables.put("confirmationLink", "https://events.etu.nz/");
        }

        // Special vote link
        if (eventMember.getToken() != null) {
            variables.put("specialVoteLink", "https://events.etu.nz/register/special-vote?token=" + eventMember.getToken());
        } else {
            variables.put("specialVoteLink", "https://events.etu.nz/");
        }

        // Ticket link
        if (eventMember.getTicketToken() != null) {
            variables.put("ticketUrl", "https://events.etu.nz/ticket/" + eventMember.getTicketToken());
            variables.put("ticketLink", "https://events.etu.nz/ticket/" + eventMember.getTicketToken());
        } else {
            variables.put("ticketUrl", "");
            variables.put("ticketLink", "");
        }

        // Member token
        variables.put("memberToken", eventMember.getToken() != null ? eventMember.getToken().toString() : "");

        // Other BMM fields
        variables.put("forumDesc", eventMember.getForumDesc() != null ? eventMember.getForumDesc() : "");
        variables.put("workplaceInfo", eventMember.getWorkplaceInfo() != null ? eventMember.getWorkplaceInfo() : "");
        variables.put("additionalComments", eventMember.getAdditionalComments() != null ? eventMember.getAdditionalComments() : "");

        // Additional member status variables from EventMember
        variables.put("isAttending", String.valueOf(eventMember.getIsAttending() != null ? eventMember.getIsAttending() : false));
        variables.put("isSpecialVote", String.valueOf(eventMember.getIsSpecialVote() != null ? eventMember.getIsSpecialVote() : false));
        variables.put("hasRegistered", String.valueOf(eventMember.getHasRegistered() != null ? eventMember.getHasRegistered() : false));

        return variables;
    }

    /**
     * Extract first name from EventMember
     */
    private String getFirstNameFromEventMember(EventMember eventMember) {
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

    /**
     * Replace variables in content using the same pattern as EmailServiceImpl
     * @param content Content with variables to replace
     * @param variables Map of variable name to value
     * @return Content with variables replaced
     */
    private String replaceVariables(String content, Map<String, String> variables) {
        if (content == null) return "";

        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? toAsciiOnly(entry.getValue()) : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }

        // Convert markdown-style links to HTML links
        result = result.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\">$1</a>");

        return result;
    }

    /**
     * Build preferences result summary from EventMember's registrationData
     * @param eventMember EventMember entity with BMM preferences
     * @return Formatted string showing member's first stage preferences
     */
    private String buildPreferencesResultSummary(EventMember eventMember) {
        try {
            // Check if member has individual preference fields
            if (eventMember.getPreferredVenues() != null && !eventMember.getPreferredVenues().trim().isEmpty()) {
                StringBuilder summary = new StringBuilder();
                summary.append("Your BMM preferences:\n");

                // Venues
                summary.append("Preferred venues: ").append(eventMember.getPreferredVenues()).append("\n");

                // Times
                if (eventMember.getPreferredTimes() != null && !eventMember.getPreferredTimes().trim().isEmpty()) {
                    summary.append("Preferred times: ").append(eventMember.getPreferredTimes()).append("\n");
                }

                // Attendance willingness
                if (eventMember.getAttendanceWillingness() != null) {
                    summary.append("Attendance willingness: ").append(eventMember.getAttendanceWillingness()).append("\n");
                }

                // Meeting format
                if (eventMember.getMeetingFormat() != null) {
                    summary.append("Meeting format preference: ").append(eventMember.getMeetingFormat()).append("\n");
                }

                return summary.toString();
            }

            // Fallback: try to parse from registrationData JSON
            if (eventMember.getRegistrationData() != null && !eventMember.getRegistrationData().trim().isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> preferencesData = objectMapper.readValue(eventMember.getRegistrationData(), Map.class);

                StringBuilder summary = new StringBuilder();
                summary.append("Your BMM preferences:\n");

                if (preferencesData.containsKey("preferredVenues")) {
                    summary.append("Preferred venues: ").append(preferencesData.get("preferredVenues")).append("\n");
                }
                if (preferencesData.containsKey("preferredTimes")) {
                    summary.append("Preferred times: ").append(preferencesData.get("preferredTimes")).append("\n");
                }
                if (preferencesData.containsKey("attendanceWillingness")) {
                    summary.append("Attendance willingness: ").append(preferencesData.get("attendanceWillingness")).append("\n");
                }
                if (preferencesData.containsKey("meetingFormat")) {
                    summary.append("Meeting format preference: ").append(preferencesData.get("meetingFormat")).append("\n");
                }

                return summary.toString();
            }

            return "No BMM preferences submitted yet.";

        } catch (Exception e) {
            log.warn("Failed to build preferences summary for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage());
            return "BMM preferences data unavailable.";
        }
    }

    /**
     * Convert text to ASCII-only characters (same as EmailServiceImpl)
     * @param text Text to convert
     * @return ASCII-only version of the text
     */
    private String toAsciiOnly(String text) {
        if (text == null) return "";

        text = text.replace("ū", "u");
        text = text.replace("ā", "a");
        text = text.replace("ē", "e");
        text = text.replace("ī", "i");
        text = text.replace("ō", "o");
        text = text.replace("Ū", "U");
        text = text.replace("Ā", "A");
        text = text.replace("Ē", "E");
        text = text.replace("Ī", "I");
        text = text.replace("Ō", "O");

        return text.replaceAll("[^\\x00-\\x7F]", "");
    }

    /**
     * Generate BMM link based on member's current stage
     */
    private String generateBmmLinkBasedOnStage(EventMember eventMember) {
        if (eventMember.getToken() == null) {
            return "https://events.etu.nz/";
        }

        String baseUrl = "https://events.etu.nz/bmm/";
        String tokenParam = "?token=" + eventMember.getToken();
        String eventParam = "&event=" + eventMember.getEvent().getId();

        // Determine appropriate stage based on BMM stage (String field)
        String bmmStage = eventMember.getBmmStage();
        if (bmmStage == null || "INVITED".equals(bmmStage)) {
            return baseUrl + "preferences" + tokenParam + eventParam;
        } else if ("PREFERENCE_SUBMITTED".equals(bmmStage) || "VENUE_ASSIGNED".equals(bmmStage)) {
            return baseUrl + "confirmation" + tokenParam + eventParam;
        } else if ("ATTENDANCE_CONFIRMED".equals(bmmStage) || "NOT_ATTENDING".equals(bmmStage)) {
            // If not attending, they might need special vote
            if (eventMember.getIsAttending() != null && !eventMember.getIsAttending()) {
                return "https://events.etu.nz/register/special-vote" + tokenParam;
            }
            return baseUrl + "attendance" + tokenParam + eventParam;
        } else if ("TICKET_ISSUED".equals(bmmStage) || "CHECKED_IN".equals(bmmStage)) {
            return baseUrl + "ticket" + tokenParam + eventParam;
        }

        // Default to preferences page
        return baseUrl + "preferences" + tokenParam + eventParam;
    }
}