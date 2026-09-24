(function () {
    const RESULT_URL = '/api/catalog/elections/2026/results';
    const COALITION_URL = '/api/catalog/elections/2026/coalitions/evaluate';
    const MAP_URL = '/assets/maps/morocco-regions-2026.svg';
    const POLL_INTERVAL_MS = 30_000;
    const REQUEST_TIMEOUT_MS = 10_000;
    const MAX_POLL_BACKOFF_MS = 5 * 60_000;
    const COALITION_DEBOUNCE_MS = 250;
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
            regionStatus: { PENDING: 'في انتظار النتائج', PARTIAL: 'نتائج جزئية', FINAL: 'نتائج نهائية' }, regionPending: 'النتائج مازال ما تعلناتش فهاد الجهة.', regionSeats: '{count} مقعد معلن', regionAllocated: '{count} مقعد مخصص', seats: 'مقاعد', seat: 'مقعد', winner: 'الفائز', constituency: 'الدائرة الانتخابية',
            nationalTitle: 'توزيع المقاعد على الأحزاب', nationalIntro: 'الأحزاب مرتبة حسب عدد المقاعد المعلنة. ما كنعلنوش على أغلبية هنا؛ جرّب التحالفات فالأداة.', noResults: 'مازال ما كاين حتى مقعد معلن. هاد الصفحة غادي تتحدّث مباشرة ملي تدخل النتائج الرسمية.', votes: '{count} صوت', voteShare: '{percent}% من الأصوات',
            coalitionTitle: 'كوّن الأغلبية ديالك', coalitionIntro: 'اختار الأحزاب وشوف واش وصلو لـ198 مقعد، وشنو مستوى التقارب بين برامجهم المنشورة.', coalitionChoose: 'اختار الأحزاب', coalitionSummary: 'التحالف ديالك', coalitionSeats: 'مقعد من 395', coalitionNeed: 'خاصك {count} مقعد آخر باش توصل للأغلبية.', coalitionWon: 'وصلتي للأغلبية بـ{count} مقعد زيادة.', coalitionStart: 'اختار جوج أحزاب على الأقل باش نحسبو التقارب.', coalitionNoResults: 'الأداة غادي تولّي متاحة ملي تتعلن المقاعد.',
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
            regionStatus: { PENDING: 'En attente', PARTIAL: 'Résultats partiels', FINAL: 'Résultats définitifs' }, regionPending: 'Aucun résultat n’a encore été publié pour cette région.', regionSeats: '{count} sièges déclarés', regionAllocated: '{count} sièges attribués', seats: 'sièges', seat: 'siège', winner: 'Élu', constituency: 'Circonscription',
            nationalTitle: 'Répartition des sièges par parti', nationalIntro: 'Les partis sont classés par sièges déclarés. La majorité est explorée séparément dans le simulateur.', noResults: 'Aucun siège n’a encore été déclaré. La page se mettra à jour dès l’ajout des résultats officiels.', votes: '{count} voix', voteShare: '{percent}% des voix',
            coalitionTitle: 'Composez votre majorité', coalitionIntro: 'Choisissez des partis, atteignez 198 sièges et consultez leur proximité sur la base des programmes publiés.', coalitionChoose: 'Choisissez les partis', coalitionSummary: 'Votre coalition', coalitionSeats: 'sièges sur 395', coalitionNeed: 'Il manque {count} sièges pour obtenir la majorité.', coalitionWon: 'Majorité atteinte avec {count} sièges d’avance.', coalitionStart: 'Choisissez au moins deux partis pour calculer leur proximité.', coalitionNoResults: 'Le simulateur sera disponible dès la publication des sièges.',
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
            regionStatus: { PENDING: 'Awaiting results', PARTIAL: 'Partial results', FINAL: 'Final results' }, regionPending: 'No results have been published for this region yet.', regionSeats: '{count} seats declared', regionAllocated: '{count} seats allocated', seats: 'seats', seat: 'seat', winner: 'Winner', constituency: 'Constituency',
            nationalTitle: 'Seats by party', nationalIntro: 'Parties are ranked by declared seats. Majority-building is explored separately in the coalition tool.', noResults: 'No seats have been declared yet. This page will update when official results are entered.', votes: '{count} votes', voteShare: '{percent}% of votes',
            coalitionTitle: 'Build your majority', coalitionIntro: 'Select parties, reach 198 seats, and see how closely their published programmes align.', coalitionChoose: 'Choose parties', coalitionSummary: 'Your coalition', coalitionSeats: 'seats out of 395', coalitionNeed: '{count} more seats needed for a majority.', coalitionWon: 'Majority reached with {count} seats to spare.', coalitionStart: 'Choose at least two parties to calculate programme alignment.', coalitionNoResults: 'The builder will be available once seats are published.',
            alignment: 'Programme alignment', alignmentStrong: 'Strong alignment', alignmentMedium: 'Medium alignment', alignmentWeak: 'Weak alignment', alignmentLoading: 'Calculating alignment…', alignmentMissing: 'The published data is not sufficient for an honest score.', coverage: '{percent}% coverage · {questions} comparable questions', agreements: 'Strongest common ground', tensions: 'Main tensions', none: 'No standout theme',
            method: 'Alignment uses documented positions from published programmes only. Missing and “no position” entries are never treated as neutral.', sourceTitle: 'Source', sourceOpen: 'Open official source'
        }
    };

    let snapshot;
    let locale;
    let copy;
    let selectedRegionKey;
    let selectedPartyCodes = new Set();
    let coalitionRequest = 0;
    let coalitionTimer;
    let coalitionAbortController;
    let pollTimer;
    let pollFailureCount = 0;
    let resultRequestInFlight = false;
    let resultAbortController;
    let pendingManualRetry = false;

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
        applyCopy();
        bindTabs();
        byId('electionRetry').addEventListener('click', () => load());
        byId('electionRegionSelect').addEventListener('change', event => selectRegion(event.target.value, true));
        load();
        document.addEventListener('visibilitychange', handleVisibilityChange);
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
        setText('electionCoalitionTitle', copy.coalitionTitle); setText('electionCoalitionIntro', copy.coalitionIntro); setText('electionCoalitionChoose', copy.coalitionChoose); setText('electionCoalitionSummaryLabel', copy.coalitionSummary); setText('electionCoalitionSeatUnit', copy.coalitionSeats); setText('electionCoalitionEmpty', copy.coalitionNoResults);
        setText('electionAlignmentLabel', copy.alignment); setText('electionAgreementTitle', copy.agreements); setText('electionTensionTitle', copy.tensions); setText('electionAlignmentNote', copy.method); setText('electionSourceTitle', copy.sourceTitle); setText('electionSourceLink', copy.sourceOpen);
    }

    async function load(options = {}) {
        if (resultRequestInFlight) {
            if (!options.poll) {
                pendingManualRetry = true;
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
                signal: controller.signal
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            snapshot = await response.json();
            pollFailureCount = 0;
            byId('electionStale').hidden = true;
            render();
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
                pendingManualRetry = false;
                load();
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

    function render() {
        renderOverview(); renderMetrics(); renderRegionSelect(); renderMap(); renderNational(); renderCoalitionParties(); renderSource();
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

    function renderRegionSelect() {
        const select = clear('electionRegionSelect');
        snapshot.regions.forEach(region => {
            const option = element('option', '', region.name);
            option.value = region.mapKey;
            select.append(option);
        });
        if (!selectedRegionKey || !snapshot.regions.some(region => region.mapKey === selectedRegionKey)) selectedRegionKey = snapshot.regions[0]?.mapKey;
        select.value = selectedRegionKey || '';
        renderRegionDetails(regionByKey(selectedRegionKey));
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
        selectedRegionKey = mapKey;
        byId('electionRegionSelect').value = mapKey;
        renderRegionDetails(regionByKey(mapKey));
        renderMap();
        if (report) track('election_region_selected', { election_year: 2026, region: mapKey });
    }

    function regionByKey(mapKey) { return snapshot?.regions.find(region => region.mapKey === mapKey); }

    function renderRegionDetails(region) {
        const root = clear('electionRegionDetails');
        if (!region) return;
        root.append(element('span', 'section-kicker', copy.regionStatus[region.status] || region.status), element('h3', '', region.name));
        const seatText = region.allocatedSeats == null
            ? format(copy.regionSeats, { count: number(region.declaredSeats) })
            : `${format(copy.regionSeats, { count: number(region.declaredSeats) })} · ${format(copy.regionAllocated, { count: number(region.allocatedSeats) })}`;
        root.append(element('p', 'election-region-seat-total', seatText));
        if (!region.parties.length) { root.append(element('p', 'election-region-pending', copy.regionPending)); return; }
        const list = element('div', 'election-region-parties');
        region.parties.forEach(party => list.append(partyRow(party, false)));
        root.append(list);
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
            metadata.append(element('span', '', voteLabel), element('b', '', `${number(party.totalSeats)} ${party.totalSeats === 1 ? copy.seat : copy.seats}`));
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
        selectedPartyCodes = new Set([...selectedPartyCodes].filter(code => snapshot.parties.some(party => party.code === code)));
        const root = clear('electionCoalitionParties');
        snapshot.parties.filter(party => party.totalSeats > 0).forEach(party => {
            const button = element('button', `election-coalition-party ${partyClass(party.code)}`);
            button.type = 'button'; button.dataset.partyCode = party.code;
            button.setAttribute('aria-pressed', String(selectedPartyCodes.has(party.code)));
            button.append(logo(party), element('span', '', party.name), element('strong', '', number(party.totalSeats)));
            button.addEventListener('click', () => toggleParty(party.code));
            root.append(button);
        });
        renderCoalitionSummary();
    }

    function toggleParty(code) {
        if (selectedPartyCodes.has(code)) selectedPartyCodes.delete(code); else selectedPartyCodes.add(code);
        byId('electionCoalitionParties').querySelector(`[data-party-code="${code}"]`)?.setAttribute('aria-pressed', String(selectedPartyCodes.has(code)));
        renderCoalitionSummary();
        scheduleCoalitionEvaluation();
        track('election_coalition_changed', { election_year: 2026, selected_party_count: selectedPartyCodes.size });
    }

    function scheduleCoalitionEvaluation() {
        window.clearTimeout(coalitionTimer);
        coalitionAbortController?.abort();
        if (selectedPartyCodes.size < 2) {
            coalitionRequest++;
            return;
        }
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
        coalitionAbortController = new AbortController();
        setText('electionAlignmentNote', copy.alignmentLoading);
        try {
            const options = await window.FhemniAuth.withCsrf({ method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify({ language: locale, partyCodes: [...selectedPartyCodes] }) });
            const response = await fetch(COALITION_URL, { ...options, signal: coalitionAbortController.signal });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const evaluation = await response.json();
            if (requestId !== coalitionRequest) return;
            renderAlignment(evaluation.alignment);
        } catch (error) {
            if (error.name === 'AbortError') return;
            if (requestId === coalitionRequest) { byId('electionAlignment').hidden = true; setText('electionAlignmentNote', copy.alignmentMissing); }
            console.error('Coalition alignment could not be calculated.', error);
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

    function partyRow(party, compact) {
        const row = element('div', `${compact ? 'election-tooltip-party' : 'election-region-party'} ${partyClass(party.code)}`);
        if (compact) {
            row.append(logo(party), element('span', '', party.name), element('strong', '', `${number(party.totalSeats)} ${party.totalSeats === 1 ? copy.seat : copy.seats}`));
            return row;
        }

        row.append(
            logo(party),
            element('span', 'election-region-party-name', party.name),
            element('strong', 'election-region-party-seats', `${number(party.totalSeats)} ${party.totalSeats === 1 ? copy.seat : copy.seats}`)
        );
        if (party.winners?.length) {
            const winners = element('div', 'election-region-winners');
            party.winners.forEach(winner => winners.append(winnerRow(winner)));
            row.append(winners);
        }
        return row;
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
