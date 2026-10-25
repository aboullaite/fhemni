package dev.maboullaite.fhemni.memberanalysis;

import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Non-content receipts survive report expiry. Admission callers hold the account row lock. */
@Repository
public class MemberAllowanceRepository {
    private final JdbcClient jdbc;
    public MemberAllowanceRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public long used(UUID user,String kind,MemberAllowanceWindow window) {
        return jdbc.sql("SELECT COUNT(*) FROM member_allowance_receipts WHERE user_id=:user AND kind=:kind AND charged=TRUE AND requested_at>=:start AND requested_at<:end")
            .param("user",user).param("kind",kind).param("start",window.start().atOffset(ZoneOffset.UTC))
            .param("end",window.end().atOffset(ZoneOffset.UTC)).query(Long.class).single();
    }
    public void reserve(UUID receipt,UUID job,UUID user,String kind,int limit,Instant now) {
        var window=MemberAllowanceWindow.current(Clock.fixed(now,ZoneOffset.UTC));
        if(used(user,kind,window)>=limit)throw new IllegalStateException(kind.equals("VIDEO")?"MONTHLY_ALLOWANCE_REACHED":"MONTHLY_CHAT_ALLOWANCE_REACHED");
        jdbc.sql("INSERT INTO member_allowance_receipts(id,job_id,user_id,kind,charged,requested_at) VALUES(:id,:job,:user,:kind,TRUE,:now)")
            .param("id",receipt).param("job",job).param("user",user).param("kind",kind).param("now",now.atOffset(ZoneOffset.UTC)).update();
    }
    public void refundVideo(UUID job) {
        jdbc.sql("UPDATE member_allowance_receipts SET charged=FALSE WHERE job_id=:job AND kind='VIDEO'").param("job",job).update();
    }
}
