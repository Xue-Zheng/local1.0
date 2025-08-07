package nz.etu.voting.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString(exclude = {"eventMemberships"})
@EqualsAndHashCode(exclude = {"eventMemberships"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "members")
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "membership_number", nullable = false, unique = true)
    private String membershipNumber;

    //    Financial Declaration 数据字段
    @Column(name = "financial_indicator")
    private String financialIndicator; // financialIndicatorDescription

    @Column(name = "fore1")
    private String fore1; // fore1 - 名字

    @Column(name = "known_as")
    private String knownAs; // knownAs - 别名

    @Column(name = "surname")
    private String surname; // surname - 姓

    @Column(name = "dob")
    private String dob; // dob - 生日 (DD/MM/YYYY格式)

    @Column(name = "employee_ref")
    private String employeeRef; // employeeRef - 员工编号

    //    重要：行业分类字段
    @Column(name = "site_industry_desc")
    private String siteIndustryDesc; // siteIndustryDesc - 用于分类的关键字段!

    @Column(name = "occupation")
    private String occupation; // occupation - 职业

    //    地址信息 (Financial Declaration格式)
    @Column(name = "add_res1")
    private String addRes1; // addRes1 - 地址1

    @Column(name = "add_res2")
    private String addRes2; // addRes2 - 地址2

    @Column(name = "add_res3")
    private String addRes3; // addRes3 - 地址3

    @Column(name = "add_res4")
    private String addRes4; // addRes4 - 地区

    @Column(name = "add_res5")
    private String addRes5; // addRes5 - 城市

    @Column(name = "add_res_pc")
    private String addResPc; // addResPc - 邮编

    //    联系信息
    @Column(name = "telephone_mobile")
    private String telephoneMobile; // telephoneMobile

    @Column(name = "primary_email")
    private String primaryEmail; // primaryEmail

    //    工作信息
    @Column(name = "employer_name")
    private String employerName; // employerName (数组第一个值)

    @Column(name = "workplace_desc", columnDefinition = "TEXT")
    private String workplaceDesc; // workplaceDesc (数组第一个值)

    //    地区和分支信息
    @Column(name = "region_desc")
    private String regionDesc; // regionDesc - Northern, Central, Southern

    @Column(name = "branch_desc")
    private String branchDesc; // branchDesc (数组第一个值)

    @Column(name = "forum_desc")
    private String forumDesc; // forumDesc

    //    会员状态信息
    @Column(name = "last_payment_date")
    private String lastPaymentDate; // lastPaymentDate

    @Column(name = "membership_type_desc")
    private String membershipTypeDesc; // membershipTypeDesc

    @Column(name = "epmu_mem_type_desc")
    private String epmuMemTypeDesc; // epmuMemTypeDesc

    //    新增完整JSON字段支持
    @Column(name = "age_of_member")
    private String ageOfMember; // ageOfMember

    @Column(name = "gender_desc")
    private String genderDesc; // genderDesc

    @Column(name = "ethnic_region_desc")
    private String ethnicRegionDesc; // ethnicRegionDesc

    @Column(name = "ethnic_origin_desc")
    private String ethnicOriginDesc; // ethnicOriginDesc (用于事件参与者)

    @Column(name = "site_sub_industry_desc")
    private String siteSubIndustryDesc; // siteSubIndustryDesc

    @Column(name = "bargaining_group_desc", columnDefinition = "TEXT")
    private String bargainingGroupDesc; // bargainingGroupDesc

    @Column(name = "site_prim_org_name")
    private String sitePrimOrgName; // sitePrimOrgName (事件参与者)

    @Column(name = "org_team_p_desc_epmu")
    private String orgTeamPDescEpmu; // orgTeamPDescEpmu (事件参与者)

    @Column(name = "director_name")
    private String directorName; // directorName (事件参与者)

    @Column(name = "sub_ind_sector")
    private String subIndSector; // subIndSector (事件参与者)

    //    数据来源追踪
    @Column(name = "data_source")
    private String dataSource = "MANUAL"; // INFORMER_EMAIL, INFORMER_SMS, MANUAL, INFORMER_AUTO_CREATED

    @Column(name = "last_sync_time")
    private LocalDateTime lastSyncTime;

    @Column(name = "sync_status")
    private String syncStatus = "PENDING"; // PENDING, SUCCESS, FAILED

    @Column(name = "import_batch_id")
    private String importBatchId; // 导入批次ID，便于追踪

    //    RabbitMQ 消息发送状态追踪字段 (增量发送核心功能)
    @Column(name = "email_sent_status")
    private String emailSentStatus = "NOT_SENT"; // NOT_SENT, SENDING, SUCCESS, FAILED

    @Column(name = "sms_sent_status")
    private String smsSentStatus = "NOT_SENT"; // NOT_SENT, SENDING, SUCCESS, FAILED

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt; // 邮件发送时间戳

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt; // 短信发送时间戳

    @Column(name = "last_email_template")
    private String lastEmailTemplate; // 最后使用的邮件模板

    @Column(name = "send_retry_count")
    private Integer sendRetryCount = 0; // 重试次数

    @Column(name = "send_error_message")
    private String sendErrorMessage; // 发送失败原因

    //    联系方式状态字段
    @Column(name = "has_email")
    private Boolean hasEmail = false; // 是否有有效邮箱

    @Column(name = "has_mobile")
    private Boolean hasMobile = false; // 是否有有效手机号

    //    兼容性字段（保留原有逻辑）
    @Column(name = "dob_legacy")
    private LocalDate dobLegacy; // 保留原有LocalDate类型

    @Column
    private String address; // 保留原有地址字段

    @Column(name = "phone_home")
    private String phoneHome;

    @Column(name = "phone_work")
    private String phoneWork;

    @Column
    private String employer;

    @Column(name = "payroll_number")
    private String payrollNumber;

    @Column(name = "site_code")
    private String siteNumber;

    @Column(name = "employment_status")
    private String employmentStatus;

    @Column
    private String department;

    @Column(name = "job_title")
    private String jobTitle;

    @Column
    private String location;

    //    系统字段
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<EventMember> eventMemberships;

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

    @Column(name = "checkin_time")
    private LocalDateTime checkinTime;

    // CRITICAL: 新增：Checkin详细追踪字段
    @Column(name = "checked_in")
    private Boolean checkedIn = false;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_in_location")
    private String checkInLocation;

    @Column(name = "check_in_admin_id")
    private Long checkInAdminId;

    @Column(name = "check_in_admin_username")
    private String checkInAdminUsername;

    @Column(name = "check_in_admin_name")
    private String checkInAdminName;

    @Column(name = "check_in_method")
    private String checkInMethod; // QR_SCAN, MANUAL, BULK

    @Column(name = "check_in_venue")
    private String checkInVenue; // 具体venue名称

    @Column(name = "absence_reason")
    private String absenceReason;

    @Column(name = "venue")
    private String venue; // 用户选择的具体venue（可能与forumDesc不同）

    public void setBatchId(String batchId) {
    }

    public void setForename1(String s) {
    }
}