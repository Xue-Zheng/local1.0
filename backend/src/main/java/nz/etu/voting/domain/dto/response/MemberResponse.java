package nz.etu.voting.domain.dto.response;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    private Long id;
    private String name;
    private String primaryEmail;
    private String membershipNumber;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dob;
    private String address;
    private String phoneHome;
    private String telephoneMobile;
    private String phoneWork;
    private String employer;
    private String payrollNumber;
    private String siteNumber;
    private String employmentStatus;
    private String department;
    private String jobTitle;
    private String location;
    private Boolean hasRegistered;
    private Boolean isAttending;
    private Boolean isSpecialVote;
    private LocalDateTime checkinTime;
    private Boolean hasEmail;
    private Boolean hasMobile;

    private String verificationCode;
    private String token;

    @Setter
    @Getter
    private String absenceReason;
}