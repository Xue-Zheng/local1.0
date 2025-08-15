package nz.etu.voting.domain.entity;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_progress")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sync_id", unique = true, nullable = false)
    private String syncId;

    @Column(name = "sync_type", nullable = false)
    private String syncType; // EMAIL_MEMBERS, SMS_MEMBERS, ATTENDEES

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    @Column(name = "total_records")
    private Integer totalRecords;

    @Column(name = "processed_records")
    private Integer processedRecords;

    @Column(name = "error_count")
    private Integer errorCount;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "message")
    private String message;

    @Column(name = "created_by")
    private String createdBy;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    public enum SyncStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    @Transient
    public double getProgressPercentage() {
        if (totalRecords == null || totalRecords == 0) {
            return 0.0;
        }
        if (processedRecords == null) {
            return 0.0;
        }
        return (double) processedRecords / totalRecords * 100;
    }
}