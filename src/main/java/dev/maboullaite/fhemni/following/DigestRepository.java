package dev.maboullaite.fhemni.following;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
public class DigestRepository {
    private final JdbcClient jdbc;private final FollowRepository locks;
    public DigestRepository(JdbcClient jdbc,FollowRepository locks){this.jdbc=jdbc;this.locks=locks;}
    public record Consent(boolean enabled,String locale,String consentEmail,UUID consentVersion) {}
    public record Claim(UUID id,UUID userId,LocalDate periodStart,UUID consentVersion,UUID leaseToken) {}
    public Consent consent(UUID user){return jdbc.sql("SELECT enabled,locale,consent_email,consent_version FROM accountability_digest_preferences WHERE user_id=:user").param("user",user).query(Consent.class).optional().orElse(new Consent(false,"ar",null,null));}
    public String verifiedEmail(UUID user){return jdbc.sql("SELECT u.email FROM app_users u WHERE u.id=:user AND u.email IS NOT NULL AND EXISTS(SELECT 1 FROM external_identities i WHERE i.user_id=u.id AND i.email_verified=TRUE AND i.last_login_at=u.last_login_at)").param("user",user).query(String.class).optional().orElse(null);}
    public void lock(UUID user){locks.lock(user);}
    public void save(UUID user,boolean enabled,String locale,String email){var version=UUID.randomUUID();int changed=jdbc.sql("UPDATE accountability_digest_preferences SET enabled=:enabled,locale=:locale,consent_email=:email,consent_version=:version,updated_at=:now WHERE user_id=:user").param("enabled",enabled).param("locale",locale).param("email",email).param("version",version).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("user",user).update();if(changed==0)jdbc.sql("INSERT INTO accountability_digest_preferences(user_id,enabled,locale,consent_email,consent_version,updated_at) VALUES(:user,:enabled,:locale,:email,:version,:now)").param("enabled",enabled).param("locale",locale).param("email",email).param("version",version).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("user",user).update();}
    public List<UUID> enabledUsers(){return jdbc.sql("SELECT user_id FROM accountability_digest_preferences WHERE enabled=TRUE").query(UUID.class).list();}
    public long count(UUID user,LocalDate start){return jdbc.sql("SELECT COUNT(*) FROM accountability_digest_outbox WHERE user_id=:user AND period_start=:start").param("user",user).param("start",start).query(Long.class).single();}
    public void reserve(UUID user,LocalDate start,UUID version){if(count(user,start)>0)return;jdbc.sql("INSERT INTO accountability_digest_outbox(id,user_id,period_start,consent_version,state,created_at) VALUES(:id,:user,:start,:version,'PENDING',:now)").param("id",UUID.randomUUID()).param("user",user).param("start",start).param("version",version).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();}
    @Transactional public Optional<Claim> claim(Instant now){
        // A crash after the send boundary is never retried automatically.
        jdbc.sql("UPDATE accountability_digest_outbox SET state='UNKNOWN',completed_at=:now WHERE state='SENDING' AND lease_until<:now").param("now",now.atOffset(ZoneOffset.UTC)).update();
        var ids=jdbc.sql("SELECT id FROM accountability_digest_outbox WHERE state='PENDING' OR (state='LEASED' AND lease_until<:now) ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED").param("now",now.atOffset(ZoneOffset.UTC)).query(UUID.class).list();if(ids.isEmpty())return Optional.empty();
        var id=ids.getFirst();var token=UUID.randomUUID();jdbc.sql("UPDATE accountability_digest_outbox SET state='LEASED',lease_token=:token,lease_until=:until WHERE id=:id").param("token",token).param("until",now.plusSeconds(300).atOffset(ZoneOffset.UTC)).param("id",id).update();
        return jdbc.sql("SELECT id,user_id,period_start,consent_version,lease_token FROM accountability_digest_outbox WHERE id=:id").param("id",id).query(Claim.class).optional();
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW) public boolean beginSend(Claim claim){return jdbc.sql("UPDATE accountability_digest_outbox SET state='SENDING' WHERE id=:id AND lease_token=:token AND state='LEASED' AND lease_until>:now").param("id",claim.id()).param("token",claim.leaseToken()).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update()==1;}
    public void finish(Claim claim,String state){jdbc.sql("UPDATE accountability_digest_outbox SET state=:state,completed_at=:now WHERE id=:id AND lease_token=:token AND state IN ('LEASED','SENDING')").param("state",state).param("now",Instant.now().atOffset(ZoneOffset.UTC)).param("id",claim.id()).param("token",claim.leaseToken()).update();}
    public String state(UUID id){return jdbc.sql("SELECT state FROM accountability_digest_outbox WHERE id=:id").param("id",id).query(String.class).single();}
}
