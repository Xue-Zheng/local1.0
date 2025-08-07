package nz.etu.voting.domain.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginResponse {
    private Long id;
    private String username;
    private String name;
    private String primaryEmail;
    private String role;
    private String token;
}