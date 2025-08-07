package nz.etu.voting.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nz.etu.voting.domain.entity.NotificationLog;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkNotificationRequest {

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @NotNull(message = "Template ID is required")
    private Long templateId;

    @NotNull(message = "Notification type is required")
    private NotificationLog.NotificationType notificationType;

    @NotNull(message = "Recipient type is required")
    private String recipientType; // all, registered, unregistered, attending, special_vote, etc

    private Map<String, String> variables;
}
