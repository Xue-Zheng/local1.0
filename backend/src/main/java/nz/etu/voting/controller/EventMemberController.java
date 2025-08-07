package nz.etu.voting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.AttendanceRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.service.TicketEmailService;
import nz.etu.voting.service.StratumService;
import nz.etu.voting.service.VenueService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

//统一的EventMember控制器 - 一套系统架构 + 专门处理出席确认 + 自动票据邮件发送
@Slf4j
@RestController
@RequestMapping("/api/event-registration")
@CrossOrigin(origins = {"http://localhost:3000", "http://10.0.9.238:3000", "https://events.etu.nz"})
@RequiredArgsConstructor
public class EventMemberController {

    private final EventMemberRepository eventMemberRepository;
    private final TicketEmailService ticketEmailService;
    private final StratumService stratumService;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final VenueService venueService;

    //    通过token获取EventMember信息
    @GetMapping("/member/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventMember(@PathVariable String token) {
        try {
            log.info("🔍 Getting EventMember with token: {}", token);
            String cleanToken = token.trim();

            // First try to parse as UUID
            if (cleanToken.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                UUID uuid = UUID.fromString(cleanToken);
                Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

                if (memberOpt.isPresent()) {
                    EventMember member = memberOpt.get();
                    log.info("✅ Found EventMember by UUID token: membershipNumber={}, bmmStage={}",
                            member.getMembershipNumber(), member.getBmmRegistrationStage());
                    Map<String, Object> response = buildEventMemberResponse(member);
                    return ResponseEntity.ok(ApiResponse.success("Member information retrieved", response));
                }
            }

            // If not UUID or not found by UUID, try as memberToken
            Optional<EventMember> memberByMemberToken = eventMemberRepository.findByMemberToken(cleanToken);
            if (memberByMemberToken.isPresent()) {
                EventMember member = memberByMemberToken.get();
                log.info("✅ Found EventMember by memberToken: membershipNumber={}, bmmStage={}",
                        member.getMembershipNumber(), member.getBmmRegistrationStage());
                Map<String, Object> response = buildEventMemberResponse(member);
                return ResponseEntity.ok(ApiResponse.success("Member information retrieved", response));
            }

            return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));

        } catch (Exception e) {
            log.error("Error processing member token '{}': {}", token, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Error processing request: " + e.getMessage()));
        }
    }

    //    核心功能：更新出席选择 + 自动发送票据邮件 + 这是统一系统的关键功能
    @PostMapping("/attendance/{token}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAttendance(
            @PathVariable String token,
            @RequestBody Map<String, Object> request) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            if (!member.getHasRegistered()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member has not completed registration, cannot update attendance choice"));
            }

//            记录之前的出席状态
            boolean wasAttending = member.getIsAttending();

            log.info("🎯 Updating attendance for EventMember {}: isAttending={}, hasAbsenceReason={}",
                    member.getMembershipNumber(),
                    request.get("isAttending"),
                    request.get("absenceReason") != null);

//            更新出席状态和相关信息
            member.setIsAttending((Boolean) request.get("isAttending"));

            if (!((Boolean) request.get("isAttending"))) {
//                不出席的情况
                if (request.get("absenceReason") != null) {
                    member.setAbsenceReason((String) request.get("absenceReason"));
                }

                // 处理特殊投票申请
                boolean isSpecialVote = (Boolean) request.get("isSpecialVote") != null && (Boolean) request.get("isSpecialVote");
                member.setIsSpecialVote(isSpecialVote);

                // 处理特殊投票理由 - 支持"其他"原因的详细说明
                if (request.get("specialVoteReason") != null && !((String) request.get("specialVoteReason")).trim().isEmpty()) {
                    String specialReason = (String) request.get("specialVoteReason");
                    // 将特殊投票理由追加到缺席理由中，便于管理员查看
                    String currentAbsenceReason = member.getAbsenceReason();
                    if ("other".equals(currentAbsenceReason)) {
                        member.setAbsenceReason("other: " + specialReason);
                    }
                }

                log.info("Member {} cannot attend - Reason: {}, Special Vote: {}",
                        member.getMembershipNumber(), member.getAbsenceReason(), member.getIsSpecialVote());
            } else {
//                出席的情况，清除不出席相关信息
                member.setAbsenceReason(null);
                member.setIsSpecialVote(false);
                log.info("Member {} confirmed attendance", member.getMembershipNumber());
            }

//            更新时间戳
            member.setAttendanceDecisionMadeAt(LocalDateTime.now());
            member.setLastActivityAt(LocalDateTime.now());

//            保存更新
            eventMemberRepository.save(member);

//            关键功能：如果会员刚确认出席，立即自动发送票据邮件
            if (!wasAttending && member.getIsAttending()) {
                log.info("TICKET: Member {} just confirmed attendance - sending ticket email automatically",
                        member.getMembershipNumber());

                try {
                    if (member.getPrimaryEmail() != null && !member.getPrimaryEmail().isEmpty()) {
                        // Use BMM-specific ticket sending method
                        ticketEmailService.sendBMMTicketOnConfirmation(member);
                        log.info("✅ BMM Ticket email sent successfully to {}", member.getPrimaryEmail());
                    } else {
                        log.warn("⚠️ Cannot send ticket email - member has no email address");
                    }
                } catch (Exception emailError) {
                    log.error("❌ Failed to send BMM ticket email to member {}: {}",
                            member.getMembershipNumber(), emailError.getMessage());
//                            不影响主流程，继续返回成功
                }
            }

            Map<String, Object> response = buildEventMemberResponse(member);

//            添加票据信息到响应
            if (member.getIsAttending()) {
                // Use ticketToken instead of member token for ticket access
                String ticketToken = member.getTicketToken() != null ?
                        member.getTicketToken().toString() : member.getToken().toString();
                response.put("ticketUrl", String.format("https://events.etu.nz/ticket?token=%s", ticketToken));
                response.put("qrCodeEmailSent", member.getQrCodeEmailSent());
                response.put("ticketMessage", "TICKET: Your ticket email has been sent! Please check your email or click the link above to view your digital ticket.");
            }

            return ResponseEntity.ok(ApiResponse.success("Attendance choice updated successfully", response));

        } catch (Exception e) {
            log.error("Failed to update attendance choice: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    更新EventMember表单信息 - 带路径参数版本
    @PostMapping("/update-form/{token}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEventMemberFormWithToken(
            @PathVariable String token,
            @RequestBody Map<String, Object> request) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

//            更新基本信息
            if (request.containsKey("name")) {
                member.setName((String) request.get("name"));
            }
            if (request.containsKey("primaryEmail")) {
                member.setPrimaryEmail((String) request.get("primaryEmail"));
            }
            if (request.containsKey("telephoneMobile")) {
                member.setTelephoneMobile((String) request.get("telephoneMobile"));
            }
            if (request.containsKey("regionDesc")) {
                member.setRegionDesc((String) request.get("regionDesc"));
            }
            if (request.containsKey("workplace")) {
                member.setWorkplace((String) request.get("workplace"));
            }
            if (request.containsKey("employer")) {
                member.setEmployer((String) request.get("employer"));
            }

//            标记为已注册
            member.setHasRegistered(true);
            member.setUpdatedAt(LocalDateTime.now());

            eventMemberRepository.save(member);

            // CRITICAL: 关键修复：同步更新关联的Member表并触发Stratum同步
            if (member.getMember() != null) {
                try {
                    Member linkedMember = member.getMember();

                    // 同步基本信息到Member表
                    if (request.containsKey("name")) {
                        linkedMember.setName((String) request.get("name"));
                    }
                    if (request.containsKey("primaryEmail")) {
                        linkedMember.setPrimaryEmail((String) request.get("primaryEmail"));
                    }
                    if (request.containsKey("telephoneMobile")) {
                        linkedMember.setTelephoneMobile((String) request.get("telephoneMobile"));
                    }

                    // CRITICAL: 关键：更新Member表的注册状态和联系方式状态
                    linkedMember.setHasRegistered(true);
                    linkedMember.setUpdatedAt(LocalDateTime.now());

                    // 更新联系方式状态标志
                    linkedMember.setHasEmail(linkedMember.getPrimaryEmail() != null &&
                            !linkedMember.getPrimaryEmail().trim().isEmpty() &&
                            !linkedMember.getPrimaryEmail().contains("@temp-email.etu.nz"));
                    linkedMember.setHasMobile(linkedMember.getTelephoneMobile() != null &&
                            !linkedMember.getTelephoneMobile().trim().isEmpty());

                    // 保存Member更新
                    memberRepository.save(linkedMember);

                    log.info("🔄 Member table updated for {}: hasRegistered=true, hasEmail={}, hasMobile={}",
                            linkedMember.getMembershipNumber(), linkedMember.getHasEmail(), linkedMember.getHasMobile());

                    // CRITICAL: 触发Stratum同步
                    boolean syncResult = stratumService.syncMemberToStratum(linkedMember);

                    if (syncResult) {
                        log.info("✅ Registration information successfully synchronized to Stratum for member: {}", member.getMembershipNumber());
                    } else {
                        log.warn("⚠️ Failed to synchronize registration to Stratum for member: {}, but local data has been updated", member.getMembershipNumber());
                    }

                } catch (Exception e) {
                    log.error("❌ Failed to synchronize registration to Stratum for member {}: {}", member.getMembershipNumber(), e.getMessage(), e);
                    // 不影响主流程，继续返回成功响应
                }
            } else {
                log.warn("No linked Member found for EventMember {}, skipping Member table and Stratum sync", member.getMembershipNumber());
            }

            log.info("EventMember form updated for member: {}", member.getMembershipNumber());

            Map<String, Object> response = buildEventMemberResponse(member);
            return ResponseEntity.ok(ApiResponse.success("Registration information updated successfully", response));

        } catch (Exception e) {
            log.error("Failed to update EventMember form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    更新EventMember表单信息 - 兼容旧版本（token在请求体中）
    @PostMapping("/update-form")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEventMemberForm(
            @RequestBody Map<String, Object> request) {
        try {
            String token = (String) request.get("token");
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

//            更新基本信息
            if (request.containsKey("name")) {
                member.setName((String) request.get("name"));
            }
            if (request.containsKey("primaryEmail")) {
                member.setPrimaryEmail((String) request.get("primaryEmail"));
            }
            if (request.containsKey("telephoneMobile")) {
                member.setTelephoneMobile((String) request.get("telephoneMobile"));
            }
            if (request.containsKey("regionDesc")) {
                member.setRegionDesc((String) request.get("regionDesc"));
            }
            if (request.containsKey("workplace")) {
                member.setWorkplace((String) request.get("workplace"));
            }
            if (request.containsKey("employer")) {
                member.setEmployer((String) request.get("employer"));
            }

//            标记为已注册
            member.setHasRegistered(true);
            member.setUpdatedAt(LocalDateTime.now());

            eventMemberRepository.save(member);

            // CRITICAL: 关键修复：同步更新关联的Member表并触发Stratum同步
            if (member.getMember() != null) {
                try {
                    Member linkedMember = member.getMember();

                    // 同步基本信息到Member表
                    if (request.containsKey("name")) {
                        linkedMember.setName((String) request.get("name"));
                    }
                    if (request.containsKey("primaryEmail")) {
                        linkedMember.setPrimaryEmail((String) request.get("primaryEmail"));
                    }
                    if (request.containsKey("telephoneMobile")) {
                        linkedMember.setTelephoneMobile((String) request.get("telephoneMobile"));
                    }

                    // CRITICAL: 关键：更新Member表的注册状态和联系方式状态
                    linkedMember.setHasRegistered(true);
                    linkedMember.setUpdatedAt(LocalDateTime.now());

                    // 更新联系方式状态标志
                    linkedMember.setHasEmail(linkedMember.getPrimaryEmail() != null &&
                            !linkedMember.getPrimaryEmail().trim().isEmpty() &&
                            !linkedMember.getPrimaryEmail().contains("@temp-email.etu.nz"));
                    linkedMember.setHasMobile(linkedMember.getTelephoneMobile() != null &&
                            !linkedMember.getTelephoneMobile().trim().isEmpty());

                    // 保存Member更新
                    memberRepository.save(linkedMember);

                    log.info("🔄 Member table updated for {}: hasRegistered=true, hasEmail={}, hasMobile={}",
                            linkedMember.getMembershipNumber(), linkedMember.getHasEmail(), linkedMember.getHasMobile());

                    // CRITICAL: 触发Stratum同步
                    boolean syncResult = stratumService.syncMemberToStratum(linkedMember);

                    if (syncResult) {
                        log.info("✅ Registration information successfully synchronized to Stratum for member: {}", member.getMembershipNumber());
                    } else {
                        log.warn("⚠️ Failed to synchronize registration to Stratum for member: {}, but local data has been updated", member.getMembershipNumber());
                    }

                } catch (Exception e) {
                    log.error("❌ Failed to synchronize registration to Stratum for member {}: {}", member.getMembershipNumber(), e.getMessage(), e);
                    // 不影响主流程，继续返回成功响应
                }
            } else {
                log.warn("No linked Member found for EventMember {}, skipping Member table and Stratum sync", member.getMembershipNumber());
            }

            log.info("EventMember form updated for member: {}", member.getMembershipNumber());

            Map<String, Object> response = buildEventMemberResponse(member);
            return ResponseEntity.ok(ApiResponse.success("Registration information updated successfully", response));

        } catch (Exception e) {
            log.error("Failed to update EventMember form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    BMM专用：处理包含Financial Form字段的完整注册信息更新
    @PostMapping("/update-bmm-registration/{token}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateBMMRegistration(
            @PathVariable String token,
            @RequestBody Map<String, Object> request) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

//            更新基本信息
            if (request.containsKey("name")) {
                member.setName((String) request.get("name"));
            }
            if (request.containsKey("primaryEmail")) {
                member.setPrimaryEmail((String) request.get("primaryEmail"));
            }
            if (request.containsKey("telephoneMobile")) {
                member.setTelephoneMobile((String) request.get("telephoneMobile"));
            }
            if (request.containsKey("regionDesc")) {
                member.setRegionDesc((String) request.get("regionDesc"));
            }
            if (request.containsKey("workplace")) {
                member.setWorkplace((String) request.get("workplace"));
            }
            if (request.containsKey("employer")) {
                member.setEmployer((String) request.get("employer"));
            }

//            处理BMM Financial Form额外字段
//            使用registrationData JSON字段存储完整的财务表单信息
            Map<String, Object> financialData = new HashMap<>();

            // 收集所有financial form字段
            String[] financialFields = {
                    "industry", "address", "phoneHome", "phoneWork", "payrollNumber",
                    "siteCode", "employmentStatus", "department", "jobTitle",
                    "location", "dob"
            };

            for (String field : financialFields) {
                if (request.containsKey(field)) {
                    financialData.put(field, request.get(field));
                }
            }

            // CRITICAL: 处理BMM Preferences数据 - 使用新的字段结构
            if (request.containsKey("bmmPreferences")) {
                Object bmmPreferencesObj = request.get("bmmPreferences");
                if (bmmPreferencesObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bmmPrefs = (Map<String, Object>) bmmPreferencesObj;

                    // Store BMM preferences in separate fields for easier querying
                    if (bmmPrefs.containsKey("preferredVenues")) {
                        Object venuesObj = bmmPrefs.get("preferredVenues");
                        if (venuesObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> venues = (List<String>) venuesObj;
                            member.setPreferredVenues(String.join(",", venues));
                            log.info("BMM Preferred venues for {}: {}", member.getMembershipNumber(), venues);
                        }
                    }

                    if (bmmPrefs.containsKey("preferredTimes")) {
                        Object timesObj = bmmPrefs.get("preferredTimes");
                        if (timesObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> times = (List<String>) timesObj;
                            member.setPreferredTimes(String.join(",", times));
                            log.info("BMM Preferred times for {}: {}", member.getMembershipNumber(), times);
                        }
                    }

                    if (bmmPrefs.containsKey("attendanceWillingness")) {
                        member.setAttendanceWillingness((String) bmmPrefs.get("attendanceWillingness"));
                    }

                    if (bmmPrefs.containsKey("workplaceInfo")) {
                        member.setWorkplaceInfo((String) bmmPrefs.get("workplaceInfo"));
                    }

                    if (bmmPrefs.containsKey("meetingFormat")) {
                        member.setMeetingFormat((String) bmmPrefs.get("meetingFormat"));
                    }

                    if (bmmPrefs.containsKey("additionalComments")) {
                        member.setAdditionalComments((String) bmmPrefs.get("additionalComments"));
                    }

                    if (bmmPrefs.containsKey("suggestedVenue")) {
                        member.setSuggestedVenue((String) bmmPrefs.get("suggestedVenue"));
                    }

                    // 🗳️ 处理Southern Region Special Vote Preference
                    if (bmmPrefs.containsKey("specialVotePreference")) {
                        String specialVotePreference = (String) bmmPrefs.get("specialVotePreference");
                        member.setSpecialVotePreference(specialVotePreference);

                        // 确定是否符合Special Vote资格（使用VenueService验证）
                        if (venueService.isSpecialVoteEligible(member.getRegionDesc())) {
                            if ("YES".equals(specialVotePreference)) {
                                member.setSpecialVoteEligible(true);
                                member.setBmmSpecialVoteStatus("PENDING");
                                log.info("Special vote PENDING for Southern Region member {}", member.getMembershipNumber());
                            } else if ("NO".equals(specialVotePreference)) {
                                member.setSpecialVoteEligible(false);
                                member.setBmmSpecialVoteStatus("NOT_APPLICABLE");
                            } else if ("NOT_SURE".equals(specialVotePreference)) {
                                member.setSpecialVoteEligible(false);
                                member.setBmmSpecialVoteStatus("NOT_APPLICABLE");
                            }
                        } else {
                            // Northern Region成员，不符合special vote资格
                            member.setSpecialVoteEligible(false);
                            member.setBmmSpecialVoteStatus("NOT_APPLICABLE");
                        }
                    }

                    // CRITICAL: Store complete BMM preferences in the new JSON field
                    try {
                        StringBuilder jsonBuilder = new StringBuilder("{");
                        boolean first = true;
                        for (Map.Entry<String, Object> entry : bmmPrefs.entrySet()) {
                            if (!first) jsonBuilder.append(",");
                            jsonBuilder.append("\"").append(entry.getKey()).append("\":");

                            if (entry.getValue() instanceof List) {
                                @SuppressWarnings("unchecked")
                                List<String> list = (List<String>) entry.getValue();
                                jsonBuilder.append("[");
                                boolean firstItem = true;
                                for (String item : list) {
                                    if (!firstItem) jsonBuilder.append(",");
                                    jsonBuilder.append("\"").append(item).append("\"");
                                    firstItem = false;
                                }
                                jsonBuilder.append("]");
                            } else {
                                jsonBuilder.append("\"").append(entry.getValue() != null ? entry.getValue().toString() : "").append("\"");
                            }
                            first = false;
                        }
                        jsonBuilder.append("}");

                        member.setBmmPreferences(jsonBuilder.toString());
                        log.info("BMM Preferences JSON stored for member {}: {}", member.getMembershipNumber(), jsonBuilder.toString());
                    } catch (Exception jsonError) {
                        log.warn("Failed to serialize BMM preferences for member {}: {}", member.getMembershipNumber(), jsonError.getMessage());
                    }

                    // Also store in financialData for backward compatibility
                    financialData.put("bmmPreferences", bmmPrefs);

                    log.info("BMM Preferences stored for member {}: venues={}, times={}, willingness={}, format={}, specialVote={}",
                            member.getMembershipNumber(),
                            member.getPreferredVenues(),
                            member.getPreferredTimes(),
                            member.getAttendanceWillingness(),
                            member.getMeetingFormat(),
                            member.getSpecialVotePreference());
                }
            }

            // 将财务数据和BMM preferences存储为JSON
            if (!financialData.isEmpty()) {
                try {
                    // 简单的JSON序列化 - 在生产环境中应该使用Jackson ObjectMapper
                    StringBuilder jsonBuilder = new StringBuilder("{");
                    boolean first = true;
                    for (Map.Entry<String, Object> entry : financialData.entrySet()) {
                        if (!first) jsonBuilder.append(",");
                        jsonBuilder.append("\"").append(entry.getKey()).append("\":");

                        if (entry.getValue() instanceof Map || entry.getValue() instanceof List) {
                            // For complex objects, store as string representation
                            jsonBuilder.append("\"").append(entry.getValue().toString()).append("\"");
                        } else {
                            jsonBuilder.append("\"").append(entry.getValue() != null ? entry.getValue().toString() : "").append("\"");
                        }
                        first = false;
                    }
                    jsonBuilder.append("}");

                    member.setRegistrationData(jsonBuilder.toString());
                    log.info("BMM Complete registration data stored for member {}: {}", member.getMembershipNumber(), jsonBuilder.toString());
                } catch (Exception jsonError) {
                    log.warn("Failed to serialize complete registration data for member {}: {}", member.getMembershipNumber(), jsonError.getMessage());
                }
            }

//            🎯 更新BMM注册状态管理
            member.setHasRegistered(true);
            member.setRegistrationStep("UPDATE_INFO");
            member.setBmmRegistrationStage("PREFERENCE_SUBMITTED"); // 从 PENDING 更新为 PREFERENCE_SUBMITTED
            member.setFormSubmissionTime(LocalDateTime.now());
            member.setUpdatedAt(LocalDateTime.now());
            member.setBmmLastInteractionAt(LocalDateTime.now());

            // CRITICAL: Generate ticket token for BMM process
            if (member.getTicketToken() == null) {
                member.setTicketToken(UUID.randomUUID());
                member.setTicketStatus("PENDING");
                log.info("Generated ticket token for BMM member {}: {}", member.getMembershipNumber(), member.getTicketToken());
            }

            // CRITICAL: 判断出席意愿并设置相应的状态
            if ("yes".equals(member.getAttendanceWillingness())) {
                member.setIsAttending(true);
                member.setAttendanceLikelihoodScore(0.8); // 高概率出席
                log.info("Member {} indicates willingness to attend BMM", member.getMembershipNumber());
            } else if ("no".equals(member.getAttendanceWillingness())) {
                member.setIsAttending(false);
                member.setAttendanceLikelihoodScore(0.1); // 低概率出席
                log.info("Member {} indicates unwillingness to attend BMM", member.getMembershipNumber());
            } else {
                member.setAttendanceLikelihoodScore(0.5); // 中等概率
            }

            eventMemberRepository.save(member);

            // 🗳️ CRITICAL: BMM注册信息更新后同步到Stratum系统（直接使用EventMember，不依赖Member表）
            try {
                log.info("🔄 BMM registration using EventMember table only for {}: hasRegistered=true", member.getMembershipNumber());

                // 直接同步EventMember到Stratum系统
                boolean syncResult = stratumService.syncEventMemberToStratum(member);

                if (syncResult) {
                    log.info("✅ BMM registration information successfully synchronized to Stratum for member: {}", member.getMembershipNumber());
                } else {
                    log.warn("⚠️ Failed to synchronize BMM registration to Stratum for member: {}, but local data has been updated", member.getMembershipNumber());
                }

            } catch (Exception e) {
                log.error("❌ Failed to synchronize BMM registration to Stratum for member {}: {}", member.getMembershipNumber(), e.getMessage(), e);
                // 不影响主流程，继续返回成功响应
            }

            log.info("BMM registration form completed for member: {} with financial declaration", member.getMembershipNumber());

            Map<String, Object> response = buildEventMemberResponse(member);
            return ResponseEntity.ok(ApiResponse.success("BMM registration information updated successfully", response));

        } catch (Exception e) {
            log.error("Failed to update BMM registration form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    获取EventMember基本信息
    @GetMapping("/member-info/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEventMemberInfo(@PathVariable String token) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            Map<String, Object> info = new HashMap<>();
            info.put("membershipNumber", member.getMembershipNumber());
            info.put("verificationCode", member.getVerificationCode());
            info.put("name", member.getName());
            info.put("primaryEmail", member.getPrimaryEmail());
            info.put("hasRegistered", member.getHasRegistered());
            info.put("isAttending", member.getIsAttending());

//            事件信息
            if (member.getEvent() != null) {
                Map<String, Object> eventInfo = new HashMap<>();
                eventInfo.put("id", member.getEvent().getId());
                eventInfo.put("name", member.getEvent().getName());
                eventInfo.put("code", member.getEvent().getEventCode());
                eventInfo.put("type", member.getEvent().getEventType());
                info.put("event", eventInfo);
            }

            return ResponseEntity.ok(ApiResponse.success("Member info retrieved", info));

        } catch (Exception e) {
            log.error("Failed to get member info: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    向后兼容：支持传统的验证流程
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyEventMember(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            String membershipNumber = request.get("membershipNumber");
            String verificationCode = request.get("verificationCode");

            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token"));
            }

            EventMember member = memberOpt.get();

            if (!member.getMembershipNumber().equals(membershipNumber) ||
                    !member.getVerificationCode().equals(verificationCode)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Membership number or verification code does not match"));
            }

            Map<String, Object> response = buildEventMemberResponse(member);
            return ResponseEntity.ok(ApiResponse.success("Verification successful", response));

        } catch (Exception e) {
            log.error("Verification failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    //    构建EventMember响应对象 - 包含BMM表单需要的所有字段
    // 📝 特殊投票申请提交 (仅Southern Region不出席会员)
    @PostMapping("/special-vote/{token}")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> submitSpecialVoteApplication(
            @PathVariable String token,
            @RequestBody Map<String, Object> request) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            // 验证：只有Central和Southern Region成员才能申请特殊投票（使用VenueService验证）
            if (!venueService.isSpecialVoteEligible(member.getRegionDesc())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Special vote applications are only available for Central and Southern Region members"));
            }

            // 验证：只有不出席的会员才能申请特殊投票
            if (member.getIsAttending() == null || member.getIsAttending()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Special vote applications are only for members who cannot attend the meeting"));
            }

            // 验证：必须有不出席理由
            if (member.getAbsenceReason() == null || member.getAbsenceReason().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Please confirm your non-attendance first"));
            }

            // 构建特殊投票申请数据
            Map<String, Object> specialVoteData = new HashMap<>();
            specialVoteData.put("eligibilityReason", request.get("eligibilityReason"));
            specialVoteData.put("supportingEvidence", request.get("supportingEvidence"));
            specialVoteData.put("additionalInfo", request.get("additionalInfo"));
            specialVoteData.put("contactPhone", request.get("contactPhone"));
            specialVoteData.put("returnAddress", request.get("returnAddress"));
            specialVoteData.put("declarationAgreed", request.get("declarationAgreed"));
            specialVoteData.put("submittedAt", LocalDateTime.now().toString());
            specialVoteData.put("membershipNumber", member.getMembershipNumber());
            specialVoteData.put("region", member.getRegionDesc());
            specialVoteData.put("absenceReason", member.getAbsenceReason());

            // 更新EventMember的注册数据，添加特殊投票申请信息
            String existingDataStr = member.getRegistrationData();
            Map<String, Object> existingData = new HashMap<>();

            if (existingDataStr != null && !existingDataStr.trim().isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    existingData = mapper.readValue(existingDataStr, Map.class);
                } catch (Exception e) {
                    log.warn("Failed to parse existing registration data for member {}: {}", member.getMembershipNumber(), e.getMessage());
                }
            }

            existingData.put("specialVoteApplication", specialVoteData);

            try {
                ObjectMapper mapper = new ObjectMapper();
                member.setRegistrationData(mapper.writeValueAsString(existingData));
            } catch (Exception e) {
                log.error("Failed to serialize registration data for member {}: {}", member.getMembershipNumber(), e.getMessage());
                return ResponseEntity.badRequest().body(ApiResponse.error("Failed to save application data"));
            }

            // 标记为特殊投票申请者
            member.setIsSpecialVote(true);
            member.setUpdatedAt(LocalDateTime.now());
            member.setSpecialVoteAppliedAt(LocalDateTime.now());
            member.setSpecialVoteSubmittedDate(LocalDateTime.now());
            member.setSpecialVoteEligibilityReason((String) request.get("eligibilityReason"));
            member.setSpecialVoteDetails((String) request.get("supportingEvidence"));

            eventMemberRepository.save(member);

            log.info("Special vote application submitted for {} member: {}", member.getRegionDesc(), member.getMembershipNumber());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Special vote application submitted successfully");
            response.put("applicationId", member.getId());
            response.put("submittedAt", specialVoteData.get("submittedAt"));
            response.put("membershipNumber", member.getMembershipNumber());
            response.put("region", member.getRegionDesc());

            return ResponseEntity.ok(ApiResponse.success("Special vote application submitted successfully", response));

        } catch (Exception e) {
            log.error("Failed to submit special vote application: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to submit special vote application: " + e.getMessage()));
        }
    }

    // 🎯 BMM Second Stage: Attendance Confirmation
    @PostMapping("/confirm-bmm-attendance/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> confirmBmmAttendance(
            @PathVariable String token,
            @RequestBody Map<String, Object> confirmationData) {
        try {
            log.info("Processing BMM attendance confirmation for token: {}", token);

            Optional<EventMember> eventMemberOpt = eventMemberRepository.findByToken(UUID.fromString(token));
            if (!eventMemberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token or member not found"));
            }

            EventMember eventMember = eventMemberOpt.get();

            // Validate that this is a BMM event
            if (!eventMember.getEvent().getEventType().equals(Event.EventType.BMM_VOTING)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("This endpoint is only for BMM events"));
            }

            // Update attendance confirmation
            boolean isAttending = (Boolean) confirmationData.getOrDefault("isAttending", false);
            eventMember.setIsAttending(isAttending);

            if (isAttending) {
                // 确认出席
                eventMember.setBmmRegistrationStage("ATTENDANCE_CONFIRMED");
                eventMember.setBmmAttendanceConfirmedAt(LocalDateTime.now());

                // TICKET: 自动生成并发送ticket
                try {
                    ticketEmailService.sendBMMTicketOnConfirmation(eventMember);
                    log.info("BMM ticket sent automatically for confirmed attendance: {}",
                            eventMember.getMembershipNumber());
                } catch (Exception e) {
                    log.error("Failed to send BMM ticket for member {}: {}",
                            eventMember.getMembershipNumber(), e.getMessage());
                    // Don't fail the confirmation if ticket sending fails
                }
            } else {
                // 拒绝出席
                eventMember.setBmmRegistrationStage("ATTENDANCE_DECLINED");
                eventMember.setBmmAttendanceDeclinedAt(LocalDateTime.now());

                // 对于Central和Southern Region会员，如果拒绝出席，可能需要发送特殊投票邮件（使用VenueService验证）
                if (venueService.isSpecialVoteEligible(eventMember.getRegionDesc())) {
                    eventMember.setSpecialVoteEligible(true);
                    log.info("{} member declined attendance, marked as special vote eligible: {}", eventMember.getRegionDesc(),
                            eventMember.getMembershipNumber());
                }
            }

            // Save additional confirmation data
            if (confirmationData.containsKey("additionalComments")) {
                eventMember.setAdditionalComments((String) confirmationData.get("additionalComments"));
            }

            eventMember.setLastActivityAt(LocalDateTime.now());
            eventMemberRepository.save(eventMember);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", eventMember.getMembershipNumber());
            response.put("name", eventMember.getName());
            response.put("isAttending", eventMember.getIsAttending());
            response.put("bmmStage", eventMember.getBmmRegistrationStage());
            response.put("ticketStatus", eventMember.getTicketStatus());
            response.put("hasTicket", eventMember.getTicketToken() != null);

            if (eventMember.getTicketToken() != null) {
                response.put("ticketUrl", "https://events.etu.nz/ticket?token=" + eventMember.getTicketToken());
            }

            log.info("BMM attendance confirmation processed successfully for member: {} (attending: {})",
                    eventMember.getMembershipNumber(), isAttending);

            return ResponseEntity.ok(ApiResponse.success(
                    isAttending ? "Attendance confirmed and ticket sent" : "Attendance declined",
                    response));

        } catch (Exception e) {
            log.error("Failed to process BMM attendance confirmation: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to process confirmation: " + e.getMessage()));
        }
    }

    // 🎯 BMM Management: Get BMM Status and Assignment
    @GetMapping("/bmm-status/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMStatus(@PathVariable String token) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();

            Map<String, Object> response = new HashMap<>();
            response.put("membershipNumber", member.getMembershipNumber());
            response.put("name", member.getName());
            response.put("region", member.getRegionDesc());
            response.put("bmmRegistrationStage", member.getBmmRegistrationStage());
            response.put("hasRegistered", member.getHasRegistered());
            response.put("isAttending", member.getIsAttending());
            response.put("attendanceWillingness", member.getAttendanceWillingness());

            // BMM偏好信息
            response.put("preferredVenues", member.getPreferredVenues());
            response.put("preferredTimes", member.getPreferredTimes());
            response.put("meetingFormat", member.getMeetingFormat());
            response.put("bmmPreferences", member.getBmmPreferences());

            // 分配信息
            response.put("assignedVenue", member.getAssignedVenue());
            response.put("assignedDateTime", member.getAssignedDateTime());
            response.put("assignedRegion", member.getAssignedRegion());

            // 票据信息
            response.put("ticketStatus", member.getTicketStatus());
            response.put("ticketToken", member.getTicketToken());
            response.put("ticketGeneratedAt", member.getTicketGeneratedAt());

            // Special Vote信息
            response.put("specialVoteEligible", member.getSpecialVoteEligible());
            response.put("specialVotePreference", member.getSpecialVotePreference());
            response.put("bmmSpecialVoteStatus", member.getBmmSpecialVoteStatus());

            // 时间戳
            response.put("formSubmissionTime", member.getFormSubmissionTime());
            response.put("bmmAttendanceConfirmedAt", member.getBmmAttendanceConfirmedAt());
            response.put("bmmAttendanceDeclinedAt", member.getBmmAttendanceDeclinedAt());
            response.put("bmmLastInteractionAt", member.getBmmLastInteractionAt());

            return ResponseEntity.ok(ApiResponse.success("BMM status retrieved successfully", response));

        } catch (Exception e) {
            log.error("Failed to get BMM status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private Map<String, Object> buildEventMemberResponse(EventMember member) {
        Map<String, Object> response = new HashMap<>();

        // 基本信息
        response.put("id", member.getId());
        response.put("name", member.getName());
        response.put("primaryEmail", member.getPrimaryEmail());
        response.put("membershipNumber", member.getMembershipNumber());
        response.put("verificationCode", member.getVerificationCode());
        response.put("telephoneMobile", member.getTelephoneMobile());
        response.put("employer", member.getEmployer());
        response.put("hasRegistered", member.getHasRegistered());
        response.put("isAttending", member.getIsAttending());
        response.put("isSpecialVote", member.getIsSpecialVote());
        response.put("absenceReason", member.getAbsenceReason());
        response.put("token", member.getToken().toString());

        // EventMember直接字段
        response.put("regionDesc", member.getRegionDesc());
        response.put("workplace", member.getWorkplace());
        response.put("forumDesc", member.getForumDesc());

        // BMM specific fields
        response.put("bmmStage", member.getBmmRegistrationStage() != null ? member.getBmmRegistrationStage() : "not_started");
        response.put("preferredVenuesJson", member.getPreferredVenuesJson());
        response.put("preferredDatesJson", member.getPreferredDatesJson());
        response.put("preferredTimesJson", member.getPreferredTimesJson());
        response.put("preferredAttending", member.getPreferredAttending()); // 初步意向

        // BMM venue assignment
        response.put("assignedVenue", member.getAssignedVenue());
        response.put("assignedRegion", member.getAssignedRegion());
        response.put("assignedDateTime", member.getAssignedDateTime());

        // Get venue details from forum_desc
        if (member.getForumDesc() != null && !member.getForumDesc().trim().isEmpty()) {
            Map<String, Object> venueInfo = venueService.getVenueByForumDesc(member.getForumDesc());
            if (venueInfo != null) {
                // Create assignedVenue object for frontend
                Map<String, Object> assignedVenueObj = new HashMap<>();
                assignedVenueObj.put("id", venueInfo.get("id"));
                assignedVenueObj.put("name", venueInfo.get("forumDesc"));
                assignedVenueObj.put("venue", venueInfo.get("venue"));
                assignedVenueObj.put("address", venueInfo.get("address"));
                assignedVenueObj.put("date", venueInfo.get("date"));
                assignedVenueObj.put("capacity", venueInfo.get("capacity"));
                assignedVenueObj.put("region", member.getRegionDesc());
                response.put("assignedVenue", assignedVenueObj);

                // Also populate assignedDateTime if not already set and date is available
                if ((member.getAssignedDateTime() == null || member.getAssignedDateTime().toString().isEmpty())
                        && venueInfo.get("date") != null) {
                    response.put("assignedDateTime", venueInfo.get("date"));
                }
            }
        }

        // EventMember's own financial form fields (these take priority over Member table)
        // Include both field names for better compatibility
        response.put("postalAddress", member.getAddress());
        response.put("address", member.getAddress()); // Also include as 'address' for frontend compatibility
        response.put("phoneHome", member.getPhoneHome());
        response.put("phoneWork", member.getPhoneWork());

        // Debug logging for financial form fields
        log.info("🔍 EventMember financial fields - membershipNumber: {}, primaryEmail: {}, postalAddress: {}, phoneHome: {}",
                member.getMembershipNumber(), member.getPrimaryEmail(), member.getAddress(), member.getPhoneHome());
        response.put("payrollNumber", member.getPayrollNumber());
        response.put("siteCode", member.getSiteCode());
        response.put("employmentStatus", member.getEmploymentStatus());
        response.put("department", member.getDepartment());
        response.put("jobTitle", member.getJobTitle());
        response.put("location", member.getLocation());
        response.put("dob", member.getDob());

        // 如果有关联的Member，获取更多详细信息自动填充BMM表单 (fallback values)
        if (member.getMember() != null) {
            Member linkedMember = member.getMember();
            log.info("🔍 Member table fallback fields - membershipNumber: {}, primaryEmail: {}, address: {}, phoneHome: {}",
                    linkedMember.getMembershipNumber(), linkedMember.getPrimaryEmail(), linkedMember.getAddress(), linkedMember.getPhoneHome());

            // BMM Financial Form需要的字段 (only if EventMember fields are empty)
            if (member.getAddress() == null || member.getAddress().isEmpty()) {
                response.put("postalAddress", linkedMember.getAddress());
                response.put("address", linkedMember.getAddress()); // Also include as 'address' for frontend compatibility
                log.info("🔄 Using Member table address for postalAddress: {}", linkedMember.getAddress());
            }
            if (member.getPhoneHome() == null || member.getPhoneHome().isEmpty()) {
                response.put("phoneHome", linkedMember.getPhoneHome());
            }
            if (member.getPhoneWork() == null || member.getPhoneWork().isEmpty()) {
                response.put("phoneWork", linkedMember.getPhoneWork());
            }
            if (member.getPayrollNumber() == null || member.getPayrollNumber().isEmpty()) {
                response.put("payrollNumber", linkedMember.getPayrollNumber());
            }
            if (member.getSiteCode() == null || member.getSiteCode().isEmpty()) {
                response.put("siteCode", linkedMember.getSiteNumber());
            }
            if (member.getEmploymentStatus() == null || member.getEmploymentStatus().isEmpty()) {
                response.put("employmentStatus", linkedMember.getEmploymentStatus());
            }
            if (member.getDepartment() == null || member.getDepartment().isEmpty()) {
                response.put("department", linkedMember.getDepartment());
            }
            if (member.getJobTitle() == null || member.getJobTitle().isEmpty()) {
                response.put("jobTitle", linkedMember.getJobTitle());
            }
            if (member.getLocation() == null || member.getLocation().isEmpty()) {
                response.put("location", linkedMember.getLocation());
            }
            if (member.getDob() == null || member.getDob().isEmpty()) {
                response.put("dob", linkedMember.getDob());
            }

            // Additional fields for reference
            response.put("occupation", linkedMember.getOccupation());
            response.put("employerName", linkedMember.getEmployerName());
            response.put("workplaceDesc", linkedMember.getWorkplaceDesc());
            response.put("siteIndustryDesc", linkedMember.getSiteIndustryDesc());
        }

//        事件信息
        if (member.getEvent() != null) {
            Map<String, Object> eventInfo = new HashMap<>();
            eventInfo.put("id", member.getEvent().getId());
            eventInfo.put("name", member.getEvent().getName());
            eventInfo.put("code", member.getEvent().getEventCode());
            eventInfo.put("type", member.getEvent().getEventType());
            eventInfo.put("date", member.getEvent().getEventDate());
            eventInfo.put("venue", member.getEvent().getVenue());
            response.put("event", eventInfo);
        }

        return response;
    }

    // 🏢 BMM Venue Management Endpoints

    /**
     * Get available venues for a member's region
     */
    @GetMapping("/venues/region/{token}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVenuesForMemberRegion(@PathVariable String token) {
        try {
            UUID uuid = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(uuid);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

            EventMember member = memberOpt.get();
            String region = member.getRegionDesc();

            List<Map<String, Object>> venues = venueService.getVenuesByRegion(region);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("region", region);
            responseData.put("venues", venues);
            responseData.put("totalVenues", venues.size());
            responseData.put("specialVoteEligible", venueService.isSpecialVoteEligible(region));

            return ResponseEntity.ok(ApiResponse.success("Regional venues retrieved successfully", venues));

        } catch (Exception e) {
            log.error("Failed to get venues for member region: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get regional venues: " + e.getMessage()));
        }
    }

    /**
     * Get BMM venue configuration for registration form
     */
    @GetMapping("/venues/bmm-config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBMMVenueConfig() {
        try {
            Map<String, Object> regionSummary = venueService.getRegionSummary();
            Map<String, Object> stats = venueService.getVenueStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("regions", regionSummary);
            response.put("statistics", stats);
            response.put("allVenues", venueService.getAllVenueOptions());

            return ResponseEntity.ok(ApiResponse.success("BMM venue configuration retrieved", response));

        } catch (Exception e) {
            log.error("Failed to get BMM venue configuration: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get venue configuration: " + e.getMessage()));
        }
    }

    /**
     * Search venues by criteria
     */
    @GetMapping("/venues/search")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> searchVenues(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String term) {
        try {
            List<Map<String, Object>> results = venueService.findVenues(region, term);
            return ResponseEntity.ok(ApiResponse.success("Venue search completed", results));
        } catch (Exception e) {
            log.error("Failed to search venues: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to search venues: " + e.getMessage()));
        }
    }

    /**
     * Get venue options by forum description (for forums with multiple venues like Greymouth, Whangarei)
     */
    @GetMapping("/venues/options/{forumDesc}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getVenueOptionsByForum(@PathVariable String forumDesc) {
        try {
            log.info("Fetching venue options for forum: {}", forumDesc);

            // Get venue options from VenueService
            List<Map<String, Object>> venueOptions = venueService.getVenueOptionsByForumDesc(forumDesc);

            if (venueOptions.isEmpty()) {
                log.warn("No venue options found for forum: {}", forumDesc);
                return ResponseEntity.ok(ApiResponse.success("No venue options found", venueOptions));
            }

            log.info("Found {} venue options for forum {}", venueOptions.size(), forumDesc);
            return ResponseEntity.ok(ApiResponse.success("Venue options retrieved successfully", venueOptions));

        } catch (Exception e) {
            log.error("Failed to get venue options for forum {}: {}", forumDesc, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get venue options: " + e.getMessage()));
        }
    }


}