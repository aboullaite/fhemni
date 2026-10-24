package dev.maboullaite.fhemni.government;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.util.List;
import org.springframework.stereotype.Repository;
@Repository
public class CommitmentRepository {
    private final GovernmentRepository repository;
    private final ProgrammeDiscovery discovery;
    public CommitmentRepository(GovernmentRepository repository,ProgrammeDiscovery discovery){this.repository=repository;this.discovery=discovery;}
    public List<CommitmentView> published(String mandate) {
        return repository.publishedPayloads(mandate,SubjectType.COMMITMENT).stream().map(json->repository.decode(json,CommitmentView.class)).map(discovery::project).toList();
    }
}
