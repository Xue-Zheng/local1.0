package nz.etu.voting.service;

import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.EventMember;

public interface StratumService {

    boolean syncMemberToStratum(Member member);

    // 直接从EventMember同步到Stratum，用于BMM流程
    boolean syncEventMemberToStratum(EventMember eventMember);
}