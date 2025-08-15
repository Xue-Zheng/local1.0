package nz.etu.voting.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nz.etu.voting.domain.entity.Event;

import java.time.LocalDateTime;

/**
 * 轻量级事件摘要DTO，只包含必要的展示字段
 * 用于列表展示，避免加载完整的Event实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventSummaryDTO {

    private Long id;
    private String name;
    private String eventCode;
    private Event.EventType eventType;
    private LocalDateTime eventDate;
    private String venue;
    private Boolean isActive;
    private Boolean registrationOpen;

    // 统计信息
    private Long totalMembers;
    private Long registeredMembers;
    private Long attendingMembers;
    private Long checkedInMembers;

    // 构造函数用于JPQL查询
    public EventSummaryDTO(Long id, String name, String eventCode, Event.EventType eventType,
                           LocalDateTime eventDate, String venue, Boolean isActive, Boolean registrationOpen) {
        this.id = id;
        this.name = name;
        this.eventCode = eventCode;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.venue = venue;
        this.isActive = isActive;
        this.registrationOpen = registrationOpen;
    }
}