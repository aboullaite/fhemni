package dev.maboullaite.fhemni.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CatalogVideoRepository {

    private static final String COLUMNS = """
            id, youtube_video_id, slug, canonical_url, title, author_name,
            thumbnail_url, show_name, published_on, source_language, category,
            short_summary, status, published_analysis_id, listed, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public CatalogVideoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CatalogPage findPublic(String query, String language, int page, int size) {
        List<Object> parameters = new ArrayList<>();
        String where = publicWhere(query, language, parameters);

        JdbcClient.StatementSpec count = jdbc.sql("SELECT COUNT(*) FROM catalog_videos " + where);
        count = bind(count, parameters);
        long total = count.query(Long.class).single();

        JdbcClient.StatementSpec select = jdbc.sql("""
                SELECT %s
                  FROM catalog_videos
                %s
                 ORDER BY CASE WHEN published_on IS NULL THEN 1 ELSE 0 END,
                          published_on DESC, created_at DESC
                 LIMIT ? OFFSET ?
                """.formatted(COLUMNS, where));
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add(page * size);
        select = bind(select, pageParameters);
        List<CatalogVideo> items = select.query(this::map).list();
        return new CatalogPage(items, page, size, total);
    }

    public Optional<CatalogVideo> findPublicBySlug(String slug) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM catalog_videos WHERE slug = :slug AND listed = TRUE")
                .param("slug", slug)
                .query(this::map)
                .optional();
    }

    public Optional<CatalogVideo> findByYouTubeId(String youtubeVideoId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM catalog_videos WHERE youtube_video_id = :youtubeVideoId")
                .param("youtubeVideoId", youtubeVideoId)
                .query(this::map)
                .optional();
    }

    public List<CatalogVideo> findAll(int limit) {
        return jdbc.sql("""
                        SELECT %s
                          FROM catalog_videos
                         ORDER BY updated_at DESC
                         LIMIT :limit
                        """.formatted(COLUMNS))
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public List<CatalogVideo> findMissingPublishedOn(int limit) {
        return jdbc.sql("""
                        SELECT %s
                          FROM catalog_videos
                         WHERE listed = TRUE AND published_on IS NULL
                         ORDER BY created_at ASC
                         LIMIT :limit
                        """.formatted(COLUMNS))
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    public void updatePublishedOn(UUID id, LocalDate publishedOn) {
        jdbc.sql("""
                        UPDATE catalog_videos
                           SET published_on = :publishedOn, updated_at = :updatedAt
                         WHERE id = :id AND published_on IS NULL
                        """)
                .param("publishedOn", publishedOn)
                .param("updatedAt", utc(Instant.now()))
                .param("id", id)
                .update();
    }

    @Transactional
    public SaveResult saveImported(ImportedCatalogVideo imported) {
        Optional<CatalogVideo> existing = findByYouTubeId(imported.youtubeVideoId());
        Instant now = Instant.now();
        if (existing.isPresent()) {
            CatalogVideo video = existing.get();
            CatalogVideo updated = new CatalogVideo(
                    video.id(),
                    video.youtubeVideoId(),
                    video.slug(),
                    imported.canonicalUrl(),
                    imported.title(),
                    imported.authorName(),
                    imported.thumbnailUrl(),
                    imported.showName() == null ? video.showName() : imported.showName(),
                    imported.publishedOn() == null ? video.publishedOn() : imported.publishedOn(),
                    imported.sourceLanguage(),
                    video.category(),
                    video.shortSummary(),
                    video.status(),
                    video.publishedAnalysisId(),
                    true,
                    video.createdAt(),
                    now);
            jdbc.sql("""
                            UPDATE catalog_videos
                               SET canonical_url = :canonicalUrl,
                                   title = :title,
                                   author_name = :authorName,
                                   thumbnail_url = :thumbnailUrl,
                                   show_name = COALESCE(:showName, show_name),
                                   published_on = COALESCE(:publishedOn, published_on),
                                   source_language = :sourceLanguage,
                                   listed = TRUE,
                                   updated_at = :updatedAt
                             WHERE id = :id
                            """)
                    .param("canonicalUrl", imported.canonicalUrl())
                    .param("title", imported.title())
                    .param("authorName", imported.authorName())
                    .param("thumbnailUrl", imported.thumbnailUrl())
                    .param("showName", imported.showName(), Types.VARCHAR)
                    .param("publishedOn", imported.publishedOn(), Types.DATE)
                    .param("sourceLanguage", imported.sourceLanguage())
                    .param("updatedAt", utc(now))
                    .param("id", video.id())
                    .update();
            return new SaveResult(false, updated);
        }

        CatalogVideo video = new CatalogVideo(
                UUID.randomUUID(), imported.youtubeVideoId(), "episode-" + imported.youtubeVideoId(),
                imported.canonicalUrl(), imported.title(), imported.authorName(), imported.thumbnailUrl(),
                imported.showName(), imported.publishedOn(), imported.sourceLanguage(), "public_affairs", null,
                CatalogStatus.CATALOGUED, null, true, now, now);
        jdbc.sql("""
                        INSERT INTO catalog_videos (
                            id, youtube_video_id, slug, canonical_url, title, author_name,
                            thumbnail_url, show_name, published_on, source_language, category,
                            short_summary, status, listed, created_at, updated_at
                        ) VALUES (
                            :id, :youtubeVideoId, :slug, :canonicalUrl, :title, :authorName,
                            :thumbnailUrl, :showName, :publishedOn, :sourceLanguage, :category,
                            :shortSummary, :status, :listed, :createdAt, :updatedAt
                        )
                        """)
                .param("id", video.id())
                .param("youtubeVideoId", video.youtubeVideoId())
                .param("slug", video.slug())
                .param("canonicalUrl", video.canonicalUrl())
                .param("title", video.title())
                .param("authorName", video.authorName())
                .param("thumbnailUrl", video.thumbnailUrl())
                .param("showName", video.showName(), Types.VARCHAR)
                .param("publishedOn", video.publishedOn(), Types.DATE)
                .param("sourceLanguage", video.sourceLanguage())
                .param("category", video.category())
                .param("shortSummary", video.shortSummary(), Types.LONGVARCHAR)
                .param("status", video.status().name())
                .param("listed", video.listed())
                .param("createdAt", utc(video.createdAt()))
                .param("updatedAt", utc(video.updatedAt()))
                .update();
        return new SaveResult(true, video);
    }

    private String publicWhere(String query, String language, List<Object> parameters) {
        StringBuilder where = new StringBuilder("WHERE listed = TRUE");
        if (query != null && !query.isBlank()) {
            where.append(" AND (LOWER(title) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(author_name) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(show_name) LIKE ? ESCAPE '\\')");
            String like = "%" + escapeLikePattern(query.strip().toLowerCase(Locale.ROOT)) + "%";
            parameters.add(like);
            parameters.add(like);
            parameters.add(like);
        }
        if (language != null && !language.isBlank()) {
            where.append(" AND source_language = ?");
            parameters.add(language.strip().toLowerCase(Locale.ROOT));
        }
        return where.toString();
    }

    private String escapeLikePattern(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, List<Object> parameters) {
        JdbcClient.StatementSpec result = statement;
        for (Object parameter : parameters) {
            result = result.param(parameter);
        }
        return result;
    }

    private CatalogVideo map(ResultSet resultSet, int rowNumber) throws SQLException {
        LocalDate publishedOn = resultSet.getObject("published_on", LocalDate.class);
        return new CatalogVideo(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("youtube_video_id"),
                resultSet.getString("slug"),
                resultSet.getString("canonical_url"),
                resultSet.getString("title"),
                resultSet.getString("author_name"),
                resultSet.getString("thumbnail_url"),
                resultSet.getString("show_name"),
                publishedOn,
                resultSet.getString("source_language"),
                resultSet.getString("category"),
                resultSet.getString("short_summary"),
                CatalogStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("published_analysis_id", UUID.class),
                resultSet.getBoolean("listed"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    public record ImportedCatalogVideo(
            String youtubeVideoId,
            String canonicalUrl,
            String title,
            String authorName,
            String thumbnailUrl,
            String showName,
            LocalDate publishedOn,
            String sourceLanguage) {
    }

    public record SaveResult(boolean created, CatalogVideo video) {
    }
}
