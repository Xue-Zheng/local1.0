package nz.etu.voting.domain.dto.request;

import lombok.Data;

@Data
public class AttendanceRequest {
    private String token;
    private Boolean isAttending;
    private String absenceReason;
    private Boolean isSpecialVote;
}