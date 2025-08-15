package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

//管理员迁移控制器 - 用于数据迁移和BMM会员导入
@Slf4j
@RestController
@RequestMapping("/api/admin/migration")
@CrossOrigin(origins = {"http://localhost:3000", "http://10.0.9.238:3000", "https://events.etu.nz"})
@RequiredArgsConstructor
public class AdminMigrationController {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    //    将所有Member表中的会员迁移到指定的EventMember事件
    @PostMapping("/members-to-event/{eventId}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> migrateMembersToEvent(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "false") boolean overwriteExisting) {
        try {
            log.info("Starting migration of Members to Event ID: {}", eventId);

//            验证事件存在
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

//            获取所有Member
            List<Member> allMembers = memberRepository.findAll();
            log.info("Found {} members to migrate", allMembers.size());

            int created = 0;
            int updated = 0;
            int skipped = 0;
            int errors = 0;

            for (Member member : allMembers) {
                try {
//                    检查是否已存在EventMember
                    Optional<EventMember> existingOpt = eventMemberRepository
                            .findByEventAndMembershipNumber(event, member.getMembershipNumber());

                    if (existingOpt.isPresent()) {
                        if (overwriteExisting) {
//                            更新现有EventMember
                            EventMember existing = existingOpt.get();
                            updateEventMemberFromMember(existing, member);
                            eventMemberRepository.save(existing);
                            updated++;
                            log.debug("Updated EventMember for member: {}", member.getMembershipNumber());
                        } else {
                            skipped++;
                            log.debug("Skipped existing EventMember for member: {}", member.getMembershipNumber());
                        }
                    } else {
//                        创建新的EventMember
                        EventMember eventMember = createEventMemberFromMember(member, event);
                        eventMemberRepository.save(eventMember);
                        created++;
                        log.debug("Created EventMember for member: {}", member.getMembershipNumber());
                    }

                } catch (Exception e) {
                    log.error("Failed to migrate member {}: {}", member.getMembershipNumber(), e.getMessage());
                    errors++;
                }
            }

//            更新事件统计
            event.setMemberSyncCount(created + updated);
            event.setLastSyncTime(LocalDateTime.now());
            event.setSyncStatus(Event.SyncStatus.SUCCESS);
            eventRepository.save(event);

            Map<String, Object> result = new HashMap<>();
            result.put("eventId", eventId);
            result.put("eventName", event.getName());
            result.put("totalMembers", allMembers.size());
            result.put("created", created);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("errors", errors);
            result.put("message", String.format("Migration completed: %d created, %d updated, %d skipped, %d errors",
                    created, updated, skipped, errors));

            log.info("Migration completed for event {}: {} created, {} updated, {} skipped, {} errors",
                    event.getName(), created, updated, skipped, errors);

            return ResponseEntity.ok(ApiResponse.success("Migration completed successfully", result));

        } catch (Exception e) {
            log.error("Migration failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Migration failed: " + e.getMessage()));
        }
    }

    //    专门为BMM事件导入所有会员
    @PostMapping("/bmm-members-import")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> importBMMMembers() {
        try {
            log.info("Starting BMM members import");

//            查找BMM事件
            Event bmmEvent = eventRepository.findByEventType(Event.EventType.BMM_VOTING)
                    .stream()
                    .filter(Event::getIsActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No active BMM event found"));

            log.info("Found BMM event: {}", bmmEvent.getName());

//            调用通用迁移方法
            return migrateMembersToEvent(bmmEvent.getId(), false);

        } catch (Exception e) {
            log.error("BMM members import failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("BMM import failed: " + e.getMessage()));
        }
    }

    //    获取迁移状态统计
    @GetMapping("/status/{eventId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMigrationStatus(@PathVariable Long eventId) {
        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            long totalMembers = memberRepository.count();
            long eventMembers = eventMemberRepository.countByEvent(event);
            long attendingMembers = eventMemberRepository.countByEventAndIsAttendingTrue(event);
            long registeredMembers = eventMemberRepository.countRegisteredByEvent(event);

            Map<String, Object> status = new HashMap<>();
            status.put("eventId", eventId);
            status.put("eventName", event.getName());
            status.put("totalMembers", totalMembers);
            status.put("eventMembers", eventMembers);
            status.put("attendingMembers", attendingMembers);
            status.put("registeredMembers", registeredMembers);
            status.put("migrationProgress", totalMembers > 0 ?
                    String.format("%.1f%%", (eventMembers * 100.0 / totalMembers)) : "0%");
            status.put("lastSyncTime", event.getLastSyncTime());
            status.put("syncStatus", event.getSyncStatus());

            return ResponseEntity.ok(ApiResponse.success("Migration status retrieved", status));

        } catch (Exception e) {
            log.error("Failed to get migration status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get status: " + e.getMessage()));
        }
    }

    //    清理EventMember数据（危险操作）
    @DeleteMapping("/cleanup-event/{eventId}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupEventMembers(
            @PathVariable Long eventId,
            @RequestParam String confirmationCode) {

//        安全确认
        if (!"CONFIRM_CLEANUP_2024".equals(confirmationCode)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid confirmation code"));
        }

        try {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new IllegalArgumentException("Event not found"));

            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);
            long deletedCount = eventMembers.size();
            eventMemberRepository.deleteAll(eventMembers);

            log.warn("CLEANUP: Deleted {} EventMembers for event: {}", deletedCount, event.getName());

            Map<String, Object> result = new HashMap<>();
            result.put("eventId", eventId);
            result.put("eventName", event.getName());
            result.put("deletedCount", deletedCount);
            result.put("message", String.format("Deleted %d EventMembers", deletedCount));

            return ResponseEntity.ok(ApiResponse.success("Cleanup completed", result));

        } catch (Exception e) {
            log.error("Cleanup failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Cleanup failed: " + e.getMessage()));
        }
    }

    //    获取系统总体迁移状态
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemMigrationStatus() {
        try {
            long totalMembers = memberRepository.count();
            long totalEventMembers = eventMemberRepository.count();
            long totalEvents = eventRepository.count();

            Map<String, Object> status = new HashMap<>();
            status.put("totalMembers", totalMembers);
            status.put("totalEventMembers", totalEventMembers);
            status.put("totalEvents", totalEvents);

            return ResponseEntity.ok(ApiResponse.success("System status retrieved", status));

        } catch (Exception e) {
            log.error("Failed to get system status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get status: " + e.getMessage()));
        }
    }

    //    迁移传统数据 (Legacy Data Migration)
    @PostMapping("/migrate-legacy-data")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> migrateLegacyData() {
        try {
            log.info("Starting legacy data migration");

//            查找BMM事件
            Event bmmEvent = eventRepository.findByEventType(Event.EventType.BMM_VOTING)
                    .stream()
                    .filter(Event::getIsActive)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No active BMM event found"));

//            执行会员迁移
            return migrateMembersToEvent(bmmEvent.getId(), true);

        } catch (Exception e) {
            log.error("Legacy data migration failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Legacy migration failed: " + e.getMessage()));
        }
    }

    //    重新生成所有Token
    @PostMapping("/regenerate-tokens")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> regenerateAllTokens() {
        try {
            log.info("Starting token regeneration for all EventMembers");

            List<EventMember> allEventMembers = eventMemberRepository.findAll();
            int regenerated = 0;

            for (EventMember eventMember : allEventMembers) {
                eventMember.setToken(UUID.randomUUID());
                eventMember.setUpdatedAt(LocalDateTime.now());
                eventMemberRepository.save(eventMember);
                regenerated++;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("regeneratedCount", regenerated);
            result.put("message", String.format("Regenerated %d tokens", regenerated));

            log.info("Token regeneration completed: {} tokens regenerated", regenerated);

            return ResponseEntity.ok(ApiResponse.success("Token regeneration completed", result));

        } catch (Exception e) {
            log.error("Token regeneration failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Token regeneration failed: " + e.getMessage()));
        }
    }

    //    数据清理
    @PostMapping("/cleanup-data")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupData() {
        try {
            log.info("Starting data cleanup");

//            清理重复的EventMember记录
            List<EventMember> allEventMembers = eventMemberRepository.findAll();
            Map<String, EventMember> uniqueEventMembers = new HashMap<>();
            List<EventMember> duplicates = new ArrayList<>();

            for (EventMember em : allEventMembers) {
                String key = em.getEvent().getId() + "_" + em.getMembershipNumber();
                if (uniqueEventMembers.containsKey(key)) {
                    duplicates.add(em);
                } else {
                    uniqueEventMembers.put(key, em);
                }
            }

            eventMemberRepository.deleteAll(duplicates);

            Map<String, Object> result = new HashMap<>();
            result.put("duplicatesRemoved", duplicates.size());
            result.put("message", String.format("Removed %d duplicate records", duplicates.size()));

            log.info("Data cleanup completed: {} duplicates removed", duplicates.size());

            return ResponseEntity.ok(ApiResponse.success("Data cleanup completed", result));

        } catch (Exception e) {
            log.error("Data cleanup failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Data cleanup failed: " + e.getMessage()));
        }
    }

    //    删除所有BMM相关数据
    @PostMapping("/fresh-start")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> freshStart() {
        try {
            log.warn("Starting fresh start - deleting all BMM data");

//            删除所有EventMember
            long eventMembersDeleted = eventMemberRepository.count();
            eventMemberRepository.deleteAll();

//            删除所有BMM事件
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            eventRepository.deleteAll(bmmEvents);

            Map<String, Object> result = new HashMap<>();
            result.put("eventMembersDeleted", eventMembersDeleted);
            result.put("eventsDeleted", bmmEvents.size());
            result.put("message", String.format("Deleted %d EventMembers and %d Events", eventMembersDeleted, bmmEvents.size()));

            log.warn("Fresh start completed: {} EventMembers and {} Events deleted", eventMembersDeleted, bmmEvents.size());

            return ResponseEntity.ok(ApiResponse.success("Fresh start completed", result));

        } catch (Exception e) {
            log.error("Fresh start failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Fresh start failed: " + e.getMessage()));
        }
    }

    //    从Member创建EventMember
    private EventMember createEventMemberFromMember(Member member, Event event) {
        return EventMember.builder()
                .event(event)
                .member(member)
                .name(member.getName())
                .primaryEmail(member.getPrimaryEmail())
                .membershipNumber(member.getMembershipNumber())
                .telephoneMobile(member.getTelephoneMobile())
                .hasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().isEmpty())
                .hasMobile(member.getHasMobile())
                .token(member.getToken())
                .verificationCode(member.getVerificationCode())
                .hasRegistered(member.getHasRegistered())
                .isAttending(member.getIsAttending())
                .isSpecialVote(member.getIsSpecialVote())
                .hasVoted(member.getHasVoted())
                .absenceReason(member.getAbsenceReason())
                .checkedIn(false) // EventMember特有字段
                .employer(member.getEmployer())
                .regionDesc(member.getRegionDesc())
                .branch(member.getBranchDesc())
                .workplace(member.getWorkplaceDesc())
                .dataSource("MEMBER_MIGRATION")
                .importBatchId("MIGRATION_" + LocalDateTime.now().toString())
                .importedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    //    用Member数据更新EventMember
    private void updateEventMemberFromMember(EventMember eventMember, Member member) {
        eventMember.setName(member.getName());
        eventMember.setPrimaryEmail(member.getPrimaryEmail());
        eventMember.setTelephoneMobile(member.getTelephoneMobile());
        eventMember.setHasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().isEmpty());
        eventMember.setHasMobile(member.getHasMobile());
        eventMember.setHasRegistered(member.getHasRegistered());
        eventMember.setIsAttending(member.getIsAttending());
        eventMember.setIsSpecialVote(member.getIsSpecialVote());
        eventMember.setHasVoted(member.getHasVoted());
        eventMember.setAbsenceReason(member.getAbsenceReason());
        eventMember.setEmployer(member.getEmployer());
        eventMember.setRegionDesc(member.getRegionDesc());
        eventMember.setBranch(member.getBranchDesc());
        eventMember.setWorkplace(member.getWorkplaceDesc());
        eventMember.setUpdatedAt(LocalDateTime.now());
    }
}