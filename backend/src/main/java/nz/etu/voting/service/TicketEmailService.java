package nz.etu.voting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.EmailService;
import nz.etu.voting.service.QRCodeService;
import nz.etu.voting.service.SmsService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import nz.etu.voting.service.EmailService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

//专门的票据邮件服务 - 像电影票一样的体验 + 在会员确认出席后自动发送精美的邮件票据
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketEmailService {

    private final EmailService emailService;
    private final SmsService smsService;
    private final EventMemberRepository eventMemberRepository;
    private final RabbitTemplate rabbitTemplate;
    private final QRCodeService qrCodeService;
    private final RestTemplate restTemplate;

    @Value("${app.rabbitmq.queue.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queue.sms}")
    private String smsQueue;

    @Value("${app.api.baseUrl:http://localhost:8082}")
    private String apiBaseUrl;

    @Value("${stratum.api.url}")
    private String stratumApiUrl;

    @Value("${stratum.api.securityKey}")
    private String stratumApiSecurityKey;

    @Value("${email.sender.address:events@etu.nz}")
    private String senderEmail;

    @Value("${email.default.provider:STRATUM}")
    private String defaultEmailProvider;

    // 移除重复的配置，使用统一的emailQueue

    //    发送票据邮件 - 像电影票一样的体验 + 在会员确认出席后自动发送
    public void sendTicketEmail(EventMember eventMember) {
        try {
            log.info("Sending ticket email to member: {} for event: {}",
                    eventMember.getMembershipNumber(), eventMember.getEvent().getName());

            // Ensure member has a ticket token for unique ticket URLs
            if (eventMember.getTicketToken() == null) {
                eventMember.setTicketToken(UUID.randomUUID());
                eventMember.setTicketStatus("GENERATED");
                eventMember = eventMemberRepository.save(eventMember);
                log.info("Generated ticket token for member {} during email sending", eventMember.getMembershipNumber());
            }

            String ticketUrl = String.format("https://events.etu.nz/ticket?token=%s", eventMember.getTicketToken());

            String subject = String.format("Your Ticket for %s - %s",
                    eventMember.getEvent().getName(),
                    eventMember.getEvent().getEventCode());

            String message = buildBMMTicketEmailContentForStratum(eventMember, ticketUrl);

            // Send via send-advanced API endpoint
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("eventId", eventMember.getEvent().getId());
            requestBody.put("subject", subject);
            requestBody.put("content", message);
            requestBody.put("emailType", "EVENT_TICKET");

            Map<String, Object> criteria = new HashMap<>();
            criteria.put("memberIds", List.of(eventMember.getId()));
            requestBody.put("criteria", criteria);

            // Get current admin token from request context
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest currentRequest = attributes.getRequest();
                    String authHeader = currentRequest.getHeader("Authorization");
                    if (authHeader != null) {
                        headers.set("Authorization", authHeader);
                        log.info("Using admin token for internal API call");
                    }
                }
            } catch (Exception e) {
                log.warn("Could not get admin token from request context: {}", e.getMessage());
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiBaseUrl + "/api/admin/email/send-advanced",
                    request,
                    Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK ||
                    !"success".equals(response.getBody().get("status"))) {
                throw new RuntimeException("Failed to send email via API: " + response.getBody());
            }

//            Mark ticket email as sent
            eventMember.setQrCodeEmailSent(true);
            eventMember.setLastActivityAt(LocalDateTime.now());
            eventMemberRepository.save(eventMember);

            log.info("Ticket email sent successfully to member: {}", eventMember.getMembershipNumber());

        } catch (Exception e) {
            log.error("Failed to send ticket email to member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to send ticket email", e);
        }
    }

    //    批量发送票据邮件给所有确认出席的会员
    public int sendTicketEmailsToAttendingMembers(Event event) {
        try {
            log.info("Starting batch ticket email sending for event: {}", event.getName());

            List<EventMember> attendingMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);
            List<EventMember> needTicketEmail = attendingMembers.stream()
                    .filter(em -> !em.getQrCodeEmailSent() && em.getPrimaryEmail() != null && !em.getPrimaryEmail().isEmpty())
                    .collect(Collectors.toList());

            log.info("Found {} attending members needing ticket emails", needTicketEmail.size());

            int sentCount = 0;
            for (EventMember member : needTicketEmail) {
                try {
                    sendTicketEmail(member);
                    sentCount++;

//                    减少延迟以提高发送速度 - 每100封邮件暂停100ms
                    if (sentCount % 100 == 0) {
                        Thread.sleep(100); // 100ms pause every 100 emails
                        log.info("Sent {} ticket emails so far...", sentCount);
                    }

                } catch (Exception e) {
                    log.error("Failed to send ticket email to member {}: {}",
                            member.getMembershipNumber(), e.getMessage());
                }
            }

            log.info("Batch ticket email sending completed. Sent {} emails", sentCount);
            return sentCount;

        } catch (Exception e) {
            log.error("Failed batch ticket email sending for event {}: {}", event.getName(), e.getMessage());
            throw new RuntimeException("Batch ticket email sending failed", e);
        }
    }

    //    在会员确认出席时自动触发票据邮件
    public void sendTicketEmailOnAttendanceConfirmation(EventMember eventMember) {
        if (eventMember.getIsAttending() && !eventMember.getQrCodeEmailSent()) {
            log.info("Auto-sending ticket email for member {} who just confirmed attendance",
                    eventMember.getMembershipNumber());
            sendTicketEmail(eventMember);
        }
    }

    //    构建票据邮件模板 - 像电影票一样的专业设计
    private String buildTicketEmailTemplate(EventMember eventMember, String ticketUrl) {
        Event event = eventMember.getEvent();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>Your Event Ticket</title>");
        html.append("<style>");
        html.append("body { font-family: 'Arial', sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }");
        html.append(".ticket-container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 25px rgba(0,0,0,0.1); }");
        html.append(".ticket-header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; }");
        html.append(".event-title { font-size: 28px; font-weight: bold; margin-bottom: 10px; }");
        html.append(".event-subtitle { font-size: 16px; opacity: 0.9; }");
        html.append(".ticket-body { padding: 30px; }");
        html.append(".member-info { background: #f8f9fa; border-radius: 8px; padding: 20px; margin-bottom: 25px; }");
        html.append(".info-row { display: flex; justify-content: space-between; margin-bottom: 10px; }");
        html.append(".info-label { font-weight: bold; color: #666; }");
        html.append(".info-value { color: #333; }");
        html.append(".qr-section { text-align: center; background: #fff; border: 2px dashed #e0e0e0; border-radius: 8px; padding: 25px; margin: 25px 0; }");
        html.append(".qr-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 15px; }");
        html.append(".ticket-button { display: inline-block; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; margin: 10px; }");
        html.append(".instructions { background: #e3f2fd; border-left: 4px solid #2196f3; padding: 20px; margin: 25px 0; border-radius: 0 8px 8px 0; }");
        html.append(".instructions h3 { color: #1976d2; margin-top: 0; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 14px; color: #666; }");
        html.append("@media (max-width: 600px) { .info-row { flex-direction: column; } .ticket-button { width: 90%; } }");
        html.append("</style>");
        html.append("</head><body>");

//        Header
        html.append("<div class=\"ticket-container\">");
        html.append("<div class=\"ticket-header\">");
        html.append("<div class=\"event-title\">").append(escapeHtml(event.getName())).append("</div>");
        html.append("<div class=\"event-subtitle\">Event Code: ").append(escapeHtml(event.getEventCode())).append("</div>");
        html.append("</div>");

//        Body
        html.append("<div class=\"ticket-body\">");

//        Member Information
        html.append("<div class=\"member-info\">");
        html.append("<h3 style=\"margin-top: 0; color: #333;\">Ticket Holder Information</h3>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Name:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getName())).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Membership Number:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getMembershipNumber())).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Email:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getPrimaryEmail())).append("</span>");
        html.append("</div>");
        if (eventMember.getRegionDesc() != null) {
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"info-label\">Region:</span>");
            html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getRegionDesc())).append("</span>");
            html.append("</div>");
        }
        html.append("</div>");

//        Event Details
        html.append("<div class=\"member-info\">");
        html.append("<h3 style=\"margin-top: 0; color: #333;\">📅 Event Details</h3>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Event Type:</span>");
        html.append("<span class=\"info-value\">").append(getEventTypeDisplay(event.getEventType())).append("</span>");
        html.append("</div>");
        // 注释掉显示错误日期和场地的部分
        /*
        if (event.getEventDate() != null) {
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"info-label\">Date & Time:</span>");
            html.append("<span class=\"info-value\">").append(formatDateTime(event.getEventDate())).append("</span>");
            html.append("</div>");
        }
        if (event.getVenue() != null) {
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"info-label\">Venue:</span>");
            html.append("<span class=\"info-value\">").append(escapeHtml(event.getVenue())).append("</span>");
            html.append("</div>");
        }
        */

        // Add session time if available - 注释掉以避免显示错误信息
        /*
        String sessionTime = extractSessionTime(eventMember);
        if (sessionTime != null) {
            html.append("<div class=\"info-row\">");
            html.append("<span class=\"info-label\">Session Time:</span>");
            html.append("<span class=\"info-value\">").append(sessionTime).append("</span>");
            html.append("</div>");
        }
        */
        html.append("</div>");

//        QR Code Section
        html.append("<div class=\"qr-section\">");
        html.append("<div class=\"qr-title\">🔗 Your Digital Ticket</div>");
        html.append("<p style=\"color: #666; margin-bottom: 20px;\">Click the button below to view your QR code ticket. Save this page or take a screenshot for easy access at the venue.</p>");
        html.append("<a href=\"").append(ticketUrl).append("\" class=\"ticket-button\">VIEW MY TICKET</a>");
        html.append("<br><small style=\"color: #888; font-size: 12px;\">Ticket Link: ").append(ticketUrl).append("</small>");
        html.append("</div>");

//        Instructions
        html.append("<div class=\"instructions\">");
        html.append("<h3>📱 How to Use Your Ticket</h3>");
        html.append("<ul style=\"margin: 0; padding-left: 20px;\">");
        html.append("<li><strong>Click the button above</strong> to open your digital ticket</li>");
        html.append("<li><strong>Save the page</strong> to your phone's home screen for quick access</li>");
        html.append("<li><strong>Screenshot the QR code</strong> as a backup</li>");
        html.append("<li><strong>Present your QR code</strong> at the venue for check-in</li>");
        html.append("<li><strong>Keep this email</strong> for your records</li>");
        html.append("</ul>");
        html.append("</div>");

        html.append("</div>"); // End ticket-body

//        Footer
        html.append("<div class=\"footer\">");
        html.append("<p><strong>E tū Union</strong><br>");
        html.append("Events@etu.nz | 0800 1 UNION | www.etu.nz</p>");
        html.append("<p style=\"font-size: 12px; color: #999;\">This is an automated message. Please do not reply to this email.</p>");
        html.append("</div>");

        html.append("</div>"); // End ticket-container
        html.append("</body></html>");

        return html.toString();
    }

    //    格式化事件类型显示
    private String getEventTypeDisplay(Event.EventType eventType) {
        switch (eventType) {
            case BMM_VOTING: return "Biennial Membership Meeting";
            case SPECIAL_CONFERENCE: return "Special Conference";
            case GENERAL_MEETING: return "General Meeting";
            case ANNUAL_MEETING: return "Annual Meeting";
            case SURVEY_MEETING: return "Survey Meeting";
            case BALLOT_VOTING: return "Ballot Voting";
            case WORKSHOP: return "Workshop";
            case UNION_MEETING: return "Union Meeting";
            default: return eventType.toString().replace("_", " ");
        }
    }

    //    格式化日期时间
    private String formatDateTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm a");
        return dateTime.format(formatter);
    }

    //    HTML转义
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    // TICKET: BMM专用：确认出席时发送ticket (使用Mailjet)
    public void sendBMMTicketOnConfirmation(EventMember eventMember) {
        try {
            log.info("Processing BMM ticket for member {} who confirmed attendance",
                    eventMember.getMembershipNumber());

            // Generate ticket token if not exists
            if (eventMember.getTicketToken() == null) {
                eventMember.setTicketToken(UUID.randomUUID());
                eventMember.setTicketStatus("GENERATED");
                eventMember.setTicketGeneratedAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                log.info("Generated ticket token for member {}", eventMember.getMembershipNumber());
            }

            // Check if member has valid email
            boolean hasValidEmail = eventMember.getPrimaryEmail() != null &&
                    !eventMember.getPrimaryEmail().trim().isEmpty() &&
                    !eventMember.getPrimaryEmail().contains("@temp-email.etu.nz");

            log.info("Email validation for member {}: email={}, hasValidEmail={}",
                    eventMember.getMembershipNumber(), eventMember.getPrimaryEmail(), hasValidEmail);

            if (hasValidEmail) {
                // Try to send email ticket
                try {
                    log.info("Proceeding to send email ticket for member {}", eventMember.getMembershipNumber());
                    sendBMMTicketEmailWithMailjet(eventMember);
                    log.info("Email ticket sent successfully for member {}", eventMember.getMembershipNumber());
                } catch (Exception emailError) {
                    // If email sending fails, still keep the ticket as generated
                    log.error("Failed to send email for member {}, but ticket is still valid: {}",
                            eventMember.getMembershipNumber(), emailError.getMessage());
                    eventMember.setTicketStatus("GENERATED");
                    eventMember.setTicketSentMethod("EMAIL_FAILED_WEBSITE_ONLY");
                    eventMemberRepository.save(eventMember);
                }
            } else {
                // No email available - ticket is generated and available on website
                log.info("Member {} has no valid email. Ticket generated and available on website only. Email: {}",
                        eventMember.getMembershipNumber(), eventMember.getPrimaryEmail());
                eventMember.setTicketStatus("GENERATED");
                eventMember.setTicketSentMethod("WEBSITE_ONLY");
                eventMemberRepository.save(eventMember);
            }

        } catch (Exception e) {
            // Only mark as FAILED if ticket generation itself failed
            log.error("Critical error processing BMM ticket for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage(), e);
            eventMember.setTicketStatus("GENERATION_FAILED");
            eventMemberRepository.save(eventMember);
            throw new RuntimeException("Failed to process BMM ticket", e);
        }
    }

    // EMAIL: BMM邮件版ticket发送 (确认出席时使用Mailjet)
    private void sendBMMTicketEmailWithMailjet(EventMember eventMember) {
        try {
            log.info("Starting sendBMMTicketEmailWithMailjet for member: {}, email: {}",
                    eventMember.getMembershipNumber(), eventMember.getPrimaryEmail());

            String ticketUrl = String.format("https://events.etu.nz/ticket?token=%s",
                    eventMember.getTicketToken());
            log.info("Generated ticket URL: {}", ticketUrl);

            String subject = String.format("Your BMM Ticket - %s Region",
                    eventMember.getAssignedRegion() != null ?
                            eventMember.getAssignedRegion() : eventMember.getRegionDesc());
            log.info("Email subject: {}", subject);

            String emailContent = buildBMMTicketEmailContentForStratum(eventMember, ticketUrl);
            log.info("Email content length: {} characters", emailContent.length());

            // Force Mailjet for confirmation emails
            log.info("Using Mailjet for confirmation ticket email");

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("recipient", eventMember.getPrimaryEmail());
            emailData.put("recipientName", eventMember.getName());
            emailData.put("subject", subject);
            emailData.put("content", emailContent);
            emailData.put("eventMemberId", eventMember.getId());
            emailData.put("memberId", eventMember.getId());
            emailData.put("membershipNumber", eventMember.getMembershipNumber());
            emailData.put("templateCode", "BMM_TICKET");
            emailData.put("notificationType", "EMAIL");
            // Email provider selection for confirmation emails
            // Option 1: Force Mailjet (current)
//            emailData.put("provider", "MAILJET");  // Force Mailjet

            // Option 2: Force Stratum (uncomment to use)
             emailData.put("provider", "STRATUM");  // Force Stratum

            // Option 3: Use default from config (uncomment to use)
            // emailData.put("provider", defaultEmailProvider);  // Use configured provider from application.properties

            rabbitTemplate.convertAndSend(emailQueue, emailData);

            // Update ticket status
            eventMember.setTicketStatus("EMAIL_SENT");
            eventMember.setQrCodeEmailSent(true);
            eventMember.setLastActivityAt(LocalDateTime.now());
            eventMemberRepository.save(eventMember);
            log.info("BMM ticket email sent via Mailjet for member: {}", eventMember.getMembershipNumber());

        } catch (Exception e) {
            log.error("Failed to send BMM ticket email for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to send BMM ticket email", e);
        }
    }

    // EMAIL: BMM邮件版ticket发送 (默认使用配置的provider)
    private void sendBMMTicketEmail(EventMember eventMember) {
        try {
            log.info("Starting sendBMMTicketEmail for member: {}, email: {}",
                    eventMember.getMembershipNumber(), eventMember.getPrimaryEmail());

            String ticketUrl = String.format("https://events.etu.nz/ticket?token=%s",
                    eventMember.getTicketToken());
            log.info("Generated ticket URL: {}", ticketUrl);

            String subject = String.format("Your BMM Ticket - %s Region",
                    eventMember.getAssignedRegion() != null ?
                            eventMember.getAssignedRegion() : eventMember.getRegionDesc());
            log.info("Email subject: {}", subject);

            String emailContent = buildBMMTicketEmailContentForStratum(eventMember, ticketUrl);
            log.info("Email content length: {} characters", emailContent.length());

            // Check if we should use Mailjet instead of Stratum
            if ("MAILJET".equalsIgnoreCase(defaultEmailProvider)) {
                // Send via Mailjet through RabbitMQ
                Map<String, Object> emailData = new HashMap<>();
                emailData.put("recipient", eventMember.getPrimaryEmail());
                emailData.put("recipientName", eventMember.getName());
                emailData.put("subject", subject);
                emailData.put("content", emailContent);
                emailData.put("eventMemberId", eventMember.getId());
                emailData.put("memberId", eventMember.getId());
                emailData.put("membershipNumber", eventMember.getMembershipNumber());
                emailData.put("templateCode", "BMM_TICKET");
                emailData.put("notificationType", "EMAIL");
                emailData.put("provider", "MAILJET");

                rabbitTemplate.convertAndSend(emailQueue, emailData);

                // Update ticket status
                eventMember.setTicketStatus("EMAIL_SENT");
                eventMember.setQrCodeEmailSent(true);
                eventMember.setLastActivityAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                log.info("BMM ticket email sent via Mailjet for member: {}", eventMember.getMembershipNumber());
            } else {
                // Send via Stratum through RabbitMQ (consistent with other emails)
                log.info("Using Stratum via RabbitMQ to send email");

                Map<String, Object> emailData = new HashMap<>();
                emailData.put("recipient", eventMember.getPrimaryEmail());
                emailData.put("recipientName", eventMember.getName());
                emailData.put("subject", subject);
                emailData.put("content", emailContent);
                emailData.put("eventMemberId", eventMember.getId());
                emailData.put("memberId", eventMember.getId());
                emailData.put("membershipNumber", eventMember.getMembershipNumber());
                emailData.put("templateCode", "BMM_TICKET");
                emailData.put("notificationType", "EMAIL");
                emailData.put("provider", "STRATUM");

                rabbitTemplate.convertAndSend(emailQueue, emailData);

                // Update ticket status to PENDING until confirmed by consumer
                eventMember.setTicketStatus("EMAIL_QUEUED");
                eventMember.setQrCodeEmailSent(false); // Will be set to true by consumer on success
                eventMember.setLastActivityAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                log.info("BMM ticket email queued via Stratum for member: {}", eventMember.getMembershipNumber());
            }

        } catch (Exception e) {
            log.error("Failed to send BMM ticket email for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage(), e);
            log.error("Full stack trace:", e);
            eventMember.setTicketStatus("EMAIL_FAILED");
            eventMemberRepository.save(eventMember);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    // 📱 BMM SMS版ticket发送
    private void sendBMMTicketSMS(EventMember eventMember) {
        try {
            String ticketUrl = String.format("https://events.etu.nz/ticket?token=%s",
                    eventMember.getTicketToken());

            String smsContent = buildBMMTicketSMSContent(eventMember, ticketUrl);

            // Send via send-advanced API endpoint
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("eventId", eventMember.getEvent().getId());
            requestBody.put("message", smsContent);
            requestBody.put("smsType", "BMM_TICKET");

            Map<String, Object> criteria = new HashMap<>();
            criteria.put("memberIds", List.of(eventMember.getId()));
            requestBody.put("criteria", criteria);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiBaseUrl + "/api/admin/sms/send-advanced",
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK &&
                    "success".equals(response.getBody().get("status"))) {
                // Update ticket status
                eventMember.setTicketStatus("SMS_SENT");
                eventMember.setSmsSent(true);
                eventMember.setLastActivityAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                log.info("BMM ticket SMS sent via API for member: {}", eventMember.getMembershipNumber());
            } else {
                throw new RuntimeException("Failed to send SMS via API: " + response.getBody());
            }

        } catch (Exception e) {
            log.error("Failed to send BMM ticket SMS for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage());
            eventMember.setTicketStatus("SMS_FAILED");
            eventMemberRepository.save(eventMember);
        }
    }

    // TICKET: 构建BMM邮件版ticket内容 (HTML版本 - 已废弃，改用Stratum纯文本)
    @Deprecated
    private String buildBMMTicketEmailContent(EventMember eventMember, String ticketUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        html.append("<title>Your BMM Ticket</title>");
        html.append("<style>");
        html.append("body { font-family: 'Arial', sans-serif; margin: 0; padding: 20px; background-color: #f5f5f5; }");
        html.append(".ticket-container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 25px rgba(0,0,0,0.1); }");
        html.append(".ticket-header { background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%); color: white; padding: 30px; text-align: center; }");
        html.append(".event-title { font-size: 28px; font-weight: bold; margin-bottom: 10px; }");
        html.append(".event-subtitle { font-size: 16px; opacity: 0.9; }");
        html.append(".ticket-body { padding: 30px; }");
        html.append(".member-info { background: #f8f9fa; border-radius: 8px; padding: 20px; margin-bottom: 25px; }");
        html.append(".info-row { display: flex; justify-content: space-between; margin-bottom: 10px; }");
        html.append(".info-label { font-weight: bold; color: #666; }");
        html.append(".info-value { color: #333; }");
        html.append(".qr-section { text-align: center; background: #fff; border: 2px dashed #e0e0e0; border-radius: 8px; padding: 25px; margin: 25px 0; }");
        html.append(".qr-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 15px; }");
        html.append(".ticket-button { display: inline-block; background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%); color: white; padding: 15px 30px; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 16px; margin: 10px; }");
        html.append(".instructions { background: #e3f2fd; border-left: 4px solid #2196f3; padding: 20px; margin: 25px 0; border-radius: 0 8px 8px 0; }");
        html.append(".instructions h3 { color: #1976d2; margin-top: 0; }");
        html.append(".footer { background: #f8f9fa; padding: 20px; text-align: center; font-size: 14px; color: #666; }");
        html.append(".important { background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 8px; padding: 15px; margin: 20px 0; }");
        html.append("@media (max-width: 600px) { .info-row { flex-direction: column; } .ticket-button { width: 90%; } }");
        html.append("</style>");
        html.append("</head><body>");

        // Header
        html.append("<div class=\"ticket-container\">");
        html.append("<div class=\"ticket-header\">");
        html.append("<div class=\"event-title\">BMM Ticket</div>");
        html.append("<div class=\"event-subtitle\">2025 E tu Biennial Membership Meeting</div>");
        html.append("</div>");

        // Body
        html.append("<div class=\"ticket-body\">");

        // Member Information
        html.append("<div class=\"member-info\">");
        html.append("<h3 style=\"margin-top: 0; color: #333;\">Ticket Holder</h3>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Name:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getName())).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Membership Number:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getMembershipNumber())).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Region:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getAssignedRegion() != null ?
                eventMember.getAssignedRegion() :
                eventMember.getRegionDesc())).append("</span>");
        html.append("</div>");
        html.append("</div>");

        // Meeting Details
        html.append("<div class=\"member-info\">");
        html.append("<h3 style=\"margin-top: 0; color: #333;\">Your Meeting Details</h3>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Location:</span>");
        html.append("<span class=\"info-value\">").append(escapeHtml(eventMember.getAssignedVenue() != null ?
                eventMember.getAssignedVenue() : "TBA")).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Date:</span>");
        html.append("<span class=\"info-value\">").append(formatDate(eventMember.getAssignedDateTime())).append("</span>");
        html.append("</div>");
        html.append("<div class=\"info-row\">");
        html.append("<span class=\"info-label\">Time:</span>");
        html.append("<span class=\"info-value\">").append(formatTime(eventMember.getAssignedDateTime())).append("</span>");
        html.append("</div>");
        html.append("</div>");

        // Important Notice
        html.append("<div class=\"important\">");
        html.append("<h3 style=\"margin-top: 0; color: #856404;\">IMPORTANT: Bring This Ticket</h3>");
        html.append("<p style=\"margin-bottom: 0; color: #856404;\">You MUST present this ticket (printed or on your phone) at the venue for entry. Without a valid ticket, you may not be able to enter the meeting.</p>");
        html.append("</div>");

        // QR Code Section
        html.append("<div class=\"qr-section\">");
        html.append("<div class=\"qr-title\">Your Digital Ticket</div>");
        html.append("<p style=\"color: #666; margin-bottom: 20px;\">Click the button below to access your QR code ticket. Save this page or take a screenshot for easy access at the venue.</p>");
        html.append("<a href=\"").append(ticketUrl).append("\" class=\"ticket-button\">VIEW MY TICKET & QR CODE</a>");
        html.append("<br><small style=\"color: #888; font-size: 12px;\">Ticket URL: ").append(ticketUrl).append("</small>");
        html.append("</div>");

        // Instructions
        html.append("<div class=\"instructions\">");
        html.append("<h3>How to Use Your Ticket</h3>");
        html.append("<ul style=\"margin: 0; padding-left: 20px;\">");
        html.append("<li><strong>Click the button above</strong> to open your digital ticket</li>");
        html.append("<li><strong>Save the page</strong> to your phone's home screen for offline access</li>");
        html.append("<li><strong>Screenshot the QR code</strong> as a backup</li>");
        html.append("<li><strong>Present your ticket</strong> at the venue entrance for check-in</li>");
        html.append("<li><strong>Arrive 15 minutes early</strong> for smooth check-in</li>");
        html.append("</ul>");
        html.append("</div>");

        html.append("</div>"); // End ticket-body

        // Footer
        html.append("<div class=\"footer\">");
        html.append("<p><strong>E tu Union</strong><br>");
        html.append("Email: support@etu.nz | Phone: 0800 1 UNION | Web: www.etu.nz</p>");
        html.append("<p style=\"font-size: 12px; color: #999;\">This is your official BMM ticket. Please keep this email for your records.</p>");
        html.append("</div>");

        html.append("</div>"); // End ticket-container
        html.append("</body></html>");

        return html.toString();
    }

    // 📱 构建BMM SMS版ticket内容
    private String buildBMMTicketSMSContent(EventMember eventMember, String ticketUrl) {
        // Simple message to avoid all special characters
        return "Hi, this is your BMM ticket!";
    }

    // 构建统一的邮件内容 - 用于Stratum和Mailjet
    private String buildBMMTicketEmailContentForStratum(EventMember eventMember, String ticketUrl) {
        // Build simplified template - just essential info and ticket link
        String template = "Kia ora {{name}},\n\n" +
                "Your attendance for the 2025 E tū Biennial Membership Meeting has been confirmed.\n\n" +
                "======================================\n" +
                "YOUR TICKET IS READY\n" +
                "======================================\n\n" +
                "Name: {{name}}\n" +
                "Member ID: {{membershipNumber}}\n" +
                "Region: {{region}}\n\n" +
                "--------------------------------------\n" +
                "ACCESS YOUR DIGITAL TICKET:\n" +
                "{{ticketUrl}}\n" +
                "--------------------------------------\n\n" +
                "This ticket contains all your meeting details including:\n" +
                "- Venue location and address\n" +
                "- Date and time of your meeting\n" +
                "- QR code for check-in\n\n" +
                "IMPORTANT INSTRUCTIONS:\n" +
                "1. Click the link above to view your complete ticket\n" +
                "2. Save the ticket to your phone or take a screenshot\n" +
                "3. You MUST bring this ticket to the venue for check-in\n\n" +
                "--------------------------------------\n" +
                "NEED HELP?\n" +
                "--------------------------------------\n" +
                "Email: support@etu.nz\n" +
                "Phone: 0800 1 UNION (0800 186 466)\n" +
                "Website: www.etu.nz\n\n" +
                "Thank you for confirming your attendance.\n" +
                "We look forward to seeing you at the meeting.\n\n" +
                "Ngā mihi,\n" +
                "E tū Union\n";

        // Replace variables with actual values - only essential info
        String content = template
                .replace("{{name}}", eventMember.getName() != null ? eventMember.getName() : "Member")
                .replace("{{membershipNumber}}", eventMember.getMembershipNumber() != null ? eventMember.getMembershipNumber() : "")
                .replace("{{region}}", eventMember.getRegionDesc() != null ? eventMember.getRegionDesc() : "")
                .replace("{{ticketUrl}}", ticketUrl);

        return content;
    }

    // 构建Stratum邮件XML
    private String buildStratumEmailXml(String memberNumber, String email, String name,
                                        String subject, String content) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<AddEmail>");
        xml.append("<MemberNumber>").append(escapeXml(memberNumber)).append("</MemberNumber>");
        xml.append("<Subject>").append(escapeXml(subject)).append("</Subject>");
        xml.append("<Body>");
        xml.append("<Value><![CDATA[").append(content).append("]]></Value>");
        xml.append("</Body>");
        xml.append("<MailType>O</MailType>");
        xml.append("<FromAddress>").append(senderEmail).append("</FromAddress>");
        xml.append("<MemberAddress>").append(escapeXml(email)).append("</MemberAddress>");
        xml.append("<MailName>").append(escapeXml(name)).append("</MailName>");
        xml.append("</AddEmail>");

        return xml.toString();
    }

    // XML转义
    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // TICKET: 批量发送BMM tickets给确认出席的会员
    public Map<String, Integer> sendBMMTicketsBatch(Event event) {
        try {
            log.info("Starting BMM ticket batch sending for event: {}", event.getName());

            // Find all members who confirmed attendance but haven't received tickets
            List<EventMember> confirmedMembers = eventMemberRepository.findByEventAndBmmRegistrationStage(
                    event, "ATTENDANCE_CONFIRMED");

            List<EventMember> needTickets = confirmedMembers.stream()
                    .filter(em -> em.getTicketStatus() == null ||
                            (!em.getTicketStatus().equals("EMAIL_SENT") &&
                                    !em.getTicketStatus().equals("SMS_SENT")))
                    .collect(Collectors.toList());

            log.info("Found {} members needing BMM tickets", needTickets.size());

            int emailsSent = 0;
            int smsSent = 0;
            int failed = 0;

            for (EventMember member : needTickets) {
                try {
                    sendBMMTicketOnConfirmation(member);

                    if (member.getTicketStatus() != null &&
                            member.getTicketStatus().equals("EMAIL_SENT")) {
                        emailsSent++;
                    } else if (member.getTicketStatus() != null &&
                            member.getTicketStatus().equals("SMS_SENT")) {
                        smsSent++;
                    }

                    // 减少延迟以提高发送速度
                    if ((emailsSent + smsSent) % 100 == 0) {
                        Thread.sleep(100); // 100ms pause every 100 messages
                    }

                } catch (Exception e) {
                    log.error("Failed to send BMM ticket to member {}: {}",
                            member.getMembershipNumber(), e.getMessage());
                    failed++;
                }
            }

            Map<String, Integer> results = new HashMap<>();
            results.put("emailsSent", emailsSent);
            results.put("smsSent", smsSent);
            results.put("failed", failed);
            results.put("total", needTickets.size());

            log.info("BMM ticket batch sending completed: {} emails, {} SMS, {} failed",
                    emailsSent, smsSent, failed);

            return results;

        } catch (Exception e) {
            log.error("Failed BMM ticket batch sending for event {}: {}",
                    event.getName(), e.getMessage());
            throw new RuntimeException("BMM ticket batch sending failed", e);
        }
    }

    // Helper methods
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return "TBA";
        return dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return "TBA";
        return dateTime.format(DateTimeFormatter.ofPattern("h:mm a"));
    }

    // Extract session time based on member preferences
    private String extractSessionTime(EventMember eventMember) {
        String forumDesc = eventMember.getForumDesc();

        // First check if this is a forumVenueMapping member
        if (forumDesc != null && forumDesc.equals("Greymouth")) {
            // For these special forums, return special instructions
            return "Multiple venues and times available - see options below";
        }

        // Check single session venues
        if ("Gisborne".equals(forumDesc) || "Nelson".equals(forumDesc)) {
            return "12:30 PM";  // Only lunchtime available
        }

        // Check if member has preferred times JSON
        String preferredTimesJson = eventMember.getPreferredTimesJson();
        if (preferredTimesJson != null && !preferredTimesJson.isEmpty()) {
            try {
                // Check venue-specific constraints
                if ("Napier".equals(forumDesc)) {
                    // Napier only has morning (10:30) and afternoon (2:30), no lunchtime
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        return "2:30 PM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        // Lunchtime preference but Napier doesn't have it, default to morning
                        return "10:30 AM";
                    }
                    return "10:30 AM"; // Default for Napier
                } else if ("Auckland North Shore".equals(forumDesc) || "Auckland West".equals(forumDesc) ||
                        "Manukau 3".equals(forumDesc) || "Pukekohe".equals(forumDesc) || 
                        "Whangarei".equals(forumDesc) || "Christchurch 2".equals(forumDesc) ||
                        "Rotorua".equals(forumDesc) || "Hamilton 1".equals(forumDesc) ||
                        "Hamilton 2".equals(forumDesc) || "Tauranga".equals(forumDesc) ||
                        "Dunedin".equals(forumDesc) || "Invercargill".equals(forumDesc) ||
                        "New Plymouth".equals(forumDesc) || "Timaru".equals(forumDesc) ||
                        "Christchurch 1".equals(forumDesc) || "Wellington 1".equals(forumDesc) ||
                        "Palmerston North".equals(forumDesc) || "Wellington 2".equals(forumDesc)) {
                    // Venues with only morning + lunchtime (no afternoon)
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        return "12:30 PM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        // Afternoon preference but not available, default to lunchtime
                        return "12:30 PM";
                    }
                    return "10:30 AM"; // Default for 2-session venues
                } else {
                    // Three-session venues (Manukau 1, Manukau 2, Auckland Central)
                    if (preferredTimesJson.contains("morning")) {
                        return "10:30 AM";
                    } else if (preferredTimesJson.contains("lunchtime")) {
                        return "12:30 PM";
                    } else if (preferredTimesJson.contains("afternoon") ||
                            preferredTimesJson.contains("after work") ||
                            preferredTimesJson.contains("night shift")) {
                        return "2:30 PM";
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing preferred times JSON: {}", e.getMessage());
            }
        }

        // Default based on venue type
        if ("Napier".equals(forumDesc)) {
            return "10:30 AM";  // Default to morning for Napier
        } else if ("Auckland North Shore".equals(forumDesc) || "Auckland West".equals(forumDesc) ||
                "Manukau 3".equals(forumDesc) || "Pukekohe".equals(forumDesc) || 
                "Whangarei".equals(forumDesc) || "Christchurch 2".equals(forumDesc) ||
                "Rotorua".equals(forumDesc) || "Hamilton 1".equals(forumDesc) ||
                "Hamilton 2".equals(forumDesc) || "Tauranga".equals(forumDesc) ||
                "Dunedin".equals(forumDesc) || "Invercargill".equals(forumDesc) ||
                "New Plymouth".equals(forumDesc) || "Timaru".equals(forumDesc) ||
                "Christchurch 1".equals(forumDesc) || "Wellington 1".equals(forumDesc) ||
                "Palmerston North".equals(forumDesc) || "Wellington 2".equals(forumDesc)) {
            return "10:30 AM";  // Default to morning for 2-session venues
        }

        // Default for three-session venues
        return "10:30 AM";  // Default to morning
    }
}