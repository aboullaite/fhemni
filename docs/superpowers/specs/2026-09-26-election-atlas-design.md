# 2026 Election Atlas

**Date:** 2026-09-26

**Status:** Design scope approved; written specification pending review

**Scope:** A fourth, final-results-only analytical tab for the existing 2026 legislative-election page

## Context

Fhemni already publishes the complete 2026 legislative result on `/elections/2026`: the national ranking, a neutral regional map, 395 named representatives, and the coalition builder. The National tab already lists all 28 reported parties and lists in seat order and shows each entry's combined ballot total and share. The Election Atlas must not repeat that list as another simple ranking.

The new experience should make the final result understandable at several levels: how the 395 seats were won, how ballot ranking differs from parliamentary ranking, how concentrated the result is, how representation varies among Morocco's 12 regions, how local constituencies are structured, and who represents each constituency.

The production snapshot currently contains:

- 395 declared seats: 305 local-constituency seats and 90 regional-list seats;
- 12 final regions and 92 local constituencies;
- 28 national result entries, of which 14 hold seats;
- 9,738,526 combined local and regional-list ballots across all 28 entries;
- all 305 local winners and all 90 regional-list winners by name;
- incomplete local-winner vote figures, so candidate rankings and winning margins are not supported.

## Goals

- Add a fourth `Graphs / Graphiques / بالأرقام` tab between National and Coalition.
- Present an extensive but coherent analytical story rather than a gallery of repetitive charts.
- Reuse the existing result snapshot, polling loop, source metadata, party colors, party symbols, locale handling, and regional navigation.
- Keep every value traceable to an API field or an explicitly documented arithmetic transform.
- Make exact values available without hover, color perception, or a pointing device.
- Support Arabic RTL, French, English, 320 px mobile layouts, keyboard navigation, screen readers, reduced motion, and 200% zoom.
- Retain the existing National ranking as the canonical all-entry ballot-and-seat list.

## Non-goals

- Duplicating the complete 28-entry ballot-and-seat list already shown in National.
- Claiming that ballot share should translate proportionally into seat share.
- Producing a fairness, efficiency, wasted-vote, or votes-per-seat score.
- Showing regional or constituency vote share without complete regional or constituency ballot totals.
- Ranking candidates, calculating victory margins, or comparing candidate popularity from incomplete winner-only vote figures.
- Estimating unique voters from combined local and regional-list ballot totals.
- Inferring gender, age, profession, or other demographic attributes from names.
- Adding historical comparisons, forecasts, or swing calculations without a comparable historical dataset.
- Adding a chart library, CDN dependency, new browser request, or separate polling timer.

## Information architecture

The existing default remains Map. The tab order becomes:

1. Map and regions
2. National result
3. Graphs / Graphiques / بالأرقام
4. Build a majority

The new tab uses the `#graphs` fragment. Existing `#map`, `#national`, and `#coalition` links continue to work. A locale change preserves the active tab, selected analytical controls, and selected party. Polling refreshes chart data without resetting focus or controls.

The tab begins with the title “The final result, explained” and a short source/status line. It does not repeat the page's existing headline metric cards.

## Analytical modules

### 1. How the seats were won

A horizontal stacked bar for each represented party or list shows local-constituency seats and regional-list seats. Rows are ordered by total seats, then combined ballots, then stable party code. Tied totals share the same numerical rank in accessible text.

Each row displays:

- party symbol and localized name;
- local seats;
- regional-list seats;
- total seats;
- share of the 395-seat chamber.

Formula:

`chamberShare = 100 × party.totalSeats / election.totalSeats`

The two segment styles describe seat type rather than party identity. Party color appears in the symbol/accent, while solid and outlined/hatch treatments distinguish local and regional-list segments. Unknown components are not displayed as zero. The chart is available only when every displayed party's seat components reconcile to its total and national components reconcile to 305 and 90 for the final snapshot.

### 2. Ballots and parliamentary representation

One panel offers two modes over the same complete national dataset:

- **Share:** a dumbbell/aligned-dot comparison between combined-ballot share and chamber-seat share;
- **Rank:** a slopegraph connecting each entry's ballot rank to its seat rank.

This panel includes all 28 result entries. Zero-seat entries remain visible. Entries tied on seats receive the same seat rank; the following rank skips appropriately. Ballot rank uses raw ballot totals rather than the rounded API share.

Formulas:

- `ballotShare = 100 × party.votes / election.validVotes`
- `seatShare = 100 × party.totalSeats / election.totalSeats`
- `difference = seatShare − ballotShare`

The difference may be displayed numerically as a percentage-point difference but never labeled gain, loss, fairness, advantage, efficiency, or proportional entitlement. The explanatory note states that the ballot totals combine two contests and do not count unique voters. The panel is suppressed when any national result entry lacks ballots, the denominator is non-positive, or party ballots do not reconcile exactly to `election.validVotes`.

### 3. How concentrated was the result?

A cumulative concentration view shows how quickly combined ballots and chamber seats accumulate when entries are ordered by ballot total. It highlights three published groupings without changing membership between series:

- top four entries;
- top ten entries;
- remaining 18 entries.

The panel prints exact raw totals and percentages for each grouping. It also states the smallest number of parties required for an arithmetic majority, derived by adding parties in seat order until the majority threshold is reached. This is arithmetic only and does not imply political compatibility.

All percentages are computed from raw integers before rounding. Group totals must reconcile exactly to the national totals.

### 4. Local and regional ballots

When a complete source-backed split exists for all 28 national entries, paired horizontal bars compare each entry's local-constituency ballots with its regional-list ballots. This view explains changes such as one party ranking differently between the two ballots.

The data model gains nullable `local_votes` and `regional_votes` fields on `election_party_results`. `votes` remains the published combined total for backward compatibility. The chart is available only when:

- every one of the 28 rows has both components;
- each row satisfies `votes = local_votes + regional_votes`;
- the sum of local components equals the source's complete local-ballot total;
- the sum of regional components equals the source's complete regional-ballot total;
- both component totals sum to `election.validVotes`;
- the exact source URL and revision timestamp are preserved in the operational snapshot.

If these conditions are not met, this module shows a concise “breakdown not yet available” explanation. No partial party split is shown as if it were complete.

### 5. The 12 regional delegations

Twelve stacked bars show the party composition of each region's full parliamentary delegation. Regions remain in the existing stable map order rather than being re-sorted by a selected party.

Each regional bar displays:

- localized region name;
- allocated and declared seats;
- party segments with exact seat labels;
- all parties tied for the region's largest delegation.

The module uses seats only. It never describes a party as “winning” or “controlling” a region. Ties remain explicit. A region is charted as complete only when it is `FINAL`, its party total matches `allocatedSeats`, and its local/regional components reconcile.

### 6. Party × region matrix

A heatmap-style semantic table shows regional parliamentary footprint. Rows are the 12 regions; columns are the 14 represented parties/lists in national seat order. Each cell always includes the exact seat count in text.

Controls switch among:

- seat count;
- share of the region's delegation;
- share of the selected party's national seats.

The denominator and interpretation change visibly with the selected mode. A selected-party mobile view replaces the wide matrix with 12 horizontal bars while preserving the same values. Zero seats are shown as genuine zero only for final regions; omission in a partial region remains unknown.

An explicit action from every region opens the Map tab, selects that region, and applies the existing filter-reset rules. Merely focusing or hovering a matrix cell does not alter Map or Coalition state.

### 7. Constituency structure

A distribution chart groups the 92 local constituencies by their number of elected local seats. The count for a constituency is derived from its complete local winner group, not from optional winner vote values.

The chart reports:

- number of constituencies in each seat-size bucket;
- local seats represented by each bucket;
- the total of 92 constituencies and 305 local seats.

The module is available only when all 305 local winners have a non-empty constituency code and the distinct constituency count is 92. It does not infer configured allocation metadata from incomplete rows.

### 8. Where parties won representation

A party selector drives two related charts:

- regions containing at least one seat for the selected party;
- local constituencies containing at least one local winner for the selected party.

The summary prints the count out of 12 regions and out of 92 constituencies. Bars use exact counts and shares of seats within each final region. The wording is “represented in,” never “won the region.” Regional-list winners are included in regional breadth but excluded from local constituency breadth.

### 9. Representative explorer

The tab closes with a searchable directory of all 395 elected representatives. It is a data explorer rather than another chart and complements the existing geographic Map view.

Filters:

- region;
- local constituency;
- party/list;
- local or regional-list seat;
- candidate-name search.

Each row contains the elected representative's name, party, seat type, region, constituency where applicable, and published winner vote value where available. Missing vote values display “Not published,” never zero. Regional-list representatives remain separate from local constituency groups.

The explorer reuses the existing regional filter semantics where practical but owns independent state so it does not silently alter the Map tab.

## Data projection and rendering architecture

The page continues to issue one result request and retain one snapshot in memory. No chart makes its own request.

A new dependency-free `election-insights.js` module follows the existing browser/CommonJS pattern used by `election-region-filters.js`. It exports pure functions that do not read the DOM, mutate the snapshot, localize text, or round source values:

- `auditSnapshot(snapshot)`
- `deriveSeatComposition(snapshot)`
- `deriveBallotSeatComparison(snapshot)`
- `deriveConcentration(snapshot)`
- `deriveBallotComponents(snapshot)`
- `buildRegionMatrix(snapshot, mode)`
- `deriveConstituencyDistribution(snapshot)`
- `derivePartyFootprint(snapshot, partyCode)`
- `indexRepresentatives(snapshot)`

Every result includes explicit availability, denominator, completeness, and diagnostic fields. Unknown values remain distinct from zero.

`election-results.js` owns localized copy, formatting, tab state, controls, and DOM rendering. It invokes the projections from its existing `render()` flow after every successful initial load, locale reload, and poll. Unchanged polling preserves selected chart modes, party, search text, scroll position, and focus.

The implementation uses semantic HTML/CSS bars and tables where possible. Inline SVG created with `createElementNS` is limited to the dumbbell/slopegraph where geometry materially improves understanding. SVG marks are decorative when the same data is present in adjacent structured text. No external chart library or inline style attribute is introduced.

## Data model and API changes

The national party result gains two nullable fields:

- `localVotes`
- `regionalVotes`

The corresponding database columns are nullable, non-negative big integers. Existing snapshots and clients remain valid when they are null. The public response adds the fields without changing existing names or meanings.

The operational `results.sql` remains the complete replayable result snapshot and is not committed or pushed. It receives the source-backed split only after all 28 rows and all three national sums reconcile. The fixed public source labels and Maroc.ma source URL remain unchanged; the exact evidence URL and source revision remain in snapshot comments.

The API does not add regional vote totals, historical series, demographic fields, or computed narratives.

## Localization

All headings, legends, controls, empty states, notes, and table captions have Arabic/Darija, French, and English copy.

Numbers use the existing `Intl.NumberFormat` locale pattern. Mixed-script names, party codes, and numerical phrases use `<bdi>` where needed. In RTL, horizontal scales begin at inline start and grow toward inline end; data order is unchanged and numerical strings remain isolated.

The combined-ballot note adapts to `voteBasis`. The OFFICIAL_AGGREGATE final snapshot uses explicit wording that the value combines local and regional-list ballots and is not a count of unique voters. A different or unknown basis receives neutral wording or suppresses incompatible comparisons.

## Responsive behavior

- Desktop and tablet show full-width vertically stacked analytical panels.
- The four-tab strip remains in one reading order and scrolls horizontally below 640 px instead of compressing labels.
- Wide matrices switch to the selected-party bar view on narrow screens; the complete table remains accessible through a disclosure.
- Labels sit above bars on small screens, with exact numbers immediately below.
- No rotated text, truncated party/region name, miniature axis label, or chart-level horizontal page overflow is allowed.
- All controls meet a 44 px minimum target size.

## Accessibility

- The tab list implements roving `tabIndex`, Arrow keys, Home, End, and correct RTL behavior.
- Each analytical panel begins with a plain-language takeaway and unit/denominator statement.
- Every graphical view has a “View figures” disclosure containing a captioned table with row and column headers.
- Exact values are never available only through hover or tooltip.
- Decorative marks are not keyboard focusable; only controls and navigation actions enter the focus order.
- Selector changes announce one concise summary through a polite live region without moving focus.
- Color is always paired with text, shape, outline, or hatch.
- Focus indicators and text contrast follow existing Fhemni tokens.
- Animation is optional and disabled under `prefers-reduced-motion`.

## Empty, stale, and corrected states

- Before a complete final/corrected snapshot, the tab remains discoverable and explains that analysis will appear when final results are available.
- Chart-specific validation failure suppresses only that chart and shows its specific explanation; it does not fabricate a plausible graph.
- A transient refresh failure retains the previous valid charts and uses the existing stale-data notice.
- A corrected snapshot re-renders from the new data and labels itself with the correction status and source date.
- Polling never replaces an interactive control while it has focus.

## Testing strategy

### Pure JavaScript tests

Node tests cover every projection with fixtures for:

- the complete 395-seat final snapshot;
- empty/counting data;
- partial regions;
- null versus genuine zero;
- tied regional leaders and tied party ranks;
- all 28 ballot rows, including positive totals that round below 0.1%;
- missing or non-reconciling ballot denominators;
- incomplete and complete local/regional ballot splits;
- 92 constituencies and 305 local winners;
- missing candidate vote values;
- deterministic ordering without input mutation.

### Server and page tests

Integration/static tests verify:

- the API exposes nullable `localVotes` and `regionalVotes`;
- database constraints reject negative component votes;
- the four-tab ARIA wiring and `#graphs` allowlist;
- insight module script order and cache versions;
- absence of inline style attributes and external chart dependencies;
- all three locale copy families;
- CSP compatibility.

### Browser verification

Manual or automated browser checks cover:

- Arabic RTL, French, and English;
- 320 px, 640 px, 900 px, desktop, and 200% zoom;
- keyboard-only tab and control operation;
- screen-reader table access;
- reduced motion;
- locale change and polling state preservation;
- navigation from a regional graph to the existing Map tab;
- no additional network request or polling timer.

## Acceptance criteria

1. The National tab remains the only complete 28-entry ballot-and-seat list; Graphs does not duplicate it as another simple ranking.
2. Every displayed percentage states or exposes its denominator and derives from raw integers before rounding.
3. All national seat, ballot, regional, constituency, and representative totals reconcile to the final snapshot before their views render.
4. Unknown and zero values remain visually and semantically distinct.
5. Ballot/seat comparisons are descriptive and contain no fairness or proportional-entitlement claim.
6. Regional views use seats only and preserve plurality ties.
7. Candidate comparisons never use incomplete winner-only vote data.
8. All chart values are readable without hover, color discrimination, or a pointing device.
9. Arabic, French, and English remain usable on mobile, desktop, RTL, keyboard, screen reader, and 200% zoom.
10. Polling, locale changes, Map filters, and Coalition state do not reset Graphs controls or issue duplicate result requests.
11. No third-party chart dependency or CSP relaxation is introduced.
12. The current final snapshot renders all supported views, while any unavailable local/regional ballot split fails closed with an explicit explanation.
