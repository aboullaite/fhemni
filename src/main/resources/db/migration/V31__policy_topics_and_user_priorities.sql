-- A small, editorially controlled taxonomy lets users follow the public-policy
-- subjects they care about without asking an AI model to classify every page view.
CREATE TABLE policy_topics (
    code VARCHAR(48) PRIMARY KEY,
    parent_code VARCHAR(48) REFERENCES policy_topics (code),
    label_ar VARCHAR(120) NOT NULL,
    label_fr VARCHAR(120) NOT NULL,
    label_en VARCHAR(120) NOT NULL,
    sort_order INTEGER NOT NULL,
    selectable BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (parent_code, sort_order)
);

CREATE TABLE promise_policy_topics (
    promise_id UUID NOT NULL REFERENCES party_promises (id) ON DELETE CASCADE,
    topic_code VARCHAR(48) NOT NULL REFERENCES policy_topics (code),
    relationship VARCHAR(16) NOT NULL CHECK (relationship IN ('DIRECT', 'RELATED')),
    mapping_source VARCHAR(16) NOT NULL CHECK (mapping_source IN ('RULE', 'EDITORIAL')),
    mapping_version VARCHAR(64) NOT NULL,
    mapped_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (promise_id, topic_code)
);
CREATE INDEX promise_policy_topics_topic_idx ON promise_policy_topics (topic_code, promise_id);

CREATE TABLE user_policy_topic_preferences (
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    topic_code VARCHAR(48) NOT NULL REFERENCES policy_topics (code),
    position INTEGER NOT NULL CHECK (position BETWEEN 1 AND 3),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, topic_code),
    UNIQUE (user_id, position)
);

INSERT INTO policy_topics (
    code, parent_code, label_ar, label_fr, label_en, sort_order, selectable, active
) VALUES
    ('EDUCATION', NULL, 'التعليم', 'Éducation', 'Education', 1, TRUE, TRUE),
    ('EMPLOYMENT', NULL, 'الشغل', 'Emploi', 'Employment', 2, TRUE, TRUE),
    ('HEALTH', NULL, 'الصحة', 'Santé', 'Healthcare', 3, TRUE, TRUE),
    ('PURCHASING_POWER', NULL, 'القدرة الشرائية', 'Pouvoir d''achat', 'Purchasing power', 4, TRUE, TRUE),
    ('HOUSING', NULL, 'السكن', 'Logement', 'Housing', 5, TRUE, TRUE),
    ('SOCIAL_PROTECTION', NULL, 'الحماية الاجتماعية', 'Protection sociale', 'Social protection', 6, TRUE, TRUE),
    ('JUSTICE_SECURITY', NULL, 'الأمن والعدالة', 'Sécurité et justice', 'Security and justice', 7, TRUE, TRUE),
    ('WATER_ENERGY_ENVIRONMENT', NULL, 'الماء والطاقة والبيئة', 'Eau, énergie et environnement', 'Water, energy and environment', 8, TRUE, TRUE),
    ('GOVERNANCE', NULL, 'الحكامة ومحاربة الفساد', 'Gouvernance et lutte contre la corruption', 'Governance and anti-corruption', 9, TRUE, TRUE),
    ('REGIONAL_DEVELOPMENT', NULL, 'التنمية المجالية والقروية', 'Développement territorial et rural', 'Regional and rural development', 10, TRUE, TRUE),
    ('OTHER', NULL, 'مواضيع أخرى', 'Autres sujets', 'Other subjects', 99, FALSE, TRUE),

    ('EDUCATION_PRESCHOOL', 'EDUCATION', 'التعليم الأولي', 'Préscolaire', 'Preschool', 1, FALSE, TRUE),
    ('EDUCATION_SCHOOLS', 'EDUCATION', 'المدرسة والبنية التحتية', 'Écoles et infrastructures', 'Schools and infrastructure', 2, FALSE, TRUE),
    ('EDUCATION_DROPOUT', 'EDUCATION', 'محاربة الهدر المدرسي', 'Décrochage scolaire', 'School dropout', 3, FALSE, TRUE),
    ('EDUCATION_HIGHER', 'EDUCATION', 'التعليم العالي والجامعات', 'Enseignement supérieur', 'Higher education', 4, FALSE, TRUE),
    ('EDUCATION_RESEARCH', 'EDUCATION', 'البحث العلمي', 'Recherche scientifique', 'Scientific research', 5, FALSE, TRUE),
    ('EDUCATION_TEACHERS', 'EDUCATION', 'الأساتذة والتوظيف', 'Enseignants et recrutement', 'Teachers and recruitment', 6, FALSE, TRUE),

    ('EMPLOYMENT_JOB_CREATION', 'EMPLOYMENT', 'خلق مناصب الشغل', 'Création d''emplois', 'Job creation', 1, FALSE, TRUE),
    ('EMPLOYMENT_FIRST_JOB', 'EMPLOYMENT', 'أول فرصة وإدماج الشباب', 'Premier emploi et insertion', 'First job and youth inclusion', 2, FALSE, TRUE),
    ('EMPLOYMENT_UNEMPLOYMENT', 'EMPLOYMENT', 'تقليص البطالة', 'Réduction du chômage', 'Reducing unemployment', 3, FALSE, TRUE),
    ('EMPLOYMENT_SELF_EMPLOYMENT', 'EMPLOYMENT', 'المقاولة والعمل الحر', 'Entrepreneuriat et travail indépendant', 'Entrepreneurship and self-employment', 4, FALSE, TRUE),
    ('EMPLOYMENT_WORKER_RIGHTS', 'EMPLOYMENT', 'الأجور وحقوق الأجراء', 'Salaires et droits du travail', 'Pay and worker rights', 5, FALSE, TRUE),
    ('EMPLOYMENT_WOMEN', 'EMPLOYMENT', 'إدماج النساء فسوق الشغل', 'Participation économique des femmes', 'Women in the workforce', 6, FALSE, TRUE);
