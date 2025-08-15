package nz.etu.voting.service;

import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EventTemplateService {

    //    获取所有活跃的事件模板
    List<EventTemplate> getAllActiveTemplates();

    //    根据事件类型获取模板
    List<EventTemplate> getTemplatesByEventType(Event.EventType eventType);

    //    获取事件类型的默认模板
    Optional<EventTemplate> getDefaultTemplateForEventType(Event.EventType eventType);

    //    创建或更新事件模板
    EventTemplate saveTemplate(EventTemplate template);

    //    根据模板创建事件
    Event createEventFromTemplate(EventTemplate template, Map<String, Object> eventData);

    //    获取事件的有效配置（模板+覆盖设置）
    Map<String, Object> getEffectiveEventConfiguration(Event event);

    //    获取事件的注册流程步骤
    List<String> getRegistrationStepsForEvent(Event event);

    //    检查事件是否支持特定功能
    boolean doesEventSupportFeature(Event event, String featureName);

    //    获取事件的页面内容配置
    Map<String, String> getPageContentForEvent(Event event);

    //    获取事件的通知模板
    Map<String, String> getNotificationTemplatesForEvent(Event event);

    //    初始化默认模板
    void initializeDefaultTemplates();
}