package dev.maboullaite.fhemni.government;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Public evidence projections. Editorial account identifiers never belong in these views. */
public final class GovernmentViews {
    private GovernmentViews() {}
    public enum Lifecycle { FORMATION, FORMED_PROGRAMME_PENDING, PROGRAMME_PUBLISHED }
    public enum Origin { CAMPAIGN, GOVERNMENT }
    public enum DeliveryState { NO_EVIDENCE_FOUND, UNDERWAY, PARTLY_DELIVERED, DELIVERED, WITHDRAWN }
    public enum EvidenceConfidence { GOVERNMENT_REPORTED, CORROBORATED, CONTESTED }
    public enum Relation { ADOPTED, MODIFIED, NOT_IDENTIFIED, UNRESOLVED }
    public enum SubjectType { MANDATE, COMMITMENT, MEASURE, LINK }
    public enum RevisionState { DRAFT, REVIEWED, PUBLISHED, SUPERSEDED }
    public enum OperationalStatus { UNKNOWN, PENDING, AVAILABLE, SUSPENDED }
    public record LocalText(String ar, String fr, String en) {}
    public record Page<T>(List<T> items, int page, int pageSize, long totalItems) {
        public Page { items=List.copyOf(items); }
    }
    public record Evidence(UUID id, String url, String passage, String locator, String originalLanguage,
            Instant publishedAt, Instant retrievedAt, String archivedSha256) {}
    public record Membership(UUID id, LocalText name, LocalText role, String partyCode,
            LocalDate effectiveFrom, LocalDate effectiveUntil, List<UUID> evidenceIds) {}
    public record ReviewCoverage(int reviewed, Integer total, boolean complete) {}
    public record ProgrammeVersion(UUID id, int version, LocalText title, LocalDate publishedAt, List<UUID> evidenceIds) {}
    public record MandateView(String id, LocalText label, Lifecycle lifecycle, Instant verifiedAt,
            List<Membership> membership, ProgrammeVersion programme, ReviewCoverage reviewCoverage,
            List<PublishedUpdate> recentUpdates) {}
    public record Scope(String level, String code, LocalText label) {}
    public record Target(String metric, String unit, BigDecimal baseline, LocalDate baselineDate,
            BigDecimal targetValue, LocalDate targetDate, String methodologyKey) {}
    public record Observation(UUID id, String metric, String unit, BigDecimal value, LocalDate periodStart,
            LocalDate periodEnd, Scope scope, String methodologyKey, List<UUID> evidenceIds, Instant reviewedAt) {}
    public record DeliveryAssessment(DeliveryState state, EvidenceConfidence evidenceConfidence,
            LocalText explanation, List<UUID> evidenceIds, Instant reviewedAt) {}
    public record CommitmentLink(UUID campaignPromiseId, UUID governmentCommitmentId, Relation relation,
            UUID programmeVersionId, LocalText explanation, List<UUID> evidenceIds) {}
    public record RevisionSummary(UUID id, int version, Instant publishedAt, LocalText explanation) {}
    public record RelationshipView(UUID id,String slug,LocalText title,CommitmentLink relationship,List<Evidence> evidence) {}
    public record PublicRevision(UUID id,SubjectType subjectType,String slug,int version,Instant publishedAt,
            LocalText explanation,Object snapshot,List<Evidence> evidence) {}
    /** Literal extraction context, not an assessment or a normalized measurable target. */
    public record RelatedEntry(String slug, String relation) {}
    public record TopicCount(String code, long count) {}
    public record DiscoveryTopic(String code, LocalText label) {}
    public record ProgrammePlace(String regionCode, LocalText place, String sourceExcerpt, int printedPage) {}
    public record RegionalProgrammeEntry(CommitmentView commitment, List<ProgrammePlace> places) {}
    public record RegionCoverage(Scope region, long count) {}
    public record RegionalProgrammePage(List<RegionalProgrammeEntry> items, int page, int pageSize, long totalItems,
            List<RegionCoverage> regionCounts, int linkedEntries, int programmeEntries) {}
    public record ProgrammeGraphicEntry(String slug, LocalText title, ProgrammeContext programmeContext) {}
    public record ProgrammeInsights(int totalEntries, List<TopicCount> topics, List<ProgrammeGraphicEntry> entries) {}
    public record ProgrammeContext(String sourceStatus, List<Integer> printedPages, String kind,
            String summaryEn, String deadlineText, String targetText, String scopeText,
            List<String> caveats, List<RelatedEntry> relatedEntries) {}
    public record CommitmentView(UUID id, String slug, Origin origin, LocalText title, String originalText,
            String originalLanguage, LocalText author, UUID campaignPromiseId, List<String> topicCodes,
            Scope geographicScope, LocalText responsibleBody, Target target, LocalDate deadline,
            DeliveryAssessment deliveryAssessment, List<CommitmentLink> links, List<Evidence> evidence,
            List<Observation> observations, List<RevisionSummary> revisions, ProgrammeContext programmeContext) {
        public CommitmentView(UUID id, String slug, Origin origin, LocalText title, String originalText,
                String originalLanguage, LocalText author, UUID campaignPromiseId, List<String> topicCodes,
                Scope geographicScope, LocalText responsibleBody, Target target, LocalDate deadline,
                DeliveryAssessment deliveryAssessment, List<CommitmentLink> links, List<Evidence> evidence,
                List<Observation> observations, List<RevisionSummary> revisions) {
            this(id,slug,origin,title,originalText,originalLanguage,author,campaignPromiseId,topicCodes,
                    geographicScope,responsibleBody,target,deadline,deliveryAssessment,links,evidence,observations,revisions,null);
        }
    }
    public record PublishedUpdate(UUID id, String subjectType, String subjectId, UUID revisionId,
            String materialHash, List<String> topicCodes, List<String> regionCodes, LocalText title,
            LocalText summary, Instant publishedAt) {}
    public record MeasureView(UUID id,String slug,LocalText title,LocalDate announcement,LocalDate effectiveDate,
            OperationalStatus operationalStatus,LocalText eligibility,LocalText steps,String officialActionUrl,
            Scope scope,LocalText responsibleBody,List<Evidence> evidence) {}
    public record GeographicObservation(UUID commitmentId,String commitmentSlug,LocalText title,
            LocalText responsibleBody,Observation observation,List<Evidence> evidence) {}
    public static MandateView pending(String id) {
        return new MandateView(id,new LocalText("الحكومة 2026–2031","Gouvernement 2026–2031","Government 2026–2031"),
                Lifecycle.FORMATION,null,List.of(),null,null,List.of());
    }
}
