# 2026 Legislative Election Results

**Date:** 2026-09-23

**Status:** Proposed

**Scope:** Public election-results experience, database-backed live updates, coalition exploration, and recoverable result data

## Context

Fhemni needs a public results page for Morocco's 2026 legislative election as official results arrive. The page must explain the national result, show how seats are distributed across Morocco's regions, and let readers explore possible parliamentary majorities without implying that arithmetic alone makes a coalition politically viable.

Results must be updateable directly in PostgreSQL without an application deploy. The implementation should remain intentionally small: Flyway owns the schema and stable reference data, while the changing election results are applied as an operational SQL snapshot. There is no admin ingestion UI, ingestion token, scraper service, or new deployment component in this phase.

## Goals

- Show every contesting party for which a result has been reported, including votes, vote share, and parliamentary seats.
- Present the national ranking with diagrams and bars rather than a dense table.
- Provide a neutral, interactive map of Morocco's 12 regions.
- On hover, keyboard focus, or tap, show all parties with seats in that region, including party symbols and seat counts.
- Keep a synchronized region selector and regional result bars below the map.
- Let readers combine parties and see whether the selection reaches an absolute majority of the 395-seat chamber.
- Add a programme-based alignment signal to the coalition builder so a numerical majority is not presented as guaranteed political compatibility.
- Reflect direct database updates on the public page without rebuilding or redeploying the application.
- Keep a version-controlled, directly replayable copy of the live result data and an operations runbook so the result can be reconstructed after data loss.
- Work well in Darija/Arabic, French, and English, including RTL layouts and mobile interaction.

## Non-goals

- Automatically scraping, reconciling, or publishing official result sources. A local research/pull tool can be designed later.
- Building an administrator UI or public write API for results.
- Predicting which coalition will govern or labeling a coalition as politically agreed.
- Coloring a region as if it belonged to one winning party when several parties hold seats there.
- Replacing the civic-position editorial workflow or exposing unpublished party positions.
- Building a generic election platform for every election type before the 2026 page is proven.
- Storing visitor-built coalitions or other personal result-page state on the server.

## User experience

The canonical page is `/elections/2026`. It opens with a compact election status header showing whether counting is ongoing, preliminary, final, or corrected, the number of declared seats, turnout when available, the source label, and the last update time.

The primary navigation has three tabs:

1. **Map and regions — الخريطة والجهات**
2. **National result — النتائج الوطنية**
3. **Build a majority — ركّب الأغلبية**

The selected tab is represented in the URL fragment so refresh and browser history preserve it. The page keeps one result payload in memory; changing tabs does not trigger duplicate result requests.

### Map and regions

The map is visually neutral. Region fills use Fhemni's surface colors and do not adopt a party color. Hover, focus, or tap adds only a restrained outline/highlight to the selected region.

Each region path is keyboard focusable and has a localized accessible label. Hover and keyboard focus show a small tooltip near the region containing:

- localized region name;
- declared seats in that region;
- every party with at least one declared seat, ordered by seats and then curated party order;
- party symbol, short code/name, and seat count.

Tooltips are an enhancement, not the only way to read the data. Selecting a region also updates a persistent detail panel beneath the map. On touch devices, the first tap selects and reveals the detail; it does not depend on hover.

A localized region selector sits above the detail panel and stays synchronized with map selection. The detail panel uses horizontal bars for party seats. An empty or not-yet-reported region gets an explicit counting-state message rather than a blank chart.

### National result

The national tab ranks parties by seats, then votes, then curated party order. Each row contains the party symbol, localized name, votes, vote share, total seats, and a proportional horizontal seat bar. A compact segment inside the bar distinguishes local-constituency seats from regional-list seats when both are known.

There is no majority threshold marker in the national ranking. The absolute-majority concept belongs only to the interactive builder.

Parties with zero seats remain visible when vote data has been reported. Parties with neither vote nor seat data are omitted until a result row is loaded, so an unreported party is not falsely displayed as having zero support.

### Build a majority

The majority builder presents party cards/chips with symbols, seats, and a clear selected state. Selection is local browser state and can be reset in one action.

The summary contains:

- selected seat total;
- progress toward the absolute majority, derived as `floor(totalSeats / 2) + 1` (198 for 395 seats);
- seats still needed or seats above the threshold;
- a programme-alignment rating based only on published Fhemni party positions;
- the strongest shared themes and strongest tension themes when evidence coverage is sufficient.

The copy must distinguish arithmetic from politics: reaching 198 seats means only that the selected parties have a numerical majority. The alignment score describes published-programme similarity, not coalition willingness, negotiations, discipline, or future behavior.

If fewer than two selected parties have sufficient overlapping published positions, the interface shows “not enough comparable programme data” instead of manufacturing a score. Parties such as PUD that have no published programme positions may still be selected for seat arithmetic; they simply do not increase alignment coverage.

## Data model

Flyway adds the schema and the stable 2026 election/region reference rows. Changing result values are not seeded by Flyway.

### `elections`

| Column | Type | Rules |
| --- | --- | --- |
| `id` | UUID | Primary key |
| `slug` | VARCHAR(40) | Unique; `legislative-2026` |
| `election_date` | DATE | Required |
| `status` | VARCHAR(16) | `SCHEDULED`, `COUNTING`, `PRELIMINARY`, `FINAL`, or `CORRECTED` |
| `total_seats` | INTEGER | Positive; 395 for this election |
| `registered_voters` | BIGINT | Nullable, non-negative |
| `votes_cast` | BIGINT | Nullable, non-negative |
| `valid_votes` | BIGINT | Nullable, non-negative and not greater than votes cast |
| `vote_basis` | VARCHAR(32) | Nullable; `LOCAL_CONSTITUENCY`, `REGIONAL_LIST`, or `OFFICIAL_AGGREGATE` |
| `source_label_ar`, `source_label_fr`, `source_label_en` | VARCHAR(300) | Required localized source names |
| `source_url` | VARCHAR(1200) | Nullable; official result source |
| `source_updated_at` | TIMESTAMP WITH TIME ZONE | Nullable; timestamp supplied by the source |
| `updated_at` | TIMESTAMP WITH TIME ZONE | Required; changes with every result snapshot |

### `election_regions`

This table is election-specific so seat allocations may change in future elections without rewriting a global region record.

The seeded codes are `tanger-tetouan-al-hoceima`, `oriental`, `fes-meknes`, `rabat-sale-kenitra`, `beni-mellal-khenifra`, `casablanca-settat`, `marrakech-safi`, `draa-tafilalet`, `souss-massa`, `guelmim-oued-noun`, `laayoune-sakia-el-hamra`, and `dakhla-oued-ed-dahab`.

| Column | Type | Rules |
| --- | --- | --- |
| `election_id` | UUID | Foreign key to `elections`, part of primary key |
| `code` | VARCHAR(32) | Stable application/map code, part of primary key |
| `name_ar`, `name_fr`, `name_en` | VARCHAR(160) | Required localized names |
| `map_key` | VARCHAR(64) | Unique within the election; matches an SVG `data-region-code` |
| `allocated_seats` | INTEGER | Nullable until confirmed; non-negative |
| `status` | VARCHAR(16) | `PENDING`, `PARTIAL`, or `FINAL` |
| `sort_order` | INTEGER | Required |
| `updated_at` | TIMESTAMP WITH TIME ZONE | Required |

### `election_party_results`

| Column | Type | Rules |
| --- | --- | --- |
| `election_id` | UUID | Foreign key, part of primary key |
| `party_code` | VARCHAR(10) | Foreign key to `political_parties`, part of primary key |
| `votes` | BIGINT | Nullable, non-negative |
| `local_seats` | INTEGER | Required, non-negative |
| `regional_list_seats` | INTEGER | Required, non-negative |
| `total_seats` | INTEGER | Required, non-negative and equal to the two seat components |
| `updated_at` | TIMESTAMP WITH TIME ZONE | Required |

Vote share is calculated when the response is built rather than stored independently. This avoids vote/percentage drift after corrections.

### `election_region_party_results`

| Column | Type | Rules |
| --- | --- | --- |
| `election_id` | UUID | Foreign key, part of primary key |
| `region_code` | VARCHAR(32) | Composite foreign key to `election_regions`, part of primary key |
| `party_code` | VARCHAR(10) | Foreign key to `political_parties`, part of primary key |
| `local_seats` | INTEGER | Required, non-negative |
| `regional_list_seats` | INTEGER | Required, non-negative |
| `total_seats` | INTEGER | Required, non-negative and equal to the two seat components |
| `updated_at` | TIMESTAMP WITH TIME ZONE | Required |

Database checks enforce non-negative values and row-level seat arithmetic. Foreign keys prevent unknown parties or regions from entering the public result.

At the JDBC boundary, timestamp columns use `OffsetDateTime` and are converted to `Instant` in the domain response, matching the repository convention required by PostgreSQL's driver.

Cross-row checks are performed by the operational result transaction before it commits:

- declared national seats must never exceed `elections.total_seats`;
- a `FINAL` or `CORRECTED` election must declare exactly the configured total seats;
- regional party totals may not exceed the corresponding national party totals while counting is incomplete;
- once every region is `FINAL`, regional party totals must equal the national result;
- when a national valid-vote total is present, the party vote rows must sum to it;
- vote and seat totals in the source snapshot must otherwise be internally consistent.

Regional rows deliberately store seats, not a generic `votes` field: local-constituency and regional-list ballots are different measures and must not be combined into an ambiguous regional total. The national vote count is shown with its declared `vote_basis`.

## Public API

### Result snapshot

`GET /api/catalog/elections/2026/results?lang=ar`

The response contains one compact, localized snapshot:

```json
{
  "election": {
    "slug": "legislative-2026",
    "status": "PRELIMINARY",
    "electionDate": "2026-09-23",
    "totalSeats": 395,
    "majoritySeats": 198,
    "declaredSeats": 312,
    "registeredVoters": null,
    "votesCast": null,
    "validVotes": null,
    "voteBasis": "LOCAL_CONSTITUENCY",
    "sourceLabel": "Official results",
    "sourceUrl": null,
    "sourceUpdatedAt": null,
    "updatedAt": "2026-09-23T21:10:00Z"
  },
  "parties": [
    {
      "code": "RNI",
      "name": "التجمع الوطني للأحرار",
      "color": "#1B7FC1",
      "symbolAsset": "/assets/parties/rni-dove.svg",
      "votes": 123456,
      "voteShare": 17.4,
      "localSeats": 72,
      "regionalListSeats": 18,
      "totalSeats": 90
    }
  ],
  "regions": [
    {
      "code": "casablanca-settat",
      "name": "الدار البيضاء - سطات",
      "mapKey": "casablanca-settat",
      "status": "PARTIAL",
      "allocatedSeats": 68,
      "declaredSeats": 51,
      "parties": []
    }
  ]
}
```

The API returns `404` for an unknown election and `400` for an unsupported language. Result-row presence is the publication boundary: every party with a loaded result row is exposed even if it is not otherwise visible in the programme catalogue. `UNKNOWN` is never a valid result party. English uses the existing official French party name until a separately verified English legal name exists. The endpoint never exposes draft civic positions.

The page requests a fresh snapshot every 30 seconds only while the document is visible. The response is a small bounded dataset and uses `Cache-Control: public, max-age=5`. Repository queries batch-load parties and regions; there is no per-party or per-region query loop. A database update therefore appears to an active reader within approximately 35 seconds without a deploy.

### Coalition evaluation

`POST /api/catalog/elections/2026/coalitions/evaluate`

Request:

```json
{
  "language": "ar",
  "partyCodes": ["RNI", "PAM", "PI"]
}
```

The endpoint is read-only and stores nothing. It validates a unique list of no more than the number of loaded result parties and rejects unknown or non-result party codes.

Response:

```json
{
  "selectedSeats": 214,
  "resultUpdatedAt": "2026-09-23T21:10:00Z",
  "majoritySeats": 198,
  "remainingSeats": 0,
  "seatsAboveMajority": 16,
  "hasMajority": true,
  "alignment": {
    "status": "AVAILABLE",
    "score": 71,
    "comparableQuestions": 16,
    "comparedPairQuestions": 42,
    "possiblePairQuestions": 54,
    "coveragePercent": 78,
    "level": "MEDIUM",
    "strongestAgreements": [],
    "strongestTensions": []
  }
}
```

The frontend debounces selection changes before evaluating. A failed alignment request does not break seat arithmetic: the browser already has seat totals and displays the coalition total with a temporarily unavailable alignment message.

Evaluation is bounded and cheap: there are no provider calls, writes, or unbounded inputs. The response uses `Cache-Control: no-store`. If `resultUpdatedAt` differs from the snapshot currently displayed, the browser refreshes the result before presenting the server's seat total.

## Programme-alignment calculation

The evaluator uses only `PUBLISHED` positions from the current civic questionnaire edition.

Stances use the same numeric interpretation as the existing compass:

- `SUPPORTS` = `+2`
- `MIXED` = `0`
- `OPPOSES` = `-2`
- `NO_POSITION` or a missing row = not comparable

For every selected party pair and every question where both parties have comparable positions, pair alignment is:

`1 - abs(leftValue - rightValue) / 4`

The overall score is the unweighted mean of those comparable pair/question scores, expressed from 0 to 100. Theme scores aggregate their questions with the same rule. Agreement themes score at least 75 and are ordered by coverage, then score. Tension themes score below 50 and are ordered by coverage, then lowest score. At most two of each are returned; if no theme qualifies, that list is empty.

The response is `INSUFFICIENT_DATA` instead of a numeric level when:

- fewer than two selected parties are comparable; or
- fewer than six questionnaire questions have at least one comparable selected-party pair; or
- less than one third of all possible selected-party/question comparisons are covered.

Level labels are intentionally broad: `STRONG` for 75–100, `MEDIUM` for 50–74, and `WEAK` for 0–49. The UI always shows coverage beside the level so a coalition with thin evidence cannot look more certain than it is.

## Frontend implementation

The page follows the existing vanilla HTML/CSS/JavaScript architecture:

- `election-results.html`
- `css/election-results.css`
- `js/election-results.js`
- a local, reviewed `assets/maps/morocco-regions-2026.svg`
- `PageController` route for `/elections/2026`
- explicit public security rules for the page and election APIs

The map SVG is bundled locally and contains one path/group per region with a stable `data-region-code`. JavaScript fetches it from the same origin, parses it as SVG with `DOMParser`, and appends reviewed nodes without using `innerHTML`. No CDN, remote GeoJSON fetch, D3 dependency, or inline style mutation is required, preserving the production content-security policy. The asset records its public source, license/provenance, and review date; politically sensitive boundary choices are never improvised by application code.

JavaScript applies predefined CSS classes and does not generate style attributes. Each known party code has a static stylesheet class that declares its curated color as a CSS custom property. JavaScript maps validated party codes to those classes and never injects a database color into CSS.

The page supports:

- Arabic/Darija RTL, French, and English copy;
- keyboard tab navigation and region selection;
- tooltip content available through the persistent region panel;
- touch targets of at least 44px;
- reduced-motion preferences;
- loading skeletons, empty states, stale-data state, and retry state;
- compact mobile bars rather than horizontal page overflow;
- visible source and last-update attribution.

If the refresh fails after a successful load, the existing result remains visible with a stale-data warning and retry action. Initial failure shows a friendly unavailable state, never invented zero results.

## Result update and recovery workflow

The version-controlled source of truth for mutable result data is:

`data/elections/2026/results.sql`

It is an idempotent, PostgreSQL-compatible full snapshot, not a Flyway migration. It contains no credentials, host names, or deployment secrets. It:

1. starts a transaction and acquires an election-specific advisory transaction lock;
2. updates election and per-region reporting metadata plus the snapshot timestamp;
3. replaces the 2026 national and regional party result rows inside that transaction;
4. runs the cross-row validation checks described above;
5. commits only when all checks pass.

Using a full snapshot avoids stale rows when an official correction removes or reclassifies a previous result. PostgreSQL readers continue seeing the previous committed snapshot until the replacement transaction commits.

The same file is used for live updates and disaster recovery. The operator edits the snapshot locally, reviews and commits the data diff, applies that exact committed file to PostgreSQL using the existing secure database access path, and runs the verification queries. This keeps the recovery record at least as durable as the live change without requiring an application deploy. In a recovery, Flyway first reconstructs the schema/reference rows and this file restores the latest election result.

The companion runbook is:

`docs/operations/election-results.md`

It documents:

- authoritative-source recording;
- pre-update database backup/checkpoint;
- safe single-transaction application;
- post-update national and regional verification queries;
- page/API smoke checks;
- correction and rollback procedure;
- full restore procedure on a fresh migrated database;
- how to confirm the committed SQL file matches production;
- the rule that credentials and raw database dumps never enter Git.

The later local search/pull tool should produce or update this same snapshot rather than inventing a second ingestion format.

## Observability

Result reads and coalition evaluations use structured application logs only for failures and validation problems; successful public polling is not logged at info level. Existing HTTP and JVM metrics cover request rate, latency, and failures.

The result page sends these GA4 events through the existing analytics helper:

- `election_results_tab_viewed` with `tab`;
- `election_region_selected` with `region_code` and `method` (`map` or `selector`);
- `election_coalition_changed` with party count, selected seats, and majority boolean, but not the party combination itself.

No visitor identity or coalition composition is persisted by Fhemni.

## Failure and edge cases

- **Counting has not started:** page shell and region map load with a clear pending state.
- **Partial national result:** declared-seat count is shown; bars use reported values and do not imply finality.
- **National result ahead of regions:** national tab updates; map labels incomplete regions as partial without forcing false reconciliation.
- **Corrected result:** one atomic snapshot replaces the previous values and changes the visible timestamp/status.
- **Unknown party code:** the SQL transaction fails on the foreign key before publication; the party must be added through reviewed reference data first.
- **Missing party symbol:** existing neutral verified/unverified party asset behavior is reused; the page never invents a logo.
- **No programme data:** seat arithmetic remains available; alignment reports insufficient coverage.
- **API outage after load:** last successful data stays visible with a stale warning.
- **JavaScript disabled:** the page provides a localized explanation and source link; the interactive views require JavaScript.

## Security and trust boundaries

- Election writes remain database-operator actions; no new public or admin write endpoint is introduced.
- Result API values are selected from typed database columns, not stored HTML.
- Localized strings are rendered with text nodes, never `innerHTML`.
- Source URLs must be HTTP(S) and are opened with safe external-link attributes.
- Coalition input is bounded, deduplicated, and restricted to parties present in the public result.
- Only published civic positions contribute to alignment.
- All political interpretation copy states that programme similarity is evidence-based context, not a recommendation or prediction.

## Verification

Automated coverage includes:

- Flyway migration on H2 PostgreSQL mode;
- repository mapping and aggregate queries;
- national/region seat arithmetic and ordering;
- API localization and unknown-election/language behavior;
- partial, final, corrected, and empty result states;
- coalition input validation, threshold arithmetic, position coverage, and alignment scoring;
- confirmation that draft/no-position rows do not influence alignment;
- public security access to the page and read-only endpoints;
- static checks that map region codes match seeded region codes;
- frontend unit-level tests for selection, retry, tab state, and builder fallback where the existing test setup permits;
- desktop and mobile visual checks in Arabic RTL, French, and English;
- keyboard-only map and builder checks;
- an end-to-end manual update using the snapshot file against a disposable PostgreSQL database, followed by API and page verification.

The final branch must pass `./mvnw verify` under Java 26.

## Acceptance criteria

- `/elections/2026` is public and renders all three designed tabs on desktop and mobile.
- Directly applying a valid result snapshot changes the public result without an application deploy.
- National results show reported votes and seats for every loaded contesting party using bars, not a primary data table.
- The neutral map exposes all seat-winning parties for a region by hover, focus, and tap.
- The region selector and map selection stay synchronized.
- The national view contains no majority marker.
- The majority builder reaches 198 seats at the correct point and clearly separates arithmetic majority from programme alignment.
- Alignment uses published positions only and reports insufficient data rather than treating missing positions as neutral.
- Arabic, French, and English are supported, and Arabic remains usable in RTL on a narrow mobile viewport.
- The checked-in result snapshot and operations runbook are sufficient to rebuild the result data after applying Flyway migrations.
- No credentials, database dumps, or deployment-specific secrets are committed.
