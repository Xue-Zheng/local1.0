package nz.etu.voting.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "event_member_id")
    private EventMember eventMember;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    private String recipient;

    @Column(name = "recipient_name")
    private String recipientName;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_time")
    private LocalDateTime sentTime;

    @Column(name = "is_successful", nullable = false)
    private Boolean isSuccessful = false;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "template_code")
    private String templateCode;

    @Column(name = "email_type")
    private String emailType;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_username")
    private String adminUsername;

    public enum NotificationType {
        EMAIL,
        SMS,
        AUTO_EMAIL,
        AUTO_SMS
    }
}