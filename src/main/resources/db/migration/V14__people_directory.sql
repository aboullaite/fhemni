-- Public guest/party directory, curated from verified Moroccan press sources.
-- Party names follow maroc.ma and party statutes; colors are indicative UI
-- tints, not official artwork. Person affiliations were checked against MAP,
-- Le360, Médias24, TelQuel, Yabiladi and official sites in 2026 (see the
-- source notes below). Changes to this reference data go through reviewed
-- migrations, like code.
CREATE TABLE political_parties (
    code VARCHAR(10) PRIMARY KEY,
    name_fr VARCHAR(200) NOT NULL,
    name_ar VARCHAR(200) NOT NULL,
    color VARCHAR(7) NOT NULL,
    sort_order INTEGER NOT NULL
);

CREATE TABLE directory_persons (
    slug VARCHAR(160) PRIMARY KEY,
    display_name_fr VARCHAR(200) NOT NULL,
    display_name_ar VARCHAR(200) NOT NULL,
    party_code VARCHAR(10) NOT NULL REFERENCES political_parties (code)
);

CREATE TABLE person_aliases (
    person_slug VARCHAR(160) NOT NULL REFERENCES directory_persons (slug) ON DELETE CASCADE,
    alias VARCHAR(200) NOT NULL,
    PRIMARY KEY (person_slug, alias)
);
CREATE INDEX person_aliases_alias_idx ON person_aliases (alias);

INSERT INTO political_parties (code, name_fr, name_ar, color, sort_order) VALUES
    ('RNI', 'Rassemblement National des Indépendants', 'التجمع الوطني للأحرار', '#1B7FC1', 1),
    ('PAM', 'Parti Authenticité et Modernité', 'حزب الأصالة والمعاصرة', '#0A54A3', 2),
    ('PI', 'Parti de l''Istiqlal', 'حزب الاستقلال', '#C1272D', 3),
    ('PJD', 'Parti de la Justice et du Développement', 'حزب العدالة والتنمية', '#E8A90C', 4),
    ('USFP', 'Union Socialiste des Forces Populaires', 'الاتحاد الاشتراكي للقوات الشعبية', '#E30613', 5),
    ('PPS', 'Parti du Progrès et du Socialisme', 'حزب التقدم والاشتراكية', '#B71C1C', 6),
    ('MP', 'Mouvement Populaire', 'الحركة الشعبية', '#2E7D32', 7),
    ('FGD', 'Fédération de la Gauche Démocratique', 'فيدرالية اليسار الديمقراطي', '#6A1B9A', 8),
    ('UC', 'Union Constitutionnelle', 'الاتحاد الدستوري', '#00897B', 9),
    ('FFD', 'Front des Forces Démocratiques', 'جبهة القوى الديمقراطية', '#7CB342', 10),
    ('MDS', 'Mouvement Démocratique et Social', 'الحركة الديمقراطية والاجتماعية', '#43A047', 11),
    ('PSU', 'Parti Socialiste Unifié', 'الحزب الاشتراكي الموحد', '#EF6C00', 12),
    ('IND', 'Indépendant', 'مستقل', '#6B7280', 13),
    ('UNKNOWN', 'Affiliation non renseignée', 'الانتماء غير معروف', '#9E9E9E', 14);

-- RNI — Mohamed Chouki elected president on 2026-02-07 in El Jadida
-- (MAP, Médias24, TelQuel, Le360). Aziz Akhannouch led the RNI 2016–2026
-- (MAP, 2026-01-12). Lahcen Essaadi, RNI secretary of state for crafts
-- (MAP, 2024-10-23; Hespress, 2026-06-05). Rachid Talbi Alami, RNI
-- parliament president running in Tétouan (Yabiladi, 2026-08-28).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('mohamed-chouki', 'Mohamed Chouki', 'محمد شوقي', 'RNI'),
    ('aziz-akhannouch', 'Aziz Akhannouch', 'عزيز أخنوش', 'RNI'),
    ('lahcen-essaadi', 'Lahcen Essaadi', 'لحسن السعدي', 'RNI'),
    ('rachid-talbi-alami', 'Rachid Talbi Alami', 'راشيد الطالبي العلمي', 'RNI');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('mohamed-chouki', 'Mohamed Chaouki'),
    ('mohamed-chouki', 'Mohammed Chouki'),
    ('mohamed-chouki', 'محمد شوقي'),
    ('aziz-akhannouch', 'عزيز أخنوش'),
    ('lahcen-essaadi', 'Lahcen Saadi'),
    ('lahcen-essaadi', 'Lahcen Saâdi'),
    ('lahcen-essaadi', 'Lahcen Es-Saadi'),
    ('lahcen-essaadi', 'لحسن السعدي'),
    ('rachid-talbi-alami', 'Rachid Talbi El Alami'),
    ('rachid-talbi-alami', 'راشيد الطالبي العلمي'),
    ('rachid-talbi-alami', 'رشيد الطالبي العلمي');

-- PAM — Abdellatif Ouahbi, former SG, minister of Justice running in
-- Taroudant Nord under PAM colors (Le360, 2026-08-27); Fatima Ezzahra El
-- Mansouri, collective leadership (Le360, 2024-02-12); Hicham El Mhajri back
-- in the political bureau (Le360, 2025-05-31; Médias24, 2026-07-14);
-- Mehdi Bensaïd, minister and head of list (Hespress, 2026-08-31).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('abdellatif-ouahbi', 'Abdellatif Ouahbi', 'عبد اللطيف وهبي', 'PAM'),
    ('fatima-ezzahra-el-mansouri', 'Fatima Ezzahra El Mansouri', 'فاطمة الزهراء المنصوري', 'PAM'),
    ('hicham-el-mhajri', 'Hicham El Mhajri', 'هشام المهاجري', 'PAM'),
    ('mehdi-bensaid', 'Mehdi Bensaïd', 'محمد مهدي بنسعيد', 'PAM');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('abdellatif-ouahbi', 'Abdellatif El Ouahbi'),
    ('abdellatif-ouahbi', 'عبد اللطيف وهبي'),
    ('fatima-ezzahra-el-mansouri', 'Fatima Zahra El Mansouri'),
    ('fatima-ezzahra-el-mansouri', 'فاطمة الزهراء المنصوري'),
    ('hicham-el-mhajri', 'Hicham El Mahjri'),
    ('hicham-el-mhajri', 'Hicham El Mahjiri'),
    ('hicham-el-mhajri', 'هشام المهاجري'),
    ('mehdi-bensaid', 'Mehdi Bensaid'),
    ('mehdi-bensaid', 'Mohamed Mehdi Bensaid'),
    ('mehdi-bensaid', 'Mohammed Mehdi Bensaid'),
    ('mehdi-bensaid', 'محمد مهدي بنسعيد');

-- PI — Nizar Baraka, secretary-general (Le360, 2026-06-06; Le Matin, 2026-07-13).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('nizar-baraka', 'Nizar Baraka', 'نزار بركة', 'PI');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('nizar-baraka', 'نزار بركة');

-- PJD — Abdelilah Benkirane re-elected at the 9th congress (TelQuel,
-- 2025-04-29; Médias24, 2026-05-31); Driss El Azami, president of the
-- national council and head of list Skhirat-Témara (Le360, 2026-04-22);
-- Mustapha El Khalfi, head of list Rabat-Océan (Hespress, 2026-08-31);
-- Abdallah Bouanou, parliamentary group president (Médias24, 2026-05-31);
-- Amina Maâ El Ainin, party figure (LeSiteinfo, 2017/2019).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('abdelilah-benkirane', 'Abdelilah Benkirane', 'عبد الإله بنكيران', 'PJD'),
    ('driss-el-azami', 'Driss El Azami', 'إدريس الأزمي', 'PJD'),
    ('mustapha-el-khalfi', 'Mustapha El Khalfi', 'مصطفى الخلفي', 'PJD'),
    ('abdallah-bouanou', 'Abdallah Bouanou', 'عبد الله بوانو', 'PJD'),
    ('amina-maa-el-ainin', 'Amina Maâ El Ainin', 'أمينة ماء العينين', 'PJD');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('abdelilah-benkirane', 'Abdelillah Benkirane'),
    ('abdelilah-benkirane', 'عبد الإله بنكيران'),
    ('driss-el-azami', 'Driss Azami'),
    ('driss-el-azami', 'Idriss El Azami'),
    ('driss-el-azami', 'Idriss El Azami El Idrissi'),
    ('driss-el-azami', 'Driss El Azami El Idrissi'),
    ('driss-el-azami', 'إدريس الأزمي'),
    ('driss-el-azami', 'إدريس الأزمي الإدريسي'),
    ('mustapha-el-khalfi', 'Mustapha ElKhalfi'),
    ('mustapha-el-khalfi', 'مصطفى الخلفي'),
    ('abdallah-bouanou', 'Abdellah Bouanou'),
    ('abdallah-bouanou', 'Abdellah Bwanou'),
    ('abdallah-bouanou', 'عبد الله بوانو'),
    ('abdallah-bouanou', 'عبد الله بووانو'),
    ('amina-maa-el-ainin', 'Amina Mae El Ainin'),
    ('amina-maa-el-ainin', 'Amina Maa El Ainine'),
    ('amina-maa-el-ainin', 'أمينة ماء العينين');

-- USFP — Driss Lachgar, first secretary re-elected for a 4th term
-- (Challenge, 2025-10-18; LeBrief and Le Matin, 2026-08).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('driss-lachgar', 'Driss Lachgar', 'إدريس لشكر', 'USFP');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('driss-lachgar', 'Driss Lachguar'),
    ('driss-lachgar', 'إدريس لشكر');

-- PPS — Nabil Benabdallah, secretary-general (Médias24 and Le360, 2026-06-12/13).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('nabil-benabdallah', 'Nabil Benabdallah', 'نبيل بنعبد الله', 'PPS');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('nabil-benabdallah', 'Mohamed Nabil Benabdallah'),
    ('nabil-benabdallah', 'Mohammed Nabil Benabdallah'),
    ('nabil-benabdallah', 'نبيل بنعبد الله'),
    ('nabil-benabdallah', 'محمد نبيل بنعبد الله'),
    ('nabil-benabdallah', 'نبيل بن عبد الله'),
    ('nabil-benabdallah', 'محمد نبيل بن عبد الله');

-- MP — Mohamed Ouzzine, secretary-general since 2022-11-26 (Le360);
-- Mohand Laenser, historic figure and 2026 candidate in Boulemane
-- (Médias24, 2026-08-20).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('mohamed-ouzzine', 'Mohamed Ouzzine', 'محمد أوزين', 'MP'),
    ('mohand-laenser', 'Mohand Laenser', 'محند العنصر', 'MP');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('mohamed-ouzzine', 'Mohammed Ouzzine'),
    ('mohamed-ouzzine', 'محمد أوزين'),
    ('mohand-laenser', 'محند العنصر');

-- PSU — Nabila Mounib, invested head of list in Anfa by the PSU political
-- bureau (Yabiladi, 2026-07-07); Jamal El Asri, who succeeded her as
-- secretary-general (Le360, 2023-11-06).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('nabila-mounib', 'Nabila Mounib', 'نبيلة منيب', 'PSU'),
    ('jamal-el-asri', 'Jamal El Asri', 'جمال العسري', 'PSU');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('nabila-mounib', 'نبيلة منيب'),
    ('jamal-el-asri', 'Djamel El Asri'),
    ('jamal-el-asri', 'جمال العسري');

-- UC — Mohamed Joudar, secretary-general since 2022-10-01, succeeding Mohamed
-- Sajid (Le360, 2022-10-03; LeSiteinfo, 2022-11-14); El Habib Dekkak,
-- political bureau member (MMNews, 2026-09-06).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('mohamed-joudar', 'Mohamed Joudar', 'محمد جودار', 'UC'),
    ('el-habib-dekkak', 'El Habib Dekkak', 'الحبيب الدقاق', 'UC');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('mohamed-joudar', 'Mohammed Joudar'),
    ('mohamed-joudar', 'محمد جودار'),
    ('el-habib-dekkak', 'El Habib Eddaqqaq'),
    ('el-habib-dekkak', 'El Habib Eddaqaq'),
    ('el-habib-dekkak', 'الحبيب الدقاق');

-- FGD — Abdeslam El Aziz, secretary-general since 2023-01-08 (Hespress and
-- TelQuel with MAP, 2023-01-09; Médias24, 2026-07-04).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('abdeslam-el-aziz', 'Abdeslam El Aziz', 'عبد السلام العزيز', 'FGD');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('abdeslam-el-aziz', 'Abdessalam El Aziz'),
    ('abdeslam-el-aziz', 'Abdeslam Elaziz'),
    ('abdeslam-el-aziz', 'عبد السلام العزيز');

-- FFD — Mustapha Benali, secretary-general (Le360, 2026-08-15; Médias24,
-- 2026-07-16; L'Economiste, 2022-03-28).
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('mustapha-benali', 'Mustapha Benali', 'مصطفى بنعلي', 'FFD');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('mustapha-benali', 'Moustapha Benali'),
    ('mustapha-benali', 'مصطفى بنعلي'),
    ('mustapha-benali', 'المصطفى بنعلي'),
    ('mustapha-benali', 'مصطفى بن علي');

-- Show journalists stay explicitly unaffiliated: only the verified French
-- spelling is curated, never a party. Sanaa Rahimi and Jamaa present
-- "ساعة الصراحة" on 2M (2m.ma, 2026-03-25); Abir Elmallouki
-- (@abir_elmallouki) presents "للحديث بقية" on Al Aoula. Jamaa's French
-- family-name spelling follows viewer usage ("Jamaa Goulahcen", Hespress
-- comments, 2013) — to be confirmed.
INSERT INTO directory_persons (slug, display_name_fr, display_name_ar, party_code) VALUES
    ('sanaa-rahimi', 'Sanaa Rahimi', 'سناء رحيمي', 'UNKNOWN'),
    ('abir-elmallouki', 'Abir Elmallouki', 'عبير الملوكي', 'UNKNOWN'),
    ('jamaa-goulahcen', 'Jamaa Goulahcen', 'جامع كولحسن', 'UNKNOWN');
INSERT INTO person_aliases (person_slug, alias) VALUES
    ('sanaa-rahimi', 'Sanae Rahimi'),
    ('sanaa-rahimi', 'سناء رحيمي'),
    ('sanaa-rahimi', 'سناء'),
    ('abir-elmallouki', 'Abir El Mellouki'),
    ('abir-elmallouki', 'عبير الملوكي'),
    ('abir-elmallouki', 'عبير ملوكي'),
    ('jamaa-goulahcen', 'Jamaa Koulhcen'),
    ('jamaa-goulahcen', 'جامع كولحسن'),
    ('jamaa-goulahcen', 'جامع غولحسن');
