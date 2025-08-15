package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventMemberRepository extends JpaRepository<EventMember, Long> {

    Optional<EventMember> findByToken(UUID token);

    Optional<EventMember> findByEventAndMembershipNumber(Event event, String membershipNumber);

    Optional<EventMember> findByMembershipNumberAndEvent(String membershipNumber, Event event);

    Optional<EventMember> findByMembershipNumberAndVerificationCode(String membershipNumber, String verificationCode);

    List<EventMember> findByEvent(Event event);

    List<EventMember> findByMembershipNumber(String membershipNumber);

    List<EventMember> findByEventAndHasRegisteredTrue(Event event);

    List<EventMember> findByEventAndHasRegisteredFalse(Event event);

    List<EventMember> findByEventAndIsAttendingTrue(Event event);

    List<EventMember> findByEventAndIsSpecialVoteTrue(Event event);

    List<EventMember> findByEventAndHasVotedTrue(Event event);

    List<EventMember> findByEventAndCheckedInTrue(Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event")
    Long countByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.hasRegistered = true")
    Long countRegisteredByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.isAttending = true")
    Long countAttendingByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.isSpecialVote = true")
    Long countSpecialVoteByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.hasVoted = true")
    Long countVotedByEvent(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.checkedIn = true")
    Long countCheckedInByEvent(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasEmail = false AND em.hasMobile = true")
    List<EventMember> findMembersWithoutEmailByEvent(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasEmail = true")
    List<EventMember> findMembersWithEmailByEvent(@Param("event") Event event);

    // 按注册时间筛选
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasRegistered = true AND em.registrationCompletedAt >= :fromTime")
    List<EventMember> findRegisteredMembersSince(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasRegistered = true AND em.registrationCompletedAt BETWEEN :fromTime AND :toTime")
    List<EventMember> findRegisteredMembersBetween(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime);

    // 按出席确认时间筛选
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.isAttending = true AND em.attendanceDecisionMadeAt >= :fromTime")
    List<EventMember> findAttendingMembersSince(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.isAttending = false AND em.attendanceDecisionMadeAt >= :fromTime")
    List<EventMember> findNonAttendingMembersSince(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    // 按邮件发送状态筛选
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.initialEmailSent = false")
    List<EventMember> findMembersNeedingInitialEmail(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasRegistered = true AND em.registrationConfirmationEmailSent = false")
    List<EventMember> findMembersNeedingRegistrationConfirmation(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.isAttending = true AND em.attendanceConfirmationEmailSent = false")
    List<EventMember> findMembersNeedingAttendanceConfirmation(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.isAttending = true AND em.qrCodeEmailSent = false")
    List<EventMember> findMembersNeedingQRCode(@Param("event") Event event);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasRegistered = false AND em.followUpReminderSent = false")
    List<EventMember> findMembersNeedingFollowUpReminder(@Param("event") Event event);

    // 按行业筛选（Manufacturing Food等）
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.industryFilterCriteria = :criteria")
    List<EventMember> findMembersByIndustryCriteria(@Param("event") Event event, @Param("criteria") String criteria);

    // 按实际行业字段筛选
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND " +
            "(LOWER(em.member.siteIndustryDesc) LIKE LOWER(CONCAT('%', :industry, '%')) OR " +
            "LOWER(em.member.employerName) LIKE LOWER(CONCAT('%', :industry, '%')))")
    List<EventMember> findMembersByIndustryFields(@Param("event") Event event, @Param("industry") String industry);

    // 组合查询 - 新注册且需要发送特定邮件的会员
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.hasRegistered = true AND em.registrationCompletedAt >= :fromTime AND em.attendanceConfirmationEmailSent = false")
    List<EventMember> findNewlyRegisteredMembersNeedingEmail(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.isAttending = true AND em.attendanceDecisionMadeAt >= :fromTime AND em.qrCodeEmailSent = false")
    List<EventMember> findNewlyAttendingMembersNeedingQRCode(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    // 统计查询
    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.registrationCompletedAt >= :fromTime")
    Long countNewRegistrationsSince(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.attendanceDecisionMadeAt >= :fromTime AND em.isAttending = true")
    Long countNewAttendeesSince(@Param("event") Event event, @Param("fromTime") LocalDateTime fromTime);

    // 按数据源筛选
    @Query("SELECT em FROM EventMember em WHERE em.event = :event AND em.dataSource = :dataSource")
    List<EventMember> findMembersByDataSource(@Param("event") Event event, @Param("dataSource") String dataSource);

    // 复杂筛选查询
    @Query("SELECT em FROM EventMember em WHERE em.event = :event " +
            "AND (:hasRegistered IS NULL OR em.hasRegistered = :hasRegistered) " +
            "AND (:isAttending IS NULL OR em.isAttending = :isAttending) " +
            "AND (:emailSent IS NULL OR em.initialEmailSent = :emailSent) " +
            "AND (:fromTime IS NULL OR em.lastActivityAt >= :fromTime) " +
            "AND (:toTime IS NULL OR em.lastActivityAt <= :toTime)")
    List<EventMember> findMembersByComplexCriteria(
            @Param("event") Event event,
            @Param("hasRegistered") Boolean hasRegistered,
            @Param("isAttending") Boolean isAttending,
            @Param("emailSent") Boolean emailSent,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    // BMM Region-specific queries
    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.regionDesc = :region")
    Long countByEventAndRegionDesc(@Param("event") Event event, @Param("region") String region);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.regionDesc = :region AND em.checkedIn = true")
    Long countByEventAndRegionDescAndCheckedInTrue(@Param("event") Event event, @Param("region") String region);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.regionDesc = :region AND em.isAttending = true")
    Long countByEventAndRegionDescAndIsAttendingTrue(@Param("event") Event event, @Param("region") String region);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.regionDesc = :region AND em.isSpecialVote = true")
    Long countByEventAndRegionDescAndIsSpecialVoteTrue(@Param("event") Event event, @Param("region") String region);

    // Additional count methods for BMM stats
    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.isAttending = true")
    Long countByEventAndIsAttendingTrue(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.checkedIn = true")
    Long countByEventAndCheckedInTrue(@Param("event") Event event);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.isSpecialVote = true")
    Long countByEventAndIsSpecialVoteTrue(@Param("event") Event event);

    // 新增：多表联合查询 - 同时基于Member表和EventMember表字段进行复杂过滤
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (:hasRegistered IS NULL OR em.hasRegistered = :hasRegistered) " +
            "AND (:isAttending IS NULL OR em.isAttending = :isAttending) " +
            "AND (:hasVoted IS NULL OR em.hasVoted = :hasVoted) " +
            "AND (:checkedIn IS NULL OR em.checkedIn = :checkedIn) " +
            "AND (:regionDesc IS NULL OR em.regionDesc = :regionDesc) " +
            "AND (:genderDesc IS NULL OR em.genderDesc = :genderDesc) " +
            "AND (:siteIndustryDesc IS NULL OR em.siteIndustryDesc = :siteIndustryDesc) " +
            "AND (:siteSubIndustryDesc IS NULL OR em.siteSubIndustryDesc = :siteSubIndustryDesc) " +
            "AND (:workplaceDesc IS NULL OR em.workplaceDesc = :workplaceDesc) " +
            "AND (:employerName IS NULL OR em.employer = :employerName) " +
            "AND (:bargainingGroupDesc IS NULL OR em.bargainingGroupDesc = :bargainingGroupDesc) " +
            "AND (:employmentStatus IS NULL OR em.employmentStatus = :employmentStatus) " +
            "AND (:ethnicRegionDesc IS NULL OR em.ethnicRegionDesc = :ethnicRegionDesc) " +
            "AND (:jobTitle IS NULL OR em.jobTitle = :jobTitle) " +
            "AND (:department IS NULL OR em.department = :department) " +
            "AND (:siteNumber IS NULL OR em.siteCode = :siteNumber) " +
            "AND (:hasEmail IS NULL OR em.hasEmail = :hasEmail) " +
            "AND (:hasMobile IS NULL OR em.hasMobile = :hasMobile) " +
            "AND (:branchDesc IS NULL OR em.branch = :branchDesc) " +
            "AND (:forumDesc IS NULL OR em.forumDesc = :forumDesc) " +
            "AND (:membershipTypeDesc IS NULL OR em.membershipTypeDesc = :membershipTypeDesc) " +
            "AND (:occupation IS NULL OR em.occupation = :occupation)")
    List<EventMember> findByEventAndMemberCriteria(
            @Param("event") Event event,
            @Param("hasRegistered") Boolean hasRegistered,
            @Param("isAttending") Boolean isAttending,
            @Param("hasVoted") Boolean hasVoted,
            @Param("checkedIn") Boolean checkedIn,
            @Param("regionDesc") String regionDesc,
            @Param("genderDesc") String genderDesc,
            @Param("siteIndustryDesc") String siteIndustryDesc,
            @Param("siteSubIndustryDesc") String siteSubIndustryDesc,
            @Param("workplaceDesc") String workplaceDesc,
            @Param("employerName") String employerName,
            @Param("bargainingGroupDesc") String bargainingGroupDesc,
            @Param("employmentStatus") String employmentStatus,
            @Param("ethnicRegionDesc") String ethnicRegionDesc,
            @Param("jobTitle") String jobTitle,
            @Param("department") String department,
            @Param("siteNumber") String siteNumber,
            @Param("hasEmail") Boolean hasEmail,
            @Param("hasMobile") Boolean hasMobile,
            @Param("branchDesc") String branchDesc,
            @Param("forumDesc") String forumDesc,
            @Param("membershipTypeDesc") String membershipTypeDesc,
            @Param("occupation") String occupation
    );

    // 按行业关键词进行多表联合查询
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (em.siteIndustryDesc LIKE CONCAT('%', :industry, '%') " +
            "OR em.siteSubIndustryDesc LIKE CONCAT('%', :industry, '%') " +
            "OR em.workplaceDesc LIKE CONCAT('%', :industry, '%') " +
            "OR em.employer LIKE CONCAT('%', :industry, '%'))")
    List<EventMember> findByEventAndIndustryKeyword(
            @Param("event") Event event,
            @Param("industry") String industry
    );

    // 按年龄范围进行多表联合查询
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND em.ageOfMember IS NOT NULL " +
            "AND CAST(em.ageOfMember AS double) >= :minAge " +
            "AND CAST(em.ageOfMember AS double) <= :maxAge")
    List<EventMember> findByEventAndAgeRange(
            @Param("event") Event event,
            @Param("minAge") Double minAge,
            @Param("maxAge") Double maxAge
    );

    // 多条件组合查询 - 优化版本，支持多个行业、地区、状态的OR组合
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (:industries IS NULL OR " +
            " em.siteIndustryDesc IN :industries OR " +
            " em.siteSubIndustryDesc IN :industries OR " +
            " em.workplaceDesc LIKE CONCAT('%', :industryKeyword, '%') OR " +
            " em.employer LIKE CONCAT('%', :industryKeyword, '%')) " +
            "AND (:regions IS NULL OR em.regionDesc IN :regions) " +
            "AND (:genders IS NULL OR em.genderDesc IN :genders) " +
            "AND (:hasRegistered IS NULL OR em.hasRegistered = :hasRegistered) " +
            "AND (:isAttending IS NULL OR em.isAttending = :isAttending)")
    List<EventMember> findByEventAndMultipleCriteria(
            @Param("event") Event event,
            @Param("industries") List<String> industries,
            @Param("regions") List<String> regions,
            @Param("genders") List<String> genders,
            @Param("hasRegistered") Boolean hasRegistered,
            @Param("isAttending") Boolean isAttending,
            @Param("industryKeyword") String industryKeyword
    );

    // 获取发送通知的目标用户 - 优化版本，支持更精确的通讯偏好过滤
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND ((:communicationType = 'EMAIL' AND em.hasEmail = true AND em.primaryEmail IS NOT NULL " +
            " AND em.primaryEmail != '' AND em.primaryEmail NOT LIKE '%@temp-email.etu.nz%') " +
            "OR (:communicationType = 'SMS' AND em.hasMobile = true AND em.telephoneMobile IS NOT NULL " +
            " AND em.telephoneMobile != '' AND LENGTH(TRIM(em.telephoneMobile)) >= 8) " +
            "OR (:communicationType = 'BOTH' AND ((em.hasEmail = true AND em.primaryEmail IS NOT NULL AND em.primaryEmail != '') " +
            " OR (em.hasMobile = true AND em.telephoneMobile IS NOT NULL AND em.telephoneMobile != ''))))")
    List<EventMember> findByEventAndCommunicationType(
            @Param("event") Event event,
            @Param("communicationType") String communicationType
    );

    // 新增：按工作状态和雇佣信息进行高级过滤
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (:employmentStatus IS NULL OR em.employmentStatus = :employmentStatus) " +
            "AND (:payrollNumber IS NULL OR em.payrollNumber = :payrollNumber) " +
            "AND (:siteCode IS NULL OR em.siteCode = :siteCode) " +
            "AND (:departmentKeyword IS NULL OR em.department LIKE CONCAT('%', :departmentKeyword, '%')) " +
            "AND (:jobTitleKeyword IS NULL OR em.jobTitle LIKE CONCAT('%', :jobTitleKeyword, '%'))")
    List<EventMember> findByEventAndEmploymentDetails(
            @Param("event") Event event,
            @Param("employmentStatus") String employmentStatus,
            @Param("payrollNumber") String payrollNumber,
            @Param("siteCode") String siteCode,
            @Param("departmentKeyword") String departmentKeyword,
            @Param("jobTitleKeyword") String jobTitleKeyword
    );

    //  新增：按地址信息进行过滤（用于区域性会议）
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (:regionDesc IS NULL OR em.regionDesc = :regionDesc) " +
            "AND (:cityKeyword IS NULL OR " +
            " em.addRes1 LIKE CONCAT('%', :cityKeyword, '%') OR " +
            " em.addRes2 LIKE CONCAT('%', :cityKeyword, '%') OR " +
            " em.addRes3 LIKE CONCAT('%', :cityKeyword, '%')) " +
            "AND (:postcode IS NULL OR em.addResPc = :postcode)")
    List<EventMember> findByEventAndLocationDetails(
            @Param("event") Event event,
            @Param("regionDesc") String regionDesc,
            @Param("cityKeyword") String cityKeyword,
            @Param("postcode") String postcode
    );

    // 新增：统计查询 - 多表联合统计
    @Query("SELECT COUNT(em) FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND (:regionDesc IS NULL OR em.regionDesc = :regionDesc) " +
            "AND (:hasRegistered IS NULL OR em.hasRegistered = :hasRegistered) " +
            "AND (:isAttending IS NULL OR em.isAttending = :isAttending) " +
            "AND (:hasEmail IS NULL OR em.hasEmail = :hasEmail) " +
            "AND (:hasMobile IS NULL OR em.hasMobile = :hasMobile)")
    Long countByEventAndMemberCriteria(
            @Param("event") Event event,
            @Param("regionDesc") String regionDesc,
            @Param("hasRegistered") Boolean hasRegistered,
            @Param("isAttending") Boolean isAttending,
            @Param("hasEmail") Boolean hasEmail,
            @Param("hasMobile") Boolean hasMobile
    );

    // 新增：获取最新活跃的EventMember（按最后活动时间排序）
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND em.lastActivityAt IS NOT NULL " +
            "ORDER BY em.lastActivityAt DESC")
    List<EventMember> findByEventOrderByLastActivityDesc(@Param("event") Event event);

    // 新增：根据注册完成时间排序获取最新注册成员
    @Query("SELECT em FROM EventMember em " +
            "WHERE em.event = :event " +
            "AND em.hasRegistered = true " +
            "AND em.registrationCompletedAt IS NOT NULL " +
            "ORDER BY em.registrationCompletedAt DESC")
    List<EventMember> findRegisteredByEventOrderByRegistrationDesc(@Param("event") Event event);

    // 🎯 BMM特定查询方法
    List<EventMember> findByEventAndBmmRegistrationStage(Event event, String bmmRegistrationStage);

    List<EventMember> findByBmmRegistrationStage(String bmmRegistrationStage);

    List<EventMember> findByEventAndTicketStatus(Event event, String ticketStatus);

    List<EventMember> findByEventAndTicketStatusIsNull(Event event);

    Optional<EventMember> findByTicketToken(UUID ticketToken);

    // Email查询方法
    List<EventMember> findByPrimaryEmail(String primaryEmail);

    // BMM member token查询方法
    Optional<EventMember> findByMemberToken(String memberToken);

    // BMM Check-in related queries
    List<EventMember> findByCheckedInTrueAndBmmRegistrationStage(String bmmRegistrationStage);

    Long countByBmmRegistrationStage(String bmmRegistrationStage);

    // Forum-based counts for venue statistics
    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.forumDesc = :forum")
    Long countByEventAndForumDesc(@Param("event") Event event, @Param("forum") String forum);

    @Query("SELECT COUNT(em) FROM EventMember em WHERE em.event = :event AND em.forumDesc = :forum AND em.checkedIn = true")
    Long countByEventAndForumDescAndCheckedInTrue(@Param("event") Event event, @Param("forum") String forum);

    // Get all distinct forums for an event
    @Query("SELECT DISTINCT em.forumDesc FROM EventMember em WHERE em.event = :event AND em.forumDesc IS NOT NULL ORDER BY em.forumDesc")
    List<String> findDistinctForumDescByEvent(@Param("event") Event event);
}