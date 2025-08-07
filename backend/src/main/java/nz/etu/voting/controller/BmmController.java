package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.BmmPreferenceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.NonAttendanceRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.FinancialForm;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.FinancialFormRepository;
import nz.etu.voting.service.BmmService;
import nz.etu.voting.service.TicketEmailService;
import nz.etu.voting.service.StratumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BMM Public Controller - Handles BMM member-facing operations
 */
@Slf4j
@RestController
@RequestMapping("/api/bmm")
@CrossOrigin(origins = {"http://localhost:3000", "http://10.0.9.238:3000", "https://events.etu.nz"})
@RequiredArgsConstructor
public class BmmController {

    private final BmmService bmmService;
    private final EventMemberRepository eventMemberRepository;
    private final TicketEmailService ticketEmailService;
    private final StratumService stratumService;
    private final FinancialFormRepository financialFormRepository;

    /**
     * Submit BMM preferences - Stage 1
     */
    @PostMapping("/preferences")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitBmmPreferences(
            @RequestBody Map<String, Object> request) {
        try {
            log.info("Received BMM preferences submission: {}", request);

            String memberToken = (String) request.get("memberToken");
            if (memberToken == null || memberToken.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Member token is required"));
            }

            // Create preference request
            BmmPreferenceRequest preferenceRequest = new BmmPreferenceRequest();
            preferenceRequest.setToken(memberToken);
            preferenceRequest.setPreferredVenues((List<String>) request.get("preferredVenues"));
            preferenceRequest.setPreferredDates((List<String>) request.get("preferredDates"));
            preferenceRequest.setPreferredTimes((List<String>) request.get("preferredTimes"));
            preferenceRequest.setIntendToAttend((Boolean) request.get("intendToAttend"));
            preferenceRequest.setWorkplaceInfo((String) request.get("workplaceInfo"));
            preferenceRequest.setAdditionalComments((String) request.get("additionalComments"));
            preferenceRequest.setSuggestedVenue((String) request.get("suggestedVenue"));
            preferenceRequest.setPreferenceSpecialVote((Boolean) request.get("preferenceSpecialVote"));

            // Save preferences
            bmmService.savePreferencesToEventMember(preferenceRequest);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Preferences submitted successfully");
            response.put("nextStep", "Wait for venue assignment");

            return ResponseEntity.ok(ApiResponse.success("BMM preferences saved successfully", response));

        } catch (Exception e) {
            log.error("Error submitting BMM preferences: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to submit preferences: " + e.getMessage()));
        }
    }

    /**
     * Get available venues for BMM
     */
    @GetMapping("/venues")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBmmVenues() {
        try {
            // Load venues from configuration
            List<Map<String, Object>> venues = loadBmmVenuesConfig();
            return ResponseEntity.ok(ApiResponse.success("Venues retrieved successfully", venues));
        } catch (Exception e) {
            log.error("Error fetching BMM venues: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch venues"));
        }
    }

    /**
     * Check BMM registration status
     */
    @GetMapping("/status/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkBmmStatus(@PathVariable String token) {
        try {
            EventMember member = bmmService.findEventMemberByToken(token);

            Map<String, Object> status = new HashMap<>();
            status.put("stage", member.getBmmStage());
            status.put("memberName", member.getName());
            status.put("membershipNumber", member.getMembershipNumber());
            status.put("region", member.getRegionDesc());
            status.put("hasSubmittedPreferences", member.getPreferenceSubmittedAt() != null);
            status.put("hasVenueAssigned", member.getAssignedVenueFinal() != null);
            status.put("hasConfirmedAttendance", member.getAttendanceConfirmed());
            status.put("ticketGenerated", member.getTicketToken() != null);

            return ResponseEntity.ok(ApiResponse.success("Status retrieved successfully", status));

        } catch (Exception e) {
            log.error("Error checking BMM status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to check status"));
        }
    }

    private List<Map<String, Object>> loadBmmVenuesConfig() {
        List<Map<String, Object>> allVenues = new java.util.ArrayList<>();
        try {
            // Load venues from configuration file
            java.io.InputStream inputStream = getClass().getResourceAsStream("/bmm-venues-config.json");
            if (inputStream != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> config = mapper.readValue(inputStream, Map.class);
                Map<String, Map<String, Object>> regions = (Map<String, Map<String, Object>>) config.get("regions");

                // Extract venues from all regions
                for (Map.Entry<String, Map<String, Object>> regionEntry : regions.entrySet()) {
                    String regionName = regionEntry.getKey();
                    List<Map<String, Object>> regionVenues = (List<Map<String, Object>>) regionEntry.getValue().get("venues");

                    // Add region name to each venue
                    for (Map<String, Object> venue : regionVenues) {
                        venue.put("region", regionName);
                        allVenues.add(venue);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load BMM venues config: {}", e.getMessage());
            // Return sample data as fallback
            Map<String, Object> venue1 = new HashMap<>();
            venue1.put("name", "Auckland Central");
            venue1.put("address", "Alexandra Park – Tasman Room, 233 Green Lane West, Epsom, Auckland");
            venue1.put("region", "Northern Region");
            venue1.put("capacity", 800);
            allVenues.add(venue1);
        }
        return allVenues;
    }

    /**
     * Update financial form data only (without confirming attendance)
     */
    @PostMapping("/update-financial-form")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateFinancialForm(@RequestBody Map<String, Object> request) {
        log.info("🚨🚨🚨 UPDATE-FINANCIAL-FORM ENDPOINT HIT - Starting processing 🚨🚨🚨");
        log.info("🚨 Request body size: {} characters", request != null ? request.toString().length() : 0);

        try {
            log.info("🔥 BMM UPDATE-FINANCIAL-FORM - Request received: {}", request);

            String memberToken = (String) request.get("memberToken");
            Map<String, Object> financialForm = (Map<String, Object>) request.get("financialForm");

            log.info("🔥 BMM UPDATE-FINANCIAL-FORM - memberToken: {}, financialForm present: {}",
                    memberToken, financialForm != null);

            // Validate token and find EventMember
            EventMember eventMember = bmmService.findEventMemberByToken(memberToken);
            if (eventMember == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid token"));
            }

            // Capture before data for audit trail
            String beforeData = "";
            try {
                Map<String, Object> beforeSnapshot = new HashMap<>();
                beforeSnapshot.put("name", eventMember.getName());
                beforeSnapshot.put("address", eventMember.getAddress());
                beforeSnapshot.put("primaryEmail", eventMember.getPrimaryEmail());
                beforeSnapshot.put("phoneHome", eventMember.getPhoneHome());
                beforeSnapshot.put("phoneWork", eventMember.getPhoneWork());
                beforeSnapshot.put("telephoneMobile", eventMember.getTelephoneMobile());
                beforeSnapshot.put("dob", eventMember.getDob());
                beforeSnapshot.put("payrollNumber", eventMember.getPayrollNumber());
                beforeSnapshot.put("siteCode", eventMember.getSiteCode());
                beforeSnapshot.put("employmentStatus", eventMember.getEmploymentStatus());
                beforeSnapshot.put("department", eventMember.getDepartment());
                beforeSnapshot.put("jobTitle", eventMember.getJobTitle());
                beforeSnapshot.put("location", eventMember.getLocation());
                beforeSnapshot.put("employer", eventMember.getEmployer());
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                beforeData = objectMapper.writeValueAsString(beforeSnapshot);
            } catch (Exception e) {
                log.error("Error capturing before data: {}", e.getMessage());
            }

            // Update financial information directly on EventMember
            if (financialForm != null && !financialForm.isEmpty()) {
                log.info("=== UPDATING FINANCIAL FORM DATA FOR MEMBER {} ===", eventMember.getMembershipNumber());
                log.info("Financial form data received: {}", financialForm);

                // Update all financial form fields to EventMember
                if (financialForm.get("name") != null) {
                    String oldName = eventMember.getName();
                    eventMember.setName((String) financialForm.get("name"));
                    log.info("Updated name: {} -> {}", oldName, financialForm.get("name"));
                }
                if (financialForm.get("postalAddress") != null) {
                    String oldAddress = eventMember.getAddress();
                    eventMember.setAddress((String) financialForm.get("postalAddress"));
                    log.info("Updated address: {} -> {}", oldAddress, financialForm.get("postalAddress"));
                }
                if (financialForm.get("phoneHome") != null) {
                    String oldPhone = eventMember.getPhoneHome();
                    eventMember.setPhoneHome((String) financialForm.get("phoneHome"));
                    log.info("Updated phoneHome: {} -> {}", oldPhone, financialForm.get("phoneHome"));
                }
                if (financialForm.get("phoneWork") != null) {
                    String oldWorkPhone = eventMember.getPhoneWork();
                    eventMember.setPhoneWork((String) financialForm.get("phoneWork"));
                    log.info("Updated phoneWork: {} -> {}", oldWorkPhone, financialForm.get("phoneWork"));
                }
                if (financialForm.get("telephoneMobile") != null) {
                    String oldMobile = eventMember.getTelephoneMobile();
                    eventMember.setTelephoneMobile((String) financialForm.get("telephoneMobile"));
                    log.info("Updated telephoneMobile: {} -> {}", oldMobile, financialForm.get("telephoneMobile"));
                }
                if (financialForm.get("primaryEmail") != null) {
                    String oldEmail = eventMember.getPrimaryEmail();
                    eventMember.setPrimaryEmail((String) financialForm.get("primaryEmail"));
                    log.info("Updated primaryEmail: {} -> {}", oldEmail, financialForm.get("primaryEmail"));
                }
                if (financialForm.get("dob") != null) {
                    String dobStr = (String) financialForm.get("dob");
                    if (!dobStr.isEmpty()) {
                        String oldDob = eventMember.getDob();
                        eventMember.setDob(dobStr);
                        log.info("Updated dob: {} -> {}", oldDob, dobStr);
                    }
                }
                if (financialForm.get("payrollNumber") != null) {
                    String oldPayroll = eventMember.getPayrollNumber();
                    eventMember.setPayrollNumber((String) financialForm.get("payrollNumber"));
                    log.info("Updated payrollNumber: {} -> {}", oldPayroll, financialForm.get("payrollNumber"));
                }
                if (financialForm.get("siteCode") != null) {
                    String oldSiteCode = eventMember.getSiteCode();
                    eventMember.setSiteCode((String) financialForm.get("siteCode"));
                    log.info("Updated siteCode: {} -> {}", oldSiteCode, financialForm.get("siteCode"));
                }
                if (financialForm.get("employmentStatus") != null) {
                    String oldStatus = eventMember.getEmploymentStatus();
                    eventMember.setEmploymentStatus((String) financialForm.get("employmentStatus"));
                    log.info("Updated employmentStatus: {} -> {}", oldStatus, financialForm.get("employmentStatus"));
                }
                if (financialForm.get("department") != null) {
                    String oldDept = eventMember.getDepartment();
                    eventMember.setDepartment((String) financialForm.get("department"));
                    log.info("Updated department: {} -> {}", oldDept, financialForm.get("department"));
                }
                if (financialForm.get("jobTitle") != null) {
                    String oldTitle = eventMember.getJobTitle();
                    eventMember.setJobTitle((String) financialForm.get("jobTitle"));
                    log.info("Updated jobTitle: {} -> {}", oldTitle, financialForm.get("jobTitle"));
                }
                if (financialForm.get("location") != null) {
                    String oldLocation = eventMember.getLocation();
                    eventMember.setLocation((String) financialForm.get("location"));
                    log.info("Updated location: {} -> {}", oldLocation, financialForm.get("location"));
                }
                if (financialForm.get("employer") != null) {
                    String oldEmployer = eventMember.getEmployer();
                    eventMember.setEmployer((String) financialForm.get("employer"));
                    log.info("Updated employer: {} -> {}", oldEmployer, financialForm.get("employer"));
                }

                // Save the updated EventMember
                eventMember.setLastActivityAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                log.info("=== FINANCIAL FORM DATA SAVED TO DATABASE ===");

                // Capture after data and create FinancialForm audit record
                try {
                    Map<String, Object> afterSnapshot = new HashMap<>();
                    afterSnapshot.put("name", eventMember.getName());
                    afterSnapshot.put("address", eventMember.getAddress());
                    afterSnapshot.put("primaryEmail", eventMember.getPrimaryEmail());
                    afterSnapshot.put("phoneHome", eventMember.getPhoneHome());
                    afterSnapshot.put("phoneWork", eventMember.getPhoneWork());
                    afterSnapshot.put("telephoneMobile", eventMember.getTelephoneMobile());
                    afterSnapshot.put("dob", eventMember.getDob());
                    afterSnapshot.put("payrollNumber", eventMember.getPayrollNumber());
                    afterSnapshot.put("siteCode", eventMember.getSiteCode());
                    afterSnapshot.put("employmentStatus", eventMember.getEmploymentStatus());
                    afterSnapshot.put("department", eventMember.getDepartment());
                    afterSnapshot.put("jobTitle", eventMember.getJobTitle());
                    afterSnapshot.put("location", eventMember.getLocation());
                    afterSnapshot.put("employer", eventMember.getEmployer());
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String afterData = objectMapper.writeValueAsString(afterSnapshot);

                    // Create FinancialForm audit record
                    FinancialForm financialFormRecord = FinancialForm.builder()
                            .eventMemberId(eventMember.getId())
                            .eventId(eventMember.getEvent().getId())
                            .memberName(eventMember.getName())
                            .membershipNumber(eventMember.getMembershipNumber())
                            .primaryEmail(eventMember.getPrimaryEmail())
                            .telephoneMobile(eventMember.getTelephoneMobile())
                            .beforeData(beforeData)
                            .afterData(afterData)
                            .updatedFields("name,address,primaryEmail,phoneHome,phoneWork,telephoneMobile,dob,payrollNumber,siteCode,employmentStatus,department,jobTitle,location,employer")
                            .formType("BMM_FINANCIAL_UPDATE")
                            .updateSource("WEB")
                            .updatedBy("SYSTEM")
                            .stratumSyncStatus("PENDING")
                            .approvalStatus("APPROVED")
                            .build();

                    log.info("=== ATTEMPTING TO SAVE FINANCIAL FORM RECORD ===");
                    log.info("EventMember ID: {}, Event ID: {}", eventMember.getId(), eventMember.getEvent().getId());
                    log.info("Member Name: {}, Membership Number: {}", eventMember.getName(), eventMember.getMembershipNumber());

                    FinancialForm savedForm = financialFormRepository.save(financialFormRecord);
                    log.info("=== FINANCIAL FORM AUDIT RECORD CREATED WITH ID {} ===", savedForm.getId());

                    // Update EventMember with FinancialForm reference
                    eventMember.setFinancialFormId(savedForm.getId());
                    eventMemberRepository.save(eventMember);
                    log.info("=== EVENTMEMBER UPDATED WITH FINANCIAL FORM ID {} ===", savedForm.getId());

                } catch (Exception e) {
                    log.error("Error creating financial form audit record: {}", e.getMessage());
                    // Continue even if audit record fails
                }

                // Sync to Stratum (failure won't affect the flow)
                try {
                    boolean syncSuccess = stratumService.syncEventMemberToStratum(eventMember);
                    if (syncSuccess) {
                        log.info("Successfully synced EventMember {} to Stratum", eventMember.getMembershipNumber());
                    } else {
                        log.warn("Failed to sync EventMember {} to Stratum, but continuing with the flow", eventMember.getMembershipNumber());
                    }
                } catch (Exception e) {
                    log.error("Error syncing to Stratum for member {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                    // Continue with the flow even if sync fails
                }
            } else {
                log.warn("No financial form data provided for member {}", eventMember.getMembershipNumber());
            }

            // Generate ticket token after updating financial form (but don't send email yet)
            if (eventMember.getTicketToken() == null) {
                eventMember.setTicketToken(UUID.randomUUID());
                eventMember.setTicketStatus("GENERATED");
                eventMember.setTicketGeneratedAt(LocalDateTime.now());
                eventMember.setTicketSentMethod("PENDING");  // Will be updated when email is sent or marked as website-only
                eventMemberRepository.save(eventMember);
                log.info("Generated ticket token for member {} after financial form update", eventMember.getMembershipNumber());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Financial form updated successfully");
            response.put("membershipNumber", eventMember.getMembershipNumber());
            response.put("ticketToken", eventMember.getTicketToken() != null ? eventMember.getTicketToken().toString() : null);

            return ResponseEntity.ok(ApiResponse.success("Financial form updated", response));

        } catch (Exception e) {
            log.error("Error updating financial form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to update financial form: " + e.getMessage()));
        }
    }

    /**
     * Confirm attendance for Stage 2
     */
    @PostMapping("/confirm-attendance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmAttendance(@RequestBody Map<String, Object> request) {
        log.info("🚨🚨🚨 CONFIRM-ATTENDANCE ENDPOINT HIT - Starting processing 🚨🚨🚨");
        log.info("🚨 Request body size: {} characters", request != null ? request.toString().length() : 0);

        try {
            log.info("🔥 BMM CONFIRM-ATTENDANCE ENDPOINT CALLED - Request received: {}", request);

            String memberToken = (String) request.get("memberToken");
            Boolean isAttending = (Boolean) request.get("isAttending");
            Map<String, Object> financialForm = (Map<String, Object>) request.get("financialForm");

            log.info("🔥 BMM CONFIRM-ATTENDANCE - memberToken: {}, isAttending: {}, financialForm present: {}",
                    memberToken, isAttending, financialForm != null);

            // Validate token and find EventMember
            EventMember eventMember = bmmService.findEventMemberByToken(memberToken);
            if (eventMember == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid token"));
            }

            // Update financial information directly on EventMember
            if (financialForm != null && !financialForm.isEmpty()) {
                log.info("=== UPDATING FINANCIAL FORM DATA FOR MEMBER {} ===", eventMember.getMembershipNumber());
                log.info("Financial form data received: {}", financialForm);

                // Update all financial form fields to EventMember
                if (financialForm.get("name") != null) {
                    String oldName = eventMember.getName();
                    eventMember.setName((String) financialForm.get("name"));
                    log.info("Updated name: {} -> {}", oldName, financialForm.get("name"));
                }
                if (financialForm.get("postalAddress") != null) {
                    String oldAddress = eventMember.getAddress();
                    eventMember.setAddress((String) financialForm.get("postalAddress"));
                    log.info("Updated address: {} -> {}", oldAddress, financialForm.get("postalAddress"));
                }
                // employerName is read-only on frontend, not updated
                if (financialForm.get("phoneHome") != null) {
                    String oldPhone = eventMember.getPhoneHome();
                    eventMember.setPhoneHome((String) financialForm.get("phoneHome"));
                    log.info("Updated phoneHome: {} -> {}", oldPhone, financialForm.get("phoneHome"));
                }
                if (financialForm.get("phoneWork") != null) {
                    String oldWorkPhone = eventMember.getPhoneWork();
                    eventMember.setPhoneWork((String) financialForm.get("phoneWork"));
                    log.info("Updated phoneWork: {} -> {}", oldWorkPhone, financialForm.get("phoneWork"));
                }
                if (financialForm.get("telephoneMobile") != null) {
                    String oldMobile = eventMember.getTelephoneMobile();
                    eventMember.setTelephoneMobile((String) financialForm.get("telephoneMobile"));
                    log.info("Updated telephoneMobile: {} -> {}", oldMobile, financialForm.get("telephoneMobile"));
                }
                if (financialForm.get("primaryEmail") != null) {
                    String oldEmail = eventMember.getPrimaryEmail();
                    eventMember.setPrimaryEmail((String) financialForm.get("primaryEmail"));
                    log.info("Updated primaryEmail: {} -> {}", oldEmail, financialForm.get("primaryEmail"));
                }
                if (financialForm.get("dob") != null) {
                    String dobStr = (String) financialForm.get("dob");
                    if (!dobStr.isEmpty()) {
                        String oldDob = eventMember.getDob();
                        eventMember.setDob(dobStr);
                        log.info("Updated dob: {} -> {}", oldDob, dobStr);
                    }
                }
                if (financialForm.get("payrollNumber") != null) {
                    String oldPayroll = eventMember.getPayrollNumber();
                    eventMember.setPayrollNumber((String) financialForm.get("payrollNumber"));
                    log.info("Updated payrollNumber: {} -> {}", oldPayroll, financialForm.get("payrollNumber"));
                }
                if (financialForm.get("siteCode") != null) {
                    String oldSiteCode = eventMember.getSiteCode();
                    eventMember.setSiteCode((String) financialForm.get("siteCode"));
                    log.info("Updated siteCode: {} -> {}", oldSiteCode, financialForm.get("siteCode"));
                }
                if (financialForm.get("employmentStatus") != null) {
                    String oldStatus = eventMember.getEmploymentStatus();
                    eventMember.setEmploymentStatus((String) financialForm.get("employmentStatus"));
                    log.info("Updated employmentStatus: {} -> {}", oldStatus, financialForm.get("employmentStatus"));
                }
                if (financialForm.get("department") != null) {
                    String oldDept = eventMember.getDepartment();
                    eventMember.setDepartment((String) financialForm.get("department"));
                    log.info("Updated department: {} -> {}", oldDept, financialForm.get("department"));
                }
                if (financialForm.get("jobTitle") != null) {
                    String oldTitle = eventMember.getJobTitle();
                    eventMember.setJobTitle((String) financialForm.get("jobTitle"));
                    log.info("Updated jobTitle: {} -> {}", oldTitle, financialForm.get("jobTitle"));
                }
                if (financialForm.get("location") != null) {
                    String oldLocation = eventMember.getLocation();
                    eventMember.setLocation((String) financialForm.get("location"));
                    log.info("Updated location: {} -> {}", oldLocation, financialForm.get("location"));
                }
                if (financialForm.get("employer") != null) {
                    String oldEmployer = eventMember.getEmployer();
                    eventMember.setEmployer((String) financialForm.get("employer"));
                    log.info("Updated employer: {} -> {}", oldEmployer, financialForm.get("employer"));
                }
                // membershipNumber is read-only, not updated
                log.info("=== FINANCIAL FORM DATA UPDATE COMPLETED ===");
            } else {
                log.warn("No financial form data provided for member {}", eventMember.getMembershipNumber());
            }

            // Update attendance status
            eventMember.setIsAttending(true);
            eventMember.setAttendanceConfirmed(true);
            eventMember.setAttendanceDecisionMadeAt(LocalDateTime.now());
            eventMember.setBmmStage("ATTENDANCE_CONFIRMED");
            eventMember.setBmmRegistrationStage("ATTENDANCE_CONFIRMED"); // CRITICAL: Set the correct field for ticket validation
            eventMember.setLastActivityAt(LocalDateTime.now());

            // Auto-assign venue for members who haven't completed Stage 1
            if (eventMember.getAssignedVenueFinal() == null || eventMember.getAssignedVenueFinal().isEmpty()) {
                log.info("Member {} has no assigned venue, auto-assigning based on forum", eventMember.getMembershipNumber());

                // Assign venue based on forumDesc
                String forumDesc = eventMember.getForumDesc();
                if (forumDesc != null && !forumDesc.isEmpty()) {
                    eventMember.setAssignedVenueFinal(forumDesc);
                    eventMember.setAssignedVenue(forumDesc);
                    log.info("Assigned venue {} to member {} based on forum", forumDesc, eventMember.getMembershipNumber());

                    // Assign region if not set
                    if (eventMember.getAssignedRegion() == null || eventMember.getAssignedRegion().isEmpty()) {
                        eventMember.setAssignedRegion(eventMember.getRegionDesc());
                    }
                } else {
                    log.warn("Member {} has no forum assignment, cannot auto-assign venue", eventMember.getMembershipNumber());
                }
            }

            eventMemberRepository.save(eventMember);

            // Create FinancialForm audit record if financial data was provided
            if (financialForm != null && !financialForm.isEmpty()) {
                try {
                    Map<String, Object> afterSnapshot = new HashMap<>();
                    afterSnapshot.put("name", eventMember.getName());
                    afterSnapshot.put("address", eventMember.getAddress());
                    afterSnapshot.put("primaryEmail", eventMember.getPrimaryEmail());
                    afterSnapshot.put("phoneHome", eventMember.getPhoneHome());
                    afterSnapshot.put("phoneWork", eventMember.getPhoneWork());
                    afterSnapshot.put("telephoneMobile", eventMember.getTelephoneMobile());
                    afterSnapshot.put("dob", eventMember.getDob());
                    afterSnapshot.put("payrollNumber", eventMember.getPayrollNumber());
                    afterSnapshot.put("siteCode", eventMember.getSiteCode());
                    afterSnapshot.put("employmentStatus", eventMember.getEmploymentStatus());
                    afterSnapshot.put("department", eventMember.getDepartment());
                    afterSnapshot.put("jobTitle", eventMember.getJobTitle());
                    afterSnapshot.put("location", eventMember.getLocation());
                    afterSnapshot.put("employer", eventMember.getEmployer());
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    String afterData = objectMapper.writeValueAsString(afterSnapshot);

                    // Create FinancialForm audit record
                    FinancialForm financialFormRecord = FinancialForm.builder()
                            .eventMemberId(eventMember.getId())
                            .eventId(eventMember.getEvent().getId())
                            .memberName(eventMember.getName())
                            .membershipNumber(eventMember.getMembershipNumber())
                            .primaryEmail(eventMember.getPrimaryEmail())
                            .telephoneMobile(eventMember.getTelephoneMobile())
                            .beforeData("{}") // Empty for confirm-attendance
                            .afterData(afterData)
                            .updatedFields("name,address,primaryEmail,phoneHome,phoneWork,telephoneMobile,dob,payrollNumber,siteCode,employmentStatus,department,jobTitle,location,employer")
                            .formType("BMM_ATTENDANCE_CONFIRMATION")
                            .updateSource("WEB")
                            .updatedBy("SYSTEM")
                            .stratumSyncStatus("PENDING")
                            .approvalStatus("APPROVED")
                            .build();

                    log.info("=== CREATING FINANCIAL FORM RECORD IN CONFIRM-ATTENDANCE ===");
                    FinancialForm savedForm = financialFormRepository.save(financialFormRecord);
                    log.info("=== FINANCIAL FORM RECORD CREATED WITH ID {} ===", savedForm.getId());

                    // Update EventMember with FinancialForm reference
                    eventMember.setFinancialFormId(savedForm.getId());
                    eventMemberRepository.save(eventMember);

                } catch (Exception e) {
                    log.error("Error creating financial form record in confirm-attendance: {}", e.getMessage());
                    // Continue even if audit record fails
                }
            }

            // Sync to Stratum (failure won't affect the flow)
            try {
                boolean syncSuccess = stratumService.syncEventMemberToStratum(eventMember);
                if (syncSuccess) {
                    log.info("Successfully synced EventMember {} to Stratum", eventMember.getMembershipNumber());
                } else {
                    log.warn("Failed to sync EventMember {} to Stratum, but continuing with the flow", eventMember.getMembershipNumber());
                }
            } catch (Exception e) {
                log.error("Error syncing to Stratum for member {}: {}", eventMember.getMembershipNumber(), e.getMessage());
                // Continue with the flow even if sync fails
            }

            // Auto-send confirmation ticket email/SMS
            log.info("Triggering BMM ticket email for member {} after attendance confirmation", eventMember.getMembershipNumber());
            ticketEmailService.sendBMMTicketOnConfirmation(eventMember);
            log.info("BMM ticket email triggered successfully for member {}", eventMember.getMembershipNumber());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Attendance confirmed successfully");
            response.put("ticketSent", true);
            response.put("stage", "ATTENDANCE_CONFIRMED");
            response.put("ticketToken", eventMember.getTicketToken() != null ? eventMember.getTicketToken().toString() : null);

            return ResponseEntity.ok(ApiResponse.success("Attendance confirmed", response));

        } catch (Exception e) {
            log.error("Error confirming attendance: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to confirm attendance: " + e.getMessage()));
        }
    }

    /**
     * Record non-attendance for Stage 2
     */
    @PostMapping("/non-attendance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordNonAttendance(@RequestBody Map<String, Object> request) {
        try {
            String memberToken = (String) request.get("memberToken");
            String absenceReason = (String) request.get("absenceReason");
            Boolean isSpecialVote = (Boolean) request.get("isSpecialVote");
            String specialVoteReason = (String) request.get("specialVoteReason");

            // Validate token and find EventMember
            EventMember eventMember = bmmService.findEventMemberByToken(memberToken);
            if (eventMember == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid token"));
            }

            // Update non-attendance
            eventMember.setIsAttending(false);
            eventMember.setAttendanceConfirmed(true);
            eventMember.setAbsenceReason(absenceReason);
            eventMember.setAttendanceDecisionMadeAt(LocalDateTime.now());
            eventMember.setBmmStage("NOT_ATTENDING");
            eventMember.setLastActivityAt(LocalDateTime.now());

            // Handle special vote request for Central/Southern regions
            // Check if member is eligible (Central/Southern region OR Greymouth special case)
            String region = eventMember.getRegionDesc();
            String forum = eventMember.getForumDesc();
            boolean isEligibleForSpecialVote = (region != null &&
                    (region.contains("Central") || region.contains("Southern"))) ||
                    "Greymouth".equals(forum);

            if (Boolean.TRUE.equals(isSpecialVote) && isEligibleForSpecialVote) {
                eventMember.setSpecialVoteEligible(true); // Set eligibility flag
                eventMember.setSpecialVoteRequested(true); // This is the actual request flag
                eventMember.setSpecialVoteApplicationReason(specialVoteReason);
                eventMember.setSpecialVoteAppliedAt(LocalDateTime.now());
                eventMember.setBmmSpecialVoteStatus("APPROVED"); // Auto-approve all special votes
                log.info("Special vote approved for member {} from region/forum {}/{}",
                        eventMember.getMembershipNumber(), region, forum);
            }

            eventMemberRepository.save(eventMember);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Non-attendance recorded");
            response.put("stage", "NOT_ATTENDING");

            // Different messages based on region
            if ("Northern Region".equals(eventMember.getRegionDesc())) {
                response.put("followUp", "Thank you for letting us know.");
            } else if (Boolean.TRUE.equals(isSpecialVote)) {
                response.put("followUp", "Your special vote has been approved.");
                response.put("specialVoteStatus", "APPROVED");
            } else {
                response.put("followUp", "Thank you for letting us know. You can still apply for special vote if eligible.");
            }

            return ResponseEntity.ok(ApiResponse.success("Non-attendance recorded", response));

        } catch (Exception e) {
            log.error("Error recording non-attendance: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to record non-attendance: " + e.getMessage()));
        }
    }

    /**
     * Handle cancelled venue response - for West Coast venues
     */
    @PostMapping("/cancelled-venue-response")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleCancelledVenueResponse(@RequestBody Map<String, Object> request) {
        try {
            log.info("Cancelled venue response received: {}", request);

            String memberToken = (String) request.get("memberToken");
            Boolean isSpecialVote = (Boolean) request.get("isSpecialVote");
            String specialVoteReason = (String) request.get("specialVoteReason");

            // Validate token and find EventMember
            EventMember eventMember = bmmService.findEventMemberByToken(memberToken);
            if (eventMember == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid token"));
            }

            // Mark as not attending (since venue was cancelled)
            eventMember.setIsAttending(false);
            eventMember.setAttendanceConfirmed(true);
            eventMember.setAbsenceReason("Venue cancelled");
            eventMember.setAttendanceDecisionMadeAt(LocalDateTime.now());
            eventMember.setBmmStage("VENUE_CANCELLED");
            eventMember.setLastActivityAt(LocalDateTime.now());

            // Handle special vote request
            if (Boolean.TRUE.equals(isSpecialVote)) {
                eventMember.setSpecialVoteRequested(true);
                eventMember.setSpecialVoteApplicationReason(specialVoteReason);
                eventMember.setSpecialVoteAppliedAt(LocalDateTime.now());
                eventMember.setBmmSpecialVoteStatus("APPROVED"); // Auto-approve for cancelled venues
                log.info("Special vote requested and auto-approved for member {} due to venue cancellation",
                        eventMember.getMembershipNumber());
            } else {
                eventMember.setSpecialVoteRequested(false);
                log.info("Member {} opted not to request special vote", eventMember.getMembershipNumber());
            }

            eventMemberRepository.save(eventMember);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Response recorded successfully");
            response.put("specialVoteRequested", isSpecialVote);

            if (Boolean.TRUE.equals(isSpecialVote)) {
                response.put("specialVoteStatus", "APPROVED");
                response.put("followUp", "Your special vote has been approved due to venue cancellation.");
            }

            return ResponseEntity.ok(ApiResponse.success("Cancelled venue response recorded", response));

        } catch (Exception e) {
            log.error("Error handling cancelled venue response: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to process response: " + e.getMessage()));
        }
    }
}