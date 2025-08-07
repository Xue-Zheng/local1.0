package nz.etu.voting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.FinancialForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsService {

    private final RestTemplate restTemplate;

    @Value("${stratum.sms.api.url}")
    private String stratumSmsApiUrl;

    @Value("${stratum.sms.api.securityKey}")
    private String stratumSmsApiSecurityKey;

    public void sendSms(String phoneNumber, String membershipNumber, String message) {
        try {
//            Enhanced logging for debugging
            log.info("=== SMS Sending Details ===");
            log.info("Phone Number: [{}]", phoneNumber);
            log.info("Membership Number: [{}]", membershipNumber);
            log.info("Message Length: {} characters", message != null ? message.length() : 0);

//            验证必要参数
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                throw new RuntimeException("Phone number cannot be empty");
            }

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Membership number is empty, using placeholder");
                membershipNumber = "BULK_SMS_" + System.currentTimeMillis();
            }

//            清理和验证membership number
            String cleanMembershipNumber = membershipNumber.trim();
            log.info("Cleaned Membership Number: [{}]", cleanMembershipNumber);

            String xmlPayload = buildSmsXmlPayload(cleanMembershipNumber, message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.add("Accept-Charset", "utf-8");

            Map<String, String> formData = new HashMap<>();
            formData.put("newValues", xmlPayload);

            String body = formData.entrySet().stream().map(entry -> {
                try {
                    return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.joining("&"));

            HttpEntity<String> request = new HttpEntity<>(body, headers);

            String fullUrl = stratumSmsApiUrl + "?securityKey=" + stratumSmsApiSecurityKey;

            log.info("🚀 Sending SMS request to Stratum API: {}", fullUrl);
            log.debug("📄 SMS XML Payload: {}", xmlPayload);

            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("✅ SMS sent successfully - Status Code: {}", response.getStatusCode());
            log.info("📨 Stratum Response Content: {}", response.getBody());

        } catch (Exception e) {
            log.error("❌ SMS sending failed - Phone: {}, Member: {}, Error: {}",
                    phoneNumber, membershipNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to send SMS via Stratum", e);
        }
    }

    public void sendSmsToEventMember(EventMember eventMember, String message) {
        String phoneNumber = getPreferredPhoneNumber(eventMember);
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            sendSms(phoneNumber, eventMember.getMembershipNumber(), message);
        } else {
            log.warn("Cannot send SMS to member {} - no mobile phone available", eventMember.getMembershipNumber());
            throw new RuntimeException("Member has no mobile phone number");
        }
    }

    //    获取首选电话号码：优先使用Member的telephoneMobile字段（这个字段来自Financial Form更新）
    private String getPreferredPhoneNumber(EventMember eventMember) {
        // 优先使用Member表中的telephoneMobile（这是Financial Form更新后的号码）
        if (eventMember.getMember() != null &&
                eventMember.getMember().getTelephoneMobile() != null &&
                !eventMember.getMember().getTelephoneMobile().trim().isEmpty()) {

            log.info("Using updated telephone mobile from Member for member: {}",
                    eventMember.getMembershipNumber());
            return eventMember.getMember().getTelephoneMobile();
        }

//        回退到EventMember的手机号码
        return eventMember.getTelephoneMobile();
    }

    private String buildSmsXmlPayload(String membershipNumber, String message) {
        StringBuilder xml = new StringBuilder();
        xml.append("<AddTxtMessage>");
        xml.append("<MemberNumber>").append(escapeXml(membershipNumber)).append("</MemberNumber>");
        xml.append("<Message>").append(escapeXml(message)).append("</Message>");
        xml.append("<SessionMemberNumber>").append(escapeXml(membershipNumber)).append("</SessionMemberNumber>");
        xml.append("</AddTxtMessage>");
        return xml.toString();
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}