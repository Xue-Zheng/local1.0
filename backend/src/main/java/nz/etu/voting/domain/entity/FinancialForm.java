package nz.etu.voting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "financial_forms")
public class FinancialForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联到EventMember，不是Member
    @Column(name = "event_member_id", nullable = false)
    private Long eventMemberId;

    // 更新前的数据（JSON格式）
    @Column(name = "before_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String beforeData;

    // 更新后的数据（JSON格式）
    @Column(name = "after_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String afterData;

    // 更新的字段列表（方便审计）
    @Column(name = "updated_fields", columnDefinition = "TEXT")
    private String updatedFields;

    // Stratum同步状态
    @Column(name = "stratum_sync_status", length = 50)
    private String stratumSyncStatus = "PENDING"; // PENDING, SUCCESS, FAILED

    @Column(name = "stratum_sync_error", columnDefinition = "TEXT")
    private String stratumSyncError;

    @Column(name = "stratum_sync_attempted_at")
    private LocalDateTime stratumSyncAttemptedAt;

    @Column(name = "stratum_sync_succeeded_at")
    private LocalDateTime stratumSyncSucceededAt;

    // 操作者信息
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "update_source", length = 50)
    private String updateSource; // WEB, API, ADMIN, IMPORT

    // IP地址（用于审计）
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    // 时间戳
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 备注
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // 表单类型（用于区分不同的更新场景）
    @Column(name = "form_type", length = 50)
    private String formType = "BMM_REGISTRATION"; // BMM_REGISTRATION, PROFILE_UPDATE, etc.

    // 关联的事件ID（方便查询）
    @Column(name = "event_id")
    private Long eventId;

    // 审批状态（如果需要审批）
    @Column(name = "approval_status", length = 50)
    private String approvalStatus; // PENDING, APPROVED, REJECTED

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // 便于查询的关键字段（从JSON中提取）
    @Column(name = "member_name")
    private String memberName;

    @Column(name = "membership_number")
    private String membershipNumber;

    @Column(name = "primary_email")
    private String primaryEmail;

    @Column(name = "telephone_mobile")
    private String telephoneMobile;

    // 数据版本（用于乐观锁）
    @Version
    @Column(name = "version")
    private Integer version = 0;
}