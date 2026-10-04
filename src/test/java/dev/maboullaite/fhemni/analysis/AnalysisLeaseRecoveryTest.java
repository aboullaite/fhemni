package dev.maboullaite.fhemni.analysis;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import dev.maboullaite.fhemni.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
@SpringBootTest(properties={"fhemni.gemini.api-key=","spring.datasource.url=jdbc:h2:mem:lease-recovery;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class AnalysisLeaseRecoveryTest {
    @Autowired JdbcClient jdbc;@Autowired tools.jackson.databind.ObjectMapper mapper;@Autowired AnalysisRevisionRepository repository;
    @Test void anotherInstanceDoesNotFailHealthyAdminWork(){var other=new AnalysisRevisionRepository(jdbc,mapper);UUID id=UUID.randomUUID();other.create(new AnalysisSnapshot(id,"https://www.youtube.com/watch?v=abcdefghijk","abcdefghijk",OutputLanguage.ENGLISH,AnalysisStatus.QUEUED,0,"Queued",false,Instant.now(),null,null,List.of(),false,null),"fixture","v1","fixture","v1","fixture");repository.failInterrupted();assertThat(other.find(id).orElseThrow().snapshot().status()).isEqualTo(AnalysisStatus.QUEUED);jdbc.sql("UPDATE analysis_revisions SET worker_lease_until=:expired WHERE id=:id").param("id",id).param("expired",Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC)).update();repository.failInterrupted();assertThat(other.find(id).orElseThrow().snapshot().status()).isEqualTo(AnalysisStatus.FAILED);}
    @Test void expiredOwnerCannotReviveOrCompleteRecoveredWork(){
        UUID id=UUID.randomUUID();
        repository.create(new AnalysisSnapshot(id,"https://www.youtube.com/watch?v=abcdefghijk","abcdefghijk",OutputLanguage.ENGLISH,AnalysisStatus.QUEUED,0,"Queued",false,Instant.now(),null,null,List.of(),false,null),"fixture","v1","fixture","v1","fixture");
        jdbc.sql("UPDATE analysis_revisions SET worker_lease_until=:expired WHERE id=:id").param("id",id).param("expired",Instant.now().minusSeconds(1).atOffset(ZoneOffset.UTC)).update();
        repository.failInterrupted();
        repository.updateProgress(id,AnalysisStatus.ANALYZING,12,"Late progress");
        assertThat(repository.find(id).orElseThrow().snapshot().status()).isEqualTo(AnalysisStatus.FAILED);
        repository.complete(id,new VideoReport("Late","Summary","Details",List.of(),List.of(),List.of(),List.of()),"private-interaction");
        assertThat(repository.find(id).orElseThrow().snapshot().status()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(repository.find(id).orElseThrow().snapshot().report()).isNull();
    }
}
