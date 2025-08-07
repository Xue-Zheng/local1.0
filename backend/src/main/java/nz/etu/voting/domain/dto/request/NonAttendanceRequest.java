package nz.etu.voting.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonAttendanceRequest {

    @NotNull(message = "Event member ID is required")
    private Long eventMemberId;

    @NotBlank(message = "Non-attendance reason is required")
    private String nonAttendanceReason;

    // Special vote application fields (only for Central/Southern regions)
    private Boolean requestSpecialVote;

    private String specialVoteReason;

    private String specialVoteEligibilityReason; // DISABILITY, ILLNESS, DISTANCE, WORK_REQUIRED, OTHER

    // For distance-based special vote
    private Double distanceFromVenue;

    // For work-required special vote
    private String employerWorkRequirement;

    // For medical special vote
    private Boolean medicalCertificateProvided;

    private String additionalDetails;
}