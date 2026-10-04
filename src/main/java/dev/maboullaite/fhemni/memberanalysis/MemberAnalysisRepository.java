package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;
import java.util.*;
import java.sql.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.model.*;
import tools.jackson.databind.ObjectMapper;

@Repository
public class MemberAnalysisRepository {
    private final JdbcClient jdbc;private final ObjectMapper mapper;
    public MemberAnalysisRepository(JdbcClient jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public void lockOwner(UUID user){jdbc.sql("SELECT id FROM app_users WHERE id=:user FOR UPDATE").param("user",user).query(UUID.class).single();}
    public String role(UUID user){return jdbc.sql("SELECT role FROM app_users WHERE id=:user").param("user",user).query(String.class).single();}
    public long used(UUID user,YearMonth month){return jdbc.sql("SELECT COUNT(*) FROM member_analysis_jobs WHERE user_id=:user AND allowance_month=:month AND allowance_charged=TRUE").param("user",user).param("month",month.atDay(1)).query(Long.class).single();}
    public boolean active(UUID user){return jdbc.sql("SELECT COUNT(*) FROM member_analysis_active WHERE user_id=:user").param("user",user).query(Long.class).single()>0;}
    public Optional<MemberJob> byKey(UUID user,String key){return jdbc.sql("SELECT * FROM member_analysis_jobs WHERE user_id=:user AND idempotency_key=:key").param("user",user).param("key",key).query(this::map).optional();}
    public MemberJob owner(UUID id,UUID user){return jdbc.sql("SELECT * FROM member_analysis_jobs WHERE id=:id AND user_id=:user").param("id",id).param("user",user).query(this::map).optional().orElseThrow(()->new NoSuchElementException("Analysis not found."));}
    public MemberJob create(UUID id,UUID user,String key,String url,String video,OutputLanguage language,long seconds,UUID reservation,VideoIntelligenceGateway gateway){var now=Instant.now().atOffset(ZoneOffset.UTC);jdbc.sql("INSERT INTO member_analysis_jobs(id,user_id,idempotency_key,video_url,video_id,language,state,allowance_month,duration_seconds,usage_reservation_id,model,prompt_version,fact_check_model,fact_check_prompt_version,credential_version,created_at,updated_at) VALUES(:id,:user,:key,:url,:video,:language,'QUEUED',:month,:seconds,:reservation,:model,:prompt,:facts,:factsPrompt,:credential,:now,:now)").param("id",id).param("user",user).param("key",key).param("url",url).param("video",video).param("language",language.name()).param("month",YearMonth.now(ZoneOffset.UTC).atDay(1)).param("seconds",seconds).param("reservation",reservation).param("model",gateway.model()).param("prompt",gateway.promptVersion()).param("facts",gateway.factCheckModel()).param("factsPrompt",gateway.factCheckPromptVersion()).param("credential",gateway.credentialVersion()).param("now",now).update();jdbc.sql("INSERT INTO member_analysis_active(user_id,job_id) VALUES(:user,:id)").param("user",user).param("id",id).update();return owner(id,user);}
    @Transactional public void failQueued(UUID id,UUID user){lockOwner(user);int changed=jdbc.sql("UPDATE member_analysis_jobs SET state='FAILED',allowance_charged=FALSE,error_code='PROCESSING_FAILED',updated_at=:now WHERE id=:id AND user_id=:user AND state='QUEUED'").param("id",id).param("user",user).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();if(changed==1)jdbc.sql("DELETE FROM member_analysis_active WHERE job_id=:id").param("id",id).update();}
    private MemberJob map(ResultSet row,int ignored)throws SQLException{String json=row.getString("report_json");return new MemberJob(row.getObject("id",UUID.class),row.getString("video_url"),row.getString("video_id"),OutputLanguage.valueOf(row.getString("language")).code(),row.getString("state"),row.getObject("created_at",OffsetDateTime.class).toInstant(),row.getObject("updated_at",OffsetDateTime.class).toInstant(),json==null?null:mapper.readValue(json,VideoReport.class),row.getString("error_code"));}
}
