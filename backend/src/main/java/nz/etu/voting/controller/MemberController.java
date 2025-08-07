package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.dto.response.MemberResponse;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/member-management")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class MemberController {

    private final MemberRepository memberRepository;

    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getAllMembers() {
        try {
            List<Member> members = memberRepository.findAll();
            List<MemberResponse> memberResponses = members.stream()
                    .map(this::mapToMemberResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success("Members retrieved successfully", memberResponses));
        } catch (Exception e) {
            log.error("Failed to retrieve members", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<ApiResponse<MemberResponse>> getMemberById(@PathVariable Long id) {
        try {
            Optional<Member> memberOpt = memberRepository.findById(id);
            if (memberOpt.isPresent()) {
                MemberResponse response = mapToMemberResponse(memberOpt.get());
                return ResponseEntity.ok(ApiResponse.success("Member retrieved successfully", response));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }
        } catch (Exception e) {
            log.error("Failed to retrieve member with id: " + id, e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private MemberResponse mapToMemberResponse(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .name(member.getName())
                .primaryEmail(member.getPrimaryEmail())
                .membershipNumber(member.getMembershipNumber())
                .dob(member.getDobLegacy() != null ? member.getDobLegacy() :
                        (member.getDob() != null ? parseDate(member.getDob()) : null))
                .address(member.getAddress())
                .phoneHome(member.getPhoneHome())
                .telephoneMobile(member.getTelephoneMobile())
                .phoneWork(member.getPhoneWork())
                .employer(member.getEmployer())
                .payrollNumber(member.getPayrollNumber())
                .siteNumber(member.getSiteNumber())
                .employmentStatus(member.getEmploymentStatus())
                .department(member.getDepartment())
                .jobTitle(member.getJobTitle())
                .location(member.getLocation())
                .hasRegistered(member.getHasRegistered())
                .isAttending(member.getIsAttending())
                .isSpecialVote(member.getIsSpecialVote())
                .verificationCode(member.getVerificationCode())
                .token(member.getToken() != null ? member.getToken().toString() : null)
                .absenceReason(member.getAbsenceReason())
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
//            尝试解析DD/MM/YYYY格式
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }


}