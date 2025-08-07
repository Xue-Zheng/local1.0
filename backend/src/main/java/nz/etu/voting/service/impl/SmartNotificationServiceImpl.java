package nz.etu.voting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.EmailService;
import nz.etu.voting.service.SmartNotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartNotificationServiceImpl implements SmartNotificationService {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public NotificationResult sendInitialInvitations(Long eventId, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> recipients = eventMemberRepository.findMembersNeedingInitialEmail(event);

        log.info("Sending initial invitations to {} members for event {}", recipients.size(), event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "INITIAL_INVITATION");
    }

    @Override
    @Transactional
    public NotificationResult sendRegistrationConfirmations(Long eventId, LocalDateTime since, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> recipients = eventMemberRepository.findNewlyRegisteredMembersNeedingEmail(event, since);

        log.info("Sending registration confirmations to {} newly registered members since {} for event {}",
                recipients.size(), since, event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "REGISTRATION_CONFIRMATION");
    }

    @Override
    @Transactional
    public NotificationResult sendAttendanceConfirmations(Long eventId, LocalDateTime since, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> recipients = eventMemberRepository.findAttendingMembersSince(event, since)
                .stream()
                .filter(member -> member.getAttendanceConfirmationEmailSent() != null && !member.getAttendanceConfirmationEmailSent())
                .collect(Collectors.toList());

        log.info("Sending attendance confirmations to {} newly attending members since {} for event {}",
                recipients.size(), since, event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "ATTENDANCE_CONFIRMATION");
    }

    @Override
    @Transactional
    public NotificationResult sendQRCodeEmails(Long eventId, LocalDateTime since, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> recipients = eventMemberRepository.findNewlyAttendingMembersNeedingQRCode(event, since);

        log.info("Sending QR code emails to {} newly attending members since {} for event {}",
                recipients.size(), since, event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "QR_CODE");
    }

    @Override
    @Transactional
    public NotificationResult sendFollowUpReminders(Long eventId, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> recipients = eventMemberRepository.findMembersNeedingFollowUpReminder(event);

        log.info("Sending follow-up reminders to {} unregistered members for event {}",
                recipients.size(), event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "FOLLOW_UP_REMINDER");
    }

    @Override
    @Transactional
    public NotificationResult sendManufacturingFoodSurvey(Long eventId, String emailSubject, String emailContent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

//        Use actual industry fields to filter manufacturing food companies
        List<EventMember> recipients = eventMemberRepository.findMembersByIndustryFields(event, "manufacturing food");

        log.info("Sending manufacturing food survey to {} members for event {}",
                recipients.size(), event.getName());

        return sendEmailsToMembers(recipients, emailSubject, emailContent, "MANUFACTURING_FOOD_SURVEY");
    }

    @Override
    public List<EventMember> previewEmailRecipients(Long eventId, String emailType, LocalDateTime since) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        switch (emailType.toUpperCase()) {
            case "INITIAL_INVITATION":
                return eventMemberRepository.findMembersNeedingInitialEmail(event);
            case "REGISTRATION_CONFIRMATION":
                return since != null ?
                        eventMemberRepository.findNewlyRegisteredMembersNeedingEmail(event, since) :
                        eventMemberRepository.findMembersNeedingRegistrationConfirmation(event);
            case "ATTENDANCE_CONFIRMATION":
                return since != null ?
                        eventMemberRepository.findAttendingMembersSince(event, since)
                                .stream()
                                .filter(member -> member.getAttendanceConfirmationEmailSent() != null && !member.getAttendanceConfirmationEmailSent())
                                .collect(Collectors.toList()) :
                        eventMemberRepository.findMembersNeedingAttendanceConfirmation(event);
            case "QR_CODE":
                return since != null ?
                        eventMemberRepository.findNewlyAttendingMembersNeedingQRCode(event, since) :
                        eventMemberRepository.findMembersNeedingQRCode(event);
            case "FOLLOW_UP_REMINDER":
                return eventMemberRepository.findMembersNeedingFollowUpReminder(event);
            case "MANUFACTURING_FOOD_SURVEY":
                return eventMemberRepository.findMembersByIndustryFields(event, "manufacturing food");
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> getMemberFilterStats(Long eventId, LocalDateTime since) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        Map<String, Long> stats = new HashMap<>();

//        基础统计
        stats.put("totalMembers", eventMemberRepository.countByEvent(event));
        stats.put("registeredMembers", eventMemberRepository.countRegisteredByEvent(event));
        stats.put("attendingMembers", eventMemberRepository.countAttendingByEvent(event));
        stats.put("specialVoteMembers", eventMemberRepository.countSpecialVoteByEvent(event));

        if (since != null) {
//            时间相关统计
            stats.put("newRegistrationsSince", eventMemberRepository.countNewRegistrationsSince(event, since));
            stats.put("newAttendeesSince", eventMemberRepository.countNewAttendeesSince(event, since));
        }

//        邮件发送统计
        stats.put("needingInitialEmail", (long) eventMemberRepository.findMembersNeedingInitialEmail(event).size());
        stats.put("needingRegistrationConfirmation", (long) eventMemberRepository.findMembersNeedingRegistrationConfirmation(event).size());
        stats.put("needingAttendanceConfirmation", (long) eventMemberRepository.findMembersNeedingAttendanceConfirmation(event).size());
        stats.put("needingQRCode", (long) eventMemberRepository.findMembersNeedingQRCode(event).size());
        stats.put("needingFollowUpReminder", (long) eventMemberRepository.findMembersNeedingFollowUpReminder(event).size());

        return stats;
    }

    @Override
    @Transactional
    public void markEmailSent(EventMember member, String emailType) {
        LocalDateTime now = LocalDateTime.now();

        switch (emailType.toUpperCase()) {
            case "INITIAL_INVITATION":
                member.setInitialEmailSent(true);
                member.setInitialEmailSentAt(now);
                break;
            case "REGISTRATION_CONFIRMATION":
                member.setRegistrationConfirmationEmailSent(true);
                member.setRegistrationConfirmationEmailSentAt(now);
                break;
            case "ATTENDANCE_CONFIRMATION":
                member.setAttendanceConfirmationEmailSent(true);
                member.setAttendanceConfirmationEmailSentAt(now);
                break;
            case "QR_CODE":
                member.setQrCodeEmailSent(true);
                member.setQrCodeEmailSentAt(now);
                break;
            case "FOLLOW_UP_REMINDER":
                member.setFollowUpReminderSent(true);
                member.setFollowUpReminderSentAt(now);
                break;
        }

        member.setLastActivityAt(now);
        eventMemberRepository.save(member);
    }

    @Override
    public List<EventMember> filterMembers(Long eventId, MemberFilterCriteria criteria) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

//        使用复杂查询方法
        return eventMemberRepository.findMembersByComplexCriteria(
                event,
                criteria.getHasRegistered(),
                criteria.getIsAttending(),
                criteria.getInitialEmailSent(),
                criteria.getLastActivitySince(),
                null // toTime
        );
    }

    @Override
    public List<LocalDateTime> getRecentActivityTimepoints(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        List<EventMember> allMembers = eventMemberRepository.findByEvent(event);

        Set<LocalDateTime> timepoints = new TreeSet<>();

        for (EventMember member : allMembers) {
            if (member.getRegistrationCompletedAt() != null) {
                timepoints.add(member.getRegistrationCompletedAt());
            }
            if (member.getAttendanceDecisionMadeAt() != null) {
                timepoints.add(member.getAttendanceDecisionMadeAt());
            }
            if (member.getLastActivityAt() != null) {
                timepoints.add(member.getLastActivityAt());
            }
        }

//        返回最近的10个时间点
        return timepoints.stream()
                .sorted(Comparator.reverseOrder())
                .limit(10)
                .collect(Collectors.toList());
    }

    private NotificationResult sendEmailsToMembers(List<EventMember> members, String emailSubject,
                                                   String emailContent, String emailType) {
        if (members.isEmpty()) {
            return new NotificationResult(0, 0, 0, Collections.emptyList(), emailType, LocalDateTime.now());
        }

        List<String> errors = new ArrayList<>();
        int successCount = 0;

        for (EventMember member : members) {
            try {
//                替换邮件变量
                String personalizedContent = personalizeEmailContent(emailContent, member);
                String personalizedSubject = personalizeEmailContent(emailSubject, member);

//                发送邮件 (这里应该调用实际的邮件服务)
                boolean success = sendEmailToMember(member, personalizedSubject, personalizedContent);

                if (success) {
                    markEmailSent(member, emailType);
                    successCount++;
                } else {
                    errors.add("Failed to send email to " + member.getPrimaryEmail());
                }

            } catch (Exception e) {
                log.error("Error sending email to member {}: {}", member.getMembershipNumber(), e.getMessage());
                errors.add("Error sending email to " + member.getPrimaryEmail() + ": " + e.getMessage());
            }
        }

        return new NotificationResult(
                members.size(),
                successCount,
                members.size() - successCount,
                errors,
                emailType,
                LocalDateTime.now()
        );
    }

    private String personalizeEmailContent(String content, EventMember member) {
        return content
                .replace("{{name}}", member.getName())
                .replace("{{membershipNumber}}", member.getMembershipNumber())
                .replace("{{verificationCode}}", member.getVerificationCode())
                .replace("{{eventName}}", member.getEvent().getName())
                .replace("{{registrationLink}}", generateRegistrationLinkWithEvent(member));
    }

    private boolean sendEmailToMember(EventMember member, String subject, String content) {
        try {
//            这里应该调用实际的邮件服务
//            暂时返回true模拟成功
            log.info("Sending email to {}: {}", member.getPrimaryEmail(), subject);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", member.getPrimaryEmail(), e.getMessage());
            return false;
        }
    }

    private String generateRegistrationLinkWithEvent(EventMember member) {
        if (member.getToken() == null) {
            return "https://events.etu.nz/";
        }

        String registrationLink = "https://events.etu.nz/?token=" + member.getToken();

        // EventMember已经有关联的事件，直接使用
        if (member.getEvent() != null) {
            registrationLink += "&event=" + member.getEvent().getId();
        }

        return registrationLink;
    }
}