package dev.maboullaite.fhemni.government;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class GovernmentMandateBatchTest {
    @Test void repeatedEnsureInsideOneImportTransactionDoesNotDuplicateTheMandate() {
        var dataSource=new DriverManagerDataSource("jdbc:h2:mem:batch-mandate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","sa","");
        var jdbc=JdbcClient.create(dataSource);
        jdbc.sql("CREATE TABLE government_mandates(id VARCHAR(40) PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL)").update();
        var repository=new GovernmentRepository(jdbc,JsonMapper.builder().build());
        new TransactionTemplate(new JdbcTransactionManager(dataSource)).executeWithoutResult(status->{
            repository.ensureMandate("2026-2031");
            assertEquals(1,jdbc.sql("SELECT COUNT(*) FROM government_mandates").query(Integer.class).single());
            repository.ensureMandate("2026-2031");
            repository.ensureMandate("2026-2031");
        });
        assertEquals(1,jdbc.sql("SELECT COUNT(*) FROM government_mandates").query(Integer.class).single());
    }
}
