UPDATE catalog_videos
   SET source_language = 'ary', updated_at = CURRENT_TIMESTAMP
 WHERE source_language = 'ar';
