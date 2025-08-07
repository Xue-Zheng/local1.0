package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Event API Controller - Public endpoints for event member operations
 */
@Slf4j
@RestController
@RequestMapping("/api/event")
@CrossOrigin(origins = {"http://localhost:3000", "http://10.0.9.238:3000", "https://events.etu.nz"})
@RequiredArgsConstructor
public class EventApiController {

    private final EventMemberRepository eventMemberRepository;

    /**
     * Get member information by member token
     * This endpoint is used by BMM frontend pages
     */
    @GetMapping("/member/{memberToken}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberByToken(@PathVariable String memberToken) {
        try {
            log.info("Fetching member info for token: {}", memberToken);

            Optional<EventMember> memberOpt = eventMemberRepository.findByMemberToken(memberToken);

            if (!memberOpt.isPresent()) {
                log.warn("No member found for token: {}", memberToken);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            Map<String, Object> memberData = new HashMap<>();
            memberData.put("id", member.getId());
            memberData.put("name", member.getName());
            memberData.put("email", member.getPrimaryEmail());
            memberData.put("membershipNumber", member.getMembershipNumber());
            memberData.put("regionDesc", member.getRegionDesc());
            memberData.put("forumDesc", member.getForumDesc());
            memberData.put("telephoneMobile", member.getTelephoneMobile());
            memberData.put("memberToken", member.getMemberToken());
            memberData.put("hasRegistered", member.getHasRegistered());
            memberData.put("isAttending", member.getIsAttending());
            memberData.put("absenceReason", member.getAbsenceReason());

            // BMM specific fields
            memberData.put("bmmStage", member.getBmmStage() != null ? member.getBmmStage() : "INVITED");
            memberData.put("preferredVenuesJson", member.getPreferredVenuesJson());
            memberData.put("preferredDatesJson", member.getPreferredDatesJson());
            memberData.put("preferredTimesJson", member.getPreferredTimesJson());
            memberData.put("preferredAttending", member.getPreferredAttending());
            memberData.put("preferenceSpecialVote", member.getPreferenceSpecialVote());
            memberData.put("preferenceSubmittedAt", member.getPreferenceSubmittedAt());
            memberData.put("assignedVenueFinal", member.getAssignedVenueFinal());
            memberData.put("assignedDatetimeFinal", member.getAssignedDatetimeFinal());
            memberData.put("venueAssignedAt", member.getVenueAssignedAt());
            memberData.put("financialFormId", member.getFinancialFormId());
            memberData.put("specialVoteEligible", member.getSpecialVoteEligible());
            memberData.put("specialVoteRequested", member.getSpecialVoteRequested());
            memberData.put("specialVoteApplicationReason", member.getSpecialVoteApplicationReason());
            memberData.put("bmmSpecialVoteStatus", member.getBmmSpecialVoteStatus());
            memberData.put("ticketStatus", member.getTicketStatus());
            memberData.put("ticketGeneratedAt", member.getTicketGeneratedAt());

            // 添加场地配置信息 - 使用forumDesc匹配场地
            List<Map<String, Object>> availableVenues = new ArrayList<>();
            try {
                if (member.getForumDesc() != null) {
                    Map<String, Object> venueInfo = getVenueByForumDesc(member.getForumDesc(), member.getRegionDesc());
                    if (venueInfo != null) {
                        memberData.put("assignedVenue", venueInfo);
                        availableVenues.add(venueInfo);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load venue for forumDesc {}: {}", member.getForumDesc(), e.getMessage());
            }
            // 确保总是返回availableVenues，即使是空数组
            memberData.put("availableVenues", availableVenues);

            return ResponseEntity.ok(ApiResponse.success("Member retrieved successfully", memberData));

        } catch (Exception e) {
            log.error("Error fetching member by token: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve member: " + e.getMessage()));
        }
    }

    /**
     * Helper method to get venue by forumDesc matching from config file
     * @param forumDesc The forum description from member record
     * @param regionDesc The region description as fallback
     * @return Single venue matching the forumDesc, or null if not found
     */
    private Map<String, Object> getVenueByForumDesc(String forumDesc, String regionDesc) {
        try {
            java.io.InputStream inputStream = getClass().getResourceAsStream("/bmm-venues-config.json");
            if (inputStream != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> config = mapper.readValue(inputStream, Map.class);
                Map<String, Map<String, Object>> regions = (Map<String, Map<String, Object>>) config.get("regions");

                // Search through all regions for a venue matching forumDesc
                for (Map.Entry<String, Map<String, Object>> regionEntry : regions.entrySet()) {
                    Map<String, Object> regionData = regionEntry.getValue();
                    List<Map<String, Object>> venues = (List<Map<String, Object>>) regionData.get("venues");

                    if (venues != null) {
                        for (Map<String, Object> venue : venues) {
                            String venueName = (String) venue.get("name");
                            // 精确匹配forumDesc与venue的name字段
                            if (venueName != null && venueName.equalsIgnoreCase(forumDesc)) {
                                // 添加region信息到venue对象
                                venue.put("region", regionEntry.getKey());
                                return venue;
                            }
                        }
                    }
                }

                log.warn("No venue found matching forumDesc: {}, region: {}", forumDesc, regionDesc);
            }
        } catch (Exception e) {
            log.error("Failed to load venue for forumDesc {}: {}", forumDesc, e.getMessage());
        }
        return null;
    }
}