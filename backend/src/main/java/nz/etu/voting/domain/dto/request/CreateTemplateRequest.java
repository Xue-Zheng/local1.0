package nz.etu.voting.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nz.etu.voting.domain.entity.NotificationTemplate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTemplateRequest {

    @NotBlank(message = "Template code is required")
    private String templateCode;

    @NotBlank(message = "Template name is required")
    private String name;

    private String description;

    @NotNull(message = "Template type is required")
    private NotificationTemplate.TemplateType templateType;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Content is required")
    private String content;
}