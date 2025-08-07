package nz.etu.voting.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BmmAssignmentResponse {

    private String memberName;
    private String membershipNumber;
    private String memberToken;

    // Assignment details
    private String assignedVenue;
    private LocalDateTime assignedDateTime;
    private String assignedRegion;

    // Current stage and status
    private String bmmStage;
    private String ticketStatus;
    private String registrationStatus;

    // Venue assignment metadata
    private LocalDateTime venueAssignedAt;
    private String venueAssignedBy;

    // Ticket information
    private String ticketToken;
    private LocalDateTime ticketGeneratedAt;
    private String ticketPdfPath;

    // Special vote eligibility (for Central/Southern regions)
    private Boolean specialVoteEligible;
    private String specialVotePreference;
    private String bmmSpecialVoteStatus;

    // Communication preferences
    private Boolean hasEmail;
    private Boolean hasMobile;
    private String primaryEmail;
    private String telephoneMobile;

    // Additional information
    private String regionDesc;
    private String workplace;
    private String employer;
}