package nz.etu.voting.domain.dto.request;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkEmailRequest {
    @NotBlank(message = "Recipient type is required")
    private String recipientType; // all, registered, unregistered, attending, specialVote, voted
    @NotBlank(message = "Subject is required")
    private String subject;
    @NotBlank(message = "Email content is required")
    private String emailContent;
    private boolean useTemplate;

    private String templateId;

    private List<MultipartFile> attachments;

    public List<String> getCustomRecipients;

    private List<String> customRecipients;
}