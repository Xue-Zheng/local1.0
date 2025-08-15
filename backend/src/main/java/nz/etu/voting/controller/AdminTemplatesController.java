package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

//Admin Templates Controller and Supports email and SMS template management
@Slf4j
@RestController
@RequestMapping("/api/admin/templates")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
public class AdminTemplatesController {

    //    Get all templates list (email and SMS)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTemplates() {
        log.info("Fetching all templates");

        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> allTemplates = new ArrayList<>();

//            Get email templates
            List<Map<String, Object>> emailTemplates = getEmailTemplatesList();
            for (Map<String, Object> template : emailTemplates) {
                template.put("type", "email");
                template.put("isActive", true);
                template.put("createdAt", "2024-01-01T00:00:00Z");
                template.put("updatedAt", "2024-01-01T00:00:00Z");
                allTemplates.add(template);
            }

//            Get SMS templates
            List<Map<String, Object>> smsTemplates = getSmsTemplatesList();
            for (Map<String, Object> template : smsTemplates) {
                template.put("type", "sms");
                template.put("isActive", true);
                template.put("createdAt", "2024-01-01T00:00:00Z");
                template.put("updatedAt", "2024-01-01T00:00:00Z");
                allTemplates.add(template);
            }

            response.put("status", "success");
            response.put("message", "Templates retrieved successfully");
            response.put("data", allTemplates);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch all templates", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to retrieve templates: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get email templates list
    @GetMapping("/email")
    public ResponseEntity<Map<String, Object>> getEmailTemplates() {
        log.info("Fetching email templates");

        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> templates = getEmailTemplatesList();

            response.put("status", "success");
            response.put("data", templates);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch email templates", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to retrieve email templates: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Get SMS templates list
    @GetMapping("/sms")
    public ResponseEntity<Map<String, Object>> getSmsTemplates() {
        log.info("Fetching SMS templates");

        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> templates = getSmsTemplatesList();

            response.put("status", "success");
            response.put("data", templates);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch SMS templates", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to retrieve SMS templates: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Preview template content
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> previewTemplate(@RequestBody Map<String, Object> request) {
        log.info("Previewing template content");

        try {
            String templateId = (String) request.get("templateId");
            String content = (String) request.get("content");

            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Template content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

//            Sample data replacement
            String previewContent = content
                    .replace("{{name}}", "John Smith")
                    .replace("{{firstName}}", "John")
                    .replace("{{membershipNumber}}", "ETU123456")
                    .replace("{{verificationCode}}", "ABC123")
                    .replace("{{registrationLink}}", "https://events.etu.nz/?token={{actualToken}}");

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("originalContent", content);
            data.put("previewContent", previewContent);
            data.put("templateId", templateId);

            response.put("status", "success");
            response.put("message", "Template preview generated successfully");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to preview template", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Template preview failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Validate template syntax
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateTemplate(@RequestBody Map<String, Object> request) {
        log.info("Validating template syntax");

        try {
            String content = (String) request.get("content");
            String templateType = (String) request.get("templateType"); // email or sms

            if (content == null || content.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "Template content cannot be empty");
                return ResponseEntity.badRequest().body(response);
            }

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

//            Check variable syntax
            if (!content.contains("{{") && !content.contains("}}")) {
                warnings.add("No variables found in template, please confirm this is intentional");
            }

//            Check unclosed variables
            long openCount = content.split("\\{\\{").length - 1;
            long closeCount = content.split("\\}\\}").length - 1;

            if (openCount != closeCount) {
                errors.add("Variable syntax error: {{ and }} count mismatch");
            }

//            SMS length check
            if ("sms".equals(templateType)) {
                if (content.length() > 160) {
                    warnings.add("SMS content exceeds 160 characters, may be split into multiple messages");
                }
                if (!content.contains("STOP") && !content.contains("unsubscribe")) {
                    warnings.add("Consider adding unsubscribe instructions at the end of SMS");
                }
            }

//            Email subject check
            if ("email".equals(templateType)) {
                String subject = (String) request.get("subject");
                if (subject != null && subject.length() > 78) {
                    warnings.add("Email subject is too long, may be truncated in some email clients");
                }
            }

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("isValid", errors.isEmpty());
            data.put("errors", errors);
            data.put("warnings", warnings);

            response.put("status", "success");
            response.put("message", errors.isEmpty() ? "Template validation passed" : "Template validation failed");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to validate template", e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Template validation failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    //    Helper method to get email templates list
    private List<Map<String, Object>> getEmailTemplatesList() {
        List<Map<String, Object>> templates = new ArrayList<>();

//        Default email templates
        Map<String, Object> registrationTemplate = new HashMap<>();
        registrationTemplate.put("id", 1);
        registrationTemplate.put("name", "Event Registration Invitation");
        registrationTemplate.put("subject", "E tū Union Annual Meeting Registration Invitation - {{name}}");
        registrationTemplate.put("content",
                "Dear {{name}} member,\n\n" +
                        "Hello! E tū Union cordially invites you to attend our upcoming Annual Meeting.\n\n" +
                        "Membership Number: {{membershipNumber}}\n" +
                        "Verification Code: {{verificationCode}}\n\n" +
                        "Please click the following link to complete registration:\n" +
                        "{{registrationLink}}\n\n" +
                        "If you have any questions, please contact us.\n\n" +
                        "Thank you!\n" +
                        "E tū Union"
        );
        registrationTemplate.put("variables", List.of("name", "membershipNumber", "verificationCode", "registrationLink"));
        templates.add(registrationTemplate);

        Map<String, Object> reminderTemplate = new HashMap<>();
        reminderTemplate.put("id", 2);
        reminderTemplate.put("name", "Registration Reminder");
        reminderTemplate.put("subject", "Reminder: E tū Union Annual Meeting Registration - {{name}}");
        reminderTemplate.put("content",
                "Dear {{name}} member,\n\n" +
                        "This is a friendly reminder email. We noticed you haven't completed registration for the Annual Meeting yet.\n\n" +
                        "Membership Number: {{membershipNumber}}\n" +
                        "Verification Code: {{verificationCode}}\n\n" +
                        "Please click the following link to complete registration as soon as possible:\n" +
                        "{{registrationLink}}\n\n" +
                        "Registration deadline is approaching, please don't miss it!\n\n" +
                        "Thank you!\n" +
                        "E tū Union"
        );
        reminderTemplate.put("variables", List.of("name", "membershipNumber", "verificationCode", "registrationLink"));
        templates.add(reminderTemplate);

        Map<String, Object> confirmationTemplate = new HashMap<>();
        confirmationTemplate.put("id", 3);
        confirmationTemplate.put("name", "Registration Confirmation");
        confirmationTemplate.put("subject", "Registration Confirmed: E tū Union Annual Meeting - {{name}}");
        confirmationTemplate.put("content",
                "Dear {{name}} member,\n\n" +
                        "Thank you for registering for the E tū Union Annual Meeting!\n\n" +
                        "Your registration information:\n" +
                        "Membership Number: {{membershipNumber}}\n" +
                        "Name: {{name}}\n\n" +
                        "Meeting details will be sent to you shortly.\n\n" +
                        "Looking forward to seeing you at the meeting!\n\n" +
                        "Thank you!\n" +
                        "E tū Union"
        );
        confirmationTemplate.put("variables", List.of("name", "membershipNumber"));
        templates.add(confirmationTemplate);

        return templates;
    }

    //    Helper method to get SMS templates list
    private List<Map<String, Object>> getSmsTemplatesList() {
        List<Map<String, Object>> templates = new ArrayList<>();

//        Default SMS templates
        Map<String, Object> registrationSms = new HashMap<>();
        registrationSms.put("id", 4);
        registrationSms.put("name", "Registration Invitation SMS");
        registrationSms.put("content",
                "[E tū Union] Dear {{name}} member, you are cordially invited to attend the Annual Meeting. Membership: {{membershipNumber}}, Code: {{verificationCode}}. Register: {{registrationLink}} Reply STOP to opt out"
        );
        registrationSms.put("variables", List.of("name", "membershipNumber", "verificationCode", "registrationLink"));
        templates.add(registrationSms);

        Map<String, Object> reminderSms = new HashMap<>();
        reminderSms.put("id", 5);
        reminderSms.put("name", "Registration Reminder SMS");
        reminderSms.put("content",
                "[E tū Union] Reminder: {{name}} member, you haven't registered for the Annual Meeting yet. Membership: {{membershipNumber}}, please register soon: {{registrationLink}} Reply STOP to opt out"
        );
        reminderSms.put("variables", List.of("name", "membershipNumber", "registrationLink"));
        templates.add(reminderSms);

        Map<String, Object> confirmationSms = new HashMap<>();
        confirmationSms.put("id", 6);
        confirmationSms.put("name", "Registration Confirmation SMS");
        confirmationSms.put("content",
                "[E tū Union] {{name}} member, thank you for registering for the Annual Meeting! Membership: {{membershipNumber}}. Meeting details will be sent shortly. Looking forward to seeing you! Reply STOP to opt out"
        );
        confirmationSms.put("variables", List.of("name", "membershipNumber"));
        templates.add(confirmationSms);

        Map<String, Object> checkinReminder = new HashMap<>();
        checkinReminder.put("id", 7);
        checkinReminder.put("name", "Check-in Reminder SMS");
        checkinReminder.put("content",
                "[E tū Union] {{name}} member, the Annual Meeting is about to start, please check in on time. Membership: {{membershipNumber}}. Thank you for your participation! Reply STOP to opt out"
        );
        checkinReminder.put("variables", List.of("name", "membershipNumber"));
        templates.add(checkinReminder);

        return templates;
    }
}