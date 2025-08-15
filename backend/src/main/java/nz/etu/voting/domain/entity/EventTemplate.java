package nz.etu.voting.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_templates")
public class EventTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, unique = true)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Event.EventType eventType;

    @Column(name = "template_description", columnDefinition = "TEXT")
    private String templateDescription;

    //    注册流程配置
    @Column(name = "registration_steps", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String registrationSteps; // 定义该类型会议的注册步骤

    @Column(name = "requires_attendance_confirmation")
    private Boolean requiresAttendanceConfirmation = true;

    @Column(name = "requires_special_vote_option")
    private Boolean requiresSpecialVoteOption = false;

    @Column(name = "requires_absence_reason")
    private Boolean requiresAbsenceReason = false;

    @Column(name = "allows_qr_checkin")
    private Boolean allowsQrCheckin = true;

    @Column(name = "requires_survey_completion")
    private Boolean requiresSurveyCompletion = false;

    //    页面内容配置
    @Column(name = "landing_page_title")
    private String landingPageTitle;

    @Column(name = "landing_page_description", columnDefinition = "TEXT")
    private String landingPageDescription;

    @Column(name = "registration_form_title")
    private String registrationFormTitle;

    @Column(name = "registration_form_instructions", columnDefinition = "TEXT")
    private String registrationFormInstructions;

    @Column(name = "attendance_question_text")
    private String attendanceQuestionText;

    @Column(name = "special_vote_question_text")
    private String specialVoteQuestionText;

    @Column(name = "success_message", columnDefinition = "TEXT")
    private String successMessage;

    @Column(name = "email_template_subject")
    private String emailTemplateSubject;

    @Column(name = "email_template_content", columnDefinition = "TEXT")
    private String emailTemplateContent;

    //    特殊投票配置（BMM_VOTING专用）
    @Column(name = "special_vote_eligibility_criteria", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String specialVoteEligibilityCriteria; // 特殊投票资格标准

    @Column(name = "special_vote_deadline_days")
    private Integer specialVoteDeadlineDays = 14; // 申请截止日期（会议前N天）

    @Column(name = "requires_medical_certificate")
    private Boolean requiresMedicalCertificate = false;

    @Column(name = "max_distance_for_special_vote")
    private Double maxDistanceForSpecialVote = 32.0; // 32公里限制

    //    调查问卷配置（SURVEY_MEETING专用）
    @Column(name = "survey_questions", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String surveyQuestions; // 调查问卷题目

    @Column(name = "target_sub_industry")
    private String targetSubIndustry; // 目标子行业（如manufacturing food）

    @Column(name = "expected_participants")
    private Integer expectedParticipants; // 预期参与人数

    //    多区域配置（BMM_VOTING专用）
    @Column(name = "enabled_regions", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String enabledRegions; // 启用的区域列表

    @Column(name = "region_specific_settings", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String regionSpecificSettings; // 各区域特定设置

    //    通知配置
    @Column(name = "email_notification_enabled")
    private Boolean emailNotificationEnabled = true;

    @Column(name = "sms_notification_enabled")
    private Boolean smsNotificationEnabled = true;

    @Column(name = "reminder_schedule", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String reminderSchedule; // 提醒时间安排

    //    数据源配置
    @Column(name = "default_informer_dataset_ids", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String defaultInformerDatasetIds; // 默认Informer数据源

    @Column(name = "member_filter_criteria", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String memberFilterCriteria; // 会员筛选条件

    //    状态管理
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_default_template")
    private Boolean isDefaultTemplate = false;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}