(function () {
    const RESULT_URL = '/api/catalog/elections/2026/results';
    const COALITION_URL = '/api/catalog/elections/2026/coalitions/evaluate';
    const MAP_URL = '/assets/maps/morocco-regions-2026.svg';
    const POLL_INTERVAL_MS = 30_000;
    const REQUEST_TIMEOUT_MS = 10_000;
    const MAX_POLL_BACKOFF_MS = 5 * 60_000;
    const COALITION_DEBOUNCE_MS = 250;
    const MAX_COALITION_PARTIES = 5;
    const REGION_FILTERS = window.FhemniElectionRegionFilters;
    const SEAT_TYPES = REGION_FILTERS.SEAT_TYPES;
    const deferredRegionRender = REGION_FILTERS.createDeferredAction(callback => window.queueMicrotask(callback));
    const PARTY_CLASSES = new Set(['rni', 'pam', 'pi', 'pjd', 'usfp', 'pps', 'mp', 'fgd', 'uc', 'ffd', 'mds', 'pud', 'psu', 'pe', 'pml', 'pvm', 'nd', 'pgv', 'pedd', 'prv', 'pdn', 'alamal', 'prd', 'umd', 'ind']);
    const COPY = {
        ar: {
            title: 'نتائج الانتخابات التشريعية 2026', description: 'تابع توزيع المقاعد وطنياً وحسب الجهات، وجرّب تبني أغلبية برلمانية.', eyebrow: 'تشريعيات 2026',
            loading: 'كنجيبو آخر النتائج…',
            retry: 'عاود جرّب', errorTitle: 'ما قدرناش نجيبو النتائج دابا', errorText: 'عاود جرّب من بعد لحظات.',
            status: { SCHEDULED: 'قريباً', COUNTING: 'الفرز جاري', PRELIMINARY: 'نتائج مؤقتة', FINAL: 'نتائج نهائية', CORRECTED: 'نتائج مصححة' },
            updated: 'آخر تحديث {date}', stale: 'التحديث متوقف مؤقتاً · هاد آخر نتائج متوفرة', progress: 'تقدم النتائج', seatsDeclared: '{declared} من {total} مقعد معلن',
            metrics: ['المقاعد المعلنة', 'المشاركة', 'مقاعد الدوائر المحلية', 'مقاعد اللوائح الجهوية'],
            tabs: ['الخريطة والجهات', 'النتائج الوطنية', 'كوّن الأغلبية ديالك'],
            mapTitle: 'النتائج حسب الجهات', mapIntro: 'دوز فوق أي جهة، ولا اختارها، باش تشوف الأحزاب والمقاعد المعلنة فيها.', mapSelect: 'اختار الجهة', mapLegend: 'لون محايد: الخريطة ما كتنسبش الجهة لحزب واحد.',
            regionStatus: { PENDING: 'في انتظار النتائج', PARTIAL: 'نتائج جزئية', FINAL: 'نتائج نهائية' }, regionPending: 'النتائج مازال ما تعلناتش فهاد الجهة.', regionSeats: '{count} مقعد معلن', regionAllocated: '{count} مقعد مخصص', regionPollUpdated: 'تحدثات نتائج {region}: {count} {unit} معلن.', seats: 'مقاعد', seat: 'مقعد', winner: 'منتخب', constituency: 'الدائرة الانتخابية', localWinners: 'الدوائر المحلية', regionalList: 'اللائحة الجهوية', winnerStatus: { PRELIMINARY: 'مؤقت', FINAL: 'نهائي', CORRECTED: 'مصحح' },
            filters: 'فلتر النتائج', filtersActive: 'فلتر النتائج، {count} مفعّلين', activeFilters: 'الفلاتر المفعّلين', clearFilters: 'مسح الكل', filtersCleared: 'تم مسح الفلاتر.', seatType: 'نوع المقعد', allSeats: 'كل المقاعد', localSeats: 'المقاعد المحلية', regionalSeats: 'مقاعد اللائحة الجهوية', partyFilter: 'الحزب', allParties: 'كل الأحزاب', constituencyFilter: 'الدائرة', allConstituencies: 'كل الدوائر', constituencyUnavailable: 'ما كتطبقش على مقاعد اللائحة الجهوية.', constituencyCleared: 'تحيد فلتر الدائرة حيث ما كينطبقش على مقاعد اللائحة الجهوية.', noConstituencies: 'مازال ما تنشرات حتى دائرة.', filterSummary: '{parties} {partyUnit} · {count} {countUnit}', filterPartyOne: 'حزب', filterPartyMany: 'أحزاب', filterSeatOne: 'مقعد', filterSeatMany: 'مقاعد', filterWinnerOne: 'منتخب', filterWinnerMany: 'منتخب', noFilterResults: 'ما لقينا حتى نتيجة بهاد الفلاتر.', removeFilter: 'حيد فلتر {label}', missingWinnerName: 'اسم منتخب واحد مازال ما تنشرش.', missingWinnerNames: 'أسامي {count} من المنتخبين مازال ما تنشروش.',
            nationalTitle: 'توزيع المقاعد على الأحزاب', nationalIntro: 'الأحزاب مرتبة حسب عدد المقاعد المعلنة. ما كنعلنوش على أغلبية هنا؛ جرّب التحالفات فالأداة.', noResults: 'مازال ما كاين حتى مقعد معلن. هاد الصفحة غادي تتحدّث مباشرة ملي تدخل النتائج الرسمية.', votes: '{count} صوت', voteShare: '{percent}% من الأصوات',
            coalitionTitle: 'كوّن الأغلبية ديالك', coalitionIntro: 'الحزب المتصدر ثابت. زيد حتى لـ4 أحزاب وشوف واش توصل للأغلبية، وشنو مستوى التقارب بين البرامج.', coalitionLeader: 'الحزب المتصدر', coalitionSummary: 'التحالف ديالك', coalitionSeats: 'مقعد من 395', coalitionNeed: 'خاصك {count} مقعد آخر باش توصل للأغلبية.', coalitionWon: 'وصلتي للأغلبية بـ{count} مقعد زيادة.', coalitionStart: 'زيد حزب آخر على الأقل باش نحسبو التقارب.', coalitionNoResults: 'الأداة غادي تولّي متاحة ملي تتعلن المقاعد.',
            alignment: 'التقارب البرنامجي', alignmentStrong: 'تقارب قوي', alignmentMedium: 'تقارب متوسط', alignmentWeak: 'تقارب ضعيف', alignmentLoading: 'كنحسبو التقارب…', alignmentMissing: 'المعطيات المنشورة ما كافياش باش نعطيو نقطة عادلة.', coverage: 'التغطية {percent}% · {questions} أسئلة قابلة للمقارنة', agreements: 'أقوى نقاط الالتقاء', tensions: 'أبرز نقاط الاختلاف', none: 'ما كايناش نقطة بارزة',
            method: 'نقطة التقارب كتستعمل غير المواقف الموثقة من البرامج المنشورة. المواقف الناقصة ولا «ما كاينش موقف» ما كتتحسبش كموقف محايد.', sourceTitle: 'المصدر', sourceOpen: 'شوف المصدر الرسمي'
        },
        fr: {
            title: 'Résultats des législatives 2026', description: 'Suivez la répartition des sièges au niveau national et régional, puis composez votre majorité.', eyebrow: 'Législatives 2026',
            loading: 'Chargement des derniers résultats…',
            retry: 'Réessayer', errorTitle: 'Impossible de charger les résultats', errorText: 'Réessayez dans quelques instants.',
            status: { SCHEDULED: 'À venir', COUNTING: 'Dépouillement en cours', PRELIMINARY: 'Résultats provisoires', FINAL: 'Résultats définitifs', CORRECTED: 'Résultats corrigés' },
            updated: 'Mise à jour {date}', stale: 'Actualisation momentanément interrompue · derniers résultats affichés', progress: 'Progression des résultats', seatsDeclared: '{declared} sièges déclarés sur {total}',
            metrics: ['Sièges déclarés', 'Participation', 'Sièges locaux', 'Sièges des listes régionales'],
            tabs: ['Carte et régions', 'Résultats nationaux', 'Composez votre majorité'],
            mapTitle: 'Résultats régionaux', mapIntro: 'Survolez, ciblez ou touchez une région pour voir tous les partis et sièges déclarés.', mapSelect: 'Choisir une région', mapLegend: 'Couleur neutre : une région peut compter plusieurs partis.',
            regionStatus: { PENDING: 'En attente', PARTIAL: 'Résultats partiels', FINAL: 'Résultats définitifs' }, regionPending: 'Aucun résultat n’a encore été publié pour cette région.', regionSeats: '{count} sièges déclarés', regionAllocated: '{count} sièges attribués', regionPollUpdated: 'Résultats actualisés pour {region} : {count} {unit} déclarés.', seats: 'sièges', seat: 'siège', winner: 'Élu', constituency: 'Circonscription', localWinners: 'Circonscriptions locales', regionalList: 'Liste régionale', winnerStatus: { PRELIMINARY: 'Provisoire', FINAL: 'Définitif', CORRECTED: 'Corrigé' },
            filters: 'Filtrer les résultats', filtersActive: 'Filtres, {count} actifs', activeFilters: 'Filtres actifs', clearFilters: 'Tout effacer', filtersCleared: 'Filtres effacés.', seatType: 'Type de siège', allSeats: 'Tous les sièges', localSeats: 'Sièges locaux', regionalSeats: 'Sièges de liste régionale', partyFilter: 'Parti', allParties: 'Tous les partis', constituencyFilter: 'Circonscription', allConstituencies: 'Toutes les circonscriptions', constituencyUnavailable: 'Non applicable aux sièges de liste régionale.', constituencyCleared: 'Le filtre de circonscription a été retiré car il ne s’applique pas aux sièges de liste régionale.', noConstituencies: 'Aucune circonscription publiée pour le moment.', filterSummary: '{parties} {partyUnit} · {count} {countUnit}', filterPartyOne: 'parti', filterPartyMany: 'partis', filterSeatOne: 'siège', filterSeatMany: 'sièges', filterWinnerOne: 'élu', filterWinnerMany: 'élus', noFilterResults: 'Aucun résultat ne correspond à ces filtres.', removeFilter: 'Retirer le filtre {label}', missingWinnerName: 'Le nom d’un élu n’est pas encore publié.', missingWinnerNames: '{count} noms d’élus ne sont pas encore publiés.',
            nationalTitle: 'Répartition des sièges par parti', nationalIntro: 'Les partis sont classés par sièges déclarés. La majorité est explorée séparément dans le simulateur.', noResults: 'Aucun siège n’a encore été déclaré. La page se mettra à jour dès l’ajout des résultats officiels.', votes: '{count} voix', voteShare: '{percent}% des voix',
            coalitionTitle: 'Composez votre majorité', coalitionIntro: 'Le parti arrivé en tête est fixé. Ajoutez jusqu’à 4 partis, atteignez 198 sièges et consultez la proximité de leurs programmes.', coalitionLeader: 'Parti arrivé en tête', coalitionSummary: 'Votre coalition', coalitionSeats: 'sièges sur 395', coalitionNeed: 'Il manque {count} sièges pour obtenir la majorité.', coalitionWon: 'Majorité atteinte avec {count} sièges d’avance.', coalitionStart: 'Ajoutez au moins un autre parti pour calculer leur proximité.', coalitionNoResults: 'Le simulateur sera disponible dès la publication des sièges.',
            alignment: 'Proximité programmatique', alignmentStrong: 'Forte proximité', alignmentMedium: 'Proximité moyenne', alignmentWeak: 'Faible proximité', alignmentLoading: 'Calcul de la proximité…', alignmentMissing: 'Les données publiées ne suffisent pas pour fournir un score honnête.', coverage: 'Couverture {percent}% · {questions} questions comparables', agreements: 'Principaux points d’accord', tensions: 'Principaux points de tension', none: 'Aucun thème saillant',
            method: 'Le score utilise uniquement les positions documentées dans les programmes publiés. Une position absente ou non définie n’est jamais traitée comme neutre.', sourceTitle: 'Source', sourceOpen: 'Ouvrir la source officielle'
        },
        en: {
            title: '2026 legislative election results', description: 'Follow national and regional seat distribution, then build a parliamentary majority.', eyebrow: '2026 legislative election',
            loading: 'Loading the latest results…',
            retry: 'Try again', errorTitle: 'We could not load the results', errorText: 'Please try again in a moment.',
            status: { SCHEDULED: 'Coming soon', COUNTING: 'Counting in progress', PRELIMINARY: 'Preliminary results', FINAL: 'Final results', CORRECTED: 'Corrected results' },
            updated: 'Updated {date}', stale: 'Live refresh temporarily unavailable · showing the latest available results', progress: 'Results progress', seatsDeclared: '{declared} of {total} seats declared',
            metrics: ['Seats declared', 'Turnout', 'Local seats', 'Regional-list seats'],
            tabs: ['Map and regions', 'National results', 'Build your majority'],
            mapTitle: 'Regional results', mapIntro: 'Hover, focus or tap a region to see every party and declared seat.', mapSelect: 'Choose a region', mapLegend: 'Neutral colour: each region can contain several parties.',
            regionStatus: { PENDING: 'Awaiting results', PARTIAL: 'Partial results', FINAL: 'Final results' }, regionPending: 'No results have been published for this region yet.', regionSeats: '{count} seats declared', regionAllocated: '{count} seats allocated', regionPollUpdated: 'Results updated for {region}: {count} {unit} declared.', seats: 'seats', seat: 'seat', winner: 'Winner', constituency: 'Constituency', localWinners: 'Local constituencies', regionalList: 'Regional list', winnerStatus: { PRELIMINARY: 'Preliminary', FINAL: 'Final', CORRECTED: 'Corrected' },
            filters: 'Filter results', filtersActive: 'Filters, {count} active', activeFilters: 'Active filters', clearFilters: 'Clear all', filtersCleared: 'Filters cleared.', seatType: 'Seat type', allSeats: 'All seats', localSeats: 'Local seats', regionalSeats: 'Regional-list seats', partyFilter: 'Party', allParties: 'All parties', constituencyFilter: 'Constituency', allConstituencies: 'All constituencies', constituencyUnavailable: 'Not applicable to regional-list seats.', constituencyCleared: 'The constituency filter was removed because it does not apply to regional-list seats.', noConstituencies: 'No constituencies have been published yet.', filterSummary: '{parties} {partyUnit} · {count} {countUnit}', filterPartyOne: 'party', filterPartyMany: 'parties', filterSeatOne: 'seat', filterSeatMany: 'seats', filterWinnerOne: 'winner', filterWinnerMany: 'winners', noFilterResults: 'No results match these filters.', removeFilter: 'Remove {label} filter', missingWinnerName: '1 winner name has not been published yet.', missingWinnerNames: '{count} winner names have not been published yet.',
            nationalTitle: 'Seats by party', nationalIntro: 'Parties are ranked by declared seats. Majority-building is explored separately in the coalition tool.', noResults: 'No seats have been declared yet. This page will update when official results are entered.', votes: '{count} votes', voteShare: '{percent}% of votes',
            coalitionTitle: 'Build your majority', coalitionIntro: 'The leading party is fixed. Add up to 4 parties, reach 198 seats, and see how closely their published programmes align.', coalitionLeader: 'Leading party', coalitionSummary: 'Your coalition', coalitionSeats: 'seats out of 395', coalitionNeed: '{count} more seats needed for a majority.', coalitionWon: 'Majority reached with {count} seats to spare.', coalitionStart: 'Add at least one other party to calculate programme alignment.', coalitionNoResults: 'The builder will be available once seats are published.',
            alignment: 'Programme alignment', alignmentStrong: 'Strong alignment', alignmentMedium: 'Medium alignment', alignmentWeak: 'Weak alignment', alignmentLoading: 'Calculating alignment…', alignmentMissing: 'The published data is not sufficient for an honest score.', coverage: '{percent}% coverage · {questions} comparable questions', agreements: 'Strongest common ground', tensions: 'Main tensions', none: 'No standout theme',
            method: 'Alignment uses documented positions from published programmes only. Missing and “no position” entries are never treated as neutral.', sourceTitle: 'Source', sourceOpen: 'Open official source'
        }
    };

    let snapshot;
    let locale;
    let copy;
    let selectedRegionKey;
    let renderedRegionKey;
    let renderedRegionSignature;
    let regionFilterState = REGION_FILTERS.normalizeState();
    let regionFiltersExpanded = false;
    let selectedPartyCodes = new Set();
    let coalitionRequest = 0;
    let coalitionTimer;
    let coalitionAbortController;
    let pollTimer;
    let pollFailureCount = 0;
    let resultRequestInFlight = false;
    let resultAbortController;
    let pendingManualRetry = false;
    let pendingFreshReload = false;
    let regionStatusAnnouncer;

    const byId = id => document.getElementById(id);
    const format = (template, values) => Object.entries(values).reduce((text, entry) => text.replaceAll(`{${entry[0]}}`, entry[1]), template);
    const number = value => new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale).format(value ?? 0);
    const percent = value => value === null || value === undefined ? '—' : new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale, { maximumFractionDigits: 1 }).format(value);
    const exactPercent = value => value === null || value === undefined ? '—' : new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    const widthClass = value => `priority-width-${Math.max(0, Math.min(100, Math.round(value || 0)))}`;

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        locale = window.FhemniI18n?.locale() || 'ar';
        copy = COPY[locale] || COPY.ar;
        regionFiltersExpanded = false;
        regionStatusAnnouncer = REGION_FILTERS.createLiveRegionAnnouncer(
            byId('electionRegionFilterStatus'),
            callback => window.queueMicrotask(callback)
        );
        applyCopy();
        bindTabs();
        byId('electionRetry').addEventListener('click', () => load());
        byId('electionRegionSelect').addEventListener('change', event => selectRegion(event.target.value, true));
        byId('electionRegionDetails').addEventListener('focusout', handleRegionFilterFocusOut);
        load();
        document.addEventListener('visibilitychange', handleVisibilityChange);
        document.addEventListener('fhemni:localechange', handleLocaleChange);
    }

    function applyCopy() {
        document.documentElement.lang = locale;
        document.documentElement.dir = locale === 'ar' ? 'rtl' : 'ltr';
        document.title = `${copy.title} — Fhemni`;
        byId('electionMetaDescription').content = copy.description;
        setText('electionLoadingText', copy.loading); setText('electionEyebrow', copy.eyebrow); setText('electionTitle', copy.title);
        setText('electionRetry', copy.retry); setText('electionErrorTitle', copy.errorTitle); setText('electionErrorText', copy.errorText);
        setText('electionProgressLabel', copy.progress);
        setText('electionMapTab', copy.tabs[0]); setText('electionNationalTab', copy.tabs[1]); setText('electionCoalitionTab', copy.tabs[2]);
        setText('electionMapTitle', copy.mapTitle); setText('electionMapIntro', copy.mapIntro); setText('electionRegionSelectLabel', copy.mapSelect); setText('electionMapLegend', copy.mapLegend);
        setText('electionNationalTitle', copy.nationalTitle); setText('electionNationalIntro', copy.nationalIntro); setText('electionNationalEmpty', copy.noResults);
        setText('electionCoalitionTitle', copy.coalitionTitle); setText('electionCoalitionIntro', copy.coalitionIntro); setText('electionCoalitionSummaryLabel', copy.coalitionSummary); setText('electionCoalitionSeatUnit', copy.coalitionSeats); setText('electionCoalitionEmpty', copy.coalitionNoResults);
        setText('electionAlignmentLabel', copy.alignment); setText('electionAgreementTitle', copy.agreements); setText('electionTensionTitle', copy.tensions); setText('electionAlignmentNote', copy.method); setText('electionSourceTitle', copy.sourceTitle); setText('electionSourceLink', copy.sourceOpen);
    }

    async function load(options = {}) {
        if (resultRequestInFlight) {
            if (!options.poll) {
                pendingManualRetry = true;
                pendingFreshReload ||= Boolean(options.fresh);
                resultAbortController?.abort();
            }
            return;
        }
        resultRequestInFlight = true;
        if (!snapshot) showState('loading');
        const controller = new AbortController();
        resultAbortController = controller;
        const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
        try {
            const response = await fetch(`${RESULT_URL}?lang=${encodeURIComponent(locale)}`, {
                headers: { Accept: 'application/json' },
                cache: options.fresh ? 'no-store' : 'default',
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            snapshot = await response.json();
            pollFailureCount = 0;
            byId('electionStale').hidden = true;
            render({ poll: Boolean(options.poll) });
            showState('content');
            if (!options.poll) track('election_results_opened', { election_year: 2026, result_status: snapshot.election.status });
        } catch (error) {
            if (error.name === 'AbortError' && pendingManualRetry) return;
            pollFailureCount++;
            if (!snapshot) {
                showState('error');
            } else {
                setText('electionStale', copy.stale);
                byId('electionStale').hidden = false;
            }
            console.error('Election results could not be loaded.', error);
        } finally {
            window.clearTimeout(timeout);
            resultRequestInFlight = false;
            resultAbortController = undefined;
            if (pendingManualRetry) {
                const fresh = pendingFreshReload;
                pendingManualRetry = false;
                pendingFreshReload = false;
                load({ fresh });
            } else {
                schedulePoll(nextPollDelay());
            }
        }
    }

    function showState(state) {
        byId('electionLoading').hidden = state !== 'loading';
        byId('electionError').hidden = state !== 'error';
        byId('electionContent').hidden = state !== 'content';
    }

    function schedulePoll(delay = POLL_INTERVAL_MS) {
        window.clearTimeout(pollTimer);
        if (!document.hidden) pollTimer = window.setTimeout(() => load({ poll: true }), delay);
    }

    function nextPollDelay() {
        if (!pollFailureCount) return POLL_INTERVAL_MS;
        return Math.min(POLL_INTERVAL_MS * (2 ** Math.min(pollFailureCount, 4)), MAX_POLL_BACKOFF_MS);
    }

    function handleVisibilityChange() {
        window.clearTimeout(pollTimer);
        if (!document.hidden) load({ poll: true });
    }

    function handleLocaleChange(event) {
        const nextLocale = event.detail?.locale || window.FhemniI18n?.locale() || 'ar';
        if (!COPY[nextLocale] || nextLocale === locale) return;
        locale = nextLocale;
        copy = COPY[locale];
        applyCopy();
        hideTooltip();
        window.clearTimeout(pollTimer);
        window.clearTimeout(coalitionTimer);
        coalitionAbortController?.abort();
        coalitionRequest++;
        deferredRegionRender.cancel();
        byId('electionAlignment').hidden = true;
        load({ fresh: true });
        scheduleCoalitionEvaluation();
    }

    function render(options = {}) {
        renderOverview(); renderMetrics(); renderRegionSelect(options); renderMap(); renderNational(); renderCoalitionParties(); renderSource();
    }

    function renderOverview() {
        const election = snapshot.election;
        setText('electionStatus', copy.status[election.status] || election.status);
        const checkedAt = election.updatedAt || election.sourceUpdatedAt;
        setText('electionUpdated', checkedAt ? format(copy.updated, { date: new Intl.DateTimeFormat(locale === 'ar' ? 'ar-MA' : locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(checkedAt)) }) : '');
        const progress = election.totalSeats ? election.declaredSeats * 100 / election.totalSeats : 0;
        setText('electionProgressValue', `${number(Math.round(progress))}%`);
        setText('electionProgressTitle', format(copy.seatsDeclared, { declared: number(election.declaredSeats), total: number(election.totalSeats) }));
        setWidth(byId('electionProgressBar'), progress);
    }

    function renderMetrics() {
        const election = snapshot.election;
        const values = [null, election.turnoutPercent == null ? '—' : `${exactPercent(election.turnoutPercent)}%`, election.localSeats == null ? '—' : number(election.localSeats), election.regionalListSeats == null ? '—' : number(election.regionalListSeats)];
        const root = clear('electionMetrics');
        copy.metrics.forEach((label, index) => {
            const value = index === 0 ? declaredSeatMetric(election) : element('strong', '', values[index]);
            root.append(card('article', 'election-metric', [element('span', '', label), value]));
        });
    }

    function declaredSeatMetric(election) {
        const value = element('strong');
        if (locale !== 'ar') {
            value.textContent = `${number(election.declaredSeats)} / ${number(election.totalSeats)}`;
            return value;
        }
        value.append(
            element('bdi', '', number(election.declaredSeats)),
            element('span', 'election-metric-separator', 'من'),
            element('bdi', '', number(election.totalSeats))
        );
        return value;
    }

    function renderRegionSelect(options = {}) {
        const select = clear('electionRegionSelect');
        snapshot.regions.forEach(region => {
            const option = element('option', '', region.name);
            option.value = region.mapKey;
            select.append(option);
        });
        if (!selectedRegionKey || !snapshot.regions.some(region => region.mapKey === selectedRegionKey)) {
            const nextRegionKey = snapshot.regions[0]?.mapKey;
            if (selectedRegionKey && nextRegionKey !== selectedRegionKey) resetRegionFilters();
            selectedRegionKey = nextRegionKey;
        }
        select.value = selectedRegionKey || '';
        const region = regionByKey(selectedRegionKey);
        if (options.poll && renderedRegionKey === selectedRegionKey && renderedRegionSignature === regionSignature(region)) return;
        const focusedFilterControl = byId('electionRegionDetails').querySelector('.election-region-filter-panel')?.contains(document.activeElement);
        if (options.poll && renderedRegionKey === selectedRegionKey && focusedFilterControl) {
            deferredRegionRender.defer(selectedRegionKey);
            return;
        }
        renderRegionDetails(region);
        if (options.poll) announceRegionPollUpdate(region);
    }

    async function renderMap() {
        const canvas = byId('electionMapCanvas');
        if (!canvas.querySelector('svg')) {
            try {
                const response = await fetch(MAP_URL);
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const documentNode = new DOMParser().parseFromString(await response.text(), 'image/svg+xml');
                const svg = document.importNode(documentNode.documentElement, true);
                svg.classList.add('election-map-svg');
                canvas.replaceChildren(svg);
            } catch (error) {
                console.error('Election map could not be loaded.', error);
                return;
            }
        }
        canvas.querySelectorAll('[data-region-key]').forEach(node => {
            const region = regionByKey(node.dataset.regionKey);
            node.classList.toggle('is-selected', node.dataset.regionKey === selectedRegionKey);
            node.classList.toggle('has-results', Boolean(region?.declaredSeats));
            node.setAttribute('aria-label', region?.name || node.dataset.regionKey);
            node.onclick = () => selectRegion(node.dataset.regionKey, true);
            node.onmouseenter = () => showTooltip(region);
            node.onmouseleave = hideTooltip;
            node.onfocus = () => showTooltip(region);
            node.onblur = hideTooltip;
            node.onkeydown = event => {
                if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); selectRegion(node.dataset.regionKey, true); }
            };
        });
    }

    function selectRegion(mapKey, report) {
        const filtersWereActive = mapKey !== selectedRegionKey && REGION_FILTERS.activeFilterCount(regionFilterState) > 0;
        if (mapKey !== selectedRegionKey) {
            deferredRegionRender.cancel();
            resetRegionFilters();
        }
        selectedRegionKey = mapKey;
        byId('electionRegionSelect').value = mapKey;
        renderRegionDetails(regionByKey(mapKey));
        renderMap();
        if (filtersWereActive) announceRegionFilterStatus(copy.filtersCleared);
        if (report) track('election_region_selected', { election_year: 2026, region: mapKey });
    }

    function regionByKey(mapKey) { return snapshot?.regions.find(region => region.mapKey === mapKey); }

    function resetRegionFilters() {
        regionFilterState = REGION_FILTERS.normalizeState();
        regionFiltersExpanded = false;
    }

    function renderRegionDetails(region) {
        const root = byId('electionRegionDetails');
        const preserveUi = Boolean(region && renderedRegionKey === region.mapKey);
        const previousScrollTop = preserveUi ? root.scrollTop : 0;
        const focusedId = preserveUi && root.contains(document.activeElement) ? document.activeElement.id : '';
        root.replaceChildren();
        if (!region) { renderedRegionKey = undefined; renderedRegionSignature = undefined; return; }
        root.append(element('span', 'section-kicker', copy.regionStatus[region.status] || region.status), element('h3', '', region.name));
        const seatText = region.allocatedSeats == null
            ? format(copy.regionSeats, { count: number(region.declaredSeats) })
            : `${format(copy.regionSeats, { count: number(region.declaredSeats) })} · ${format(copy.regionAllocated, { count: number(region.allocatedSeats) })}`;
        root.append(element('p', 'election-region-seat-total', seatText));
        if (!region.parties.length) {
            root.append(element('p', 'election-region-pending', copy.regionPending));
            finishRegionDetailsRender(root, region, preserveUi, previousScrollTop, focusedId);
            return;
        }
        const options = REGION_FILTERS.regionFilterOptions(region);
        regionFilterState = reconcileRegionFilterState(regionFilterState, options);
        const result = REGION_FILTERS.filterRegion(region, regionFilterState);
        regionFilterState = result.state;
        root.append(regionFilterControls(options, result));
        const list = element('div', 'election-region-parties');
        if (!result.parties.length) list.append(regionFilterEmpty());
        else result.parties.forEach(party => list.append(partyRow(party, false, result.countKind)));
        root.append(list);
        finishRegionDetailsRender(root, region, preserveUi, previousScrollTop, focusedId);
    }

    function finishRegionDetailsRender(root, region, preserveUi, scrollTop, focusedId) {
        renderedRegionKey = region.mapKey;
        renderedRegionSignature = regionSignature(region);
        if (!preserveUi) { root.scrollTop = 0; return; }
        root.scrollTop = scrollTop;
        if (focusedId) byId(focusedId)?.focus({ preventScroll: true });
    }

    function reconcileRegionFilterState(state, options) {
        const next = { ...state };
        if (next.partyCode && !options.parties.some(party => party.code === next.partyCode)) next.partyCode = '';
        if (next.constituencyCode && !options.constituencies.some(constituency => constituency.code === next.constituencyCode)) next.constituencyCode = '';
        return REGION_FILTERS.normalizeState(next);
    }

    function regionFilterControls(options, result) {
        const panel = element('section', 'election-region-filter-panel');
        panel.classList.toggle('is-expanded', regionFiltersExpanded);
        panel.setAttribute('aria-label', copy.filters);
        const activeCount = REGION_FILTERS.activeFilterCount(regionFilterState);
        const heading = element('div', 'election-region-filter-heading');
        const toggle = element('button', 'election-region-filter-toggle');
        toggle.id = 'electionRegionFilterToggle';
        toggle.type = 'button';
        toggle.setAttribute('aria-expanded', String(regionFiltersExpanded));
        toggle.setAttribute('aria-controls', 'electionRegionFilterFields');
        toggle.setAttribute('aria-label', activeCount ? format(copy.filtersActive, { count: number(activeCount) }) : copy.filters);
        toggle.append(element('span', 'election-region-filter-label', copy.filters));
        if (activeCount) {
            const badge = element('span', 'election-region-filter-count', number(activeCount));
            badge.setAttribute('aria-hidden', 'true');
            toggle.append(badge);
        }
        toggle.append(element('span', 'election-region-filter-chevron', '⌄'));
        toggle.lastElementChild.setAttribute('aria-hidden', 'true');
        toggle.addEventListener('click', () => {
            regionFiltersExpanded = !regionFiltersExpanded;
            renderRegionDetails(regionByKey(selectedRegionKey));
            flushDeferredRegionRender();
        });
        heading.append(toggle);
        if (activeCount) {
            const clearButton = element('button', 'election-region-filter-clear', copy.clearFilters);
            clearButton.id = 'electionRegionFilterClear';
            clearButton.type = 'button';
            clearButton.addEventListener('click', () => clearRegionFilters(true));
            heading.append(clearButton);
        }
        panel.append(heading);

        const fields = element('div', 'election-region-filter-fields');
        fields.id = 'electionRegionFilterFields';
        fields.hidden = !regionFiltersExpanded;
        fields.append(
            regionFilterSelect(
                'electionRegionSeatTypeFilter',
                copy.seatType,
                [
                    { value: SEAT_TYPES.ALL, label: copy.allSeats },
                    { value: SEAT_TYPES.LOCAL, label: copy.localSeats },
                    { value: SEAT_TYPES.REGIONAL, label: copy.regionalSeats }
                ],
                regionFilterState.seatType,
                value => changeRegionFilter('seat_type', { seatType: value })
            ),
            regionFilterSelect(
                'electionRegionPartyFilter',
                copy.partyFilter,
                [{ value: '', label: copy.allParties }, ...options.parties.map(party => ({ value: party.code, label: party.name }))],
                regionFilterState.partyCode,
                value => changeRegionFilter('party', { partyCode: value })
            ),
            regionFilterSelect(
                'electionRegionConstituencyFilter',
                copy.constituencyFilter,
                [{ value: '', label: copy.allConstituencies }, ...options.constituencies.map(constituency => ({ value: constituency.code, label: constituency.name }))],
                regionFilterState.constituencyCode,
                value => changeRegionFilter('constituency', { constituencyCode: value }),
                {
                    disabled: regionFilterState.seatType === SEAT_TYPES.REGIONAL || !options.constituencies.length,
                    helper: regionFilterState.seatType === SEAT_TYPES.REGIONAL
                        ? copy.constituencyUnavailable
                        : (!options.constituencies.length ? copy.noConstituencies : '')
                }
            )
        );
        panel.append(fields);

        const chips = regionFilterChips(options);
        if (chips.childElementCount) panel.append(chips);
        panel.append(element('p', 'election-region-filter-summary', regionFilterSummary(result)));
        return panel;
    }

    function regionFilterSelect(id, label, options, value, onChange, settings = {}) {
        const field = element('label', 'election-region-filter-field');
        field.htmlFor = id;
        field.append(element('span', '', label));
        const select = document.createElement('select');
        select.id = id;
        select.dir = 'auto';
        select.disabled = Boolean(settings.disabled);
        options.forEach(item => {
            const option = element('option', '', item.label);
            option.value = item.value;
            select.append(option);
        });
        select.value = value;
        select.addEventListener('change', event => onChange(event.target.value));
        field.append(select);
        if (settings.helper) {
            const helper = element('small', 'election-region-filter-helper', settings.helper);
            helper.id = `${id}Help`;
            select.setAttribute('aria-describedby', helper.id);
            field.append(helper);
        }
        return field;
    }

    function regionFilterChips(options) {
        const chips = element('div', 'election-region-filter-chips');
        chips.setAttribute('role', 'group');
        chips.setAttribute('aria-label', copy.activeFilters);
        const seatLabels = { [SEAT_TYPES.LOCAL]: copy.localSeats, [SEAT_TYPES.REGIONAL]: copy.regionalSeats };
        if (regionFilterState.seatType !== SEAT_TYPES.ALL) {
            chips.append(regionFilterChip(seatLabels[regionFilterState.seatType], () => changeRegionFilter('seat_type', { seatType: SEAT_TYPES.ALL })));
        }
        if (regionFilterState.partyCode) {
            const party = options.parties.find(item => item.code === regionFilterState.partyCode);
            chips.append(regionFilterChip(party?.name || regionFilterState.partyCode, () => changeRegionFilter('party', { partyCode: '' })));
        }
        if (regionFilterState.constituencyCode) {
            const constituency = options.constituencies.find(item => item.code === regionFilterState.constituencyCode);
            chips.append(regionFilterChip(constituency?.name || regionFilterState.constituencyCode, () => changeRegionFilter('constituency', { constituencyCode: '' })));
        }
        return chips;
    }

    function regionFilterChip(label, onRemove) {
        const button = element('button', 'election-region-filter-chip');
        button.type = 'button';
        button.setAttribute('aria-label', format(copy.removeFilter, { label }));
        button.append(element('bdi', '', label), element('span', '', '×'));
        button.lastElementChild.setAttribute('aria-hidden', 'true');
        button.addEventListener('click', () => {
            onRemove();
            byId('electionRegionFilterToggle')?.focus({ preventScroll: true });
        });
        return button;
    }

    function changeRegionFilter(filterType, change) {
        const region = regionByKey(selectedRegionKey);
        if (!region) return;
        const previousConstituencyCode = regionFilterState.constituencyCode;
        regionFilterState = REGION_FILTERS.normalizeState({ ...regionFilterState, ...change });
        regionFilterState = reconcileRegionFilterState(regionFilterState, REGION_FILTERS.regionFilterOptions(region));
        const result = REGION_FILTERS.filterRegion(region, regionFilterState);
        regionFilterState = result.state;
        const constituencyWasCleared = Boolean(previousConstituencyCode && !regionFilterState.constituencyCode);
        track('election_region_filtered', {
            election_year: 2026,
            region: region.mapKey,
            filter_type: filterType,
            filter_value: change.seatType || change.partyCode || change.constituencyCode || 'all',
            active_filter_count: REGION_FILTERS.activeFilterCount(regionFilterState),
            result_party_count: result.parties.length
        });
        renderRegionDetails(region);
        deferredRegionRender.cancel();
        announceRegionFilterStatus(constituencyWasCleared ? copy.constituencyCleared : '', regionFilterSummary(result));
    }

    function clearRegionFilters(report) {
        const region = regionByKey(selectedRegionKey);
        if (!region) return;
        regionFilterState = REGION_FILTERS.normalizeState();
        const result = REGION_FILTERS.filterRegion(region, regionFilterState);
        if (report) track('election_region_filtered', {
            election_year: 2026,
            region: region.mapKey,
            filter_type: 'clear_all',
            filter_value: 'all',
            active_filter_count: 0,
            result_party_count: result.parties.length
        });
        renderRegionDetails(region);
        deferredRegionRender.cancel();
        announceRegionFilterStatus(copy.filtersCleared, regionFilterSummary(result));
        byId('electionRegionFilterToggle')?.focus({ preventScroll: true });
    }

    function regionFilterSummary(result) {
        const partyUnit = result.parties.length === 1 ? copy.filterPartyOne : copy.filterPartyMany;
        const countUnit = filterCountUnit(result.countKind, result.totalCount);
        return format(copy.filterSummary, {
            parties: number(result.parties.length),
            partyUnit,
            count: number(result.totalCount),
            countUnit
        });
    }

    function filterCountUnit(countKind, count) {
        if (countKind === 'publishedWinners') return count === 1 ? copy.filterWinnerOne : copy.filterWinnerMany;
        return count === 1 ? copy.filterSeatOne : copy.filterSeatMany;
    }

    function announceRegionFilterStatus(...messages) {
        const message = messages.filter(Boolean).join(' ');
        regionStatusAnnouncer.announce(message);
    }

    function announceRegionPollUpdate(region) {
        if (!region) return;
        regionStatusAnnouncer.announce(format(copy.regionPollUpdated, {
            region: region.name,
            count: number(region.declaredSeats),
            unit: region.declaredSeats === 1 ? copy.seat : copy.seats
        }), { repeat: true });
    }

    function regionSignature(region) {
        if (!region) return '';
        return JSON.stringify({
            name: region.name,
            status: region.status,
            declaredSeats: region.declaredSeats,
            allocatedSeats: region.allocatedSeats,
            parties: region.parties
        });
    }

    function handleRegionFilterFocusOut(event) {
        const panel = byId('electionRegionDetails').querySelector('.election-region-filter-panel');
        if (event.relatedTarget && panel?.contains(event.relatedTarget)) return;
        flushDeferredRegionRender();
    }

    function flushDeferredRegionRender() {
        deferredRegionRender.flush(mapKey => {
            if (mapKey !== selectedRegionKey) return;
            const region = regionByKey(mapKey);
            if (renderedRegionSignature !== regionSignature(region)) renderRegionDetails(region);
            announceRegionPollUpdate(region);
        });
    }

    function regionFilterEmpty() {
        const empty = element('div', 'election-region-filter-empty');
        empty.append(element('p', '', copy.noFilterResults));
        const clearButton = element('button', '', copy.clearFilters);
        clearButton.type = 'button';
        clearButton.addEventListener('click', () => clearRegionFilters(true));
        empty.append(clearButton);
        return empty;
    }

    function showTooltip(region) {
        const tooltip = clear('electionMapTooltip');
        if (!region) return;
        tooltip.append(element('strong', '', region.name));
        if (!region.parties.length) tooltip.append(element('span', '', copy.regionPending));
        else region.parties.slice(0, 5).forEach(party => tooltip.append(partyRow(party, true)));
        tooltip.hidden = false;
    }

    function hideTooltip() { byId('electionMapTooltip').hidden = true; }

    function renderNational() {
        const hasResults = snapshot.parties.length > 0 && snapshot.election.declaredSeats > 0;
        byId('electionNationalEmpty').hidden = hasResults;
        byId('electionNationalResults').hidden = !hasResults;
        if (!hasResults) return;
        const ribbon = clear('electionSeatRibbon');
        snapshot.parties.filter(party => party.totalSeats > 0).forEach(party => {
            const segment = element('span', `${partyClass(party.code)} ${widthClass(party.totalSeats * 100 / snapshot.election.totalSeats)}`);
            segment.title = `${party.name}: ${number(party.totalSeats)}`;
            ribbon.append(segment);
        });
        const bars = clear('electionPartyBars');
        const maximumSeats = Math.max(...snapshot.parties.map(party => party.totalSeats), 1);
        snapshot.parties.forEach((party, index) => {
            const row = element('article', `election-party-bar ${partyClass(party.code)}`);
            row.append(element('span', 'election-party-rank', number(index + 1)), logo(party), element('strong', 'election-party-name', party.name));
            const metadata = element('div', 'election-party-meta');
            const voteLabel = party.voteShare == null ? (party.votes == null ? '' : format(copy.votes, { count: number(party.votes) })) : format(copy.voteShare, { percent: percent(party.voteShare) });
            metadata.append(element('span', '', voteLabel), seatCount(party.totalSeats));
            const track = element('div', 'election-party-track');
            const fill = element('span', widthClass(party.totalSeats * 100 / maximumSeats));
            track.append(fill); metadata.append(track); row.append(metadata); bars.append(row);
        });
    }

    function renderCoalitionParties() {
        const hasResults = snapshot.parties.some(party => party.totalSeats > 0);
        byId('electionCoalitionEmpty').hidden = hasResults;
        byId('electionCoalitionBuilder').hidden = !hasResults;
        if (!hasResults) return;
        const previousSelection = selectedPartyCodes;
        const leaderCode = leadingPartyCode();
        const availableCodes = new Set(snapshot.parties.filter(party => party.totalSeats > 0).map(party => party.code));
        const retainedCodes = [...selectedPartyCodes].filter(code => code !== leaderCode && availableCodes.has(code));
        const nextSelection = new Set([...(leaderCode ? [leaderCode] : []), ...retainedCodes.slice(0, MAX_COALITION_PARTIES - (leaderCode ? 1 : 0))]);
        const selectionChanged = previousSelection.size !== nextSelection.size
            || [...previousSelection].some(code => !nextSelection.has(code));
        selectedPartyCodes = nextSelection;
        const root = clear('electionCoalitionParties');
        snapshot.parties.filter(party => party.totalSeats > 0).forEach(party => {
            const button = element('button', `election-coalition-party ${partyClass(party.code)}`);
            button.type = 'button'; button.dataset.partyCode = party.code;
            const identity = element('span', 'election-coalition-party-name', party.name);
            if (party.code === leaderCode) identity.append(element('small', 'election-coalition-lock', copy.coalitionLeader));
            button.append(logo(party), identity, element('strong', '', number(party.totalSeats)));
            button.addEventListener('click', () => toggleParty(party.code));
            root.append(button);
        });
        syncCoalitionPartyButtons();
        renderCoalitionSummary();
        if (selectionChanged) scheduleCoalitionEvaluation();
    }

    function toggleParty(code) {
        if (code === leadingPartyCode()) return;
        if (selectedPartyCodes.has(code)) selectedPartyCodes.delete(code); else selectedPartyCodes.add(code);
        if (selectedPartyCodes.size > MAX_COALITION_PARTIES) {
            selectedPartyCodes.delete(code);
            return;
        }
        syncCoalitionPartyButtons();
        renderCoalitionSummary();
        scheduleCoalitionEvaluation();
        track('election_coalition_changed', { election_year: 2026, selected_party_count: selectedPartyCodes.size });
    }

    function leadingPartyCode() {
        const parties = snapshot?.parties?.filter(party => party.totalSeats > 0) || [];
        const maximumSeats = Math.max(...parties.map(party => party.totalSeats), 0);
        const leaders = parties.filter(party => party.totalSeats === maximumSeats);
        return leaders.length === 1 ? leaders[0].code : undefined;
    }

    function syncCoalitionPartyButtons() {
        const leaderCode = leadingPartyCode();
        const selectionFull = selectedPartyCodes.size >= MAX_COALITION_PARTIES;
        byId('electionCoalitionParties').querySelectorAll('[data-party-code]').forEach(button => {
            const selected = selectedPartyCodes.has(button.dataset.partyCode);
            const locked = button.dataset.partyCode === leaderCode;
            const unavailable = selectionFull && !selected;
            button.setAttribute('aria-pressed', String(selected));
            button.setAttribute('aria-disabled', String(locked || unavailable));
            button.classList.toggle('is-locked', locked);
            button.classList.toggle('is-unavailable', unavailable);
        });
    }

    function scheduleCoalitionEvaluation() {
        window.clearTimeout(coalitionTimer);
        coalitionRequest++;
        coalitionAbortController?.abort();
        if (selectedPartyCodes.size < 2) {
            return;
        }
        byId('electionAlignment').hidden = true;
        setText('electionAlignmentNote', copy.alignmentLoading);
        coalitionTimer = window.setTimeout(evaluateCoalition, COALITION_DEBOUNCE_MS);
    }

    function renderCoalitionSummary() {
        const selectedSeats = snapshot.parties.filter(party => selectedPartyCodes.has(party.code)).reduce((sum, party) => sum + party.totalSeats, 0);
        const election = snapshot.election;
        setText('electionCoalitionSeats', number(selectedSeats));
        setWidth(byId('electionCoalitionBar'), selectedSeats * 100 / election.totalSeats);
        setText('electionCoalitionVerdict', selectedSeats >= election.majoritySeats
            ? format(copy.coalitionWon, { count: number(selectedSeats - election.majoritySeats) })
            : format(copy.coalitionNeed, { count: number(election.majoritySeats - selectedSeats) }));
        byId('electionCoalitionVerdict').classList.toggle('has-majority', selectedSeats >= election.majoritySeats);
        if (selectedPartyCodes.size < 2) { byId('electionAlignment').hidden = true; setText('electionAlignmentNote', `${copy.coalitionStart} ${copy.method}`); }
    }

    async function evaluateCoalition() {
        const requestId = ++coalitionRequest;
        if (selectedPartyCodes.size < 2) return;
        const controller = new AbortController();
        coalitionAbortController = controller;
        let timedOut = false;
        const timeout = window.setTimeout(() => {
            timedOut = true;
            controller.abort();
        }, REQUEST_TIMEOUT_MS);
        byId('electionAlignment').hidden = true;
        setText('electionAlignmentNote', copy.alignmentLoading);
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify({ language: locale, partyCodes: [...selectedPartyCodes] }) });
            if (requestId !== coalitionRequest || controller.signal.aborted) return;
            const response = await fetch(COALITION_URL, { ...options, signal: controller.signal });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const evaluation = await response.json();
            if (requestId !== coalitionRequest) return;
            renderAlignment(evaluation.alignment);
        } catch (error) {
            if (error.name === 'AbortError' && !timedOut) return;
            if (requestId === coalitionRequest) { byId('electionAlignment').hidden = true; setText('electionAlignmentNote', copy.alignmentMissing); }
            console.error('Coalition alignment could not be calculated.', error);
        } finally {
            window.clearTimeout(timeout);
            if (coalitionAbortController === controller) coalitionAbortController = undefined;
        }
    }

    function renderAlignment(alignment) {
        if (!alignment || alignment.status !== 'AVAILABLE') {
            byId('electionAlignment').hidden = true; setText('electionAlignmentNote', `${copy.alignmentMissing} ${copy.method}`); return;
        }
        byId('electionAlignment').hidden = false;
        setText('electionAlignmentScore', `${number(alignment.score)}% · ${alignment.level === 'STRONG' ? copy.alignmentStrong : alignment.level === 'MEDIUM' ? copy.alignmentMedium : copy.alignmentWeak}`);
        setText('electionAlignmentCoverage', format(copy.coverage, { percent: number(alignment.coveragePercent), questions: number(alignment.comparableQuestions) }));
        renderThemes('electionAgreements', alignment.strongestAgreements);
        renderThemes('electionTensions', alignment.strongestTensions);
        setText('electionAlignmentNote', copy.method);
    }

    function renderThemes(id, themes) {
        const root = clear(id);
        if (!themes?.length) { root.append(element('span', 'election-theme-empty', copy.none)); return; }
        themes.forEach(theme => root.append(element('span', '', `${theme.themeLabel} · ${number(theme.score)}%`)));
    }

    function renderSource() {
        setText('electionSourceText', snapshot.election.sourceLabel || '—');
        const link = byId('electionSourceLink');
        link.hidden = !snapshot.election.sourceUrl;
        if (snapshot.election.sourceUrl) link.href = snapshot.election.sourceUrl;
    }

    function partyRow(party, compact, countKind = 'seats') {
        const row = element('div', `${compact ? 'election-tooltip-party' : 'election-region-party'} ${partyClass(party.code)}`);
        if (compact) {
            row.append(logo(party), element('span', '', party.name), seatCount(party.totalSeats));
            return row;
        }

        row.append(
            logo(party),
            element('span', 'election-region-party-name', party.name),
            seatCount(
                party.displayCount ?? party.totalSeats,
                'election-region-party-seats',
                filterCountUnit(countKind, party.displayCount ?? party.totalSeats)
            )
        );
        const localWinners = party.visibleWinners ?? party.winners ?? [];
        const regionalListWinners = party.visibleRegionalListWinners ?? party.regionalListWinners ?? [];
        if (localWinners.length || regionalListWinners.length || party.missingNameCount > 0) {
            const winners = element('div', 'election-region-winners');
            if (localWinners.length) {
                winners.append(winnerGroup(copy.localWinners, localWinners.map(winnerRow)));
            }
            if (regionalListWinners.length) {
                winners.append(winnerGroup(copy.regionalList, regionalListWinners.map(regionalWinnerRow), 'is-regional'));
            }
            if (party.missingNameCount > 0) {
                const missingNameCopy = party.missingNameCount === 1 ? copy.missingWinnerName : copy.missingWinnerNames;
                winners.append(element('p', 'election-region-winner-missing', format(missingNameCopy, { count: number(party.missingNameCount) })));
            }
            row.append(winners);
        }
        return row;
    }

    function seatCount(value, modifier = '', unit = null) {
        const count = element('strong', `election-seat-count ${modifier}`.trim());
        count.append(
            element('bdi', 'election-seat-count-number', number(value)),
            element('span', 'election-seat-count-label', unit || (value === 1 ? copy.seat : copy.seats))
        );
        return count;
    }

    function winnerGroup(label, rows, modifier = '') {
        const group = element('section', `election-region-winner-group ${modifier}`.trim());
        group.append(element('span', 'election-region-winner-group-label', label), ...rows);
        return group;
    }

    function winnerRow(winner) {
        const row = element('div', 'election-region-winner');
        const meta = element('div', 'election-region-winner-meta');
        meta.append(
            element('span', 'sr-only', `${copy.constituency}: `),
            element('bdi', 'election-region-winner-constituency', winner.constituencyName)
        );
        if (winner.votes != null) meta.append(element('span', 'election-region-winner-votes', format(copy.votes, { count: number(winner.votes) })));
        row.append(
            element('span', 'sr-only', `${copy.winner}: `),
            element('bdi', 'election-region-winner-name', winner.candidateName),
            meta
        );
        return row;
    }

    function regionalWinnerRow(winner) {
        const row = element('div', 'election-region-winner election-region-winner-regional');
        const meta = element('div', 'election-region-winner-meta');
        const status = copy.winnerStatus[winner.status] || winner.status;
        if (status) meta.append(element('span', 'election-region-winner-status', status));
        row.append(
            element('span', 'sr-only', `${copy.winner}: `),
            element('bdi', 'election-region-winner-name', winner.candidateName)
        );
        if (meta.childElementCount) row.append(meta);
        return row;
    }

    function logo(party) {
        if (!party.symbolAsset) return element('span', 'election-party-logo election-party-logo-fallback', party.code.slice(0, 2));
        const image = document.createElement('img');
        image.className = 'election-party-logo'; image.src = party.symbolAsset; image.alt = ''; image.loading = 'lazy';
        return image;
    }

    function partyClass(code) {
        const normalized = String(code || '').toLowerCase();
        return `party-accent-${PARTY_CLASSES.has(normalized) ? normalized : 'default'}`;
    }

    function bindTabs() {
        byId('electionTabs').querySelectorAll('[data-election-tab]').forEach(button => button.addEventListener('click', () => activateTab(button.dataset.electionTab, true)));
        const requested = window.location.hash.replace('#', '');
        activateTab(['map', 'national', 'coalition'].includes(requested) ? requested : 'map', false);
    }

    function activateTab(name, report) {
        const names = ['map', 'national', 'coalition'];
        names.forEach(value => {
            const selected = value === name;
            byId(`election${capitalize(value)}Tab`).setAttribute('aria-selected', String(selected));
            byId(`election${capitalize(value)}Panel`).hidden = !selected;
        });
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${name}`);
        if (report) track('election_results_tab_selected', { election_year: 2026, result_view: name });
    }

    function setWidth(node, value) {
        [...node.classList].filter(name => name.startsWith('priority-width-')).forEach(name => node.classList.remove(name));
        node.classList.add(widthClass(value));
    }

    function clear(id) { const node = byId(id); node.replaceChildren(); return node; }
    function setText(id, value) { byId(id).textContent = value ?? ''; }
    function element(tag, className = '', text = null) { const node = document.createElement(tag); if (className) node.className = className; if (text !== null) node.textContent = text; return node; }
    function card(tag, className, children) { const node = element(tag, className); node.append(...children); return node; }
    function capitalize(value) { return value[0].toUpperCase() + value.slice(1); }
    function track(name, parameters) { window.FhemniAnalytics?.trackEvent(name, parameters); }
})();
