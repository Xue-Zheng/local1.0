package nz.etu.voting.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.service.StratumService;
import lombok.RequiredArgsConstructor;

@Slf4j
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = {"http://localhost:3000", "http://10.0.9.238:3000", "https://events.etu.nz"})
@RequiredArgsConstructor
public class TestController {

    private final MemberRepository memberRepository;
    private final EventMemberRepository eventMemberRepository;
    private final StratumService stratumService;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("test");
    }

    @PostMapping("/stratum-sync/{membershipNumber}")
    public ResponseEntity<Map<String, Object>> testStratumSync(@PathVariable String membershipNumber) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Find member by membership number
            Optional<Member> memberOpt = memberRepository.findByMembershipNumber(membershipNumber);

            if (!memberOpt.isPresent()) {
                result.put("success", false);
                result.put("message", "Member not found: " + membershipNumber);
                return ResponseEntity.badRequest().body(result);
            }

            Member member = memberOpt.get();

            // Log member info before sync
            log.info("🔍 Testing Stratum sync for member: {}", membershipNumber);
            log.info("📋 Member details - Name: {}, Email: {}, Mobile: {}",
                    member.getName(), member.getPrimaryEmail(), member.getTelephoneMobile());
            log.info("🏢 Work details - Employer: {}, Department: {}, JobTitle: {}",
                    member.getEmployer(), member.getDepartment(), member.getJobTitle());

            // Attempt Stratum sync
            boolean syncResult = stratumService.syncMemberToStratum(member);

            result.put("success", syncResult);
            result.put("membershipNumber", membershipNumber);
            result.put("memberName", member.getName());
            result.put("syncResult", syncResult);

            if (syncResult) {
                result.put("message", "Stratum sync successful");
                log.info("✅ Test Stratum sync successful for member: {}", membershipNumber);
            } else {
                result.put("message", "Stratum sync failed - check logs for details");
                log.warn("❌ Test Stratum sync failed for member: {}", membershipNumber);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ Test Stratum sync error for member {}: {}", membershipNumber, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}