package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public final class GovernmentValidation {
    private GovernmentValidation() {}
    public static void text(String value,String field,int max) {
        if(value==null||value.isBlank()||value.length()>max)throw new IllegalArgumentException("Invalid "+field+".");
    }
    public static void translations(LocalText value) {
        if(value==null)throw new IllegalArgumentException("All three translations are required.");
        text(value.ar(),"Arabic text",12000);text(value.fr(),"French text",12000);text(value.en(),"English text",12000);
    }
    public static void url(String value) {
        text(value,"evidence URL",2048);
        try {
            URI uri=URI.create(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)
                throw new IllegalArgumentException("Use an HTTP(S) source URL without credentials.");
        } catch(IllegalArgumentException error) { throw new IllegalArgumentException("Use a valid HTTP(S) source URL without credentials."); }
    }
    public static String archiveDigest(byte[] bytes,String expected) {
        if(bytes==null) {
            if(expected!=null)throw new IllegalArgumentException("An archive digest requires the archived bytes.");
            return null;
        }
        if(bytes.length>5_000_000)throw new IllegalArgumentException("Evidence archives must be at most 5 MB.");
        String digest=sha256(bytes);
        if(expected!=null&&!digest.equals(expected))throw new IllegalArgumentException("Archive digest does not match its bytes.");
        return digest;
    }
    public static String sha256(byte[] bytes) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    public static void evidence(Evidence evidence) {
        if(evidence==null||evidence.id()==null||evidence.retrievedAt()==null)throw new IllegalArgumentException("Evidence and retrieval time are required.");
        url(evidence.url());text(evidence.passage(),"source passage",100000);text(evidence.originalLanguage(),"source language",16);
        if(evidence.retrievedAt().isAfter(Instant.now()))throw new IllegalArgumentException("Retrieval time cannot be in the future.");
        if(evidence.publishedAt()!=null&&evidence.publishedAt().isAfter(evidence.retrievedAt()))throw new IllegalArgumentException("Publication cannot follow retrieval.");
    }
    public static void transition(Lifecycle previous,Lifecycle next) {
        if(next==null||previous!=null&&next.ordinal()<previous.ordinal())throw new IllegalArgumentException("A published lifecycle cannot move backwards.");
    }
    public static void mandate(MandateView value,MandateView previous) {
        if(value==null||!"2026-2031".equals(value.id()))throw new IllegalArgumentException("Unknown mandate.");
        translations(value.label());transition(previous==null?null:previous.lifecycle(),value.lifecycle());
        if(value.verifiedAt()==null||value.verifiedAt().isAfter(Instant.now()))throw new IllegalArgumentException("A reviewed verification date is required.");
        if(value.membership()==null)throw new IllegalArgumentException("Membership must be explicit, even when empty.");
        if(value.lifecycle()!=Lifecycle.FORMATION&&value.membership().isEmpty())throw new IllegalArgumentException("A formed government requires sourced membership.");
        for(Membership member:value.membership()) {
            if(member.id()==null||member.effectiveFrom()==null||member.evidenceIds()==null||member.evidenceIds().isEmpty())throw new IllegalArgumentException("Dated membership evidence is required.");
            translations(member.name());translations(member.role());
            if(member.effectiveUntil()!=null&&member.effectiveUntil().isBefore(member.effectiveFrom()))throw new IllegalArgumentException("Invalid membership period.");
        }
        if(value.lifecycle()==Lifecycle.PROGRAMME_PUBLISHED) {
            var programme=value.programme();
            if(programme==null||programme.id()==null||programme.version()<1||programme.evidenceIds()==null||programme.evidenceIds().isEmpty())throw new IllegalArgumentException("Programme publication requires a sourced version.");
            translations(programme.title());coverage(value.reviewCoverage());
        } else if(value.programme()!=null||value.reviewCoverage()!=null)throw new IllegalArgumentException("An unpublished programme cannot have published commitments or coverage.");
        if(value.recentUpdates()!=null&&!value.recentUpdates().isEmpty())throw new IllegalArgumentException("Updates are generated from publications, not authored as facts.");
    }
    public static void coverage(ReviewCoverage value) {
        if(value==null||value.reviewed()<0||value.total()!=null&&(value.total()<0||value.reviewed()>value.total())
                ||value.complete()&&(value.total()==null||value.reviewed()!=value.total()))throw new IllegalArgumentException("Review coverage must reconcile.");
    }
    public static void link(CommitmentLink value,ReviewCoverage coverage) {
        if(value==null||value.campaignPromiseId()==null||value.programmeVersionId()==null||value.relation()==null)throw new IllegalArgumentException("A link needs its promise and programme version.");
        translations(value.explanation());ids(value.evidenceIds());
        if(value.relation()==Relation.NOT_IDENTIFIED&&(coverage==null||!coverage.complete()))throw new IllegalArgumentException("Not identified requires a completely reviewed programme.");
        if((value.relation()==Relation.ADOPTED||value.relation()==Relation.MODIFIED)&&value.governmentCommitmentId()==null)throw new IllegalArgumentException("An adopted or modified link needs its government commitment.");
    }
    public static void ids(List<java.util.UUID> ids) {
        if(ids==null||ids.isEmpty()||ids.stream().anyMatch(java.util.Objects::isNull)||ids.stream().distinct().count()!=ids.size())throw new IllegalArgumentException("Unique supporting evidence references are required.");
    }
    public static void assessment(DeliveryAssessment value) {
        if(value==null)return;
        if(value.state()==null||value.evidenceConfidence()==null||value.reviewedAt()==null||value.reviewedAt().isAfter(Instant.now()))throw new IllegalArgumentException("A dated evidence assessment is required.");
        translations(value.explanation());ids(value.evidenceIds());
    }
    public static void scope(Scope value) {
        if(value==null||!List.of("NATIONAL","REGION","PROVINCE","COMMUNE").contains(value.level()))throw new IllegalArgumentException("A geographic scope is required.");
        text(value.code(),"scope code",80);translations(value.label());
        if(value.level().equals("NATIONAL")&&!value.code().equals("MA"))throw new IllegalArgumentException("National scope must use MA.");
    }
    public static void commitment(CommitmentView value) {
        if(value==null||value.id()==null||value.origin()!=Origin.GOVERNMENT)throw new IllegalArgumentException("Government entries must be typed government commitments; campaign records are read-only.");
        translations(value.title());translations(value.author());text(value.originalText(),"original source text",100000);text(value.originalLanguage(),"original language",16);
        scope(value.geographicScope());if(value.responsibleBody()!=null)translations(value.responsibleBody());
        if(value.topicCodes()==null||value.links()==null||value.evidence()==null||value.evidence().isEmpty()||value.observations()==null||value.revisions()==null||!value.revisions().isEmpty())throw new IllegalArgumentException("Sources and explicit collections are required; revisions are generated.");
        if(value.campaignPromiseId()!=null)throw new IllegalArgumentException("Use reviewed links, not a campaign identity, for government commitments.");
        if(value.evidence().stream().map(Evidence::id).distinct().count()!=value.evidence().size())throw new IllegalArgumentException("Duplicate evidence.");
        value.evidence().forEach(GovernmentValidation::evidence);assessment(value.deliveryAssessment());
        if(value.target()!=null) {
            var target=value.target();text(target.metric(),"metric",120);text(target.unit(),"unit",80);text(target.methodologyKey(),"methodology",160);
            if(target.targetValue()==null||(target.baseline()==null)!=(target.baselineDate()==null))throw new IllegalArgumentException("Target value and a consistently dated baseline are required.");
        }
        for(var observation:value.observations()) {
            if(observation.id()==null||observation.value()==null||observation.periodStart()==null||observation.periodEnd()==null||observation.periodEnd().isBefore(observation.periodStart())||observation.reviewedAt()==null||observation.reviewedAt().isAfter(Instant.now()))throw new IllegalArgumentException("A dated observation is required.");
            text(observation.metric(),"observed metric",120);text(observation.unit(),"observed unit",80);text(observation.methodologyKey(),"observed methodology",160);scope(observation.scope());ids(observation.evidenceIds());
        }
    }
}
