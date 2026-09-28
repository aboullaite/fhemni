(function (root, factory) {
    const api = factory(root);
    if (typeof module === 'object' && module.exports) module.exports = api;
    else root.FhemniHistoricalElectionsPage = api;
})(typeof globalThis === 'undefined' ? this : globalThis, function (root) {
    'use strict';

    const DATA_URL = '/data/elections/history.json';
    const MEASURES = new Set(['local', 'total', 'list']);
    const SORTS = new Set(['delta-desc', 'delta-asc', 'later-desc', 'name']);
    const DEMOGRAPHIC_DIMENSIONS = new Set(['gender', 'age', 'education']);
    const COPY = {
        ar: {
            pageTitle: 'كيفاش تبدلات نتائج الانتخابات — فهّمني', metaDescription: 'قارن نتائج الانتخابات التشريعية المغربية الرسمية بين 2016 و2021 و2026.',
            loading: 'كنحضّرو المقارنة…', retry: 'عاود جرّب', errorTitle: 'ما قدرناش نفتحو الأرشيف الانتخابي', errorText: 'المعطيات ما تحمّلاتش ولا ما دازتش المراجعة. عاود جرّب.',
            eyebrow: 'الأرشيف الانتخابي', title: 'كيفاش تبدلات نتائج الانتخابات', intro: 'قارن جوج انتخابات، وتبع المقاعد والجهات والسجلات الرسمية عبر السنوات.',
            currentResultsLink: 'شوف نتائج 2026 الحالية', pairTitle: 'اختار المقارنة', from: 'الانتخابات السابقة', to: 'الانتخابات اللاحقة', pairHelp: 'كنعرضو غير الأزواج اللي عندها تغطية رسمية كاملة فهاد الأرشيف.', fallback: 'الاختيار فالرابط ما كانش مدعوم. رجعناك لآخر مقارنة متاحة.', selectionFallback: 'اختيار الحزب أو الجهة المحفوظ فالرابط ما بقاش متاح. عرضنا أول اختيار متاح.',
            jumpOverview: 'نظرة وطنية', jumpParties: 'تغيّر الأحزاب', jumpTrajectory: 'مسار حزب', jumpQuotient: 'القاسم الانتخابي', jumpRegions: 'الجهات', jumpEvidence: 'خصائص المنتخبين', jumpPeople: 'الأسماء المتكررة', jumpSources: 'المصادر والمنهجية', jumpLabel: 'أقسام المقارنة التاريخية',
            overviewKicker: 'الصورة الكبيرة', overviewTitle: 'كيفاش تبدّلات بنية التصويت؟', overviewIntro: 'كنعرضو المقاعد والأصوات ديال كل ورقة اقتراع بوحدها باش ما نخلطوش بين اللوائح.',
            ballotsCard: 'بنية المقاعد والأصوات', localBallot: 'الدوائر المحلية', nationalBallot: 'اللائحة الوطنية', regionalBallot: 'اللوائح الجهوية', ballotsNotVoters: 'الأصوات ديال كل ورقة اقتراع معروضة بوحدها؛ ماشي مجموع المصوتين.', seats: 'مقاعد', ballots: 'صوت',
            partiesKicker: 'الرابحين والخاسرين', partiesTitle: 'تغيّر مقاعد الأحزاب', partiesIntro: 'كل سطر كيبين العدد قبل ومن بعد والتغيّر الصافي، وما كيعنيش انتقال المقعد من حزب معيّن لآخر.',
            measure: 'المقياس', measureLocal: 'المقاعد المحلية', measureTotal: 'مجموع المقاعد', measureList: 'مقاعد اللائحة', partySearch: 'قلب على حزب', partySearchPlaceholder: 'الاسم الرسمي أو الاختصار', sort: 'الترتيب', sortDeltaDesc: 'أكبر زيادة', sortDeltaAsc: 'أكبر نقصان', sortLater: 'الأكثر فالسنة اللاحقة', sortName: 'الاسم',
            measureLocalCaveat: 'المقاعد المحلية هي المقارنة الافتراضية والأكثر تجانساً بين السنوات.', measureTotalCaveat: 'المجموع كيجمع 305 مقعد محلي و90 مقعد من اللوائح الجهوية فكل سنة.', measureTotalChangedCaveat: 'مجموع المقاعد قابل للحساب، ولكن النظام تبدّل: 2016 فيها 90 مقعد من اللائحة الوطنية، و2021 و2026 فيهم 90 مقعد من اللوائح الجهوية.', measureListCaveat: '2021 و2026 كيستعملو لوائح جهوية قابلة للمقارنة وطنياً.', listUnavailable: 'مقاعد اللائحة ما متاحاش فمقارنة فيها 2016، حيت كانت لائحة وطنية ماشي لوائح جهوية.', noPartyResults: 'ما لقينا حتى حزب بهاد البحث.', showAll: 'بين جميع الأحزاب ({count})', showLess: 'بين غير الأبرز', exactFigures: 'الأرقام المضبوطة', exactCaption: 'أعداد المقاعد حسب المقياس المختار', party: 'الحزب أو اللائحة', earlier: 'قبل', later: 'من بعد', change: 'التغيّر', unchanged: 'بلا تغيير',
            trajectoryKicker: 'ثلاث محطات', trajectoryTitle: 'مسار حزب عبر جميع الانتخابات', trajectoryIntro: 'اختار حزب أو لائحة وشوف المقاعد المحلية، مقاعد اللائحة الوطنية أو الجهوية، والمجموع فكل سنة متاحة.', trajectoryParty: 'الحزب أو اللائحة', totalSeats: 'المجموع', localSeats: 'محلية', listSeats: 'اللائحة', nationalListSeats: 'اللائحة الوطنية', regionalListSeats: 'اللوائح الجهوية', notObserved: 'ما بانش فالسجل الكامل لهاد السنة', sourceLabelOnly: 'الاستمرارية مبنية على نفس الاسم الرسمي بالضبط؛ التحالفات المختلفة ما كتندمجش.',
            quotientKicker: 'محاكاة مضادة للواقع', quotientTitle: 'واش القاسم الانتخابي القديم كان غادي يبدّل النتيجة؟', quotientIntro: 'كنطبقو قواعد 2016 على أصوات الدوائر المحلية ديال 2026: عتبة 3%، قاسم محسوب من أصوات اللوائح المؤهلة، ومن بعد أكبر البقايا.', quotientWarning: 'هادشي محاكاة مضادة للواقع من فهّمني، ماشي نتيجة رسمية. الأصوات الرسمية من elections.ma والحساب من فهّمني.', quotientLocalOnly: 'المقارنة الأساسية كتغطي 305 مقعد محلي، حيت هادو قابلين للمقارنة دائرة بدائرة.', quotientFullAssumption: 'باش نبينو سيناريو 395 مقعد، كنجمعو أصوات لوائح 2026 الجهوية وكنعاملوها كلائحة وطنية مفترضة بقواعد 2016. هاد الفرضية الإضافية ما كتقولش إن اللائحة الجهوية والوطنية نفس الشي.', quotientUnavailable: 'المحاكاة ما متاحاش حيت التحقق من قواعد 2016 ولا معطيات الدوائر ما دازش. ما اخترنا حتى فائز آلياً.', quotientValidationTitle: 'اختبار المقاعد المحلية', quotientValidationValue: '{constituencies}/92 دائرة · {seats}/305 مقعد', quotientValidationDetail: 'نفس القواعد رجعات التوزيع الرسمي المحلي ديال 2016 بالضبط، بلا حتى تعادل غير محسوم.', quotientListValidationTitle: 'اختبار اللائحة الوطنية', quotientListValidationValue: '{parties}/24 لائحة · {seats}/90 مقعد', quotientListValidationDetail: 'نفس القواعد رجعات توزيع اللائحة الوطنية الرسمي ديال 2016 بالضبط.', quotientPartyTitle: 'الفرق الوطني فالمقاعد المحلية', quotientPartyIntro: 'كنبينو هنا غير الأحزاب اللي تبدّل ليها العدد. الجدول المفصل كيفصل المحلي، الجهوي/الوطني، والمجموع لجميع الأحزاب.', quotientOfficial: 'رسمي 2026', quotientSimulated: 'بقواعد 2016', quotientDelta: 'الفرق', quotientAllParties: 'المحلي والجهوي/الوطني والمجموع', quotientAllPartiesCaption: 'تفصيل المقاعد الرسمية والمحاكية لكل حزب', quotientOfficialLocal: 'محلي رسمي', quotientSimulatedLocal: 'محلي محاكى', quotientOfficialRegional: 'جهوي رسمي', quotientSimulatedNational: 'وطني مفترض', quotientOfficialTotal: 'المجموع الرسمي', quotientSimulatedTotal: 'المجموع المحاكى', quotientConstituencyTitle: 'شوف الحساب داخل دائرة', quotientConstituency: 'الدائرة المحلية', quotientAllocatedSeats: 'المقاعد الموزعة', quotientTotalVotes: 'الأصوات الصحيحة', quotientEligibleVotes: 'أصوات اللوائح المؤهلة', quotientValue: 'القاسم الانتخابي', quotientThreshold: 'عتبة التأهل', quotientVotes: 'الأصوات', quotientEligible: 'مؤهلة', quotientFirstPass: 'المقاعد بالقاسم', quotientRemainder: 'الباقي', quotientRemainderSeat: 'مقعد بأكبر البقايا', quotientOfficialSeats: 'المقاعد الرسمية', quotientSimulatedSeats: 'المقاعد المحاكية', yes: 'نعم', no: 'لا',
            regionsKicker: 'فين وقع التغيّر', regionsTitle: 'مقارنة الجهات', regionsIntro: 'تكوين المقاعد المحلية فكل جهة عبر السنوات الثلاث.', region: 'الجهة', regionCaveat: 'مقارنة الجهات كتستعمل المقاعد المحلية فقط. لائحة 2016 الوطنية ما يمكنش نوزعوها على الجهات.', regionEmpty: 'ما كايناش مقارنة متاحة لهاد الجهة.',
            evidenceKicker: 'شنو نشر المصدر', evidenceTitle: 'خصائص المنتخبين', evidenceIntro: 'النسب الديموغرافية المنشورة حرفياً، مع الاحتفاظ بمرجع الدليل.', demographic: 'البعد', gender: 'النوع', age: 'العمر', education: 'المستوى الدراسي', demographicCaveat: 'كنعرضو غير النسب اللي نشرها elections.ma بنصها. ما كنستنتجوش أعداد من النسب، والفراغ ماشي صفر.', sourceQuery: 'مرجع الأرشيف', noDemographics: 'ما كايناش نسب منشورة لهاد البعد.',
            peopleKicker: 'السجل ماشي الهوية', peopleTitle: 'أسماء متكررة فلوائح المنتخبين', peopleIntro: 'استكشف التطابقات الحرفية والفريدة للاسم بين الانتخابات، مع كل ملاحظة رسمية بوحدها.', nameMatchCaveat: 'name_match_only كيعني غير تطابق الاسم، ماشي هوية متحقق منها وماشي دليل على تبديل الحزب.', peopleSearch: 'قلب فالأسامي والسجلات', peopleSearchPlaceholder: 'الاسم، الحزب أو الدائرة', differentOnly: 'غير السجلات اللي فيها تسميات حزبية مختلفة', peopleCount: '{count} تطابق اسم', peopleCaption: 'تطابقات الأسامي فالسجلات الرسمية', name: 'الاسم كما تنشر', observations: 'الملاحظات الانتخابية', evidence: 'قوة الدليل', nameMatchOnly: 'تطابق الاسم فقط', constituency: 'الدائرة', noPeople: 'ما لقينا حتى تطابق بهاد الفلاتر.', previous: 'السابق', next: 'التالي', paginationLabel: 'صفحات الأسماء المتكررة', pageStatus: 'الصفحة {page} من {pages} · {start}–{end} من {total}', emptyPageStatus: '0 نتائج',
            sourcesKicker: 'تتبّع الدليل', sourcesTitle: 'المصادر والمنهجية', sourcesIntro: 'هاد الصفحة كتوصل لسجلات كل انتخابات فـ elections.ma؛ المقارنات والحسابات كيديرهم فهّمني انطلاقاً من الأرشيف المثبّت.', sourceLinksTitle: 'روابط elections.ma', openElection: 'فتح سجل انتخابات {year}', methodTitle: 'شنو حسبنا؟', methodText: 'فهّمني كيحسب الفروق من لوائح كاملة ومراجعة. كنخليو أوراق الاقتراع منفصلة، كنستعملو المقاعد المحلية للجهات، وما كنثبتوش هوية الأشخاص من الاسم.', archiveFile: 'الأرشيف المثبّت', archiveDigest: 'SHA-256', aggregateStatus: 'صفة التجميع',
            loadedStatus: 'تحمّلات مقارنة {from} و{to}.', partyStatus: '{count} أحزاب أو لوائح ظاهرين.', peopleStatus: '{count} تطابقات؛ الصفحة {page} من {pages}.', selectedYears: '{from} ← {to}', sourceRecord: 'سجل elections.ma', analysisLabel: 'تحليل فهّمني مبني على elections.ma'
        },
        fr: {
            pageTitle: 'L’évolution des élections — Fhemni', metaDescription: 'Comparez les résultats officiels des législatives marocaines de 2016, 2021 et 2026.',
            loading: 'Préparation de la comparaison…', retry: 'Réessayer', errorTitle: 'Impossible d’ouvrir l’archive électorale', errorText: 'Les données n’ont pas pu être chargées ou validées. Réessayez.',
            eyebrow: 'Archive électorale', title: 'L’évolution des élections', intro: 'Comparez deux scrutins et suivez les sièges, les régions et les observations officielles dans le temps.',
            currentResultsLink: 'Voir les résultats actuels de 2026', pairTitle: 'Choisir la comparaison', from: 'Scrutin antérieur', to: 'Scrutin ultérieur', pairHelp: 'Seules les paires couvertes intégralement par cette archive officielle sont proposées.', fallback: 'La sélection du lien n’était pas disponible. La comparaison valide la plus récente est affichée.', selectionFallback: 'Le parti ou la région enregistré dans le lien n’est plus disponible. La première option disponible est affichée.',
            jumpOverview: 'Vue nationale', jumpParties: 'Évolution des partis', jumpTrajectory: 'Trajectoire', jumpQuotient: 'Quotient électoral', jumpRegions: 'Régions', jumpEvidence: 'Profils des élus', jumpPeople: 'Noms répétés', jumpSources: 'Sources et méthode', jumpLabel: 'Sections de la comparaison historique',
            overviewKicker: 'Vue d’ensemble', overviewTitle: 'Comment la structure du vote a-t-elle changé ?', overviewIntro: 'Les sièges et les voix de chaque bulletin sont présentés séparément afin de ne pas confondre les listes.',
            ballotsCard: 'Structure des sièges et bulletins', localBallot: 'Circonscriptions locales', nationalBallot: 'Liste nationale', regionalBallot: 'Listes régionales', ballotsNotVoters: 'Les voix de chaque bulletin restent séparées ; leur somme ne représente pas des électeurs uniques.', seats: 'sièges', ballots: 'voix',
            partiesKicker: 'Gains et pertes', partiesTitle: 'Évolution des sièges par parti', partiesIntro: 'Chaque ligne montre l’avant, l’après et l’écart net. Elle ne décrit pas un transfert d’un parti précis vers un autre.',
            measure: 'Mesure', measureLocal: 'Sièges locaux', measureTotal: 'Tous les sièges', measureList: 'Sièges de liste', partySearch: 'Rechercher un parti', partySearchPlaceholder: 'Nom officiel ou sigle', sort: 'Trier', sortDeltaDesc: 'Plus fortes hausses', sortDeltaAsc: 'Plus fortes baisses', sortLater: 'Plus de sièges ensuite', sortName: 'Nom',
            measureLocalCaveat: 'Les sièges locaux sont la comparaison par défaut et la plus homogène entre les années.', measureTotalCaveat: 'Le total réunit 305 sièges locaux et 90 sièges de listes régionales pour chaque année.', measureTotalChangedCaveat: 'Le total reste calculable, mais le système change : 90 sièges de liste nationale en 2016 contre 90 sièges de listes régionales en 2021 et 2026.', measureListCaveat: 'Les scrutins de 2021 et 2026 utilisent des listes régionales comparables au niveau national.', listUnavailable: 'Les sièges de liste sont indisponibles pour une comparaison avec 2016 : ce scrutin utilisait une liste nationale, pas des listes régionales.', noPartyResults: 'Aucun parti ne correspond à cette recherche.', showAll: 'Afficher tous les partis ({count})', showLess: 'Afficher les principaux', exactFigures: 'Chiffres exacts', exactCaption: 'Nombre de sièges selon la mesure choisie', party: 'Parti ou liste', earlier: 'Avant', later: 'Après', change: 'Écart', unchanged: 'Inchangé',
            trajectoryKicker: 'Trois étapes', trajectoryTitle: 'Trajectoire d’un parti sur tous les scrutins', trajectoryIntro: 'Choisissez un parti ou une liste pour voir les sièges locaux, de liste nationale ou régionale, et totaux à chaque scrutin disponible.', trajectoryParty: 'Parti ou liste', totalSeats: 'Total', localSeats: 'Locaux', listSeats: 'Liste', nationalListSeats: 'Liste nationale', regionalListSeats: 'Listes régionales', notObserved: 'Non observé dans la liste exhaustive de cette année', sourceLabelOnly: 'La continuité repose sur le même libellé source exact ; les alliances distinctes ne sont pas fusionnées.',
            quotientKicker: 'Simulation contrefactuelle', quotientTitle: 'Quel effet aurait eu l’ancien quotient électoral ?', quotientIntro: 'Nous appliquons aux voix locales de 2026 les règles de 2016 : seuil de 3 %, quotient calculé sur les voix des listes éligibles, puis plus forts restes.', quotientWarning: 'Il s’agit d’une simulation contrefactuelle de Fhemni, pas d’un résultat officiel. Les voix officielles proviennent d’elections.ma et le calcul est produit par Fhemni.', quotientLocalOnly: 'La comparaison principale porte sur les 305 sièges locaux, directement comparables circonscription par circonscription.', quotientFullAssumption: 'Pour montrer un scénario à 395 sièges, les voix régionales de 2026 sont agrégées et traitées comme une liste nationale hypothétique selon les règles de 2016. Cette hypothèse supplémentaire ne rend pas les listes régionales et nationale identiques.', quotientUnavailable: 'La simulation est indisponible car la validation des règles de 2016 ou des données locales a échoué. Aucun gagnant n’est choisi automatiquement.', quotientValidationTitle: 'Validation des sièges locaux', quotientValidationValue: '{constituencies}/92 circonscriptions · {seats}/305 sièges', quotientValidationDetail: 'Les mêmes règles reproduisent exactement la répartition officielle locale de 2016, sans égalité non résolue.', quotientListValidationTitle: 'Validation de la liste nationale', quotientListValidationValue: '{parties}/24 listes · {seats}/90 sièges', quotientListValidationDetail: 'Les mêmes règles reproduisent exactement la répartition officielle de la liste nationale de 2016.', quotientPartyTitle: 'Écart national des sièges locaux', quotientPartyIntro: 'Seuls les partis dont le total local change sont mis en avant. Le tableau détaille le local, le régional/national et le total pour tous les partis.', quotientOfficial: 'Officiel 2026', quotientSimulated: 'Règles de 2016', quotientDelta: 'Écart', quotientAllParties: 'Local, régional/national et total', quotientAllPartiesCaption: 'Décomposition des sièges officiels et simulés par parti', quotientOfficialLocal: 'Local officiel', quotientSimulatedLocal: 'Local simulé', quotientOfficialRegional: 'Régional officiel', quotientSimulatedNational: 'National hypothétique', quotientOfficialTotal: 'Total officiel', quotientSimulatedTotal: 'Total simulé', quotientConstituencyTitle: 'Inspecter le calcul d’une circonscription', quotientConstituency: 'Circonscription locale', quotientAllocatedSeats: 'Sièges à répartir', quotientTotalVotes: 'Suffrages valides', quotientEligibleVotes: 'Voix des listes éligibles', quotientValue: 'Quotient électoral', quotientThreshold: 'Seuil d’éligibilité', quotientVotes: 'Voix', quotientEligible: 'Éligible', quotientFirstPass: 'Sièges au quotient', quotientRemainder: 'Reste', quotientRemainderSeat: 'Siège au plus fort reste', quotientOfficialSeats: 'Sièges officiels', quotientSimulatedSeats: 'Sièges simulés', yes: 'Oui', no: 'Non',
            regionsKicker: 'Où cela change', regionsTitle: 'Comparaison régionale', regionsIntro: 'Composition des sièges locaux de chaque région sur les trois scrutins.', region: 'Région', regionCaveat: 'La comparaison régionale utilise uniquement les sièges locaux. La liste nationale de 2016 ne peut pas être attribuée aux régions.', regionEmpty: 'Aucune comparaison disponible pour cette région.',
            evidenceKicker: 'Ce que la source publie', evidenceTitle: 'Profils des élus', evidenceIntro: 'Pourcentages démographiques publiés littéralement, avec leur référence de preuve.', demographic: 'Dimension', gender: 'Genre', age: 'Âge', education: 'Niveau d’études', demographicCaveat: 'Seuls les pourcentages publiés littéralement par elections.ma sont affichés. Aucun effectif n’est déduit et une absence n’est pas un zéro.', sourceQuery: 'Référence d’archive', noDemographics: 'Aucun pourcentage publié pour cette dimension.',
            peopleKicker: 'Registre, pas identité', peopleTitle: 'Noms répétés dans les listes d’élus', peopleIntro: 'Explorez les correspondances exactes et uniques de noms entre scrutins, en conservant chaque observation officielle séparément.', nameMatchCaveat: 'name_match_only indique uniquement une correspondance de nom, pas une identité vérifiée ni la preuve d’un changement de parti.', peopleSearch: 'Rechercher dans les noms et registres', peopleSearchPlaceholder: 'Nom, parti ou circonscription', differentOnly: 'Uniquement les registres avec des libellés de parti différents', peopleCount: '{count} correspondances de nom', peopleCaption: 'Correspondances de noms dans les registres officiels', name: 'Nom publié', observations: 'Observations électorales', evidence: 'Niveau de preuve', nameMatchOnly: 'Correspondance de nom uniquement', constituency: 'Circonscription', noPeople: 'Aucune correspondance avec ces filtres.', previous: 'Précédent', next: 'Suivant', paginationLabel: 'Pages des noms répétés', pageStatus: 'Page {page} sur {pages} · {start}–{end} sur {total}', emptyPageStatus: '0 résultat',
            sourcesKicker: 'Remonter à la preuve', sourcesTitle: 'Sources et méthodologie', sourcesIntro: 'Cette page renvoie aux registres de chaque scrutin sur elections.ma ; les comparaisons et calculs sont produits par Fhemni à partir de l’archive épinglée.', sourceLinksTitle: 'Liens elections.ma', openElection: 'Ouvrir le registre du scrutin {year}', methodTitle: 'Que calculons-nous ?', methodText: 'Fhemni calcule les écarts à partir de listes exhaustives validées. Les bulletins restent séparés, les régions utilisent les sièges locaux et les noms ne prouvent pas l’identité.', archiveFile: 'Archive épinglée', archiveDigest: 'SHA-256', aggregateStatus: 'Statut de l’agrégation',
            loadedStatus: 'Comparaison {from}–{to} chargée.', partyStatus: '{count} partis ou listes affichés.', peopleStatus: '{count} correspondances ; page {page} sur {pages}.', selectedYears: '{from} → {to}', sourceRecord: 'Registre elections.ma', analysisLabel: 'Analyse Fhemni fondée sur elections.ma'
        },
        en: {
            pageTitle: 'How elections changed — Fhemni', metaDescription: 'Compare official Moroccan legislative election results for 2016, 2021, and 2026.',
            loading: 'Preparing the comparison…', retry: 'Try again', errorTitle: 'We could not open the election archive', errorText: 'The data could not be loaded or did not pass validation. Try again.',
            eyebrow: 'Election archive', title: 'How elections changed', intro: 'Compare two elections and follow seats, regions, and official records across the years.',
            currentResultsLink: 'View current 2026 results', pairTitle: 'Choose a comparison', from: 'Earlier election', to: 'Later election', pairHelp: 'Only pairs with complete coverage in this official archive are available.', fallback: 'That shared selection was unsupported. The newest valid comparison is shown.', selectionFallback: 'The party or region saved in this link is no longer available. The first available option is shown.',
            jumpOverview: 'National overview', jumpParties: 'Party changes', jumpTrajectory: 'Party trajectory', jumpQuotient: 'Electoral quotient', jumpRegions: 'Regions', jumpEvidence: 'Elected-member profiles', jumpPeople: 'Repeated names', jumpSources: 'Sources and method', jumpLabel: 'Historical election sections',
            overviewKicker: 'The big picture', overviewTitle: 'How did the voting structure change?', overviewIntro: 'Seats and votes are shown separately for each ballot so distinct lists are not mixed together.',
            ballotsCard: 'Seat and ballot structure', localBallot: 'Local constituencies', nationalBallot: 'National list', regionalBallot: 'Regional lists', ballotsNotVoters: 'Votes for each ballot stay separate; their sum is not a count of unique voters.', seats: 'seats', ballots: 'votes',
            partiesKicker: 'Gains and losses', partiesTitle: 'Party seat changes', partiesIntro: 'Each row shows the before count, after count, and net change. It does not claim a seat moved from one named party to another.',
            measure: 'Measure', measureLocal: 'Local seats', measureTotal: 'All seats', measureList: 'List seats', partySearch: 'Search parties', partySearchPlaceholder: 'Official name or abbreviation', sort: 'Sort', sortDeltaDesc: 'Largest gains', sortDeltaAsc: 'Largest losses', sortLater: 'Most seats later', sortName: 'Name',
            measureLocalCaveat: 'Local seats are the default and most comparable measure across all years.', measureTotalCaveat: 'The total combines 305 local seats and 90 regional-list seats in each election.', measureTotalChangedCaveat: 'Total seats remain countable, but the system changes: 90 national list seats in 2016 versus 90 regional list seats in 2021 and 2026.', measureListCaveat: 'The 2021 and 2026 elections use regional lists that are comparable at national level.', listUnavailable: 'List seats are unavailable for comparisons with 2016 because that election used a national list, not regional lists.', noPartyResults: 'No parties match this search.', showAll: 'Show all parties ({count})', showLess: 'Show leading parties', exactFigures: 'Exact figures', exactCaption: 'Seat counts for the selected measure', party: 'Party or list', earlier: 'Earlier', later: 'Later', change: 'Change', unchanged: 'No change',
            trajectoryKicker: 'Three points in time', trajectoryTitle: 'One party across every election', trajectoryIntro: 'Choose a party or list to see local, national-list or regional-list, and total seats in every available election.', trajectoryParty: 'Party or list', totalSeats: 'Total', localSeats: 'Local', listSeats: 'List', nationalListSeats: 'National list', regionalListSeats: 'Regional lists', notObserved: 'Not observed in that year’s complete roster', sourceLabelOnly: 'Continuity uses the same exact official source label; distinct alliances are not merged.',
            quotientKicker: 'Counterfactual simulation', quotientTitle: 'What would the old electoral quotient have changed?', quotientIntro: 'We apply the 2016 rules to the 2026 local vote: a 3% threshold, a quotient based on eligible-list votes, then largest remainders.', quotientWarning: 'This is a Fhemni counterfactual simulation, not an official result. Official votes come from elections.ma; Fhemni performs the calculation.', quotientLocalOnly: 'The primary comparison covers the 305 local seats, which are directly comparable constituency by constituency.', quotientFullAssumption: 'To show a 395-seat scenario, the 2026 regional-list votes are aggregated and treated as a hypothetical national list under the 2016 rules. This extra assumption does not make regional and national lists identical.', quotientUnavailable: 'The simulation is unavailable because the 2016 rule proof or local source data failed validation. No winner is selected automatically.', quotientValidationTitle: 'Local-seat validation', quotientValidationValue: '{constituencies}/92 constituencies · {seats}/305 seats', quotientValidationDetail: 'The same rules reproduce the official 2016 local allocation exactly, with no unresolved boundary ties.', quotientListValidationTitle: 'National-list validation', quotientListValidationValue: '{parties}/24 lists · {seats}/90 seats', quotientListValidationDetail: 'The same rules reproduce the official 2016 national-list allocation exactly.', quotientPartyTitle: 'National difference in local seats', quotientPartyIntro: 'Only parties whose local totals change are highlighted here. The table decomposes local, regional/national, and total seats for every party.', quotientOfficial: 'Official 2026', quotientSimulated: '2016 rules', quotientDelta: 'Difference', quotientAllParties: 'Local, regional/national, and total', quotientAllPartiesCaption: 'Official and simulated seat decomposition by party', quotientOfficialLocal: 'Official local', quotientSimulatedLocal: 'Simulated local', quotientOfficialRegional: 'Official regional', quotientSimulatedNational: 'Hypothetical national', quotientOfficialTotal: 'Official total', quotientSimulatedTotal: 'Simulated total', quotientConstituencyTitle: 'Inspect one constituency calculation', quotientConstituency: 'Local constituency', quotientAllocatedSeats: 'Seats allocated', quotientTotalVotes: 'Valid votes', quotientEligibleVotes: 'Eligible-list votes', quotientValue: 'Electoral quotient', quotientThreshold: 'Eligibility threshold', quotientVotes: 'Votes', quotientEligible: 'Eligible', quotientFirstPass: 'Quotient seats', quotientRemainder: 'Remainder', quotientRemainderSeat: 'Largest-remainder seat', quotientOfficialSeats: 'Official seats', quotientSimulatedSeats: 'Simulated seats', yes: 'Yes', no: 'No',
            regionsKicker: 'Where change happened', regionsTitle: 'Region comparison', regionsIntro: 'Local-seat composition in each region across all three elections.', region: 'Region', regionCaveat: 'Region comparisons use local seats only. The 2016 national list cannot be assigned to regions.', regionEmpty: 'No comparison is available for this region.',
            evidenceKicker: 'What the source reports', evidenceTitle: 'Elected-member profiles', evidenceIntro: 'Directly reported demographic percentages, with their evidence references preserved.', demographic: 'Dimension', gender: 'Gender', age: 'Age', education: 'Education', demographicCaveat: 'Only percentages literally reported by elections.ma are shown. Counts are not inferred, and missing does not mean zero.', sourceQuery: 'Archive reference', noDemographics: 'No reported percentages are available for this dimension.',
            peopleKicker: 'Records, not identity', peopleTitle: 'Names repeated in elected rosters', peopleIntro: 'Explore exact, unique name matches between elections while keeping every official observation separate.', nameMatchCaveat: 'name_match_only means a name match only, not verified identity and not evidence of a party switch.', peopleSearch: 'Search names and records', peopleSearchPlaceholder: 'Name, party, or constituency', differentOnly: 'Only records with different party labels', peopleCount: '{count} name matches', peopleCaption: 'Name matches in official elected rosters', name: 'Published name', observations: 'Election observations', evidence: 'Evidence status', nameMatchOnly: 'Name match only', constituency: 'Constituency', noPeople: 'No name matches meet these filters.', previous: 'Previous', next: 'Next', paginationLabel: 'Repeated-name pages', pageStatus: 'Page {page} of {pages} · {start}–{end} of {total}', emptyPageStatus: '0 results',
            sourcesKicker: 'Trace the evidence', sourcesTitle: 'Sources and methodology', sourcesIntro: 'This page links to the election-level records on elections.ma; Fhemni computes the comparisons from the pinned archive.', sourceLinksTitle: 'elections.ma links', openElection: 'Open the {year} election record', methodTitle: 'What did we calculate?', methodText: 'Fhemni calculates differences from validated complete rosters. Ballots stay separate, regions use local seats, and names do not prove identity.', archiveFile: 'Pinned archive', archiveDigest: 'SHA-256', aggregateStatus: 'Aggregate status',
            loadedStatus: 'Loaded the {from} to {to} comparison.', partyStatus: '{count} parties or lists shown.', peopleStatus: '{count} matches; page {page} of {pages}.', selectedYears: '{from} → {to}', sourceRecord: 'elections.ma record', analysisLabel: 'Fhemni analysis based on elections.ma'
        }
    };

    function readUrlState(search) {
        const params = new URLSearchParams(search || '');
        const year = name => /^20\d{2}$/.test(params.get(name) || '') ? params.get(name) : null;
        const measure = MEASURES.has(params.get('measure')) ? params.get('measure') : 'local';
        const sort = SORTS.has(params.get('sort')) ? params.get('sort') : 'delta-desc';
        const demographicDimension = DEMOGRAPHIC_DIMENSIONS.has(params.get('demographic')) ? params.get('demographic') : 'gender';
        const parsedPage = Number(params.get('page'));
        return {
            from: year('from'), to: year('to'), measure, sort,
            party: params.get('party') || '', partyQuery: params.get('partyq') || '',
            showAllParties: params.get('all') === '1', quotientConstituency: params.get('constituency') || '',
            region: params.get('region') || '', demographicDimension,
            peopleQuery: params.get('people') || '', differentOnly: params.get('different') === '1',
            page: Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1
        };
    }

    function writeUrlState(state) {
        const params = new URLSearchParams();
        if (state.from) params.set('from', state.from);
        if (state.to) params.set('to', state.to);
        if (state.measure && state.measure !== 'local') params.set('measure', state.measure);
        if (state.sort && state.sort !== 'delta-desc') params.set('sort', state.sort);
        if (state.party) params.set('party', state.party);
        if (state.partyQuery) params.set('partyq', state.partyQuery);
        if (state.showAllParties) params.set('all', '1');
        if (state.quotientConstituency) params.set('constituency', state.quotientConstituency);
        if (state.region) params.set('region', state.region);
        if (state.demographicDimension && state.demographicDimension !== 'gender') params.set('demographic', state.demographicDimension);
        if (state.peopleQuery) params.set('people', state.peopleQuery);
        if (state.differentOnly) params.set('different', '1');
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

    function evidenceReference(sourceQueryId, label) {
        return `${label}: ${sourceQueryId}`;
    }

    function formatSigned(value, locale) {
        if (!value) return '0';
        const digits = new Intl.NumberFormat(locale === 'ar' ? 'ar-MA' : locale).format(Math.abs(value));
        return `${value > 0 ? '+' : '−'}${digits}`;
    }

    if (!root.document || !root.addEventListener) {
        return { COPY, readUrlState, writeUrlState, listMeasureAvailable, listSeatLabelKey, totalMeasureCaveatKey, partyBarPercent, partyBarMaximum, regionSeatBarPercent, seatSharePercent, resolveAvailableSelection, evidenceReference, formatSigned };
    }

    const document = root.document;
    const insights = root.FhemniHistoricalElectionInsights;
    const quotientEngine = root.FhemniHistoricalElectoralQuotient;
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
    function announce(message) { setText('historyStatus', message); }
    function syncUrl() {
        root.history.replaceState(null, '', `${root.location.pathname}${writeUrlState(state)}${root.location.hash}`);
    }
    function measureValue(side) {
        if (state.measure === 'total') return side.totalSeats;
        if (state.measure === 'list') return side.listSeats;
        return side.localSeats;
    }

    function renderSelectionFallback() {
        const notice = byId('historySelectionNotice');
        notice.hidden = !selectionFallbackActive;
        notice.textContent = selectionFallbackActive ? t('selectionFallback') : '';
    }

    function applyCopy() {
        document.title = t('pageTitle');
        byId('historyMetaDescription').content = t('metaDescription');
        const labels = {
            historyLoadingText: 'loading', historyErrorTitle: 'errorTitle', historyErrorText: 'errorText', historyRetry: 'retry',
            historyEyebrow: 'eyebrow', historyTitle: 'title', historyIntro: 'intro', historyCurrentResultsLink: 'currentResultsLink', historyPairTitle: 'pairTitle', historyFromLabel: 'from', historyToLabel: 'to', historyPairHelp: 'pairHelp',
            historyOverviewKicker: 'overviewKicker', historyOverviewTitle: 'overviewTitle', historyOverviewIntro: 'overviewIntro',
            historyPartiesKicker: 'partiesKicker', historyPartiesTitle: 'partiesTitle', historyPartiesIntro: 'partiesIntro', historyMeasureLabel: 'measure', historyPartySearchLabel: 'partySearch', historySortLabel: 'sort',
            historyTrajectoryKicker: 'trajectoryKicker', historyTrajectoryTitle: 'trajectoryTitle', historyTrajectoryIntro: 'trajectoryIntro', historyTrajectoryPartyLabel: 'trajectoryParty',
            historyQuotientKicker: 'quotientKicker', historyQuotientTitle: 'quotientTitle', historyQuotientIntro: 'quotientIntro', historyQuotientWarning: 'quotientWarning', historyQuotientLocalOnly: 'quotientLocalOnly', historyQuotientFullAssumption: 'quotientFullAssumption', historyQuotientPartyTitle: 'quotientPartyTitle', historyQuotientPartyIntro: 'quotientPartyIntro', historyQuotientAllParties: 'quotientAllParties', historyQuotientAllPartiesCaption: 'quotientAllPartiesCaption', historyQuotientConstituencyTitle: 'quotientConstituencyTitle', historyQuotientConstituencyLabel: 'quotientConstituency', historyQuotientParty: 'party', historyQuotientOfficialLocal: 'quotientOfficialLocal', historyQuotientSimulatedLocal: 'quotientSimulatedLocal', historyQuotientOfficialRegional: 'quotientOfficialRegional', historyQuotientSimulatedNational: 'quotientSimulatedNational', historyQuotientOfficialTotal: 'quotientOfficialTotal', historyQuotientSimulatedTotal: 'quotientSimulatedTotal', historyQuotientDelta: 'quotientDelta', historyQuotientContestParty: 'party', historyQuotientVotes: 'quotientVotes', historyQuotientEligible: 'quotientEligible', historyQuotientFirstPass: 'quotientFirstPass', historyQuotientRemainder: 'quotientRemainder', historyQuotientRemainderSeat: 'quotientRemainderSeat', historyQuotientOfficialSeats: 'quotientOfficialSeats', historyQuotientSimulatedSeats: 'quotientSimulatedSeats',
            historyRegionsKicker: 'regionsKicker', historyRegionsTitle: 'regionsTitle', historyRegionsIntro: 'regionsIntro', historyRegionLabel: 'region', historyRegionCaveat: 'regionCaveat',
            historyEvidenceKicker: 'evidenceKicker', historyEvidenceTitle: 'evidenceTitle', historyEvidenceIntro: 'evidenceIntro', historyDemographicLabel: 'demographic', historyDemographicCaveat: 'demographicCaveat',
            historyPeopleKicker: 'peopleKicker', historyPeopleTitle: 'peopleTitle', historyPeopleIntro: 'peopleIntro', historyNameMatchCaveat: 'nameMatchCaveat', historyPeopleSearchLabel: 'peopleSearch', historyDifferentOnlyLabel: 'differentOnly', historyPeopleCaption: 'peopleCaption', historyPeopleName: 'name', historyPeopleObservations: 'observations', historyPeopleEvidence: 'evidence', historyPeoplePrevious: 'previous', historyPeopleNext: 'next',
            historySourcesKicker: 'sourcesKicker', historySourcesTitle: 'sourcesTitle', historySourcesIntro: 'sourcesIntro', historySourceLinksTitle: 'sourceLinksTitle', historyMethodTitle: 'methodTitle', historyMethodText: 'methodText', historyExactParty: 'party', historyExactChange: 'change'
        };
        for (const [id, key] of Object.entries(labels)) setText(id, t(key));
        byId('historyPartySearch').placeholder = t('partySearchPlaceholder');
        byId('historyPeopleSearch').placeholder = t('peopleSearchPlaceholder');
        byId('historyJumpNav').setAttribute('aria-label', t('jumpLabel'));
        byId('historyPagination').setAttribute('aria-label', t('paginationLabel'));
        if (!byId('historyPairNotice').hidden) setText('historyPairNotice', t('fallback'));
        renderSelectionFallback();
        const measure = byId('historyMeasure').options;
        measure[0].textContent = t('measureLocal'); measure[1].textContent = t('measureTotal'); measure[2].textContent = t('measureList');
        const sort = byId('historyPartySort').options;
        sort[0].textContent = t('sortDeltaDesc'); sort[1].textContent = t('sortDeltaAsc'); sort[2].textContent = t('sortLater'); sort[3].textContent = t('sortName');
        const demographic = byId('historyDemographic').options;
        demographic[0].textContent = t('gender'); demographic[1].textContent = t('age'); demographic[2].textContent = t('education');
        byId('historyExactFigures').querySelector('summary').textContent = t('exactFigures');
        setText('historyExactCaption', t('exactCaption'));
        rebuildJumps();
    }

    function rebuildJumps() {
        const items = [['historyOverview', 'jumpOverview'], ['historyParties', 'jumpParties'], ['historyTrajectory', 'jumpTrajectory'], ['historyQuotient', 'jumpQuotient'], ['historyRegions', 'jumpRegions'], ['historyEvidence', 'jumpEvidence'], ['historyPeople', 'jumpPeople'], ['historySources', 'jumpSources']];
        byId('historyJumpNav').replaceChildren(...items.map(([id, key]) => {
            const link = node('a', '', t(key)); link.href = `#${id}`; return link;
        }));
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
        if (state.measure === 'list' && !listMeasureAvailable(state.from, state.to)) state.measure = 'local';
        const from = byId('historyFrom'); const to = byId('historyTo');
        from.replaceChildren(...years.slice(0, -1).map(year => option(String(year), String(year))));
        to.replaceChildren(...years.slice(1).map(year => option(String(year), String(year))));
        from.value = state.from; to.value = state.to;
        byId('historyMeasure').value = state.measure;
        byId('historyMeasure').querySelector('option[value="list"]').disabled = !listMeasureAvailable(state.from, state.to);
        byId('historyPartySort').value = state.sort;
        byId('historyPartySearch').value = state.partyQuery;
        byId('historyPeopleSearch').value = state.peopleQuery;
        byId('historyDifferentOnly').checked = state.differentOnly;
        byId('historyDemographic').value = state.demographicDimension;
        const partyFallback = populateTrajectoryParties();
        const quotientFallback = populateQuotientConstituencies();
        const regionFallback = populateRegions();
        selectionFallbackActive = partyFallback || quotientFallback || regionFallback;
        renderSelectionFallback();
        syncUrl();
    }

    function populateTrajectoryParties() {
        const latestYear = Math.max(...payload.elections.map(item => item.year));
        const latestSeats = new Map(payload.nationalPartyResults.filter(row => row.year === latestYear).map(row => [row.comparisonKey, row.seats]));
        const seen = new Map();
        for (const party of payload.partyObservations) if (!seen.has(party.comparisonKey)) seen.set(party.comparisonKey, party);
        const parties = [...seen.values()].sort((a, b) => (latestSeats.get(b.comparisonKey) || 0) - (latestSeats.get(a.comparisonKey) || 0) || a.nameAr.localeCompare(b.nameAr, 'ar'));
        const select = byId('historyTrajectoryParty');
        select.replaceChildren(...parties.map(party => option(party.comparisonKey, `${party.abbreviation || '—'} · ${party.nameAr}`)));
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
        ballots.append(node('span', 'section-kicker', t('ballotsCard')));
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

    function partyBar(row, maximum) {
        const item = node('article', 'history-party-row');
        const identity = node('div', 'history-party-identity');
        identity.append(bdi(row.abbreviation || '—', 'history-party-code'), bdi(row.nameAr, 'history-party-name'));
        const graphic = node('div', 'history-delta-graphic');
        const loss = node('span', 'history-delta-loss'); const gain = node('span', 'history-delta-gain');
        const width = `${partyBarPercent(row.selectedDelta, maximum)}%`;
        if (row.selectedDelta < 0) loss.style.width = width;
        if (row.selectedDelta > 0) gain.style.width = width;
        graphic.append(loss, node('i', 'history-delta-axis'), gain);
        const values = node('div', 'history-party-values');
        values.append(bdi(number(measureValue(row.earlier))), node('span', '', '→'), bdi(number(measureValue(row.later))), bdi(formatSigned(row.selectedDelta, locale), row.selectedDelta < 0 ? 'is-loss' : row.selectedDelta > 0 ? 'is-gain' : ''));
        item.append(identity, graphic, values);
        return item;
    }

    function renderParties() {
        const result = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: state.sort, query: state.partyQuery,
            showAll: state.showAllParties
        });
        if (!result.available) throw new Error('party changes unavailable');
        const scale = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: 'delta-desc', query: '', showAll: true
        });
        if (!scale.available) throw new Error('party scale unavailable');
        const maximum = partyBarMaximum(scale.rows);
        byId('historyPartyBars').replaceChildren(...result.rows.map(row => partyBar(row, maximum)));
        byId('historyPartyEmpty').hidden = result.totalRows !== 0;
        setText('historyPartyEmpty', t('noPartyResults'));
        const showButton = byId('historyShowAll');
        showButton.hidden = result.totalRows <= 10;
        showButton.textContent = state.showAllParties ? t('showLess') : t('showAll', { count: number(result.totalRows) });
        const caveat = state.measure === 'list' ? 'measureListCaveat' : state.measure === 'total' ? totalMeasureCaveatKey(state.from, state.to) : 'measureLocalCaveat';
        const availability = listMeasureAvailable(state.from, state.to) ? '' : ` ${t('listUnavailable')}`;
        setText('historyMeasureCaveat', `${t(caveat)}${availability}`);
        setText('historyExactEarlier', state.from); setText('historyExactLater', state.to);
        const exact = insights.derivePartyDeltas(payload, Number(state.from), Number(state.to), {
            measure: state.measure, sort: state.sort, query: state.partyQuery, showAll: true
        });
        byId('historyExactBody').replaceChildren(...exact.rows.map(row => {
            const tr = node('tr'); const nameCell = node('th'); nameCell.scope = 'row'; nameCell.append(bdi(`${row.abbreviation || '—'} · ${row.nameAr}`));
            const earlier = node('td'); earlier.append(bdi(number(measureValue(row.earlier))));
            const later = node('td'); later.append(bdi(number(measureValue(row.later))));
            const delta = node('td'); delta.append(bdi(formatSigned(row.selectedDelta, locale)));
            tr.append(nameCell, earlier, later, delta); return tr;
        }));
        announce(t('partyStatus', { count: number(result.rows.length) }));
    }

    function renderTrajectory() {
        const trajectory = insights.derivePartyTrajectory(payload, state.party);
        if (!trajectory.available) return;
        const maximum = Math.max(1, ...trajectory.points.map(point => point.totalSeats));
        const heading = node('div', 'history-trajectory-heading');
        heading.append(bdi(trajectory.abbreviation || '—', 'history-party-code'), bdi(trajectory.nameAr), node('span', 'history-analysis-label', t('analysisLabel')));
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
        if (!quotientSimulation?.available) {
            const unavailable = node('article', 'history-quotient-proof is-unavailable');
            unavailable.append(node('span', 'section-kicker', t('quotientValidationTitle')),
                node('strong', '', t('quotientUnavailable')));
            byId('historyQuotientValidation').replaceChildren(unavailable);
            national.hidden = true;
            constituency.hidden = true;
            return;
        }

        national.hidden = false;
        constituency.hidden = false;
        const validation = quotientSimulation.validation;
        const proof = node('article', 'history-quotient-proof');
        proof.append(node('span', 'section-kicker', t('quotientValidationTitle')),
            node('strong', '', t('quotientValidationValue', {
                constituencies: number(validation.exactConstituencyMatches),
                seats: number(validation.exactSeatMatches)
            })), node('p', '', t('quotientValidationDetail')));
        const proofs = [proof];
        if (fullSystemSimulation?.available) {
            const listValidation = fullSystemSimulation.listValidation;
            const listProof = node('article', 'history-quotient-proof');
            listProof.append(node('span', 'section-kicker', t('quotientListValidationTitle')),
                node('strong', '', t('quotientListValidationValue', {
                    parties: number(listValidation.exactPartyMatches),
                    seats: number(listValidation.exactSeatMatches)
                })), node('p', '', t('quotientListValidationDetail')));
            proofs.push(listProof);
        }
        byId('historyQuotientValidation').replaceChildren(...proofs);

        const changed = quotientSimulation.partyDeltas.filter(row => row.delta !== 0);
        byId('historyQuotientPartyChanges').replaceChildren(...changed.map(row => {
            const article = node('article', 'history-quotient-party-row');
            const identity = node('div', 'history-party-identity');
            identity.append(bdi(row.abbreviation || '—', 'history-party-code'),
                bdi(row.nameAr, 'history-party-name'));
            const values = node('div', 'history-quotient-party-values');
            for (const [label, value] of [[t('quotientOfficial'), row.officialLocalSeats],
                [t('quotientSimulated'), row.simulatedLocalSeats]]) {
                const metric = node('span');
                metric.append(node('small', '', label), bdi(number(value)));
                values.append(metric);
            }
            const delta = node('strong', row.delta > 0 ? 'is-gain' : 'is-loss',
                formatSigned(row.delta, locale));
            delta.setAttribute('aria-label', `${t('quotientDelta')}: ${formatSigned(row.delta, locale)}`);
            article.append(identity, values, delta);
            return article;
        }));

        const fullRows = fullSystemSimulation?.available ? fullSystemSimulation.partyDeltas : [];
        byId('historyQuotientPartyBody').closest('details').hidden = !fullSystemSimulation?.available;
        byId('historyQuotientPartyBody').replaceChildren(...fullRows.map(row => {
            const tr = node('tr');
            const name = node('th'); name.scope = 'row';
            name.append(bdi(`${row.abbreviation || '—'} · ${row.nameAr}`));
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
            [t('quotientEligibleVotes'), number(contest.eligibleVotes)],
            [t('quotientThreshold'), `3% · ${number(contest.eligibilityThreshold.value)} ${t('ballots')}`],
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
            name.append(bdi(`${party.abbreviation || '—'} · ${party.nameAr}`));
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
            const label = node('div', 'history-party-identity'); label.append(bdi(row.abbreviation || '—', 'history-party-code'), bdi(row.nameAr, 'history-party-name'));
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
        const table = node('div', 'history-demographic-grid');
        for (const row of demographics.rows) {
            const article = node('article', 'history-demographic-row');
            article.append(bdi(row.categoryAr, 'history-demographic-category'));
            const pointsNode = node('div', 'history-demographic-points');
            for (const year of demographics.years) {
                const point = row.points.find(item => item.year === year);
                const value = node('span'); value.append(node('small', '', String(year)), node('bdi', '', point ? point.sourcePercentageText : '—'));
                if (point) value.append(node('small', 'history-demographic-source', evidenceReference(point.sourceQueryId, t('sourceQuery'))));
                pointsNode.append(value);
            }
            article.append(pointsNode); table.append(article);
        }
        byId('historyDemographicContent').replaceChildren(table);
    }

    function occurrenceList(group, compact = false) {
        const list = node(compact ? 'div' : 'ul', compact ? 'history-occurrence-cards' : 'history-occurrence-list');
        for (const item of group.occurrences) {
            const entry = node(compact ? 'article' : 'li');
            entry.append(node('strong', '', String(item.year)), bdi(item.abbreviation || '—', 'history-party-code'), bdi(item.nameAr));
            const place = node('span', '', `${t('constituency')}: ${item.constituencyNameAr}`); place.dir = 'auto'; entry.append(place);
            list.append(entry);
        }
        return list;
    }

    function renderPeople() {
        const result = insights.deriveRepeatedNameGroups(payload, {
            query: state.peopleQuery, differentPartyLabelsOnly: state.differentOnly, page: state.page
        });
        if (!result.available) throw new Error('repeated name explorer unavailable');
        state.page = result.page;
        const start = result.totalRows ? (result.page - 1) * result.pageSize + 1 : 0;
        const end = result.totalRows ? start + result.rows.length - 1 : 0;
        setText('historyPeopleCount', t('peopleCount', { count: number(result.totalRows) }));
        byId('historyPeopleEmpty').hidden = result.totalRows !== 0;
        setText('historyPeopleEmpty', t('noPeople'));
        byId('historyPeopleTable').hidden = result.totalRows === 0;
        byId('historyPeopleCards').hidden = result.totalRows === 0;
        byId('historyPeopleBody').replaceChildren(...result.rows.map(group => {
            const tr = node('tr'); const nameCell = node('th'); nameCell.scope = 'row'; nameCell.append(bdi(group.occurrences[0]?.nameAr || group.normalizedName));
            const records = node('td'); records.append(occurrenceList(group));
            const evidence = node('td', 'history-evidence-status', t('nameMatchOnly'));
            tr.append(nameCell, records, evidence); return tr;
        }));
        byId('historyPeopleCards').replaceChildren(...result.rows.map(group => {
            const article = node('article', 'history-person-card');
            const heading = node('h3'); heading.append(bdi(group.occurrences[0]?.nameAr || group.normalizedName));
            article.append(heading, node('p', 'history-evidence-status', t('nameMatchOnly')), occurrenceList(group, true)); return article;
        }));
        const pageStatus = result.totalRows ? t('pageStatus', { page: number(result.page), pages: number(result.pageCount), start: number(start), end: number(end), total: number(result.totalRows) }) : t('emptyPageStatus');
        setText('historyPeoplePageStatus', pageStatus);
        byId('historyPeoplePrevious').disabled = result.page <= 1;
        byId('historyPeopleNext').disabled = result.page >= result.pageCount;
        syncUrl();
        announce(t('peopleStatus', { count: number(result.totalRows), page: number(result.page), pages: number(result.pageCount || 1) }));
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
    }

    function renderAll() {
        renderOverview(); renderParties(); renderTrajectory(); renderQuotient(); renderRegions(); renderEvidence(); renderPeople(); renderSources();
        setText('historyExactEarlier', state.from); setText('historyExactLater', state.to);
        announce(t('loadedStatus', { from: state.from, to: state.to }));
    }

    function updatePair() {
        const resolution = insights.resolveYearPair(payload, byId('historyFrom').value, byId('historyTo').value);
        byId('historyPairNotice').hidden = !resolution.usedFallback;
        setText('historyPairNotice', resolution.usedFallback ? t('fallback') : '');
        state.from = String(resolution.pair.fromYear); state.to = String(resolution.pair.toYear);
        const listOption = byId('historyMeasure').querySelector('option[value="list"]');
        listOption.disabled = !listMeasureAvailable(state.from, state.to);
        if (state.measure === 'list' && listOption.disabled) {
            state.measure = 'local';
            byId('historyMeasure').value = state.measure;
        }
        byId('historyFrom').value = state.from; byId('historyTo').value = state.to;
        syncUrl(); renderAll();
    }

    function bindEvents() {
        byId('historyRetry').addEventListener('click', load);
        byId('historyFrom').addEventListener('change', updatePair);
        byId('historyTo').addEventListener('change', updatePair);
        byId('historyMeasure').addEventListener('change', event => { state.measure = event.currentTarget.value; syncUrl(); renderParties(); });
        byId('historyPartySort').addEventListener('change', event => { state.sort = event.currentTarget.value; syncUrl(); renderParties(); });
        byId('historyPartySearch').addEventListener('input', event => { state.partyQuery = event.currentTarget.value; syncUrl(); renderParties(); });
        byId('historyShowAll').addEventListener('click', () => { state.showAllParties = !state.showAllParties; syncUrl(); renderParties(); });
        byId('historyTrajectoryParty').addEventListener('change', event => { state.party = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderTrajectory(); });
        byId('historyQuotientConstituency').addEventListener('change', event => { state.quotientConstituency = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderQuotient(); });
        byId('historyRegion').addEventListener('change', event => { state.region = event.currentTarget.value; selectionFallbackActive = false; renderSelectionFallback(); syncUrl(); renderRegions(); });
        byId('historyDemographic').addEventListener('change', event => { state.demographicDimension = event.currentTarget.value; syncUrl(); renderEvidence(); });
        byId('historyPeopleSearch').addEventListener('input', event => { state.peopleQuery = event.currentTarget.value; state.page = 1; renderPeople(); });
        byId('historyDifferentOnly').addEventListener('change', event => { state.differentOnly = event.currentTarget.checked; state.page = 1; renderPeople(); });
        byId('historyPeoplePrevious').addEventListener('click', () => { state.page -= 1; renderPeople(); byId('historyPeopleCount').focus(); });
        byId('historyPeopleNext').addEventListener('click', () => { state.page += 1; renderPeople(); byId('historyPeopleCount').focus(); });
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
            const nextPayload = await response.json();
            const audit = insights.auditHistoricalPayload(nextPayload);
            if (!audit.available) throw new Error(`history data invalid: ${audit.diagnostics.join(',')}`);
            payload = nextPayload;
            quotientSimulation = quotientEngine?.derive2026LocalSeatCounterfactual(payload)
                || { available: false, diagnostics: ['quotient-engine-unavailable'] };
            fullSystemSimulation = quotientEngine?.derive2026Full2016SystemCounterfactual(payload)
                || { available: false, diagnostics: ['quotient-engine-unavailable'] };
            populateSelectors(); renderAll();
            byId('historyLoading').hidden = true; byId('historyContent').hidden = false;
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
    return { COPY, readUrlState, writeUrlState, listMeasureAvailable, listSeatLabelKey, totalMeasureCaveatKey, partyBarPercent, partyBarMaximum, regionSeatBarPercent, seatSharePercent, resolveAvailableSelection, evidenceReference, formatSigned };
});
