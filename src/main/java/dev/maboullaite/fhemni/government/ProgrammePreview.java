package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Explicitly local-only, all-or-nothing research loading. Never a production Flyway migration. */
@Component
@Profile("programme-preview")
public class ProgrammePreview implements ApplicationRunner {
    private final ProgrammePreviewData.Snapshot snapshot;
    private final GovernmentRepository repository;
    private final GovernmentEvidenceRepository evidence;
    private final GovernmentEditorialService editorial;
    private final TransactionTemplate transaction;
    private final GovernmentPublicationEvents events;
    public ProgrammePreview(Environment environment,ObjectMapper mapper,GovernmentRepository repository,
            GovernmentEvidenceRepository evidence,GovernmentEditorialService editorial,
            org.springframework.transaction.PlatformTransactionManager transactionManager,GovernmentPublicationEvents events,
            dev.maboullaite.fhemni.identity.AuthProperties auth) throws IOException {
        ProgrammePreviewData.requireLocal(environment.getProperty("spring.datasource.url"),environment.getProperty("server.address"),Arrays.asList(environment.getActiveProfiles()));
        ProgrammePreviewData.requireTestIdentity(auth);
        snapshot=ProgrammePreviewData.load(Path.of(environment.getProperty("fhemni.programme-preview.directory","data/government/2026-2031")),mapper);
        this.repository=repository;this.evidence=evidence;this.editorial=editorial;
        this.transaction=new TransactionTemplate(transactionManager);this.events=events;
    }
    public ProgrammePreviewData.Snapshot snapshot(){return snapshot;}
    @Override public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status->importSnapshot());
    }
    private void importSnapshot() {
        var existing=repository.publishedPayloads("2026-2031",SubjectType.COMMITMENT);
        var mandate=repository.published(SubjectType.MANDATE,"2026-2031",MandateView.class);
        if(mandate.isPresent()) {
            // Never overwrite a user's local edits or silently complete a partial import.
            if(!matchesExisting(snapshot,mandate.get(),existing.stream().map(json->repository.decode(json,CommitmentView.class)).toList(),events))
                throw new IllegalStateException("The local preview has changed. Keep that database; use an explicit reviewed migration instead of overwriting it.");
            return;
        }
        if(!existing.isEmpty())throw new IllegalStateException("Refusing to import into a partial local preview.");
        repository.ensureMandate("2026-2031");
        for(var source:snapshot.sources())evidence.save("2026-2031",source,
                source.archivedSha256()==null?null:snapshot.pdf());
        publish(SubjectType.MANDATE,"2026-2031",snapshot.mandate());
        for(var row:snapshot.commitments())publish(SubjectType.COMMITMENT,row.slug(),row);
    }
    static boolean matchesExisting(ProgrammePreviewData.Snapshot snapshot,MandateView mandate,List<CommitmentView> rows,GovernmentPublicationEvents events) {
        if(!events.materialHash(snapshot.mandate()).equals(events.materialHash(mandate)))return false;
        var expected=new HashMap<UUID,String>();
        snapshot.commitments().forEach(row->expected.put(row.id(),events.materialHash(row)));
        return rows.size()==expected.size()&&rows.stream().allMatch(row->Objects.equals(expected.get(row.id()),events.materialHash(row)));
    }
    private void publish(SubjectType type,String slug,Object payload) {
        UUID actor=ProgrammePreviewData.id("local-research-importer");
        var draft=repository.createDraft("2026-2031",type,slug,payload,actor,
                "LOCAL RESEARCH PREVIEW ONLY. Programme PDF authenticity unresolved; no delivery assessment. Not a production editorial sign-off.");
        var reviewed=editorial.review(draft.id(),draft.rowVersion(),actor);
        editorial.publish(reviewed.id(),reviewed.rowVersion(),actor);
    }
}
