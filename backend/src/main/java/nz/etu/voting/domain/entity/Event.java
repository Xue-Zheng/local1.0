package nz.etu.voting.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@ToString(exclude = {"eventMembers"})
@EqualsAndHashCode(exclude = {"eventMembers"})

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "event_code", nullable = false, unique = true)
    private String eventCode;

    @Column(name = "dataset_id", nullable = false)
    private String datasetId;

    @Column(name = "attendee_dataset_id")
    private String attendeeDatasetId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType = EventType.GENERAL_MEETING;

    @Column(name = "event_date")
    private LocalDateTime eventDate;

    @Column(name = "venue")
    private String venue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_voting_enabled", nullable = false)
    private Boolean isVotingEnabled = false;

    @Column(name = "registration_open", nullable = false)
    private Boolean registrationOpen = true;

    @Column(name = "max_attendees")
    private Integer maxAttendees;

    //    事件模板关联
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_template_id")
    private EventTemplate eventTemplate; // 关联的事件模板

    //    动态配置（覆盖模板设置）
    @Column(name = "custom_landing_page_title")
    private String customLandingPageTitle;

    @Column(name = "custom_landing_page_description", columnDefinition = "TEXT")
    private String customLandingPageDescription;

    @Column(name = "custom_registration_instructions", columnDefinition = "TEXT")
    private String customRegistrationInstructions;

    @Column(name = "custom_email_template", columnDefinition = "TEXT")
    private String customEmailTemplate;

    @Column(name = "override_template_settings")
    @JdbcTypeCode(SqlTypes.JSON)
    private String overrideTemplateSettings; // 覆盖模板的特定设置

//三个Informer数据源链接

    //    1. Event Attendees 数据链接
    @Column(name = "informer_attendee_url", columnDefinition = "TEXT")
    private String informerAttendeeUrl; // 1.20252026-fy-event-attendees-dataset

    //    2. Financial Declaration - Email Members 数据链接
    @Column(name = "informer_email_members_url", columnDefinition = "TEXT")
    private String informerEmailMembersUrl; // clara-financial-declaration-all-active-e-tu-members-email-on-file

    //    3. Financial Declaration - SMS Only Members 数据链接
    @Column(name = "informer_sms_members_url", columnDefinition = "TEXT")
    private String informerSmsMembersUrl; // clara-financial-declaration-all-active-e-tu-members-sms-only-no-email

    //    同步控制
    @Column(name = "auto_sync_enabled")
    private Boolean autoSyncEnabled = false; // 是否自动同步

    @Column(name = "sync_schedule")
    private String syncSchedule; // 同步计划 cron表达式

    @Column(name = "last_attendee_sync_time")
    private LocalDateTime lastAttendeeSyncTime; // 最后一次Attendee同步时间

    @Column(name = "last_email_members_sync_time")
    private LocalDateTime lastEmailMembersSyncTime; // 最后一次Email会员同步时间

    @Column(name = "last_sms_members_sync_time")
    private LocalDateTime lastSmsMembersSyncTime; // 最后一次SMS会员同步时间

    // ===== 展示控制和模板配置 =====
    @Column(name = "is_registration_enabled")
    private Boolean isRegistrationEnabled = true; // 是否可注册

    @Column(name = "display_template")
    private String displayTemplate = "DEFAULT"; // 显示模板类型

    @Column(name = "event_config")
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventConfig; // 事件配置

    @Column(name = "custom_fields")
    @JdbcTypeCode(SqlTypes.JSON)
    private String customFields; // 自定义字段配置

    @Column(name = "page_content", columnDefinition = "TEXT")
    private String pageContent; // 页面展示内容

    @Column(name = "banner_image_url")
    private String bannerImageUrl; // 横幅图片

    @Column(name = "theme_color")
    private String themeColor; // 主题颜色

    //    注册流程配置
    @Column(name = "registration_flow")
    @JdbcTypeCode(SqlTypes.JSON)
    private String registrationFlow; // 注册流程配置

    //    扫码签到
    @Column(name = "organizer_scan_token")
    private String organizerScanToken; // 组织者扫码令牌

    @Column(name = "qr_scan_enabled")
    private Boolean qrScanEnabled = true; // 是否启用二维码扫描

    @Column(name = "scan_link_expires_at")
    private LocalDateTime scanLinkExpiresAt; // 扫码链接过期时间

    //    地区相关
    @Column(name = "target_regions")
    @JdbcTypeCode(SqlTypes.JSON)
    private String targetRegions; // 目标地区

    @Column(name = "primary_region")
    private String primaryRegion; // 主要地区

    //    统计字段
    @Column(name = "total_invited")
    private Integer totalInvited = 0; // 总邀请人数

    @Column(name = "email_sent_count")
    private Integer emailSentCount = 0; // 已发送邮件数

    @Column(name = "sms_sent_count")
    private Integer smsSentCount = 0; // 已发送短信数

    @Column(name = "attendee_sync_count")
    private Integer attendeeSyncCount = 0; // Attendee同步数量

    @Column(name = "email_members_sync_count")
    private Integer emailMembersSyncCount = 0; // Email会员同步数量

    @Column(name = "sms_members_sync_count")
    private Integer smsMembersSyncCount = 0; // SMS会员同步数量

    //    原有字段保持不变
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus = SyncStatus.PENDING;

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Column(name = "member_sync_count")
    private Integer memberSyncCount = 0;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<EventMember> eventMembers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EventType {
        GENERAL_MEETING,
        SPECIAL_CONFERENCE, // 特殊会议 - 问出席原因
        SURVEY_MEETING, // 调查问卷会议 - manufacturing food sub-industry
        BMM_VOTING, // BMM投票会议 - 五区域扫码 + 特殊投票申请
        BALLOT_VOTING,
        ANNUAL_MEETING,
        WORKSHOP,
        UNION_MEETING
    }

    public enum SyncStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILED,
        PARTIAL
    }
}

