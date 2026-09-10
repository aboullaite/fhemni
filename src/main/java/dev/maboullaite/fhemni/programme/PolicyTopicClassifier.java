package dev.maboullaite.fhemni.programme;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Assignment;
import dev.maboullaite.fhemni.programme.PromisePolicyTopic.Relationship;
import org.springframework.stereotype.Component;

@Component
public class PolicyTopicClassifier {

    public static final String VERSION = "policy-topics-v2";

    private static final Pattern EDUCATION = pattern(
            "education|enseignement|ecole|school|universit|research|recherche|تعليم|مدرس|جامع|بحث علمي|تربوي");
    private static final Pattern EXPLICIT_EDUCATION = pattern(
            "education|enseignement|ecole|school|research|recherche|تعليم|مدرس|بحث علمي|تربوي");
    private static final Pattern UNIVERSITY = pattern("universit|جامع");
    private static final Pattern MEDICAL_FACILITY = pattern(
            "hospital|hopital|medical cent(?:er|re)|مستشف|استشف|مركز طبي");
    private static final Pattern EMPLOYMENT = pattern(
            "employment|emploi|jobs?|travail|labou?r|chomage|تشغيل|شغل|بطالة|توظيف|مقاول|منصب");

    private static final List<TopicRule> EDUCATION_DETAILS = List.of(
            rule("EDUCATION_PRESCHOOL", "preschool|préscolaire|التعليم الاولي|تعليم اولي|روض"),
            rule("EDUCATION_DROPOUT", "dropout|decrochage|الهدر المدرسي|الانقطاع المدرسي"),
            rule("EDUCATION_RESEARCH", "scientific research|recherche scientifique|البحث العلمي"),
            rule("EDUCATION_HIGHER", "higher education|enseignement superieur|universit|التعليم العالي|جامع"),
            rule("EDUCATION_TEACHERS", "teachers?|enseignants?|اساتذ|الاساتذ|توظيف.*تعليم|اطر التعليم"),
            rule("EDUCATION_SCHOOLS", "schools?|ecoles?|مدرس|مؤسسات تعليمية|البنية التحتية التعليمية"));

    private static final List<TopicRule> EMPLOYMENT_DETAILS = List.of(
            rule("EMPLOYMENT_JOB_CREATION", "job creation|creation d.emplois|خلق.*(?:شغل|منصب)|احداث.*منصب|مليون منصب"),
            rule("EMPLOYMENT_FIRST_JOB", "first job|premier emploi|اول عقد|اول فرصة|تجربتي|ادماج مهني|neet|ضمانة الشباب"),
            rule("EMPLOYMENT_UNEMPLOYMENT", "unemployment|chomage|بطالة"),
            rule("EMPLOYMENT_SELF_EMPLOYMENT", "self.employ|entreprene|auto.entrepreneur|مقاول|العمل الحر|مشاريع الشباب"),
            rule("EMPLOYMENT_WORKER_RIGHTS", "minimum wage|salaire|conge|labou?r rights|droits? (?:du travail|des travailleurs)|اجور|الاجور|عطلة|عامل موسمي|حقوق الاجراء"),
            rule("EMPLOYMENT_WOMEN", "women.*work|female.*participation|participation.*femmes|مشاركة.*نساء|ادماج.*نساء"));

    private static final List<TopicRule> BROAD_TOPICS = List.of(
            rule("HEALTH", "health|sante|medical|hospital|صحة|الصحة|طب|طبي|استشف|مستشفى"),
            rule("PURCHASING_POWER", "purchasing power|pouvoir d.achat|cost of living|قدرة شرائية|القدرة الشرائية|اسعار|الاسعار|غلاء|اجور|الاجور|معاش"),
            rule("HOUSING", "housing|logement|habitat|سكن|السكن|ايجار|كراء"),
            rule("SOCIAL_PROTECTION", "social protection|protection sociale|social security|securite sociale|pension|retraite|حماية اجتماعية|الحماية الاجتماعية|تقاعد|معاش|دعم اجتماعي"),
            rule("JUSTICE_SECURITY", "justice|judicial|courts?|security|securite|عدالة|العدالة|قضاء|محكم|الامن(?!\\s+(?:المائي|الطاقي|الغذائي))"),
            rule("WATER_ENERGY_ENVIRONMENT", "water|energy|environment|climate|eau|energie|environnement|climat|ماء|مياه|الماء|الطاقة|طاقي|بيئ|مناخ|محروقات"),
            rule("GOVERNANCE", "governance|corruption|transparen|election|institutions?|حكامة|فساد|شفاف|نزاهة|انتخاب|ديمقراط|مؤسسات"),
            rule("REGIONAL_DEVELOPMENT", "regional|territorial|rural|regions?|جهوي|الجهات|مجالي|تراب|قروي|القرى|العالم القروي"));

    public List<Assignment> classify(PartyPromise promise) {
        String headline = normalize(String.join(" ",
                promise.topic(),
                promise.title().ar(),
                promise.title().fr(),
                promise.title().en()));
        String complete = normalize(String.join(" ",
                headline,
                promise.promiseText(),
                promise.mechanism(),
                promise.financing()));
        Map<String, Relationship> matches = new LinkedHashMap<>();

        boolean medicalUniversity = UNIVERSITY.matcher(complete).find()
                && MEDICAL_FACILITY.matcher(complete).find()
                && !EXPLICIT_EDUCATION.matcher(complete).find();
        if (!medicalUniversity) {
            classifyFamily("EDUCATION", EDUCATION, EDUCATION_DETAILS, headline, complete, matches);
        }
        classifyFamily("EMPLOYMENT", EMPLOYMENT, EMPLOYMENT_DETAILS, headline, complete, matches);
        for (TopicRule rule : BROAD_TOPICS) {
            addIfMatched(rule, headline, complete, matches);
        }
        if (matches.isEmpty()) {
            matches.put("OTHER", Relationship.DIRECT);
        }
        return matches.entrySet().stream()
                .map(entry -> new Assignment(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void classifyFamily(
            String broadCode,
            Pattern broadPattern,
            List<TopicRule> details,
            String headline,
            String complete,
            Map<String, Relationship> matches) {
        if (!broadPattern.matcher(complete).find()) {
            return;
        }
        int before = matches.size();
        details.forEach(rule -> addIfMatched(rule, headline, complete, matches));
        if (matches.size() == before) {
            matches.put(broadCode, relationship(broadPattern, headline));
        }
    }

    private void addIfMatched(
            TopicRule rule,
            String headline,
            String complete,
            Map<String, Relationship> matches) {
        if (rule.pattern().matcher(complete).find()) {
            matches.put(rule.code(), relationship(rule.pattern(), headline));
        }
    }

    private Relationship relationship(Pattern pattern, String headline) {
        return pattern.matcher(headline).find() ? Relationship.DIRECT : Relationship.RELATED;
    }

    private static TopicRule rule(String code, String expression) {
        return new TopicRule(code, pattern(expression));
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ى', 'ي')
                .toLowerCase(Locale.ROOT);
    }

    private record TopicRule(String code, Pattern pattern) {
    }
}
