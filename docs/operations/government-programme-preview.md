# Local programme review

The `programme-preview` profile loads the pinned research snapshot into a separate, persistent H2 database (`data/government-preview`). It must bind to `127.0.0.1`. The importer rejects PostgreSQL, the normal local database, public bindings, and the production profile. Nothing is imported during normal or production startup.

Use SDKMAN Java 26, then run:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=programme-preview
```

The research input directory is `data/government/2026-2031`; it is intentionally gitignored, not embedded in the application. The PDF and all five JSON input files are verified against SHA-256 constants outside those files before loading. Missing or modified input fails closed. Do not regenerate checksums simply to bypass a failure.

The import is transactional and repeatable without duplicates. It uses the existing draft/review/publication machinery solely to exercise the local end-to-end flow. Those local revision events are explicitly marked research preview; they are not production editorial approval or delivery evidence. A changed existing preview is never overwritten automatically.

Review these routes:

- `/government`: programme overview and ministers navigation.
- `/government?tab=programme`: search and filters, ten entries per page.
- `/government/commitments/{slug}`: source anchors, printed PDF pages, literal targets and dates, related research entries, follow controls and revisions.
- `/government/ministers`: current and former membership, search, ten members per page and evidence.
- `/following`: saved follows and matching publication updates after signing in.

The local test-account endpoint can start a normal, one-use magic-link confirmation for `reviewer@fhemni.local`; it is no longer advertised in the public tracker. No email is sent and this is not an administrator. The endpoint exists only in this isolated profile, checks loopback access and requires the normal CSRF token. No production authentication behavior is changed.

## Evidence boundaries

The user-selected public reference is the House of Representatives programme page. The archived PDF is retained at its actual mirror origin and remains **not authenticated against an official copy**. Every imported programme entry carries this flag. Cabinet evidence and programme-PDF authentication are separate questions.

The 595 extracted entries are **not 595 unique promises**. Overview/detail repetition and component/overlap links are retained; no summed promise-delivery percentage is computed. Topic tags are reviewed Fhemni discovery classifications, not official assignments. No minister ownership is guessed from a portfolio.

## Discovery indexes

`src/main/resources/government/programme-topics.json` assigns all 595 source entries to 28 substantive topics, with an additional `OTHER` fallback retained for future ambiguity. `programme-geography.json` links 26 entries to explicit named places in 11 regions. Geographic basins without a precise crosswalk, unnamed sites and national commitments are not assigned to regions. Missing links do not mean a region has no commitments.

Both indexes are applied only when the published slug, Arabic title and literal source anchor match. Geography additionally requires matching printed pages. Source payloads, revision hashes, geographic scope and delivery assessments remain unchanged. Run `node scripts/validate-programme-topics.mjs` with the local research archive to validate both indexes against all source records.

`/government?tab=regions` defaults to programme references: counts represent distinct entries per region, not money, implementation or independent promises. Entries may mention multiple regions. Region/topic/search filters and pagination operate on the full indexed publication set. The separate `regionalView=measurements` view still requires actual published observations. New policy-topic codes are registered by V73 so readers can follow them; this does not retroactively rewrite historical publication events.

All programme entries begin unassessed. Literal target operators, baselines and periods are retained as research context rather than converted into precise deadlines or observed outcomes. Programme-reported baselines are not independent measurements. Arabic original titles remain visibly labelled when translations are unavailable. Subsequent delivery observations, party-programme matches and practical eligibility information require independent editorial review before publication.
