package nz.etu.voting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = {"event", "member"})
@EqualsAndHashCode(exclude = {"event", "member"})

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_members")
public class EventMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //    现有基本字段（保留不变）
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String primaryEmail;

    @Column(name = "membership_number", nullable = false)
    private String membershipNumber;

    @Column(name = "telephone_mobile")
    private String telephoneMobile;

    @Column(name = "has_email", nullable = false)
    private Boolean hasEmail = true;

    @Column(name = "has_mobile", nullable = false)
    private Boolean hasMobile = false;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(name = "verification_code", nullable = false, length = 6)
    private String verificationCode;

    @Column(name = "has_registered", nullable = false)
    private Boolean hasRegistered = false;

    @Column(name = "is_attending", nullable = false)
    private Boolean isAttending = false;

    @Column(name = "is_special_vote", nullable = false)
    private Boolean isSpecialVote = false;

    @Column(name = "has_voted", nullable = false)
    private Boolean hasVoted = false;

    @Column(name = "vote_timestamp")
    private LocalDateTime voteTimestamp;

    @Column(name = "absence_reason")
    private String absenceReason;

    @Column(name = "checked_in", nullable = false)
    private Boolean checkedIn = false;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_in_location")
    private String checkInLocation;

    // CRITICAL: 新增：Checkin Admin追踪字段
    @Column(name = "check_in_admin_id")
    private Long checkInAdminId;

    @Column(name = "check_in_admin_username")
    private String checkInAdminUsername;

    @Column(name = "check_in_admin_name")
    private String checkInAdminName;

    @Column(name = "check_in_method")
    private String checkInMethod; // QR_SCAN, MANUAL, BULK

    @Column(name = "check_in_venue")
    private String checkInVenue; // 具体venue名称（如Auckland Central, Wellington等）

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    //    特殊投票相关
    @Column(name = "special_vote_reason")
    private String specialVoteReason;

    @Column(name = "special_vote_details", columnDefinition = "TEXT")
    private String specialVoteDetails;

    @Column(name = "special_vote_status")
    private String specialVoteStatus = "PENDING"; // PENDING, APPROVED, REJECTED, INELIGIBLE

    @Column(name = "special_vote_reviewed_by")
    private String specialVoteReviewedBy;

    @Column(name = "special_vote_reviewed_at")
    private LocalDateTime specialVoteReviewedAt;

    @Column(name = "special_vote_review_notes", columnDefinition = "TEXT")
    private String specialVoteReviewNotes;

    //    特殊投票申请详细原因（BMM投票会议专用）
    @Column(name = "special_vote_eligibility_reason")
    private String specialVoteEligibilityReason; // DISABILITY, ILLNESS, DISTANCE, WORK_REQUIRED, OTHER

    @Column(name = "distance_from_venue")
    private Double distanceFromVenue; // 距离会场的公里数

    @Column(name = "employer_work_requirement")
    private String employerWorkRequirement; // 雇主工作要求详情

    @Column(name = "medical_certificate_provided")
    private Boolean medicalCertificateProvided = false; // 是否提供医疗证明

    @Column(name = "special_vote_submitted_date")
    private LocalDateTime specialVoteSubmittedDate; // 申请提交日期

    //    调查问卷相关（SURVEY_MEETING专用）
    @Column(name = "survey_completed")
    private Boolean surveyCompleted = false;

    @Column(name = "survey_responses", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String surveyResponses; // 调查问卷回答内容

    @Column(name = "survey_completed_at")
    private LocalDateTime surveyCompletedAt;

    //    注册流程状态跟踪（支持三种会议类型的不同流程）
    @Column(name = "registration_step")
    private String registrationStep = "INITIAL"; // INITIAL, UPDATE_INFO, CONFIRM_ATTENDANCE, QR_RECEIVED, COMPLETED

    @Column(name = "registration_data", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String registrationData;

    @Column(name = "form_submission_time")
    private LocalDateTime formSubmissionTime;

    //    通知状态
    @Column(name = "email_sent")
    private Boolean emailSent = false;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "sms_sent")
    private Boolean smsSent = false;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "reminder_sent")
    private Boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    //    详细的通知历史跟踪
    @Column(name = "initial_email_sent")
    private Boolean initialEmailSent = false;

    @Column(name = "initial_email_sent_at")
    private LocalDateTime initialEmailSentAt;

    @Column(name = "registration_confirmation_email_sent")
    private Boolean registrationConfirmationEmailSent = false;

    @Column(name = "registration_confirmation_email_sent_at")
    private LocalDateTime registrationConfirmationEmailSentAt;

    @Column(name = "attendance_confirmation_email_sent")
    private Boolean attendanceConfirmationEmailSent = false;

    @Column(name = "attendance_confirmation_email_sent_at")
    private LocalDateTime attendanceConfirmationEmailSentAt;

    @Column(name = "qr_code_email_sent")
    private Boolean qrCodeEmailSent = false;

    @Column(name = "qr_code_email_sent_at")
    private LocalDateTime qrCodeEmailSentAt;

    @Column(name = "follow_up_reminder_sent")
    private Boolean followUpReminderSent = false;

    @Column(name = "follow_up_reminder_sent_at")
    private LocalDateTime followUpReminderSentAt;

    //    状态变更时间跟踪
    @Column(name = "registration_completed_at")
    private LocalDateTime registrationCompletedAt;

    @Column(name = "attendance_decision_made_at")
    private LocalDateTime attendanceDecisionMadeAt;

    @Column(name = "special_vote_applied_at")
    private LocalDateTime specialVoteAppliedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    //    数据源和导入信息
    @Column(name = "data_source")
    private String dataSource; // INFORMER_ATTENDEES, INFORMER_EMAIL_MEMBERS, INFORMER_SMS_MEMBERS, MANUAL

    @Column(name = "import_batch_id")
    private String importBatchId;

    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    @Column(name = "industry_filter_criteria")
    private String industryFilterCriteria; // 用于记录manufacturing food等筛选条件

    //    地区信息
    @Column(name = "region")
    private String region;

    @Column(name = "region_desc")
    private String regionDesc;

    @Column(name = "branch")
    private String branch;

    @Column(name = "bargaining_group")
    private String bargainingGroup;

    @Column(name = "workplace")
    private String workplace;

    @Column(name = "employer")
    private String employer;

    @Column(name = "registration_status")
    private String registrationStatus = "PENDING";

    // CRITICAL: 新增：从Member同步的详细字段
    @Column(name = "fore1")
    private String fore1; // 名字

    @Column(name = "known_as")
    private String knownAs; // 别名

    @Column(name = "surname")
    private String surname; // 姓

    @Column(name = "dob")
    private String dob; // 生日

    @Column(name = "age_of_member")
    private String ageOfMember; // 年龄

    @Column(name = "gender_desc")
    private String genderDesc; // 性别

    @Column(name = "ethnic_region_desc")
    private String ethnicRegionDesc; // 民族地区

    @Column(name = "ethnic_origin_desc")
    private String ethnicOriginDesc; // 民族来源

    @Column(name = "employment_status")
    private String employmentStatus; // 就业状态

    @Column(name = "payroll_number")
    private String payrollNumber; // 工资单号

    @Column(name = "site_code")
    private String siteCode; // 站点代码

    @Column(name = "site_sub_industry_desc")
    private String siteSubIndustryDesc; // 细分行业

    @Column(name = "site_industry_desc")
    private String siteIndustryDesc; // 主行业分类（用于筛选的关键字段）

    @Column(name = "membership_type_desc")
    private String membershipTypeDesc; // 会员类型

    @Column(name = "bargaining_group_desc", columnDefinition = "TEXT")
    private String bargainingGroupDesc; // 谈判组描述（完整版）

    @Column(name = "workplace_desc", columnDefinition = "TEXT")
    private String workplaceDesc; // 工作场所描述

    @Column(name = "site_prim_org_name")
    private String sitePrimOrgName; // 主要组织名称

    @Column(name = "org_team_p_desc_epmu")
    private String orgTeamPDescEpmu; // 组织团队描述

    @Column(name = "director_name")
    private String directorName; // 主管姓名

    @Column(name = "sub_ind_sector")
    private String subIndSector; // 子行业部门

    @Column(name = "job_title")
    private String jobTitle; // 职位

    @Column(name = "department")
    private String department; // 部门

    @Column(name = "location")
    private String location; // 工作地点

    @Column(name = "phone_home")
    private String phoneHome; // 家庭电话

    @Column(name = "phone_work")
    private String phoneWork; // 工作电话

    @Column(name = "address", columnDefinition = "TEXT")
    private String address; // 地址

    // CRITICAL: 新增：根据Informer数据样例添加缺失字段
    @Column(name = "financial_indicator_description")
    private String financialIndicatorDescription; // 财务指标描述

    @Column(name = "employee_ref")
    private String employeeRef; // 员工编号

    @Column(name = "add_res1")
    private String addRes1; // 地址1

    @Column(name = "add_res2")
    private String addRes2; // 地址2

    @Column(name = "add_res3")
    private String addRes3; // 地址3

    @Column(name = "add_res4")
    private String addRes4; // 地址4

    @Column(name = "add_res5")
    private String addRes5; // 地址5

    @Column(name = "add_res_pc")
    private String addResPc; // 邮政编码

    @Column(name = "occupation")
    private String occupation; // 职业

    @Column(name = "forum_desc")
    private String forumDesc; // 论坛描述

    @Column(name = "last_payment_date")
    private String lastPaymentDate; // 最后付款日期

    @Column(name = "epmu_mem_type_desc")
    private String epmuMemTypeDesc; // EPMU会员类型描述

    // CRITICAL: BMM Preferences 字段 - 用于保存用户的BMM偏好选择
    @Column(name = "preferred_venues", columnDefinition = "TEXT")
    private String preferredVenues; // 首选场地列表，逗号分隔

    @Column(name = "preferred_times", columnDefinition = "TEXT")
    private String preferredTimes; // 首选时间段列表，逗号分隔 (morning, lunchtime, afternoon, after-work, night-shift)

    @Column(name = "attendance_willingness")
    private String attendanceWillingness; // 参会意愿 (yes/no)

    @Column(name = "workplace_info", columnDefinition = "TEXT")
    private String workplaceInfo; // 工作场所信息（如果无法参加现有场地）

    @Column(name = "meeting_format")
    private String meetingFormat; // 会议形式偏好 (in-person/online/hybrid)

    @Column(name = "additional_comments", columnDefinition = "TEXT")
    private String additionalComments; // 额外评论或特殊要求

    @Column(name = "suggested_venue", columnDefinition = "TEXT")
    private String suggestedVenue; // 建议的场地

    // 🎯 BMM Two-Stage Process Management
    @Column(name = "bmm_registration_stage")
    private String bmmRegistrationStage = "PENDING"; // PENDING, PREFERENCE_SUBMITTED, ATTENDANCE_PENDING, ATTENDANCE_CONFIRMED, ATTENDANCE_DECLINED

    @Column(name = "bmm_preferences", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String bmmPreferences; // JSON存储所有BMM偏好数据

    @Column(name = "assigned_venue")
    private String assignedVenue; // 最终分配的具体会议地点

    @Column(name = "assigned_date_time")
    private LocalDateTime assignedDateTime; // 最终分配的具体会议时间

    @Column(name = "assigned_region")
    private String assignedRegion; // 分配的地区 (Northern/Central/Southern Region)

    @Column(name = "ticket_status")
    private String ticketStatus = "PENDING"; // PENDING, EMAIL_SENT, SMS_SENT, DOWNLOAD_READY, FAILED

    @Column(name = "ticket_token", unique = true)
    private UUID ticketToken; // 票据唯一标识符

    @Column(name = "ticket_generated_at")
    private LocalDateTime ticketGeneratedAt; // 票据生成时间

    @Column(name = "ticket_sent_at")
    private LocalDateTime ticketSentAt; // 票据发送时间

    @Column(name = "ticket_sent_method")
    private String ticketSentMethod; // EMAIL, SMS, DOWNLOAD

    // 🗳️ Southern Region Special Vote Management
    @Column(name = "special_vote_eligible")
    private Boolean specialVoteEligible = false; // 是否有资格申请special vote

    @Column(name = "special_vote_preference")
    private String specialVotePreference; // YES, NO, NOT_SURE (from registration form)

    @Column(name = "bmm_special_vote_status")
    private String bmmSpecialVoteStatus = "NOT_APPLICABLE"; // NOT_APPLICABLE, PENDING, APPROVED, DECLINED

    @Column(name = "special_vote_application_date")
    private LocalDateTime specialVoteApplicationDate; // special vote申请日期

    @Column(name = "special_vote_decision_date")
    private LocalDateTime specialVoteDecisionDate; // special vote决定日期

    @Column(name = "special_vote_decision_by")
    private String specialVoteDecisionBy; // 决定者（管理员）

    // BMM Communication Tracking
    @Column(name = "bmm_invitation_sent")
    private Boolean bmmInvitationSent = false; // 第一阶段邀请是否发送

    @Column(name = "bmm_invitation_sent_at")
    private LocalDateTime bmmInvitationSentAt;

    @Column(name = "bmm_confirmation_request_sent")
    private Boolean bmmConfirmationRequestSent = false; // 第二阶段确认请求是否发送

    @Column(name = "bmm_confirmation_request_sent_at")
    private LocalDateTime bmmConfirmationRequestSentAt;

    @Column(name = "bmm_attendance_confirmed_at")
    private LocalDateTime bmmAttendanceConfirmedAt; // 确认出席时间

    @Column(name = "bmm_attendance_declined_at")
    private LocalDateTime bmmAttendanceDeclinedAt; // 拒绝出席时间

    @Column(name = "bmm_decline_reason")
    private String bmmDeclineReason; // 拒绝出席原因

    // 📊 BMM Analytics & Tracking
    @Column(name = "venue_preference_priority")
    private Integer venuePreferencePriority; // 1=首选, 2=次选, etc.

    @Column(name = "time_preference_priority")
    private Integer timePreferencePriority; // 时间偏好优先级

    @Column(name = "attendance_likelihood_score")
    private Double attendanceLikelihoodScore; // AI计算的出席可能性分数 (0.0-1.0)

    @Column(name = "bmm_last_interaction_at")
    private LocalDateTime bmmLastInteractionAt; // 最后互动时间

    @Column(name = "bmm_notes", columnDefinition = "TEXT")
    private String bmmNotes; // 管理员备注

    // 🔄 BMM完整方案新增字段
    @Column(name = "bmm_stage")
    private String bmmStage = "INVITED"; // INVITED, PREFERENCE_SUBMITTED, VENUE_ASSIGNED, ATTENDANCE_CONFIRMED, TICKET_ISSUED, CHECKED_IN

    @Column(name = "member_token", unique = true)
    private String memberToken; // 用于访问BMM页面的唯一token

    // 第一阶段偏好数据（JSON存储）
    @Column(name = "preferred_venues_json", columnDefinition = "TEXT")
    private String preferredVenuesJson; // JSON: ["venue1"] - 只选一个venue

    @Column(name = "preferred_dates_json", columnDefinition = "TEXT")
    private String preferredDatesJson; // JSON: ["2025-09-15", "2025-09-16"]

    @Column(name = "preferred_times_json", columnDefinition = "TEXT")
    private String preferredTimesJson; // JSON: ["Morning", "Afternoon"]

    @Column(name = "preferred_attending")
    private Boolean preferredAttending; // 第一阶段的初步意向（不是最终决定）

    @Column(name = "preference_special_vote")
    private Boolean preferenceSpecialVote; // Pre-registration阶段询问是否需要特殊投票（仅中南区）

    @Column(name = "preference_submitted_at")
    private LocalDateTime preferenceSubmittedAt;

    // 第二阶段分配
    @Column(name = "assigned_venue_final")
    private String assignedVenueFinal; // 最终分配的会场名称

    @Column(name = "assigned_datetime_final")
    private LocalDateTime assignedDatetimeFinal; // 最终分配的日期时间

    @Column(name = "venue_assigned_at")
    private LocalDateTime venueAssignedAt;

    @Column(name = "venue_assigned_by")
    private String venueAssignedBy; // 分配会场的管理员

    // Financial表单关联
    @Column(name = "financial_form_id")
    private Long financialFormId;

    // 基础会员信息（从Member表复制，避免依赖）
    @Column(name = "member_name")
    private String memberName;

    @Column(name = "member_email")
    private String memberEmail;

    // telephoneMobile已在第47行定义，这里不再重复定义

    @Column(name = "email_capable")
    private Boolean emailCapable = false;

    @Column(name = "sms_capable")
    private Boolean smsCapable = false;

    // 票务管理增强
    @Column(name = "ticket_pdf_path")
    private String ticketPdfPath;

    @Column(name = "ticket_email_sent_at")
    private LocalDateTime ticketEmailSentAt;

    @Column(name = "ticket_sms_sent_at")
    private LocalDateTime ticketSmsSentAt;

    // Special Vote增强
    @Column(name = "special_vote_requested")
    private Boolean specialVoteRequested = false;

    @Column(name = "special_vote_application_reason")
    private String specialVoteApplicationReason;

    @Column(name = "special_vote_sent_at")
    private LocalDateTime specialVoteSentAt;

    @Column(name = "special_vote_completed_at")
    private LocalDateTime specialVoteCompletedAt;

    // 出席决定
    @Column(name = "attendance_decision_at")
    private LocalDateTime attendanceDecisionAt;

    @Column(name = "non_attendance_reason")
    private String nonAttendanceReason;

    @Column(name = "attendance_confirmed")
    private Boolean attendanceConfirmed;

}