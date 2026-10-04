package dev.maboullaite.fhemni.following;
import dev.maboullaite.fhemni.government.*;
import dev.maboullaite.fhemni.government.GovernmentViews.Page;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class CorrectionService {
    private final JdbcClient jdbc;private final CommitmentService commitments;private final MeasureService measures;private final FollowRepository locks;
    public CorrectionService(JdbcClient jdbc,CommitmentService commitments,MeasureService measures,FollowRepository locks){this.jdbc=jdbc;this.commitments=commitments;this.measures=measures;this.locks=locks;}
    public record Submission(String type,UUID targetId,String evidenceUrl,String text) {}
    public record Correction(UUID id,String type,UUID targetId,String evidenceUrl,String text,String status,Instant createdAt,String reviewNote,Instant reviewedAt) {}
    @Transactional public Correction submit(UUID user,Submission value){
        if(value==null||value.targetId()==null||value.type()==null||!List.of("COMMITMENT","MEASURE").contains(value.type()))throw new IllegalArgumentException("Choose a published target.");
        if(value.type().equals("COMMITMENT")){var row=commitments.detail(value.targetId().toString());if(row.origin()!=GovernmentViews.Origin.GOVERNMENT)throw new IllegalArgumentException("Choose a government commitment.");}else measures.detail(value.targetId().toString());
        GovernmentValidation.url(value.evidenceUrl());GovernmentValidation.text(value.text(),"correction",4000);locks.lock(user);
        long count=jdbc.sql("SELECT COUNT(*) FROM accountability_corrections WHERE user_id=:user AND created_at>=:date").param("user",user).param("date",Instant.now().minusSeconds(86400).atOffset(ZoneOffset.UTC)).query(Long.class).single();
        if(count>=20)throw new IllegalArgumentException("At most 20 correction submissions per day.");
        UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO accountability_corrections(id,user_id,target_type,target_id,evidence_url,correction_text,created_at) VALUES(:id,:user,:type,:target,:url,:text,:now)").param("id",id).param("user",user).param("type",value.type()).param("target",value.targetId()).param("url",value.evidenceUrl()).param("text",value.text()).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();return own(user,id);
    }
    private static final String COLUMNS="id,target_type AS type,target_id,evidence_url,correction_text AS text,status,created_at,review_note,reviewed_at";
    public Correction own(UUID user,UUID id){return jdbc.sql("SELECT "+COLUMNS+" FROM accountability_corrections WHERE id=:id AND user_id=:user").param("id",id).param("user",user).query(Correction.class).optional().orElseThrow(()->new NoSuchElementException("Correction not found."));}
    public Page<Correction> queue(int page){if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");long count=jdbc.sql("SELECT COUNT(*) FROM accountability_corrections").query(Long.class).single();var rows=jdbc.sql("SELECT "+COLUMNS+" FROM accountability_corrections ORDER BY created_at DESC,id LIMIT 10 OFFSET :offset").param("offset",(page-1)*10).query(Correction.class).list();return new Page<>(rows,page,10,count);}
    @Transactional public void review(UUID id,UUID actor,String status,String note){if(status==null||!List.of("REVIEWED","DISMISSED").contains(status))throw new IllegalArgumentException("Choose a review outcome.");GovernmentValidation.text(note,"review note",4000);int changed=jdbc.sql("UPDATE accountability_corrections SET status=:status,reviewer_id=:actor,review_note=:note,reviewed_at=:now WHERE id=:id AND status='OPEN'").param("status",status).param("actor",actor).param("note",note).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("id",id).update();if(changed!=1)throw new IllegalStateException("Correction already reviewed or no longer available.");}
}
