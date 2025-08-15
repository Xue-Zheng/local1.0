package nz.etu.voting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.*;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.repository.NotificationTemplateRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;

    @Transactional
    public void sendRegistrationConfirmation(EventMember eventMember) {
        log.info("Sending registration confirmation to member: {}", eventMember.getMembershipNumber());

        NotificationTemplate template = templateRepository.findByTemplateCode("REGISTRATION_CONFIRMATION")
                .orElse(getDefaultRegistrationTemplate());

        Map<String, String> variables = Map.of(
                "name", eventMember.getName(),
                "membershipNumber", eventMember.getMembershipNumber(),
                "eventName", eventMember.getEvent().getName(),
                "verificationCode", eventMember.getVerificationCode(),
                "registrationLink", generateRegistrationLink(eventMember)
        );

        if (eventMember.getHasEmail()) {
            sendEmailNotification(eventMember, template, variables, NotificationLog.NotificationType.AUTO_EMAIL);
        }

        if (eventMember.getHasMobile() && !eventMember.getHasEmail()) {
            sendSmsNotification(eventMember, template, variables, NotificationLog.NotificationType.AUTO_SMS);
        }
    }

    @Transactional
    public void sendBulkNotifications(Event event, List<EventMember> members, NotificationTemplate template,
                                      Map<String, String> commonVariables, NotificationLog.NotificationType type) {
        log.info("Sending bulk notifications to {} members for event: {}", members.size(), event.getName());

        for (EventMember member : members) {
            try {
                Map<String, String> personalizedVariables = personalizeVariables(commonVariables, member);

                if (type == NotificationLog.NotificationType.EMAIL && member.getHasEmail()) {
                    sendEmailNotification(member, template, personalizedVariables, type);
                } else if (type == NotificationLog.NotificationType.SMS && member.getHasMobile()) {
                    sendSmsNotification(member, template, personalizedVariables, type);
                }
            } catch (Exception e) {
                log.error("Failed to send notification to member {}: {}", member.getMembershipNumber(), e.getMessage());
            }
        }
    }

    private void sendEmailNotification(EventMember eventMember, NotificationTemplate template,
                                       Map<String, String> variables, NotificationLog.NotificationType type) {
        try {
            String personalizedSubject = replaceVariables(template.getSubject(), variables);
            String personalizedContent = replaceVariables(template.getContent(), variables);


            Map<String, Object> emailData = Map.of(
                    "recipient", eventMember.getPrimaryEmail(),
                    "recipientName", eventMember.getName(),
                    "subject", personalizedSubject,
                    "content", personalizedContent,
                    "eventMemberId", eventMember.getId(),
                    "templateCode", template.getTemplateCode(),
                    "notificationType", type.name()
            );

            rabbitTemplate.convertAndSend(emailQueue, emailData);


            logNotification(eventMember, type, eventMember.getPrimaryEmail(), eventMember.getName(),
                    personalizedSubject, personalizedContent, template.getTemplateCode(), true, null);

        } catch (Exception e) {
            log.error("Failed to queue email for member {}: {}", eventMember.getMembershipNumber(), e.getMessage());
            logNotification(eventMember, type, eventMember.getPrimaryEmail(), eventMember.getName(),
                    template.getSubject(), template.getContent(), template.getTemplateCode(), false, e.getMessage());
        }
    }

    private void sendSmsNotification(EventMember eventMember, NotificationTemplate template,
                                     Map<String, String> variables, NotificationLog.NotificationType type) {
        try {
            String personalizedContent = replaceVariables(template.getContent(), variables);


            Map<String, Object> smsData = Map.of(
                    "recipient", eventMember.getTelephoneMobile(),
                    "recipientName", eventMember.getName(),
                    "content", personalizedContent,
                    "eventMemberId", eventMember.getId(),
                    "templateCode", template.getTemplateCode(),
                    "notificationType", type.name()
            );

            rabbitTemplate.convertAndSend(smsQueue, smsData);


            logNotification(eventMember, type, eventMember.getTelephoneMobile(), eventMember.getName(),
                    "SMS", personalizedContent, template.getTemplateCode(), true, null);

        } catch (Exception e) {
            log.error("Failed to queue SMS for member {}: {}", eventMember.getMembershipNumber(), e.getMessage());
            logNotification(eventMember, type, eventMember.getTelephoneMobile(), eventMember.getName(),
                    "SMS", template.getContent(), template.getTemplateCode(), false, e.getMessage());
        }
    }

    private void logNotification(EventMember eventMember, NotificationLog.NotificationType type,
                                 String recipient, String recipientName, String subject, String content,
                                 String templateCode, boolean successful, String errorMessage) {
        NotificationLog log = NotificationLog.builder()
                .event(eventMember.getEvent())
                .eventMember(eventMember)
                .notificationType(type)
                .recipient(recipient)
                .recipientName(recipientName)
                .subject(subject)
                .content(content)
                .sentTime(LocalDateTime.now())
                .isSuccessful(successful)
                .errorMessage(errorMessage)
                .templateCode(templateCode)
                .build();

        notificationLogRepository.save(log);
    }

    private Map<String, String> personalizeVariables(Map<String, String> commonVariables, EventMember member) {
        Map<String, String> personalizedVariables = new java.util.HashMap<>(commonVariables);
        personalizedVariables.put("name", member.getName());
        personalizedVariables.put("membershipNumber", member.getMembershipNumber());
        personalizedVariables.put("verificationCode", member.getVerificationCode());
        personalizedVariables.put("registrationLink", generateRegistrationLink(member));
        personalizedVariables.put("eventName", member.getEvent().getName());
        return personalizedVariables;
    }

    private String replaceVariables(String content, Map<String, String> variables) {
        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private String generateRegistrationLink(EventMember eventMember) {
        if (eventMember.getToken() == null) {
            return "https://events.etu.nz/";
        }

        String registrationLink = "https://events.etu.nz/register/bmm-template?token=" + eventMember.getToken();

        // Add region parameter for region-specific experience
        if (eventMember.getRegionDesc() != null && !eventMember.getRegionDesc().trim().isEmpty()) {
            try {
                String encodedRegion = java.net.URLEncoder.encode(eventMember.getRegionDesc(), "UTF-8");
                registrationLink += "&region=" + encodedRegion;
            } catch (Exception e) {
                // If encoding fails, continue without region parameter
            }
        }

        // 添加事件ID参数
        if (eventMember.getEvent() != null) {
            registrationLink += "&event=" + eventMember.getEvent().getId();
        }

        return registrationLink;
    }

    private NotificationTemplate getDefaultRegistrationTemplate() {
        return NotificationTemplate.builder()
                .templateCode("DEFAULT_REGISTRATION")
                .name("Default Registration Confirmation")
                .templateType(NotificationTemplate.TemplateType.BOTH)
                .subject("Registration Confirmation - {{eventName}}")
                .content("Dear {{name}}, your registration for {{eventName}} has been confirmed. " +
                        "Your membership number: {{membershipNumber}}. Registration link: {{registrationLink}}")
                .isActive(true)
                .build();
    }
}