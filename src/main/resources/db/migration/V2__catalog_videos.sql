CREATE TABLE catalog_videos (
    id UUID PRIMARY KEY,
    youtube_video_id VARCHAR(11) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    canonical_url VARCHAR(2048) NOT NULL,
    title VARCHAR(500) NOT NULL,
    author_name VARCHAR(200) NOT NULL,
    thumbnail_url VARCHAR(2048) NOT NULL,
    show_name VARCHAR(200),
    published_on DATE,
    source_language VARCHAR(12) NOT NULL,
    category VARCHAR(80) NOT NULL,
    short_summary TEXT,
    status VARCHAR(30) NOT NULL,
    listed BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT catalog_videos_youtube_id_unique UNIQUE (youtube_video_id),
    CONSTRAINT catalog_videos_slug_unique UNIQUE (slug),
    CONSTRAINT catalog_videos_status_check CHECK (status IN ('CATALOGUED', 'PUBLISHED'))
);

CREATE INDEX catalog_videos_publication_idx
    ON catalog_videos (listed, published_on, created_at);
CREATE INDEX catalog_videos_language_idx
    ON catalog_videos (source_language);

INSERT INTO catalog_videos (
    id, youtube_video_id, slug, canonical_url, title, author_name,
    thumbnail_url, show_name, published_on, source_language, category,
    short_summary, status, listed, created_at, updated_at
) VALUES (
    'a3090250-cdfa-48c6-92dc-e7db05a44fc9',
    '14IF32HrTBs',
    'episode-14IF32HrTBs',
    'https://www.youtube.com/watch?v=14IF32HrTBs',
    'ساعة الصراحة : الأربعاء 06 ماي 2026',
    '2MTV',
    'https://i.ytimg.com/vi/14IF32HrTBs/hqdefault.jpg',
    'ساعة الصراحة',
    DATE '2026-05-06',
    'ar',
    'public_affairs',
    NULL,
    'CATALOGUED',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
), (
    'f5bd1f95-c65a-4621-b678-0dd2744b4673',
    'quf5ok_tkB4',
    'episode-quf5ok_tkB4',
    'https://www.youtube.com/watch?v=quf5ok_tkB4',
    'ساعة الصراحة : الأربعاء 25 مارس 2026',
    '2MTV',
    'https://i.ytimg.com/vi/quf5ok_tkB4/hqdefault.jpg',
    'ساعة الصراحة',
    DATE '2026-03-25',
    'ar',
    'public_affairs',
    NULL,
    'CATALOGUED',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
), (
    'f402945b-e541-4524-9c55-46c85a9ca34d',
    'n5B3boj2MFM',
    'episode-n5B3boj2MFM',
    'https://www.youtube.com/watch?v=n5B3boj2MFM',
    'ساعة الصراحة مع إدريس الأزمي حول تشريعيات 2026 وحصيلة حزب العدالة والتنمية',
    '2MTV',
    'https://i.ytimg.com/vi/n5B3boj2MFM/hqdefault.jpg',
    'ساعة الصراحة',
    NULL,
    'ar',
    'public_affairs',
    NULL,
    'CATALOGUED',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
