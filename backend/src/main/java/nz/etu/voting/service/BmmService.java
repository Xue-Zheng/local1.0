package nz.etu.voting.service;

import nz.etu.voting.domain.dto.request.BmmPreferenceRequest;
import nz.etu.voting.domain.dto.request.FinancialFormRequest;
import nz.etu.voting.domain.dto.request.AttendanceRequest;
import nz.etu.voting.domain.dto.response.BmmStatistics;
import nz.etu.voting.domain.dto.request.NonAttendanceRequest;
import nz.etu.voting.domain.dto.response.BmmAssignmentResponse;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.entity.EventMember;

public interface BmmService {

    // 通过token查找EventMember
    EventMember findEventMemberByToken(String token);

    // 第一阶段：保存偏好
    void savePreferencesToEventMember(BmmPreferenceRequest request);

    // 获取会场分配结果
    BmmAssignmentResponse getAssignmentByToken(String token);

    // 第二阶段：Financial表单更新（重要业务逻辑）
    void updateEventMemberFinancialInfo(FinancialFormRequest request);

    // 生成并发送票务
    void generateAndSendTicket(Long eventMemberId);

    // 记录不出席
    void recordNonAttendance(Long eventMemberId);

    // 处理不出席 + Special Vote
    void processNonAttendanceWithSpecialVote(NonAttendanceRequest request);

    // 发送第一阶段邀请邮件
    void sendStage1InvitationsByRegion(String regionDesc);

    // 发送第二阶段确认邮件
    void sendStage2ConfirmationEmails();

    // 发送Special Vote链接
    void sendSpecialVoteLinks();

    // 管理员分配会场
    void assignVenuesToMembers(Long eventId);

    // 获取BMM统计数据
    BmmStatistics getBmmStatistics(Long eventId);
}