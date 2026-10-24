package dev.maboullaite.fhemni.government;

import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Opt-in research snapshot. It is deliberately not bundled as verified production data. */
public final class ProgrammePreviewData {
    public static final String PDF_SHA = "7322ee0fbdafdacf6947374b2e71f12de07aa0595eb0428534c833432a895254";
    public static final String PUBLIC_SOURCE = "https://www.chambredesrepresentants.ma/ar/node/467708";
    public static final String MIRROR_SOURCE = "https://drive.google.com/file/d/1VqWo2CD-wbk9sy3dY5qKHQBuck20vnYy/view";
    public static final String PDF_URL = "/api/catalog/government/2026-2031/preview/pdf";
    private static final Map<String,String> DIGESTS = Map.of(
        "commitments-001-048.json", "5522883c097692bfdbce97acb3f277a40dd86caf28e0873f0b7cd5a41697e5bd",
        "commitments-049-095.json", "2759a170141a2e64219b2f00a89a31d177d74bd71632bd9850a5fa0a3a1e698c",
        "commitments-096-127.json", "9a8be25b6b71914cccf009a416b15910c675e9576273f92e7b5eb9189c1b5dce",
        "cabinet-snapshot.json", "df3a5012b266df61bc88c6ae844730a0d6c82e07ef51f695fbc27b0be4a6f7e9",
        "editorial-links.json", "12f91f128c6796c09486b8a00d7fe066893d7dd6778b8cecebad6ccdd1d5ab4e");
    public record Snapshot(byte[] pdf, MandateView mandate, List<Evidence> sources,
            List<CommitmentView> commitments, List<String> coalitionPartyCodes) {}

    public static void requireLocal(String database, String address, List<String> profiles) {
        String base = database == null ? "" : database.split(";",2)[0];
        if (!base.equals("jdbc:h2:file:./data/government-preview") || !"127.0.0.1".equals(address)
                || !profiles.contains("programme-preview") || profiles.contains("prod"))
            throw new IllegalStateException("Programme research preview requires its isolated local H2 database and 127.0.0.1 binding; never enable it in production.");
    }
    public static void verify(byte[] bytes, String expected, String name) {
        if (!GovernmentValidation.sha256(bytes).equals(expected))
            throw new IllegalArgumentException("Research snapshot checksum mismatch: " + name);
    }
    public static void requireTestIdentity(dev.maboullaite.fhemni.identity.AuthProperties auth) {
        String email="reviewer@fhemni.local",subject=GovernmentValidation.sha256(email.getBytes(StandardCharsets.UTF_8));
        if(auth.isBootstrapAdminEmail(email)||auth.isAdminIdentity("magic-link",subject))
            throw new IllegalStateException("The local preview reviewer must not have administrator rights.");
    }
    public static Snapshot load(Path directory, ObjectMapper mapper) throws IOException {
        var documents = new HashMap<String,JsonNode>();
        for (var entry : DIGESTS.entrySet()) {
            byte[] bytes = Files.readAllBytes(directory.resolve("research").resolve(entry.getKey()));
            verify(bytes,entry.getValue(),entry.getKey());
            documents.put(entry.getKey(),mapper.readTree(bytes));
        }
        byte[] pdf = Files.readAllBytes(directory.resolve("sources/programme-mirror.pdf"));
        verify(pdf,PDF_SHA,"programme-mirror.pdf");
        var sources = new ArrayList<Evidence>();
        var retrieved = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var archive = new Evidence(id("programme-archive"),MIRROR_SOURCE,
                "Archived research PDF; not yet authenticated against the official copy. Public reference: " + PUBLIC_SOURCE,
                "133 PDF pages; printed pages 1–127; PDF page = printed page + 6", "ar",null,retrieved,PDF_SHA);
        sources.add(archive);
        var publication = new Evidence(id("programme-publication"),PUBLIC_SOURCE,
                "User-selected official programme presentation reference, 19 October 2026. This reference does not authenticate the separately archived mirror PDF.",
                "Programme presentation reference", "ar",LocalDate.parse("2026-10-19").atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),retrieved,null);
        sources.add(publication);
        var cabinet=documents.get("cabinet-snapshot.json");
        for (var source:cabinet.path("sources")) {
            if (source.path("evidenceType").asText().startsWith("REJECTED")) continue;
            sources.add(new Evidence(id(source.path("id").asText()),source.path("url").asText(),
                    "Research summary — " + source.path("publisher").asText() + ": " + String.join("; ",strings(source.path("supports"))),
                    source.path("evidenceType").asText(),"en",null,retrieved,null));
        }
        var memberships=new ArrayList<Membership>();
        for (String group:List.of("current","former")) for (var member:cabinet.path(group)) {
            String start=member.path("cabinetMembershipStart").asText();
            memberships.add(new Membership(id(member.path("id").asText()),
                    new LocalText(member.path("nameAr").asText(),member.path("nameFr").asText(),member.path("nameFr").asText()),
                    new LocalText(member.path("roleAr").asText(),member.path("roleFr").asText(),member.path("roleFr").asText()),
                    nullable(member.path("partyAffiliation"),"code"),LocalDate.parse(start),
                    nullable(member,"cabinetMembershipEnd")==null?null:LocalDate.parse(member.path("cabinetMembershipEnd").asText()),
                    strings(member.path("evidenceSourceIds")).stream().map(ProgrammePreviewData::id).toList()));
        }
        if(memberships.size()!=32||memberships.stream().filter(m->m.effectiveUntil()==null).count()!=31)
            throw new IllegalArgumentException("Cabinet snapshot does not reconcile.");
        var rows=new ArrayList<JsonNode>();
        for(String file:List.of("commitments-001-048.json","commitments-049-095.json","commitments-096-127.json"))
            documents.get(file).path("commitments").forEach(rows::add);
        var identifiers=new HashSet<String>();
        for(var row:rows)if(!identifiers.add(row.path("id").asText()))throw new IllegalArgumentException("Duplicate research entry.");
        if(rows.size()!=595)throw new IllegalArgumentException("Incomplete research snapshot.");
        var related=new HashMap<String,List<RelatedEntry>>();
        for(var link:documents.get("editorial-links.json").path("links")) {
            String from=link.path("from").asText(),to=link.path("to").asText();
            if(!identifiers.contains(from)||!identifiers.contains(to))throw new IllegalArgumentException("Unknown related entry.");
            related.computeIfAbsent(from,key->new ArrayList<>()).add(new RelatedEntry("programme-"+to,link.path("relation").asText()));
        }
        var commitments=new ArrayList<CommitmentView>();
        for(var row:rows) {
            var source=new Evidence(id("excerpt-"+row.path("id").asText()),MIRROR_SOURCE,row.path("sourceAnchor").asText(),
                    "Printed page(s): " + row.path("pages") + "; PDF page = printed page + 6. Archive SHA-256: " + PDF_SHA,
                    "ar",null,retrieved,null);
            sources.add(source);
            commitments.add(commitment(row,List.of(source),related.getOrDefault(row.path("id").asText(),List.of())));
        }
        var mandate=new MandateView("2026-2031",new LocalText("الحكومة 2026–2031","Gouvernement 2026–2031","Government 2026–2031"),
                Lifecycle.PROGRAMME_PUBLISHED,retrieved,List.copyOf(memberships),
                new ProgrammeVersion(id("programme-version"),1,new LocalText("البرنامج الحكومي","Programme gouvernemental","Government programme"),LocalDate.parse("2026-10-19"),List.of(publication.id(),archive.id())),
                new ReviewCoverage(0,null,false),List.of());
        return new Snapshot(pdf,mandate,List.copyOf(sources),List.copyOf(commitments),strings(cabinet.path("coalition").path("partyCodes")));
    }
    public static CommitmentView commitment(JsonNode row,List<Evidence> sources,List<RelatedEntry> related) {
        if(!"MIRROR_UNAUTHENTICATED".equals(row.path("sourceStatus").asText())||!"UNASSESSED".equals(row.path("deliveryStatus").asText()))
            throw new IllegalArgumentException("Research preview must not assert authenticated evidence or delivery.");
        String slug=row.path("id").asText(),title=row.path("titleAr").asText();
        if(!slug.matches("[a-z0-9][a-z0-9-]{0,148}")||title.isBlank()||row.path("sourceAnchor").asText().isBlank())
            throw new IllegalArgumentException("Missing entry identity, title or source anchor.");
        var pages=new ArrayList<Integer>();
        for(var page:row.path("pages")) {int number=page.asInt();if(number<1||number>127)throw new IllegalArgumentException("Invalid printed page.");pages.add(number);}
        if(pages.isEmpty())throw new IllegalArgumentException("A source page is required.");
        String body=nullable(row,"responsibleBody");
        return new CommitmentView(id(slug),"programme-"+slug,Origin.GOVERNMENT,new LocalText(title,title,title),row.path("sourceAnchor").asText(),"ar",
                new LocalText("البرنامج الحكومي","Programme gouvernemental","Government programme"),null,topics(title,nullable(row,"summaryEn")),
                new Scope("UNSPECIFIED","UNSPECIFIED",new LocalText("النطاق الجغرافي ما تصنّفش بعد","Périmètre géographique non classé","Geographic scope not yet classified")),body==null?null:new LocalText(body,body,body),
                null,null,null,List.of(),sources,List.of(),List.of(),
                new ProgrammeContext("MIRROR_UNAUTHENTICATED",List.copyOf(pages),nullable(row,"kind"),nullable(row,"summaryEn"),
                        nullable(row,"deadlineText"),literalTarget(row.path("target")),nullable(row,"scopeText"),strings(row.path("caveats")),List.copyOf(related)));
    }
    private static String literalTarget(JsonNode target) {
        if(target.isMissingNode()||target.isNull())return null;
        // Retain the extraction's operator/range/baseline literally, without converting it into a dated observation.
        var fields=new ArrayList<String>();
        target.properties().forEach(entry->{if(!entry.getValue().isNull())fields.add(entry.getKey()+": "+(entry.getValue().isTextual()?entry.getValue().asText():entry.getValue().toString()));});
        return String.join(" · ",fields);
    }
    /** Conservative discovery tags authored by Fhemni, not official ministry assignments. */
    static List<String> topics(String title,String summary) {
        String value=(title+" "+(summary==null?"":summary)).toLowerCase(Locale.ROOT);
        var result=new ArrayList<String>();
        tag(result,value,"EDUCATION","التعليم|مدرس|جامع|تكوين|preschool|school|education|universit|training|research");
        tag(result,value,"EMPLOYMENT","الشغل|تشغيل|بطالة|المقاول|employment|unemployment|jobs|entrepreneur|enterprises|sme");
        tag(result,value,"HEALTH","الصحة|الصحي|مستشف|دواء|أدوية|طبي|health|hospital|medic|patient");
        tag(result,value,"PURCHASING_POWER","الأسعار|التموين|القدرة الشرائية|تموين|purchasing power|food basket|food supply|prices|inflation");
        tag(result,value,"HOUSING","السكن|الإسكان|سكني|بنايات|housing|rental|home ownership|buildings");
        tag(result,value,"SOCIAL_PROTECTION","الحماية الاجتماعية|تقاعد|معاش|التأمين|الأسر|social protection|pension|social assistance|insurance");
        tag(result,value,"JUSTICE_SECURITY","العدالة|القضاء|الأمن|judicial|justice|courts|security");
        tag(result,value,"WATER_ENERGY_ENVIRONMENT","الماء|المياه|الطاقة|البيئة|مناخ|water|energy|environment|climate|desalination");
        tag(result,value,"GOVERNANCE","الحكامة|الفساد|الإدارة|إداري|ميزانية|governance|corruption|administrati|budget|public procurement");
        tag(result,value,"REGIONAL_DEVELOPMENT","قروي|ترابي|جهوي|طرقي|rural|territorial|regional development|rail|transport|roads|port capacity");
        return result.isEmpty()?List.of("OTHER"):List.copyOf(result);
    }
    private static void tag(List<String> result,String text,String code,String pattern){if(java.util.regex.Pattern.compile(pattern).matcher(text).find())result.add(code);}
    static String nullable(JsonNode node,String field){var value=node.path(field);return value.isMissingNode()||value.isNull()||value.asText().isBlank()?null:value.asText();}
    static List<String> strings(JsonNode array){var values=new ArrayList<String>();array.forEach(value->values.add(value.asText()));return List.copyOf(values);}
    static UUID id(String key){return UUID.nameUUIDFromBytes(("fhemni-local-programme-preview:"+key).getBytes(StandardCharsets.UTF_8));}
}
