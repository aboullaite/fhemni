package dev.maboullaite.fhemni.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                        SELECT slug, display_name_fr, display_name_ar, party_code
                          FROM directory_persons
                         ORDER BY slug
                        """)
                .query((resultSet, rowNumber) -> new PersonRow(
                        resultSet.getString("slug"),
                        resultSet.getString("display_name_fr"),
                        resultSet.getString("display_name_ar"),
                        resultSet.getString("party_code")))
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
        return persons.values().stream()
                .map(row -> new CuratedPerson(
                        row.slug(), row.displayNameFr(), row.displayNameAr(), row.partyCode(),
                        List.copyOf(row.aliases())))
                .toList();
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
        private final String partyCode;
        private final List<String> aliases = new ArrayList<>();

        private PersonRow(String slug, String displayNameFr, String displayNameAr, String partyCode) {
            this.slug = slug;
            this.displayNameFr = displayNameFr;
            this.displayNameAr = displayNameAr;
            this.partyCode = partyCode;
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

        private String partyCode() {
            return partyCode;
        }

        private List<String> aliases() {
            return aliases;
        }
    }

    private record AliasRow(String personSlug, String alias) {
    }
}
