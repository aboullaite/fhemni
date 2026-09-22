package dev.maboullaite.fhemni.civic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "fhemni.gemini.api-key=",
        "fhemni.civic.shares.cleanup-claim-lease=PT0S",
        "spring.datasource.url=jdbc:h2:mem:civic-priority-share-cleanup-retry-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class CivicPriorityShareCleanupRetryIntegrationTest {

    @Autowired
    private CivicPriorityShareService shares;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private CivicPriorityShareImageStorage storage;

    @Test
    void retriesAnExpiredObjectWhenTheFirstStorageDeletionFails() throws Exception {
        String token = "a".repeat(32);
        String objectKey = "civic-priority-shares/ar/compass/retry.png";
        jdbc.sql("""
                        INSERT INTO civic_priority_shares (
                            id, share_token, share_kind, language, image_object_key,
                            image_sha256, created_at
                        ) VALUES (
                            :id, :token, 'COMPASS', 'ar', :objectKey, :digest, :createdAt
                        )
                        """)
                .param("id", UUID.randomUUID())
                .param("token", token)
                .param("objectKey", objectKey)
                .param("digest", "b".repeat(64))
                .param("createdAt", Instant.now().minus(31, ChronoUnit.DAYS))
                .update();
        when(storage.delete(objectKey))
                .thenThrow(new IOException("temporary GCS failure"))
                .thenReturn(true);

        shares.deleteExpiredShares();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM civic_priority_share_deletions WHERE share_token = :token")
                .param("token", token)
                .query(Long.class)
                .single()).isOne();
        shares.deleteExpiredShares();

        verify(storage, times(2)).delete(objectKey);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM civic_priority_shares WHERE share_token = :token")
                .param("token", token)
                .query(Long.class)
                .single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM civic_priority_share_deletions WHERE share_token = :token")
                .param("token", token)
                .query(Long.class)
                .single()).isZero();
    }
}
