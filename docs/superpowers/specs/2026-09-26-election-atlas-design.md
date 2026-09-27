# 2026 Election Atlas

**Date:** 2026-09-26

**Status:** Revised visual direction approved; written specification ready for review

**Scope:** A fourth, final-results-only analytical tab for the existing 2026 legislative-election page

## Context

Fhemni already publishes Morocco's complete 2026 legislative result on `/elections/2026`: a regional map, a national result table, all 395 elected representatives, and the coalition builder. The new Election Atlas must explain relationships in that final result rather than reproduce the National tab as another static ranking.

The final snapshot contains:

- 395 seats: 305 local-constituency seats and 90 regional-list seats;
- 12 final regions and 92 local constituencies;
- 28 national result entries, of which 14 hold seats;
- 9,738,526 combined local and regional-list ballots;
- all 305 local winners and all 90 regional-list winners by name;
- incomplete winner vote values, so candidate rankings and victory margins remain unsupported.

Médias24's complete 28-entry ballot table also supplies a reconciling split of 4,900,377 local ballots and 4,838,149 regional-list ballots. Those two components sum to 9,738,526. Import is permitted only after the `PA` party-identity discrepancy and raw revision timestamp provenance are resolved explicitly.

## Goals

- Add a fourth `Graphs / Graphiques / بالأرقام` tab between National and Coalition.
- Tell one coherent story through five full-width sections: ballot structure, ballots versus representation, geography, constituency structure, and representatives.
- Let every summarized chart expand to complete figures, including all 28 entries where applicable.
- Generate editorial takeaways from the same pure transforms that generate the graphics.
- Reuse the existing result request, polling loop, locale handling, party metadata, source presentation, and navigation.
- Make every exact value available without hover, color perception, or a pointing device.
- Support Arabic RTL, French, English, 320 px mobile layouts, keyboard navigation, screen readers, reduced motion, and 200% zoom.

## Non-goals

- Adding a second static copy of the National result table.
- Claiming ballot share should translate proportionally into seat share.
- Producing fairness, efficiency, wasted-vote, proportional-entitlement, or votes-per-seat scores.
- Showing regional vote share without complete regional ballot totals.
- Ranking candidates, calculating victory margins, or comparing candidate popularity from incomplete winner-only vote values.
- Treating 9,738,526 combined ballots as unique voters.
- Inferring transfers between the local and regional ballots or explaining aggregate differences causally.
- Inferring demographic attributes from names.
- Adding historical comparisons, forecasts, or swing calculations without comparable data.
- Adding a chart library, CDN dependency, extra browser request, or separate polling timer.

## Information architecture

The tab order becomes:

1. Map and regions
2. National result
3. Graphs / Graphiques / بالأرقام
4. Build a majority

The new tab uses `#graphs`. Existing fragments remain valid. Locale changes preserve the active tab and analytical controls. Polling refreshes the data without resetting focus, scroll position, selected party, expanded state, or search text.

The tab begins with “The final result, explained” and four local jump links:

- Ballots & seats
- Geography
- Constituencies
- Representatives

The existing National tab remains the canonical table for the complete election result. Graphs may expose all 28 entries through analytical views, search, expansion, and accessible figure tables, but it does not add another plain ranking table.

The tab does not render an internal provenance or validation paragraph beneath the experience. Existing page-level public source labels remain unchanged. Exact evidence URLs and revision details stay in the operational snapshot and update records.

## Analytical sections

### 1. Two ballots, different results

This is the lead analytical section. Paired horizontal bars compare local-constituency ballots with regional-list ballots for all 28 entries.

The initial view shows the leading ten by combined ballots. A working “Show all 28” control, party search, and order control expose the complete dataset. Available orderings are combined ballots, local ballots, and regional-list ballots. Switching order never changes the values or denominator.

Each row displays:

- party/list code, symbol, and localized name;
- local ballot count and share;
- regional-list ballot count and share;
- combined total in the figures disclosure.

The denominators are separate and always visible:

- `localShare = 100 × localVotes / 4,900,377`
- `regionalShare = 100 × regionalVotes / 4,838,149`
- `combinedVotes = localVotes + regionalVotes`

The opening generated takeaway explains the verified RNI/PJD reversal:

- RNI local ballots: 718,630;
- PJD local ballots: 696,031;
- RNI local lead: 22,599;
- PJD regional-list ballots: 730,315;
- RNI regional-list ballots: 640,468;
- PJD regional-list lead: 89,847;
- PJD combined lead: 67,248.

The copy states only the arithmetic relationship. It does not imply that the same people changed their votes or that one contest caused the other.

This section renders only when all 28 component rows pass the ballot-component audit described under Data model and validation. If the audit fails, it shows a concise localized unavailable state rather than partial or inferred figures.

### 2. Ballots and parliamentary representation

One comparison aligns each entry's combined-ballot share and chamber-seat share. Circles and squares distinguish the two measures in addition to color.

The default view shows ten entries in combined-ballot order. Party search, “Show all 28,” and an ordering control expose all entries, including all 14 zero-seat entries. Available ordering is combined ballots or total seats. Ties preserve stable party-code ordering and accessible shared ranks.

Each row displays:

- combined-ballot share and raw count;
- chamber-seat share and total seats;
- local plus regional-list seat components.

Formulas:

- `ballotShare = 100 × votes / 9,738,526`
- `seatShare = 100 × totalSeats / 395`
- `difference = seatShare − ballotShare`

The visual may show the percentage-point difference, but never labels it gain, loss, advantage, fairness, efficiency, or deserved representation.

The separate rank slopegraph, seat-composition panel, and concentration panel are removed. Their useful information is consolidated here through exact row values and generated takeaways.

The concentration takeaway uses ballot ordering and fixed membership across both measures:

- top four: 6,400,212 ballots (65.72%) and 282 seats (71.39%);
- top ten: 9,313,417 ballots (95.63%) and 389 seats (98.48%);
- remaining 18: 425,109 ballots (4.37%) and six seats (1.52%);
- zero-seat entries: 206,250 ballots (2.12%).

The top-ten text is generated from raw values and must not reproduce the conflicting “roughly 90%” newsroom prose.

### 3. Where are parties represented?

One geography explorer replaces the prior regional-delegation chart, party-by-region matrix, and separate footprint panel. It has three views.

#### By region

The user selects one of all 12 regions. The view shows every represented party in that region, exact seat count, share of the region's complete delegation, delegation size, number of represented parties, and all parties tied for the largest delegation.

Formula:

`regionShare = 100 × partyRegionSeats / region.allocatedSeats`

Wording is “largest delegation” or “represented,” never “won,” “controls,” or “governs” the region.

#### By party

The user selects any nationally represented entry. The view shows all 12 regions, including genuine zeroes, with:

- the party's seat count in the region;
- the region's total delegation;
- share of the selected party's national seats;
- number of regions containing at least one seat;
- local and regional-list national seat components.

Formula:

`partyDistributionShare = 100 × partyRegionSeats / party.totalSeats`

Distinct local-constituency breadth is computed from unique constituency identifiers among the party's local winners. It is never substituted with local-seat count.

#### All figures

Desktop shows a semantic matrix with the 12 regions and all 14 represented entries. Every cell contains an exact seat count. Color uses one fixed scale across the whole matrix; it never rescales independently per row or mode. Zeroes remain visible.

Mobile defaults to the selected-region or selected-party list. The complete matrix remains available as an accessible disclosure rather than creating page-level horizontal overflow.

Every regional or party view includes a “See representatives” action that opens the final explorer with visible filters. Geography interactions do not silently alter Map or Coalition state.

### 4. How large are local constituencies?

A five-bin distribution groups the 92 local constituencies by allocated local seats:

- 21 two-seat constituencies;
- 38 three-seat constituencies;
- 22 four-seat constituencies;
- five five-seat constituencies;
- six six-seat constituencies.

The bins reconcile to 92 constituencies and 305 local seats. Bar height is derived from a definite plotting height and the largest constituency count.

Selecting a bin reveals its constituency list and an action into the representative explorer. The explanatory text states that bar height counts constituencies, not seats. The module is available only when the complete local constituency set reconciles to 92/305.

### 5. Who represents me?

The tab closes with a searchable directory over all 395 elected representatives.

Filters:

- name or constituency search;
- region;
- local constituency;
- party/list;
- local or regional-list seat.

The explorer shows the result count, active filter chips, reset, and progressive disclosure or pagination. Each result contains name, party, seat type, region, constituency where applicable, and published winner vote value when available.

Missing vote values display “Not published,” never zero. Regional-list representatives show their region and do not receive an invented local constituency.

Desktop uses a semantic table. Mobile uses stacked result cards that retain party, region/constituency, seat type, and vote availability; it must not hide those essential fields.

## Editorial takeaways

Each major analytical section has at most one short takeaway. Takeaways are deterministic outputs of the same raw-value transforms used by the visual and figure table. They are never independently typed constants in rendering code.

Takeaways must:

- identify their denominator or ranking basis;
- preserve ties;
- use exact raw totals before rounding;
- avoid causality, evaluation, or proportional-entitlement language;
- disappear with their section when validation fails.

The initial approved takeaways are the RNI/PJD ballot reversal, top-four/top-ten concentration, and selected geography breadth.

## Data projection and rendering architecture

The page continues to issue one result request and retain one snapshot in memory. No analytical section makes its own request.

A dependency-free `election-insights.js` module follows the existing browser/CommonJS pattern used by `election-region-filters.js`. It exports pure functions that do not read the DOM, mutate the snapshot, localize text, or round source values:

- `auditSnapshot(snapshot)`
- `deriveBallotComponents(snapshot)`
- `deriveBallotSeatComparison(snapshot)`
- `deriveConcentration(snapshot)`
- `buildRegionMatrix(snapshot)`
- `deriveRegionDelegation(snapshot, regionCode)`
- `derivePartyGeography(snapshot, partyCode)`
- `deriveConstituencyDistribution(snapshot)`
- `indexRepresentatives(snapshot)`

Every projection returns explicit availability, denominator, completeness, and diagnostic fields. Unknown remains distinct from zero.

`election-results.js` owns localized copy, formatting, tab state, controls, and DOM rendering. It invokes the projections from the existing `render()` flow after initial load, locale reload, and polling refresh. Rendering derives mark positions from values; it never patches party-specific coordinates such as the prior incorrect USFP dot order.

Implementation uses semantic HTML/CSS bars and tables where possible. Inline SVG is allowed only when geometry materially improves comprehension and the same data exists in adjacent structured text. No external chart dependency or inline event handler is introduced.

## Data model and validation

`election_party_results` gains two nullable, non-negative big-integer columns:

- `local_votes`
- `regional_votes`

The API adds nullable `localVotes` and `regionalVotes` without changing the existing `votes` meaning.

The operational `results.sql` remains the complete replayable snapshot and is never committed or pushed. The ballot-component import occurs only when:

1. all 28 entries have both components;
2. every row satisfies `votes = local_votes + regional_votes`;
3. local components sum to 4,900,377;
4. regional components sum to 4,838,149;
5. both components sum to 9,738,526;
6. source party “PND” maps to existing `ND` while remaining distinct from `PDN`;
7. the source row labeled “Parti du travail” is resolved authoritatively against existing `PA — Parti de l’Action` rather than matched by total alone;
8. the raw article revision timestamp and its timezone interpretation are preserved explicitly without silent normalization.

`IND.regionalVotes = 0` is a genuine source-published zero. It must not be treated as missing.

The fixed public source labels and Maroc.ma URL remain unchanged. The exact newsroom evidence URL and source revision stay in operational comments and update records, not as an extra paragraph inside the Graphs tab.

## Localization

All headings, legends, controls, takeaways, unavailable states, table captions, filter chips, and result labels have Arabic/Darija, French, and English copy.

Numbers use the existing `Intl.NumberFormat` locale pattern. Mixed-script names, party codes, and numeric phrases use `<bdi>` where needed. In RTL, bars grow from inline start while data order and isolated numeric strings remain stable.

The combined-ballot explanation states that the figure combines local and regional-list ballots and is not a unique-voter count.

## Responsive behavior

- All five sections are full-width and vertically stacked.
- The tab strip scrolls horizontally below 640 px rather than compressing labels.
- Row layouts become label → plot → exact values on narrow screens.
- Complete 28-entry expansion remains usable without horizontal chart scrolling.
- Geography uses region/party lists by default on mobile; the matrix is a disclosure.
- Representative table rows become cards while preserving constituency/region and seat type.
- No rotated label, truncated party/region name, miniature text, or page-level horizontal overflow is allowed.
- Controls provide approximately 44 px coarse-pointer targets.

## Accessibility

- The tab list implements the existing roving-tabindex and RTL keyboard behavior.
- Each analytical section begins with a plain-language takeaway and denominator statement.
- Every graphic has a complete “View figures” disclosure with semantic headers.
- Exact values are never hover-only or color-only.
- Ballot and seat marks differ by both color and shape.
- Decorative marks are not keyboard focusable.
- Selector and expansion changes announce one concise summary through a polite live region.
- Focus remains stable across polling updates.
- Existing contrast and focus tokens are retained.
- Motion is optional and disabled under `prefers-reduced-motion`.

## Empty, stale, and corrected states

- Before a complete final/corrected snapshot, the tab remains discoverable and explains when analysis becomes available.
- A section-specific audit failure suppresses only that section and presents a localized unavailable state.
- A transient refresh failure retains the previous valid analysis and uses the existing stale-data notice.
- A corrected snapshot re-renders from new raw data and preserves interactive state where still valid.
- Polling never replaces a control while it has focus.

## Testing strategy

### Pure JavaScript tests

Node tests cover:

- complete 395-seat final data;
- all 28 ballot rows, including genuine zero and positive values below 0.1%;
- row and national reconciliation of local/regional ballot components;
- the RNI/PJD reversal arithmetic;
- exact top-four, top-ten, remaining-18, and zero-seat groupings;
- ballot/seat mark ordering, including USFP;
- tied seats and stable ordering;
- all 12 final regions and all 14 represented entries;
- fixed-scale matrix values and genuine zeroes;
- distinct constituency breadth versus local-seat count;
- the 92/305 constituency distribution;
- representative indexing with missing votes and regional-list seats;
- unavailable states for incomplete or contradictory input;
- deterministic results without input mutation.

### Server and page tests

Integration/static tests verify:

- nullable `localVotes` and `regionalVotes` API fields;
- non-negative database constraints;
- four-tab ARIA wiring and `#graphs` allowlist;
- insight-module script order and cache versions;
- no external chart dependency or separate result request;
- no internal provenance paragraph in the Graphs tab;
- Arabic, French, and English copy families;
- CSP compatibility.

### Browser verification

Browser checks cover:

- ten-row initial and complete 28-row expanded states;
- party search and every ordering control;
- the RNI/PJD and USFP relationships;
- all three geography modes and all 12 regions;
- constituency-bin drill-down;
- representative filter chips, reset, and mobile cards;
- Arabic RTL, French, and English;
- 320 px, 640 px, 900 px, desktop, and 200% zoom;
- keyboard-only use, screen-reader tables, and reduced motion;
- locale/polling state preservation;
- no extra network request or polling timer;
- no page-level horizontal overflow.

## Acceptance criteria

1. Graphs contains the approved five-section story and no redundant standalone seat-composition, rank-slopegraph, concentration, or footprint panels.
2. All 28 entries are reachable in both ballot sections through working search and expansion controls.
3. The National tab remains the canonical result table; Graphs uses analytical comparisons rather than a duplicated static ranking.
4. Every percentage exposes its denominator and derives from raw integers before rounding.
5. All seat, ballot, regional, constituency, and representative totals reconcile before their sections render.
6. The ballot split imports only after all eight validation conditions pass.
7. Generated takeaways match the values displayed in their section and contain no conflicting hand-written claim.
8. Unknown and zero remain visually and semantically distinct.
9. Regional views preserve ties and never claim a party won or controls a region.
10. Candidate comparisons never use incomplete winner-only vote data.
11. All values are readable without hover, color discrimination, or a pointing device.
12. Mobile preserves complete data access and essential representative fields without page-level horizontal overflow.
13. Arabic, French, and English work in RTL/LTR, keyboard, screen reader, reduced motion, and 200% zoom.
14. Polling and locale changes preserve Graphs controls and do not duplicate result requests.
15. No chart dependency or CSP relaxation is introduced.
16. The Graphs tab contains no internal Médias24/provenance warning paragraph.
