package dev.maboullaite.fhemni.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;

import dev.maboullaite.fhemni.catalog.PersonDirectory.CuratedPerson;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the curated guest directory owned by Flyway.
 */
@Repository
public class DirectoryPersonRepository {

    private final JdbcClient jdbc;

    public DirectoryPersonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CuratedPerson> findAll() {
        Map<String, PersonRow> persons = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT slug, display_name_fr, display_name_ar
                          FROM directory_persons
                         ORDER BY slug
                        """)
                .query((resultSet, rowNumber) -> new PersonRow(
                        resultSet.getString("slug"),
                        resultSet.getString("display_name_fr"),
                        resultSet.getString("display_name_ar")))
                .list()
                .forEach(row -> persons.put(row.slug(), row));
        jdbc.sql("""
                        SELECT person_slug, alias
                          FROM person_aliases
                         ORDER BY person_slug, alias
                        """)
                .query((resultSet, rowNumber) -> new AliasRow(
                        resultSet.getString("person_slug"),
                        resultSet.getString("alias")))
                .list()
                .forEach(row -> {
                    PersonRow person = persons.get(row.personSlug());
                    if (person != null) {
                        person.aliases().add(row.alias());
                    }
                });
        jdbc.sql("""
                        SELECT id, person_slug, party_code, valid_from, valid_until,
                               source_url, source_label, verified_at
                          FROM person_affiliations
                         ORDER BY person_slug, valid_from NULLS FIRST, id
                        """)
                .query(this::mapAffiliation)
                .list()
                .forEach(affiliation -> {
                    PersonRow person = persons.get(affiliation.personSlug());
                    if (person != null) {
                        person.affiliations().add(affiliation);
                    }
                });
        return persons.values().stream()
                .map(row -> new CuratedPerson(
                        row.slug(), row.displayNameFr(), row.displayNameAr(),
                        currentParty(row.affiliations()), List.copyOf(row.aliases()),
                        List.copyOf(row.affiliations())))
                .toList();
    }

    public void insertPerson(String slug, String displayNameFr, String displayNameAr) {
        jdbc.sql("""
                        INSERT INTO directory_persons (slug, display_name_fr, display_name_ar)
                        VALUES (:slug, :displayNameFr, :displayNameAr)
                        """)
                .param("slug", slug)
                .param("displayNameFr", displayNameFr)
                .param("displayNameAr", displayNameAr)
                .update();
    }

    public boolean exists(String slug) {
        return jdbc.sql("SELECT COUNT(*) FROM directory_persons WHERE slug = :slug")
                .param("slug", slug)
                .query(Integer.class)
                .single() > 0;
    }

    public void addAlias(String personSlug, String alias) {
        jdbc.sql("""
                        INSERT INTO person_aliases (person_slug, alias)
                        SELECT :personSlug, :alias
                         WHERE NOT EXISTS (
                               SELECT 1 FROM person_aliases
                                WHERE person_slug = :personSlug AND alias = :alias
                         )
                        """)
                .param("personSlug", personSlug)
                .param("alias", alias)
                .update();
    }

    public void insertAffiliation(
            String personSlug,
            String partyCode,
            LocalDate validFrom,
            LocalDate validUntil,
            String sourceUrl,
            String sourceLabel,
            Instant verifiedAt) {
        jdbc.sql("""
                        INSERT INTO person_affiliations (
                            person_slug, party_code, valid_from, valid_until,
                            source_url, source_label, verified_at, created_at
                        ) VALUES (
                            :personSlug, :partyCode, :validFrom, :validUntil,
                            :sourceUrl, :sourceLabel, :verifiedAt, CURRENT_TIMESTAMP
                        )
                        """)
                .param("personSlug", personSlug)
                .param("partyCode", partyCode)
                .param("validFrom", validFrom)
                .param("validUntil", validUntil)
                .param("sourceUrl", sourceUrl)
                .param("sourceLabel", sourceLabel)
                .param("verifiedAt", verifiedAt)
                .update();
    }

    public void updateAffiliationParty(
            long id,
            String personSlug,
            String partyCode,
            Instant verifiedAt) {
        int updated = jdbc.sql("""
                        UPDATE person_affiliations
                           SET party_code = :partyCode,
                               verified_at = :verifiedAt
                         WHERE id = :id AND person_slug = :personSlug
                        """)
                .param("partyCode", partyCode)
                .param("verifiedAt", verifiedAt)
                .param("id", id)
                .param("personSlug", personSlug)
                .update();
        if (updated == 0) {
            throw new java.util.NoSuchElementException("This affiliation was not found.");
        }
    }

    public void deleteAffiliation(long id, String personSlug) {
        int deleted = jdbc.sql("""
                        DELETE FROM person_affiliations
                         WHERE id = :id AND person_slug = :personSlug
                        """)
                .param("id", id)
                .param("personSlug", personSlug)
                .update();
        if (deleted == 0) {
            throw new java.util.NoSuchElementException("This affiliation was not found.");
        }
    }

    public List<String> findHonorifics() {
        return jdbc.sql("""
                        SELECT prefix
                          FROM honorific_prefixes
                         ORDER BY prefix
                        """)
                .query((resultSet, rowNumber) -> resultSet.getString("prefix"))
                .list();
    }

    private static final class PersonRow {
        private final String slug;
        private final String displayNameFr;
        private final String displayNameAr;
        private final List<String> aliases = new ArrayList<>();
        private final List<PersonAffiliation> affiliations = new ArrayList<>();

        private PersonRow(String slug, String displayNameFr, String displayNameAr) {
            this.slug = slug;
            this.displayNameFr = displayNameFr;
            this.displayNameAr = displayNameAr;
        }

        private String slug() {
            return slug;
        }

        private String displayNameFr() {
            return displayNameFr;
        }

        private String displayNameAr() {
            return displayNameAr;
        }

        private List<String> aliases() {
            return aliases;
        }

        private List<PersonAffiliation> affiliations() {
            return affiliations;
        }
    }

    private record AliasRow(String personSlug, String alias) {
    }

    private PersonAffiliation mapAffiliation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PersonAffiliation(
                resultSet.getLong("id"),
                resultSet.getString("person_slug"),
                resultSet.getString("party_code"),
                resultSet.getObject("valid_from", LocalDate.class),
                resultSet.getObject("valid_until", LocalDate.class),
                resultSet.getString("source_url"),
                resultSet.getString("source_label"),
                resultSet.getObject("verified_at", Instant.class));
    }

    private String currentParty(List<PersonAffiliation> affiliations) {
        return affiliations.stream()
                .filter(affiliation -> affiliation.activeOn(LocalDate.now()))
                .reduce((first, second) -> second)
                .map(PersonAffiliation::partyCode)
                .orElse(PartyDirectory.UNKNOWN);
    }
}
