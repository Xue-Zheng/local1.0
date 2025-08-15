package nz.etu.voting.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.service.EmailService;
import nz.etu.voting.service.SmsService;
import nz.etu.voting.service.impl.EmailServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationMessageConsumer {

    private final EmailService emailService;
    private final SmsService smsService;
    private final EventMemberRepository eventMemberRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final MemberRepository memberRepository;

    @RabbitListener(queues = "${app.rabbitmq.queue.email}")
    public void processEmailMessage(Map<String, Object> emailData) {
        try {
            log.info("Processing email message for recipient: {}", emailData.get("recipient"));

            String recipient = (String) emailData.get("recipient");
            String recipientName = (String) emailData.get("recipientName");
            String subject = (String) emailData.get("subject");
            String content = (String) emailData.get("content");
            Long eventMemberId = Long.valueOf(emailData.get("eventMemberId").toString());
            String templateCode = (String) emailData.get("templateCode");
            String notificationTypeStr = (String) emailData.get("notificationType");
            String provider = (String) emailData.get("provider");

            // Log the subject and content to debug
            log.info("Email details - Subject: [{}], Content length: {}, Provider: {}",
                    subject, content != null ? content.length() : 0, provider);
            log.debug("Email content preview: {}", content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);

            // Use provider if specified, otherwise default to Stratum
            boolean emailSentSuccessfully = false;
            String errorMessage = null;

            try {
                if ("MAILJET".equalsIgnoreCase(provider)) {
                    log.info("📧 Sending via MAILJET to: {}", recipient);
                    ((EmailServiceImpl) emailService).sendEmailWithProvider(recipient, recipientName, subject, content, "MAILJET");
                } else {
                    log.info("📧 Sending via STRATUM to: {}", recipient);
                    emailService.sendSimpleEmail(recipient, recipientName, subject, content);
                }
                emailSentSuccessfully = true;
                log.info("✅ Email sent successfully via {} to: {}", provider != null ? provider : "STRATUM", recipient);
            } catch (Exception emailException) {
                emailSentSuccessfully = false;
                errorMessage = emailException.getMessage();
                log.error("❌ Email sending failed via {} to {}: {}", provider != null ? provider : "STRATUM", recipient, errorMessage);
            }

            // Create notification log with correct success status and provider info
            updateNotificationLogStatus(eventMemberId, NotificationLog.NotificationType.valueOf(notificationTypeStr),
                    recipient, emailSentSuccessfully, errorMessage, subject, content, provider);

            // Special handling for BMM ticket emails
            if ("BMM_TICKET".equals(templateCode) && emailSentSuccessfully) {
                try {
                    Optional<EventMember> memberOpt = eventMemberRepository.findById(eventMemberId);
                    if (memberOpt.isPresent()) {
                        EventMember member = memberOpt.get();
                        member.setTicketStatus("EMAIL_SENT");
                        member.setQrCodeEmailSent(true);
                        member.setLastActivityAt(LocalDateTime.now());
                        eventMemberRepository.save(member);
                        log.info("✅ Updated BMM ticket status for member: {}", member.getMembershipNumber());
                    }
                } catch (Exception e) {
                    log.error("Failed to update BMM ticket status: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to process email message: {}", e.getMessage(), e);

            try {
                Long eventMemberId = Long.valueOf(emailData.get("eventMemberId").toString());
                String notificationTypeStr = (String) emailData.get("notificationType");
                String recipient = (String) emailData.get("recipient");
                String subject = (String) emailData.get("subject");
                String content = (String) emailData.get("content");

                updateNotificationLogStatus(eventMemberId, NotificationLog.NotificationType.valueOf(notificationTypeStr),
                        recipient, false, e.getMessage(), subject, content);
            } catch (Exception logError) {
                log.error("Failed to update notification log: {}", logError.getMessage());
            }
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.sms}")
    public void processSmsMessage(Map<String, Object> smsData) {
        try {
            log.info("Processing SMS message for recipient: {}", smsData.get("recipient"));

            String recipient = (String) smsData.get("recipient");
            String content = (String) smsData.get("content");
            Long eventMemberId = Long.valueOf(smsData.get("eventMemberId").toString());
            String templateCode = (String) smsData.get("templateCode");
            String notificationTypeStr = (String) smsData.get("notificationType");

//            eventMemberId may be Member ID (not EventMember ID) from bulk operations
            String membershipNumber = (String) smsData.get("membershipNumber");
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("MembershipNumber is empty from SMS data, generating fallback for eventMemberId: {}", eventMemberId);
                membershipNumber = "BULK_" + eventMemberId;
            }

            smsService.sendSms(recipient, membershipNumber, content);

            updateNotificationLogStatus(eventMemberId, NotificationLog.NotificationType.valueOf(notificationTypeStr),
                    recipient, true, null, null, content);

            log.info("SMS sent successfully to: {}", recipient);

        } catch (Exception e) {
            log.error("Failed to process SMS message: {}", e.getMessage(), e);

            try {
                Long eventMemberId = Long.valueOf(smsData.get("eventMemberId").toString());
                String notificationTypeStr = (String) smsData.get("notificationType");
                String recipient = (String) smsData.get("recipient");
                String content = (String) smsData.get("content");

                updateNotificationLogStatus(eventMemberId, NotificationLog.NotificationType.valueOf(notificationTypeStr),
                        recipient, false, e.getMessage(), null, content);
            } catch (Exception logError) {
                log.error("Failed to update notification log: {}", logError.getMessage());
            }
        }
    }

    private void updateNotificationLogStatus(Long eventMemberId, NotificationLog.NotificationType type,
                                             String recipient, boolean successful, String errorMessage, String subject, String content) {
        updateNotificationLogStatus(eventMemberId, type, recipient, successful, errorMessage, subject, content, null);
    }

    private void updateNotificationLogStatus(Long eventMemberId, NotificationLog.NotificationType type,
                                             String recipient, boolean successful, String errorMessage, String subject, String content, String provider) {
        try {
            log.debug("🔍 Updating notification log status - EventMemberId: {}, Type: {}, Recipient: {}, Success: {}",
                    eventMemberId, type, recipient, successful);

            NotificationLog logToUpdate = null;

            // 🎯 BMM事件基于EventMember，直接通过EventMember查找NotificationLog
            if (eventMemberId != null) {
                EventMember eventMember = eventMemberRepository.findById(eventMemberId).orElse(null);
                if (eventMember != null) {
                    // 查找最近1小时内的通知日志
                    LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
                    List<NotificationLog> eventMemberLogs = notificationLogRepository.findByEventMemberOrderBySentTimeDesc(eventMember);

                    logToUpdate = eventMemberLogs.stream()
                            .filter(nl -> nl.getNotificationType() == type &&
                                    nl.getRecipient().equals(recipient) &&
                                    nl.getSentTime().isAfter(cutoffTime) &&
                                    (nl.getIsSuccessful() == null || !Boolean.TRUE.equals(nl.getIsSuccessful()))) // 找待更新的记录
                            .findFirst()
                            .orElse(null);

                    if (logToUpdate != null) {
                        log.debug("✅ Found notification log by EventMember: Log ID {}", logToUpdate.getId());
                    }
                }
            }

            // 🔧 如果通过EventMember没找到，尝试通过recipient查找最近的记录
            if (logToUpdate == null) {
                LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1);
                List<NotificationLog> recentLogs = notificationLogRepository.findByRecipientAndNotificationTypeAndSentTimeAfter(
                        recipient, type, cutoffTime);

                if (!recentLogs.isEmpty()) {
                    logToUpdate = recentLogs.stream()
                            .filter(nl -> nl.getIsSuccessful() == null || !Boolean.TRUE.equals(nl.getIsSuccessful()))
                            .max((a, b) -> a.getSentTime().compareTo(b.getSentTime()))
                            .orElse(recentLogs.get(0));
                    log.debug("✅ Found notification log by recipient+type: Log ID {}", logToUpdate.getId());
                }
            }

            if (logToUpdate != null) {
                // 📝 Update the log status
                logToUpdate.setIsSuccessful(successful);
                logToUpdate.setErrorMessage(errorMessage);
                logToUpdate.setSentTime(LocalDateTime.now());

                // Update email type with provider information
                if (provider != null && type == NotificationLog.NotificationType.EMAIL) {
                    String currentEmailType = logToUpdate.getEmailType();
                    if (currentEmailType != null && !currentEmailType.contains(provider.toUpperCase())) {
                        logToUpdate.setEmailType(currentEmailType + "_" + provider.toUpperCase());
                    }
                }

                NotificationLog savedLog = notificationLogRepository.save(logToUpdate);
                log.info("✅ Updated notification log status - Log ID: {}, EventMember: {}, Recipient: {}, Success: {}, Provider: {}",
                        savedLog.getId(), eventMemberId, recipient, successful, provider);
            } else {
                log.warn("⚠️ No existing notification log found for EventMember: {}, Recipient: {}", eventMemberId, recipient);

                // 🆕 Create new log if none found (fallback for BMM event)
                EventMember eventMember = eventMemberId != null ? eventMemberRepository.findById(eventMemberId).orElse(null) : null;
                Member member = eventMember != null ? eventMember.getMember() :
                        memberRepository.findByPrimaryEmail(recipient).orElse(null);

                String emailTypeWithProvider = type == NotificationLog.NotificationType.EMAIL ?
                        (provider != null ? "BMM_EVENT_" + provider.toUpperCase() : "BMM_EVENT_FALLBACK") : null;

                NotificationLog newLog = NotificationLog.builder()
                        .eventMember(eventMember) // 🎯 BMM事件主要关联EventMember
                        .member(member)
                        .notificationType(type)
                        .recipient(recipient)
                        .recipientName(member != null ? member.getName() : "Unknown")
                        .subject(subject)  // Add subject
                        .content(content)  // Add content
                        .sentTime(LocalDateTime.now())
                        .isSuccessful(successful)
                        .errorMessage(errorMessage)
                        .emailType(emailTypeWithProvider)
                        .adminId(1L)
                        .adminUsername("system")
                        .build();

                NotificationLog savedLog = notificationLogRepository.save(newLog);
                log.info("🆕 Created new BMM notification log - Log ID: {}, EventMember: {}, Recipient: {}, Success: {}, Provider: {}",
                        savedLog.getId(), eventMemberId, recipient, successful, provider);
            }
        } catch (Exception e) {
            log.error("❌ Failed to update notification log status for EventMember {}, recipient {}: {}",
                    eventMemberId, recipient, e.getMessage(), e);
        }
    }
}