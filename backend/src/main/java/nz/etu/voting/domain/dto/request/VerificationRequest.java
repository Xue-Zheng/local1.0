package nz.etu.voting.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotBlank(message = "Membership number cannot be empty")
    private String membershipNumber;

    @NotBlank(message = "Verification code cannot be empty")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String verificationCode;

    @NotBlank(message = "Token cannot be empty")
    private String token;
}