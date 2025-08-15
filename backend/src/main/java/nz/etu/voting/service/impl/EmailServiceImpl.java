package nz.etu.voting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.BulkEmailRequest;
import nz.etu.voting.domain.dto.response.EmailResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final MemberRepository memberRepository;
    private final EventMemberRepository eventMemberRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final RestTemplate restTemplate;
    private final nz.etu.voting.service.MailjetService mailjetService;

    @Value("${stratum.api.url}")
    private String stratumApiUrl;

    @Value("${stratum.api.securityKey}")
    private String stratumApiSecurityKey;

    @Value("${etu.sender.email}")
    private String senderEmail;

    @Value("${etu.sender.name}")
    private String senderName;

    @Override
    public void sendTemplate(String toEmail, String toName, String templateId, Map<String, String> variables) {
        log.info("Sending template email to: {} using templateId: {}", toEmail, templateId);
        String personalizedContent = replaceVariables(variables.get("content"), variables);
        sendStratumEmail(toEmail, toName, variables.get("subject"), personalizedContent);
    }

    @Override
    public void sendBulkTemplate(List<Map<String, Object>> recipients, String templateId) {
        for (Map<String, Object> recipient : recipients) {
            String email = (String) recipient.get("email");
            String name = (String) recipient.get("name");
            @SuppressWarnings("unchecked")
            Map<String, String> vars = (Map<String, String>) recipient.get("variables");

            sendTemplate(email, name, templateId, vars);
        }
    }

    @Override
    public void sendSimpleEmail(String toEmail, String toName, String subject, String textContent) {
        log.info("=== sendSimpleEmail called ===");
        log.info("To: {} ({})", toEmail, toName);
        log.info("Subject: [{}]", subject);
        log.info("Content length: {}", textContent != null ? textContent.length() : 0);
        log.debug("Content preview: {}", textContent != null && textContent.length() > 100 ? textContent.substring(0, 100) + "..." : textContent);

        sendStratumEmail(toEmail, toName, subject, textContent);
    }

    private void sendStratumEmail(String toEmail, String toName, String subject, String textContent) {
        try {
            subject = toAsciiOnly(subject);
            toName = toAsciiOnly(toName);
            textContent = toAsciiOnly(textContent);

            String xmlPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + buildStratumXmlPayload(toEmail, toName, subject, textContent);

            log.info("=== Stratum XML Payload ===");
            log.info("XML (first 500 chars): {}", xmlPayload.length() > 500 ? xmlPayload.substring(0, 500) + "..." : xmlPayload);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.add("Accept-Charset", "utf-8");

            Map<String, String> formData = new HashMap<>();
            formData.put("newValues", xmlPayload);

            String body = formData.entrySet().stream().map(entry -> {
                try {
                    return entry.getKey() + "=" +
                            URLEncoder.encode(entry.getValue(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.joining("&"));

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String fullUrl = stratumApiUrl + "?securityKey=" + stratumApiSecurityKey;

            log.info("Sending request to Stratum API: {}", fullUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Email sent successfully: {}", response);
        } catch (Exception e) {
            log.error("Failed to send email via Stratum: {}", e.getMessage());
            throw new RuntimeException("Failed to send email via Stratum", e);
        }
    }

    private String buildStratumXmlPayload(String toEmail, String toName, String subject, String textContent) {
        StringBuilder xml = new StringBuilder();
        xml.append("<AddEmail>");
        xml.append("<MemberNumber>").append(getMemberNumberByEmail(toEmail)).append("</MemberNumber>");
        xml.append("<Subject>").append(escapeXml(subject)).append("</Subject>");
        xml.append("<Body>");
        xml.append("<Value><![CDATA[").append(textContent).append("]]></Value>");
        xml.append("</Body>");
        xml.append("<MailType>O</MailType>");
        xml.append("<FromAddress>").append(senderEmail).append("</FromAddress>");
        xml.append("<MemberAddress>").append(toEmail).append("</MemberAddress>");
        xml.append("<MailName>").append(escapeXml(toName)).append("</MailName>");
        xml.append("</AddEmail>");

        return xml.toString();
    }

    private String getMemberNumberByEmail(String email) {
        // First try to find membershipNumber from EventMember table (BMM system primary data source)
        List<EventMember> eventMembers = eventMemberRepository.findByPrimaryEmail(email);
        if (!eventMembers.isEmpty()) {
            return eventMembers.get(0).getMembershipNumber();
        }

        // Fallback to Member table if not found in EventMember
        return memberRepository.findByPrimaryEmail(email)
                .map(Member::getMembershipNumber)
                .orElse("UNKNOWN"); // 使用 UNKNOWN 而不是空字符串
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @Override
    public EmailResponse sendBulkEmails(BulkEmailRequest request) {

        Long adminId = 1L;
        String adminUsername = "admin";

        List<Member> members;
        if ("custom".equals(request.getRecipientType()) && request.getCustomRecipients() != null && !request.getCustomRecipients().isEmpty()) {
            members = memberRepository.findByPrimaryEmailIn(request.getCustomRecipients());
        } else {
            members = getMembersByCategory(request.getRecipientType());
        }

        int total = members.size();
        int sent = 0;
        int failed = 0;

        for (Member member : members) {
            try {
                Map<String, String> variables = new HashMap<>();
                variables.put("name", member.getName());
                variables.put("membershipNumber", member.getMembershipNumber());
                variables.put("verificationCode", member.getVerificationCode());
                if (member.getToken() != null) {
                    // Fix: Use correct registration URL path
                    String registrationLink = "https://events.etu.nz/register?token=" + member.getToken();
                    try {
                        List<EventMember> eventMembers = eventMemberRepository.findByMembershipNumber(member.getMembershipNumber());
                        if (!eventMembers.isEmpty()) {
                            Long eventId = eventMembers.get(0).getEvent().getId();
                            registrationLink += "&event=" + eventId;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to get event for member {}: {}", member.getMembershipNumber(), e.getMessage());
                    }
                    variables.put("registrationLink", registrationLink);
                } else {
                    variables.put("registrationLink", "https://events.etu.nz/register");
                }
                variables.put("isAttending", String.valueOf(member.getIsAttending()));
                variables.put("isSpecialVote", String.valueOf(member.getIsSpecialVote()));
                // isSpecialMember field removed

                String personalizedContent = replaceVariables(request.getEmailContent(), variables);

                boolean success = true;
                String errorMsg = null;

                try {
                    sendEmailWithAttachments(member.getPrimaryEmail(), member.getName(), request.getSubject(),
                            personalizedContent, request.getAttachments());
                } catch (Exception e) {
                    success = false;
                    errorMsg = e.getMessage();
                    log.error("Failed to send email to {}: {}", member.getPrimaryEmail(), e.getMessage());
                }

                NotificationLog emailLog = NotificationLog.builder()
                        .member(member)
                        .notificationType(NotificationLog.NotificationType.EMAIL)
                        .recipient(member.getPrimaryEmail())
                        .recipientName(member.getName())
                        .subject(request.getSubject())
                        .content(personalizedContent)
                        .sentTime(LocalDateTime.now())
                        .isSuccessful(success)
                        .errorMessage(errorMsg)
                        .emailType("EMAIL")
                        .adminId(adminId)
                        .adminUsername(adminUsername)
                        .build();

                notificationLogRepository.save(emailLog);

                if (success) {
                    sent++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", member.getPrimaryEmail(), e.getMessage());
                failed++;
            }
        }

        return new EmailResponse(total, sent, failed);
    }

    private void sendEmailWithAttachments(String toEmail, String toName, String subject,
                                          String textContent, List<MultipartFile> attachments) {
        try {
            subject = toAsciiOnly(subject);
            textContent = toAsciiOnly(textContent);
            toName = toAsciiOnly(toName);

            StringBuilder xml = new StringBuilder();
            xml.append("<AddEmail>");
            xml.append("<MemberNumber>").append(getMemberNumberByEmail(toEmail)).append("</MemberNumber>");
            xml.append("<Subject>").append(escapeXml(subject)).append("</Subject>");
            xml.append("<Body>");
            xml.append("<Value><![CDATA[").append(textContent).append("]]></Value>");
            xml.append("</Body>");
            xml.append("<MailType>O</MailType>");
            xml.append("<FromAddress>").append(senderEmail).append("</FromAddress>");
            xml.append("<MemberAddress>").append(toEmail).append("</MemberAddress>");
            xml.append("<MailName>").append(escapeXml(toName)).append("</MailName>");

            if (attachments != null && !attachments.isEmpty()) {
                xml.append("<Attachments>");
                for (MultipartFile attachment : attachments) {
                    if (attachment != null && !attachment.isEmpty()) {

                        xml.append("<Attachment>");
                        xml.append("<FileName>").append(escapeXml(attachment.getOriginalFilename())).append("</FileName>");
                        xml.append("<ContentType>").append(escapeXml(attachment.getContentType())).append("</ContentType>");
                        xml.append("<Content>").append(Base64.getEncoder().encodeToString(attachment.getBytes())).append("</Content>");
                        xml.append("</Attachment>");
                    }
                }
                xml.append("</Attachments>");
            }

            xml.append("</AddEmail>");

            String xmlPayload = xml.toString();

            log.debug("Attachment XML payload length: {}", xmlPayload.length());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, String> formData = new HashMap<>();
            formData.put("newValues", xmlPayload);

            String body = formData.entrySet().stream().map(entry -> {
                try {
                    return entry.getKey() + "=" +
                            URLEncoder.encode(entry.getValue(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.joining("&"));

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String fullUrl = stratumApiUrl + "?securityKey=" + stratumApiSecurityKey;

            log.info("Sending request to Stratum API with attachments: {}", fullUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("Email with attachments sent successfully: {}", response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to send email with attachments via Stratum: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send email with attachments via Stratum", e);
        }
    }

    private List<Member> getMembersByCategory(String category) {
        List<Member> allMembers;
        switch (category) {
            case "all":
                allMembers = memberRepository.findAll();
                break;
            case "registered":
                allMembers = memberRepository.findByHasRegisteredTrue();
                break;
            case "unregistered":
                allMembers = memberRepository.findByHasRegisteredFalse();
                break;
            case "attending":
                allMembers = memberRepository.findByIsAttendingTrue();
                break;
            case "specialVote":
                allMembers = memberRepository.findByIsSpecialVoteTrue();
                break;
            // Member type distinction removed - use event-specific targeting instead
            case "voted":
                allMembers = memberRepository.findByHasVotedTrue();
                break;
            case "has_email":
                allMembers = memberRepository.findByHasEmailTrue();
                break;
            case "sms_only":
                // SMS only用户：有手机号但没有有效邮箱
                allMembers = memberRepository.findAll().stream()
                        .filter(m -> !m.getHasEmail() && m.getHasMobile())
                        .collect(Collectors.toList());
                break;
            default:
                allMembers = new ArrayList<>();
                break;
        }

        // 关键改进：Email服务只返回有有效邮箱的会员
        return allMembers.stream()
                .filter(member -> member.getHasEmail() &&
                        StringUtils.hasText(member.getPrimaryEmail()) &&
                        !member.getPrimaryEmail().contains("@temp-email.etu.nz"))
                .collect(Collectors.toList());
    }
    private String replaceSpecialCharacters(String text) {
        if (text == null ) return "";
        text = text.replace("ū", "u");
        return text;
    }
    private String toAsciiOnly(String text) {
        if (text == null) return "";

        text = text.replace("ū", "u");
        text = text.replace("ā", "a");
        text = text.replace("ē", "e");
        text = text.replace("ī", "i");
        text = text.replace("ō", "o");
        text = text.replace("Ū", "U");
        text = text.replace("Ā", "A");
        text = text.replace("Ē", "E");
        text = text.replace("Ī", "I");
        text = text.replace("Ō", "O");

        return text.replaceAll("[^\\x00-\\x7F]", "");
    }
    private String replaceVariables(String content, Map<String, String> variables) {
        String result = content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? toAsciiOnly(entry.getValue()) : "";
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        result = result.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\">$1</a>");
        return result;
    }

    // New method to support provider selection (Stratum or Mailjet)
    public void sendEmailWithProvider(String toEmail, String toName, String subject,
                                      String content, String provider) {
        log.info("Sending email with provider: {} to: {}", provider, toEmail);

        if ("MAILJET".equalsIgnoreCase(provider)) {
            // Use Mailjet for sending
            mailjetService.sendEmail(toEmail, toName, subject, content);
        } else {
            // Default to Stratum (existing functionality)
            sendSimpleEmail(toEmail, toName, subject, content);
        }
    }
}
