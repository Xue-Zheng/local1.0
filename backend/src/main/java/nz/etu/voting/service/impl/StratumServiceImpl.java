package nz.etu.voting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.service.StratumService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StratumServiceImpl implements StratumService {

    private final RestTemplate restTemplate;

    @Value("${stratum.api.member.url}")
    private String stratumApiMemberUrl;

    @Value("${stratum.api.member.securityKey}")
    private String stratumApiMemberSecurityKey;

    @Override
    public boolean syncMemberToStratum(Member member) {
        log.info("🔄 Start sync Member Information to Stratum: {}", member.getMembershipNumber());

        try {
            String xmlPayload = buildStratumMemberXml(member);
            log.info("📄 Stratum Member Update XML for {}: {}", member.getMembershipNumber(), xmlPayload);

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

            String fullUrl;
            if (stratumApiMemberUrl.endsWith("/")) {
                fullUrl = stratumApiMemberUrl + member.getMembershipNumber() + "?securityKey=" + stratumApiMemberSecurityKey;
            } else {
                fullUrl = stratumApiMemberUrl + "/" + member.getMembershipNumber() + "?securityKey=" + stratumApiMemberSecurityKey;
            }

            log.info(" Send Request to Stratum Member API: {}", fullUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("✅ Stratum Response Status: {}", response.getStatusCode());
            log.info("📄 Stratum Response Content: {}", response.getBody());

            boolean isSuccessful = response.getStatusCode().is2xxSuccessful();
            if (isSuccessful) {
                String responseBody = response.getBody();
                if (responseBody != null && (responseBody.toLowerCase().contains("error") ||
                        responseBody.toLowerCase().contains("failed") ||
                        responseBody.toLowerCase().contains("invalid"))) {
                    log.warn("⚠️ Stratum returned success status but response contains error: {}", responseBody);
                    return false;
                }
                log.info("✅ Member {} successfully synchronized to Stratum", member.getMembershipNumber());
                return true;
            } else {
                log.error("❌ Stratum sync failed with status: {} - {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Update Stratum member Information Failed for {}: {}", member.getMembershipNumber(), e.getMessage(), e);
            return false;
        }
    }

    private String buildStratumMemberXml(Member member) {
        StringBuilder xml = new StringBuilder();
        xml.append("<Members>");

        xml.append("<MembershipNumber>").append(escapeXml(member.getMembershipNumber())).append("</MembershipNumber>");

        String fullName = member.getName();
        String firstName = "";
        String lastName = "";

        if (fullName != null && !fullName.trim().isEmpty()) {
            fullName = fullName.trim();
            if (fullName.contains(" ")) {
                String[] nameParts = fullName.split("\\s+");
                firstName = nameParts[0];
                if (nameParts.length > 1) {
                    lastName = String.join(" ", java.util.Arrays.copyOfRange(nameParts, 1, nameParts.length));
                }
            } else {
                firstName = fullName;
                lastName = "";
            }
        }

        if (!firstName.isEmpty()) {
            xml.append("<Forenames>");
            xml.append("<Value>").append(escapeXml(firstName)).append("</Value>");
            xml.append("</Forenames>");
        }

        if (!lastName.isEmpty()) {
            xml.append("<Surname>").append(escapeXml(lastName)).append("</Surname>");
        }

        if (member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty()) {
            xml.append("<HomeEmail>").append(escapeXml(member.getPrimaryEmail())).append("</HomeEmail>");
        }

        if (member.getDobLegacy() != null) {
            String formattedDate = member.getDobLegacy().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            xml.append("<DateOfBirth>").append(formattedDate).append("</DateOfBirth>");
        }

        if (member.getAddress() != null && !member.getAddress().trim().isEmpty()) {
            xml.append("<Address1>").append(escapeXml(member.getAddress().trim())).append("</Address1>");
        }

        if (member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty()) {
            xml.append("<MobilePhone>").append(escapeXml(member.getTelephoneMobile())).append("</MobilePhone>");
        }

        if (member.getPhoneHome() != null && !member.getPhoneHome().trim().isEmpty()) {
            xml.append("<HomePhone>").append(escapeXml(member.getPhoneHome())).append("</HomePhone>");
        }

        if (member.getPhoneWork() != null && !member.getPhoneWork().trim().isEmpty()) {
            xml.append("<WorkPhone>").append(escapeXml(member.getPhoneWork())).append("</WorkPhone>");
        }

        if (member.getJobTitle() != null && !member.getJobTitle().trim().isEmpty()) {
            xml.append("<Occupation>").append(escapeXml(member.getJobTitle())).append("</Occupation>");
        }

        if (member.getDepartment() != null && !member.getDepartment().trim().isEmpty()) {
            xml.append("<Department>").append(escapeXml(member.getDepartment())).append("</Department>");
        }

        if (member.getEmployer() != null && !member.getEmployer().trim().isEmpty()) {
            xml.append("<Employer>").append(escapeXml(member.getEmployer())).append("</Employer>");
            xml.append("<EmployerName>").append(escapeXml(member.getEmployer())).append("</EmployerName>");
        }

        if (member.getEmploymentStatus() != null && !member.getEmploymentStatus().trim().isEmpty()) {
            xml.append("<EmploymentStatus>").append(escapeXml(member.getEmploymentStatus())).append("</EmploymentStatus>");
        }

        if (member.getPayrollNumber() != null && !member.getPayrollNumber().trim().isEmpty()) {
            xml.append("<PayrollNumber>").append(escapeXml(member.getPayrollNumber())).append("</PayrollNumber>");
        }

        if (member.getSiteNumber() != null && !member.getSiteNumber().trim().isEmpty()) {
            xml.append("<SiteNumber>").append(escapeXml(member.getSiteNumber())).append("</SiteNumber>");
        }

        if (member.getLocation() != null && !member.getLocation().trim().isEmpty()) {
            xml.append("<Location>").append(escapeXml(member.getLocation())).append("</Location>");
        }

        xml.append("</Members>");

        log.debug("🔍 Generated XML structure for member {}: firstName='{}', lastName='{}', email='{}'",
                member.getMembershipNumber(), firstName, lastName, member.getPrimaryEmail());

        return xml.toString();
    }

    @Override
    public boolean syncEventMemberToStratum(EventMember eventMember) {
        log.info("🔄 Start sync EventMember Information to Stratum: {}", eventMember.getMembershipNumber());

        try {
            String xmlPayload = buildStratumEventMemberXml(eventMember);
            log.info("📄 Stratum EventMember Update XML for {}: {}", eventMember.getMembershipNumber(), xmlPayload);

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

            String fullUrl;
            if (stratumApiMemberUrl.endsWith("/")) {
                fullUrl = stratumApiMemberUrl + eventMember.getMembershipNumber() + "?securityKey=" + stratumApiMemberSecurityKey;
            } else {
                fullUrl = stratumApiMemberUrl + "/" + eventMember.getMembershipNumber() + "?securityKey=" + stratumApiMemberSecurityKey;
            }

            log.info("🌐 Send Request to Stratum Member API: {}", fullUrl);

            ResponseEntity<String> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            log.info("✅ Stratum Response Status: {}", response.getStatusCode());
            log.info("📄 Stratum Response Content: {}", response.getBody());

            boolean isSuccessful = response.getStatusCode().is2xxSuccessful();
            if (isSuccessful) {
                String responseBody = response.getBody();
                if (responseBody != null && (responseBody.toLowerCase().contains("error") ||
                        responseBody.toLowerCase().contains("failed") ||
                        responseBody.toLowerCase().contains("invalid"))) {
                    log.warn("⚠️ Stratum returned success status but response contains error: {}", responseBody);
                    return false;
                }
                log.info("✅ EventMember {} successfully synchronized to Stratum", eventMember.getMembershipNumber());
                return true;
            } else {
                log.error("❌ Stratum sync failed with status: {} - {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Update Stratum EventMember Information Failed for {}: {}", eventMember.getMembershipNumber(), e.getMessage(), e);
            return false;
        }
    }

    private String buildStratumEventMemberXml(EventMember eventMember) {
        StringBuilder xml = new StringBuilder();
        xml.append("<Members>");

        xml.append("<MembershipNumber>").append(escapeXml(eventMember.getMembershipNumber())).append("</MembershipNumber>");

        // 处理姓名分割
        String fullName = eventMember.getName();
        String firstName = "";
        String lastName = "";

        if (fullName != null && !fullName.trim().isEmpty()) {
            fullName = fullName.trim();
            if (fullName.contains(" ")) {
                String[] nameParts = fullName.split("\\s+");
                firstName = nameParts[0];
                if (nameParts.length > 1) {
                    lastName = String.join(" ", java.util.Arrays.copyOfRange(nameParts, 1, nameParts.length));
                }
            } else {
                firstName = fullName;
                lastName = "";
            }
        }

        if (!firstName.isEmpty()) {
            xml.append("<Forenames>");
            xml.append("<Value>").append(escapeXml(firstName)).append("</Value>");
            xml.append("</Forenames>");
        }

        if (!lastName.isEmpty()) {
            xml.append("<Surname>").append(escapeXml(lastName)).append("</Surname>");
        }

        if (eventMember.getPrimaryEmail() != null && !eventMember.getPrimaryEmail().trim().isEmpty()) {
            xml.append("<HomeEmail>").append(escapeXml(eventMember.getPrimaryEmail())).append("</HomeEmail>");
        }

        if (eventMember.getAddress() != null && !eventMember.getAddress().trim().isEmpty()) {
            xml.append("<Address1>").append(escapeXml(eventMember.getAddress().trim())).append("</Address1>");
        }

        if (eventMember.getTelephoneMobile() != null && !eventMember.getTelephoneMobile().trim().isEmpty()) {
            xml.append("<MobilePhone>").append(escapeXml(eventMember.getTelephoneMobile())).append("</MobilePhone>");
        }

        if (eventMember.getPhoneHome() != null && !eventMember.getPhoneHome().trim().isEmpty()) {
            xml.append("<HomePhone>").append(escapeXml(eventMember.getPhoneHome())).append("</HomePhone>");
        }

        if (eventMember.getPhoneWork() != null && !eventMember.getPhoneWork().trim().isEmpty()) {
            xml.append("<WorkPhone>").append(escapeXml(eventMember.getPhoneWork())).append("</WorkPhone>");
        }

        if (eventMember.getJobTitle() != null && !eventMember.getJobTitle().trim().isEmpty()) {
            xml.append("<Occupation>").append(escapeXml(eventMember.getJobTitle())).append("</Occupation>");
        }

        if (eventMember.getDepartment() != null && !eventMember.getDepartment().trim().isEmpty()) {
            xml.append("<Department>").append(escapeXml(eventMember.getDepartment())).append("</Department>");
        }

        if (eventMember.getEmployer() != null && !eventMember.getEmployer().trim().isEmpty()) {
            xml.append("<Employer>").append(escapeXml(eventMember.getEmployer())).append("</Employer>");
            xml.append("<EmployerName>").append(escapeXml(eventMember.getEmployer())).append("</EmployerName>");
        }

        if (eventMember.getEmploymentStatus() != null && !eventMember.getEmploymentStatus().trim().isEmpty()) {
            xml.append("<EmploymentStatus>").append(escapeXml(eventMember.getEmploymentStatus())).append("</EmploymentStatus>");
        }

        if (eventMember.getPayrollNumber() != null && !eventMember.getPayrollNumber().trim().isEmpty()) {
            xml.append("<PayrollNumber>").append(escapeXml(eventMember.getPayrollNumber())).append("</PayrollNumber>");
        }

        if (eventMember.getSiteCode() != null && !eventMember.getSiteCode().trim().isEmpty()) {
            xml.append("<SiteNumber>").append(escapeXml(eventMember.getSiteCode())).append("</SiteNumber>");
        }

        if (eventMember.getLocation() != null && !eventMember.getLocation().trim().isEmpty()) {
            xml.append("<Location>").append(escapeXml(eventMember.getLocation())).append("</Location>");
        }

        xml.append("</Members>");

        log.debug("🔍 Generated XML structure for EventMember {}: firstName='{}', lastName='{}', email='{}'",
                eventMember.getMembershipNumber(), firstName, lastName, eventMember.getPrimaryEmail());

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