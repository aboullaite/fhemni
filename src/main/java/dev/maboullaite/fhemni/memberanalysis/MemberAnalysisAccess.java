package dev.maboullaite.fhemni.memberanalysis;
import java.util.UUID;
import org.springframework.stereotype.Service;
@Service
public class MemberAnalysisAccess {
    private final MemberAnalysisRepository repository;
    public MemberAnalysisAccess(MemberAnalysisRepository repository){this.repository=repository;}
    public MemberJob requireOwner(UUID jobId,UUID user){return repository.owner(jobId,user);}
}
