package nz.etu.voting.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSmsRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotBlank(message = "Message is required")
    private String message;

    @NotBlank(message = "Recipient type is required")
    private String recipientType;

    private Map<String, String> variables;

}