package nz.etu.voting.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventTemplate;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventTemplateRepository;
import nz.etu.voting.service.EventTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventTemplateServiceImpl implements EventTemplateService {

    private final EventTemplateRepository eventTemplateRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<EventTemplate> getAllActiveTemplates() {
        return eventTemplateRepository.findAllActiveTemplatesOrdered();
    }

    @Override
    public List<EventTemplate> getTemplatesByEventType(Event.EventType eventType) {
        return eventTemplateRepository.findByEventType(eventType);
    }

    @Override
    public Optional<EventTemplate> getDefaultTemplateForEventType(Event.EventType eventType) {
        return eventTemplateRepository.findByEventTypeAndIsDefaultTemplateTrue(eventType);
    }

    @Override
    @Transactional
    public EventTemplate saveTemplate(EventTemplate template) {
        return eventTemplateRepository.save(template);
    }

    @Override
    @Transactional
    public Event createEventFromTemplate(EventTemplate template, Map<String, Object> eventData) {
        Event.EventBuilder eventBuilder = Event.builder()
                .eventTemplate(template)
                .eventType(template.getEventType())
                .name((String) eventData.get("name"))
                .eventCode((String) eventData.get("eventCode"))
                .description((String) eventData.get("description"))
                .isActive(true)
                .registrationOpen(true)
                .syncStatus(Event.SyncStatus.PENDING);

//        应用模板的默认设置
        if (template.getAllowsQrCheckin()) {
            eventBuilder.qrScanEnabled(true);
        }

//        应用自定义设置（如果提供）
        if (eventData.containsKey("customLandingPageTitle")) {
            eventBuilder.customLandingPageTitle((String) eventData.get("customLandingPageTitle"));
        }

        if (eventData.containsKey("overrideSettings")) {
            try {
                String overrideJson = objectMapper.writeValueAsString(eventData.get("overrideSettings"));
                eventBuilder.overrideTemplateSettings(overrideJson);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize override settings: {}", e.getMessage());
            }
        }

        return eventRepository.save(eventBuilder.build());
    }

    @Override
    public Map<String, Object> getEffectiveEventConfiguration(Event event) {
        Map<String, Object> config = new HashMap<>();
        EventTemplate template = event.getEventTemplate();

        if (template != null) {
//            应用模板默认设置
            config.put("requiresAttendanceConfirmation", template.getRequiresAttendanceConfirmation());
            config.put("requiresSpecialVoteOption", template.getRequiresSpecialVoteOption());
            config.put("requiresAbsenceReason", template.getRequiresAbsenceReason());
            config.put("allowsQrCheckin", template.getAllowsQrCheckin());
            config.put("requiresSurveyCompletion", template.getRequiresSurveyCompletion());
            config.put("emailNotificationEnabled", template.getEmailNotificationEnabled());
            config.put("smsNotificationEnabled", template.getSmsNotificationEnabled());

//            应用事件特定的覆盖设置
            if (event.getOverrideTemplateSettings() != null) {
                try {
                    Map<String, Object> overrides = objectMapper.readValue(
                            event.getOverrideTemplateSettings(), Map.class);
                    config.putAll(overrides);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse override settings for event {}: {}",
                            event.getId(), e.getMessage());
                }
            }
        }

        return config;
    }

    @Override
    public List<String> getRegistrationStepsForEvent(Event event) {
        EventTemplate template = event.getEventTemplate();
        if (template != null && template.getRegistrationSteps() != null) {
            try {
                return objectMapper.readValue(template.getRegistrationSteps(), List.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse registration steps for template {}: {}",
                        template.getId(), e.getMessage());
            }
        }

//        返回默认步骤
        return Arrays.asList("INITIAL", "UPDATE_INFO", "CONFIRM_ATTENDANCE", "COMPLETED");
    }

    @Override
    public boolean doesEventSupportFeature(Event event, String featureName) {
        Map<String, Object> config = getEffectiveEventConfiguration(event);

        switch (featureName) {
            case "specialVote":
                return Boolean.TRUE.equals(config.get("requiresSpecialVoteOption"));
            case "qrCheckin":
                return Boolean.TRUE.equals(config.get("allowsQrCheckin"));
            case "survey":
                return Boolean.TRUE.equals(config.get("requiresSurveyCompletion"));
            case "absenceReason":
                return Boolean.TRUE.equals(config.get("requiresAbsenceReason"));
            default:
                return false;
        }
    }

    @Override
    public Map<String, String> getPageContentForEvent(Event event) {
        Map<String, String> content = new HashMap<>();
        EventTemplate template = event.getEventTemplate();

        if (template != null) {
//            使用模板内容
            content.put("landingPageTitle", template.getLandingPageTitle());
            content.put("landingPageDescription", template.getLandingPageDescription());
            content.put("registrationFormTitle", template.getRegistrationFormTitle());
            content.put("registrationFormInstructions", template.getRegistrationFormInstructions());
            content.put("attendanceQuestionText", template.getAttendanceQuestionText());
            content.put("specialVoteQuestionText", template.getSpecialVoteQuestionText());
            content.put("successMessage", template.getSuccessMessage());

//            覆盖自定义内容
            if (event.getCustomLandingPageTitle() != null) {
                content.put("landingPageTitle", event.getCustomLandingPageTitle());
            }
            if (event.getCustomLandingPageDescription() != null) {
                content.put("landingPageDescription", event.getCustomLandingPageDescription());
            }
            if (event.getCustomRegistrationInstructions() != null) {
                content.put("registrationFormInstructions", event.getCustomRegistrationInstructions());
            }
        }

        return content;
    }

    @Override
    public Map<String, String> getNotificationTemplatesForEvent(Event event) {
        Map<String, String> templates = new HashMap<>();
        EventTemplate template = event.getEventTemplate();

        if (template != null) {
            templates.put("emailSubject", template.getEmailTemplateSubject());
            templates.put("emailContent", template.getEmailTemplateContent());

//            覆盖自定义邮件模板
            if (event.getCustomEmailTemplate() != null) {
                templates.put("emailContent", event.getCustomEmailTemplate());
            }
        }

        return templates;
    }

    @PostConstruct
    @Override
    @Transactional
    public void initializeDefaultTemplates() {
        log.info("Initializing default event templates...");

//        1. Special Conference Template
        createDefaultTemplateIfNotExists("Special Conference Default", Event.EventType.SPECIAL_CONFERENCE,
                "E tū Special Conference",
                "Welcome to the registration portal for E tū Special Conference. Your participation is essential.",
                "Confirm your attendance for Special Conference",
                "Please confirm whether you will attend the conference and provide your reason.",
                "Will you attend this special conference?",
                null, // 不需要特殊投票
                "Thank you for your response. Further details will be sent to you.",
                "Confirm Your Attendance - E tū Special Conference",
                "Kia ora {{name}}, Please confirm your attendance for the Special Conference...",
                Arrays.asList("INITIAL", "UPDATE_INFO", "CONFIRM_ATTENDANCE", "COMPLETED"),
                true, false, true, false, false);

//        2. Survey Meeting Template
        createDefaultTemplateIfNotExists("Survey Meeting Default", Event.EventType.SURVEY_MEETING,
                "Manufacturing Food Industry Survey",
                "Participate in our important industry survey for manufacturing food sector members.",
                "Complete Industry Survey",
                "Please complete the survey questions to help us better serve our members.",
                "Will you participate in this survey?",
                null,
                "Thank you for completing the survey. Your responses are valuable to us.",
                "Manufacturing Food Industry Survey - Your Participation Needed",
                "Kia ora {{name}}, We invite you to participate in our industry survey...",
                Arrays.asList("INITIAL", "UPDATE_INFO", "COMPLETE_SURVEY", "COMPLETED"),
                true, false, false, false, true);

//        3. BMM Voting Template
        createDefaultTemplateIfNotExists("BMM Voting Default", Event.EventType.BMM_VOTING,
                "E tū BMM Voting Meeting",
                "Register for the BMM voting meeting across five regions with QR check-in capabilities.",
                "BMM Voting Registration",
                "Confirm your attendance or apply for special voting rights if you cannot attend.",
                "Will you attend the BMM voting meeting in person?",
                "If you cannot attend, you may be eligible for special voting rights based on specific criteria.",
                "Registration complete. You will receive your QR code for check-in.",
                "BMM Voting Meeting Registration",
                "Kia ora {{name}}, Registration is now open for the BMM Voting Meeting...",
                Arrays.asList("INITIAL", "UPDATE_INFO", "CONFIRM_ATTENDANCE", "SPECIAL_VOTE_OPTION", "QR_GENERATED", "COMPLETED"),
                true, true, true, true, false);

        log.info("Default event templates initialization completed");
    }

    private void createDefaultTemplateIfNotExists(String templateName, Event.EventType eventType,
                                                  String landingTitle, String landingDesc, String formTitle, String formInstructions,
                                                  String attendanceQuestion, String specialVoteQuestion, String successMsg,
                                                  String emailSubject, String emailContent, List<String> steps,
                                                  boolean requiresAttendance, boolean requiresSpecialVote, boolean requiresAbsenceReason,
                                                  boolean allowsQrCheckin, boolean requiresSurvey) {

        if (eventTemplateRepository.findByTemplateName(templateName).isEmpty()) {
            try {
                EventTemplate template = EventTemplate.builder()
                        .templateName(templateName)
                        .eventType(eventType)
                        .templateDescription("Default template for " + eventType)
                        .landingPageTitle(landingTitle)
                        .landingPageDescription(landingDesc)
                        .registrationFormTitle(formTitle)
                        .registrationFormInstructions(formInstructions)
                        .attendanceQuestionText(attendanceQuestion)
                        .specialVoteQuestionText(specialVoteQuestion)
                        .successMessage(successMsg)
                        .emailTemplateSubject(emailSubject)
                        .emailTemplateContent(emailContent)
                        .registrationSteps(objectMapper.writeValueAsString(steps))
                        .requiresAttendanceConfirmation(requiresAttendance)
                        .requiresSpecialVoteOption(requiresSpecialVote)
                        .requiresAbsenceReason(requiresAbsenceReason)
                        .allowsQrCheckin(allowsQrCheckin)
                        .requiresSurveyCompletion(requiresSurvey)
                        .emailNotificationEnabled(true)
                        .smsNotificationEnabled(true)
                        .isActive(true)
                        .isDefaultTemplate(true)
                        .createdBy("SYSTEM")
                        .defaultInformerDatasetIds("[]")
                        .enabledRegions("[]")
                        .regionSpecificSettings("{}")
                        .reminderSchedule("[]")
                        .memberFilterCriteria("{}")
                        .specialVoteEligibilityCriteria("{}")
                        .surveyQuestions("[]")
                        .build();

//                BMM Voting 特殊配置
                if (eventType == Event.EventType.BMM_VOTING) {
                    template.setSpecialVoteDeadlineDays(14);
                    template.setMaxDistanceForSpecialVote(32.0);
//                    BMM三个地区配置
                    template.setEnabledRegions("[\"Northern\", \"Central\", \"Southern\"]");
                    template.setSpecialVoteEligibilityCriteria(
                            "[\"DISABILITY\", \"ILLNESS\", \"DISTANCE\", \"WORK_REQUIRED\"]");
                }

//                Survey Meeting 特殊配置
                if (eventType == Event.EventType.SURVEY_MEETING) {
                    template.setTargetSubIndustry("manufacturing food");
                    template.setExpectedParticipants(8000);
                    template.setSurveyQuestions("[{\"question\":\"Sample survey question\",\"type\":\"text\"}]");
                }

                eventTemplateRepository.save(template);
                log.info("Created default template: {}", templateName);
            } catch (JsonProcessingException e) {
                log.error("Failed to create default template {}: {}", templateName, e.getMessage());
            }
        }
    }
}