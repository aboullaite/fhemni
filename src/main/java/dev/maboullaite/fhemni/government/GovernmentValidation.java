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
}
