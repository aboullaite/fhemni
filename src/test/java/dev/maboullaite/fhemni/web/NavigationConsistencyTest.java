package dev.maboullaite.fhemni.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class NavigationConsistencyTest {

    private static final List<String> ADMIN_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html");

    private static final List<String> SECONDARY_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "community.html",
            "compare-programmes.html",
            "methodology.html",
            "parties.html",
            "party.html",
            "person.html",
            "promise.html",
            "priorities.html",
            "video.html",
            "videos.html");

    private static final List<String> ALL_PAGES = List.of(
            "admin.html",
            "admin-episodes.html",
            "admin-people.html",
            "admin-programmes.html",
            "admin-suggestions.html",
            "analysis.html",
            "community.html",
            "compare-programmes.html",
            "index.html",
            "login.html",
            "methodology.html",
            "parties.html",
            "party.html",
            "person.html",
            "promise.html",
            "priorities.html",
            "video.html",
            "videos.html");

    @Test
    void everyPrimaryNavigationUsesTheCanonicalThreeLinks() throws IOException {
        assertCanonicalPrimaryNavigation("index.html");
        for (String page : SECONDARY_PAGES) {
            assertCanonicalPrimaryNavigation(page);
        }
    }

    @Test
    void everyPrimaryNavigationLinksToThePriorityCompass() throws IOException {
        assertThat(html("index.html"))
                .contains("href=\"/priorities\" data-i18n=\"common.prioritiesQuiz\"");

        for (String page : SECONDARY_PAGES) {
            assertThat(html(page))
                    .as("priority compass navigation in %s", page)
                    .contains("href=\"/priorities\"")
                    .contains("data-i18n=\"common.prioritiesQuiz\"");
        }
    }

    @Test
    void priorityQuestionsUseFormScaleTypeAndMatchingActions() throws IOException {
        assertThat(html("priorities.html"))
                .contains("id=\"priorityPrevious\" class=\"priority-secondary-button\"")
                .contains("id=\"prioritySkip\" class=\"priority-secondary-button\"")
                .doesNotContain("priority-skip-button");
        assertThat(html("js/priorities.js"))
                .contains("context: 'علاش الاختيار ماشي ساهل؟'")
                .doesNotContain("context: 'شنو المفاضلة هنا؟'");
        assertThat(html("css/app.css"))
                .contains("font-size: clamp(26px, 2.4vw, 30px)")
                .contains("font-weight: 500; line-height: 1.5; text-align: center")
                .contains("font-size: clamp(21px, 5.5vw, 23px)")
                .contains("grid-template-columns: repeat(2, minmax(0, 160px))")
                .contains(".priority-theme-result { display: grid; grid-template-columns: 38px minmax(0,1fr); align-items: start;")
                .contains(".priority-result-rank { display: grid; width: 34px; height: 34px; place-items: center; margin-top: 1px;")
                .contains("font-variant-numeric: tabular-nums;")
                .contains("font-weight: 900; line-height: 1;");
    }

    @Test
    void priorityResultsEmphasizeTheCompassPartiesAndRankingWithoutSecondaryAnswerDetails() throws IOException {
        assertThat(html("priorities.html"))
                .contains("data-priority-orbit-tag=\"8\"")
                .contains("class=\"priority-result-section priority-compass-section\"")
                .contains("class=\"priority-compass-primary\"")
                .contains("class=\"compass-party-column\"")
                .contains("id=\"priorityRadarChart\"")
                .contains("class=\"compass-remaining\"")
                .contains("id=\"priorityShareDialog\"")
                .contains("id=\"priorityCompassShareHint\"")
                .contains("data-priority-share-kind=\"compass\"")
                .contains("data-priority-share-kind=\"parties\"")
                .contains("id=\"priorityShareLink\"")
                .contains("data-priority-share-action=\"link\"")
                .contains("data-priority-social=\"facebook\"")
                .contains("data-priority-social=\"whatsapp\"")
                .contains("data-priority-social=\"x\"")
                .contains("data-priority-social=\"linkedin\"")
                .contains("/webjars/html-to-image/1.11.13/dist/html-to-image.js")
                .contains("class=\"priority-result-section priority-summary-section\"")
                .doesNotContain("id=\"priorityResultsIntro\"")
                .doesNotContain("priority-result-details")
                .doesNotContain("priority-programme-proof-points")
                .doesNotContain("priorityProgrammeOfficial")
                .doesNotContain("priorityResultDetailsLabel")
                .doesNotContain("priorityPositionResults")
                .doesNotContain("priorityTensionResults")
                .doesNotContain("priorityMethodologyText");
        assertThat(html("js/priorities.js"))
                .contains("orbitTags: ['الأسعار', 'الصحة', 'الماء', 'السكن', 'الحماية الاجتماعية', 'الحكامة', 'الشغل', 'التعليم', 'المساواة']")
                .contains("compass-profile-radar")
                .contains("const radarMaximum = Math.max(")
                .contains("const scaledRadarRadius = score =>")
                .contains("compass-match-list")
                .contains("compass-match-row")
                .contains("compass-match-bar")
                .contains("function widthClass(value)")
                .contains("window.htmlToImage.toBlob")
                .contains("height: 1920")
                .contains("fhemni-priority-compass-story.png")
                .contains("navigator.canShare?.({ files: [asset.file] })")
                .contains("navigator.share({ title: asset.title, text: asset.text, files: [asset.file] })")
                .contains("/api/catalog/questionnaires/current/shares")
                .contains("navigator.share({ title: asset.title, text, url })")
                .contains("function isMobileShareDevice()")
                .contains("https://www.facebook.com/sharer/sharer.php")
                .contains("https://wa.me/")
                .doesNotContain("priorityShareCopyImage")
                .doesNotContain("priorityShareCopyText")
                .doesNotContain(".style.")
                .doesNotContain("style=\"")
                .doesNotContain("resultsIntro:")
                .doesNotContain("programmeOfficial:")
                .doesNotContain("#priorityProgrammeOfficial")
                .doesNotContain("شوف تفاصيل الأجوبة")
                .doesNotContain("Voir le détail des réponses")
                .doesNotContain("See answer details")
                .doesNotContain("#priorityResultDetailsLabel")
                .doesNotContain("renderPositionResults(")
                .doesNotContain("renderTensions(")
                .doesNotContain("compass-radar-svg");
        assertThat(html("css/app.css"))
                .contains("text-wrap: balance;")
                .contains(".priority-compass-primary { display: grid;")
                .contains(".priority-summary-section > h2")
                .contains(".priority-theme-results { display: grid; grid-template-columns: repeat(2, minmax(0,1fr));")
                .contains(".priority-theme-result + .priority-theme-result { border-top: 1px solid var(--line);")
                .doesNotContain(".priority-result-details")
                .doesNotContain(".priority-programme-proof-points")
                .contains(".compass-match-list { display: grid;")
                .contains(".compass-match-row { display: grid;")
                .contains(".compass-match-bar > span")
                .contains(".compass-profile-chart { display: grid; place-items: center; min-height: 390px;")
                .contains(".compass-profile-shape { fill: rgba(15,81,69,.26);")
                .contains("font-family: \"Tajawal Heading Numerals\", \"Tajawal\", Tahoma, Arial, sans-serif;")
                .contains(".priority-answer-button[aria-pressed=\"true\"] { border-color: var(--teal-dark);");
    }

    @Test
    void homePagePublishesACompleteLargeSocialCard() throws IOException {
        String home = html("index.html");
        assertThat(home)
                .contains("property=\"og:title\"")
                .contains("property=\"og:description\"")
                .contains("دقق فوعود الأحزاب المغربية وقارن البرامج والتقييمات والمصادر")
                .contains("property=\"og:site_name\"")
                .contains("name=\"twitter:card\" content=\"summary_large_image\"")
                .contains("https://fhemni.ma/assets/social/fhemni-og.png?v=20260910-2")
                .doesNotContain("fhemni.aboullaite.me");

        var imageResource = new ClassPathResource("static/assets/social/fhemni-og.png");
        var image = ImageIO.read(imageResource.getInputStream());
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }

    @Test
    void everyPageUsesTheVersionedFhemniFavicon() throws IOException {
        for (String page : ALL_PAGES) {
            assertThat(html(page))
                    .as("favicon in %s", page)
                    .contains("href=\"/favicon.ico?v=20260911\" sizes=\"32x32\"");
        }

        var favicon = new ClassPathResource("static/favicon.ico");
        assertThat(favicon.exists()).isTrue();

        var touchIcon = ImageIO.read(new ClassPathResource(
                "static/assets/brand/apple-touch-icon.png").getInputStream());
        assertThat(touchIcon.getWidth()).isEqualTo(180);
        assertThat(touchIcon.getHeight()).isEqualTo(180);
    }

    @Test
    void loginAndLegalPagesExposeStableProviderNeutralPolicyUrls() throws IOException {
        assertThat(html("login.html"))
                .contains("href=\"/terms\"")
                .contains("href=\"/privacy\"");
        assertThat(html("terms.html"))
                .contains("Terms of Service")
                .contains("href=\"/privacy\"")
                .contains("an available authentication method")
                .contains("any identity-provider account")
                .doesNotContain("Discord or Google account");
        assertThat(html("privacy.html"))
                .contains("Privacy Policy")
                .contains("privacy@fhemni.ma")
                .contains("third-party identity provider")
                .contains("authentication-provider data")
                .contains("request access, correction, export, objection, restriction, or deletion")
                .contains("public share link")
                .contains("individual answers used to create it are not uploaded")
                .doesNotContain("Discord user ID")
                .doesNotContain("associated Discord data")
                .doesNotContain("Discord's Authorized Apps");
        assertThat(html("js/i18n.js"))
                .contains("ensureLegalFooterLinks(root)")
                .contains("href=\"/terms\" data-i18n=\"common.terms\"")
                .contains("href=\"/privacy\" data-i18n=\"common.privacyPolicy\"");
    }

    @Test
    void everyPageUsesThePinnedWebFontStylesheet() throws IOException {
        for (String page : ALL_PAGES) {
            String version = page.equals("priorities.html") ? "20260917-8" : "20260916-22";
            assertThat(html(page))
                    .as("stylesheet in %s", page)
                    .containsOnlyOnce("/css/dist.css?v=" + version);
        }
    }

    @Test
    void compactPartyBadgesOpticallyAlignTheirColourDot() throws IOException {
        assertThat(html("css/app.css"))
                .contains(".party-badge .party-dot { transform: translateY(-1px); }");
    }

    @Test
    void publicPromiseCardsDoNotRepeatTheLocalizedVerdictInTheirSummary() throws IOException {
        assertThat(html("js/catalog-ui.js"))
                .contains("function assessmentSummary(value, verdict)")
                .contains("DIFFICILE")
                .contains("DONN[ÉE]ES\\s+INSUFFISANTES")
                .contains("المعطيات\\s+ما\\s+كافياش");
        assertThat(html("js/landing.js"))
                .contains("FhemniCatalog.assessmentSummary(");
        assertThat(html("js/party.js"))
                .contains("FhemniCatalog.assessmentSummary(");
        assertThat(html("js/promise.js"))
                .contains("FhemniCatalog.assessmentSummary(")
                .doesNotContain("function cleanSummary(value)");
        assertThat(html("index.html"))
                .contains("/js/catalog-ui.js?v=20260914-1")
                .contains("/js/landing.js?v=20260914-1");
        assertThat(html("promise.html"))
                .contains("/js/catalog-ui.js?v=20260914-1")
                .contains("/js/promise.js?v=20260914-1");
    }

    @Test
    void catalogPlayButtonUsesAFontIndependentCenteredTriangle() throws IOException {
        assertThat(html("css/app.css"))
                .contains(".catalog-thumbnail::after { content: \"\";")
                .contains(".catalog-thumbnail::before { content: \"\";")
                .contains("border-left: 10px solid var(--orange)")
                .doesNotContain(".catalog-thumbnail::after { content: \"▶\"");
    }

    @Test
    void everyPageUsesCampaignAwarePrivacySafeAnalytics() throws IOException {
        for (String page : ALL_PAGES) {
            assertThat(html(page))
                    .as("analytics asset in %s", page)
                    .contains("/js/analytics.js?v=20260911-1");
        }

        assertThat(html("js/analytics.js"))
                .contains("page_location: campaignLocation()")
                .contains("CAMPAIGN_PARAMETER")
                .contains("key === 'page_location' ? MAX_PAGE_LOCATION_LENGTH : 100")
                .doesNotContain("page_location: `${window.location.origin}${window.location.pathname}`")
                .doesNotContain("page_location: window.location.href");
    }

    @Test
    void loginPageUsesClearDarijaCopyAndBrandedProviders() throws IOException {
        assertThat(html("login.html"))
                .contains("ستافد أكثر من فهّمني")
                .contains("دخل لحسابك باش تسول فهّمني فالشات، تقترح وتصوّت.")
                .contains("/js/login.js?v=20260913-1");
        assertThat(html("js/i18n.js"))
                .contains("'login.emailAction': 'بغيت الرابط'")
                .contains("'login.emailAction': 'Get link'")
                .contains("'login.emailAction': 'Recevoir le lien'")
                .contains("new URLSearchParams(window.location.search).get('lang')")
                .contains("storeLocale(requested);")
                .doesNotContain("'login.emailAction': 'Email me a sign-in link'")
                .doesNotContain("'login.emailAction': 'Recevoir un lien de connexion'");
        assertThat(html("js/login.js"))
                .contains("auth-provider--email")
                .contains("locale: window.FhemniI18n?.locale?.() || document.documentElement.lang")
                .contains("['google', 'discord'].includes(providerId)")
                .contains("`auth-provider--${providerId}`")
                .contains("providerIcon(provider.id)");
        assertThat(html("css/dist.css"))
                .contains(".auth-provider--google")
                .contains("#1a73e8")
                .contains(".auth-provider--discord")
                .contains("#5865f2")
                .contains(".auth-main{min-height:0;padding:22px 0 72px;display:block}")
                .contains(".auth-card{width:100%;padding-inline:20px}")
                .contains(".magic-link-fields{grid-template-columns:minmax(0,1fr)}");
    }

    @Test
    void nonFactualClaimExplanationIsLocalizedAtRenderTime() throws IOException {
        assertThat(html("js/i18n.js"))
                .contains("'analysis.nonFactualExplanation': 'Opinions, proposals, and predictions")
                .contains("'analysis.nonFactualExplanation': 'Les opinions, propositions et prévisions")
                .contains("'analysis.nonFactualExplanation': 'الآراء والاقتراحات والتوقعات كيبانو بوحدهم")
                .contains("'analysis.whatDiscussed': 'على شنو هضروا؟'")
                .doesNotContain("'analysis.whatDiscussed': 'على شنو تهضروا؟'");
        assertThat(html("js/analysis.js"))
                .contains("claim.kind === 'FACT'")
                .contains("t('analysis.nonFactualExplanation')");
        assertThat(html("analysis.html"))
                .contains("/js/analysis.js?v=20260912-2")
                .contains("/js/i18n.js?v=20260915-1")
                .doesNotContain("analysis.evidenceKicker");
        assertThat(html("js/i18n.js"))
                .doesNotContain("analysis.evidenceKicker")
                .doesNotContain("الأدلة، ماشي غير نقط");
    }

    @Test
    void analysisBadgesContainArabicLabelsOnNarrowCards() throws IOException {
        assertThat(html("css/dist.css"))
                .contains(".claim-card .claim-meta{align-items:flex-start")
                .contains(".claim-card .claim-badges{min-width:0;max-width:100%}")
                .contains(".claim-card .claim-badges .badge{text-align:center;white-space:normal;height:auto;min-height:24px;padding-block:3px;line-height:1.35}");
    }

    @Test
    void compactPartyBadgesAlignLatinCodesWithTheirPartyDot() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("border-radius:999px;align-items:center;gap:7px")
                .contains("font-size:10px;font-weight:850;line-height:1;text-decoration:none;display:inline-flex}");
    }

    @Test
    void homeFactCheckPanelUsesTheSharedRadiusWithoutADoubleDivider() throws IOException {
        assertThat(html("css/dist.css"))
                .contains(".fact-check-feature{")
                .contains("border-radius:var(--radius-panel)")
                .contains(".fact-check-feature+.featured-section{border-top:0}")
                .doesNotContain(".fact-check-feature{padding:44px clamp(20px,3vw,36px);border:1px solid #176b6333;border-radius:28px");
    }

    @Test
    void homePageUsesOneConciseFactCheckHeading() throws IOException {
        assertThat(html("index.html"))
                .contains("id=\"factCheckTitle\" data-i18n=\"landing.factCheckTitle\"")
                .doesNotContain("landing.factCheckKicker")
                .doesNotContain("landing.factCheckIntro");
        assertThat(html("js/i18n.js"))
                .contains("'landing.promiseCheck': 'حلل الأدلة'")
                .contains("اللحظة اللي تقالت فيها")
                .contains("'landing.promiseAskText': 'قرا الخلاصة ولا سول الفيديو و دقق فاللحظة اللي تقالت فيها الهضرة.'")
                .doesNotContain("'landing.promiseCheck': 'حل الأدلة'")
                .doesNotContain("وبقا مربوط باللحظة")
                .doesNotContain("اللحظة اللي تقالات فيها");
    }

    @Test
    void adminPagesUseTheLatestMobileAssets() throws IOException {
        for (String page : ADMIN_PAGES) {
            assertThat(html(page))
                    .as("mobile assets in %s", page)
                    .contains("/css/dist.css?v=20260916-22")
                    .contains("/js/i18n.js?v=20260915-1");
        }
    }

    @Test
    void adminDashboardSurfacesOpenReaderReports() throws IOException {
        assertThat(html("admin.html"))
                .contains("id=\"adminAssessmentReportsAlert\"")
                .contains("href=\"/admin/programmes?focus=reports\"")
                .contains("admin.readerReportsAlertTitle")
                .contains("/js/admin.js?v=20260915-1");
        assertThat(html("js/admin.js"))
                .contains("/api/admin/programmes/assessment-reports")
                .contains("renderAssessmentReportsAlert");
        assertThat(html("js/programme-admin.js"))
                .contains("focusReaderReports")
                .contains("programme-report-flag")
                .contains("programme-promise-row.has-reader-reports")
                .contains("expandedPromises")
                .contains("expandedAssessmentDetails")
                .contains("admin.focusedReviewActive")
                .contains("dismissAssessmentReportConfirm")
                .contains("assessmentReportDismissed")
                .contains("/assessment-reports/${reportId}/dismiss")
                .contains("window.setTimeout(pollActiveWork, 3000)")
                .contains("updateProgrammeMediaPanel(programmeId)")
                .contains("data-programme-id")
                .doesNotContain("window.setTimeout(load, 3000)");
        assertThat(html("js/catalog-ui.js"))
                .contains("category.dataset.reportCategory = ''")
                .contains("FACTUAL_OR_LEGAL_ERROR")
                .contains("OUTDATED_OR_MISSING_SOURCE")
                .contains("UNCLEAR_REASONING")
                .contains("assessmentId: dialog.dataset.assessmentId || null")
                .contains("category: dialog.querySelector('[data-report-category]').value");
    }

    @Test
    void suggestedEpisodesCanBeSelectedAndSentToTheBatchImporter() throws IOException {
        assertThat(html("admin-suggestions.html"))
                .contains("id=\"selectAllSuggestions\"")
                .contains("id=\"selectedSuggestionCount\"")
                .contains("id=\"addSelectedSuggestions\"");
        assertThat(html("js/admin.js"))
                .contains("const maxSuggestionSelection = 20")
                .contains("suggestion.moderationStatus !== 'REVIEW_REQUIRED'")
                .contains("parameters.append('youtubeUrl', youtubeUrl)")
                .contains("getAll('youtubeUrl')")
                .contains("selectedSuggestionIds.clear()")
                .contains("addToImporter(selectedUrls)");
        assertThat(html("js/i18n.js"))
                .contains("'admin.addSelectedToImporter': 'Add selected to importer'")
                .contains("'admin.addSelectedToImporter': 'Ajouter la sélection'")
                .contains("'admin.addSelectedToImporter': 'زيد المختارين'");
    }

    @Test
    void narrowAdminHeadersPutLanguageChoicesBesideTheNavigation() throws IOException {
        assertThat(html("css/dist.css"))
                .contains("@media (max-width:360px)")
                .contains(".admin-page .public-header .header-actions{display:contents}")
                .contains(".admin-page .public-header .header-actions>.site-language-options{grid-area:2/1");
    }

    @Test
    void partyPageCombinesTheBriefingWithTheFullProgramme() throws IOException {
        String partyPage = html("party.html");
        assertThat(partyPage)
                .contains("id=\"partyProgrammeTab\"")
                .contains("id=\"partyChatTab\"")
                .contains("id=\"partyEpisodesTab\"")
                .contains("class=\"programme-overview\"")
                .contains("id=\"programmeMedia\" class=\"programme-media\"")
                .contains("class=\"video-js vjs-big-play-centered\"")
                .contains("/webjars/video.js/8.23.8/dist/video-js.min.css")
                .contains("/css/dist.css?v=20260916-22")
                .contains("/js/videojs-config.js?v=20260911-1")
                .contains("/webjars/video.js/8.23.8/dist/video.min.js")
                .contains("/js/i18n.js?v=20260915-1")
                .contains("/js/party.js?v=20260914-1")
                .containsOnlyOnce("data-i18n=\"programme.kicker\"")
                .doesNotContain("data-i18n=\"programme.mediaPowered\"")
                .doesNotContain("id=\"partyBriefingTab\"")
                .doesNotContain("id=\"programmeMediaAudio\"")
                .doesNotContain("id=\"programmeMediaTranscript\"")
                .doesNotContain("programme.mediaTitle")
                .doesNotContain("programme.mediaTranscript")
                .doesNotContain("data-programme-skip")
                .doesNotContain("id=\"partyPriorities\"");
        assertThat(partyPage.indexOf("id=\"programmeMedia\"")).isBetween(
                partyPage.indexOf("id=\"partyProgramme\""),
                partyPage.indexOf("id=\"partyPromises\""));
        assertThat(partyPage.indexOf("/js/videojs-config.js"))
                .isLessThan(partyPage.indexOf("/webjars/video.js/8.23.8/dist/video.min.js"));
        assertThat(html("js/videojs-config.js"))
                .contains("window.VIDEOJS_NO_DYNAMIC_STYLE = true");
        assertThat(html("js/i18n.js"))
                .contains("'programme.mediaDisclosure': 'خلاصة بالذكاء الاصطناعي، الخطأ وارد.'");
        assertThat(html("js/party.js"))
                .doesNotContain("['briefing'")
                .doesNotContain("#programmeMediaAudio")
                .doesNotContain("#programmeMediaTranscript");
        assertThat(html("css/dist.css"))
                .contains("https://storage.googleapis.com/fhemni-public-assets-mohamed-playground/fonts/videojs.46d5222f8568.woff");
    }

    private String html(String page) throws IOException {
        return new ClassPathResource("static/" + page).getContentAsString(UTF_8);
    }

    private void assertCanonicalPrimaryNavigation(String page) throws IOException {
        String pageHtml = html(page);
        int start = pageHtml.indexOf("<nav class=\"primary-nav\"");
        int end = pageHtml.indexOf("</nav>", start);
        assertThat(start).as("primary navigation start in %s", page).isGreaterThanOrEqualTo(0);
        assertThat(end).as("primary navigation end in %s", page).isGreaterThan(start);
        assertThat(pageHtml.substring(start, end))
                .as("primary navigation in %s", page)
                .contains("href=\"/videos\"")
                .contains("href=\"/parties\"")
                .contains("href=\"/priorities\"")
                .doesNotContain("common.howItWorks");
    }
}
