package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties="fhemni.gemini.api-key=")
class GovernmentPublicationIntegrationTest {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);
    }
    @Autowired GovernmentRepository repository;
    @Autowired GovernmentEditorialService editorial;
    @Autowired GovernmentService government;
    @Autowired GovernmentPublicationEvents events;
    @Autowired GovernmentEvidenceRepository evidence;
    @Autowired CommitmentService commitments;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired dev.maboullaite.fhemni.programme.PartyProgrammeRepository campaignRepository;
    final UUID admin=UUID.randomUUID();
    static LocalText text(String value){return new LocalText(value,value,value);}
    @BeforeEach void isolatedGovernmentFixtures() {
        for(String table:List.of("government_publication_events","government_membership_history","government_programme_versions","government_publications","government_revisions","government_subjects","government_evidence","government_mandates"))jdbc.sql("DELETE FROM "+table).update();
        jdbc.sql("DELETE FROM party_promises WHERE programme_id IN (SELECT id FROM party_programmes WHERE source_url='https://example.org/campaign')").update();
        jdbc.sql("DELETE FROM party_programmes WHERE source_url='https://example.org/campaign'").update();
    }
    @Test void reviewedPublicationIsAtomicImmutableAndOnlyMaterialChangesEmitEvents() throws Exception {
        var pending=GovernmentViews.pending("2026-2031");
        var payload=new MandateView(pending.id(),pending.label(),pending.lifecycle(),Instant.now().minusSeconds(10),List.of(),null,null,List.of());
        var draft=repository.createDraft(payload.id(),SubjectType.MANDATE,payload.id(),payload,admin,"Initial reviewed state");
        assertThatThrownBy(()->editorial.publish(draft.id(),draft.rowVersion(),admin)).isInstanceOf(IllegalStateException.class);
        assertThat(government.mandate(payload.id()).verifiedAt()).isNull();
        var reviewed=editorial.review(draft.id(),draft.rowVersion(),admin);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=List.of(pool.submit(()->publishAfter(start,reviewed)),pool.submit(()->publishAfter(start,reviewed)));
            start.countDown();
            assertThat(jobs.get(0).get()+jobs.get(1).get()).isEqualTo(1);
        }
        assertThat(events.latest(payload.id(),10)).hasSize(1);
        assertThat(government.mandate(payload.id()).verifiedAt()).isEqualTo(payload.verifiedAt());
        var timestampOnly=new MandateView(payload.id(),payload.label(),payload.lifecycle(),Instant.now().minusSeconds(1),List.of(),null,null,List.of());
        var correction=repository.createDraft(payload.id(),SubjectType.MANDATE,payload.id(),timestampOnly,admin,"Rechecked unchanged sources");
        var checked=editorial.review(correction.id(),1,admin);editorial.publish(checked.id(),checked.rowVersion(),admin);
        assertThat(events.latest(payload.id(),10)).hasSize(1);
        assertThat(repository.revision(draft.id()).orElseThrow().payload()).isEqualTo(draft.payload());
        assertThat(repository.revision(draft.id()).orElseThrow().status()).isEqualTo(RevisionState.SUPERSEDED);
    }
    int publishAfter(CountDownLatch latch,GovernmentRepository.Revision revision)throws Exception {
        latch.await();try{editorial.publish(revision.id(),revision.rowVersion(),admin);return 1;}catch(IllegalStateException stale){return 0;}
    }
    @Test void commitmentPublicationRequiresOriginalEvidenceAndDoesNotInventDelivery() {
        repository.ensureMandate("2026-2031");
        var source=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/programme","Original source passage","p. 5","en",null,Instant.now().minusSeconds(1),null),null);
        var value=new CommitmentView(UUID.randomUUID(),"test-commitment",Origin.GOVERNMENT,text("A sourced commitment"),"Original source passage","en",text("Government"),null,List.of(),
            new Scope("NATIONAL","MA",text("Morocco")),null,null,null,null,List.of(),List.of(source),List.of(),List.of());
        var draft=repository.createDraft("2026-2031",SubjectType.COMMITMENT,value.slug(),value,admin,"Source extraction");
        assertThat(commitments.list("2026-2031","GOVERNMENT",null,null,1).items()).isEmpty();
        // A programme commitment cannot be approved before its programme is published.
        assertThatThrownBy(()->editorial.review(draft.id(),1,admin)).isInstanceOf(IllegalArgumentException.class);
        assertThat(value.deliveryAssessment()).isNull();
    }
    @Test void incompleteProgrammeCannotSupportAbsentPromiseAndAssessmentsNeedEvidence() {
        var link=new CommitmentLink(UUID.randomUUID(),null,Relation.NOT_IDENTIFIED,UUID.randomUUID(),text("Not found"),List.of(UUID.randomUUID()));
        assertThatThrownBy(()->GovernmentValidation.link(link,new ReviewCoverage(1,2,false))).isInstanceOf(IllegalArgumentException.class);
        var assessment=new DeliveryAssessment(DeliveryState.DELIVERED,EvidenceConfidence.CORROBORATED,text("Done"),List.of(),Instant.now());
        assertThatThrownBy(()->GovernmentValidation.assessment(assessment)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->GovernmentValidation.assessment(new DeliveryAssessment(DeliveryState.WITHDRAWN,EvidenceConfidence.GOVERNMENT_REPORTED,text(""),List.of(UUID.randomUUID()),Instant.now()))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void twoCampaignLinksCountOneGovernmentCommitmentAndRegionalEvidenceRemainsScoped() {
        repository.ensureMandate("2026-2031");
        var source=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/programme","Original passage","p. 1","en",null,Instant.now().minusSeconds(5),null),null);
        assertThatThrownBy(()->government.source("2026-2031",source.id())).isInstanceOf(java.util.NoSuchElementException.class);
        var programme=new ProgrammeVersion(UUID.randomUUID(),1,text("Programme"),java.time.LocalDate.parse("2026-10-01"),List.of(source.id()));
        var mandate=new MandateView("2026-2031",text("Mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(1),
            List.of(new Membership(UUID.randomUUID(),text("Test-only minister"),text("Role"),null,java.time.LocalDate.parse("2026-10-01"),null,List.of(source.id()))),programme,new ReviewCoverage(2,2,true),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.MANDATE,mandate.id(),mandate,admin,"Test-only programme"));
        assertThat(government.source("2026-2031",source.id()).passage()).isEqualTo(source.passage());
        var promiseIds=campaignFixtures();
        UUID id=UUID.randomUUID();
        String region=jdbc.sql("SELECT code FROM election_regions ORDER BY code LIMIT 1").query(String.class).single();
        var links=promiseIds.stream().map(promise->new CommitmentLink(promise,id,Relation.ADOPTED,programme.id(),text("Source supports link"),List.of(source.id()))).toList();
        var value=new CommitmentView(id,"regional-test",Origin.GOVERNMENT,text("Regional commitment"),source.passage(),"en",text("Government"),null,List.of("HEALTH"),
            new Scope("REGION",region,text("Region")),null,null,null,null,links,List.of(source),List.of(),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.COMMITMENT,value.slug(),value,admin,"Two links, one commitment"));
        var page=commitments.list(mandate.id(),"GOVERNMENT",null,null,1);
        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items().getFirst().links()).hasSize(2);
        assertThat(page.items().getFirst().deliveryAssessment()).isNull();
        assertThat(events.latest(mandate.id(),10).getFirst().regionCodes()).containsExactly(region);
        assertThat(events.latest(mandate.id(),10).getFirst().subjectId()).isEqualTo(value.id().toString());
        assertThat(commitments.detail(value.id().toString()).revisions()).hasSize(1);
        assertThat(government.mandate(mandate.id()).recentUpdates()).hasSize(2);
        var campaign=commitments.list(mandate.id(),"CAMPAIGN",null,"RNI",1);
        assertThat(campaign.items()).hasSize(2).allSatisfy(row->assertThat(row.deliveryAssessment()).isNull());
        var stale=repository.createDraft(mandate.id(),SubjectType.COMMITMENT,value.slug(),value,admin,"First correction");
        var checked=editorial.review(stale.id(),1,admin);
        var changedMandate=new MandateView(mandate.id(),text("Updated mandate context"),mandate.lifecycle(),mandate.verifiedAt(),mandate.membership(),mandate.programme(),mandate.reviewCoverage(),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.MANDATE,mandate.id(),changedMandate,admin,"Changed publication context"));
        assertThatThrownBy(()->editorial.publish(checked.id(),checked.rowVersion(),admin)).isInstanceOf(IllegalStateException.class);
        repository.createDraft(mandate.id(),SubjectType.COMMITMENT,value.slug(),value,admin,"Newer correction");
        assertThatThrownBy(()->editorial.publish(checked.id(),checked.rowVersion(),admin)).isInstanceOf(IllegalStateException.class);
    }
    @Test void independentlyReviewedRelationshipsAreVisibleFromBothEndpointsAndAbsenceNeedsFullCoverage() {
        repository.ensureMandate("2026-2031");
        var source=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/review","Reviewed programme passage","p. 2","en",null,Instant.now().minusSeconds(5),null),null);
        var programme=new ProgrammeVersion(UUID.randomUUID(),1,text("Programme"),java.time.LocalDate.parse("2026-10-01"),List.of(source.id()));
        var member=new Membership(UUID.randomUUID(),text("Fixture minister"),text("Role"),null,java.time.LocalDate.parse("2026-10-01"),null,List.of(source.id()));
        var mandate=new MandateView("2026-2031",text("Mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(1),List.of(member),programme,new ReviewCoverage(1,2,false),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.MANDATE,mandate.id(),mandate,admin,"Internal editorial note"));
        var promises=campaignFixtures();UUID governmentId=UUID.randomUUID();
        var target=new CommitmentView(governmentId,"linked-target",Origin.GOVERNMENT,text("Government target"),source.passage(),"en",text("Government"),null,List.of(),new Scope("NATIONAL","MA",text("Morocco")),null,null,null,null,List.of(),List.of(source),List.of(),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.COMMITMENT,target.slug(),target,admin,"Target"));
        var link=new CommitmentLink(promises.getFirst(),governmentId,Relation.ADOPTED,programme.id(),text("Adopted wording"),List.of(source.id()));
        var relationship=new RelationshipView(UUID.randomUUID(),"link-"+promises.getFirst()+"-"+programme.id(),text("Programme link"),link,List.of(source));
        publish(repository.createDraft(mandate.id(),SubjectType.LINK,relationship.slug(),relationship,admin,"Reviewed adoption"));
        assertThat(commitments.detail(promises.getFirst().toString()).links()).containsExactly(link);
        assertThat(commitments.detail(governmentId.toString()).links()).containsExactly(link);
        assertThat(commitments.detail(promises.getFirst().toString()).deliveryAssessment()).isNull();
        var modified=new CommitmentLink(promises.getFirst(),governmentId,Relation.MODIFIED,programme.id(),text("Modified wording"),List.of(source.id()));
        publish(repository.createDraft(mandate.id(),SubjectType.LINK,relationship.slug(),new RelationshipView(relationship.id(),relationship.slug(),relationship.title(),modified,List.of(source)),admin,"Reviewed modification"));
        assertThat(commitments.detail(governmentId.toString()).links()).containsExactly(modified);
        assertThat(commitments.detail(promises.getFirst().toString()).links()).containsExactly(modified);
        var absent=new RelationshipView(UUID.randomUUID(),"link-"+promises.getLast()+"-"+programme.id(),text("Programme review"),new CommitmentLink(promises.getLast(),null,Relation.NOT_IDENTIFIED,programme.id(),text("Not identified after review"),List.of(source.id())),List.of(source));
        var incomplete=repository.createDraft(mandate.id(),SubjectType.LINK,absent.slug(),absent,admin,"Cannot establish absence yet");
        assertThatThrownBy(()->editorial.review(incomplete.id(),1,admin)).isInstanceOf(IllegalArgumentException.class);
        var complete=new MandateView(mandate.id(),mandate.label(),mandate.lifecycle(),mandate.verifiedAt(),mandate.membership(),programme,new ReviewCoverage(2,2,true),List.of());
        publish(repository.createDraft(mandate.id(),SubjectType.MANDATE,mandate.id(),complete,admin,"Full review"));
        publish(repository.createDraft(mandate.id(),SubjectType.LINK,absent.slug(),absent,admin,"Reviewed absence"));
        assertThat(commitments.detail(promises.getLast().toString()).links()).containsExactly(absent.relationship());
        assertThat(commitments.list(mandate.id(),"GOVERNMENT",null,null,1).totalItems()).isEqualTo(1);
    }
    @Test void publicSnapshotsRetainPriorProgrammeAndAssessmentWithoutPrivateEditorialNotes() {
        repository.ensureMandate("2026-2031");
        var source=evidence.save("2026-2031",new Evidence(UUID.randomUUID(),"https://example.org/history","Original source","p. 1","en",null,Instant.now().minusSeconds(5),null),null);
        var programme=new ProgrammeVersion(UUID.randomUUID(),1,text("First programme"),java.time.LocalDate.parse("2026-10-01"),List.of(source.id()));
        var member=new Membership(UUID.randomUUID(),text("First minister"),text("Role"),null,java.time.LocalDate.parse("2026-10-01"),null,List.of(source.id()));
        var first=new MandateView("2026-2031",text("Mandate"),Lifecycle.PROGRAMME_PUBLISHED,Instant.now().minusSeconds(1),List.of(member),programme,new ReviewCoverage(1,1,true),List.of());
        var initial=repository.createDraft(first.id(),SubjectType.MANDATE,first.id(),first,admin,"SECRET editorial note");publish(initial);
        var revisedProgramme=new ProgrammeVersion(UUID.randomUUID(),2,text("Amended programme"),programme.publishedAt(),List.of(source.id()));
        var revisedMember=new Membership(UUID.randomUUID(),text("Replacement minister"),member.role(),null,member.effectiveFrom(),null,List.of(source.id()));
        var second=new MandateView(first.id(),first.label(),first.lifecycle(),first.verifiedAt(),List.of(revisedMember),revisedProgramme,first.reviewCoverage(),List.of());
        var amended=repository.createDraft(first.id(),SubjectType.MANDATE,first.id(),second,admin,"SECRET personnel note");publish(amended);
        var snapshot=government.revision(initial.id());
        assertThat((MandateView)snapshot.snapshot()).isEqualTo(first);
        assertThat(snapshot.evidence()).containsExactly(source);
        assertThat((MandateView)government.revision(amended.id()).snapshot()).isEqualTo(second);
        assertThat(repository.encode(snapshot)).doesNotContain("SECRET",admin.toString(),"authorId","reviewerId");
        assertThat(repository.publicHistory(SubjectType.MANDATE,first.id()).getFirst().explanation().en()).contains("membership","programme");
        var assessment=new DeliveryAssessment(DeliveryState.UNDERWAY,EvidenceConfidence.GOVERNMENT_REPORTED,text("Started"),List.of(source.id()),Instant.now().minusSeconds(1));
        var target=new CommitmentView(UUID.randomUUID(),"history-target",Origin.GOVERNMENT,text("Target"),source.passage(),"en",text("Government"),null,List.of(),new Scope("NATIONAL","MA",text("Morocco")),null,null,null,assessment,List.of(),List.of(source),List.of(),List.of());
        var original=repository.createDraft(first.id(),SubjectType.COMMITMENT,target.slug(),target,admin,"Private assessment note");publish(original);
        var corrected=new CommitmentView(target.id(),target.slug(),target.origin(),target.title(),target.originalText(),target.originalLanguage(),target.author(),null,target.topicCodes(),target.geographicScope(),null,null,null,new DeliveryAssessment(DeliveryState.PARTLY_DELIVERED,EvidenceConfidence.CORROBORATED,text("Corrected finding"),List.of(source.id()),assessment.reviewedAt()),List.of(),List.of(source),List.of(),List.of());
        publish(repository.createDraft(first.id(),SubjectType.COMMITMENT,target.slug(),corrected,admin,"Correction"));
        assertThat(((CommitmentView)government.revision(original.id()).snapshot()).deliveryAssessment()).isEqualTo(assessment);
        var draft=repository.createDraft(first.id(),SubjectType.COMMITMENT,target.slug(),corrected,admin,"Private unpublished draft");
        assertThatThrownBy(()->government.revision(draft.id())).isInstanceOf(java.util.NoSuchElementException.class);
        assertThat(repository.publicHistory(SubjectType.COMMITMENT,target.slug())).hasSize(2);
    }
    void publish(GovernmentRepository.Revision draft){var reviewed=editorial.review(draft.id(),1,admin);editorial.publish(reviewed.id(),reviewed.rowVersion(),admin);}
    List<UUID> campaignFixtures() {
        var now=Instant.now();var id=UUID.randomUUID();var title=new dev.maboullaite.fhemni.programme.PartyProgramme.LocalizedText("وعد","Promesse","Promise");
        campaignRepository.insertProgramme(new dev.maboullaite.fhemni.programme.PartyProgramme(id,"RNI",2026,2026,2031,title,title,"https://example.org/campaign","Programme","en","Original",List.of(),"a".repeat(64),now,true,dev.maboullaite.fhemni.programme.EditorialStatus.DRAFT,now,now,null));
        var ids=List.of(UUID.randomUUID(),UUID.randomUUID());
        for(var promise:ids)campaignRepository.insertPromise(new dev.maboullaite.fhemni.programme.PartyPromise(promise,id,"fixture-"+promise,"HEALTH",title,"Original passage","p. 1","Mechanism","Financing",dev.maboullaite.fhemni.programme.EditorialStatus.DRAFT,now,now,null));
        jdbc.sql("UPDATE party_promises SET editorial_status='PUBLISHED' WHERE programme_id=:id").param("id",id).update();
        jdbc.sql("UPDATE party_programmes SET editorial_status='PUBLISHED' WHERE id=:id").param("id",id).update();
        return ids;
    }
}
