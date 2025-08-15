package nz.etu.voting.domain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BmmPreferenceRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotNull(message = "Preferred venues cannot be null")
    private List<String> preferredVenues; // Single venue selection

    @NotNull(message = "Preferred dates cannot be null")
    private List<String> preferredDates; // Multiple dates allowed

    @NotNull(message = "Preferred times cannot be null")
    private List<String> preferredTimes; // Multiple times allowed

    private Boolean intendToAttend; // 初步意向（可以为null）

    private String workplaceInfo;

    private String suggestedVenue;

    private String additionalComments;

    private Boolean preferenceSpecialVote; // Pre-registration阶段的特殊投票意向（仅中南区）
}