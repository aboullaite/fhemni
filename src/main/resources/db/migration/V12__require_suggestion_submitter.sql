-- Legacy suggestions predate authenticated submissions. Attribute them to the
-- oldest administrator before enforcing ownership for every future row.
UPDATE video_suggestions
   SET suggested_by_user_id = (
       SELECT id
         FROM app_users
        WHERE role = 'ADMIN'
        ORDER BY created_at
        FETCH FIRST 1 ROW ONLY
   )
 WHERE suggested_by_user_id IS NULL;

ALTER TABLE video_suggestions
    ALTER COLUMN suggested_by_user_id SET NOT NULL;
