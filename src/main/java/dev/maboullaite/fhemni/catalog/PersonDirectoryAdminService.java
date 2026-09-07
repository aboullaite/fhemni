package dev.maboullaite.fhemni.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import dev.maboullaite.fhemni.catalog.PersonDirectory.CuratedPerson;
import dev.maboullaite.fhemni.catalog.PersonCatalogService.PersonSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Secured editorial workflow for guest identities and sourced affiliations. */
@Service
public class PersonDirectoryAdminService {

    private static final int MAX_ALIASES = 30;
    private static final String ADMIN_ASSIGNMENT = "Assigned by a Fhemni administrator";

    private final DirectoryPersonRepository people;
    private final PoliticalPartyRepository parties;
    private final PersonCatalogService catalogue;

    public PersonDirectoryAdminService(
            DirectoryPersonRepository people,
            PoliticalPartyRepository parties,
            PersonCatalogService catalogue) {
        this.people = people;
        this.parties = parties;
        this.catalogue = catalogue;
    }

    public DirectoryWorkbench workbench() {
        List<CuratedPerson> curated = people.findAll();
        List<PersonSummary> unmatched = catalogue.searchPeople(null, null).stream()
                .filter(person -> !person.curated())
                .toList();
        return new DirectoryWorkbench(parties.findAll(), curated, unmatched);
    }

    @Transactional
    public void curate(CuratePerson command) {
        String displayNameFr = required(command.displayNameFr(), "The guest name is required.", 200);
        String displayNameAr = required(command.displayNameAr(), "The Arabic guest name is required.", 200);
        String slug = PersonDirectory.slugify(
                command.slug() == null || command.slug().isBlank() ? displayNameFr : command.slug());
        if (people.exists(slug)) {
            throw new IllegalStateException("This guest is already in the directory.");
        }
        people.insertPerson(slug, displayNameFr, displayNameAr);
        aliases(command.aliases(), displayNameFr, displayNameAr)
                .forEach(alias -> people.addAlias(slug, alias));
        if (command.affiliation() != null
                && !PartyDirectory.UNKNOWN.equalsIgnoreCase(command.affiliation().partyCode())) {
            addAffiliation(slug, command.affiliation());
        }
        catalogue.invalidateCache();
    }

    @Transactional
    public void addAffiliation(String personSlug, AffiliationCommand command) {
        String slug = required(personSlug, "The guest is required.", 160).toLowerCase(Locale.ROOT);
        if (!people.exists(slug)) {
            throw new NoSuchElementException("This guest was not found.");
        }
        String partyCode = validateParty(command);
        if (PartyDirectory.UNKNOWN.equals(partyCode)) {
            catalogue.invalidateCache();
            return;
        }
        AffiliationPeriod period = validatePeriod(command);
        people.insertAffiliation(
                slug, partyCode, period.validFrom(), period.validUntil(), null, ADMIN_ASSIGNMENT, Instant.now());
        catalogue.invalidateCache();
    }

    @Transactional
    public void updateAffiliation(String personSlug, long id, AffiliationCommand command) {
        String slug = required(personSlug, "The guest is required.", 160).toLowerCase(Locale.ROOT);
        String partyCode = validateParty(command);
        if (PartyDirectory.UNKNOWN.equals(partyCode)) {
            people.deleteAffiliation(id, slug);
        } else {
            AffiliationPeriod period = validatePeriod(command);
            people.updateAffiliation(id, slug, partyCode, period.validFrom(), period.validUntil(), Instant.now());
        }
        catalogue.invalidateCache();
    }

    private String validateParty(AffiliationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("The party mapping is required.");
        }
        String partyCode = required(command.partyCode(), "Select a party.", 10).toUpperCase(Locale.ROOT);
        PoliticalParty party = parties.findAll().stream()
                .filter(candidate -> candidate.code().equals(partyCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select a known party."));
        return party.code();
    }

    private AffiliationPeriod validatePeriod(AffiliationCommand command) {
        LocalDate validFrom = command.validFrom();
        LocalDate validUntil = command.validUntil();
        if (validFrom == null) {
            throw new IllegalArgumentException("Choose when this affiliation started.");
        }
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("The affiliation end date cannot be before its start date.");
        }
        return new AffiliationPeriod(validFrom, validUntil);
    }

    private List<String> aliases(List<String> provided, String displayNameFr, String displayNameAr) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(displayNameFr);
        aliases.add(displayNameAr);
        if (provided != null) {
            provided.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::strip)
                    .forEach(value -> aliases.add(required(value, "An alias is too long.", 200)));
        }
        if (aliases.size() > MAX_ALIASES) {
            throw new IllegalArgumentException("A guest can have at most " + MAX_ALIASES + " aliases.");
        }
        return List.copyOf(aliases);
    }

    private String required(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String clean = value.strip();
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
        return clean;
    }

    public record DirectoryWorkbench(
            List<PoliticalParty> parties,
            List<CuratedPerson> people,
            List<PersonSummary> unmatched) {
    }

    public record CuratePerson(
            String slug,
            String displayNameFr,
            String displayNameAr,
            List<String> aliases,
            AffiliationCommand affiliation) {
    }

    public record AffiliationCommand(String partyCode, LocalDate validFrom, LocalDate validUntil) {
    }

    private record AffiliationPeriod(LocalDate validFrom, LocalDate validUntil) {
    }
}
