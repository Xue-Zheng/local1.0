package nz.etu.voting.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nz.etu.voting.domain.entity.Event;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    private String name;

    @NotBlank(message = "Event code is required")
    private String eventCode;

    @NotBlank(message = "Dataset ID is required")
    private String datasetId;

    private String attendeeDatasetId;

    private String description;

    private Event.EventType eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    private String venue;

    private Boolean isVotingEnabled;

    private Boolean registrationOpen;

    private Integer maxAttendees;

    private Long eventTemplateId;
}