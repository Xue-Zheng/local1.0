package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventTemplate;
import nz.etu.voting.service.EventTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/event-templates")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
@Slf4j
public class EventTemplateController {

    private final EventTemplateService eventTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EventTemplate>>> getAllTemplates() {
        try {
            List<EventTemplate> templates = eventTemplateService.getAllActiveTemplates();
            return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
        } catch (Exception e) {
            log.error("Failed to retrieve templates", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/by-type/{eventType}")
    public ResponseEntity<ApiResponse<List<EventTemplate>>> getTemplatesByType(
            @PathVariable Event.EventType eventType) {
        try {
            List<EventTemplate> templates = eventTemplateService.getTemplatesByEventType(eventType);
            return ResponseEntity.ok(ApiResponse.success("Templates retrieved successfully", templates));
        } catch (Exception e) {
            log.error("Failed to retrieve templates for type: {}", eventType, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventTemplate>> getTemplate(@PathVariable Long id) {
        try {
            EventTemplate template = eventTemplateService.getAllActiveTemplates().stream()
                    .filter(t -> t.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Template not found"));
            return ResponseEntity.ok(ApiResponse.success("Template retrieved successfully", template));
        } catch (Exception e) {
            log.error("Failed to retrieve template: {}", id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EventTemplate>> createTemplate(@RequestBody EventTemplate template) {
        try {
            template.setCreatedBy("ADMIN"); // Fixed: Using default admin user for now
            template.setCreatedAt(LocalDateTime.now());
            EventTemplate savedTemplate = eventTemplateService.saveTemplate(template);
            return ResponseEntity.ok(ApiResponse.success("Template created successfully", savedTemplate));
        } catch (Exception e) {
            log.error("Failed to create template: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to create template: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EventTemplate>> updateTemplate(
            @PathVariable Long id, @RequestBody EventTemplate template) {
        try {
            template.setId(id);
            template.setUpdatedBy("ADMIN"); // Fixed: Using default admin user for now
            EventTemplate savedTemplate = eventTemplateService.saveTemplate(template);
            return ResponseEntity.ok(ApiResponse.success("Template updated successfully", savedTemplate));
        } catch (Exception e) {
            log.error("Failed to update template: {}", id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{templateId}/create-event")
    public ResponseEntity<ApiResponse<Event>> createEventFromTemplate(
            @PathVariable Long templateId, @RequestBody Map<String, Object> eventData) {
        try {
            EventTemplate template = eventTemplateService.getAllActiveTemplates().stream()
                    .filter(t -> t.getId().equals(templateId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Template not found"));

            Event event = eventTemplateService.createEventFromTemplate(template, eventData);
            return ResponseEntity.ok(ApiResponse.success("Event created from template successfully", event));
        } catch (Exception e) {
            log.error("Failed to create event from template: {}", templateId, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/event-types")
    public ResponseEntity<ApiResponse<Event.EventType[]>> getEventTypes() {
        try {
            return ResponseEntity.ok(ApiResponse.success("Event types retrieved successfully", Event.EventType.values()));
        } catch (Exception e) {
            log.error("Failed to retrieve event types", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/initialize-defaults")
    public ResponseEntity<ApiResponse<String>> initializeDefaultTemplates() {
        try {
            eventTemplateService.initializeDefaultTemplates();
            return ResponseEntity.ok(ApiResponse.success("Default templates initialized successfully",
                    "Default templates have been created"));
        } catch (Exception e) {
            log.error("Failed to initialize default templates", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}