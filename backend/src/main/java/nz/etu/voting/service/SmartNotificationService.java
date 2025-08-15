package nz.etu.voting.service;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SmartNotificationService {

    //    发送初始邀请邮件给所有未发送过的会员
    NotificationResult sendInitialInvitations(Long eventId, String emailSubject, String emailContent);

    //    发送注册确认邮件给新注册的会员
    NotificationResult sendRegistrationConfirmations(Long eventId, LocalDateTime since, String emailSubject, String emailContent);

    //    发送出席确认邮件给新确认出席的会员
    NotificationResult sendAttendanceConfirmations(Long eventId, LocalDateTime since, String emailSubject, String emailContent);

    //    发送QR码邮件给需要的会员
    NotificationResult sendQRCodeEmails(Long eventId, LocalDateTime since, String emailSubject, String emailContent);

    //    发送跟进提醒给未注册的会员
    NotificationResult sendFollowUpReminders(Long eventId, String emailSubject, String emailContent);

    //    Manufacturing Food Survey Email
    NotificationResult sendManufacturingFoodSurvey(Long eventId, String emailSubject, String emailContent);

    //    获取需要发送特定类型邮件的会员列表（预览）
    List<EventMember> previewEmailRecipients(Long eventId, String emailType, LocalDateTime since);

    //    获取会员筛选统计信息
    Map<String, Long> getMemberFilterStats(Long eventId, LocalDateTime since);

    //    标记邮件发送状态
    void markEmailSent(EventMember member, String emailType);

    //    根据复杂条件筛选会员
    List<EventMember> filterMembers(Long eventId, MemberFilterCriteria criteria);

    //    获取最近的活动时间点（用于"since when"筛选）
    List<LocalDateTime> getRecentActivityTimepoints(Long eventId);

    class NotificationResult {
        private final int totalRecipients;
        private final int successCount;
        private final int failureCount;
        private final List<String> errors;
        private final String emailType;
        private final LocalDateTime sentAt;

        public NotificationResult(int totalRecipients, int successCount, int failureCount,
                                  List<String> errors, String emailType, LocalDateTime sentAt) {
            this.totalRecipients = totalRecipients;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errors = errors;
            this.emailType = emailType;
            this.sentAt = sentAt;
        }

        //        Getters
        public int getTotalRecipients() { return totalRecipients; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<String> getErrors() { return errors; }
        public String getEmailType() { return emailType; }
        public LocalDateTime getSentAt() { return sentAt; }
    }

    class MemberFilterCriteria {
        private Boolean hasRegistered;
        private Boolean isAttending;
        private Boolean isSpecialVote;
        private Boolean initialEmailSent;
        private Boolean registrationConfirmationEmailSent;
        private Boolean attendanceConfirmationEmailSent;
        private Boolean qrCodeEmailSent;
        private LocalDateTime registeredSince;
        private LocalDateTime attendanceDecisionSince;
        private LocalDateTime lastActivitySince;
        private String industryFilterCriteria;
        private String dataSource;

        //        Constructors
        public MemberFilterCriteria() {}

        //        Builder pattern methods
        public MemberFilterCriteria hasRegistered(Boolean hasRegistered) {
            this.hasRegistered = hasRegistered;
            return this;
        }

        public MemberFilterCriteria isAttending(Boolean isAttending) {
            this.isAttending = isAttending;
            return this;
        }

        public MemberFilterCriteria registeredSince(LocalDateTime registeredSince) {
            this.registeredSince = registeredSince;
            return this;
        }

        public MemberFilterCriteria industryFilterCriteria(String industryFilterCriteria) {
            this.industryFilterCriteria = industryFilterCriteria;
            return this;
        }

        //        Getters
        public Boolean getHasRegistered() { return hasRegistered; }
        public Boolean getIsAttending() { return isAttending; }
        public Boolean getIsSpecialVote() { return isSpecialVote; }
        public Boolean getInitialEmailSent() { return initialEmailSent; }
        public Boolean getRegistrationConfirmationEmailSent() { return registrationConfirmationEmailSent; }
        public Boolean getAttendanceConfirmationEmailSent() { return attendanceConfirmationEmailSent; }
        public Boolean getQrCodeEmailSent() { return qrCodeEmailSent; }
        public LocalDateTime getRegisteredSince() { return registeredSince; }
        public LocalDateTime getAttendanceDecisionSince() { return attendanceDecisionSince; }
        public LocalDateTime getLastActivitySince() { return lastActivitySince; }
        public String getIndustryFilterCriteria() { return industryFilterCriteria; }
        public String getDataSource() { return dataSource; }

        //        Setters for all fields
        public void setHasRegistered(Boolean hasRegistered) { this.hasRegistered = hasRegistered; }
        public void setIsAttending(Boolean isAttending) { this.isAttending = isAttending; }
        public void setIsSpecialVote(Boolean isSpecialVote) { this.isSpecialVote = isSpecialVote; }
        public void setInitialEmailSent(Boolean initialEmailSent) { this.initialEmailSent = initialEmailSent; }
        public void setRegistrationConfirmationEmailSent(Boolean registrationConfirmationEmailSent) { this.registrationConfirmationEmailSent = registrationConfirmationEmailSent; }
        public void setAttendanceConfirmationEmailSent(Boolean attendanceConfirmationEmailSent) { this.attendanceConfirmationEmailSent = attendanceConfirmationEmailSent; }
        public void setQrCodeEmailSent(Boolean qrCodeEmailSent) { this.qrCodeEmailSent = qrCodeEmailSent; }
        public void setRegisteredSince(LocalDateTime registeredSince) { this.registeredSince = registeredSince; }
        public void setAttendanceDecisionSince(LocalDateTime attendanceDecisionSince) { this.attendanceDecisionSince = attendanceDecisionSince; }
        public void setLastActivitySince(LocalDateTime lastActivitySince) { this.lastActivitySince = lastActivitySince; }
        public void setIndustryFilterCriteria(String industryFilterCriteria) { this.industryFilterCriteria = industryFilterCriteria; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }
}