package nz.etu.voting.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BmmStatistics {

    // Overall statistics
    private Long totalMembers;
    private Long preferenceSubmitted;
    private Long venueAssigned;
    private Long attendanceConfirmed;
    private Long attendanceDeclined;
    private Long ticketsIssued;
    private Long checkedIn;

    // Stage-based statistics
    private Long pendingInvitations;
    private Long pendingAssignments;
    private Long pendingConfirmations;
    private Long pendingTickets;

    // Regional breakdown
    private Map<String, RegionalStats> regionalStats;

    // Special vote statistics (Central/Southern regions only)
    private Long specialVoteEligible;
    private Long specialVoteRequested;
    private Long specialVoteApproved;
    private Long specialVoteCompleted;

    // Communication statistics
    private Long invitationEmailsSent;
    private Long confirmationRequestsSent;
    private Long ticketEmailsSent;
    private Long specialVoteLinksSent;

    // Venue assignment statistics
    private Map<String, VenueStats> venueStats;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionalStats {
        private String region;
        private Long totalMembers;
        private Long preferenceSubmitted;
        private Long venueAssigned;
        private Long attendanceConfirmed;
        private Long attendanceDeclined;
        private Long ticketsIssued;
        private Long checkedIn;
        private Long specialVoteEligible;
        private Long specialVoteRequested;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VenueStats {
        private String venue;
        private Long assigned;
        private Long confirmed;
        private Long declined;
        private Long checkedIn;
        private Integer capacity;
        private Double utilizationRate;
    }
}