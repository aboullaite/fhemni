-- Complete only the affiliations stated explicitly in participant-specific
-- roles from published episode analyses. Do not infer an affiliation from an
-- episode title or from another guest in the same episode.

-- These are the only two parties named by those roles that are not already in
-- the directory. Both appear in the official 2026 candidate-list publication:
-- https://www.maroc.ma/fr/actualites/legislatives-2026-1850-listes-de-candidatures-presentees-lechelle-nationale
-- Their electoral symbols remain deliberately unverified.
INSERT INTO political_parties (
    code, name_fr, name_ar, color, sort_order, visible,
    symbol_label_fr, symbol_label_ar, symbol_asset, symbol_verified,
    catalogue_code
) VALUES
    ('ALAMAL', 'Parti Al Amal', 'حزب الأمل', '#7C2D12', 22, TRUE,
     'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE, 'ALAMAL'),
    ('PRD', 'Parti de la Réforme et du Développement', 'حزب الإصلاح والتنمية', '#075985', 23, TRUE,
     'Symbole à vérifier', 'الرمز خاصو مراجعة', '/assets/parties/party.svg', FALSE, 'PRD');

-- Production already knows these people from their episode analyses. Seeding
-- the same identities also makes a fresh database resolve them consistently.
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar)
SELECT seed.slug, seed.display_name_fr, seed.display_name_ar
  FROM (VALUES
        ('خالد-البقالي', 'خالد البقالي', 'خالد البقالي'),
        ('بدر-العربي', 'بدر العربي', 'بدر العربي'),
        ('نبيل-العادل', 'نبيل العادل', 'نبيل العادل'),
        ('مصطفي-صغيري', 'مصطفى صغيري', 'مصطفى صغيري'),
        ('هشام-ايت-منا', 'هشام آيت منا', 'هشام آيت منا'),
        ('محمد-اوجار', 'محمد أوجار', 'محمد أوجار'),
        ('ليلي-ذاكري', 'ليلى ذاكري', 'ليلى ذاكري'),
        ('عبد-الجبار-الرشيدي', 'عبد الجبار الرشيدي', 'عبد الجبار الرشيدي'),
        ('كمال-الهشومي', 'كمال الهشومي', 'كمال الهشومي'),
        ('سمير-الباز', 'سمير الباز', 'سمير الباز'),
        ('سليمه-غريطه', 'سليمة غريطة', 'سليمة غريطة'),
        ('باني-محمد-ولد-بركه', 'باني محمد ولد بركة', 'باني محمد ولد بركة')
       ) AS seed(slug, display_name_fr, display_name_ar)
 WHERE NOT EXISTS (
       SELECT 1
         FROM directory_persons person
        WHERE person.slug = seed.slug
 );

INSERT INTO person_affiliations (
    person_slug, party_code, valid_from, valid_until,
    source_url, source_label, verified_at, created_at
)
SELECT seed.person_slug, seed.party_code, NULL, NULL,
       seed.source_url, seed.source_label, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM (VALUES
        ('خالد-البقالي', 'PDN', 'https://www.youtube.com/watch?v=uE4zzVBstx4',
         'الأمين العام للحزب الديمقراطي الوطني'),
        ('بدر-العربي', 'PDN', 'https://www.youtube.com/watch?v=B1COnlcQLs4',
         'محامي والمنسق الإقليمي للحزب الديمقراطي الوطني ببني ملال ومناظر فقرة وجها لوجه'),
        ('نبيل-العادل', 'MP', 'https://www.youtube.com/watch?v=fzTGSPbbvc0',
         'عضو المجلس الوطني لحزب الحركة الشعبية'),
        ('مصطفي-صغيري', 'MP', 'https://www.youtube.com/watch?v=26AjT1bdgSw',
         'رئيس لجنة البرنامج الانتخابي وعضو المجلس الوطني لحزب الحركة الشعبية'),
        ('هشام-ايت-منا', 'RNI', 'https://www.youtube.com/watch?v=Z-ACRzTq2ZI',
         'قيادي في حزب التجمع الوطني للأحرار وظهر في مقطع فيديو مسجل'),
        ('محمد-اوجار', 'RNI', 'https://www.youtube.com/watch?v=w3hjGY-Fcjo',
         'عضو المكتب السياسي لحزب التجمع الوطني للأحرار ووزير عدل أسبق'),
        ('ليلي-ذاكري', 'PPS', 'https://www.youtube.com/watch?v=4fVg02R0HAU',
         'عضو المكتب الوطني للشبيبة الاشتراكية واللجنة المركزية لحزب التقدم والاشتراكية'),
        ('عبد-الجبار-الرشيدي', 'PI', 'https://www.youtube.com/watch?v=mYMsouy08Y8',
         'رئيس المجلس الوطني لحزب الاستقلال وكاتب دولة مكلف بالإدماج الاجتماعي'),
        ('كمال-الهشومي', 'USFP', 'https://www.youtube.com/watch?v=Wk3NOgPXwlQ',
         'أستاذ القانون الدستوري والعلوم السياسية وعضو المكتب السياسي لحزب الاتحاد الاشتراكي للقوات الشعبية'),
        ('سمير-الباز', 'PML', 'https://www.youtube.com/watch?v=w3hjGY-Fcjo',
         'عضو المكتب السياسي للحزب المغربي الحر وباحث في التواصل السياسي'),
        ('سليمه-غريطه', 'PRD', 'https://www.youtube.com/watch?v=scW1dED_R28',
         'محاسبة والمنسقة الإقليمية لحزب الإصلاح والتنمية بسيدي سليمان'),
        ('باني-محمد-ولد-بركه', 'ALAMAL', 'https://www.youtube.com/watch?v=SS8hBq5eGe0',
         'الأمين العام لحزب الأمل')
       ) AS seed(person_slug, party_code, source_url, source_label)
 WHERE NOT EXISTS (
       SELECT 1
        FROM person_affiliations affiliation
        WHERE affiliation.person_slug = seed.person_slug
          AND (affiliation.valid_from IS NULL OR affiliation.valid_from <= CURRENT_DATE)
          AND (affiliation.valid_until IS NULL OR affiliation.valid_until >= CURRENT_DATE)
 );
