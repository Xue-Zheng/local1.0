package nz.etu.voting.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.*;
import nz.etu.voting.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;

    @Value("${bmm.auto-associate-on-startup:false}")
    private boolean autoAssociateOnStartup;

    @Override
    public void run(String... args) throws Exception {
        if (memberRepository.count() == 0) {
            loadData();
        }

        // 只在配置启用时才自动关联成员
        if (autoAssociateOnStartup) {
            log.info("Auto-associate on startup is enabled");
            ensureBMMEventAndAssociateMembers();
        } else {
            log.info("Auto-associate on startup is disabled. BMM member association will happen during scheduled sync at 2:00 AM");
        }
    }

    public void loadData() {
        log.info("Loading initial data (without test members)...");

//        Remove default event creation - only BMM event should be created

        log.info("Initial data loaded successfully!");
        log.info("Created {} events", eventRepository.count());
        // log.info("Created {} event attendees", eventAttendeeRepository.count());
    }

    //    Ensure BMM event exists and all members are associated as participants
    public void ensureBMMEventAndAssociateMembers() {
        log.info("Ensuring BMM event exists and all members are associated...");

        try {
//            Check if BMM event already exists
            List<Event> existingBMMEvents = eventRepository.findByEventTypeAndIsActiveTrue(Event.EventType.BMM_VOTING);
            Event bmmEvent;

            if (existingBMMEvents.isEmpty()) {
//                Create BMM event
                bmmEvent = Event.builder()
                        .name("Biennial Membership Meeting (BMM)")
                        .eventCode("BMM_" + LocalDateTime.now().getYear())
                        .datasetId("bmm-voting-dataset")
                        .description("Biennial Membership Meeting - All members voting event")
                        .eventType(Event.EventType.BMM_VOTING)
                        .eventDate(LocalDateTime.now().plusDays(30)) // Default 30 days from now
                        .venue("To Be Determined")
                        .isActive(true)
                        .isVotingEnabled(true)
                        .registrationOpen(true)
                        .qrScanEnabled(true)
                        .organizerScanToken(UUID.randomUUID().toString())
                        .syncStatus(Event.SyncStatus.SUCCESS)
                        .autoSyncEnabled(false) // BMM doesn't use Informer sync
                        .memberSyncCount(0)
                        .build();

                eventRepository.save(bmmEvent);
                log.info("Created BMM event with ID: {}", bmmEvent.getId());
            } else {
                bmmEvent = existingBMMEvents.get(0);
                log.info("BMM event already exists with ID: {}", bmmEvent.getId());
            }

//            Get all members
            List<Member> allMembers = memberRepository.findAll().stream()
                    .filter(member -> member.getMembershipNumber() != null &&
                            !member.getMembershipNumber().trim().isEmpty())
                    .toList();

//            Get existing event members for BMM
            List<EventMember> existingEventMembers = eventMemberRepository.findByEvent(bmmEvent);
            List<String> existingMembershipNumbers = existingEventMembers.stream()
                    .map(EventMember::getMembershipNumber)
                    .toList();

//            Find members not yet associated with BMM
            List<Member> newMembers = allMembers.stream()
                    .filter(member -> !existingMembershipNumbers.contains(member.getMembershipNumber()))
                    .toList();

            if (!newMembers.isEmpty()) {
                log.info("Associating {} new members with BMM event", newMembers.size());

//                Create EventMember records for new members
                List<EventMember> newEventMembers = new ArrayList<>();
                for (Member member : newMembers) {
                    EventMember eventMember = EventMember.builder()
                            .event(bmmEvent)
                            .member(member)
                            .name(member.getName())
                            .primaryEmail(member.getPrimaryEmail())
                            .membershipNumber(member.getMembershipNumber())
                            .telephoneMobile(member.getTelephoneMobile())
                            .hasEmail(member.getHasEmail() != null ? member.getHasEmail() :
                                    (member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty()))
                            .hasMobile(member.getHasMobile() != null ? member.getHasMobile() :
                                    (member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty()))
                            .token(UUID.randomUUID())
                            .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                            .hasRegistered(false)
                            .isAttending(false)
                            .isSpecialVote(false)
                            .hasVoted(false)
                            .checkedIn(false)
                            .dataSource("BMM_AUTO_ASSOCIATION")
                            .regionDesc(member.getRegionDesc())
                            .region(member.getRegionDesc())
                            .branch(member.getBranchDesc())
                            .bargainingGroup(member.getForumDesc())
                            .workplace(member.getWorkplaceDesc())
                            .employer(member.getEmployerName())
                            .registrationStatus("PENDING")
                            .build();

                    newEventMembers.add(eventMember);
                }

//                Save in batches to handle large numbers of members
                int batchSize = 1000;
                for (int i = 0; i < newEventMembers.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, newEventMembers.size());
                    List<EventMember> batch = newEventMembers.subList(i, endIndex);
                    eventMemberRepository.saveAll(batch);
                    log.info("Saved batch of {} event members (progress: {}/{})",
                            batch.size(), endIndex, newEventMembers.size());
                }

//                Update event member count
                bmmEvent.setMemberSyncCount(bmmEvent.getMemberSyncCount() + newEventMembers.size());
                bmmEvent.setLastSyncTime(LocalDateTime.now());
                eventRepository.save(bmmEvent);

                log.info("Successfully associated {} members with BMM event", newEventMembers.size());
            } else {
                log.info("All members are already associated with BMM event");
            }

//            Final count
            Long totalBMMMembers = eventMemberRepository.countByEvent(bmmEvent);
            log.info("BMM event has {} total associated members", totalBMMMembers);

        } catch (Exception e) {
            log.error("Error ensuring BMM event and member association", e);
            throw new RuntimeException("Failed to ensure BMM event setup", e);
        }
    }
}