package nz.etu.voting.repository;

import nz.etu.voting.domain.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByPrimaryEmail(String primaryEmail);

    Optional<Member> findByToken(UUID token);

    Optional<Member> findByMembershipNumberAndVerificationCode(String membershipNumber, String verificationCode);

    Optional<Member> findByMembershipNumber(String membershipNumber);

    Optional<Member> findByTelephoneMobile(String telephoneMobile);

    List<Member> findByHasRegisteredTrue();
    List<Member> findByHasRegisteredFalse();
    List<Member> findByIsAttendingTrue();
    List<Member> findByIsSpecialVoteTrue();
    List<Member> findByIsSpecialVoteFalse();
    List<Member> findByHasVotedTrue();

    List<Member> findByPrimaryEmailIn(List<String> primaryEmails);

//    Count methods for migration

    //    按数据源统计
    @Query("SELECT COUNT(m) FROM Member m WHERE m.dataSource = :dataSource")
    Long countByDataSource(@Param("dataSource") String dataSource);

    //    按行业分类统计
    @Query("SELECT m.siteIndustryDesc, COUNT(m) FROM Member m GROUP BY m.siteIndustryDesc")
    List<Object[]> countBySiteIndustryDesc();

    //    按地区统计
    @Query("SELECT m.regionDesc, COUNT(m) FROM Member m GROUP BY m.regionDesc")
    List<Object[]> countByRegionDesc();

    //    按邮箱状态筛选
    List<Member> findByHasEmailTrue();
    List<Member> findByHasEmailFalse();
    //    按手机状态筛选
    List<Member> findByHasMobileTrue();
    List<Member> findByHasMobileFalse();

    //    按数据源筛选
    List<Member> findByDataSource(String dataSource);

    //    按行业筛选
    List<Member> findBySiteIndustryDesc(String siteIndustryDesc);

    //    按地区筛选
    List<Member> findByRegionDesc(String regionDesc);

    //    复合查询
    @Query("SELECT m FROM Member m WHERE " +
            "(:dataSource IS NULL OR m.dataSource = :dataSource) AND " +
            "(:hasEmail IS NULL OR m.hasEmail = :hasEmail) AND " +
            "(:regionDesc IS NULL OR m.regionDesc = :regionDesc)")
    List<Member> findByComplexFilter(@Param("dataSource") String dataSource,
                                     @Param("hasEmail") Boolean hasEmail,
                                     @Param("regionDesc") String regionDesc);

    //    模糊匹配数据源统计
    @Query("SELECT COUNT(m) FROM Member m WHERE m.dataSource LIKE %:dataSourcePattern%")
    Long countByDataSourceContaining(@Param("dataSourcePattern") String dataSourcePattern);

    //    按时间范围统计同步数据
    @Query("SELECT COUNT(m) FROM Member m WHERE m.dataSource = :dataSource AND m.lastSyncTime >= :afterTime")
    Long countByDataSourceAndLastSyncTimeAfter(@Param("dataSource") String dataSource, @Param("afterTime") LocalDateTime afterTime);

    //    查找最新同步的记录
    Optional<Member> findTopByOrderByLastSyncTimeDesc();

    long countByHasRegisteredTrue();

    long countByIsAttendingTrue();

    Long countByIsSpecialVoteTrue();
}