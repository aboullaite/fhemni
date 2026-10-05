package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAnalysisRetention {
    private final JdbcClient jdbc;
    private final MemberAnalysisRepository repository;
    public MemberAnalysisRetention(JdbcClient jdbc,MemberAnalysisRepository repository){this.jdbc=jdbc;this.repository=repository;}
    @Scheduled(fixedDelayString="${fhemni.member-analysis.cleanup-delay-ms:300000}",initialDelayString="${fhemni.member-analysis.cleanup-initial-delay-ms:60000}")
    @Transactional public void cleanup(){purgeExpired(Instant.now());}
    private record Expired(UUID id,UUID userId) {}
    @Transactional public int purgeExpired(Instant now){
        var rows=jdbc.sql("SELECT id,user_id FROM member_analysis_jobs WHERE expires_at<=:now ORDER BY user_id,id LIMIT 100")
            .param("now",now.atOffset(ZoneOffset.UTC)).query(Expired.class).list();
        int removed=0;
        for(var row:rows){
            repository.lockOwner(row.userId());
            removed+=jdbc.sql("DELETE FROM member_analysis_jobs WHERE id=:id AND expires_at<=:now")
                .param("id",row.id()).param("now",now.atOffset(ZoneOffset.UTC)).update();
        }
        return removed;
    }
}
