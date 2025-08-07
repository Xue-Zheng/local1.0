package nz.etu.voting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nz.etu.voting.domain.dto.request.AttendanceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.VerificationRequest;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.dto.response.MemberResponse;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/registration")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class RegistrationController {
    private final MemberService memberService;
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<MemberResponse>> verifyMember(@Valid @RequestBody VerificationRequest request) {
        try {
            MemberResponse member = memberService.verifyMember(request);
            return ResponseEntity.ok(ApiResponse.success("Verification successful", member));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @GetMapping("/member/{token}")
    public ResponseEntity<ApiResponse<MemberResponse>> getMemberByToken(@PathVariable String token) {
        try {
            UUID uuid = UUID.fromString(token);
            MemberResponse member = memberService.getMemberByToken(uuid);
            return ResponseEntity.ok(ApiResponse.success(member));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @PostMapping("/update-form/{token}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateForm(
            @PathVariable String token,
            @Valid @RequestBody FinancialFormRequest request) {
        try {
            UUID uuid = UUID.fromString(token);
            MemberResponse member = memberService.updateMemberFinancialForm(uuid, request);
            return ResponseEntity.ok(ApiResponse.success("Information updated successfully", member));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @GetMapping("/member-type/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberType(@PathVariable String token) {
        try {
            UUID uuid = UUID.fromString(token);
            Member member = memberService.findByToken(uuid);

            Map<String, Object> typeInfo = new HashMap<>();
            // Special member field removed
            typeInfo.put("membershipNumber", member.getMembershipNumber());
            typeInfo.put("verificationCode", member.getVerificationCode());
            return ResponseEntity.ok(ApiResponse.success("Member type info retrieved", typeInfo));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
    @PostMapping("/attendance/{token}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateAttendance(
            @PathVariable String token,
            @Valid @RequestBody AttendanceRequest request) {
        try {
            UUID uuid = UUID.fromString(token);
            MemberResponse member = memberService.updateAttendanceChoice(uuid, request);
            return ResponseEntity.ok(ApiResponse.success("Attendance choice updated successfully", member));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}