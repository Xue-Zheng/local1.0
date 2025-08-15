package nz.etu.voting.service;

import nz.etu.voting.domain.dto.request.BulkEmailRequest;
import nz.etu.voting.domain.dto.response.EmailResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface EmailService {
    void sendTemplate(String toEmail, String toName, String templateId, Map<String, String> variables);
    void sendBulkTemplate(List<Map<String, Object>> recipients, String templateId);
    void sendSimpleEmail(String toEmail, String toName, String subject, String textContent);
    void sendEmailWithProvider(String toEmail, String toName, String subject, String content, String provider);

    EmailResponse sendBulkEmails(BulkEmailRequest request);
}