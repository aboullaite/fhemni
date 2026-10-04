package dev.maboullaite.fhemni.memberanalysis;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import dev.maboullaite.fhemni.gemini.VideoIntelligenceGateway;
import dev.maboullaite.fhemni.cost.AiUsageGuard;
@Service
public class MemberAnalysisPolicy {
    private final MemberAnalysisRepository repository;private final VideoEligibilityGateway videos;private final VideoIntelligenceGateway gateway;private final AiUsageGuard budget;
    private final boolean enabled;private final int userLimit,premiumLimit,maxSeconds;
    public MemberAnalysisPolicy(MemberAnalysisRepository repository,VideoEligibilityGateway videos,VideoIntelligenceGateway gateway,AiUsageGuard budget,@Value("${fhemni.member-analysis.enabled:false}")boolean enabled,@Value("${fhemni.member-analysis.user-monthly:1}")int userLimit,@Value("${fhemni.member-analysis.premium-monthly:5}")int premiumLimit,@Value("${fhemni.member-analysis.max-seconds:1800}")int maxSeconds){if(userLimit<1||premiumLimit<1||maxSeconds<1)throw new IllegalArgumentException("Member limits must be positive.");this.repository=repository;this.videos=videos;this.gateway=gateway;this.budget=budget;this.enabled=enabled;this.userLimit=userLimit;this.premiumLimit=premiumLimit;this.maxSeconds=maxSeconds;}
    public record Policy(boolean enabled,String unavailableReason,int monthlyLimit,long remaining,int maxSeconds,int concurrency,boolean active,Instant resetsAt) {}
    public Policy forUser(UUID user){int limit=repository.role(user).equals("PREMIUM")?premiumLimit:userLimit;var month=YearMonth.now(ZoneOffset.UTC);boolean available=enabled&&videos.configured()&&gateway.live()&&budget.analysisEnabled();return new Policy(available,available?null:"PROCESSING_UNAVAILABLE",limit,Math.max(0,limit-repository.used(user,month)),maxSeconds,1,repository.active(user),month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));}
}
