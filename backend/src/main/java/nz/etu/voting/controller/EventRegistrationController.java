package nz.etu.voting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/bmm-registration")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
public class EventRegistrationController {

    private final EventMemberRepository eventMemberRepository;
    private final ObjectMapper objectMapper;

    // BMM preferences endpoint that frontend is calling
    @PostMapping("/bmm-preferences/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> saveBmmPreferences(
            @PathVariable String token,
            @RequestBody Map<String, Object> preferences) {
        try {
            log.info("Saving BMM preferences for token: {}", token);

            UUID memberToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            // Save BMM preferences as JSON
            String preferencesJson = objectMapper.writeValueAsString(preferences);
            member.setRegistrationData(preferencesJson);

            // Update BMM stage
            member.setBmmRegistrationStage("PREFERENCE_SUBMITTED");
            member.setHasRegistered(true);
            member.setUpdatedAt(LocalDateTime.now());

            // Save individual preference fields for easy querying
            if (preferences.containsKey("preferredVenues")) {
                member.setPreferredVenues(preferences.get("preferredVenues").toString());
            }
            if (preferences.containsKey("preferredTimes")) {
                member.setPreferredTimes(preferences.get("preferredTimes").toString());
            }
            if (preferences.containsKey("attendanceWillingness")) {
                member.setAttendanceWillingness(preferences.get("attendanceWillingness").toString());
            }
            if (preferences.containsKey("meetingFormat")) {
                member.setMeetingFormat(preferences.get("meetingFormat").toString());
            }
            if (preferences.containsKey("specialVoteEligibility")) {
                member.setSpecialVotePreference(preferences.get("specialVoteEligibility").toString());
            }
            if (preferences.containsKey("workplaceInfo")) {
                member.setWorkplaceInfo(preferences.get("workplaceInfo").toString());
            }
            if (preferences.containsKey("suggestedVenue")) {
                member.setSuggestedVenue(preferences.get("suggestedVenue").toString());
            }
            if (preferences.containsKey("additionalComments")) {
                member.setAdditionalComments(preferences.get("additionalComments").toString());
            }
            if (preferences.containsKey("region")) {
                member.setAssignedRegion(preferences.get("region").toString());
            }

            eventMemberRepository.save(member);

            log.info("BMM preferences saved successfully for member: {}", member.getMembershipNumber());

            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", member.getMembershipNumber());
            response.put("name", member.getName());
            response.put("bmmStage", member.getBmmRegistrationStage());
            response.put("hasRegistered", member.getHasRegistered());

            return ResponseEntity.ok(ApiResponse.success("BMM preferences saved successfully", response));

        } catch (Exception e) {
            log.error("Failed to save BMM preferences: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to save preferences: " + e.getMessage()));
        }
    }

    // Special vote application endpoint
    @PostMapping("/special-vote/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitSpecialVoteApplication(
            @PathVariable String token,
            @RequestBody Map<String, Object> application) {
        try {
            log.info("Processing special vote application for token: {}", token);

            UUID memberToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            // Validation: Only Southern Region members
            if ("Northern Region".equals(member.getRegionDesc()) || "Northern Region".equals(member.getAssignedRegion()) ||
                    "Central Region".equals(member.getRegionDesc()) || "Central Region".equals(member.getAssignedRegion())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Special vote applications are only available for Southern Region members"));
            }

            // Validation: Member must not be attending
            if (member.getIsAttending()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Special vote applications are only for members who cannot attend"));
            }

            // Save special vote application data
            String applicationJson = objectMapper.writeValueAsString(application);
            member.setSpecialVoteDetails(applicationJson);
            member.setSpecialVoteStatus("PENDING");
            member.setSpecialVoteEligible(true);
            member.setIsSpecialVote(true);
            member.setSpecialVoteApplicationDate(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());

            // Save individual fields for easy querying
            if (application.containsKey("eligibilityReason")) {
                member.setSpecialVoteEligibilityReason(application.get("eligibilityReason").toString());
            }
            if (application.containsKey("supportingEvidence")) {
                member.setSpecialVoteReason(application.get("supportingEvidence").toString());
            }

            eventMemberRepository.save(member);

            log.info("Special vote application saved successfully for member: {}", member.getMembershipNumber());

            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", member.getMembershipNumber());
            response.put("name", member.getName());
            response.put("status", member.getSpecialVoteStatus());
            response.put("applicationDate", member.getSpecialVoteApplicationDate());

            return ResponseEntity.ok(ApiResponse.success("Special vote application submitted successfully", response));

        } catch (Exception e) {
            log.error("Failed to process special vote application: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to submit application: " + e.getMessage()));
        }
    }
}