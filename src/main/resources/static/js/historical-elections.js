(function (root, factory) {
    const api = factory(root);
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.FhemniHistoricalElectionsPage = api;
})(typeof globalThis === 'undefined' ? this : globalThis, function (root) {
    'use strict';

    const DATA_URL = '/data/elections/history.json?v=20260928-2';
    const BACKFILL_URL = '/data/elections/affiliation-backfill.json?v=20260928-2';
    const MEASURES = new Set(['total', 'local', 'list']);
    const SORTS = new Set(['delta-desc', 'delta-asc', 'later-desc', 'name']);
    const DEMOGRAPHIC_DIMENSIONS = new Set(['gender', 'age', 'education']);
    const PARTY_LOGO_ASSETS = Object.freeze({
        RNI: 'rni-display.png', PAM: 'pam-display.png', PI: 'pi-display.png',
        PJD: 'pjd-display.png', USFP: 'usfp-display.png', PPS: 'pps-display.png',
        MP: 'mp-display.png', UC: 'uc-display.png', FFD: 'ffd-display.png',
        MDS: 'mds-display.png', PSU: 'psu-display.png', PUD: 'pud-display.png',
        'P.EQUITE': 'pe-display.png', PGVM: 'pgv-display.png', PND: 'nd-display.png',
        AG: 'fgd-official-2026.png', ALAMAL: 'alamal-display.png'
    });
    const EVIDENCE_GATED_PARTY_LOGOS = new Set(['AG', 'PSU']);
    const HISTORY_TABS = [
        { tab: 'overview', panelId: 'historyOverviewPanel', labelKey: 'tabOverview' },
        { tab: 'parties', panelId: 'historyPartiesPanel', labelKey: 'tabParties' },
        { tab: 'quotient', panelId: 'historyQuotientPanel', labelKey: 'tabQuotient' },
        { tab: 'regions', panelId: 'historyRegionsPanel', labelKey: 'tabRegions' },
        { tab: 'people', panelId: 'historyPeoplePanel', labelKey: 'tabPeople' },
        { tab: 'sources', panelId: 'historySourcesPanel', labelKey: 'tabSources' }
    ];
    const COPY = {
        ar: {
            affiliationDocumented: 'انتماء موثق',
            affiliationScope: 'المرشحون مع الانتماءات السابقة الموثقة',
            affiliationCaveat: 'التغطية ما زالت جزئية. الانتماء الحزبي ما كيعنيش الفوز بمقعد برلماني.',
            affiliationSources: 'مصادر استكمال الانتماءات',
            identityCorroboration: 'تأكيد الهوية',
            pageTitle: 'كيفاش تبدلات نتائج الانتخابات — فهّمني', metaDescription: 'قارن نتائج الانتخابات التشريعية المغربية الرسمية بين 2016 و2021 و2026.',
            loading: 'كنحضّرو المقارنة…', retry: 'عاود جرّب', errorTitle: 'ما قدرناش نفتحو الأرشيف الانتخابي', errorText: 'المعطيات ما تحمّلاتش ولا ما دازتش المراجعة. عاود جرّب.',
            eyebrow: 'الأرشيف الانتخابي', title: 'كيفاش تبدلات نتائج الانتخابات', intro: 'قارن جوج انتخابات، وتبع المقاعد والجهات والسجلات الرسمية عبر السنوات.',
            currentResultsLink: 'شوف نتائج 2026 الحالية', pairTitle: 'اختار المقارنة', from: 'الانتخابات السابقة', to: 'الانتخابات اللاحقة', fallback: 'الاختيار فالرابط ما كانش مدعوم. رجعناك لآخر مقارنة متاحة.', selectionFallback: 'اختيار الحزب أو الجهة المحفوظ فالرابط ما بقاش متاح. عرضنا أول اختيار متاح.',
            jumpOverview: 'نظرة وطنية', jumpParties: 'تغيّر الأحزاب', jumpTrajectory: 'مسار حزب', jumpQuotient: 'القاسم الانتخابي', jumpRegions: 'الجهات', jumpEvidence: 'خصائص المنتخبين', jumpPeople: 'الأسماء المتكررة', jumpSources: 'المصادر والمنهجية', jumpLabel: 'أقسام المقارنة التاريخية', tabOverview: 'نظرة عامة', tabParties: 'الأحزاب', tabQuotient: 'القاسم الانتخابي', tabRegions: 'الجهات', tabPeople: 'الترحال السياسي', tabSources: 'المنهجية',
            overviewKicker: 'الصورة الكبيرة', overviewTitle: 'الأصوات والتمثيل البرلماني', overviewIntro: 'كنعرضو المقاعد والأصوات ديال كل ورقة اقتراع بوحدها باش ما نخلطوش بين اللوائح.',
            ballotsCard: 'بنية المقاعد والأصوات', localBallot: 'الدوائر المحلية', nationalBallot: 'اللائحة الوطنية', regionalBallot: 'اللوائح الجهوية', ballotsNotVoters: 'الأصوات ديال كل ورقة اقتراع معروضة بوحدها؛ ماشي مجموع المصوتين.', seats: 'مقاعد', ballots: 'صوت',
            partiesKicker: 'الرابحين والخاسرين', partiesTitle: 'تغيّر مقاعد الأحزاب',
            measure: 'المقياس', measureLocal: 'المقاعد المحلية', measureTotal: 'مجموع المقاعد', measureList: 'مقاعد اللائحة', partySearch: 'قلب على حزب', partySearchPlaceholder: 'الاسم الرسمي أو الاختصار', sort: 'الترتيب', sortDeltaDesc: 'أكبر زيادة', sortDeltaAsc: 'أكبر نقصان', sortLater: 'الأكثر فالسنة اللاحقة', sortName: 'الاسم',
            measureLocalCaveat: 'المجموع هو المقياس الافتراضي؛ المقاعد المحلية هي الأكثر تجانساً بين السنوات.', measureTotalCaveat: 'المجموع كيجمع 305 مقعد محلي و90 مقعد من اللوائح الجهوية فكل سنة.', measureTotalChangedCaveat: 'مجموع المقاعد قابل للحساب، ولكن النظام تبدّل: 2016 فيها 90 مقعد من اللائحة الوطنية، و2021 و2026 فيهم 90 مقعد من اللوائح الجهوية.', measureListCaveat: '2021 و2026 كيستعملو لوائح جهوية قابلة للمقارنة وطنياً.', listUnavailable: 'مقاعد اللائحة ما متاحاش فمقارنة فيها 2016، حيت كانت لائحة وطنية ماشي لوائح جهوية.', noPartyResults: 'ما لقينا حتى حزب بهاد البحث.', sourceOnlyResult: 'نتيجة رسمية بلا مقارنة', showAll: 'بين الكل ({partyCount})', showLess: 'بين غير الأبرز', exactFigures: 'الأرقام المضبوطة', exactCaption: 'المقاعد لكل حزب حسب الاختيار', party: 'الحزب أو اللائحة', earlier: 'قبل', later: 'من بعد', change: 'التغيّر', unchanged: 'بلا تغيير',
            trajectoryKicker: 'ثلاث محطات', trajectoryTitle: 'مسار حزب عبر جميع الانتخابات', trajectoryIntro: 'اختار حزب أو لائحة وشوف المقاعد المحلية، مقاعد اللائحة الوطنية أو الجهوية، والمجموع فكل سنة متاحة.', trajectoryParty: 'الحزب أو اللائحة', totalSeats: 'المجموع', localSeats: 'محلية', listSeats: 'اللائحة', nationalListSeats: 'اللائحة الوطنية', regionalListSeats: 'اللوائح الجهوية', notObserved: 'ما بانش فالسجل الكامل لهاد السنة', sourceLabelOnly: 'فالعادة، الاستمرارية كتعتمد على نفس الاسم الرسمي بالضبط. غير التحالفات اللي تأكد تكوينها بين الانتخابات كنجمعوها؛ وباقي التسميات كتبقى مستقلة.',
            quotientKicker: 'محاكاة مضادة للواقع', quotientTitle: 'شنو يتبدّل بقواعد 2016 وفرضية اللائحة الوطنية؟', quotientIntro: 'النتيجة الرسمية ديال 2026 مبنية على عدد المسجلين. هاد السيناريو كيستعمل قاسم الأصوات المؤهلة وعتبة 3%، وكيجمع أصوات اللوائح الجهوية فلائحة وطنية مفترضة.', quotientWarning: 'محاكاة من فهّمني، ماشي نتيجة رسمية ولا تغيير القاسم بوحدو. كنجمعو 90 مقعد فلائحة وطنية مفترضة؛ قانون 2016 كان فيه جوج أجزاء 60 + 30. هاد التبسيط يقدر يبدّل توزيع المقاعد بين الأحزاب.', quotientUnavailable: 'المحاكاة غير متاحة: المعطيات ناقصة، ما تصالحاتش، أو كاين تعادل غير محسوم. ما كنختاروش فائز آلياً.', quotientValidationTitle: 'اختبار المقاعد المحلية', quotientValidationValue: '{constituencies}/92 دائرة · {seats}/305 مقعد', quotientValidationDetail: 'نفس القواعد رجعات التوزيع الرسمي المحلي ديال 2016 بالضبط، بلا حتى تعادل غير محسوم.', quotientListValidationTitle: 'اختبار اللائحة الوطنية', quotientListValidationValue: '{parties}/24 لائحة · {seats}/90 مقعد', quotientListValidationDetail: 'نفس القواعد رجعات توزيع اللائحة الوطنية الرسمي ديال 2016 بالضبط.', quotientPartyTitle: 'الفرق فمجموع المقاعد', quotientPartyIntro: 'كل خط كيبين المحلي ومقاعد اللائحة بالألوان، والرقم فالطرف هو المجموع. كنبينو غير الأحزاب اللي تبدّل ليها المجموع.', quotientOfficial: 'رسمي 2026', quotientSimulated: 'قواعد 2016 · لائحة وطنية مفترضة', quotientDelta: 'الفرق', quotientAllParties: 'المحلي واللائحة والمجموع', quotientAllPartiesCaption: 'تفصيل المقاعد الرسمية والمحاكية لكل حزب', quotientOfficialLocal: 'محلي رسمي', quotientSimulatedLocal: 'محلي محاكى', quotientOfficialRegional: 'جهوي رسمي', quotientSimulatedNational: 'وطني محاكى', quotientOfficialTotal: 'المجموع الرسمي', quotientSimulatedTotal: 'المجموع المحاكى', quotientConstituencyTitle: 'شوف الحساب داخل دائرة', quotientConstituency: 'الدائرة المحلية', quotientAllocatedSeats: 'المقاعد الموزعة', quotientTotalVotes: 'الأصوات الصحيحة', quotientEligibleVotes: 'أصوات اللوائح المؤهلة', quotientValue: 'القاسم الانتخابي', quotientThreshold: 'عتبة التأهل', quotientVotes: 'الأصوات', quotientEligible: 'مؤهلة', quotientFirstPass: 'المقاعد بالقاسم', quotientRemainder: 'الباقي', quotientRemainderSeat: 'مقعد بأكبر البقايا', quotientOfficialSeats: 'المقاعد الرسمية', quotientSimulatedSeats: 'المقاعد المحاكية', yes: 'نعم', no: 'لا',
            regionsKicker: 'فين وقع التغيّر', regionsTitle: 'مقارنة الجهات', regionsIntro: 'تكوين المقاعد المحلية فكل جهة عبر السنوات الثلاث.', region: 'الجهة', regionCaveat: 'مقارنة الجهات كتستعمل المقاعد المحلية فقط. لائحة 2016 الوطنية ما يمكنش نوزعوها على الجهات.', regionEmpty: 'ما كايناش مقارنة متاحة لهاد الجهة.',
            evidenceKicker: 'شنو نشر المصدر', evidenceTitle: 'خصائص المنتخبين', evidenceIntro: 'النسب الديموغرافية اللي نشرها elections.ma حرفياً.', demographic: 'البعد', gender: 'الجنس', age: 'العمر', education: 'المستوى الدراسي', demographicCaveat: 'كنعرضو غير النسب اللي نشرها elections.ma بنصها. ما كنستنتجوش أعداد من النسب، والفراغ ماشي صفر.', unreported: 'غير منشور', noDemographics: 'ما كايناش نسب منشورة لهاد البعد.',
            peopleKicker: 'السجل ماشي الهوية', peopleTitle: 'الترحال السياسي بين السجلات الانتخابية', peopleIntro: 'استكشف السجلات بين الانتخابات والانتماءات السابقة الموثقة، مع مصدر كل ملاحظة بوحدها.', nameMatchCaveat: 'كنقارنو الأسماء المطابقة والفريدة فوكلاء اللوائح والمنتخبين، حتى اللي ما ربحوش. الأرشيف ما كيغطيش جميع المرشحين ولا جميع الانتقالات السياسية؛ تشابه الاسم بوحدو ما كيأكدش الهوية.', movementTitle: 'الأحزاب المستقبلة والمغادرة فالسجلات', movementIntro: 'كنعرضو أكبر خمس حصيلات فكل جهة: عدد السجلات اللي تبدّل فيها الانتماء، ومنهم شحال تربح ليهم مقعد فالانتخابات اللاحقة. العدد مرتبط بالنطاق المختار، وماشي إحصاء لجميع الانتقالات السياسية.', movementGains: 'سجلات وافدة', movementLosses: 'سجلات مغادرة', movementCount: '{matchCount}', peopleSearch: 'قلب فالأسامي والسجلات', peopleSearchPlaceholder: 'الاسم، الحزب أو الدائرة', differentOnly: 'غير السجلات اللي فيها تسميات حزبية مختلفة', peopleCount: '{nameMatchCount}', peopleCaption: 'السجلات الانتخابية فالجوج السنوات', name: 'الاسم كما تنشر', observations: 'الملاحظات الانتخابية', evidence: 'قوة الدليل', nameMatchOnly: 'تطابق الاسم فقط', constituency: 'الدائرة', noPeople: 'ما لقينا حتى تطابق بهاد الفلاتر.', previous: 'السابق', next: 'التالي', paginationLabel: 'صفحات الأسماء المتكررة', pageStatus: 'الصفحة {page} من {pages} · {start}–{end} من {total}', emptyPageStatus: '0 نتائج', peopleScope: 'نطاق المقارنة', scopeCandidates: 'وكلاء اللوائح والمنتخبون', scopeElected: 'المنتخبون فالجوج السنوات فقط', candidateElected: 'منتخب', candidateNotElected: 'غير منتخب', movementIncoming: 'الوافدون', movementOutgoing: 'المغادرون', movementElected: 'المنتخبون فـ{year}', movementElectedElsewhere: 'المنتخبون مع الحزب اللاحق فـ{year}',
            sourcesKicker: 'تتبّع الدليل', sourcesTitle: 'المصادر والمنهجية', sourcesIntro: 'هاد الصفحة كتوصل لسجلات كل انتخابات فـ elections.ma؛ المقارنات والحسابات كيديرهم فهّمني انطلاقاً من الأرشيف المثبّت.', sourceLinksTitle: 'روابط elections.ma', openElection: 'فتح سجل انتخابات {year}', methodTitle: 'شنو حسبنا؟', fullMethodology: 'المصادر والمنهجية كاملة', methodText: 'هاد السيناريو كيحتافظ بـ305 مقعد محلي فـ92 دائرة وكيطبق عتبة 3% وقاسم الأصوات ديال اللوائح المؤهلة ثم أكبر البقايا. باش نبنيو سيناريو 395 مقعد، كنجمعو أصوات 2026 الجهوية وطنياً وكنوزعو 90 مقعد دفعة وحدة. هادي فرضية مبسطة، ماشي تغيير القاسم بوحدو ولا إعادة تطبيق كاملة لقانون 2016 اللي كان كيفصل 60 + 30 مقعد؛ هاد الفرق يقدر يبدّل مقاعد الأحزاب. الحساب كيتحقق من نتائج 2016 المثبتة، وأي تعادل بلا قاعدة متحقق منها كيوقف المحاكاة. مقارنة الانتماءات كتجمع وكلاء اللوائح وجميع المنتخبين، كتدمج السجل المكرر داخل نفس الدائرة والحزب، وكتستبعد الأسماء الملتبسة والاستمرارية الحزبية غير المتحقق منها. كنزيدو الانتماءات السابقة غير بمصادر موثقة ومؤرخة؛ ماشي تاريخ كامل لجميع المرشحين أو جميع تغييرات الانتماء.  أصوات اللائحة ماشي أصوات شخصية انتقلت مع المرشح.', archiveFile: 'الأرشيف المثبّت', archiveDigest: 'SHA-256', aggregateStatus: 'صفة التجميع',
            loadedStatus: 'تحمّلات مقارنة {from} و{to}.', partyStatus: '{partyCount} ظاهرين.', peopleStatus: '{matchCount}؛ الصفحة {page} من {pages}.', selectedYears: '{from} ← {to}', sourceRecord: 'سجل elections.ma', analysisLabel: 'تحليل فهّمني مبني على معطيات elections.ma'
        },
        fr: {
            affiliationDocumented: 'Affiliation documentée',
            affiliationScope: 'Candidats et affiliations antérieures documentées',
            affiliationCaveat: 'La couverture reste partielle. Une affiliation politique ne signifie pas un siège de député.',
            affiliationSources: 'Sources complémentaires des affiliations',
            identityCorroboration: 'Vérification d’identité',
            pageTitle: 'L’évolution des élections — Fhemni', metaDescription: 'Comparez les résultats officiels des législatives marocaines de 2016, 2021 et 2026.',
            loading: 'Préparation de la comparaison…', retry: 'Réessayer', errorTitle: 'Impossible d’ouvrir l’archive électorale', errorText: 'Les données n’ont pas pu être chargées ou validées. Réessayez.',
            eyebrow: 'Archive électorale', title: 'L’évolution des élections', intro: 'Comparez deux scrutins et suivez les sièges, les régions et les observations officielles dans le temps.',
            currentResultsLink: 'Voir les résultats actuels de 2026', pairTitle: 'Choisir la comparaison', from: 'Scrutin antérieur', to: 'Scrutin ultérieur', fallback: 'La sélection du lien n’était pas disponible. La comparaison valide la plus récente est affichée.', selectionFallback: 'Le parti ou la région enregistré dans le lien n’est plus disponible. La première option disponible est affichée.',
            jumpOverview: 'Vue nationale', jumpParties: 'Évolution des partis', jumpTrajectory: 'Trajectoire', jumpQuotient: 'Quotient électoral', jumpRegions: 'Régions', jumpEvidence: 'Profils des élus', jumpPeople: 'Noms répétés', jumpSources: 'Sources et méthode', jumpLabel: 'Sections de la comparaison historique', tabOverview: 'Vue d’ensemble', tabParties: 'Partis', tabQuotient: 'Quotient électoral', tabRegions: 'Régions', tabPeople: 'Mobilité politique', tabSources: 'Méthodologie',
            overviewKicker: 'Vue d’ensemble', overviewTitle: 'Voix et représentation parlementaire', overviewIntro: 'Les sièges et les voix de chaque bulletin sont présentés séparément afin de ne pas confondre les listes.',
            ballotsCard: 'Structure des sièges et bulletins', localBallot: 'Circonscriptions locales', nationalBallot: 'Liste nationale', regionalBallot: 'Listes régionales', ballotsNotVoters: 'Les voix de chaque bulletin restent séparées ; leur somme ne représente pas des électeurs uniques.', seats: 'sièges', ballots: 'voix',
            partiesKicker: 'Gains et pertes', partiesTitle: 'Évolution des sièges par parti',
            measure: 'Mesure', measureLocal: 'Sièges locaux', measureTotal: 'Tous les sièges', measureList: 'Sièges de liste', partySearch: 'Rechercher un parti', partySearchPlaceholder: 'Nom officiel ou sigle', sort: 'Trier', sortDeltaDesc: 'Plus fortes hausses', sortDeltaAsc: 'Plus fortes baisses', sortLater: 'Plus de sièges ensuite', sortName: 'Nom',
            measureLocalCaveat: 'Le total des sièges est la mesure par défaut ; les sièges locaux sont les plus homogènes entre les années.', measureTotalCaveat: 'Le total réunit 305 sièges locaux et 90 sièges de listes régionales pour chaque année.', measureTotalChangedCaveat: 'Le total reste calculable, mais le système change : 90 sièges de liste nationale en 2016 contre 90 sièges de listes régionales en 2021 et 2026.', measureListCaveat: 'Les scrutins de 2021 et 2026 utilisent des listes régionales comparables au niveau national.', listUnavailable: 'Les sièges de liste sont indisponibles pour une comparaison avec 2016 : ce scrutin utilisait une liste nationale, pas des listes régionales.', noPartyResults: 'Aucun parti ne correspond à cette recherche.', sourceOnlyResult: 'Résultat officiel non comparé', showAll: 'Afficher tous les partis ({count})', showLess: 'Afficher les principaux', exactFigures: 'Chiffres exacts', exactCaption: 'Sièges par parti selon la vue', party: 'Parti ou liste', earlier: 'Avant', later: 'Après', change: 'Écart', unchanged: 'Inchangé',
            trajectoryKicker: 'Trois étapes', trajectoryTitle: 'Trajectoire d’un parti sur tous les scrutins', trajectoryIntro: 'Choisissez un parti ou une liste pour voir les sièges locaux, de liste nationale ou régionale, et totaux à chaque scrutin disponible.', trajectoryParty: 'Parti ou liste', totalSeats: 'Total', localSeats: 'Locaux', listSeats: 'Liste', nationalListSeats: 'Liste nationale', regionalListSeats: 'Listes régionales', notObserved: 'Non observé dans la liste exhaustive de cette année', sourceLabelOnly: 'La continuité repose normalement sur le même libellé officiel exact. Seules les compositions d’alliance vérifiées entre scrutins sont regroupées ; les autres libellés restent distincts.',
            quotientKicker: 'Simulation contrefactuelle', quotientTitle: 'Que changeraient les règles de 2016 avec une liste nationale agrégée ?', quotientIntro: 'Le résultat officiel de 2026 utilise les électeurs inscrits. Ce scénario applique un quotient des suffrages éligibles, un seuil de 3% et regroupe les voix régionales dans une liste nationale hypothétique.', quotientWarning: 'Simulation contrefactuelle Fhemni, pas un résultat officiel ni un changement du seul quotient. Les 90 sièges sont regroupés en un scrutin national hypothétique ; la loi de 2016 prévoyait deux sections de 60 + 30 sièges. Cette simplification peut modifier les sièges par parti.', quotientUnavailable: 'Simulation indisponible : données incomplètes, incohérentes ou égalité non résolue. Aucun gagnant n’est choisi automatiquement.', quotientValidationTitle: 'Validation des sièges locaux', quotientValidationValue: '{constituencies}/92 circonscriptions · {seats}/305 sièges', quotientValidationDetail: 'Les mêmes règles reproduisent exactement la répartition officielle locale de 2016, sans égalité non résolue.', quotientListValidationTitle: 'Validation de la liste nationale', quotientListValidationValue: '{parties}/24 listes · {seats}/90 sièges', quotientListValidationDetail: 'Les mêmes règles reproduisent exactement la répartition officielle de la liste nationale de 2016.', quotientPartyTitle: 'Écart du total des sièges', quotientPartyIntro: 'Chaque barre colore les sièges locaux et de liste ; le nombre final est le total. Seuls les partis dont le total change sont affichés.', quotientOfficial: 'Officiel 2026', quotientSimulated: 'Règles 2016 · liste nationale agrégée', quotientDelta: 'Écart', quotientAllParties: 'Local, liste et total', quotientAllPartiesCaption: 'Décomposition des sièges officiels et simulés par parti', quotientOfficialLocal: 'Local officiel', quotientSimulatedLocal: 'Local simulé', quotientOfficialRegional: 'Régional officiel', quotientSimulatedNational: 'National simulé', quotientOfficialTotal: 'Total officiel', quotientSimulatedTotal: 'Total simulé', quotientConstituencyTitle: 'Inspecter le calcul d’une circonscription', quotientConstituency: 'Circonscription locale', quotientAllocatedSeats: 'Sièges à répartir', quotientTotalVotes: 'Suffrages valides', quotientEligibleVotes: 'Voix des listes éligibles', quotientValue: 'Quotient électoral', quotientThreshold: 'Seuil d’éligibilité', quotientVotes: 'Voix', quotientEligible: 'Éligible', quotientFirstPass: 'Sièges au quotient', quotientRemainder: 'Reste', quotientRemainderSeat: 'Siège au plus fort reste', quotientOfficialSeats: 'Sièges officiels', quotientSimulatedSeats: 'Sièges simulés', yes: 'Oui', no: 'Non',
            regionsKicker: 'Où cela change', regionsTitle: 'Comparaison régionale', regionsIntro: 'Composition des sièges locaux de chaque région sur les trois scrutins.', region: 'Région', regionCaveat: 'La comparaison régionale utilise uniquement les sièges locaux. La liste nationale de 2016 ne peut pas être attribuée aux régions.', regionEmpty: 'Aucune comparaison disponible pour cette région.',
            evidenceKicker: 'Ce que la source publie', evidenceTitle: 'Profils des élus', evidenceIntro: 'Pourcentages démographiques publiés littéralement par elections.ma.', demographic: 'Dimension', gender: 'Genre', age: 'Âge', education: 'Niveau d’études', demographicCaveat: 'Seuls les pourcentages publiés littéralement par elections.ma sont affichés. Aucun effectif n’est déduit et une absence n’est pas un zéro.', unreported: 'Non publié', noDemographics: 'Aucun pourcentage publié pour cette dimension.',
            peopleKicker: 'Registre, pas identité', peopleTitle: 'Mobilité politique dans les registres électoraux', peopleIntro: 'Explorez les registres entre scrutins et les affiliations antérieures documentées, avec la source de chaque observation.', nameMatchCaveat: 'Correspondances exactes et uniques parmi les têtes de liste et les élus, y compris les non-élus. L’archive ne couvre ni tous les candidats ni tous les changements d’affiliation ; un nom identique ne certifie pas l’identité.', movementTitle: 'Affiliations d’arrivée et de départ', movementIntro: 'Les cinq principaux totaux de chaque côté : registres avec changement d’affiliation, puis nombre d’élus au scrutin suivant. Le périmètre sélectionné s’applique ; ce n’est pas un recensement de tous les changements d’affiliation.', movementGains: 'Registres entrants', movementLosses: 'Registres sortants', movementCount: '{count} correspondances', peopleSearch: 'Rechercher dans les noms et registres', peopleSearchPlaceholder: 'Nom, parti ou circonscription', differentOnly: 'Uniquement les registres avec des libellés de parti différents', peopleCount: '{count} correspondances de nom', peopleCaption: 'Registres des deux élections', name: 'Nom publié', observations: 'Observations électorales', evidence: 'Niveau de preuve', nameMatchOnly: 'Correspondance de nom uniquement', constituency: 'Circonscription', noPeople: 'Aucune correspondance avec ces filtres.', previous: 'Précédent', next: 'Suivant', paginationLabel: 'Pages des noms répétés', pageStatus: 'Page {page} sur {pages} · {start}–{end} sur {total}', emptyPageStatus: '0 résultat', peopleScope: 'Périmètre', scopeCandidates: 'Têtes de liste et élus', scopeElected: 'Élus aux deux scrutins seulement', candidateElected: 'Élu(e)', candidateNotElected: 'Non élu(e)', movementIncoming: 'Entrants', movementOutgoing: 'Sortants', movementElected: 'Élus en {year}', movementElectedElsewhere: 'Élus avec leur nouvelle affiliation en {year}',
            sourcesKicker: 'Remonter à la preuve', sourcesTitle: 'Sources et méthodologie', sourcesIntro: 'Cette page renvoie aux registres de chaque scrutin sur elections.ma ; les comparaisons et calculs sont produits par Fhemni à partir de l’archive épinglée.', sourceLinksTitle: 'Liens elections.ma', openElection: 'Ouvrir le registre du scrutin {year}', methodTitle: 'Que calculons-nous ?', fullMethodology: 'Sources et méthodologie complètes', methodText: 'Ce scénario conserve 305 sièges locaux dans 92 circonscriptions et applique un seuil de 3%, le quotient des voix éligibles puis les plus forts restes. Pour le scénario à 395 sièges, les voix régionales de 2026 sont regroupées au niveau national afin de répartir 90 sièges en une seule fois. Cette hypothèse simplificatrice ne change pas le seul quotient et ne reproduit pas intégralement la loi de 2016, qui séparait 60 + 30 sièges ; elle peut modifier les résultats par parti. Le calcul est contrôlé contre les résultats archivés de 2016 ; une égalité sans règle vérifiée bloque la simulation. La comparaison des affiliations réunit les têtes de liste et tous les élus, dédoublonne une même candidature, et exclut les noms ambigus et les continuités partisanes non vérifiées. Les affiliations antérieures sont complétées par des sources datées et vérifiées ; ce n’est pas un historique exhaustif de tous les candidats ou de leurs changements d’affiliation. les voix de liste ne sont pas des voix personnelles transférées.', archiveFile: 'Archive épinglée', archiveDigest: 'SHA-256', aggregateStatus: 'Statut de l’agrégation',
            loadedStatus: 'Comparaison {from}–{to} chargée.', partyStatus: '{count} partis ou listes affichés.', peopleStatus: '{count} correspondances ; page {page} sur {pages}.', selectedYears: '{from} → {to}', sourceRecord: 'Registre elections.ma', analysisLabel: 'Analyse Fhemni fondée sur elections.ma'
        },
        en: {
            affiliationDocumented: 'Documented affiliation',
            affiliationScope: 'Candidates and documented prior affiliations',
            affiliationCaveat: 'Coverage remains partial. Party affiliation does not imply a parliamentary election win.',
            affiliationSources: 'Supplemental affiliation sources',
            identityCorroboration: 'Identity corroboration',
            pageTitle: 'How elections changed — Fhemni', metaDescription: 'Compare official Moroccan legislative election results for 2016, 2021, and 2026.',
            loading: 'Preparing the comparison…', retry: 'Try again', errorTitle: 'We could not open the election archive', errorText: 'The data could not be loaded or did not pass validation. Try again.',
            eyebrow: 'Election archive', title: 'How elections changed', intro: 'Compare two elections and follow seats, regions, and official records across the years.',
            currentResultsLink: 'View current 2026 results', pairTitle: 'Choose a comparison', from: 'Earlier election', to: 'Later election', fallback: 'That shared selection was unsupported. The newest valid comparison is shown.', selectionFallback: 'The party or region saved in this link is no longer available. The first available option is shown.',
            jumpOverview: 'National overview', jumpParties: 'Party changes', jumpTrajectory: 'Party trajectory', jumpQuotient: 'Electoral quotient', jumpRegions: 'Regions', jumpEvidence: 'Elected-member profiles', jumpPeople: 'Repeated names', jumpSources: 'Sources and method', jumpLabel: 'Historical election sections', tabOverview: 'Overview', tabParties: 'Parties', tabQuotient: 'Electoral quotient', tabRegions: 'Regions', tabPeople: 'Political mobility', tabSources: 'Methodology',
            overviewKicker: 'The big picture', overviewTitle: 'Votes and parliamentary representation', overviewIntro: 'Seats and votes are shown separately for each ballot so distinct lists are not mixed together.',
            ballotsCard: 'Seat and ballot structure', localBallot: 'Local constituencies', nationalBallot: 'National list', regionalBallot: 'Regional lists', ballotsNotVoters: 'Votes for each ballot stay separate; their sum is not a count of unique voters.', seats: 'seats', ballots: 'votes',
            partiesKicker: 'Gains and losses', partiesTitle: 'Party seat changes',
            measure: 'Measure', measureLocal: 'Local seats', measureTotal: 'All seats', measureList: 'List seats', partySearch: 'Search parties', partySearchPlaceholder: 'Official name or abbreviation', sort: 'Sort', sortDeltaDesc: 'Largest gains', sortDeltaAsc: 'Largest losses', sortLater: 'Most seats later', sortName: 'Name',
            measureLocalCaveat: 'Total seats are the default measure; local seats are the most comparable across years.', measureTotalCaveat: 'The total combines 305 local seats and 90 regional-list seats in each election.', measureTotalChangedCaveat: 'Total seats remain countable, but the system changes: 90 national list seats in 2016 versus 90 regional list seats in 2021 and 2026.', measureListCaveat: 'The 2021 and 2026 elections use regional lists that are comparable at national level.', listUnavailable: 'List seats are unavailable for comparisons with 2016 because that election used a national list, not regional lists.', noPartyResults: 'No parties match this search.', sourceOnlyResult: 'Official result, not compared', showAll: 'Show all parties ({count})', showLess: 'Show leading parties', exactFigures: 'Exact figures', exactCaption: 'Seats by party for the selected view', party: 'Party or list', earlier: 'Earlier', later: 'Later', change: 'Change', unchanged: 'No change',
            trajectoryKicker: 'Three points in time', trajectoryTitle: 'One party across every election', trajectoryIntro: 'Choose a party or list to see local, national-list or regional-list, and total seats in every available election.', trajectoryParty: 'Party or list', totalSeats: 'Total', localSeats: 'Local', listSeats: 'List', nationalListSeats: 'National list', regionalListSeats: 'Regional lists', notObserved: 'Not observed in that year’s complete roster', sourceLabelOnly: 'Continuity normally uses the same exact official label. Only verified alliance compositions are rolled up across elections; other labels remain distinct.',
            quotientKicker: 'Counterfactual simulation', quotientTitle: 'What changes under 2016 rules with a pooled national list?', quotientIntro: 'The official 2026 result uses registered voters. This scenario uses an eligible-vote quotient, a 3% threshold and pools regional-list votes into a hypothetical national list.', quotientWarning: 'A Fhemni counterfactual, not an official result or a change to the quotient alone. It pools 90 seats into one hypothetical national contest; 2016 law had separate 60 + 30 sections. This simplification can change individual party allocations.', quotientUnavailable: 'Simulation unavailable: incomplete or inconsistent data, or an unresolved tie. No winner is chosen automatically.', quotientValidationTitle: 'Local-seat validation', quotientValidationValue: '{constituencies}/92 constituencies · {seats}/305 seats', quotientValidationDetail: 'The same rules reproduce the official 2016 local allocation exactly, with no unresolved boundary ties.', quotientListValidationTitle: 'National-list validation', quotientListValidationValue: '{parties}/24 lists · {seats}/90 seats', quotientListValidationDetail: 'The same rules reproduce the official 2016 national-list allocation exactly.', quotientPartyTitle: 'Difference in total seats', quotientPartyIntro: 'Each bar colors local and list seats; the final number is the total. Only parties whose total changes are shown.', quotientOfficial: 'Official 2026', quotientSimulated: '2016 rules · pooled national list', quotientDelta: 'Difference', quotientAllParties: 'Local, list, and total', quotientAllPartiesCaption: 'Official and simulated seat decomposition by party', quotientOfficialLocal: 'Official local', quotientSimulatedLocal: 'Simulated local', quotientOfficialRegional: 'Official regional', quotientSimulatedNational: 'Simulated national', quotientOfficialTotal: 'Official total', quotientSimulatedTotal: 'Simulated total', quotientConstituencyTitle: 'Inspect one constituency calculation', quotientConstituency: 'Local constituency', quotientAllocatedSeats: 'Seats allocated', quotientTotalVotes: 'Valid votes', quotientEligibleVotes: 'Eligible-list votes', quotientValue: 'Electoral quotient', quotientThreshold: 'Eligibility threshold', quotientVotes: 'Votes', quotientEligible: 'Eligible', quotientFirstPass: 'Quotient seats', quotientRemainder: 'Remainder', quotientRemainderSeat: 'Largest-remainder seat', quotientOfficialSeats: 'Official seats', quotientSimulatedSeats: 'Simulated seats', yes: 'Yes', no: 'No',
            regionsKicker: 'Where change happened', regionsTitle: 'Region comparison', regionsIntro: 'Local-seat composition in each region across all three elections.', region: 'Region', regionCaveat: 'Region comparisons use local seats only. The 2016 national list cannot be assigned to regions.', regionEmpty: 'No comparison is available for this region.',
            evidenceKicker: 'What the source reports', evidenceTitle: 'Elected-member profiles', evidenceIntro: 'Demographic percentages reported directly by elections.ma.', demographic: 'Dimension', gender: 'Gender', age: 'Age', education: 'Education', demographicCaveat: 'Only percentages literally reported by elections.ma are shown. Counts are not inferred, and missing does not mean zero.', unreported: 'Not reported', noDemographics: 'No reported percentages are available for this dimension.',
            peopleKicker: 'Records, not identity', peopleTitle: 'Political mobility in election records', peopleIntro: 'Explore election records and documented prior affiliations, keeping the source of each observation separate.', nameMatchCaveat: 'Exact, unique name matches among list heads and elected members, including unsuccessful candidates. The archive does not cover every candidate or affiliation change; matching names alone do not verify identity.', movementTitle: 'Incoming and outgoing party affiliations', movementIntro: 'Top five totals on each side: records with changed affiliations, followed by how many won a seat at the later election. Counts follow the selected scope, not a census of all political transfers.', movementGains: 'Incoming records', movementLosses: 'Outgoing records', movementCount: '{count} matches', peopleSearch: 'Search names and records', peopleSearchPlaceholder: 'Name, party, or constituency', differentOnly: 'Only records with different party labels', peopleCount: '{count} name matches', peopleCaption: 'Records in both elections', name: 'Published name', observations: 'Election observations', evidence: 'Evidence status', nameMatchOnly: 'Name match only', constituency: 'Constituency', noPeople: 'No name matches meet these filters.', previous: 'Previous', next: 'Next', paginationLabel: 'Repeated-name pages', pageStatus: 'Page {page} of {pages} · {start}–{end} of {total}', emptyPageStatus: '0 results', peopleScope: 'Comparison scope', scopeCandidates: 'List heads and elected members', scopeElected: 'Elected in both years only', candidateElected: 'Elected', candidateNotElected: 'Not elected', movementIncoming: 'Incoming', movementOutgoing: 'Outgoing', movementElected: 'Elected in {year}', movementElectedElsewhere: 'Elected with their later affiliation in {year}',
            sourcesKicker: 'Trace the evidence', sourcesTitle: 'Sources and methodology', sourcesIntro: 'This page links to the election-level records on elections.ma; Fhemni computes the comparisons from the pinned archive.', sourceLinksTitle: 'elections.ma links', openElection: 'Open the {year} election record', methodTitle: 'What did we calculate?', fullMethodology: 'Full sources and methodology', methodText: 'This 395-seat scenario retains 305 local seats across 92 constituencies and applies a 3% exclusion threshold, an eligible-vote quotient and largest remainders. Regional-list votes from 2026 are pooled nationally to allocate 90 seats in one contest. This is not a change to the quotient alone or a full reproduction of 2016 law, which had separate 60 + 30 sections; pooling may change party allocations. Calculations are checked against the pinned 2016 results, and ties without a verified rule block the simulation. Affiliation comparisons combine list heads and all elected members, deduplicate the same candidature, and exclude ambiguous names and unverified party continuity. Dated, reviewed sources supplement prior affiliations; this is not a complete history of every candidate or affiliation change. list votes are not personal votes carried by a candidate.', archiveFile: 'Pinned archive', archiveDigest: 'SHA-256', aggregateStatus: 'Aggregate status',
            loadedStatus: 'Loaded the {from} to {to} comparison.', partyStatus: '{count} parties or lists shown.', peopleStatus: '{count} matches; page {page} of {pages}.', selectedYears: '{from} → {to}', sourceRecord: 'elections.ma record', analysisLabel: 'Fhemni analysis based on elections.ma'
        }
    };

    function normalizeHistoryTab(value) {
        return HISTORY_TABS.some(item => item.tab === value) ? value : 'overview';
    }

    function historyTabPanelState(activeTab) {
        const normalized = normalizeHistoryTab(activeTab);
        return HISTORY_TABS.map(item => ({
            tab: item.tab,
            panelId: item.panelId,
            active: item.tab === normalized
        }));
    }

    function historyTabIndex(currentIndex, key, count, direction = 'ltr') {
        if (!Number.isInteger(count) || count < 1) return 0;
        if (key === 'Home') return 0;
        if (key === 'End') return count - 1;
        const rtl = direction === 'rtl';
        const step = key === 'ArrowLeft' ? (rtl ? 1 : -1)
            : key === 'ArrowRight' ? (rtl ? -1 : 1) : 0;
        return step ? (currentIndex + step + count) % count : currentIndex;
    }

    const ARABIC_HISTORY_COUNT_FORMS = Object.freeze({
        match: Object.freeze({ zero: 'لا تطابقات', one: 'تطابق واحد', two: 'تطابقان',
            few: '{count} تطابقات', many: '{count} تطابقاً', other: '{count} تطابق' }),
        nameMatch: Object.freeze({ zero: 'لا تطابقات أسماء', one: 'تطابق اسم واحد', two: 'تطابقا اسم',
            few: '{count} تطابقات أسماء', many: '{count} تطابق اسم', other: '{count} تطابق اسم' }),
        party: Object.freeze({ zero: 'لا أحزاب أو لوائح', one: 'حزب أو لائحة واحدة', two: 'حزبان أو لائحتان',
            few: '{count} أحزاب أو لوائح', many: '{count} حزباً أو لائحةً', other: '{count} حزب أو لائحة' })
    });
    const HISTORY_COUNT_BINDINGS = Object.freeze({
        movementCount: Object.freeze({ noun: 'match', placeholder: 'matchCount' }),
        peopleCount: Object.freeze({ noun: 'nameMatch', placeholder: 'nameMatchCount' }),
        partyStatus: Object.freeze({ noun: 'party', placeholder: 'partyCount' }),
        peopleStatus: Object.freeze({ noun: 'match', placeholder: 'matchCount' }),
        showAll: Object.freeze({ noun: 'party', placeholder: 'partyCount' })
    });

    function interpolate(template, values) {
        let rendered = template;
        for (const [name, replacement] of Object.entries(values)) {
            rendered = rendered.replaceAll(`{${name}}`, String(replacement));
        }
        return rendered;
    }

    function historyCountText(locale, key, count, values = {}) {
        const language = COPY[locale] ? locale : 'en';
        const formattedCount = new Intl.NumberFormat(language === 'ar' ? 'ar-MA' : language).format(count);
        const binding = HISTORY_COUNT_BINDINGS[key];
        const localizedCount = language === 'ar' && binding
            ? interpolate(ARABIC_HISTORY_COUNT_FORMS[binding.noun][new Intl.PluralRules('ar').select(Number(count))],
                { count: formattedCount })
            : formattedCount;
        return interpolate(COPY[language][key] || COPY.en[key] || key, {
            ...values,
            count: formattedCount,
            ...(binding ? { [binding.placeholder]: localizedCount } : {})
        });
    }

    function createHistoryAnnouncer(status, filters, scheduleTask) {
        const announcer = scheduleTask
            ? filters.createLiveRegionAnnouncer(status, scheduleTask)
            : filters.createLiveRegionAnnouncer(status);
        return message => announcer.announce(message, { repeat: true });
    }

    function readUrlState(search) {
        const params = new URLSearchParams(search || '');
        const year = name => /^20\d{2}$/.test(params.get(name) || '') ? params.get(name) : null;
        const measure = MEASURES.has(params.get('measure')) ? params.get('measure') : 'total';
        const sort = SORTS.has(params.get('sort')) ? params.get('sort') : 'delta-desc';
        const demographicDimension = DEMOGRAPHIC_DIMENSIONS.has(params.get('demographic')) ? params.get('demographic') : 'gender';
        const parsedPage = Number(params.get('page'));
        return {
            from: year('from'), to: year('to'), tab: normalizeHistoryTab(params.get('tab')), measure, sort,
            party: params.get('party') || '', partyQuery: params.get('partyq') || '',
            showAllParties: params.get('all') === '1', quotientConstituency: params.get('constituency') || '',
            region: params.get('region') || '', demographicDimension,
            peopleQuery: params.get('people') || '', differentOnly: params.get('different') === '1',
            peopleScope: params.get('scope') === 'elected' ? 'elected' : 'candidates',
            page: Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1
        };
    }

    function writeUrlState(state) {
        const params = new URLSearchParams();
        if (state.from) params.set('from', state.from);
        if (state.to) params.set('to', state.to);
        if (normalizeHistoryTab(state.tab) !== 'overview') params.set('tab', normalizeHistoryTab(state.tab));
        if (state.measure && state.measure !== 'total') params.set('measure', state.measure);
        if (state.sort && state.sort !== 'delta-desc') params.set('sort', state.sort);
        if (state.party) params.set('party', state.party);
        if (state.partyQuery) params.set('partyq', state.partyQuery);
        if (state.showAllParties) params.set('all', '1');
        if (state.quotientConstituency) params.set('constituency', state.quotientConstituency);
        if (state.region) params.set('region', state.region);
        if (state.demographicDimension && state.demographicDimension !== 'gender') params.set('demographic', state.demographicDimension);
        if (state.peopleQuery) params.set('people', state.peopleQuery);
        if (state.differentOnly) params.set('different', '1');
        if (state.peopleScope === 'elected') params.set('scope', 'elected');
        if (state.page > 1) params.set('page', String(state.page));
        const query = params.toString();
        return query ? `?${query}` : '';
    }

    function partyBarPercent(value, maximum) {
        if (!Number.isFinite(value) || !Number.isFinite(maximum) || maximum <= 0) return 0;
        return Math.min(100, Math.round(Math.abs(value) / maximum * 10000) / 100);
    }

    function partyBarMaximum(rows) {
        return Math.max(0, ...(Array.isArray(rows) ? rows : []).map(row => Math.abs(row.selectedDelta)));
    }

    function movementOutcomeSeries(row, maximum, kind) {
        if (!Number.isSafeInteger(row?.count) || row.count < 0
                || !Number.isSafeInteger(row?.laterElectedCount) || row.laterElectedCount < 0
                || row.laterElectedCount > row.count || !Number.isFinite(maximum)
                || maximum < row.count || maximum <= 0 || !['gain', 'loss'].includes(kind)) return [];
        return [
            { count: row.count, labelKey: kind === 'gain' ? 'movementIncoming' : 'movementOutgoing', elected: false },
            { count: row.laterElectedCount, labelKey: kind === 'gain' ? 'movementElected' : 'movementElectedElsewhere', elected: true }
        ].map(point => ({ ...point, percent: partyBarPercent(point.count, maximum) }));
    }

    function stackedSeatSeries(firstLabel, firstSeats, secondLabel, secondSeats, maximum, measure = 'total') {
        return [[firstLabel, firstSeats], [secondLabel, secondSeats]].map(([label, seats]) => {
            const localSeats = measure === 'list' ? 0 : seats.localSeats;
            const listSeats = measure === 'local' ? 0 : seats.listSeats;
            const totalSeats = measure === 'local' ? seats.localSeats
                : measure === 'list' ? seats.listSeats : seats.totalSeats;
            return {
                label: String(label), localSeats, listSeats, totalSeats,
                localPercent: partyBarPercent(localSeats, maximum),
                listPercent: partyBarPercent(listSeats, maximum)
            };
        });
    }

    function quotientViewState(localSimulation, nationalSimulation) {
        const showConstituency = Boolean(localSimulation?.available);
        return {
            showNational: showConstituency && Boolean(nationalSimulation?.available),
            showConstituency
        };
    }

    function demographicChartModel(trends) {
        const dimension = trends?.dimension;
        const rows = Array.isArray(trends?.rows) ? trends.rows : [];
        const years = Array.isArray(trends?.years) ? trends.years : [];
        const chartYears = years.map(year => {
            const segments = rows.flatMap((row, colorIndex) => {
                const point = Array.isArray(row.points)
                    ? row.points.find(item => item.year === year) : null;
                return point ? [{
                    categoryAr: row.categoryAr,
                    percentage: point.percentage,
                    sourcePercentageText: point.sourcePercentageText,
                    sourceQueryId: point.sourceQueryId,
                    colorIndex
                }] : [];
            });
            const reportedTotal = Math.round(segments.reduce((sum, segment) =>
                sum + segment.percentage, 0) * 100) / 100;
            return {
                year,
                segments,
                reportedTotal,
                unreportedPercentage: Math.round(Math.max(0, 100 - reportedTotal) * 100) / 100
            };
        });
        return {
            kind: dimension === 'gender' ? 'donut' : 'stacked',
            categories: rows.map((row, colorIndex) => ({
                categoryAr: row.categoryAr,
                colorIndex
            })),
            years: chartYears
        };
    }

    function createEvidenceStatusBadge(document, label) {
        const badge = document.createElement('span');
        badge.className = 'history-evidence-status';
        badge.textContent = label;
        return badge;
    }

    function appendMethodologyLink(container, document, label) {
        const link = document.createElement('a');
        link.href = '#historySources';
        link.textContent = label;
        if (link.dataset) link.dataset.historyTabLink = 'sources';
        container.append(document.createTextNode(' '), link);
    }

    function regionSeatBarPercent(seats, regionCapacity) {
        if (!Number.isFinite(seats) || !Number.isFinite(regionCapacity) || regionCapacity <= 0) return 0;
        return Math.min(100, Math.round(Math.max(0, seats) / regionCapacity * 10000) / 100);
    }

    function seatSharePercent(seats, totalSeats) {
        if (!Number.isFinite(seats) || !Number.isFinite(totalSeats) || totalSeats <= 0) return 0;
        return Math.min(100, Math.round(Math.max(0, seats) / totalSeats * 10000) / 100);
    }

    function listMeasureAvailable(fromYear, toYear) {
        return Number(fromYear) >= 2021 && Number(toYear) >= 2021;
    }

    function listSeatLabelKey(year) {
        return Number(year) === 2016 ? 'nationalListSeats' : 'regionalListSeats';
    }

    function totalMeasureCaveatKey(fromYear, toYear) {
        return Number(fromYear) === 2016 || Number(toYear) === 2016 ? 'measureTotalChangedCaveat' : 'measureTotalCaveat';
    }

    function resolveAvailableSelection(requested, availableValues) {
        const values = Array.isArray(availableValues) ? availableValues : [];
        const fallback = values[0] || '';
        if (!requested) return { value: fallback, usedFallback: false };
        return values.includes(requested)
            ? { value: requested, usedFallback: false }
            : { value: fallback, usedFallback: true };
    }

    function formatSigned(value, locale) {
        if (!value) return '0';
        const digits = new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale).format(Math.abs(value));
        return `${value > 0 ? '+' : '−'}${digits}`;
    }

    function historicalPartyLogoAsset(abbreviation, continuityBasis = null, nameAr = '') {
        const code = String(abbreviation || '').trim().toUpperCase();
        if (String(nameAr || '').trim() === 'حزب الأمل'
            && (!code || code === 'حزب الأمل')) {
            return '/assets/parties/alamal-display.png';
        }
        // A verified display identity does not assert continuity across elections.
        const identifiedLeftAlliance = code === 'AG' && nameAr.trim() === 'تحالف اليسار';
        if (EVIDENCE_GATED_PARTY_LOGOS.has(code)
            && !identifiedLeftAlliance
            && !['same_exact_source_label', 'verified_alliance_composition'].includes(continuityBasis)) {
            return '/assets/parties/party.svg';
        }
        const filename = PARTY_LOGO_ASSETS[code];
        return `/assets/parties/${filename || 'party.svg'}`;
    }

    function partySearchRows(result, query) {
        const comparable = Array.isArray(result?.rows) ? result.rows : [];
        const sourceOnly = String(query || '').trim() && Array.isArray(result?.notComparableRows)
            ? result.notComparableRows : [];
        return [
            ...comparable.map(row => ({ ...row, comparisonAvailable: true })),
            ...sourceOnly.map(row => ({ ...row, comparisonAvailable: false }))
        ];
    }

    function occurrenceOutcomeKey(row) {
        return row.elected === true ? 'candidateElected'
            : row.elected === false ? 'candidateNotElected' : null;
    }

    function createAffiliationSourceLink(documentRef, evidence, identityLabel) {
        const link = documentRef.createElement('a');
        link.className = 'history-affiliation-source';
        link.textContent = `${evidence.publisher === 'Identity corroboration' ? identityLabel : evidence.publisher} `;
        if (evidence.publishedAt) {
            const date = documentRef.createElement('bdi');
            date.textContent = evidence.publishedAt;
            date.dir = 'ltr';
            link.append(date);
        }
        link.href = evidence.url;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        return link;
    }

    function repeatedNameTransition(group) {
        const occurrences = Array.isArray(group?.occurrences)
            ? [...group.occurrences].sort((left, right) => Number(left.year) - Number(right.year)) : [];
        if (occurrences.length !== 2) return null;
        const [earlier, later] = occurrences;
        const continuityBasis = occurrence => occurrence.continuityBasis
            || (String(occurrence.comparisonKey || '').startsWith('exact-source-label:')
                ? 'same_exact_source_label' : 'source_observation_only');
        const identityKey = occurrence => occurrence.canonicalComparisonKey || occurrence.comparisonKey;
        const fromParty = String(earlier.canonicalAbbreviation || earlier.abbreviation || earlier.partyNameAr || '—');
        const toParty = String(later.canonicalAbbreviation || later.abbreviation || later.partyNameAr || '—');
        return {
            fromYear: Number(earlier.year),
            toYear: Number(later.year),
            fromParty,
            toParty,
            fromPartyLogo: historicalPartyLogoAsset(fromParty, continuityBasis(earlier), earlier.partyNameAr),
            toPartyLogo: historicalPartyLogoAsset(toParty, continuityBasis(later), later.partyNameAr),
            sameParty: identityKey(earlier) === identityKey(later),
            fromConstituency: String(earlier.constituencyNameAr || '—'),
            toConstituency: String(later.constituencyNameAr || '—'),
            sameConstituency: earlier.constituencyNameAr === later.constituencyNameAr
        };
    }

    if (!root.document || !root.addEventListener) {
        return { createAffiliationSourceLink, occurrenceOutcomeKey, movementOutcomeSeries, COPY, readUrlState, writeUrlState, listMeasureAvailable, listSeatLabelKey, totalMeasureCaveatKey, partyBarPercent, partyBarMaximum, stackedSeatSeries, quotientViewState, demographicChartModel, createEvidenceStatusBadge, appendMethodologyLink, regionSeatBarPercent, seatSharePercent, resolveAvailableSelection, formatSigned, normalizeHistoryTab, historyTabIndex, historyTabPanelState, historicalPartyLogoAsset, repeatedNameTransition, partySearchRows, historyCountText, createHistoryAnnouncer };
    }

    const document = root.document;
    let affiliationBackfill = null;
    const insights = root.FhemniHistoricalElectionInsights;
    const quotientEngine = root.FhemniHistoricalElectoralQuotient;
    const announceStatus = createHistoryAnnouncer(document.getElementById('historyStatus'),
        root.FhemniElectionRegionFilters);
    let payload = null;
    let quotientSimulation = null;
    let fullSystemSimulation = null;
    let locale = 'ar';
    let state = readUrlState(root.location.search);
    let selectionFallbackActive = false;

    function t(key, values = {}) {
        let value = COPY[locale]?.[key] || COPY.en[key] || key;
        for (const [name, replacement] of Object.entries(values)) {
            value = value.replaceAll(`{${name}}`, String(replacement));
        }
        return value;
    }

    function byId(id) { return document.getElementById(id); }
    function setText(id, value) { const node = byId(id); if (node) node.textContent = value; }
    function number(value, options) {
        return new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale, options).format(value);
    }
    function node(tag, className, text) {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    }
    function option(value, label) {
        const item = node('option', '', label);
        item.value = value;
        return item;
    }
    function bdi(value, className) {
        const item = node('bdi', className, value);
        item.dir = 'auto';
        return item;
    }
    function partyIdentity(abbreviation, nameAr, className = '', continuityBasis = null) {
        const identity = node('span', `history-party-identity${className ? ` ${className}` : ''}`);
        const logo = node('img', 'history-party-logo');
        logo.src = historicalPartyLogoAsset(abbreviation, continuityBasis, nameAr);
        logo.alt = '';
        logo.loading = 'lazy';
        identity.append(logo, bdi(nameAr, 'history-party-name'));
        return identity;
    }
    function announce(message) { announceStatus(message); }
    function syncUrl(mode = 'replace') {
        const method = mode === 'push' ? 'pushState' : 'replaceState';
        root.history[method](null, '', `${root.location.pathname}${writeUrlState(state)}${root.location.hash}`);
    }
    function renderSelectionFallback() {
        const notice = byId('historySelectionNotice');
        notice.hidden = !selectionFallbackActive;
        notice.textContent = selectionFallbackActive ? t('selectionFallback') : '';
    }

    function measureValue(seats, measure = state.measure) {
        return measure === 'local' ? seats.localSeats
            : measure === 'list' ? seats.listSeats : seats.totalSeats;
    }

    function syncMeasureTabs() {
        const listAvailable = listMeasureAvailable(state.from, state.to);
        for (const button of byId('historyMeasureTabs').querySelectorAll('button[data-measure]')) {
            const selected = button.dataset.measure === state.measure;
            button.setAttribute('aria-pressed', String(selected));
            button.classList.toggle('is-active', selected);
            button.disabled = button.dataset.measure === 'list' && !listAvailable;
        }
    }

    function syncHistoryTabs() {
        state.tab = normalizeHistoryTab(state.tab);
        for (const item of historyTabPanelState(state.tab)) {
            const button = byId(`historyTab${item.tab[0].toUpperCase()}${item.tab.slice(1)}`);
            const panel = byId(item.panelId);
            if (button) {
                button.setAttribute('aria-selected', String(item.active));
                button.tabIndex = item.active ? 0 : -1;
                button.classList.toggle('is-active', item.active);
            }
            if (panel) panel.hidden = !item.active;
        }
    }

    function selectHistoryTab(tab, options = {}) {
        state.tab = normalizeHistoryTab(tab);
        syncHistoryTabs();
        if (options.updateUrl !== false) syncUrl(options.push ? 'push' : 'replace');
        if (options.focus) byId(`historyTab${state.tab[0].toUpperCase()}${state.tab.slice(1)}`)?.focus();
    }

    function revealActiveHistoryTab() {
        const button = byId(`historyTab${state.tab[0].toUpperCase()}${state.tab.slice(1)}`);
        if (!button) return;
        const x = root.scrollX; const y = root.scrollY;
        button.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        root.scrollTo(x, y);
    }

    function applyCopy() {
        document.title = t('pageTitle');
        byId('historyMetaDescription').content = t('metaDescription');
        const labels = {
            historyLoadingText: 'loading', historyErrorTitle: 'errorTitle', historyErrorText: 'errorText', historyRetry: 'retry',
            historyEyebrow: 'eyebrow', historyTitle: 'title', historyIntro: 'intro', historyCurrentResultsLink: 'currentResultsLink', historyPairTitle: 'pairTitle', historyFromLabel: 'from', historyToLabel: 'to',
            historyOverviewTitle: 'overviewTitle',
            historyPartiesTitle: 'partiesTitle', historyPartySearchLabel: 'partySearch', historySortLabel: 'sort', historyMeasureTabsLabel: 'measure',
            historyTrajectoryTitle: 'trajectoryTitle', historyTrajectoryPartyLabel: 'trajectoryParty',
            historyQuotientTitle: 'quotientTitle', historyQuotientIntro: 'quotientIntro', historyQuotientWarning: 'quotientWarning', historyQuotientPartyTitle: 'quotientPartyTitle', historyQuotientPartyIntro: 'quotientPartyIntro', historyQuotientAllParties: 'quotientAllParties', historyQuotientAllPartiesCaption: 'quotientAllPartiesCaption', historyQuotientConstituencyTitle: 'quotientConstituencyTitle', historyQuotientConstituencyLabel: 'quotientConstituency', historyQuotientParty: 'party', historyQuotientOfficialLocal: 'quotientOfficialLocal', historyQuotientSimulatedLocal: 'quotientSimulatedLocal', historyQuotientOfficialRegional: 'quotientOfficialRegional', historyQuotientSimulatedNational: 'quotientSimulatedNational', historyQuotientOfficialTotal: 'quotientOfficialTotal', historyQuotientSimulatedTotal: 'quotientSimulatedTotal', historyQuotientDelta: 'quotientDelta', historyQuotientContestParty: 'party', historyQuotientVotes: 'quotientVotes', historyQuotientEligible: 'quotientEligible', historyQuotientFirstPass: 'quotientFirstPass', historyQuotientRemainder: 'quotientRemainder', historyQuotientRemainderSeat: 'quotientRemainderSeat', historyQuotientOfficialSeats: 'quotientOfficialSeats', historyQuotientSimulatedSeats: 'quotientSimulatedSeats',
            historyRegionsTitle: 'regionsTitle', historyRegionLabel: 'region', historyRegionCaveat: 'regionCaveat',
            historyEvidenceTitle: 'evidenceTitle', historyDemographicLabel: 'demographic', historyDemographicCaveat: 'demographicCaveat',
            historyPeopleTitle: 'peopleTitle', historyNameMatchCaveat: 'nameMatchCaveat', historyMovementTitle: 'movementTitle', historyMovementIntro: 'movementIntro', historyMovementGainsTitle: 'movementGains', historyMovementLossesTitle: 'movementLosses', historyPeopleSearchLabel: 'peopleSearch', historyDifferentOnlyLabel: 'differentOnly', historyPeopleCaption: 'peopleCaption', historyPeopleName: 'name', historyPeopleObservations: 'observations', historyPeopleEvidence: 'evidence', historyPeoplePrevious: 'previous', historyPeopleNext: 'next',
            historySourcesTitle: 'sourcesTitle', historySourceLinksTitle: 'sourceLinksTitle', historyMethodTitle: 'methodTitle', historyMethodText: 'methodText', historyExactParty: 'party', historyExactChange: 'change'
        };
        for (const [id, key] of Object.entries(labels)) setText(id, t(key));
        appendMethodologyLink(byId('historyQuotientWarning'), document, t('fullMethodology'));
        setText('historyMeasureTotal', t('measureTotal'));
        setText('historyMeasureLocal', t('measureLocal'));
        setText('historyMeasureList', t('measureList'));
        byId('historyPartySearch').placeholder = t('partySearchPlaceholder');
        byId('historyPeopleSearch').placeholder = t('peopleSearchPlaceholder');
        byId('historyTabs').setAttribute('aria-label', t('jumpLabel'));
        byId('historyPagination').setAttribute('aria-label', t('paginationLabel'));
        if (!byId('historyPairNotice').hidden) setText('historyPairNotice', t('fallback'));
        renderSelectionFallback();
        const sort = byId('historyPartySort').options;
        sort[0].textContent = t('sortDeltaDesc'); sort[1].textContent = t('sortDeltaAsc'); sort[2].textContent = t('sortLater'); sort[3].textContent = t('sortName');
        const demographic = byId('historyDemographic').options;
        demographic[0].textContent = t('gender'); demographic[1].textContent = t('age'); demographic[2].textContent = t('education');
        byId('historyExactFigures').querySelector('summary').textContent = t('exactFigures');
        setText('historyExactCaption', t('exactCaption'));
        syncMeasureTabs();
        rebuildTabs();
    }

    function rebuildTabs() {
        byId('historyTabs').replaceChildren(...HISTORY_TABS.map(item => {
            const button = node('button', '', t(item.labelKey));
            button.type = 'button';
            button.id = `historyTab${item.tab[0].toUpperCase()}${item.tab.slice(1)}`;
            button.dataset.historyTab = item.tab;
            button.setAttribute('role', 'tab');
            button.setAttribute('aria-controls', item.panelId);
            byId(item.panelId)?.setAttribute('aria-labelledby', button.id);
            return button;
        }));
        syncHistoryTabs();
    }

    function populateSelectors() {
        const pairs = insights.listValidYearPairs(payload);
        const years = pairs.available ? [...new Set(pairs.pairs.flatMap(pair => [pair.fromYear, pair.toYear]))] : [];
        const resolution = insights.resolveYearPair(payload, state.from, state.to);
        if (!resolution.available) throw new Error('invalid historical payload');
        const fallback = resolution.usedFallback && Boolean(state.from || state.to);
        byId('historyPairNotice').hidden = !fallback;
        setText('historyPairNotice', fallback ? t('fallback') : '');
        state.from = String(resolution.pair.fromYear);
        state.to = String(resolution.pair.toYear);
        if (state.measure === 'list' && !listMeasureAvailable(state.from, state.to)) state.measure = 'total';
        const from = byId('historyFrom'); const to = byId('historyTo');
        from.replaceChildren(...years.slice(0, -1).map(year => option(String(year), String(year))));
        to.replaceChildren(...years.slice(1).map(year => option(String(year), String(year))));
        from.value = state.from; to.value = state.to;
        byId('historyPartySort').value = state.sort;
        byId('historyPartySearch').value = state.partyQuery;
        byId('historyPeopleSearch').value = state.peopleQuery;
        byId('historyDifferentOnly').checked = state.differentOnly;
        byId('historyDemographic').value = state.demographicDimension;
        syncMeasureTabs();
        const partyFallback = populateTrajectoryParties();
        const quotientFallback = populateQuotientConstituencies();
        const regionFallback = populateRegions();
        selectionFallbackActive = partyFallback || quotientFallback || regionFallback;
        renderSelectionFallback();
        syncUrl();
    }

    function populateTrajectoryParties() {
        const years = payload.elections.map(item => item.year);
        const comparison = insights.derivePartyDeltas(payload, Math.min(...years), Math.max(...years), {
            showAll: true, sort: 'later-desc'
        });
        if (!comparison.available) throw new Error('trajectory parties unavailable');
        const parties = comparison.rows;
        const select = byId('historyTrajectoryParty');
        select.replaceChildren(...parties.map(party => option(party.comparisonKey, party.nameAr)));
        const resolution = resolveAvailableSelection(state.party, parties.map(party => party.comparisonKey));
        state.party = resolution.value;
        select.value = state.party;
        return resolution.usedFallback;
    }

    function populateQuotientConstituencies() {
        const select = byId('historyQuotientConstituency');
        const contests = quotientSimulation?.available
            ? quotientSimulation.constituencies.slice().sort((left, right) =>
                left.regionNameAr.localeCompare(right.regionNameAr, 'ar')
                || left.constituencyNameAr.localeCompare(right.constituencyNameAr, 'ar'))
            : [];
        select.replaceChildren(...contests.map(contest => option(
            contest.constituencyId, `${contest.regionNameAr} · ${contest.constituencyNameAr}`)));
        select.disabled = contests.length === 0;
        if (!contests.length) {
            state.quotientConstituency = '';
            return false;
        }
        const resolution = resolveAvailableSelection(state.quotientConstituency,
            contests.map(contest => contest.constituencyId));
        state.quotientConstituency = resolution.value;
        select.value = state.quotientConstituency;
        return resolution.usedFallback;
    }

    function populateRegions() {
        const latest = Math.max(...payload.elections.map(item => item.year));
        const regions = payload.regionalLocalSeats.filter(row => row.year === latest).slice().sort((a, b) => a.regionNameAr.localeCompare(b.regionNameAr, 'ar'));
        const select = byId('historyRegion');
        select.replaceChildren(...regions.map(region => option(String(region.regionId), region.regionNameAr)));
        const resolution = resolveAvailableSelection(state.region, regions.map(region => String(region.regionId)));
        state.region = resolution.value;
        select.value = state.region;
        return resolution.usedFallback;
    }

    function ballotLine(ballot) {
        const label = ballot.type === 'local' ? t('localBallot') : ballot.type === 'national' ? t('nationalBallot') : t('regionalBallot');
        return `${label}: ${number(ballot.seats)} ${t('seats')} · ${number(ballot.votes)} ${t('ballots')}`;
    }

    function renderOverview() {
        const overview = insights.deriveNationalOverview(payload, Number(state.from), Number(state.to));
        if (!overview.available) throw new Error('national overview unavailable');
        const ballots = node('article', 'history-overview-card history-overview-card-wide');
        const columns = node('div', 'history-ballot-columns');
        for (const election of [overview.earlier, overview.later]) {
            const column = node('div', 'history-ballot-year');
            const totalSeats = election.ballots.reduce((sum, ballot) => sum + ballot.seats, 0);
            const localSeats = election.ballots.find(ballot => ballot.type === 'local')?.seats || 0;
            const donut = node('div', 'history-ballot-donut');
            donut.style.setProperty('--local-share', `${seatSharePercent(localSeats, totalSeats)}%`);
            donut.setAttribute('role', 'img');
            donut.setAttribute('aria-label', `${election.year}: ${election.ballots.map(ballotLine).join(', ')}`);
            donut.append(bdi(String(election.year)));
            const legend = node('div', 'history-ballot-legend');
            for (const ballot of election.ballots) {
                const line = node('span', `history-ballot-${ballot.type}`);
                line.append(node('i'), node('span', '', ballotLine(ballot)));
                legend.append(line);
            }
            column.append(donut, legend);
            columns.append(column);
        }
        ballots.append(columns, node('p', '', t('ballotsNotVoters')));
        byId('historyOverviewCards').replaceChildren(ballots);
    }

    function stackedSeatBars(firstLabel, firstSeats, secondLabel, secondSeats, maximum, ariaLabel, measure = 'total') {
        const graphic = node('div', 'history-stacked-bars');
        graphic.setAttribute('role', 'img');
        graphic.setAttribute('aria-label', ariaLabel);
        for (const series of stackedSeatSeries(firstLabel, firstSeats, secondLabel, secondSeats, maximum, measure)) {
            const line = node('span', 'history-stacked-bar');
            const label = node('small', '', series.label);
            const track = node('span', 'history-stacked-track');
            const local = node('i', 'history-stacked-local');
            const list = node('i', 'history-stacked-list');
            local.style.width = `${series.localPercent}%`;
            list.style.width = `${series.listPercent}%`;
            track.append(local, list);
            line.append(label, track, bdi(number(series.totalSeats), 'history-stacked-total'));
            graphic.append(line);
        }
        return graphic;
    }

    function partyBar(row, maximum) {
        const item = node('article', 'history-party-row');
        const identity = partyIdentity(row.abbreviation, row.nameAr, '', row.continuityBasis);
        const graphic = stackedSeatBars(state.from, row.earlier, state.to, row.later, maximum,
            `${row.nameAr}: ${state.from} ${number(measureValue(row.earlier))}, ${state.to} ${number(measureValue(row.later))}`,
            state.measure);
        const delta = row.comparisonAvailable === false
            ? node('span', 'history-party-delta is-source-only', t('sourceOnlyResult'))
            : bdi(formatSigned(row.selectedDelta, locale), `history-party-delta ${row.selectedDelta < 0 ? 'is-loss' : row.selectedDelta > 0 ? 'is-gain' : ''}`);
        item.append(identity, graphic, delta);
        return item;
    }

    function renderParties() {
        syncMeasureTabs();
        const result = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: state.sort, query: state.partyQuery,
            showAll: state.showAllParties
        });
        if (!result.available) throw new Error('party changes unavailable');
        const displayRows = partySearchRows(result, state.partyQuery);
        const scale = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: 'delta-desc', query: '', showAll: true
        });
        if (!scale.available) throw new Error('party scale unavailable');
        const maximum = Math.max(1, ...[...scale.rows, ...displayRows]
            .flatMap(row => [measureValue(row.earlier), measureValue(row.later)]));
        byId('historyPartyBars').replaceChildren(...displayRows.map(row => partyBar(row, maximum)));
        byId('historyPartyEmpty').hidden = displayRows.length !== 0;
        setText('historyPartyEmpty', t('noPartyResults'));
        const showButton = byId('historyShowAll');
        showButton.hidden = Boolean(state.partyQuery) || result.totalRows <= 10;
        showButton.textContent = state.showAllParties ? t('showLess')
            : historyCountText(locale, 'showAll', result.totalRows);
        const caveat = state.measure === 'local' ? 'measureLocalCaveat'
            : state.measure === 'list' ? 'measureListCaveat' : totalMeasureCaveatKey(state.from, state.to);
        setText('historyMeasureCaveat', t(caveat));
        const legend = byId('historySeatLegend');
        const legendItems = state.measure === 'total'
            ? [node('span', 'history-seat-legend-local', t('localSeats')),
                node('span', 'history-seat-legend-list', t('listSeats')),
                node('strong', '', t('totalSeats'))]
            : state.measure === 'local'
                ? [node('span', 'history-seat-legend-local', t('localSeats'))]
                : [node('span', 'history-seat-legend-list', t('listSeats'))];
        legend.replaceChildren(...legendItems);
        setText('historyExactEarlier', state.from); setText('historyExactLater', state.to);
        const exact = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: state.sort, query: state.partyQuery, showAll: true
        });
        const exactRows = partySearchRows(exact, state.partyQuery);
        byId('historyExactBody').replaceChildren(...exactRows.map(row => {
            const tr = node('tr'); const nameCell = node('th'); nameCell.scope = 'row';
            nameCell.append(partyIdentity(row.abbreviation, row.nameAr, 'is-compact', row.continuityBasis));
            const earlier = node('td'); earlier.append(bdi(number(measureValue(row.earlier))));
            const later = node('td'); later.append(bdi(number(measureValue(row.later))));
            const delta = node('td');
            if (row.comparisonAvailable === false) {
                delta.textContent = '—';
                delta.title = t('sourceOnlyResult');
            } else {
                delta.append(bdi(formatSigned(row.selectedDelta, locale)));
            }
            tr.append(nameCell, earlier, later, delta); return tr;
        }));
        announce(historyCountText(locale, 'partyStatus', displayRows.length));
    }

    function renderTrajectory() {
        const trajectory = insights.derivePartyTrajectory(payload, state.party);
        if (!trajectory.available) return;
        const maximum = Math.max(1, ...trajectory.points.map(point => point.totalSeats));
        const heading = node('div', 'history-trajectory-heading');
        heading.append(partyIdentity(trajectory.abbreviation, trajectory.nameAr, '', trajectory.continuityBasis),
            node('span', 'history-analysis-label', t('analysisLabel')));
        const points = node('div', 'history-trajectory-points');
        for (const point of trajectory.points) {
            const item = node('article', 'history-trajectory-point');
            item.append(node('strong', '', String(point.year)));
            const meter = node('span', 'history-trajectory-meter'); const fill = node('i'); fill.style.height = `${Math.round(point.totalSeats / maximum * 100)}%`; meter.append(fill);
            const total = node('bdi', 'history-trajectory-total', `${number(point.totalSeats)} ${t('seats')}`);
            const detail = node('span', '', `${t('localSeats')}: ${number(point.localSeats)} · ${t(listSeatLabelKey(point.year))}: ${number(point.listSeats)}`);
            item.append(meter, total, detail); points.append(item);
        }
        byId('historyTrajectoryContent').replaceChildren(heading, points, node('p', 'history-evidence-note', t('sourceLabelOnly')));
    }

    function renderQuotient() {
        const national = byId('historyQuotientPartyChanges').closest('.history-quotient-national');
        const constituency = byId('historyQuotientConstituency').closest('.history-quotient-constituency');
        const view = quotientViewState(quotientSimulation, fullSystemSimulation);
        if (!view.showConstituency) {
            const validationRoot = byId('historyQuotientValidation');
            validationRoot.hidden = false;
            validationRoot.replaceChildren(node('p', 'history-warning-note', t('quotientUnavailable')));
            national.hidden = true;
            constituency.hidden = true;
            return;
        }

        national.hidden = !view.showNational;
        constituency.hidden = !view.showConstituency;
        byId('historyQuotientValidation').replaceChildren();
        byId('historyQuotientValidation').hidden = true;

        const changed = view.showNational
            ? fullSystemSimulation.partyDeltas.filter(row => row.delta !== 0) : [];
        const quotientMaximum = Math.max(1, ...changed.flatMap(row => [row.officialTotalSeats, row.simulatedTotalSeats]));
        byId('historyQuotientPartyChanges').replaceChildren(...changed.map(row => {
            const article = node('article', 'history-quotient-party-row');
            const identity = partyIdentity(row.abbreviation, row.nameAr, '', row.continuityBasis);
            const official = {
                localSeats: row.officialLocalSeats,
                listSeats: row.officialRegionalListSeats,
                totalSeats: row.officialTotalSeats
            };
            const simulated = {
                localSeats: row.simulatedLocalSeats,
                listSeats: row.simulatedNationalListSeats,
                totalSeats: row.simulatedTotalSeats
            };
            const values = stackedSeatBars(t('quotientOfficial'), official,
                t('quotientSimulated'), simulated, quotientMaximum,
                `${row.nameAr}: ${t('quotientOfficial')} ${number(official.totalSeats)}, ${t('quotientSimulated')} ${number(simulated.totalSeats)}`);
            const delta = node('strong', row.delta > 0 ? 'is-gain' : 'is-loss',
                formatSigned(row.delta, locale));
            delta.setAttribute('aria-label', `${t('quotientDelta')}: ${formatSigned(row.delta, locale)}`);
            article.append(identity, values, delta);
            return article;
        }));

        const fullRows = view.showNational ? fullSystemSimulation.partyDeltas : [];
        byId('historyQuotientPartyBody').closest('details').hidden = !view.showNational;
        byId('historyQuotientPartyBody').replaceChildren(...fullRows.map(row => {
            const tr = node('tr');
            const name = node('th'); name.scope = 'row';
            name.append(partyIdentity(row.abbreviation, row.nameAr, 'is-compact', row.continuityBasis));
            const values = [row.officialLocalSeats, row.simulatedLocalSeats,
                row.officialRegionalListSeats, row.simulatedNationalListSeats,
                row.officialTotalSeats, row.simulatedTotalSeats].map(value => {
                const td = node('td'); td.append(bdi(number(value))); return td;
            });
            const delta = node('td'); delta.append(bdi(formatSigned(row.delta, locale),
                row.delta > 0 ? 'is-gain' : row.delta < 0 ? 'is-loss' : ''));
            tr.append(name, ...values, delta);
            return tr;
        }));

        const contest = quotientSimulation.constituencies.find(row =>
            row.constituencyId === state.quotientConstituency)
            || quotientSimulation.constituencies[0];
        if (!contest) return;
        state.quotientConstituency = contest.constituencyId;
        byId('historyQuotientConstituency').value = contest.constituencyId;
        setText('historyQuotientContestCaption', contest.constituencyNameAr);
        const quotientText = `${number(contest.eligibleVotes)} ÷ ${number(contest.allocatedSeats)} = ${number(contest.electoralQuotient.value, { maximumFractionDigits: 2 })}`;
        const metrics = [
            [t('quotientAllocatedSeats'), number(contest.allocatedSeats)],
            [t('quotientTotalVotes'), number(contest.totalVotes)],
            [t('quotientThreshold'), `${number(contest.eligibilityThreshold.percent)}%`],
            [t('quotientValue'), quotientText]
        ];
        byId('historyQuotientMetrics').replaceChildren(...metrics.map(([label, value]) => {
            const article = node('article');
            article.append(node('small', '', label), bdi(value));
            return article;
        }));
        byId('historyQuotientContestBody').replaceChildren(...contest.parties.map(party => {
            const tr = node('tr');
            const name = node('th'); name.scope = 'row';
            name.append(partyIdentity(party.abbreviation, party.nameAr, 'is-compact', party.continuityBasis));
            const cells = [
                number(party.votes),
                t(party.eligible ? 'yes' : 'no'),
                number(party.firstPassSeats),
                party.remainder ? number(party.remainder.value, { maximumFractionDigits: 2 }) : '—',
                t(party.largestRemainderSeats ? 'yes' : 'no'),
                number(party.officialSeats),
                number(party.simulatedSeats)
            ].map(value => { const td = node('td'); td.append(bdi(value)); return td; });
            tr.append(name, ...cells);
            return tr;
        }));
    }

    function renderRegions() {
        const region = insights.deriveRegionComparison(payload, state.region);
        if (!region.available) { byId('historyRegionContent').textContent = t('regionEmpty'); return; }
        const heading = node('h3', '', region.regionNameAr); heading.dir = 'auto';
        const legend = node('div', 'history-year-legend');
        region.years.forEach((year, index) => legend.append(node('span', `history-year-${index + 1}`, String(year))));
        const rows = node('div', 'history-region-rows');
        const regionCapacity = Math.max(1, ...region.years.map((year, index) => region.rows.reduce((sum, row) => sum + row.points[index].localSeats, 0)));
        for (const row of region.rows.filter(item => item.points.some(point => point.localSeats > 0))) {
            const article = node('article', 'history-region-row');
            const label = partyIdentity(row.abbreviation, row.nameAr, '', row.continuityBasis);
            const values = node('div', 'history-region-values');
            row.points.forEach((point, index) => {
                const value = node('span', `history-year-${index + 1}`);
                value.style.setProperty('--seat-share', `${regionSeatBarPercent(point.localSeats, regionCapacity)}%`);
                value.setAttribute('aria-label', `${point.year}: ${number(point.localSeats)} ${t('seats')}`);
                const bar = node('i'); bar.setAttribute('aria-hidden', 'true');
                value.append(bar, bdi(number(point.localSeats))); values.append(value);
            });
            article.append(label, values); rows.append(article);
        }
        byId('historyRegionContent').replaceChildren(heading, legend, rows);
    }

    function renderEvidence() {
        const demographics = insights.deriveDemographicTrends(payload, { dimension: state.demographicDimension });
        if (!demographics.available || demographics.rows.length === 0) {
            byId('historyDemographicContent').textContent = t('noDemographics'); return;
        }
        const model = demographicChartModel(demographics);
        const color = index => `var(--history-demographic-${index % 5 + 1})`;
        const segmentLabel = segment => `${segment.categoryAr}: ${segment.sourcePercentageText}`;
        const values = chartYear => {
            const list = node('div', 'history-demographic-values');
            for (const segment of chartYear.segments) {
                const item = node('span');
                const swatch = node('i'); swatch.style.background = color(segment.colorIndex);
                const label = node('span');
                label.append(bdi(segment.categoryAr), node('strong', '', segment.sourcePercentageText));
                item.append(swatch, label); list.append(item);
            }
            if (chartYear.unreportedPercentage > 0) {
                const item = node('span', 'is-unreported');
                item.append(node('i'), node('span', '', `${t('unreported')}: ${number(chartYear.unreportedPercentage, { maximumFractionDigits: 2 })}%`));
                list.append(item);
            }
            return list;
        };

        if (model.kind === 'donut') {
            const charts = node('div', 'history-demographic-donuts');
            for (const chartYear of model.years) {
                let cursor = 0;
                const stops = chartYear.segments.map(segment => {
                    const start = cursor;
                    cursor += segment.percentage;
                    return `${color(segment.colorIndex)} ${start}% ${cursor}%`;
                });
                if (chartYear.unreportedPercentage > 0) {
                    stops.push(`var(--history-demographic-unreported) ${cursor}% 100%`);
                }
                const article = node('article', 'history-demographic-donut-card');
                const ring = node('div', 'history-demographic-donut');
                ring.style.background = `conic-gradient(${stops.join(',')})`;
                ring.setAttribute('role', 'img');
                ring.setAttribute('aria-label', `${chartYear.year}: ${chartYear.segments.map(segmentLabel).join(', ')}`);
                ring.append(node('strong', '', String(chartYear.year)));
                article.append(ring, values(chartYear)); charts.append(article);
            }
            byId('historyDemographicContent').replaceChildren(charts);
            return;
        }

        const charts = node('div', 'history-demographic-stacked');
        for (const chartYear of model.years) {
            const article = node('article', 'history-demographic-stack-card');
            article.append(node('strong', 'history-demographic-year', String(chartYear.year)));
            const track = node('div', 'history-demographic-stack');
            track.setAttribute('role', 'img');
            track.setAttribute('aria-label', `${chartYear.year}: ${chartYear.segments.map(segmentLabel).join(', ')}`);
            for (const segment of chartYear.segments) {
                const bar = node('span');
                bar.style.width = `${segment.percentage}%`;
                bar.style.background = color(segment.colorIndex);
                bar.title = segmentLabel(segment);
                track.append(bar);
            }
            if (chartYear.unreportedPercentage > 0) {
                const gap = node('span', 'is-unreported');
                gap.style.width = `${chartYear.unreportedPercentage}%`;
                gap.title = `${t('unreported')}: ${chartYear.unreportedPercentage}%`;
                track.append(gap);
            }
            article.append(track, values(chartYear)); charts.append(article);
        }
        byId('historyDemographicContent').replaceChildren(charts);
    }

    function transitionParty(model, side) {
        const party = node('span', 'history-party-transition-party');
        const logo = node('img', 'history-party-logo');
        logo.src = model[`${side}PartyLogo`];
        logo.alt = '';
        logo.loading = 'lazy';
        party.append(logo, bdi(model[`${side}Party`], 'history-party-transition-code'));
        return party;
    }

    function occurrenceTransition(group) {
        const model = repeatedNameTransition(group);
        const wrapper = node('div', 'history-occurrence-transition');
        if (!model) return wrapper;
        const transition = node('div', `history-party-transition${model.sameParty ? ' is-unchanged' : ''}`);
        transition.dir = 'ltr';
        transition.setAttribute('aria-label', `${model.fromYear} ${model.fromParty} → ${model.toYear} ${model.toParty}`);
        transition.append(transitionParty(model, 'from'));
        if (!model.sameParty) {
            const arrow = node('span', 'history-party-transition-arrow', '→');
            arrow.setAttribute('aria-hidden', 'true');
            transition.append(arrow, transitionParty(model, 'to'));
        }
        const place = node('span', 'history-transition-constituency');
        place.dir = 'auto';
        place.textContent = model.sameConstituency
            ? `${t('constituency')}: ${model.fromConstituency}`
            : `${t('constituency')}: ${model.fromConstituency} → ${model.toConstituency}`;
        wrapper.append(transition, place);
        const knownOutcomes = group.occurrences.filter(row => occurrenceOutcomeKey(row));
        if (knownOutcomes.length) {
            const outcomes = node('span', 'history-transition-constituency');
            outcomes.textContent = knownOutcomes.map(row =>
                `${row.year}: ${t(occurrenceOutcomeKey(row))}`).join(' · ');
            wrapper.append(outcomes);
        }
        return wrapper;
    }

    function movementBar(row, maximum, kind) {
        const item = node('div', `history-movement-row is-${kind}`);
        item.setAttribute('role', 'img');
        const identity = partyIdentity(row.abbreviation, row.partyNameAr,
            'history-movement-identity is-compact', row.continuityBasis);
        const series = movementOutcomeSeries(row, maximum, kind);
        if (!series.length) throw new Error('invalid movement election outcome');
        const values = node('div', 'history-movement-values');
        const descriptions = [];
        for (const point of series) {
            const label = t(point.labelKey, { year: state.to });
            const value = node('div', `history-movement-value${point.elected ? ' is-elected' : ''}`);
            const track = node('span', 'history-movement-track');
            const fill = node('i'); fill.style.width = `${point.percent}%`;
            track.append(fill);
            value.append(track, bdi(number(point.count), 'history-movement-count'));
            value.setAttribute('aria-label', `${label}: ${number(point.count)}`);
            value.title = `${label}: ${number(point.count)}`;
            descriptions.push(`${label}: ${number(point.count)}`);
            values.append(value);
        }
        item.setAttribute('aria-label', `${row.partyNameAr}: ${descriptions.join('; ')}`);
        item.append(identity, values);
        return item;
    }

    function renderPeopleMovements() {
        const movement = insights.deriveRepeatedNamePartyMovements(payload, Number(state.from), Number(state.to),
            { scope: state.peopleScope, backfill: affiliationBackfill });
        if (!movement.available) throw new Error('repeated name movement unavailable');
        for (const [kind, id] of [['gain', 'historyMovementGainsLegend'], ['loss', 'historyMovementLossesLegend']]) {
            const labels = kind === 'gain' ? ['movementIncoming', 'movementElected']
                : ['movementOutgoing', 'movementElectedElsewhere'];
            byId(id).replaceChildren(...labels.map((key, index) =>
                node('span', index === 1 ? 'is-elected' : '', t(key, { year: state.to }))));
        }
        byId('historyMovementGains').replaceChildren(...movement.gains.map(row =>
            movementBar(row, movement.maximum, 'gain')));
        byId('historyMovementLosses').replaceChildren(...movement.losses.map(row =>
            movementBar(row, movement.maximum, 'loss')));
    }

    function renderPeople() {
        byId('historyPeopleScope').value = state.peopleScope;
        setText('historyPeopleScopeLabel', t('peopleScope'));
        setText('historyScopeCandidates', t('affiliationScope'));
        setText('historyAffiliationCaveat', t('affiliationCaveat'));
        setText('historyScopeElected', t('scopeElected'));
        renderPeopleMovements();
        const result = insights.deriveRepeatedNameGroups(payload, Number(state.from), Number(state.to), {
            query: state.peopleQuery, differentPartyLabelsOnly: state.differentOnly, page: state.page,
            scope: state.peopleScope, backfill: affiliationBackfill
        });
        if (!result.available) throw new Error('repeated name explorer unavailable');
        state.page = result.page;
        const start = result.totalRows ? (result.page - 1) * result.pageSize + 1 : 0;
        const end = result.totalRows ? start + result.rows.length - 1 : 0;
        setText('historyPeopleCount', historyCountText(locale, 'peopleCount', result.totalRows));
        byId('historyPeopleEmpty').hidden = result.totalRows !== 0;
        setText('historyPeopleEmpty', t('noPeople'));
        byId('historyPeopleTable').hidden = result.totalRows === 0;
        byId('historyPeopleCards').hidden = result.totalRows === 0;
        byId('historyPeopleBody').replaceChildren(...result.rows.map(group => {
            const tr = node('tr'); const nameCell = node('th'); nameCell.scope = 'row'; nameCell.append(bdi(group.occurrences[0]?.nameAr || group.normalizedName));
            const records = node('td'); records.append(occurrenceTransition(group));
            tr.append(nameCell, records); return tr;
        }));
        byId('historyPeopleCards').replaceChildren(...result.rows.map(group => {
            const article = node('article', 'history-person-card');
            const heading = node('h3'); heading.append(bdi(group.occurrences[0]?.nameAr || group.normalizedName));
            article.append(heading, occurrenceTransition(group)); return article;
        }));
        const pageStatus = result.totalRows ? t('pageStatus', { page: number(result.page), pages: number(result.pageCount), start: number(start), end: number(end), total: number(result.totalRows) }) : t('emptyPageStatus');
        setText('historyPeoplePageStatus', pageStatus);
        byId('historyPeoplePrevious').disabled = result.page <= 1;
        byId('historyPeopleNext').disabled = result.page >= result.pageCount;
        syncUrl();
        announce(historyCountText(locale, 'peopleStatus', result.totalRows,
            { page: number(result.page), pages: number(result.pageCount || 1) }));
    }

    function renderSources() {
        byId('historySourceLinks').replaceChildren(...payload.elections.map(election => {
            const item = node('li'); const link = node('a', '', t('openElection', { year: election.year }));
            link.href = election.sourceUrl; link.target = '_blank'; link.rel = 'noopener noreferrer'; item.append(link); return item;
        }));
        const metadata = [
            [t('archiveFile'), payload.generation.sourceArchive],
            [t('archiveDigest'), payload.generation.sourceSha256],
            [t('aggregateStatus'), t('analysisLabel')]
        ];
        byId('historyArchiveMeta').replaceChildren(...metadata.flatMap(([term, value]) => {
            const description = node('dd'); description.append(bdi(value));
            return [node('dt', '', term), description];
        }));
        const sources = new Map();
        for (const row of affiliationBackfill?.records || []) {
            for (const source of row.evidence) sources.set(source.url, source);
        }
        setText('historyAffiliationSourcesTitle', t('affiliationSources'));
        byId('historyAffiliationSources').replaceChildren(...[...sources.values()].map(source => {
            const item = node('li');
            const link = node('a', '', `${source.publisher}${source.publishedAt ? ` (${source.publishedAt})` : ''}`);
            link.href = source.url; link.target = '_blank'; link.rel = 'noopener noreferrer'; item.append(link);
            return item;
        }));
    }

    function renderAll() {
        renderOverview(); renderParties(); renderTrajectory(); renderQuotient(); renderRegions(); renderEvidence(); renderPeople(); renderSources();
        syncHistoryTabs();
        setText('historyExactEarlier', state.from); setText('historyExactLater', state.to);
        announce(t('loadedStatus', { from: state.from, to: state.to }));
    }

    function updatePair() {
        const resolution = insights.resolveYearPair(payload, byId('historyFrom').value, byId('historyTo').value);
        byId('historyPairNotice').hidden = !resolution.usedFallback;
        setText('historyPairNotice', resolution.usedFallback ? t('fallback') : '');
        state.from = String(resolution.pair.fromYear); state.to = String(resolution.pair.toYear);
        if (state.measure === 'list' && !listMeasureAvailable(state.from, state.to)) state.measure = 'total';
        byId('historyFrom').value = state.from; byId('historyTo').value = state.to;
        syncUrl(); renderAll();
    }

    function bindEvents() {
        byId('historyRetry').addEventListener('click', load);
        byId('historyFrom').addEventListener('change', updatePair);
        byId('historyTo').addEventListener('change', updatePair);
        byId('historyTabs').addEventListener('click', event => {
            const button = event.target.closest('button[data-history-tab]');
            if (!button) return;
            selectHistoryTab(button.dataset.historyTab, { push: true });
        });
        byId('historyTabs').addEventListener('keydown', event => {
            if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
            const buttons = [...byId('historyTabs').querySelectorAll('button[data-history-tab]')];
            const current = buttons.indexOf(event.target.closest('button[data-history-tab]'));
            if (current < 0) return;
            event.preventDefault();
            const next = historyTabIndex(current, event.key, buttons.length,
                document.documentElement.dir === 'rtl' ? 'rtl' : 'ltr');
            selectHistoryTab(buttons[next].dataset.historyTab, { push: true, focus: true });
        });
        byId('historyMeasureTabs').addEventListener('click', event => {
            const button = event.target.closest('button[data-measure]');
            if (!button || button.disabled) return;
            state.measure = button.dataset.measure;
            syncUrl(); renderParties();
        });
        byId('historyPartySort').addEventListener('change', event => { state.sort = event.currentTarget.value; syncUrl(); renderParties(); });
        byId('historyPartySearch').addEventListener('input', event => { state.partyQuery = event.currentTarget.value; syncUrl(); renderParties(); });
        byId('historyShowAll').addEventListener('click', () => { state.showAllParties = !state.showAllParties; syncUrl(); renderParties(); });
        byId('historyTrajectoryParty').addEventListener('change', event => { state.party = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderTrajectory(); });
        byId('historyQuotientConstituency').addEventListener('change', event => { state.quotientConstituency = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderQuotient(); });
        byId('historyRegion').addEventListener('change', event => { state.region = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderRegions(); });
        byId('historyDemographic').addEventListener('change', event => { state.demographicDimension = event.currentTarget.value; syncUrl(); renderEvidence(); });
        byId('historyPeopleSearch').addEventListener('input', event => { state.peopleQuery = event.currentTarget.value; state.page = 1; renderPeople(); });
        byId('historyPeopleScope').addEventListener('change', event => { state.peopleScope = event.currentTarget.value; state.page = 1; renderPeople(); });
        byId('historyDifferentOnly').addEventListener('change', event => { state.differentOnly = event.currentTarget.checked; state.page = 1; renderPeople(); });
        byId('historyPeoplePrevious').addEventListener('click', () => { state.page -= 1; renderPeople(); byId('historyPeopleCount').focus(); });
        byId('historyPeopleNext').addEventListener('click', () => { state.page += 1; renderPeople(); byId('historyPeopleCount').focus(); });
        document.addEventListener('click', event => {
            const link = event.target.closest('a[data-history-tab-link="sources"]');
            if (!link) return;
            event.preventDefault();
            selectHistoryTab('sources', { push: true });
            byId('historySources').scrollIntoView({ block: 'start' });
        });
        root.addEventListener('popstate', () => {
            state = readUrlState(root.location.search);
            if (!payload) return;
            populateSelectors();
            renderAll();
            root.requestAnimationFrame(revealActiveHistoryTab);
        });
        document.addEventListener('fhemni:localechange', event => {
            locale = event.detail.locale; applyCopy();
            if (payload) { populateTrajectoryParties(); populateQuotientConstituencies(); populateRegions(); renderAll(); }
        });
    }

    async function load() {
        byId('historyLoading').hidden = false; byId('historyError').hidden = true; byId('historyContent').hidden = true;
        try {
            const response = await root.fetch(DATA_URL, { headers: { Accept: 'application/json' } });
            if (!response.ok) throw new Error(`history data ${response.status}`);
            const nextPayload = await root.FhemniHistoricalDataIntegrity.parseVerifiedHistoricalData(await response.arrayBuffer());
            const audit = insights.auditHistoricalPayload(nextPayload);
            if (!audit.available) throw new Error(`history data invalid: ${audit.diagnostics.join(',')}`);
            const backfillResponse = await root.fetch(BACKFILL_URL, { headers: { Accept: 'application/json' } });
            if (!backfillResponse.ok) throw new Error(`affiliation data ${backfillResponse.status}`);
            affiliationBackfill = await root.FhemniHistoricalDataIntegrity.parseVerifiedAffiliationBackfill(
                await backfillResponse.arrayBuffer());
            payload = nextPayload;
            quotientSimulation = quotientEngine?.derive2026LocalSeatCounterfactual(payload)
                || { available: false, diagnostics: ['quotient-engine-unavailable'] };
            fullSystemSimulation = quotientEngine?.derive2026Full2016SystemCounterfactual(payload)
                || { available: false, diagnostics: ['quotient-engine-unavailable'] };
            populateSelectors(); renderAll();
            byId('historyLoading').hidden = true; byId('historyContent').hidden = false;
            root.requestAnimationFrame(revealActiveHistoryTab);
        } catch (error) {
            console.error(error);
            byId('historyLoading').hidden = true; byId('historyError').hidden = false;
        }
    }

    function init() {
        locale = root.FhemniI18n?.locale?.() || 'ar';
        applyCopy(); bindEvents(); load();
    }

    document.addEventListener('DOMContentLoaded', init);
    return { createAffiliationSourceLink, occurrenceOutcomeKey, movementOutcomeSeries, COPY, readUrlState, writeUrlState, listMeasureAvailable, listSeatLabelKey, totalMeasureCaveatKey, partyBarPercent, partyBarMaximum, stackedSeatSeries, quotientViewState, demographicChartModel, createEvidenceStatusBadge, appendMethodologyLink, regionSeatBarPercent, seatSharePercent, resolveAvailableSelection, formatSigned, normalizeHistoryTab, historyTabIndex, historyTabPanelState, historicalPartyLogoAsset, repeatedNameTransition, partySearchRows, historyCountText, createHistoryAnnouncer };
});
