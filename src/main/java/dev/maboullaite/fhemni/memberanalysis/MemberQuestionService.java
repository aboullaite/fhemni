package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import dev.maboullaite.fhemni.model.*;
import dev.maboullaite.fhemni.cost.*;
import dev.maboullaite.fhemni.gemini.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class MemberQuestionService {
    private final MemberAnalysisAccess access;
    private final MemberAnalysisRepository repository;
    private final MemberAnalysisService service;
    private final AiUsageRepository usageRepository;
    private final AiUsageGuard usage;
    private final VideoIntelligenceGateway gateway;
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    public MemberQuestionService(MemberAnalysisAccess access,MemberAnalysisRepository repository,MemberAnalysisService service,AiUsageRepository usageRepository,AiUsageGuard usage,VideoIntelligenceGateway gateway,JdbcClient jdbc,ObjectMapper mapper,PlatformTransactionManager manager) {
        this.access=access;this.repository=repository;this.service=service;this.usageRepository=usageRepository;
        this.usage=usage;this.gateway=gateway;this.jdbc=jdbc;this.mapper=mapper;tx=new TransactionTemplate(manager);
    }
    public List<FollowUpAnswer> answers(UUID id,UUID user){access.requireOwner(id,user);return storedAnswers(id);}
    public String status(UUID id,UUID user) {
        access.requireOwner(id,user);
        return jdbc.sql("SELECT last_question_state,lease_until FROM member_analysis_conversations WHERE job_id=:id")
            .param("id",id).query((r,n)->{
                String state=r.getString(1);var until=r.getObject(2,OffsetDateTime.class);
                return "PROCESSING".equals(state)&&(until==null||!until.toInstant().isAfter(Instant.now()))?"UNCERTAIN":state;
            }).optional().orElse("READY");
    }
    private List<FollowUpAnswer> storedAnswers(UUID id) {
        return jdbc.sql("SELECT answers_json FROM member_analysis_conversations WHERE job_id=:id").param("id",id)
            .query(String.class).optional().map(json->Arrays.asList(mapper.readValue(json,FollowUpAnswer[].class))).orElse(List.of());
    }
    private record Claim(UUID token,AiUsageGuard.Reservation reservation,String interaction,MemberJob job,List<FollowUpAnswer> previous) {}
    public FollowUpAnswer ask(UUID id,UUID user,String question,QuestionMode mode) {
        access.requireOwner(id,user);
        if(question==null||question.isBlank()||question.length()>600)throw new IllegalArgumentException("Write a question under 600 characters.");
        QuestionMode resolved=mode==null?QuestionMode.VIDEO:mode;
        Claim claim=tx.execute(status->{
            usageRepository.lockGlobal();repository.lockOwner(user);
            var job=access.requireOwner(id,user);
            if(!job.state().equals("READY")||job.report()==null)throw new IllegalStateException("Wait for a completed report.");
            if(!service.compatible(repository.identity(id).orElseThrow()))throw new IllegalStateException("Question context unavailable.");
            var previous=storedAnswers(id);
            long count=jdbc.sql("SELECT COUNT(*) FROM member_analysis_conversations WHERE job_id=:id").param("id",id).query(Long.class).single();
            if(count==0)jdbc.sql("INSERT INTO member_analysis_conversations(job_id,interaction_id,answers_json) VALUES(:id,:interaction,'[]')")
                .param("id",id).param("interaction",repository.interaction(id)).update();
            var row=jdbc.sql("SELECT question_count,lease_token,lease_until FROM member_analysis_conversations WHERE job_id=:id FOR UPDATE")
                .param("id",id).query((r,n)->new Object[]{r.getInt(1),r.getObject(2),r.getObject(3,OffsetDateTime.class)}).single();
            if((int)row[0]>=10)throw new IllegalStateException("Conversation limit reached.");
            if(row[1]!=null&&row[2]!=null&&((OffsetDateTime)row[2]).toInstant().isAfter(Instant.now()))
                throw new IllegalStateException("A question is still processing.");
            String interaction=jdbc.sql("SELECT interaction_id FROM member_analysis_conversations WHERE job_id=:id")
                .param("id",id).query(String.class).optional().orElse(null);
            if(interaction==null)throw new IllegalStateException("Question context unavailable.");
            // Only this explicit request can replace an expired claim. Its old completion remains fenced out.
            UUID token=UUID.randomUUID();
            jdbc.sql("UPDATE member_analysis_conversations SET lease_token=:token,lease_until=:until,last_question_state='PROCESSING',question_count=question_count+1 WHERE job_id=:id")
                .param("token",token).param("until",Instant.now().plusSeconds(300).atOffset(ZoneOffset.UTC)).param("id",id).update();
            return new Claim(token,usage.reserveQuestion(id,user,resolved==QuestionMode.CHECK?AiOperation.CHAT_CHECK:AiOperation.CHAT_VIDEO,gateway.model()),interaction,job,previous);
        });
        boolean providerRecorded=false;
        try {
            var result=gateway.ask(claim.interaction(),question.strip(),resolved,OutputLanguage.fromCode(claim.job().language()),claim.job().report());
            usage.succeeded(claim.reservation(),result.usage());providerRecorded=true;
            var answer=new FollowUpAnswer(question.strip(),resolved,result.answer(),result.sources(),Instant.now());
            tx.executeWithoutResult(status->{
                repository.lockOwner(user);var all=new ArrayList<>(claim.previous());all.add(answer);
                int changed=jdbc.sql("UPDATE member_analysis_conversations SET answers_json=:json,interaction_id=:interaction,lease_token=NULL,lease_until=NULL,last_question_state='ANSWERED' WHERE job_id=:id AND lease_token=:token AND lease_until>:now")
                    .param("json",mapper.writeValueAsString(all)).param("interaction",result.interactionId()).param("id",id)
                    .param("token",claim.token()).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();
                if(changed!=1)throw new IllegalStateException("Question completion could not be confirmed.");
            });
            return answer;
        } catch(RuntimeException failure) {
            String state=failure instanceof GeminiApiException api&&api.upstreamStatus()!=null
                &&api.upstreamStatus()>=400&&api.upstreamStatus()<500&&api.upstreamStatus()!=408?"FAILED":"UNCERTAIN";
            try {
                if(!providerRecorded)usage.failed(claim.reservation(),failure instanceof GeminiApiException api?api.usage():AiUsage.empty());
            } finally {
                tx.executeWithoutResult(status->{
                    repository.lockOwner(user);
                    jdbc.sql("UPDATE member_analysis_conversations SET lease_token=NULL,lease_until=NULL,last_question_state=:state WHERE job_id=:id AND lease_token=:token")
                        .param("state",state).param("id",id).param("token",claim.token()).update();
                });
            }
            throw new IllegalStateException("The question could not be completed. Your report is still available.");
        }
    }
}
