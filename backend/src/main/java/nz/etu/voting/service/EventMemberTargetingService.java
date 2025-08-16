package nz.etu.voting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 事件成员定向服务 - 基于多表联合查询进行复杂过滤
 * 解决邮件/短信发送中需要同时利用Member表和EventMember表数据进行过滤的问题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventMemberTargetingService {

    private final EventMemberRepository eventMemberRepository;
    private final EventRepository eventRepository;

    /**
     * 根据复杂条件过滤事件成员 - 多表联合查询
     */
    public List<EventMember> getFilteredEventMembers(Long eventId, Map<String, Object> criteria) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members for event: {} with criteria: {}", event.getName(), criteria);

        // CRITICAL: 完整的字段映射 - 支持前端发送的所有字段名
        // EventMember状态字段
        Boolean hasRegistered = getBooleanFromCriteria(criteria, "hasRegistered");
        Boolean isAttending = getBooleanFromCriteria(criteria, "isAttending");
        Boolean hasVoted = getBooleanFromCriteria(criteria, "hasVoted");
        Boolean checkedIn = getBooleanFromCriteria(criteria, "checkedIn");

        // Member表字段 - 支持多种字段名映射
        String regionDesc = getStringFromCriteria(criteria, "regionDesc");
        if (regionDesc == null) regionDesc = getStringFromCriteria(criteria, "region");

        // CRITICAL: Map frontend region values to database values
        if (regionDesc != null) {
            switch (regionDesc.toLowerCase()) {
                case "northern":
                    regionDesc = "Northern";
                    break;
                case "central":
                    regionDesc = "Central";
                    break;
                case "southern":
                    regionDesc = "Southern";
                    break;
                // Keep original if already full name
                case "northern region":
                case "central region":
                case "southern region":
                    break;
                default:
                    log.debug("Unknown region value: {}, keeping original", regionDesc);
                    break;
            }
        }

        String genderDesc = getStringFromCriteria(criteria, "genderDesc");
        if (genderDesc == null) genderDesc = getStringFromCriteria(criteria, "gender");

        // 行业字段映射
        String siteSubIndustryDesc = getStringFromCriteria(criteria, "siteSubIndustryDesc");
        if (siteSubIndustryDesc == null) siteSubIndustryDesc = getStringFromCriteria(criteria, "subIndustry");

        String siteIndustryDesc = getStringFromCriteria(criteria, "siteIndustryDesc");
        if (siteIndustryDesc == null) siteIndustryDesc = getStringFromCriteria(criteria, "industry");

        String workplaceDesc = getStringFromCriteria(criteria, "workplaceDesc");
        if (workplaceDesc == null) workplaceDesc = getStringFromCriteria(criteria, "workplace");

        String employerName = getStringFromCriteria(criteria, "employerName");
        if (employerName == null) employerName = getStringFromCriteria(criteria, "employer");

        String bargainingGroupDesc = getStringFromCriteria(criteria, "bargainingGroupDesc");
        if (bargainingGroupDesc == null) bargainingGroupDesc = getStringFromCriteria(criteria, "bargainingGroup");

        String branchDesc = getStringFromCriteria(criteria, "branchDesc");
        if (branchDesc == null) branchDesc = getStringFromCriteria(criteria, "branch");

        String forumDesc = getStringFromCriteria(criteria, "forumDesc");
        if (forumDesc == null) forumDesc = getStringFromCriteria(criteria, "forum");

        String membershipTypeDesc = getStringFromCriteria(criteria, "membershipTypeDesc");
        if (membershipTypeDesc == null) membershipTypeDesc = getStringFromCriteria(criteria, "membershipType");

        String ethnicRegionDesc = getStringFromCriteria(criteria, "ethnicRegionDesc");
        if (ethnicRegionDesc == null) ethnicRegionDesc = getStringFromCriteria(criteria, "ethnicRegion");

        String occupation = getStringFromCriteria(criteria, "occupation");
        String employmentStatus = getStringFromCriteria(criteria, "employmentStatus");
        String jobTitle = getStringFromCriteria(criteria, "jobTitle");
        String department = getStringFromCriteria(criteria, "department");
        String siteNumber = getStringFromCriteria(criteria, "siteNumber");

        Boolean hasEmail = getBooleanFromCriteria(criteria, "hasEmail");
        Boolean hasMobile = getBooleanFromCriteria(criteria, "hasMobile");

        // 搜索字段
        String searchName = getStringFromCriteria(criteria, "searchName");
        String searchEmail = getStringFromCriteria(criteria, "searchEmail");
        String searchMembershipNumber = getStringFromCriteria(criteria, "searchMembershipNumber");

        // CRITICAL: 处理BMM注册阶段
        String bmmRegistrationStage = getStringFromCriteria(criteria, "bmmRegistrationStage");

        // BMM specific filters
        String bmmStage = getStringFromCriteria(criteria, "bmmStage");
        String preferenceStatus = getStringFromCriteria(criteria, "preferenceStatus");
        String attendanceIntention = getStringFromCriteria(criteria, "attendanceIntention");
        String venueAssignment = getStringFromCriteria(criteria, "venueAssignment");

        // CRITICAL: 处理特殊的registrationStatus映射
        String registrationStatus = getStringFromCriteria(criteria, "registrationStatus");
        if (registrationStatus != null && !registrationStatus.isEmpty()) {
            switch (registrationStatus) {
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
                case "special_vote":
                    // 这个需要在查询结果中过滤，因为数据库查询不支持
                    break;
            }
        }

        // CRITICAL: 处理BMM注册阶段映射
        if (bmmRegistrationStage != null && !bmmRegistrationStage.isEmpty()) {
            switch (bmmRegistrationStage) {
                case "not_started":
                    hasRegistered = false;
                    break;
                case "stage1_completed":
                    hasRegistered = true;
                    // 可以在后处理中进一步过滤
                    break;
                case "stage2_pending":
                    hasRegistered = true;
                    isAttending = null; // 还未决定是否出席
                    break;
                case "stage2_confirmed":
                    hasRegistered = true;
                    isAttending = true;
                    break;
            }
        }

        log.info("🔍 Applied field mapping - Registration: {}, Attending: {}, Region: {}, SubIndustry: {}, BMM Stage: {}",
                hasRegistered, isAttending, regionDesc, siteSubIndustryDesc, bmmRegistrationStage);
        log.info("📱 Contact filters - hasEmail: {}, hasMobile: {}", hasEmail, hasMobile);

        // CRITICAL: 使用多表联合查询
        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndMemberCriteria(
                event, hasRegistered, isAttending, hasVoted, checkedIn,
                regionDesc, genderDesc, siteIndustryDesc, siteSubIndustryDesc, workplaceDesc,
                employerName, bargainingGroupDesc, employmentStatus, ethnicRegionDesc,
                jobTitle, department, siteNumber, hasEmail, hasMobile,
                branchDesc, forumDesc, membershipTypeDesc, occupation
        );

        // CRITICAL: 后处理：处理数据库查询无法直接支持的复杂条件
        if (registrationStatus != null && registrationStatus.equals("special_vote")) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> em.getSpecialVoteRequested() != null && em.getSpecialVoteRequested())
                    .collect(Collectors.toList());
        }

        // CRITICAL: 后处理BMM注册阶段的精确过滤
        if (bmmRegistrationStage != null && !bmmRegistrationStage.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String stage = em.getBmmRegistrationStage();
                        if (stage == null) stage = "not_started";
                        return stage.equals(bmmRegistrationStage);
                    })
                    .collect(Collectors.toList());
        }

        // Filter by attendance confirmation status
        String attendanceConfirmed = getStringFromCriteria(criteria, "attendanceConfirmed");
        if (attendanceConfirmed != null && !attendanceConfirmed.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String stage = em.getBmmRegistrationStage();
                        switch (attendanceConfirmed) {
                            case "confirmed":
                                return "ATTENDANCE_CONFIRMED".equals(stage);
                            case "declined":
                                return "ATTENDANCE_DECLINED".equals(stage);
                            case "no_response":
                                // No response means not confirmed and not declined
                                return stage == null || 
                                       "PENDING".equals(stage) || 
                                       "INVITED".equals(stage) || 
                                       "PREFERENCE_SUBMITTED".equals(stage) ||
                                       "VENUE_ASSIGNED".equals(stage);
                            default:
                                return true; // Show all if unknown filter value
                        }
                    })
                    .collect(Collectors.toList());
            log.info("Applied attendance confirmation filter: {} - remaining members: {}", attendanceConfirmed, filteredMembers.size());
        }

        // Handle forum inclusion filter (only include specific forums)
        String includeForums = getStringFromCriteria(criteria, "includeForums");
        if (includeForums != null && !includeForums.isEmpty()) {
            String[] forumsToInclude = includeForums.split(",");
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String forum = em.getForumDesc();
                        if (forum == null) return false; // Exclude if no forum when filtering for specific forums
                        for (String includeForum : forumsToInclude) {
                            if (forum.trim().equalsIgnoreCase(includeForum.trim())) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            log.info("Applied forum inclusion filter: {} - remaining members: {}", includeForums, filteredMembers.size());
        }

        // Handle forum exclusion filter
        String excludeForums = getStringFromCriteria(criteria, "excludeForums");
        if (excludeForums != null && !excludeForums.isEmpty()) {
            String[] forumsToExclude = excludeForums.split(",");
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String forum = em.getForumDesc();
                        if (forum == null) return true; // Don't exclude if no forum
                        for (String excludeForum : forumsToExclude) {
                            if (forum.trim().equalsIgnoreCase(excludeForum.trim())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            log.info("Applied forum exclusion filter: {} - remaining members: {}", excludeForums, filteredMembers.size());
        }

        // Handle specific time preference filter
        String specificTimePreference = getStringFromCriteria(criteria, "specificTimePreference");
        if (specificTimePreference != null && !specificTimePreference.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String prefs = em.getPreferredTimesJson();
                        
                        if ("no_preference".equals(specificTimePreference)) {
                            return prefs == null || prefs.isEmpty() || "[]".equals(prefs);
                        }
                        
                        if (prefs == null || prefs.isEmpty()) return false;
                        
                        // Handle combination filters
                        if (specificTimePreference.contains(",")) {
                            String[] requiredPrefs = specificTimePreference.split(",");
                            for (String reqPref : requiredPrefs) {
                                if (!prefs.contains(reqPref.trim())) {
                                    return false;
                                }
                            }
                            return true;
                        }
                        
                        // Handle single preference filter
                        return prefs.contains(specificTimePreference);
                    })
                    .collect(Collectors.toList());
            log.info("Applied time preference filter: {} - remaining members: {}", specificTimePreference, filteredMembers.size());
        }

        // Handle new BMM filters
        // 1. BMM Stage filter
        if (bmmStage != null && !bmmStage.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String stage = em.getBmmStage();
                        if (stage == null) stage = "INVITED";
                        return stage.equals(bmmStage);
                    })
                    .collect(Collectors.toList());
        }

        // 2. Preference Status filter
        if (preferenceStatus != null && !preferenceStatus.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        boolean hasPreferences = em.getPreferredVenuesJson() != null &&
                                !em.getPreferredVenuesJson().isEmpty();
                        if (preferenceStatus.equals("submitted")) {
                            return hasPreferences;
                        } else if (preferenceStatus.equals("submitted_attending")) {
                            // Filter for members who submitted preferences AND indicated they plan to attend
                            return hasPreferences &&
                                    em.getPreferredAttending() != null &&
                                    em.getPreferredAttending();
                        } else if (preferenceStatus.equals("not_submitted")) {
                            return !hasPreferences;
                        } else if (preferenceStatus.equals("exclude_not_attending")) {
                            // Exclude members who explicitly said they're not attending
                            // Include: 1) Those who said yes, 2) Those who didn't submit preference at all
                            Boolean preferredAttending = em.getPreferredAttending();
                            return preferredAttending == null || preferredAttending == true;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        // 3. Attendance Intention filter
        if (attendanceIntention != null && !attendanceIntention.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        Boolean preferredAttending = em.getPreferredAttending();
                        switch (attendanceIntention) {
                            case "intend_yes":
                                return preferredAttending != null && preferredAttending;
                            case "intend_no":
                                return preferredAttending != null && !preferredAttending;
                            case "not_specified":
                                return preferredAttending == null;
                            default:
                                return true;
                        }
                    })
                    .collect(Collectors.toList());
        }

        // 4. Venue Assignment filter
        if (venueAssignment != null && !venueAssignment.isEmpty()) {
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        boolean hasVenue = em.getAssignedVenueFinal() != null &&
                                !em.getAssignedVenueFinal().isEmpty();
                        if (venueAssignment.equals("assigned")) {
                            return hasVenue;
                        } else if (venueAssignment.equals("not_assigned")) {
                            return !hasVenue;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        // 如果指定了主行业但没有子行业，使用行业关键词搜索
        if (siteIndustryDesc != null && siteSubIndustryDesc == null) {
            List<EventMember> industryMatches = eventMemberRepository.findByEventAndIndustryKeyword(event, siteIndustryDesc);
            // 取交集
            filteredMembers = filteredMembers.stream()
                    .filter(industryMatches::contains)
                    .collect(Collectors.toList());
        }

        log.info("Found {} members matching criteria for event: {}", filteredMembers.size(), event.getName());

        // CRITICAL: Filter based on actual primary_email and telephone_mobile values
        if (Boolean.FALSE.equals(hasEmail) && Boolean.TRUE.equals(hasMobile)) {
            // SMS-only filter: has mobile but no valid email
            log.info("🔍 Applying SMS-only filter based on actual field values");
            long beforeCount = filteredMembers.size();

            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String email = em.getPrimaryEmail();
                        String mobile = em.getTelephoneMobile();

                        // Has valid mobile
                        boolean hasValidMobile = mobile != null && !mobile.trim().isEmpty() && mobile.trim().length() >= 8;

                        // No valid email
                        boolean hasValidEmail = email != null && !email.trim().isEmpty() && !email.contains("@temp-email.etu.nz");

                        return hasValidMobile && !hasValidEmail;
                    })
                    .collect(Collectors.toList());

            log.info("📱 SMS-only filter applied: {} → {} members (removed {} with valid emails)",
                    beforeCount, filteredMembers.size(), beforeCount - filteredMembers.size());
        }

        // 搜索过滤 - 基于名字、邮箱、会员号的模糊匹配
        if (searchName != null && !searchName.trim().isEmpty()) {
            String searchNameLower = searchName.trim().toLowerCase();
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String name = em.getName();
                        return name != null && name.toLowerCase().contains(searchNameLower);
                    })
                    .collect(Collectors.toList());
            log.info("🔍 Name search filter '{}' applied: {} members found", searchName, filteredMembers.size());
        }

        if (searchEmail != null && !searchEmail.trim().isEmpty()) {
            String searchEmailLower = searchEmail.trim().toLowerCase();
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String email = em.getPrimaryEmail();
                        return email != null && email.toLowerCase().contains(searchEmailLower);
                    })
                    .collect(Collectors.toList());
            log.info("🔍 Email search filter '{}' applied: {} members found", searchEmail, filteredMembers.size());
        }

        if (searchMembershipNumber != null && !searchMembershipNumber.trim().isEmpty()) {
            String searchMembershipLower = searchMembershipNumber.trim().toLowerCase();
            filteredMembers = filteredMembers.stream()
                    .filter(em -> {
                        String membershipNumber = em.getMembershipNumber();
                        return membershipNumber != null && membershipNumber.toLowerCase().contains(searchMembershipLower);
                    })
                    .collect(Collectors.toList());
            log.info("🔍 Membership number search filter '{}' applied: {} members found", searchMembershipNumber, filteredMembers.size());
        }

        // Log contact method statistics based on actual values
        if (hasEmail != null || hasMobile != null) {
            long smsOnlyCount = filteredMembers.stream()
                    .filter(em -> {
                        String email = em.getPrimaryEmail();
                        String mobile = em.getTelephoneMobile();
                        boolean hasValidMobile = mobile != null && !mobile.trim().isEmpty() && mobile.trim().length() >= 8;
                        boolean hasValidEmail = email != null && !email.trim().isEmpty() && !email.contains("@temp-email.etu.nz");
                        return hasValidMobile && !hasValidEmail;
                    })
                    .count();
            long emailOnlyCount = filteredMembers.stream()
                    .filter(em -> {
                        String email = em.getPrimaryEmail();
                        String mobile = em.getTelephoneMobile();
                        boolean hasValidMobile = mobile != null && !mobile.trim().isEmpty() && mobile.trim().length() >= 8;
                        boolean hasValidEmail = email != null && !email.trim().isEmpty() && !email.contains("@temp-email.etu.nz");
                        return hasValidEmail && !hasValidMobile;
                    })
                    .count();
            long bothCount = filteredMembers.stream()
                    .filter(em -> {
                        String email = em.getPrimaryEmail();
                        String mobile = em.getTelephoneMobile();
                        boolean hasValidMobile = mobile != null && !mobile.trim().isEmpty() && mobile.trim().length() >= 8;
                        boolean hasValidEmail = email != null && !email.trim().isEmpty() && !email.contains("@temp-email.etu.nz");
                        return hasValidEmail && hasValidMobile;
                    })
                    .count();

            log.info("📊 Contact method breakdown (based on actual values) - SMS only: {}, Email only: {}, Both: {}",
                    smsOnlyCount, emailOnlyCount, bothCount);
        }

        return filteredMembers;
    }

    /**
     * 按行业关键词过滤成员
     */
    public List<EventMember> getEventMembersByIndustry(Long eventId, String industryKeyword) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by industry keyword: {} for event: {}", industryKeyword, event.getName());

        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndIndustryKeyword(event, industryKeyword);
        log.info("Found {} members in industry '{}' for event: {}", filteredMembers.size(), industryKeyword, event.getName());
        return filteredMembers;
    }

    /**
     * 按年龄范围过滤成员
     */
    public List<EventMember> getEventMembersByAgeRange(Long eventId, Double minAge, Double maxAge) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by age range: {}-{} for event: {}", minAge, maxAge, event.getName());

        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndAgeRange(event, minAge, maxAge);
        log.info("Found {} members in age range {}-{} for event: {}", filteredMembers.size(), minAge, maxAge, event.getName());
        return filteredMembers;
    }

    /**
     * 多选条件组合查询
     */
    public List<EventMember> getEventMembersByMultipleCriteria(Long eventId, List<String> industries,
                                                               List<String> regions, List<String> genders,
                                                               Boolean hasRegistered, Boolean isAttending) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by multiple criteria for event: {}", event.getName());
        log.debug("Industries: {}, Regions: {}, Genders: {}, Registered: {}, Attending: {}",
                industries, regions, genders, hasRegistered, isAttending);

        // 使用优化后的方法，传入null作为industryKeyword参数
        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndMultipleCriteria(
                event, industries, regions, genders, hasRegistered, isAttending, null);
        log.info("Found {} members matching multiple criteria for event: {}", filteredMembers.size(), event.getName());
        return filteredMembers;
    }

    /**
     * 根据通讯方式获取目标成员（用于邮件/短信发送）
     */
    public List<EventMember> getEventMembersForCommunication(Long eventId, String communicationType) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Getting event members for communication type: {} for event: {}", communicationType, event.getName());

        List<EventMember> targetMembers = eventMemberRepository.findByEventAndCommunicationType(event, communicationType);
        log.info("Found {} members for {} communication for event: {}", targetMembers.size(), communicationType, event.getName());
        return targetMembers;
    }

    /**
     * 获取过滤预览数据 - 不实际发送，只返回匹配的成员数据用于预览
     */
    public Map<String, Object> getFilterPreview(Long eventId, Map<String, Object> criteria) {
        List<EventMember> filteredMembers = getFilteredEventMembers(eventId, criteria);

        Map<String, Object> preview = new HashMap<>();
        preview.put("totalCount", filteredMembers.size());
        preview.put("emailableCount", filteredMembers.stream()
                .mapToLong(em -> em.getHasEmail() ? 1 : 0).sum());
        preview.put("smsableCount", filteredMembers.stream()
                .mapToLong(em -> em.getHasMobile() ? 1 : 0).sum());

        // 按地区分组统计
        Map<String, Long> regionBreakdown = filteredMembers.stream()
                .filter(em -> em.getRegionDesc() != null)
                .collect(Collectors.groupingBy(
                        em -> em.getRegionDesc(),
                        Collectors.counting()
                ));
        preview.put("regionBreakdown", regionBreakdown);

        // 按行业分组统计
        Map<String, Long> industryBreakdown = filteredMembers.stream()
                .filter(em -> em.getSiteSubIndustryDesc() != null)
                .collect(Collectors.groupingBy(
                        em -> em.getSiteSubIndustryDesc(),
                        Collectors.counting()
                ));
        preview.put("industryBreakdown", industryBreakdown);

        // 按注册状态分组统计
        Map<String, Long> registrationBreakdown = filteredMembers.stream()
                .collect(Collectors.groupingBy(
                        em -> em.getHasRegistered() ? "Registered" : "Not Registered",
                        Collectors.counting()
                ));
        preview.put("registrationBreakdown", registrationBreakdown);

        return preview;
    }

    /**
     * 按工作状态和雇佣信息过滤成员
     */
    public List<EventMember> getEventMembersByEmploymentDetails(Long eventId, String employmentStatus,
                                                                String payrollNumber, String siteCode,
                                                                String departmentKeyword, String jobTitleKeyword) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by employment details for event: {}", event.getName());
        log.debug("Employment filters - Status: {}, Payroll: {}, Site: {}, Dept: {}, Job: {}",
                employmentStatus, payrollNumber, siteCode, departmentKeyword, jobTitleKeyword);

        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndEmploymentDetails(
                event, employmentStatus, payrollNumber, siteCode, departmentKeyword, jobTitleKeyword);
        log.info("Found {} members matching employment criteria for event: {}", filteredMembers.size(), event.getName());
        return filteredMembers;
    }

    /**
     * 按地址信息过滤成员（用于区域性会议）
     */
    public List<EventMember> getEventMembersByLocationDetails(Long eventId, String regionDesc,
                                                              String cityKeyword, String postcode) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by location details for event: {}", event.getName());
        log.debug("Location filters - Region: {}, City: {}, Postcode: {}", regionDesc, cityKeyword, postcode);

        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndLocationDetails(
                event, regionDesc, cityKeyword, postcode);
        log.info("Found {} members matching location criteria for event: {}", filteredMembers.size(), event.getName());
        return filteredMembers;
    }

    /**
     * 获取统计数据 - 多表联合统计
     */
    public Map<String, Object> getEventMemberStatistics(Long eventId, String regionDesc,
                                                        Boolean hasRegistered, Boolean isAttending,
                                                        Boolean hasEmail, Boolean hasMobile) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new HashMap<>();
        }

        Event event = eventOpt.get();
        log.info("Getting statistics for event: {} with criteria filters", event.getName());

        Long count = eventMemberRepository.countByEventAndMemberCriteria(
                event, regionDesc, hasRegistered, isAttending, hasEmail, hasMobile);

        Map<String, Object> stats = new HashMap<>();
        stats.put("filteredCount", count);
        stats.put("eventId", eventId);
        stats.put("eventName", event.getName());
        stats.put("filters", Map.of(
                "regionDesc", regionDesc,
                "hasRegistered", hasRegistered,
                "isAttending", isAttending,
                "hasEmail", hasEmail,
                "hasMobile", hasMobile
        ));

        log.info("Statistics generated: {} members matching criteria for event: {}", count, event.getName());
        return stats;
    }

    /**
     * 获取最新活跃的事件成员
     */
    public List<EventMember> getLatestActiveEventMembers(Long eventId, Integer limit) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Getting latest active members for event: {}", event.getName());

        List<EventMember> activeMembers = eventMemberRepository.findByEventOrderByLastActivityDesc(event);

        // 如果指定了限制数量，则截取前N个
        if (limit != null && limit > 0 && activeMembers.size() > limit) {
            activeMembers = activeMembers.subList(0, limit);
        }

        log.info("Found {} latest active members for event: {}", activeMembers.size(), event.getName());
        return activeMembers;
    }

    /**
     * 获取最新注册的成员
     */
    public List<EventMember> getLatestRegisteredMembers(Long eventId, Integer limit) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Getting latest registered members for event: {}", event.getName());

        List<EventMember> registeredMembers = eventMemberRepository.findRegisteredByEventOrderByRegistrationDesc(event);

        // 如果指定了限制数量，则截取前N个
        if (limit != null && limit > 0 && registeredMembers.size() > limit) {
            registeredMembers = registeredMembers.subList(0, limit);
        }

        log.info("Found {} latest registered members for event: {}", registeredMembers.size(), event.getName());
        return registeredMembers;
    }

    /**
     * 多选条件组合查询 - 使用优化后的方法
     */
    public List<EventMember> getEventMembersByMultipleCriteriaOptimized(Long eventId, List<String> industries,
                                                                        List<String> regions, List<String> genders,
                                                                        Boolean hasRegistered, Boolean isAttending,
                                                                        String industryKeyword) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new ArrayList<>();
        }

        Event event = eventOpt.get();
        log.info("Filtering event members by optimized multiple criteria for event: {}", event.getName());
        log.debug("Optimized filters - Industries: {}, Regions: {}, Genders: {}, Registered: {}, Attending: {}, Keyword: {}",
                industries, regions, genders, hasRegistered, isAttending, industryKeyword);

        List<EventMember> filteredMembers = eventMemberRepository.findByEventAndMultipleCriteria(
                event, industries, regions, genders, hasRegistered, isAttending, industryKeyword);
        log.info("Found {} members matching optimized multiple criteria for event: {}", filteredMembers.size(), event.getName());
        return filteredMembers;
    }

    /**
     * 获取综合性的事件成员报告
     */
    public Map<String, Object> getComprehensiveEventReport(Long eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (!eventOpt.isPresent()) {
            log.warn("Event with ID {} not found", eventId);
            return new HashMap<>();
        }

        Event event = eventOpt.get();
        log.info("Generating comprehensive report for event: {}", event.getName());

        Map<String, Object> report = new HashMap<>();

        // 基础统计
        Long totalMembers = eventMemberRepository.countByEvent(event);
        Long registeredCount = eventMemberRepository.countRegisteredByEvent(event);
        Long attendingCount = eventMemberRepository.countAttendingByEvent(event);
        Long checkedInCount = eventMemberRepository.countCheckedInByEvent(event);

        // 通讯统计
        Long emailableCount = (long) getEventMembersForCommunication(eventId, "EMAIL").size();
        Long smsableCount = (long) getEventMembersForCommunication(eventId, "SMS").size();

        // 最新活动
        List<EventMember> latestRegistered = getLatestRegisteredMembers(eventId, 10);
        List<EventMember> latestActive = getLatestActiveEventMembers(eventId, 10);

        report.put("eventId", eventId);
        report.put("eventName", event.getName());
        report.put("totalMembers", totalMembers);
        report.put("registeredCount", registeredCount);
        report.put("attendingCount", attendingCount);
        report.put("checkedInCount", checkedInCount);
        report.put("emailableCount", emailableCount);
        report.put("smsableCount", smsableCount);
        report.put("registrationRate", totalMembers > 0 ? (double) registeredCount / totalMembers : 0.0);
        report.put("attendanceRate", registeredCount > 0 ? (double) attendingCount / registeredCount : 0.0);
        report.put("checkinRate", attendingCount > 0 ? (double) checkedInCount / attendingCount : 0.0);
        report.put("latestRegisteredMembers", latestRegistered.stream().map(em -> Map.of(
                "memberName", em.getName(),
                "membershipNumber", em.getMembershipNumber(),
                "registrationTime", em.getRegistrationCompletedAt()
        )).collect(Collectors.toList()));
        report.put("latestActiveMembers", latestActive.stream().map(em -> Map.of(
                "memberName", em.getName(),
                "membershipNumber", em.getMembershipNumber(),
                "lastActivity", em.getLastActivityAt()
        )).collect(Collectors.toList()));

        log.info("Comprehensive report generated for event: {} with {} total members", event.getName(), totalMembers);
        return report;
    }

    // 工具方法
    private Boolean getBooleanFromCriteria(Map<String, Object> criteria, String key) {
        Object value = criteria.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String str = (String) value;
            if (str.equalsIgnoreCase("true")) return true;
            if (str.equalsIgnoreCase("false")) return false;
        }
        return null;
    }

    private String getStringFromCriteria(Map<String, Object> criteria, String key) {
        Object value = criteria.get(key);
        if (value == null) return null;
        String str = value.toString().trim();
        return str.isEmpty() ? null : str;
    }
}