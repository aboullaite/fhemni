package dev.maboullaite.fhemni.memberanalysis;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.maboullaite.fhemni.video.YouTubeUrlParser;
import dev.maboullaite.fhemni.model.OutputLanguage;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.cost.*;
@Service
public class MemberAnalysisAdmission {
    private final MemberAnalysisRepository repository;private final MemberAnalysisPolicy policy;private final VideoEligibilityGateway videos;private final VideoIntelligenceGateway gateway;private final YouTubeUrlParser parser;private final AiUsageGuard budget;private final AiUsageRepository usage;
    public MemberAnalysisAdmission(MemberAnalysisRepository repository,MemberAnalysisPolicy policy,VideoEligibilityGateway videos,VideoIntelligenceGateway gateway,YouTubeUrlParser parser,AiUsageGuard budget,AiUsageRepository usage){this.repository=repository;this.policy=policy;this.videos=videos;this.gateway=gateway;this.parser=parser;this.budget=budget;this.usage=usage;}
    @Transactional public MemberJob submit(UUID user,String url,String languageCode,String key){
        if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new IllegalArgumentException("A valid idempotency key is required.");
        var parsed=parser.parse(url);var language=OutputLanguage.fromCode(languageCode);
        var existing=repository.byKey(user,key);if(existing.isPresent())return same(existing.get(),parsed.canonicalUrl(),language);
        var before=policy.forUser(user);if(!before.enabled())throw new IllegalStateException("PROCESSING_UNAVAILABLE");
        var eligible=videos.inspect(parsed.videoId());
        if(eligible.live())throw new IllegalArgumentException("LIVE_UNSUPPORTED");
        if(!eligible.available())throw new IllegalArgumentException(java.util.Set.of("VIDEO_UNAVAILABLE","VERIFICATION_UNAVAILABLE","DURATION_UNAVAILABLE").contains(java.util.Objects.toString(eligible.reason(),""))?eligible.reason():"VIDEO_UNAVAILABLE");
        if(eligible.durationSeconds()==null||eligible.durationSeconds()<1)throw new IllegalArgumentException("DURATION_UNAVAILABLE");
        if(eligible.durationSeconds()>before.maxSeconds())throw new IllegalArgumentException("VIDEO_TOO_LONG");
        usage.lockGlobal();repository.lockOwner(user);
        existing=repository.byKey(user,key);if(existing.isPresent())return same(existing.get(),parsed.canonicalUrl(),language);
        var current=policy.forUser(user);if(!current.enabled())throw new IllegalStateException("PROCESSING_UNAVAILABLE");if(current.active())throw new IllegalStateException("ANALYSIS_ALREADY_ACTIVE");if(current.remaining()<1)throw new IllegalStateException("MONTHLY_ALLOWANCE_REACHED");
        var id=UUID.randomUUID();var reservation=budget.reserveAnalysis(id,gateway.model());
        repository.chargeVideo(id,user,current.monthlyLimit());
        return repository.create(id,user,key,parsed.canonicalUrl(),parsed.videoId(),language,eligible.durationSeconds(),reservation.id(),gateway);
    }
    private MemberJob same(MemberJob job,String url,OutputLanguage language){if(!job.videoUrl().equals(url)||!job.language().equals(language.code()))throw new IllegalArgumentException("Idempotency key was already used for different input.");return job;}
}
