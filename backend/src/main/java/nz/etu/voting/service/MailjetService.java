package nz.etu.voting.service;

import com.mailjet.client.ClientOptions;
import com.mailjet.client.MailjetClient;
import com.mailjet.client.MailjetRequest;
import com.mailjet.client.MailjetResponse;
import com.mailjet.client.resource.Emailv31;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailjetService {

    @Value("${mailjet.api.key}")
    private String apiKey;

    @Value("${mailjet.secret.key}")
    private String apiSecret;

    @Value("${etu.sender.email:Events@etu.nz}")
    private String senderEmail;

    @Value("${etu.sender.name:E tū Union}")
    private String senderName;

    public void sendEmail(String to, String toName, String subject, String content) {
        try {
            ClientOptions options = ClientOptions.builder()
                    .apiKey(apiKey)
                    .apiSecretKey(apiSecret)
                    .build();

            MailjetClient client = new MailjetClient(options);

            // Clean and prepare content
            String cleanContent = prepareEmailContent(content);

            MailjetRequest request = new MailjetRequest(Emailv31.resource)
                    .property(Emailv31.MESSAGES, new JSONArray()
                            .put(new JSONObject()
                                    .put(Emailv31.Message.FROM, new JSONObject()
                                            .put("Email", senderEmail)
                                            .put("Name", senderName))
                                    .put(Emailv31.Message.TO, new JSONArray()
                                            .put(new JSONObject()
                                                    .put("Email", to)
                                                    .put("Name", toName != null ? toName : "")))
                                    .put(Emailv31.Message.SUBJECT, subject)
                                    .put(Emailv31.Message.TEXTPART, cleanContent)
                                    .put(Emailv31.Message.HTMLPART, convertToHtml(cleanContent))
                            ));

            // 🔧 详细日志：发送前
            log.info("=== MAILJET API REQUEST ===");
            log.info("Sending to: {} ({})", to, toName);
            log.info("Subject: {}", subject);
            log.info("From: {} ({})", senderEmail, senderName);
            log.info("Content length: {}", cleanContent.length());

            MailjetResponse response = client.post(request);

            // 🔧 详细日志：Mailjet响应
            log.info("=== MAILJET API RESPONSE ===");
            log.info("Status Code: {}", response.getStatus());
            log.info("Response Data: {}", response.getData());
            log.info("Raw Response: {}", response.getRawResponseContent());

            // 添加小延迟避免触发Mailjet速率限制（每秒最多300封）
            try {
                Thread.sleep(50); // 50ms延迟，理论上限20封/秒，留有余地
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            // ✅ 正确的成功状态判断 (Mailjet返回200或202都是成功)
            if (response.getStatus() == 200 || response.getStatus() == 202) {
                log.info("✅ Successfully sent email via Mailjet to: {} | Status: {}", to, response.getStatus());

                // 如果有MessageID，记录下来
                try {
                    JSONObject responseData = new JSONObject(response.getData().toString());
                    if (responseData.has("Messages")) {
                        JSONArray messages = responseData.getJSONArray("Messages");
                        if (messages.length() > 0) {
                            JSONObject firstMessage = messages.getJSONObject(0);
                            if (firstMessage.has("To")) {
                                JSONArray toArray = firstMessage.getJSONArray("To");
                                if (toArray.length() > 0) {
                                    JSONObject toObj = toArray.getJSONObject(0);
                                    String messageId = toObj.optString("MessageID", "N/A");
                                    log.info("📧 Mailjet Message ID: {}", messageId);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not parse Mailjet response for message ID: {}", e.getMessage());
                }

            } else {
                log.error("❌ Failed to send email via Mailjet. Status: {}, Response: {}",
                        response.getStatus(), response.getRawResponseContent());

                // 🔧 不同错误状态的具体说明
                String errorMessage = switch (response.getStatus()) {
                    case 400 -> "Bad Request - Check email format, API keys, or request structure";
                    case 401 -> "Unauthorized - Invalid API key or secret";
                    case 403 -> "Forbidden - Account suspended or insufficient permissions";
                    case 429 -> "Rate Limited - Too many requests";
                    case 500 -> "Mailjet Internal Server Error";
                    default -> "Unknown error: " + response.getStatus();
                };

                log.error("Error Details: {}", errorMessage);
                throw new RuntimeException("Failed to send email via Mailjet: " + errorMessage +
                        " (Status: " + response.getStatus() + ")");
            }

        } catch (Exception e) {
            log.error("Error sending email via Mailjet to {}: {}", to, e.getMessage());
            throw new RuntimeException("Error while sending email: " + e.getMessage(), e);
        }
    }

    public void sendEmailWithVariables(String to, String toName, String subject,
                                       String template, Map<String, String> variables) {
        // Replace variables in template
        String content = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() != null ? entry.getValue() : "");
        }

        sendEmail(to, toName, subject, content);
    }

    private String prepareEmailContent(String content) {
        // Remove nbsp and clean content
        return content.replace("&nbsp;", " ")
                .replace("\u00A0", " ")
                .trim();
    }

    private String convertToHtml(String plainText) {
        // Convert plain text to simple HTML with consistent formatting
        String htmlContent = plainText
                .replace("\n", "<br>")
                .replace("======================================", "<hr style=\"border: 2px solid #333; margin: 20px 0;\">")
                .replace("--------------------------------------", "<hr style=\"border: 1px solid #ccc; margin: 15px 0;\">")
                .replace("YOUR TICKET IS READY", "<strong style=\"font-size: 18px; color: #2563eb;\">YOUR TICKET IS READY</strong>")
                .replace("ACCESS YOUR DIGITAL TICKET:", "<strong>ACCESS YOUR DIGITAL TICKET:</strong>")
                .replace("IMPORTANT INSTRUCTIONS:", "<strong style=\"color: #dc2626;\">IMPORTANT INSTRUCTIONS:</strong>")
                .replace("NEED HELP?", "<strong>NEED HELP?</strong>")
                .replace("E tū Union", "<strong>E tū Union</strong>")
                .replace("Ngā mihi,", "<em>Ngā mihi,</em>");

        return "<html><body style=\"font-family: Arial, sans-serif; font-size: 14px; line-height: 1.8; color: #333; background-color: #f9fafb;\">"
                + "<div style=\"max-width: 600px; margin: 20px auto; padding: 30px; background-color: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);\">"
                + htmlContent
                + "</div></body></html>";
    }
}