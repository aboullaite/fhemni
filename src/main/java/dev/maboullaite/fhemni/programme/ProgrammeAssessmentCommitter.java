package dev.maboullaite.fhemni.programme;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.maboullaite.fhemni.gemini.ProgrammeIntelligenceGateway.ExtractedPromise;
import dev.maboullaite.fhemni.programme.ProgrammeFactCheckService.FactCheckResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProgrammeAssessmentCommitter {

    private final ProgrammeAssessmentJobRepository jobs;
    private final PartyProgrammeService programmes;

    ProgrammeAssessmentCommitter(
            ProgrammeAssessmentJobRepository jobs,
            PartyProgrammeService programmes) {
        this.jobs = jobs;
        this.programmes = programmes;
    }

    @Transactional
    void saveAndComplete(
            ProgrammeAssessmentJobRepository.Lease lease,
            UUID programmeId,
            List<ExtractedPromise> requested,
            FactCheckResult result,
            List<UUID> promiseIds,
            Instant now) {
        jobs.requireOwnedLease(lease, now);
        programmes.saveGeneratedAssessments(programmeId, requested, result);
        jobs.completeItems(lease, promiseIds, now);
    }
}
