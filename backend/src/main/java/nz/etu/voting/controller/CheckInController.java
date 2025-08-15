package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.service.QRCodeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/checkin")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})

@RequiredArgsConstructor
public class CheckInController {

    private final EventMemberRepository eventMemberRepository;
    private final QRCodeService qrCodeService;
    private final EventRepository eventRepository;

    @PostMapping("/{token}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkInMember(@PathVariable String token) {
        try {
            UUID memberToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token or member not found"));
            }

            EventMember member = memberOpt.get();

            if (!member.getIsAttending()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member is not registered as attending"));
            }

            if (member.getCheckedIn()) {
                Map<String, Object> response = new HashMap<>();
                response.put("message", "Member already checked in");
                response.put("checkInTime", member.getCheckInTime());
                response.put("memberInfo", buildMemberInfo(member));

                return ResponseEntity.ok(ApiResponse.success("Member already checked in", response));
            }

            member.setCheckedIn(true);
            member.setCheckInTime(LocalDateTime.now());
            eventMemberRepository.save(member);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Check-in successful!");
            response.put("checkInTime", member.getCheckInTime());
            response.put("memberInfo", buildMemberInfo(member));

            log.info("Member {} checked in successfully for event {}",
                    member.getMembershipNumber(), member.getEvent().getName());

            return ResponseEntity.ok(ApiResponse.success("Check-in successful", response));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token format"));
        } catch (Exception e) {
            log.error("Check-in failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Check-in failed: " + e.getMessage()));
        }
    }

    @GetMapping("/{token}/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMemberInfo(@PathVariable String token) {
        try {
            UUID memberToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token or member not found"));
            }

            EventMember member = memberOpt.get();
            Map<String, Object> memberInfo = buildMemberInfo(member);

            return ResponseEntity.ok(ApiResponse.success("Member information retrieved", memberInfo));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid token format"));
        } catch (Exception e) {
            log.error("Failed to get member info: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve member information"));
        }
    }

    @GetMapping("/{token}/qrcode")
    public ResponseEntity<byte[]> generateQRCode(@PathVariable String token) {
        try {
            UUID memberToken = UUID.fromString(token);
            Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);

            if (!memberOpt.isPresent()) {
                return ResponseEntity.badRequest().build();
            }

            EventMember member = memberOpt.get();
            byte[] qrCode = qrCodeService.generateQRCodeForEventMember(member);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(qrCode);

        } catch (Exception e) {
            log.error("Failed to generate QR code: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/staff/{eventId}/qrcodes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> generateEventQRCodes(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            List<EventMember> attendingMembers = eventMemberRepository.findByEventAndIsAttendingTrue(event);

            List<Map<String, Object>> qrCodeList = attendingMembers.stream().map(member -> {
                Map<String, Object> memberQR = new HashMap<>();
                memberQR.put("memberId", member.getId());
                memberQR.put("name", member.getName());
                memberQR.put("membershipNumber", member.getMembershipNumber());
                memberQR.put("primaryEmail", member.getPrimaryEmail());
                memberQR.put("token", member.getToken().toString());
                memberQR.put("qrCodeData", qrCodeService.generateStaffScannerQRCode(
                        eventId, member.getMembershipNumber(), member.getToken().toString()));
                return memberQR;
            }).toList();

            return ResponseEntity.ok(ApiResponse.success("QR codes generated for event", qrCodeList));

        } catch (Exception e) {
            log.error("Failed to generate QR codes for event: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to generate QR codes"));
        }
    }

    @GetMapping("/staff/{eventId}/members")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getEventMembers(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            Event event = eventOpt.get();
            List<EventMember> eventMembers = eventMemberRepository.findByEvent(event);

            List<Map<String, Object>> membersList = eventMembers.stream().map(member -> {
                Map<String, Object> memberData = new HashMap<>();
                memberData.put("id", member.getId());
                memberData.put("name", member.getName());
                memberData.put("membershipNumber", member.getMembershipNumber());
                memberData.put("primaryEmail", member.getPrimaryEmail());
                memberData.put("telephoneMobile", member.getTelephoneMobile());
                memberData.put("eventId", event.getId());
                memberData.put("eventName", event.getName());
                memberData.put("eventCode", event.getEventCode());
                memberData.put("isAttending", member.getIsAttending());
                memberData.put("checkedIn", member.getCheckedIn());
                memberData.put("checkInTime", member.getCheckInTime());
                memberData.put("hasVoted", member.getHasVoted());
                memberData.put("isSpecialVote", member.getIsSpecialVote());
                memberData.put("hasRegistered", member.getHasRegistered());
                memberData.put("token", member.getToken().toString());
                return memberData;
            }).toList();

            return ResponseEntity.ok(ApiResponse.success("Event members retrieved", membersList));

        } catch (Exception e) {
            log.error("Failed to get event members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Failed to retrieve event members"));
        }
    }

    @PostMapping("/staff/{eventId}/checkin")
    public ResponseEntity<ApiResponse<Map<String, Object>>> staffCheckIn(
            @PathVariable Long eventId,
            @RequestBody Map<String, String> request) {
        try {
            Optional<Event> eventOpt = eventRepository.findById(eventId);
            if (!eventOpt.isPresent()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Event not found"));
            }

            String token = request.get("token");
            String membershipNumber = request.get("membershipNumber");

            EventMember member = null;

//            优先使用token查找
            if (token != null && !token.trim().isEmpty()) {
                try {
                    UUID memberToken = UUID.fromString(token);
                    Optional<EventMember> memberOpt = eventMemberRepository.findByToken(memberToken);
                    if (memberOpt.isPresent()) {
                        member = memberOpt.get();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid token format: {}", token);
                }
            }

//            如果token查找失败，使用会员号查找
            if (member == null && membershipNumber != null && !membershipNumber.trim().isEmpty()) {
                Optional<EventMember> memberOpt = eventMemberRepository.findByEventAndMembershipNumber(
                        eventOpt.get(), membershipNumber);
                if (memberOpt.isPresent()) {
                    member = memberOpt.get();
                }
            }

            if (member == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member not found"));
            }

//            验证成员是否属于指定活动
            if (!member.getEvent().getId().equals(eventId)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member does not belong to this event"));
            }

            if (!member.getIsAttending()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Member is not registered as attending"));
            }

            Map<String, Object> response = new HashMap<>();
            boolean alreadyCheckedIn = member.getCheckedIn();

            if (!alreadyCheckedIn) {
                member.setCheckedIn(true);
                member.setCheckInTime(LocalDateTime.now());
                eventMemberRepository.save(member);
            }

            response.put("alreadyCheckedIn", alreadyCheckedIn);
            response.put("checkInTime", member.getCheckInTime());
            response.put("memberInfo", buildMemberInfo(member));

            String message = alreadyCheckedIn ? "Member already checked in" : "Check-in successful";
            log.info("Staff check-in for member {} at event {}: {}",
                    member.getMembershipNumber(), eventId, message);

            return ResponseEntity.ok(ApiResponse.success(message, response));

        } catch (Exception e) {
            log.error("Staff check-in failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Check-in failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> buildMemberInfo(EventMember member) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", member.getName());
        info.put("membershipNumber", member.getMembershipNumber());
        info.put("eventName", member.getEvent().getName());
        info.put("isAttending", member.getIsAttending());
        info.put("checkedIn", member.getCheckedIn());
        info.put("checkInTime", member.getCheckInTime());
        info.put("hasVoted", member.getHasVoted());
        info.put("isSpecialVote", member.getIsSpecialVote());
        return info;
    }
}