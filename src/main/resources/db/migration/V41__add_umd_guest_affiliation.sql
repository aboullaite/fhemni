-- UMD is listed in the official Maroc.ma party directory, which links to the
-- party's official Facebook page. That page uses the dolphin emblem; Hespress
-- also identifies the party's 2026 electoral symbol as the dolphin.
--
-- Two published reports name the same guest differently. Keep the spelling
-- used in the episode titles as the canonical identity and retain the
-- transcript variant as an alias so both appearances resolve to one profile.
INSERT INTO political_parties (
    code, name_fr, name_ar, color, sort_order, visible,
    symbol_label_fr, symbol_label_ar, symbol_asset, symbol_verified,
    catalogue_code
) VALUES (
    'UMD', 'Union Marocaine pour la Démocratie',
    'حزب الاتحاد المغربي للديمقراطية', '#1D4E75', 24, TRUE,
    'Dauphin', 'الدلفين', '/assets/parties/umd-display.png', TRUE,
    'UMD'
);

-- A full audit of every published participant role found eight more people
-- whose role states a party affiliation explicitly. Roles that only identify
-- a journalist, expert, civil-society representative or other participant
-- remain UNKNOWN: absence of a party in an episode is not evidence that a
-- person is politically independent.
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar)
SELECT seed.slug, seed.display_name_fr, seed.display_name_ar
  FROM (VALUES
        ('الهام-بلفحيلي', 'إلهام بلفحيلي', 'إلهام بلفحيلي'),
        ('سلمي-بنعزيز', 'سلمى بنعزيز', 'سلمى بنعزيز'),
        ('عمر-حياني', 'عمر حياني', 'عمر حياني'),
        ('فوزي-لقجع', 'فوزي لقجع', 'فوزي لقجع'),
        ('كنزه-الشرايبي', 'كنزة الشرايبي', 'كنزة الشرايبي'),
        ('محمد-الساسي', 'محمد الساسي', 'محمد الساسي'),
        ('هشام-عيرود', 'هشام عيرود', 'هشام عيرود'),
        ('المهدي-ياسيف', 'المهدي ياسيف', 'المهدي ياسيف'),
        ('محمد-زروق', 'محمد زروق', 'محمد زروق')
       ) AS seed(slug, display_name_fr, display_name_ar)
 WHERE NOT EXISTS (
       SELECT 1
         FROM directory_persons person
        WHERE person.slug = seed.slug
 );

INSERT INTO person_aliases (person_slug, alias) VALUES
    ('الهام-بلفحيلي', 'إلهام بلفحيلي'),
    ('الهام-بلفحيلي', 'إلهام بالحيلي');

INSERT INTO person_affiliations (
    person_slug, party_code, valid_from, valid_until,
    source_url, source_label, verified_at, created_at
)
SELECT seed.person_slug, seed.party_code, NULL, NULL,
       seed.source_url, seed.source_label, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM (VALUES
        ('الهام-بلفحيلي', 'UMD', 'https://www.youtube.com/watch?v=bt69DSSbomw',
         'الأمينة العامة لحزب الاتحاد المغربي للديمقراطية وناشطة حقوقية'),
        ('سلمي-بنعزيز', 'RNI', 'https://www.youtube.com/watch?v=jdXfK7zh7BI',
         'نائبة برلمانية وممثلة لحزب التجمع الوطني للأحرار'),
        ('عمر-حياني', 'FGD', 'https://www.youtube.com/watch?v=86UJO6RIC8k',
         'عضو المكتب السياسي لفيدرالية اليسار الديمقراطي وممثل تحالف اليسار'),
        ('فوزي-لقجع', 'PAM', 'https://www.youtube.com/watch?v=cfM0dKXkuhU',
         'وزير منتدب مكلف بالميزانية ومرشح باسم الأصالة والمعاصرة (ظهر فمقطع مصور)'),
        ('كنزه-الشرايبي', 'UC', 'https://www.youtube.com/watch?v=oGpXLOXTz2E',
         'محامية ورئيسة مقاطعة سيدي بليوط ووكيلة لائحة حزب الاتحاد الدستوري بدائرة الدار البيضاء أنفا'),
        ('محمد-الساسي', 'FGD', 'https://www.youtube.com/watch?v=bKDJhfrnWdo',
         'أستاذ القانون وباحث جامعي وفاعل سياسي يساري قيادي ففيدرالية اليسار الديمقراطي'),
        ('هشام-عيرود', 'PAM', 'https://www.youtube.com/watch?v=txj7Re9gJow',
         'عضو المكتب السياسي لحزب الأصالة والمعاصرة ومدير مركزي بوزارة إعداد التراب الوطني والتعمير والإسكان'),
        ('المهدي-ياسيف', 'RNI', 'https://www.youtube.com/watch?v=quf5ok_tkB4',
         'عضو الشبيبة التجمعية (ممثل الموقف المعارض في المناظرة)'),
        ('محمد-زروق', 'MP', 'https://www.youtube.com/watch?v=14IF32HrTBs',
         'الكاتب العام للشبيبة الحركية وممثل المعارضة في فقرة وجه لوجه')
       ) AS seed(person_slug, party_code, source_url, source_label)
 WHERE NOT EXISTS (
       SELECT 1
         FROM person_affiliations affiliation
        WHERE affiliation.person_slug = seed.person_slug
          AND (affiliation.valid_from IS NULL OR affiliation.valid_from <= CURRENT_DATE)
          AND (affiliation.valid_until IS NULL OR affiliation.valid_until >= CURRENT_DATE)
 );
