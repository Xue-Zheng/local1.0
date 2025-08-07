package nz.etu.voting.domain.dto.request;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialFormRequest {
    @NotBlank(message = "Name cannot be empty")
    private String name;
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Invalid email format")
    private String email;
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
    private Boolean isConfirmed;
    private String siteCode;

    // BMM Preferences
    private BmmPreferences bmmPreferences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BmmPreferences {
        private List<String> preferredVenues;    // Venue options within their region
        private List<String> preferredTimes;     // morning, lunchtime, afternoon, after-work, night-shift
        private String attendanceWillingness;    // yes, no
        private String workplaceInfo;            // workplace name and location
        private String meetingFormat;            // in-person, online, hybrid
        private String additionalComments;
        private String suggestedVenue;           // User-suggested venue with details
    }
}