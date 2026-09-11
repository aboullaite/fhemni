package dev.maboullaite.fhemni.programme.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:programme-media-worker-context-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "fhemni.programme-media.worker-enabled=true",
        "fhemni.programme-media.storage=local",
        "fhemni.programme-media.dispatch-interval-ms=3600000",
        "fhemni.programme-media.lease-renew-interval-ms=3600000"
})
class ProgrammeMediaWorkerContextTest {

    @Autowired
    private ProgrammeMediaWorker worker;

    @Test
    void workerStartsWhenEnabled() {
        assertThat(worker).isNotNull();
    }
}
