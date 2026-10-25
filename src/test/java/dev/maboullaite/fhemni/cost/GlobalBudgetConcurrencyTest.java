package dev.maboullaite.fhemni.cost;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@SpringBootTest(properties={"fhemni.gemini.api-key=","fhemni.cost-control.analysis-enabled=true","fhemni.cost-control.max-daily-analyses=1","spring.datasource.url=jdbc:h2:mem:global-budget-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class GlobalBudgetConcurrencyTest {
    @Autowired AiUsageRepository repository;@Autowired AiUsageGuard first;@Autowired PlatformTransactionManager transactions;
    @Test void independentInstancesShareOneDatabaseCeiling(){var second=new AiUsageGuard(repository,true,false,1,10,10,10,10,1000);var tx=new TransactionTemplate(transactions);var guards=List.of(first,second);var tasks=guards.stream().map(guard->CompletableFuture.supplyAsync(()->{try{return tx.execute(status->guard.reserveAnalysis(UUID.randomUUID(),"fixture"));}catch(AiBudgetExceededException full){return null;}})).toList();assertThat(tasks.stream().map(CompletableFuture::join).filter(Objects::nonNull)).hasSize(1);}
}
