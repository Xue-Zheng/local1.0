package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Comparator;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/admin/registration")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
public class AdminRegistrationController {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final MemberRepository memberRepository;

    //    实时注册追踪 - 按时间排序
    @GetMapping("/events/{eventId}/recent-registrations")
    public ResponseEntity<Map<String, Object>> getRecentRegistrations(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "24") int hoursBack) {

        log.info("Fetching recent registrations for event: {}, hours back: {}", eventId, hoursBack);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Event not found"));
            }

            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hoursBack);

//            获取所有会员，然后在内存中筛选和排序
            List<EventMember> allMembers = eventMemberRepository.findByEvent(event);

            List<EventMember> recentRegistrations = allMembers.stream()
                    .filter(m -> {
                        LocalDateTime regTime = m.getFormSubmissionTime() != null ? m.getFormSubmissionTime() : m.getCreatedAt();
                        return regTime != null && regTime.isAfter(cutoffTime);
                    })
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getFormSubmissionTime() != null ? a.getFormSubmissionTime() : a.getCreatedAt();
                        LocalDateTime timeB = b.getFormSubmissionTime() != null ? b.getFormSubmissionTime() : b.getCreatedAt();
                        return timeB.compareTo(timeA);
                    })
                    .skip(page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            List<Map<String, Object>> registrationList = recentRegistrations.stream().map(member -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", member.getId());
                info.put("membershipNumber", member.getMembershipNumber());
                info.put("name", member.getName());
                info.put("primaryEmail", member.getPrimaryEmail());
                info.put("registrationTime", member.getCreatedAt());
                info.put("isAttending", member.getIsAttending());
                info.put("isSpecialVote", member.getIsSpecialVote());
                info.put("absenceReason", member.getAbsenceReason());
                info.put("region", member.getRegionDesc());
                info.put("workplace", member.getWorkplace());
                info.put("employer", member.getEmployer());
                info.put("industrySubDesc", member.getMember() != null ? member.getMember().getSiteIndustryDesc() : null);
                info.put("hasRegistered", member.getHasRegistered());
                info.put("emailSent", member.getEmailSent());
                info.put("smsSent", member.getSmsSent());
                return info;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("registrations", registrationList);
            data.put("totalFound", recentRegistrations.size());
            data.put("timeRange", hoursBack + " hours");
            data.put("cutoffTime", cutoffTime);
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch recent registrations", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    //    详细的注册状态分析
    @GetMapping("/events/{eventId}/registration-analytics")
    public ResponseEntity<Map<String, Object>> getRegistrationAnalytics(@PathVariable Long eventId) {
        log.info("Fetching registration analytics for event: {}", eventId);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Event not found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(event);

            Map<String, Object> analytics = new HashMap<>();

//            基础统计
            long totalInvited = allMembers.size();
            long registered = allMembers.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count();
            long notRegistered = totalInvited - registered;
            long attending = allMembers.stream().filter(m -> m.getIsAttending() != null && m.getIsAttending()).count();
            long notAttending = allMembers.stream().filter(m -> m.getIsAttending() != null && !m.getIsAttending()).count();
            long specialVote = allMembers.stream().filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote()).count();
            long incomplete = allMembers.stream()
                    .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                    .filter(m -> m.getIsAttending() != null && !m.getIsAttending())
                    .filter(m -> m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty())
                    .count();

            analytics.put("totalInvited", totalInvited);
            analytics.put("registered", registered);
            analytics.put("notRegistered", notRegistered);
            analytics.put("attending", attending);
            analytics.put("notAttending", notAttending);
            analytics.put("specialVote", specialVote);
            analytics.put("incompleteRegistrations", incomplete);
            analytics.put("registrationRate", totalInvited > 0 ? (registered * 100.0 / totalInvited) : 0);
            analytics.put("attendanceRate", registered > 0 ? (attending * 100.0 / registered) : 0);

//            按地区分析
            Map<String, Map<String, Long>> regionAnalysis = allMembers.stream()
                    .collect(Collectors.groupingBy(
                            m -> m.getRegionDesc() != null ? m.getRegionDesc() : "Unknown",
                            Collectors.groupingBy(
                                    m -> {
                                        if (m.getHasRegistered() == null || !m.getHasRegistered()) return "not_registered";
                                        if (m.getIsAttending() != null && m.getIsAttending()) return "attending";
                                        if (m.getIsAttending() != null && !m.getIsAttending()) {
                                            if (m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty()) {
                                                return "incomplete";
                                            }
                                            return m.getIsSpecialVote() != null && m.getIsSpecialVote() ? "special_vote" : "not_attending";
                                        }
                                        return "unknown";
                                    },
                                    Collectors.counting()
                            )
                    ));
            analytics.put("regionAnalysis", regionAnalysis);

//            按行业分析
            Map<String, Long> industryAnalysis = allMembers.stream()
                    .filter(m -> m.getMember() != null && m.getMember().getSiteIndustryDesc() != null && !m.getMember().getSiteIndustryDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(
                            m -> m.getMember().getSiteIndustryDesc(),
                            Collectors.counting()
                    ));
            analytics.put("industryAnalysis", industryAnalysis);

//            最近24小时注册趋势
            LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
            List<EventMember> recent24h = allMembers.stream()
                    .filter(m -> {
                        LocalDateTime regTime = m.getFormSubmissionTime() != null ? m.getFormSubmissionTime() : m.getCreatedAt();
                        return regTime != null && regTime.isAfter(last24Hours);
                    })
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getFormSubmissionTime() != null ? a.getFormSubmissionTime() : a.getCreatedAt();
                        LocalDateTime timeB = b.getFormSubmissionTime() != null ? b.getFormSubmissionTime() : b.getCreatedAt();
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());

            analytics.put("recent24hCount", recent24h.size());
            analytics.put("recent24hRegistrations", recent24h.stream().limit(10).map(m -> {
                Map<String, Object> info = new HashMap<>();
                info.put("name", m.getName());
                info.put("membershipNumber", m.getMembershipNumber());
                info.put("registrationTime", m.getFormSubmissionTime() != null ? m.getFormSubmissionTime() : m.getCreatedAt());
                info.put("isAttending", m.getIsAttending());
                return info;
            }).collect(Collectors.toList()));

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", analytics);
            response.put("eventName", event.getName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch registration analytics", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    //    按分类获取会员列表 (支持多种筛选条件)
    @PostMapping("/events/{eventId}/members-by-criteria")
    public ResponseEntity<Map<String, Object>> getMembersByCriteria(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching members by criteria for event: {}, criteria: {}", eventId, criteria);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Event not found"));
            }

            // 构建数据库查询参数，避免加载所有数据到内存
            Boolean hasRegistered = null;
            Boolean isAttending = null;
            Boolean hasVoted = null;
            Boolean checkedIn = null;
            String regionDesc = null;
            String genderDesc = null;
            String siteIndustryDesc = null;
            String siteSubIndustryDesc = null;
            String workplaceDesc = null;
            String employerName = null;
            String bargainingGroupDesc = null;
            String employmentStatus = null;
            String ethnicRegionDesc = null;
            String jobTitle = null;
            String department = null;
            String siteNumber = null;
            Boolean hasEmail = null;
            Boolean hasMobile = null;
            String branchDesc = null;
            String forumDesc = null;
            String membershipTypeDesc = null;
            String occupation = null;

            // 解析筛选条件
            if (criteria.containsKey("registrationStatus")) {
                String status = (String) criteria.get("registrationStatus");
                switch (status) {
                    case "registered":
                        hasRegistered = true;
                        break;
                    case "not_registered":
                        hasRegistered = false;
                        break;
                    case "attending":
                        isAttending = true;
                        break;
                    case "not_attending":
                        isAttending = false;
                        break;
                    // special_vote 和 incomplete 等复杂条件暂时使用原有逻辑
                }
            }

            // 解析其他筛选条件
            if (criteria.containsKey("region")) {
                regionDesc = (String) criteria.get("region");
            }
            if (criteria.containsKey("genderDesc")) {
                genderDesc = (String) criteria.get("genderDesc");
            }
            if (criteria.containsKey("siteIndustryDesc") || criteria.containsKey("industry")) {
                siteIndustryDesc = (String) (criteria.get("siteIndustryDesc") != null ?
                        criteria.get("siteIndustryDesc") : criteria.get("industry"));
            }
            if (criteria.containsKey("siteSubIndustryDesc")) {
                siteSubIndustryDesc = (String) criteria.get("siteSubIndustryDesc");
            }
            if (criteria.containsKey("workplaceDesc")) {
                workplaceDesc = (String) criteria.get("workplaceDesc");
            }
            if (criteria.containsKey("employerName")) {
                employerName = (String) criteria.get("employerName");
            }
            if (criteria.containsKey("bargainingGroupDesc")) {
                bargainingGroupDesc = (String) criteria.get("bargainingGroupDesc");
            }
            if (criteria.containsKey("employmentStatus")) {
                employmentStatus = (String) criteria.get("employmentStatus");
            }
            if (criteria.containsKey("ethnicRegionDesc")) {
                ethnicRegionDesc = (String) criteria.get("ethnicRegionDesc");
            }
            if (criteria.containsKey("hasEmail")) {
                hasEmail = (Boolean) criteria.get("hasEmail");
            }
            if (criteria.containsKey("hasMobile")) {
                hasMobile = (Boolean) criteria.get("hasMobile");
            }

            List<EventMember> filteredMembers;

            // 对于复杂的条件（special_vote, incomplete）或者简单条件，使用优化的数据库查询
            String registrationStatus = (String) criteria.get("registrationStatus");
            if ("special_vote".equals(registrationStatus) || "incomplete".equals(registrationStatus)) {
                // 复杂条件仍使用原有的内存过滤逻辑
                List<EventMember> allMembers = eventMemberRepository.findByEvent(event);
                filteredMembers = allMembers.stream()
                        .filter(member -> {
                            if ("special_vote".equals(registrationStatus)) {
                                return member.getIsSpecialVote() != null && member.getIsSpecialVote();
                            } else if ("incomplete".equals(registrationStatus)) {
                                return (member.getHasRegistered() != null && member.getHasRegistered()) &&
                                        (member.getIsAttending() == null || !member.getIsAttending()) &&
                                        (member.getAbsenceReason() == null || member.getAbsenceReason().trim().isEmpty());
                            }
                            return true;
                        }).collect(Collectors.toList());
            } else {
                // 使用优化的数据库查询
                try {
                    filteredMembers = eventMemberRepository.findByEventAndMemberCriteria(
                            event, hasRegistered, isAttending, hasVoted, checkedIn,
                            regionDesc, genderDesc, siteIndustryDesc, siteSubIndustryDesc,
                            workplaceDesc, employerName, bargainingGroupDesc, employmentStatus,
                            ethnicRegionDesc, jobTitle, department, siteNumber,
                            hasEmail, hasMobile, branchDesc, forumDesc, membershipTypeDesc, occupation
                    );
                    log.info("Database query returned {} members for event {}", filteredMembers.size(), eventId);
                } catch (Exception e) {
                    log.error("Database query failed, falling back to in-memory filtering: {}", e.getMessage());
                    // 如果数据库查询失败，回退到原有逻辑
                    List<EventMember> allMembers = eventMemberRepository.findByEvent(event);
                    filteredMembers = allMembers; // 简化的回退逻辑
                }
            }

            // 对筛选结果进行排序（按注册时间倒序）
            filteredMembers.sort((a, b) -> {
                LocalDateTime timeA = a.getFormSubmissionTime() != null ? a.getFormSubmissionTime() : a.getCreatedAt();
                LocalDateTime timeB = b.getFormSubmissionTime() != null ? b.getFormSubmissionTime() : b.getCreatedAt();
                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;
                return timeB.compareTo(timeA);
            });

//            分页
            int start = page * size;
            int end = Math.min(start + size, filteredMembers.size());
            List<EventMember> pageMembers = filteredMembers.subList(start, end);

            List<Map<String, Object>> memberList = pageMembers.stream().map(member -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", member.getId());
                info.put("membershipNumber", member.getMembershipNumber());
                info.put("name", member.getName());
                info.put("primaryEmail", member.getPrimaryEmail());
                info.put("telephoneMobile", member.getTelephoneMobile());
                info.put("registrationTime", member.getFormSubmissionTime() != null ? member.getFormSubmissionTime() : member.getCreatedAt());
                info.put("hasRegistered", member.getHasRegistered());
                info.put("isAttending", member.getIsAttending());
                info.put("isSpecialVote", member.getIsSpecialVote());
                info.put("absenceReason", member.getAbsenceReason());
                info.put("region", member.getRegionDesc());
                info.put("workplace", member.getWorkplace());
                info.put("employer", member.getEmployer());
                info.put("industrySubDesc", member.getMember() != null ? member.getMember().getSiteIndustryDesc() : null);
                info.put("siteSubIndustryDesc", member.getMember() != null ? member.getMember().getSiteSubIndustryDesc() : null);
                info.put("ageOfMember", member.getMember() != null ? member.getMember().getAgeOfMember() : null);
                info.put("bargainingGroupDesc", member.getMember() != null ? member.getMember().getBargainingGroupDesc() : null);
                info.put("genderDesc", member.getMember() != null ? member.getMember().getGenderDesc() : null);
                info.put("ethnicRegionDesc", member.getMember() != null ? member.getMember().getEthnicRegionDesc() : null);
                info.put("membershipTypeDesc", member.getMember() != null ? member.getMember().getMembershipTypeDesc() : null);
                info.put("branchDesc", member.getMember() != null ? member.getMember().getBranchDesc() : null);
                info.put("forumDesc", member.getMember() != null ? member.getMember().getForumDesc() : null);
                info.put("emailSent", member.getEmailSent());
                info.put("smsSent", member.getSmsSent());
                info.put("hasEmail", member.getHasEmail());
                info.put("hasMobile", member.getHasMobile());
                info.put("payrollNumber", member.getMember() != null ? member.getMember().getPayrollNumber() : null);
                info.put("siteNumber", member.getMember() != null ? member.getMember().getSiteNumber() : null);
                // 签到相关字段
                info.put("token", member.getToken());
                info.put("checkedIn", member.getCheckedIn());
                info.put("checkInTime", member.getCheckInTime());
                info.put("hasVoted", member.getHasVoted());
                info.put("qrCodeEmailSent", member.getQrCodeEmailSent());
                return info;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("members", memberList);
            data.put("totalCount", filteredMembers.size());
            data.put("page", page);
            data.put("size", size);
            data.put("totalPages", (filteredMembers.size() + size - 1) / size);
            data.put("criteria", criteria);
            data.put("eventName", event.getName());

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch members by criteria", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    //    获取所有可用的筛选选项
    @GetMapping("/events/{eventId}/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions(@PathVariable Long eventId) {
        log.info("Fetching filter options for event: {}", eventId);

        try {
            Event event = eventRepository.findById(eventId).orElse(null);
            if (event == null) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Event not found"));
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(event);

            Map<String, Object> options = new HashMap<>();

//            地区选项
            Set<String> regions = allMembers.stream()
                    .map(EventMember::getRegionDesc)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            options.put("regions", regions);

//            行业选项 - 从EventMember直接获取
            Set<String> industries = allMembers.stream()
                    .map(EventMember::getSiteIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("industries", industries);

//            工作场所选项
            Set<String> workplaces = allMembers.stream()
                    .map(EventMember::getWorkplace)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("workplaces", workplaces);

//            雇主选项
            Set<String> employers = allMembers.stream()
                    .map(EventMember::getEmployer)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("employers", employers);

//            子行业选项 - 从EventMember直接获取
            Set<String> subIndustries = allMembers.stream()
                    .map(EventMember::getSiteSubIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("subIndustries", subIndustries);

//            年龄选项 - 从EventMember直接获取
            Set<String> ages = allMembers.stream()
                    .map(EventMember::getAgeOfMember)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("ages", ages);

//            议价组选项 - 从EventMember直接获取
            Set<String> bargainingGroups = allMembers.stream()
                    .map(EventMember::getBargainingGroupDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("bargainingGroups", bargainingGroups);

//            性别选项 - 从EventMember直接获取
            Set<String> genders = allMembers.stream()
                    .map(EventMember::getGenderDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("genders", genders);

//            民族地区选项
            Set<String> ethnicRegions = allMembers.stream()
                    .map(EventMember::getEthnicRegionDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("ethnicRegions", ethnicRegions);

//            会员类型选项 - 从EventMember直接获取
            Set<String> membershipTypes = allMembers.stream()
                    .map(EventMember::getMembershipTypeDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("membershipTypes", membershipTypes);

//            分支选项 - 从EventMember直接获取
            Set<String> branches = allMembers.stream()
                    .map(EventMember::getBranch)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("branches", branches);

//            论坛选项 - 从EventMember直接获取
            Set<String> forums = allMembers.stream()
                    .map(EventMember::getForumDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());
            options.put("forums", forums);

//            注册状态选项
            options.put("registrationStatuses", Arrays.asList(
                    "registered", "not_registered", "attending", "not_attending",
                    "special_vote", "incomplete"
            ));

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", options);
            response.put("eventName", event.getName());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch filter options", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    //    全局版本 - BMM专用，不需要eventId
    @GetMapping("/recent-registrations")
    public ResponseEntity<Map<String, Object>> getGlobalRecentRegistrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "24") int hoursBack) {

        log.info("Fetching global recent registrations, hours back: {}", hoursBack);

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hoursBack);

//            获取所有会员，然后在内存中筛选和排序
            List<Member> allMembers = memberRepository.findAll();

            List<Member> recentRegistrations = allMembers.stream()
                    .filter(m -> {
                        LocalDateTime regTime = m.getCreatedAt();
                        return regTime != null && regTime.isAfter(cutoffTime);
                    })
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getCreatedAt();
                        LocalDateTime timeB = b.getCreatedAt();
                        return timeB.compareTo(timeA);
                    })
                    .skip(page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            List<Map<String, Object>> registrationList = recentRegistrations.stream().map(member -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", member.getId());
                info.put("membershipNumber", member.getMembershipNumber());
                info.put("name", member.getName());
                info.put("primaryEmail", member.getPrimaryEmail());
                info.put("registrationTime", member.getCreatedAt());
                info.put("isAttending", member.getIsAttending());
                info.put("isSpecialVote", member.getIsSpecialVote());
                info.put("absenceReason", member.getAbsenceReason());
                info.put("region", member.getRegionDesc());
                info.put("workplace", member.getWorkplaceDesc());
                info.put("employer", member.getEmployer());
                info.put("industry", member.getSiteIndustryDesc());
                info.put("hasRegistered", member.getHasRegistered());
                info.put("emailSent", member.getEmailSentStatus());
                info.put("smsSent", member.getSmsSentStatus());
                return info;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("registrations", registrationList);
            data.put("totalFound", recentRegistrations.size());
            data.put("timeRange", hoursBack + " hours");
            data.put("cutoffTime", cutoffTime);

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch global recent registrations", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/registration-analytics")
    public ResponseEntity<Map<String, Object>> getGlobalRegistrationAnalytics() {
        log.info("Fetching global registration analytics");

        try {
            List<Member> allMembers = memberRepository.findAll();

            Map<String, Object> analytics = new HashMap<>();

//            基础统计
            long totalInvited = allMembers.size();
            long registered = allMembers.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count();
            long notRegistered = totalInvited - registered;
            long attending = allMembers.stream().filter(m -> m.getIsAttending() != null && m.getIsAttending()).count();
            long notAttending = allMembers.stream().filter(m -> m.getIsAttending() != null && !m.getIsAttending()).count();
            long specialVote = allMembers.stream().filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote()).count();
            long incomplete = allMembers.stream()
                    .filter(m -> m.getHasRegistered() != null && m.getHasRegistered())
                    .filter(m -> m.getIsAttending() != null && !m.getIsAttending())
                    .filter(m -> m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty())
                    .count();

            analytics.put("totalInvited", totalInvited);
            analytics.put("registered", registered);
            analytics.put("notRegistered", notRegistered);
            analytics.put("attending", attending);
            analytics.put("notAttending", notAttending);
            analytics.put("specialVote", specialVote);
            analytics.put("incompleteRegistrations", incomplete);
            analytics.put("registrationRate", totalInvited > 0 ? (registered * 100.0 / totalInvited) : 0);
            analytics.put("attendanceRate", registered > 0 ? (attending * 100.0 / registered) : 0);

//            按地区分析
            Map<String, Map<String, Long>> regionAnalysis = allMembers.stream()
                    .collect(Collectors.groupingBy(
                            m -> m.getRegionDesc() != null ? m.getRegionDesc() : "Unknown",
                            Collectors.groupingBy(
                                    m -> {
                                        if (m.getHasRegistered() == null || !m.getHasRegistered()) return "not_registered";
                                        if (m.getIsAttending() != null && m.getIsAttending()) return "attending";
                                        if (m.getIsAttending() != null && !m.getIsAttending()) {
                                            if (m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty()) {
                                                return "incomplete";
                                            }
                                            return m.getIsSpecialVote() != null && m.getIsSpecialVote() ? "special_vote" : "not_attending";
                                        }
                                        return "unknown";
                                    },
                                    Collectors.counting()
                            )
                    ));
            analytics.put("regionAnalysis", regionAnalysis);

//            按行业分析
            Map<String, Long> industryAnalysis = allMembers.stream()
                    .filter(m -> m.getSiteIndustryDesc() != null && !m.getSiteIndustryDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(
                            Member::getSiteIndustryDesc,
                            Collectors.counting()
                    ));
            analytics.put("industryAnalysis", industryAnalysis);

//            最近24小时注册趋势
            LocalDateTime last24Hours = LocalDateTime.now().minusHours(24);
            List<Member> recent24h = allMembers.stream()
                    .filter(m -> {
                        LocalDateTime regTime = m.getCreatedAt();
                        return regTime != null && regTime.isAfter(last24Hours);
                    })
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getCreatedAt();
                        LocalDateTime timeB = b.getCreatedAt();
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());

            analytics.put("recent24hCount", recent24h.size());
            analytics.put("recent24hRegistrations", recent24h.stream().limit(10).map(m -> {
                Map<String, Object> info = new HashMap<>();
                info.put("name", m.getName());
                info.put("membershipNumber", m.getMembershipNumber());
                info.put("registrationTime", m.getCreatedAt());
                info.put("isAttending", m.getIsAttending());
                return info;
            }).collect(Collectors.toList()));

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", analytics);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch global registration analytics", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/members-by-criteria")
    public ResponseEntity<Map<String, Object>> getGlobalMembersByCriteria(
            @RequestBody Map<String, Object> criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching global members by criteria: {}", criteria);

        try {
            // Get current BMM event and use EventMember data
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                // Return empty result if no BMM event
                Map<String, Object> response = new HashMap<>();
                Map<String, Object> data = new HashMap<>();
                data.put("members", new ArrayList<>());
                data.put("totalCount", 0);
                data.put("currentPage", page);
                data.put("pageSize", size);
                data.put("totalPages", 0);
                response.put("status", "success");
                response.put("data", data);
                return ResponseEntity.ok(response);
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);

            // Apply filtering conditions
            Stream<EventMember> filteredStream = allMembers.stream();

            // Registration status filter
            String registrationStatus = (String) criteria.get("registrationStatus");
            if (registrationStatus != null && !registrationStatus.isEmpty()) {
                filteredStream = filteredStream.filter(m -> {
                    switch (registrationStatus) {
                        case "registered":
                            return m.getHasRegistered() != null && m.getHasRegistered();
                        case "not_registered":
                            return m.getHasRegistered() == null || !m.getHasRegistered();
                        case "attending":
                            return m.getIsAttending() != null && m.getIsAttending();
                        case "not_attending":
                            return m.getIsAttending() != null && !m.getIsAttending();
                        case "special_vote":
                            return m.getIsSpecialVote() != null && m.getIsSpecialVote();
                        case "incomplete":
                            return (m.getHasRegistered() != null && m.getHasRegistered()) &&
                                    (m.getIsAttending() != null && !m.getIsAttending()) &&
                                    (m.getAbsenceReason() == null || m.getAbsenceReason().trim().isEmpty());
                        default:
                            return true;
                    }
                });
            }

            // Region filter
            String region = (String) criteria.get("region");
            if (region != null && !region.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getRegionDesc() != null && m.getRegionDesc().equals(region));
            }

            // Industry filter - supporting multiple field names
            String industry = (String) (criteria.get("industrySubDesc") != null ? criteria.get("industrySubDesc") :
                    criteria.get("industry") != null ? criteria.get("industry") : criteria.get("siteIndustryDesc"));
            if (industry != null && !industry.trim().isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getMember() != null && m.getMember().getSiteIndustryDesc() != null &&
                                m.getMember().getSiteIndustryDesc().contains(industry));
            }

            // Workplace filter
            String workplace = (String) criteria.get("workplace");
            if (workplace != null && !workplace.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getWorkplace() != null && m.getWorkplace().equals(workplace));
            }

            // Employer filter
            String employer = (String) criteria.get("employer");
            if (employer != null && !employer.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getEmployer() != null && m.getEmployer().equals(employer));
            }

            // Sub-industry filter
            String siteSubIndustryDesc = (String) criteria.get("siteSubIndustryDesc");
            if (siteSubIndustryDesc != null && !siteSubIndustryDesc.isEmpty()) {
                filteredStream = filteredStream.filter(m ->
                        m.getMember() != null && m.getMember().getSiteSubIndustryDesc() != null &&
                                m.getMember().getSiteSubIndustryDesc().equals(siteSubIndustryDesc));
            }

            // Contact information filter
            String contactInfo = (String) criteria.get("contactInfo");
            if (contactInfo != null && !contactInfo.isEmpty()) {
                filteredStream = filteredStream.filter(m -> {
                    boolean hasEmail = m.getPrimaryEmail() != null && !m.getPrimaryEmail().trim().isEmpty()
                            && !m.getPrimaryEmail().contains("@temp.etu.nz");
                    boolean hasMobile = m.getTelephoneMobile() != null && !m.getTelephoneMobile().trim().isEmpty();

                    switch (contactInfo) {
                        case "hasBoth":
                            return hasEmail && hasMobile;
                        case "emailOnly":
                            return hasEmail && !hasMobile;
                        case "mobileOnly":
                            return !hasEmail && hasMobile;
                        case "hasNone":
                            return !hasEmail && !hasMobile;
                        default:
                            return true;
                    }
                });
            }

            // Sort by registration time (newest first)
            List<EventMember> filteredMembers = filteredStream
                    .sorted((a, b) -> {
                        LocalDateTime timeA = a.getCreatedAt();
                        LocalDateTime timeB = b.getCreatedAt();
                        if (timeA == null && timeB == null) return 0;
                        if (timeA == null) return 1;
                        if (timeB == null) return -1;
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());

            int totalCount = filteredMembers.size();
            List<EventMember> pagedMembers = filteredMembers.stream()
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());

            List<Map<String, Object>> memberList = pagedMembers.stream().map(member -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", member.getId());
                info.put("membershipNumber", member.getMembershipNumber());
                info.put("name", member.getName());
                info.put("primaryEmail", member.getPrimaryEmail());
                info.put("telephoneMobile", member.getTelephoneMobile());
                info.put("region", member.getRegionDesc());
                info.put("industrySubDesc", member.getMember() != null ? member.getMember().getSiteIndustryDesc() : null);
                info.put("siteSubIndustryDesc", member.getMember() != null ? member.getMember().getSiteSubIndustryDesc() : null);
                info.put("workplace", member.getWorkplace());
                info.put("employer", member.getEmployer());
                info.put("ageOfMember", member.getMember() != null ? member.getMember().getAgeOfMember() : null);
                info.put("bargainingGroupDesc", member.getMember() != null ? member.getMember().getBargainingGroupDesc() : null);
                info.put("genderDesc", member.getMember() != null ? member.getMember().getGenderDesc() : null);
                info.put("ethnicRegionDesc", member.getMember() != null ? member.getMember().getEthnicRegionDesc() : null);
                info.put("membershipTypeDesc", member.getMember() != null ? member.getMember().getMembershipTypeDesc() : null);
                info.put("branchDesc", member.getMember() != null ? member.getMember().getBranchDesc() : null);
                info.put("forumDesc", member.getMember() != null ? member.getMember().getForumDesc() : null);
                info.put("hasRegistered", member.getHasRegistered());
                info.put("isAttending", member.getIsAttending());
                info.put("isSpecialVote", member.getIsSpecialVote());
                info.put("absenceReason", member.getAbsenceReason());
                info.put("registrationTime", member.getFormSubmissionTime() != null ? member.getFormSubmissionTime() : member.getCreatedAt());
                info.put("emailSent", member.getEmailSent());
                info.put("smsSent", member.getSmsSent());
                info.put("hasEmail", member.getHasEmail());
                info.put("hasMobile", member.getHasMobile());
                info.put("verificationCode", member.getVerificationCode());
                info.put("payrollNumber", member.getMember() != null ? member.getMember().getPayrollNumber() : null);
                info.put("siteNumber", member.getMember() != null ? member.getMember().getSiteNumber() : null);

                // Calculate registration status
                String status = "not_registered";
                if (member.getHasRegistered() != null && member.getHasRegistered()) {
                    if (member.getIsAttending() != null && member.getIsAttending()) {
                        status = "attending";
                    } else if (member.getIsAttending() != null && !member.getIsAttending()) {
                        if (member.getIsSpecialVote() != null && member.getIsSpecialVote()) {
                            status = "special_vote";
                        } else if (member.getAbsenceReason() == null || member.getAbsenceReason().trim().isEmpty()) {
                            status = "incomplete";
                        } else {
                            status = "not_attending";
                        }
                    }
                }
                info.put("registrationStatus", status);

                return info;
            }).collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            data.put("members", memberList);
            data.put("totalCount", totalCount);
            data.put("currentPage", page);
            data.put("pageSize", size);
            data.put("totalPages", (int) Math.ceil((double) totalCount / size));

            response.put("status", "success");
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch global members by criteria", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getGlobalFilterOptions() {
        log.info("Fetching global filter options");

        try {
            // Get current BMM event and use EventMember data
            List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
            Event currentBmmEvent = bmmEvents.stream()
                    .max(Comparator.comparing(Event::getCreatedAt))
                    .orElse(null);

            if (currentBmmEvent == null) {
                // Return empty options if no BMM event
                Map<String, Object> emptyOptions = new HashMap<>();
                emptyOptions.put("regions", new ArrayList<>());
                emptyOptions.put("industries", new ArrayList<>());
                emptyOptions.put("subIndustries", new ArrayList<>());
                emptyOptions.put("workplaces", new ArrayList<>());
                emptyOptions.put("employers", new ArrayList<>());

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("data", emptyOptions);
                return ResponseEntity.ok(response);
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(currentBmmEvent);
            log.info("Found {} total members for filter options", allMembers.size());

            // Extract unique filter options
            Set<String> regions = allMembers.stream()
                    .map(EventMember::getRegionDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            // Get industries directly from EventMember fields
            Set<String> industries = allMembers.stream()
                    .map(EventMember::getSiteIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> subIndustries = allMembers.stream()
                    .map(EventMember::getSiteSubIndustryDesc)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> workplaces = allMembers.stream()
                    .map(EventMember::getWorkplace)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            Set<String> employers = allMembers.stream()
                    .map(EventMember::getEmployer)
                    .filter(Objects::nonNull)
                    .filter(s -> !s.trim().isEmpty())
                    .collect(Collectors.toSet());

            // Note: employerName is now stored directly in EventMember.employer field

            log.info("Filter options extracted - Regions: {}, Industries: {}, SubIndustries: {}, Workplaces: {}, Employers: {}",
                    regions.size(), industries.size(), subIndustries.size(), workplaces.size(), employers.size());

            // Log sample data for debugging
            if (!industries.isEmpty()) {
                log.info("Sample industries: {}", industries.stream().limit(5).collect(Collectors.toList()));
            }
            if (!regions.isEmpty()) {
                log.info("Sample regions: {}", regions.stream().limit(5).collect(Collectors.toList()));
            }

            Map<String, Object> options = new HashMap<>();
            options.put("regions", regions.stream().sorted().collect(Collectors.toList()));
            options.put("industries", industries.stream().sorted().collect(Collectors.toList()));
            options.put("subIndustries", subIndustries.stream().sorted().collect(Collectors.toList()));
            options.put("workplaces", workplaces.stream().sorted().collect(Collectors.toList()));
            options.put("employers", employers.stream().sorted().collect(Collectors.toList()));

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", options);

            log.info("Returning filter options with {} total categories", options.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to fetch global filter options", e);
            return ResponseEntity.internalServerError().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/event-members")
    public ResponseEntity<ApiResponse<Page<EventMember>>> getEventMembers(
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        try {
            Pageable pageable = PageRequest.of(page, size,
                    Sort.by(Sort.Direction.fromString(sortDir), sortBy));

            List<EventMember> allMembers;
            if (eventId != null) {
                Event event = eventRepository.findById(eventId).orElse(null);
                if (event == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
                }
                allMembers = eventMemberRepository.findByEvent(event);
            } else {
                allMembers = eventMemberRepository.findAll();
            }

            // 手动分页
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), allMembers.size());
            List<EventMember> pageContent = allMembers.subList(start, end);
            Page<EventMember> members = new PageImpl<>(pageContent, pageable, allMembers.size());

            return ResponseEntity.ok(ApiResponse.success("Event members retrieved successfully", members));
        } catch (Exception e) {
            log.error("Failed to retrieve event members: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve event members"));
        }
    }

    @GetMapping("/members/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberStats(
            @RequestParam(required = false) Long eventId) {
        try {
            Map<String, Object> stats = new HashMap<>();

            List<EventMember> members;
            if (eventId != null) {
                Event event = eventRepository.findById(eventId).orElse(null);
                if (event == null) {
                    return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
                }
                members = eventMemberRepository.findByEvent(event);
            } else {
                // Get current BMM event by default
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                Event currentBmmEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);

                if (currentBmmEvent != null) {
                    members = eventMemberRepository.findByEvent(currentBmmEvent);
                } else {
                    members = new ArrayList<>();
                }
            }

            // 计算统计数据
            long totalMembers = members.size();
            long registeredMembers = members.stream().filter(m -> m.getHasRegistered() != null && m.getHasRegistered()).count();
            long attendingMembers = members.stream().filter(m -> m.getIsAttending() != null && m.getIsAttending()).count();
            long specialVoteMembers = members.stream().filter(m -> m.getIsSpecialVote() != null && m.getIsSpecialVote()).count();
            long hasEmailMembers = members.stream()
                    .filter(m -> m.getPrimaryEmail() != null &&
                            !m.getPrimaryEmail().trim().isEmpty() &&
                            !m.getPrimaryEmail().contains("@temp-email.etu.nz"))
                    .count();
            long hasMobileMembers = members.stream()
                    .filter(m -> m.getTelephoneMobile() != null &&
                            !m.getTelephoneMobile().trim().isEmpty())
                    .count();
            long smsOnlyMembers = members.stream()
                    .filter(m -> (m.getPrimaryEmail() == null ||
                            m.getPrimaryEmail().trim().isEmpty() ||
                            m.getPrimaryEmail().contains("@temp-email.etu.nz")) &&
                            m.getTelephoneMobile() != null &&
                            !m.getTelephoneMobile().trim().isEmpty())
                    .count();

            stats.put("totalMembers", totalMembers);
            stats.put("registeredMembers", registeredMembers);
            stats.put("attendingMembers", attendingMembers);
            stats.put("specialVoteMembers", specialVoteMembers);
            stats.put("hasEmailMembers", hasEmailMembers);
            stats.put("hasMobileMembers", hasMobileMembers);
            stats.put("smsOnlyMembers", smsOnlyMembers);

            return ResponseEntity.ok(ApiResponse.success("Member statistics retrieved successfully", stats));
        } catch (Exception e) {
            log.error("Failed to retrieve member statistics: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve member statistics"));
        }
    }

    @GetMapping("/members")
    public ResponseEntity<Map<String, Object>> getRegistrationMembers(
            @RequestParam(required = false) Long eventId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.info("Fetching registration members - eventId: {}, page: {}, size: {}", eventId, page, size);

        try {
            Event targetEvent = null;

            if (eventId != null) {
                targetEvent = eventRepository.findById(eventId).orElse(null);
            } else {
                // Get current BMM event by default
                List<Event> bmmEvents = eventRepository.findByEventType(Event.EventType.BMM_VOTING);
                targetEvent = bmmEvents.stream()
                        .max(Comparator.comparing(Event::getCreatedAt))
                        .orElse(null);
            }

            if (targetEvent == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "No event found");
                return ResponseEntity.badRequest().body(response);
            }

            List<EventMember> allMembers = eventMemberRepository.findByEvent(targetEvent);

            // Apply pagination
            int start = page * size;
            int end = Math.min(start + size, allMembers.size());
            List<EventMember> pageMembers = allMembers.subList(start, end);

            // Convert to response format
            List<Map<String, Object>> memberData = pageMembers.stream().map(member -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", member.getId());
                data.put("token", member.getToken());
                data.put("membershipNumber", member.getMembershipNumber());
                data.put("name", member.getName());
                data.put("primaryEmail", member.getPrimaryEmail());
                data.put("regionDesc", member.getRegionDesc());
                data.put("hasRegistered", member.getHasRegistered());
                data.put("isAttending", member.getIsAttending());
                data.put("bmmRegistrationStage", member.getBmmRegistrationStage());
                return data;
            }).collect(Collectors.toList());

            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("content", memberData);
            pageInfo.put("totalElements", allMembers.size());
            pageInfo.put("totalPages", (int) Math.ceil((double) allMembers.size() / size));
            pageInfo.put("size", size);
            pageInfo.put("number", page);
            pageInfo.put("numberOfElements", pageMembers.size());
            pageInfo.put("first", page == 0);
            pageInfo.put("last", end >= allMembers.size());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", pageInfo);

            log.info("Retrieved {} members for event {} (page {}/{})",
                    pageMembers.size(), targetEvent.getName(), page, pageInfo.get("totalPages"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to retrieve registration members: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to retrieve members: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}