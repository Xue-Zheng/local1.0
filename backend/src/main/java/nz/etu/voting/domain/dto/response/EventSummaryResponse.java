package nz.etu.voting.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nz.etu.voting.domain.entity.Event;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryResponse {

    private Long id;
    private String name;
    private String eventCode;
    private String description;
    private Event.EventType eventType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;
    private String venue;
    private Boolean isActive;
    private Boolean isVotingEnabled;
    private Boolean registrationOpen;
    private Integer maxAttendees;
    private Event.SyncStatus syncStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSyncTime;
    private Integer memberSyncCount;
    private Integer attendeeSyncCount;

    private Integer totalMembers;
    private Integer registeredMembers;
    private Integer attendingMembers;
    private Integer specialVoteMembers;
    private Integer votedMembers;
    private Integer checkedInMembers;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}