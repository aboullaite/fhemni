package dev.maboullaite.fhemni.following;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
@Repository
public class FollowRepository {
    private final JdbcClient jdbc;
    public FollowRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    public record FollowTarget(String type,String id) {}
    public void lock(UUID user){jdbc.sql("SELECT id FROM app_users WHERE id=:id FOR UPDATE").param("id",user).query(UUID.class).single();}
    public List<FollowTarget> list(UUID user){return jdbc.sql("SELECT target_type,target_id FROM accountability_follows WHERE user_id=:user ORDER BY created_at,target_type,target_id").param("user",user).query((rs,n)->new FollowTarget(rs.getString("target_type"),rs.getString("target_id"))).list();}
    public void change(UUID user,FollowTarget target,boolean enabled){
        if(enabled)jdbc.sql("INSERT INTO accountability_follows(user_id,target_type,target_id,created_at) SELECT :user,:type,:id,:now WHERE NOT EXISTS(SELECT 1 FROM accountability_follows WHERE user_id=:user AND target_type=:type AND target_id=:id)").param("user",user).param("type",target.type()).param("id",target.id()).param("now",Instant.now().atOffset(ZoneOffset.UTC)).update();
        else jdbc.sql("DELETE FROM accountability_follows WHERE user_id=:user AND target_type=:type AND target_id=:id").param("user",user).param("type",target.type()).param("id",target.id()).update();
    }
}
