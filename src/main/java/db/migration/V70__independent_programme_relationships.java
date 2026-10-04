package db.migration;

import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Extend the original unnamed check on both PostgreSQL and the H2 test database. */
public class V70__independent_programme_relationships extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        var names=new ArrayList<String>();
        try(var query=connection.createStatement();var rows=query.executeQuery("""
            SELECT tc.constraint_name FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc ON cc.constraint_catalog=tc.constraint_catalog
              AND cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name
            WHERE LOWER(tc.table_name)='government_subjects' AND tc.constraint_type='CHECK'
              AND LOWER(cc.check_clause) LIKE '%subject_type%' AND LOWER(cc.check_clause) LIKE '%commitment%'
            """)) {while(rows.next())names.add(rows.getString(1));}
        if(names.size()!=1)throw new IllegalStateException("Expected the existing government subject type check.");
        String quote=connection.getMetaData().getIdentifierQuoteString();
        try(var statement=connection.createStatement()) {
            statement.execute("ALTER TABLE government_subjects DROP CONSTRAINT "+quote+names.getFirst().replace(quote,quote+quote)+quote);
            statement.execute("ALTER TABLE government_subjects ADD CONSTRAINT government_subject_type_allowed CHECK(subject_type IN ('MANDATE','COMMITMENT','MEASURE','LINK'))");
        }
    }
}
