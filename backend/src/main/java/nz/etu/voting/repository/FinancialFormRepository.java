package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.FinancialForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialFormRepository extends JpaRepository<FinancialForm, Long> {

    // 根据EventMember ID查找所有表单记录
    List<FinancialForm> findByEventMemberIdOrderByCreatedAtDesc(Long eventMemberId);

    // 查找最新的表单记录
    Optional<FinancialForm> findTopByEventMemberIdOrderByCreatedAtDesc(Long eventMemberId);

    // 根据同步状态查找
    List<FinancialForm> findByStratumSyncStatus(String status);

    // 查找需要重试同步的记录
    @Query("SELECT f FROM FinancialForm f WHERE f.stratumSyncStatus = 'FAILED' " +
            "AND f.stratumSyncAttemptedAt < :retryThreshold")
    List<FinancialForm> findFailedForRetry(@Param("retryThreshold") LocalDateTime retryThreshold);

    // 根据会员编号查找
    List<FinancialForm> findByMembershipNumber(String membershipNumber);

    // 根据时间范围查找
    List<FinancialForm> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    // 查找特定事件的所有表单
    List<FinancialForm> findByEventId(Long eventId);

    // 根据表单类型查找
    List<FinancialForm> findByFormType(String formType);
}