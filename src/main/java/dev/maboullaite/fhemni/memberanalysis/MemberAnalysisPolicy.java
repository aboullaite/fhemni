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
    private final MemberAllowanceRepository allowances;
    private final boolean enabled;private final int userLimit,premiumLimit,maxSeconds;
    public MemberAnalysisPolicy(MemberAnalysisRepository repository,MemberAllowanceRepository allowances,VideoEligibilityGateway videos,VideoIntelligenceGateway gateway,AiUsageGuard budget,@Value("${fhemni.member-analysis.enabled:false}")boolean enabled,@Value("${fhemni.member-analysis.user-monthly:10}")int userLimit,@Value("${fhemni.member-analysis.premium-monthly:10}")int premiumLimit,@Value("${fhemni.member-analysis.max-seconds:1800}")int maxSeconds){if(userLimit<1||premiumLimit<1||maxSeconds<1)throw new IllegalArgumentException("Member limits must be positive.");this.repository=repository;this.allowances=allowances;this.videos=videos;this.gateway=gateway;this.budget=budget;this.enabled=enabled;this.userLimit=userLimit;this.premiumLimit=premiumLimit;this.maxSeconds=maxSeconds;}
    public record Policy(boolean enabled,String unavailableReason,int monthlyLimit,long remaining,int maxSeconds,int concurrency,boolean active,Instant resetsAt,int chatMonthlyLimit,long chatRemaining,Instant chatResetsAt,int retentionDays,boolean chatEnabled) {}
    public Policy forUser(UUID user){int limit=repository.role(user).equals("PREMIUM")?premiumLimit:userLimit;var window=MemberAllowanceWindow.current(Clock.systemUTC());boolean available=enabled&&videos.configured()&&gateway.live()&&budget.analysisEnabled();return new Policy(available,available?null:"PROCESSING_UNAVAILABLE",limit,Math.max(0,limit-allowances.used(user,"VIDEO",window)),maxSeconds,1,repository.active(user),window.end(),20,Math.max(0,20-allowances.used(user,"QUESTION",window)),window.end(),30,gateway.live()&&budget.chatEnabled());}
}
