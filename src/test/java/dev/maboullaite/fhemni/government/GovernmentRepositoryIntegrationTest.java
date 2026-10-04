package dev.maboullaite.fhemni.government;

import static org.assertj.core.api.Assertions.*;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;

@Testcontainers
class GovernmentRepositoryIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    static JdbcClient jdbc;
    static TransactionTemplate tx;
    static GovernmentRepository repository;
    @BeforeAll static void setup() throws Exception {
        var ds=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=JdbcClient.create(ds);tx=new TransactionTemplate(new JdbcTransactionManager(ds));
        Flyway.configure().dataSource(ds).target("61").load().migrate();
        long electionRows=jdbc.sql("SELECT COUNT(*) FROM elections").query(Long.class).single();
        long accountRows=jdbc.sql("SELECT COUNT(*) FROM app_users").query(Long.class).single();
        Flyway.configure().dataSource(ds).target("62").load().migrate();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM elections").query(Long.class).single()).isEqualTo(electionRows);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM app_users").query(Long.class).single()).isEqualTo(accountRows);
        repository=new GovernmentRepository(jdbc,new ObjectMapper());
    }
    @Test void typedRevisionPreservesMembershipDatesAndRejectsUnexpectedFields() {
        var source=UUID.randomUUID();
        var view=new MandateView("2026-2031",new LocalText("حكومة","Gouvernement","Government"),Lifecycle.FORMED_PROGRAMME_PENDING,
            Instant.parse("2026-10-01T00:00:00Z"),java.util.List.of(new Membership(UUID.randomUUID(),new LocalText("اسم","Nom","Name"),
            new LocalText("دور","Rôle","Role"),null,java.time.LocalDate.parse("2026-09-30"),java.time.LocalDate.parse("2026-10-01"),java.util.List.of(source))),null,null,java.util.List.of());
        var draft=tx.execute(status->repository.createDraft("2026-2031",SubjectType.MANDATE,"2026-2031",view,UUID.randomUUID(),"Dated membership draft"));
        assertThat(repository.decode(draft.payload(),MandateView.class)).isEqualTo(view);
        assertThatThrownBy(()->repository.decode(draft.payload().replaceFirst("\\{","{\"invented\":1,"),MandateView.class)).isInstanceOf(RuntimeException.class);
    }
    @Test void draftsArePrivateAndEachNewRevisionPreservesTheOldPayload() {
        var first=tx.execute(status->repository.createDraft("2026-2031",SubjectType.MANDATE,"2026-2031",GovernmentViews.pending("2026-2031"),UUID.randomUUID(),"Initial draft"));
        var second=tx.execute(status->repository.createDraft("2026-2031",SubjectType.MANDATE,"2026-2031",GovernmentViews.pending("2026-2031"),UUID.randomUUID(),"New draft"));
        assertThat(second.revisionNumber()).isEqualTo(first.revisionNumber()+1);
        assertThat(repository.published(SubjectType.MANDATE,"2026-2031",MandateView.class)).isEmpty();
        assertThat(repository.revision(first.id()).orElseThrow().payload()).isEqualTo(first.payload());
        assertThatThrownBy(()->tx.execute(status->repository.createDraft("2026-2031",SubjectType.MANDATE,"2026-2031",GovernmentViews.pending("wrong-mandate"),UUID.randomUUID(),"Invalid")))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void evidenceKeepsOriginalPassageAndVerifiesArchivedBytes() {
        tx.executeWithoutResult(status->repository.ensureMandate("2026-2031"));
        var evidenceRepository=new GovernmentEvidenceRepository(jdbc);
        var evidence=new Evidence(UUID.randomUUID(),"https://example.org/report","نص المصدر كما نُشر","p. 2","ar",null,Instant.now().minusSeconds(1),null);
        var saved=evidenceRepository.save("2026-2031",evidence,"abc".getBytes());
        assertThat(saved.archivedSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(evidenceRepository.find("2026-2031",saved.id()).orElseThrow().passage()).isEqualTo("نص المصدر كما نُشر");
        assertThat(evidenceRepository.find("another-mandate",saved.id())).isEmpty();
    }
}
