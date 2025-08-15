package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/data-categories")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class DataCategoryController {

    private final MemberRepository memberRepository;

    // 获取完整的数据分类统计
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDataCategoriesOverview() {
        try {
            List<Member> allMembers = memberRepository.findAll();

            Map<String, Object> overview = new HashMap<>();

            // 基础统计
            overview.put("totalMembers", allMembers.size());
            overview.put("withEmail", allMembers.stream().filter(m -> m.getHasEmail() != null && m.getHasEmail()).count());
            overview.put("withMobile", allMembers.stream().filter(m -> m.getHasMobile() != null && m.getHasMobile()).count());

            // 按行业分类 (siteIndustryDesc)
            Map<String, Long> industryStats = allMembers.stream()
                    .filter(m -> m.getSiteIndustryDesc() != null && !m.getSiteIndustryDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getSiteIndustryDesc, Collectors.counting()));
            overview.put("industryBreakdown", industryStats);

            // 按地区分类 (regionDesc)
            Map<String, Long> regionStats = allMembers.stream()
                    .filter(m -> m.getRegionDesc() != null && !m.getRegionDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getRegionDesc, Collectors.counting()));
            overview.put("regionBreakdown", regionStats);

            // 按性别分类 (genderDesc)
            Map<String, Long> genderStats = allMembers.stream()
                    .filter(m -> m.getGenderDesc() != null && !m.getGenderDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getGenderDesc, Collectors.counting()));
            overview.put("genderBreakdown", genderStats);

            // 按民族分类 (ethnicRegionDesc)
            Map<String, Long> ethnicStats = allMembers.stream()
                    .filter(m -> m.getEthnicRegionDesc() != null && !m.getEthnicRegionDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getEthnicRegionDesc, Collectors.counting()));
            overview.put("ethnicBreakdown", ethnicStats);

            // 按会员类型分类 (membershipTypeDesc)
            Map<String, Long> membershipTypeStats = allMembers.stream()
                    .filter(m -> m.getMembershipTypeDesc() != null && !m.getMembershipTypeDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getMembershipTypeDesc, Collectors.counting()));
            overview.put("membershipTypeBreakdown", membershipTypeStats);

            // 按雇主分类 (employerName) - Top 10
            Map<String, Long> employerStats = allMembers.stream()
                    .filter(m -> m.getEmployerName() != null && !m.getEmployerName().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getEmployerName, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            overview.put("topEmployers", employerStats);

            // 按分支分类 (branchDesc)
            Map<String, Long> branchStats = allMembers.stream()
                    .filter(m -> m.getBranchDesc() != null && !m.getBranchDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getBranchDesc, Collectors.counting()));
            overview.put("branchBreakdown", branchStats);

            // 按子行业分类 (siteSubIndustryDesc)
            Map<String, Long> subIndustryStats = allMembers.stream()
                    .filter(m -> m.getSiteSubIndustryDesc() != null && !m.getSiteSubIndustryDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getSiteSubIndustryDesc, Collectors.counting()));
            overview.put("subIndustryBreakdown", subIndustryStats);

            // 按谈判组分类 (bargainingGroupDesc) - Top 15
            Map<String, Long> bargainingGroupStats = allMembers.stream()
                    .filter(m -> m.getBargainingGroupDesc() != null && !m.getBargainingGroupDesc().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getBargainingGroupDesc, Collectors.counting()))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(15)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            overview.put("topBargainingGroups", bargainingGroupStats);

            // 按年龄组分类 (ageOfMember)
            Map<String, Long> ageGroupStats = allMembers.stream()
                    .filter(m -> m.getAgeOfMember() != null && !m.getAgeOfMember().trim().isEmpty())
                    .map(m -> {
                        try {
                            double age = Double.parseDouble(m.getAgeOfMember());
                            if (age < 25) return "Under 25";
                            else if (age < 35) return "25-34";
                            else if (age < 45) return "35-44";
                            else if (age < 55) return "45-54";
                            else if (age < 65) return "55-64";
                            else return "65+";
                        } catch (NumberFormatException e) {
                            return "Unknown";
                        }
                    })
                    .collect(Collectors.groupingBy(ageGroup -> ageGroup, Collectors.counting()));
            overview.put("ageGroupBreakdown", ageGroupStats);

            // 按数据源分类 (dataSource)
            Map<String, Long> dataSourceStats = allMembers.stream()
                    .filter(m -> m.getDataSource() != null && !m.getDataSource().trim().isEmpty())
                    .collect(Collectors.groupingBy(Member::getDataSource, Collectors.counting()));
            overview.put("dataSourceBreakdown", dataSourceStats);

            return ResponseEntity.ok(ApiResponse.success("Data categories overview retrieved successfully", overview));

        } catch (Exception e) {
            log.error("Failed to get data categories overview: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get data categories overview: " + e.getMessage()));
        }
    }

    // 获取特定分类的详细列表
    @GetMapping("/category/{categoryType}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCategoryDetails(
            @PathVariable String categoryType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            List<Member> allMembers = memberRepository.findAll();
            Map<String, Object> result = new HashMap<>();

            switch (categoryType.toLowerCase()) {
                case "industry":
                    result = getCategoryDetail(allMembers, Member::getSiteIndustryDesc, "Industry", page, size);
                    break;
                case "region":
                    result = getCategoryDetail(allMembers, Member::getRegionDesc, "Region", page, size);
                    break;
                case "gender":
                    result = getCategoryDetail(allMembers, Member::getGenderDesc, "Gender", page, size);
                    break;
                case "ethnic":
                    result = getCategoryDetail(allMembers, Member::getEthnicRegionDesc, "Ethnic Group", page, size);
                    break;
                case "employer":
                    result = getCategoryDetail(allMembers, Member::getEmployerName, "Employer", page, size);
                    break;
                case "branch":
                    result = getCategoryDetail(allMembers, Member::getBranchDesc, "Branch", page, size);
                    break;
                case "subindustry":
                    result = getCategoryDetail(allMembers, Member::getSiteSubIndustryDesc, "Sub Industry", page, size);
                    break;
                case "bargaining":
                    result = getCategoryDetail(allMembers, Member::getBargainingGroupDesc, "Bargaining Group", page, size);
                    break;
                default:
                    return ResponseEntity.badRequest().body(ApiResponse.error("Invalid category type: " + categoryType));
            }

            return ResponseEntity.ok(ApiResponse.success("Category details retrieved successfully", result));

        } catch (Exception e) {
            log.error("Failed to get category details for {}: {}", categoryType, e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get category details: " + e.getMessage()));
        }
    }

    private Map<String, Object> getCategoryDetail(List<Member> members,
                                                  java.util.function.Function<Member, String> fieldExtractor,
                                                  String categoryName, int page, int size) {
        Map<String, Object> result = new HashMap<>();

        // 统计每个类别的成员数量
        Map<String, Long> categoryStats = members.stream()
                .filter(m -> fieldExtractor.apply(m) != null && !fieldExtractor.apply(m).trim().isEmpty())
                .collect(Collectors.groupingBy(fieldExtractor, Collectors.counting()));

        // 按成员数量排序
        List<Map.Entry<String, Long>> sortedEntries = categoryStats.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());

        // 分页
        int totalCategories = sortedEntries.size();
        int start = page * size;
        int end = Math.min(start + size, totalCategories);

        List<Map<String, Object>> pagedData = new ArrayList<>();
        for (int i = start; i < end; i++) {
            Map.Entry<String, Long> entry = sortedEntries.get(i);
            Map<String, Object> categoryInfo = new HashMap<>();
            categoryInfo.put("name", entry.getKey());
            categoryInfo.put("count", entry.getValue());
            categoryInfo.put("percentage", String.format("%.1f%%", (entry.getValue() * 100.0) / members.size()));
            pagedData.add(categoryInfo);
        }

        result.put("categoryName", categoryName);
        result.put("totalCategories", totalCategories);
        result.put("totalMembers", members.size());
        result.put("page", page);
        result.put("size", size);
        result.put("data", pagedData);

        return result;
    }

    // 获取成员字段完整性报告
    @GetMapping("/completeness")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDataCompleteness() {
        try {
            List<Member> allMembers = memberRepository.findAll();
            Map<String, Object> completeness = new HashMap<>();

            int totalMembers = allMembers.size();

            // 计算各字段的完整性
            completeness.put("totalMembers", totalMembers);
            completeness.put("name", calculateCompleteness(allMembers, Member::getName, totalMembers));
            completeness.put("primaryEmail", calculateCompleteness(allMembers, Member::getPrimaryEmail, totalMembers));
            completeness.put("telephoneMobile", calculateCompleteness(allMembers, Member::getTelephoneMobile, totalMembers));
            completeness.put("siteIndustryDesc", calculateCompleteness(allMembers, Member::getSiteIndustryDesc, totalMembers));
            completeness.put("regionDesc", calculateCompleteness(allMembers, Member::getRegionDesc, totalMembers));
            completeness.put("genderDesc", calculateCompleteness(allMembers, Member::getGenderDesc, totalMembers));
            completeness.put("ageOfMember", calculateCompleteness(allMembers, Member::getAgeOfMember, totalMembers));
            completeness.put("ethnicRegionDesc", calculateCompleteness(allMembers, Member::getEthnicRegionDesc, totalMembers));
            completeness.put("employerName", calculateCompleteness(allMembers, Member::getEmployerName, totalMembers));
            completeness.put("workplaceDesc", calculateCompleteness(allMembers, Member::getWorkplaceDesc, totalMembers));
            completeness.put("branchDesc", calculateCompleteness(allMembers, Member::getBranchDesc, totalMembers));
            completeness.put("siteSubIndustryDesc", calculateCompleteness(allMembers, Member::getSiteSubIndustryDesc, totalMembers));
            completeness.put("bargainingGroupDesc", calculateCompleteness(allMembers, Member::getBargainingGroupDesc, totalMembers));
            completeness.put("occupation", calculateCompleteness(allMembers, Member::getOccupation, totalMembers));
            completeness.put("membershipTypeDesc", calculateCompleteness(allMembers, Member::getMembershipTypeDesc, totalMembers));
            completeness.put("dob", calculateCompleteness(allMembers, Member::getDob, totalMembers));
            completeness.put("address", calculateCompleteness(allMembers, Member::getAddress, totalMembers));

            return ResponseEntity.ok(ApiResponse.success("Data completeness report retrieved successfully", completeness));

        } catch (Exception e) {
            log.error("Failed to get data completeness report: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to get data completeness report: " + e.getMessage()));
        }
    }

    private Map<String, Object> calculateCompleteness(List<Member> members,
                                                      java.util.function.Function<Member, String> fieldExtractor,
                                                      int totalMembers) {
        long nonEmptyCount = members.stream()
                .filter(m -> fieldExtractor.apply(m) != null && !fieldExtractor.apply(m).trim().isEmpty())
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("count", nonEmptyCount);
        result.put("percentage", String.format("%.1f%%", (nonEmptyCount * 100.0) / totalMembers));
        result.put("missing", totalMembers - nonEmptyCount);

        return result;
    }
}