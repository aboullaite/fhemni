-- Additional reviewed programme subjects. Existing party mappings remain unchanged.
INSERT INTO policy_topics (code,parent_code,label_ar,label_fr,label_en,sort_order,selectable,active) VALUES
('AGRICULTURE_FISHERIES', NULL, 'الفلاحة والصيد والأمن الغذائي', 'Agriculture, pêche et sécurité alimentaire', 'Agriculture, fisheries and food security', 101, TRUE, TRUE),
('INDUSTRY_TRADE', NULL, 'الصناعة والتجارة', 'Industrie et commerce', 'Industry and trade', 102, TRUE, TRUE),
('INVESTMENT_ENTERPRISE', NULL, 'الاستثمار والمقاولات', 'Investissement et entreprises', 'Investment and enterprise', 103, TRUE, TRUE),
('PUBLIC_FINANCE', NULL, 'المالية العمومية والضرائب', 'Finances publiques et fiscalité', 'Public finance and taxation', 104, TRUE, TRUE),
('DIGITAL_TRANSFORMATION', NULL, 'التحول الرقمي والاتصالات', 'Transformation numérique et télécommunications', 'Digital transformation and telecommunications', 105, TRUE, TRUE),
('WATER', NULL, 'الماء والتطهير', 'Eau et assainissement', 'Water and sanitation', 106, TRUE, TRUE),
('ENERGY', NULL, 'الطاقة', 'Énergie', 'Energy', 107, TRUE, TRUE),
('ENVIRONMENT_CLIMATE', NULL, 'البيئة والمناخ ومخاطر الكوارث', 'Environnement, climat et risques de catastrophe', 'Environment, climate and disaster risks', 108, TRUE, TRUE),
('TRANSPORT_INFRASTRUCTURE', NULL, 'النقل والبنيات التحتية', 'Transports et infrastructures', 'Transport and infrastructure', 109, TRUE, TRUE),
('TOURISM_CRAFTS', NULL, 'السياحة والصناعة التقليدية', 'Tourisme et artisanat', 'Tourism and crafts', 110, TRUE, TRUE),
('CULTURE_MEDIA', NULL, 'الثقافة والإعلام واللغات', 'Culture, médias et langues', 'Culture, media and languages', 111, TRUE, TRUE),
('YOUTH_SPORT', NULL, 'الشباب والرياضة', 'Jeunesse et sport', 'Youth and sport', 112, TRUE, TRUE),
('FAMILY_EQUALITY', NULL, 'الأسرة والطفولة والمساواة', 'Famille, enfance et égalité', 'Family, children and equality', 113, TRUE, TRUE),
('DISABILITY_INCLUSION', NULL, 'الإعاقة والولوجيات', 'Handicap et accessibilité', 'Disability and accessibility', 114, TRUE, TRUE),
('DIASPORA', NULL, 'مغاربة العالم', 'Marocains du monde', 'Moroccans abroad', 115, TRUE, TRUE),
('FOREIGN_AFFAIRS', NULL, 'العلاقات والتعاون الدولي', 'Relations et coopération internationales', 'Foreign affairs and international cooperation', 116, TRUE, TRUE),
('RESEARCH_INNOVATION', NULL, 'البحث العلمي والابتكار', 'Recherche scientifique et innovation', 'Research and innovation', 117, TRUE, TRUE),
('SOCIAL_ECONOMY', NULL, 'الاقتصاد الاجتماعي والتعاونيات', 'Économie sociale et coopératives', 'Social economy and cooperatives', 118, TRUE, TRUE),
('MACROECONOMY', NULL, 'النمو والتوازنات الاقتصادية', 'Croissance et équilibres économiques', 'Growth and macroeconomic stability', 119, TRUE, TRUE)
ON CONFLICT DO NOTHING;
