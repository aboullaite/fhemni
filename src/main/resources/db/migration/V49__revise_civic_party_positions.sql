UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'targeted-subsidies')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';


UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'essential-tax-relief')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    evidence_summary_ar = 'البرنامج كيدعو صراحة لإعادة تفعيل مسطرة تحديد الأسعار (القانون 104-12) وفرض رخص تصدير وحصص على الخضر الأساسية وإنشاء مرصد مستقل للأسعار والهوامش وتعميم ملصق السعر الأقصى (ص.60).',
    evidence_summary_fr = 'Le programme appelle explicitement à réactiver la procédure légale de fixation des prix (Loi 104-12), à imposer des licences et quotas d''exportation sur les légumes essentiels, à créer un observatoire des prix et marges, et à imposer un étiquetage obligatoire du prix maximum (p.60).',
    evidence_summary_en = 'The programme explicitly calls for reactivating the legal price-setting mechanism (Law 104-12), imposing export permits and quotas on essential vegetables, creating an independent prices and margins observatory, and mandatory maximum-price labelling (p.60).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    evidence_summary_ar = 'البرنامج فيه جانبين: كيدعو لإنهاء الاحتكارات وتعزيز المنافسة الشريفة (ص.27)، ولكن كيطالب أيضاً بتسقيف أسعار المواد الأساسية وتقنين هوامش المحروقات وعقود سلاسل القيمة الموجهة من الدولة (ص.5، 36).',
    evidence_summary_fr = 'Le programme affiche deux orientations : il prône la fin des monopoles et le renforcement de la concurrence loyale (p.27), mais réclame aussi le plafonnement des prix de base, la régulation des marges pétrolières et des contrats de chaîne de valeur pilotés par l''État (p.5, 36).',
    evidence_summary_en = 'The programme has two sides: it calls for ending monopolies and strengthening fair competition (p.27), but also demands price caps on basic goods, fuel margin regulation, and state-directed value-chain contracts (pp.5, 36).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: OPPOSES→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    evidence_summary_ar = 'البرنامج كيدعو للمنافسة الشريفة والشفافية عبر منصة رقمية لتتبع الأسعار (ص.7)، ولكن كيفرض أيضاً هدنة تصديرية (حظر تصدير) على المواد الغذائية الأساسية ومراقبة مباشرة للأسواق (ص.8-9).',
    evidence_summary_fr = 'Le programme prône la concurrence loyale et la transparence via une plateforme numérique de suivi des prix (p.7), mais impose aussi un moratoire à l''exportation de denrées de base et un contrôle direct des marchés (p.8-9).',
    evidence_summary_en = 'The programme advocates fair competition and transparency via a digital price-monitoring platform (p.7), but also imposes export bans on basic foodstuffs and direct market controls (pp.8-9).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'MP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    evidence_summary_ar = 'البرنامج كيدعو لنظام دائم لمراقبة وتسقيف هوامش الربح، ومراقبة أسعار المواد الغذائية الأساسية عبر لجان جهوية، وتغليظ عقوبات الاحتكار والمضاربة، وإعادة تشغيل مصفاة سامير (ص.66-67).',
    evidence_summary_fr = 'Le programme propose un système permanent de contrôle et de plafonnement des marges bénéficiaires, la surveillance des prix alimentaires via des comités régionaux, le renforcement des sanctions contre les monopoles et la spéculation, et la relance de la raffinerie SAMIR (p.66-67).',
    evidence_summary_en = 'The programme calls for a permanent system to monitor and cap profit margins, regional committees to control basic food prices, heavier penalties for monopolies and speculation, and restarting the SAMIR refinery for fuel price control (pp.66-67).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    evidence_summary_ar = 'البرنامج كيدعو لتقليص الوضعيات الاحتكارية وضمان المنافسة الحرة وإصلاح مجلس المنافسة (ص.26)، ولكن كيطالب أيضاً بتسقيف أسعار وهوامش المحروقات وتثبيت أسعار المواد الغذائية الاستراتيجية (ص.34).',
    evidence_summary_fr = 'Le programme veut réduire les oligopoles et garantir la libre concurrence via la réforme du Conseil de la concurrence (p.26), mais exige aussi le plafonnement des marges pétrolières et la stabilisation des prix alimentaires stratégiques (p.34).',
    evidence_summary_en = 'The programme calls for reducing oligopolies and guaranteeing free competition through Competition Council reform (p.26), but also demands fuel margin caps and strategic food price stabilisation (p.34).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: OPPOSES→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PPS' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'competition-prices')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    evidence_summary_ar = 'البرنامج كيدعم المقاولات الصغيرة والمتوسطة (30-35% من الصفقات العمومية)، لكن كيقترح كذلك بنك عمومي للاستثمار وشركة وطنية للتجهيز الصناعي — سياسة صناعية تقودها الدولة إلى جانب دعم المقاولات (ص.18-19، 35-36).',
    evidence_summary_fr = 'Le programme soutient les PME (30-35 % des marchés publics) mais propose aussi une banque publique d''investissement et une société nationale d''équipement industriel — une politique industrielle étatique parallèle au soutien des PME (p.18-19, 35-36).',
    evidence_summary_en = 'The programme supports SMEs (30-35% of public contracts) but also proposes a public investment bank and a state-owned industrial equipment company — state-led industrial policy alongside SME support, not SME-first (pp.18-19, 35-36).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'sme-jobs')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FGD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'sme-jobs')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    evidence_summary_ar = 'البرنامج كينشئ هيئة وطنية مستقلة لتقييم المنظومة التربوية وتعاقدات جهوية على أساس النتائج، مع أولوية مطلقة للتعلمات الأساسية (القراءة والكتابة والحساب) قبل التوسع في البنيات (ص.42).',
    evidence_summary_fr = 'Le programme crée un organisme national indépendant d''évaluation du système éducatif et des contrats régionaux basés sur les résultats, avec une priorité absolue aux apprentissages fondamentaux avant l''expansion des infrastructures (p.42).',
    evidence_summary_en = 'The programme establishes an independent national body for evaluating the education system and regional results-based contracts, with absolute priority on basic learning (reading, writing, math) before infrastructure expansion (p.42).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'FFD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'learning-accountability')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'learning-accountability')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-allocation')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    evidence_summary_ar = 'البرنامج كيعطي الأولوية لشبكات توزيع ماء الشرب وتقليص نسبة ضياع المياه لأقل من 15%، مع الربط بين الأحواض لتوزيع أعدل للموارد وتشجيع الزراعات المقاومة للجفاف (ص.34-37).',
    evidence_summary_fr = 'Le programme donne la priorité aux réseaux d''eau potable et à la réduction des pertes à moins de 15 %, avec l''interconnexion des bassins pour une distribution plus équitable et l''encouragement des cultures résistantes à la sécheresse (p.34-37).',
    evidence_summary_en = 'The programme prioritises drinking water networks and reducing water losses to under 15%, with inter-basin links for fairer distribution and encouraging drought-resistant crops (pp.34-37).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-allocation')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    evidence_summary_ar = 'البرنامج كيدعو لسياسة مائية مندمجة كتربط ترشيد الاستهلاك بتسريع وتيرة إنجاز السدود ومشاريع تحلية مياه البحر والربط المائي بين الأحواض، مع تكثيف بناء السدود التلية (ص.86، 400-401).',
    evidence_summary_fr = 'Le programme propose une politique hydrique intégrée liant la rationalisation de la consommation à l''accélération des barrages, des projets de dessalement et d''interconnexion des bassins, avec l''intensification de la construction de barrages collinaires (p.86, 400-401).',
    evidence_summary_en = 'The programme calls for an integrated water policy that links demand rationalisation to accelerating dams, desalination projects, and inter-basin transfers, with intensified hillside dam construction — rejecting demand-first sequencing (pp.86, 400-401).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    evidence_summary_ar = 'البرنامج كيدعو لتعميم إعادة استعمال المياه العادمة وتشجيع أنظمة الاقتصاد في استهلاك المياه والزراعات المقاومة للجفاف، مع تحلية مياه البحر كأداة مكملة (ص.34-37).',
    evidence_summary_fr = 'Le programme propose de généraliser la réutilisation des eaux usées, d''encourager les systèmes d''économie d''eau et les cultures résistantes à la sécheresse, le dessalement étant présenté comme un outil complémentaire (p.34-37).',
    evidence_summary_en = 'The programme calls for universalising wastewater reuse, incentivising water-saving systems, and encouraging drought-resistant crops, with desalination presented as a supplementary tool — demand management before supply-side solutions (pp.34-37).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'USFP' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'water-demand')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'OPPOSES',
    evidence_summary_ar = 'البرنامج كيشدد على انسجام التشريعات المرتبطة بالأسرة مع المرجعية الإسلامية وكيرفض مراجعة مدونة الأسرة اللي كتمس الثوابت، وكيدعو لتشجيع الزواج الشرعي وتكوين ما قبل الزواج بدل إصلاحات المساواة الاقتصادية وتقاسم الرعاية (ص.28).',
    evidence_summary_fr = 'Le programme insiste sur la conformité de la législation familiale au référentiel islamique et rejette toute révision du code de la famille touchant aux constantes constitutionnelles, promouvant le mariage légal et la formation pré-maritale plutôt que l''égalité économique et le partage des soins (p.28).',
    evidence_summary_en = 'The programme insists on aligning family legislation with the Islamic framework and resists family code reform affecting constitutional constants, promoting religious marriage and pre-marriage training rather than economic equality and shared care reforms (p.28).',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→OPPOSES after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PJD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'NO_POSITION',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→NO_POSITION after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'PUD' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'MIXED',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: SUPPORTS→MIXED after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'RNI' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';

UPDATE civic_party_positions SET stance = 'SUPPORTS',
    reviewer_note = COALESCE(reviewer_note, '') || ' | REVISED: MIXED→SUPPORTS after programme re-read',
    reviewed_at = CURRENT_TIMESTAMP
WHERE party_code = 'UC' AND question_id IN (SELECT id FROM civic_questions WHERE question_key = 'equality-care')
  AND edition_id = 'c1000000-0000-4000-8000-000000000001';
