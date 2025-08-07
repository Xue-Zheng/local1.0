package nz.etu.voting.service;

import nz.etu.voting.domain.dto.request.AttendanceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.VerificationRequest;
import nz.etu.voting.domain.dto.response.MemberResponse;
import nz.etu.voting.domain.entity.Member;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MemberService {

    Member findByToken(UUID token);

    MemberResponse verifyMember(VerificationRequest request);

    MemberResponse getMemberByToken(UUID token);

    MemberResponse updateMemberFinancialForm(UUID token, FinancialFormRequest request);

    MemberResponse updateAttendanceChoice(UUID token, AttendanceRequest request);

    // Quick search and communication methods
    List<Map<String, Object>> quickSearchMembers(String searchTerm);
    boolean sendQuickEmailToMember(String membershipNumber, String subject, String content);
    boolean sendQuickEmailToMember(String membershipNumber, String subject, String content, boolean useVariableReplacement);
    boolean sendQuickEmailToMember(String membershipNumber, String subject, String content, boolean useVariableReplacement, String provider);
    boolean sendQuickSMSToMember(String membershipNumber, String message);
    boolean sendQuickSMSToMember(String membershipNumber, String message, boolean useVariableReplacement);
}