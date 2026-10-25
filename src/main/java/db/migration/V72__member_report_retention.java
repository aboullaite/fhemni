package db.migration;

import java.time.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V72__member_report_retention extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var c=context.getConnection();
        try(var s=c.createStatement()){s.execute("ALTER TABLE member_analysis_jobs ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE");s.execute("CREATE INDEX member_analysis_expiry ON member_analysis_jobs(expires_at)");}
        try(var s=c.createStatement();var rows=s.executeQuery("SELECT id,updated_at FROM member_analysis_jobs WHERE state IN ('READY','FAILED')");
            var update=c.prepareStatement("UPDATE member_analysis_jobs SET expires_at=? WHERE id=?")) {
            while(rows.next()){
                update.setObject(1,rows.getObject(2,OffsetDateTime.class).toInstant().plus(Duration.ofDays(30)).atOffset(ZoneOffset.UTC));
                update.setObject(2,rows.getObject(1));update.executeUpdate();
            }
        }
    }
}
