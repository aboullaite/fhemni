# 2026 Election Atlas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the approved five-section Election Atlas to the 2026 result page, backed by complete local/regional ballot components and the existing final 395-seat snapshot, while preserving the current National tab and production-source presentation.

**Architecture:** Extend the existing `election_party_results` read model with nullable ballot components, expose them through the current single results endpoint, and derive every chart, takeaway, geography view, constituency bin, and representative record in a dependency-free pure JavaScript module. Keep localization, DOM rendering, tab state, controls, polling, and formatting in the existing election page controller. Every section fails closed when its own completeness audit fails; unknown values are never converted to zero.

**Tech Stack:** Java 26, Spring Boot 4.1, JdbcClient, Flyway, H2/PostgreSQL, vanilla JavaScript with browser/CommonJS exports, semantic HTML, CSS/Tailwind build, Node 24 test runner, JUnit/MockMvc/AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-26-election-atlas-design.md`

## Global Constraints

- Preserve the existing uncommitted National-tab work in `app.css`, `dist.css`, `election-results.html`, `election-results.js`, `ElectionResultIntegrationTest.java`, and `NavigationConsistencyTest.java`. Treat those edits as the starting baseline; never discard or rewrite them wholesale.
- Checkpoint those already-approved National-tab edits in Task 0 before Atlas implementation. After that checkpoint, use path-specific staging (and `git add -p` if an unexpected overlapping edit appears) so unrelated work never leaks into an Atlas commit.
- Keep `/private/tmp/fhemni-election-results/data/elections/2026/results.sql` complete and replayable, but do **not** commit or push that operational artifact.
- Keep the fixed public source labels and `https://www.maroc.ma/fr/elections-legislatives-marocaines-2026` unchanged. The Médias24 evidence URL and raw revision timestamp remain in operational comments, not in an extra Atlas paragraph.
- Reuse the existing results request, in-memory snapshot, locale reload, and polling timer. No Atlas section may fetch independently or create another timer.
- Preserve existing `votes` semantics. `localVotes` and `regionalVotes` are additive nullable fields; `0` remains distinct from missing, especially for `IND.regionalVotes`.
- Render charts with semantic HTML/CSS and adjacent exact values. Use no CDN, chart library, inline event handler, or party-specific coordinate patch.
- Preserve Atlas controls, focus target, expanded state, search text, selected region/party/bin, representative filters, and scroll position across polling refreshes and locale reloads.
- Commit only the files named by the current task. Never stage the whole dirty worktree.

## Public Interfaces and Invariants

The results endpoint remains `GET /api/catalog/elections/2026/results?lang={ar|fr|en}`. Each party gains:

```json
{
  "votes": 1359098,
  "localVotes": 718630,
  "regionalVotes": 640468
}
```

Each local winner also gains the source constituency's explicit `allocatedSeats` value so constituency-size bins use metadata rather than winner-count inference.

`election-insights.js` exports these pure functions in both browser and CommonJS environments:

```js
auditSnapshot(snapshot)
deriveBallotComponents(snapshot)
deriveBallotSeatComparison(snapshot)
deriveConcentration(snapshot)
buildRegionMatrix(snapshot)
deriveRegionDelegation(snapshot, regionCode)
derivePartyGeography(snapshot, partyCode)
deriveConstituencyDistribution(snapshot)
indexRepresentatives(snapshot)
```

Every projection returns `{ available, diagnostics, ... }`. A projection may include raw denominators, completeness counts, ordered rows, ties, and deterministic takeaway inputs, but never localized prose or pre-rounded source values.

The full ballot audit is available only when all of these hold:

- exactly 28 party/list rows;
- every row has integer, non-negative `votes`, `localVotes`, and `regionalVotes`;
- every row satisfies `votes === localVotes + regionalVotes`;
- local components total 4,900,377;
- regional components total 4,838,149;
- combined components total 9,738,526;
- `ND` and `PDN` remain distinct and there is no stray `PND` row;
- `PA` is the canonical row for the source's `Parti du travail`/`حزب العمل` identity;
- `IND.regionalVotes === 0` is retained as a real zero.

## Review Focus

- No hidden inference: verify all zero/missing branches, denominators, stable tie ordering, and completeness gates.
- No lifecycle regressions: verify one request/one timer, abort behavior, hash navigation, locale changes, and polling state preservation.
- No accessibility regressions: verify tab semantics, labels, focus restoration, reduced motion, RTL, 200% zoom, and keyboard-only operation.
- No production drift: compare the operational snapshot, production API, and PostgreSQL immediately before any write; back up first; verify both API and page afterward.

---

## Task 0: Checkpoint the approved National-tab baseline

**Files:**

- Existing modifications: `src/main/resources/static/css/app.css`
- Existing modifications: `src/main/resources/static/css/dist.css`
- Existing modifications: `src/main/resources/static/election-results.html`
- Existing modifications: `src/main/resources/static/js/election-results.js`
- Existing modifications: `src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java`
- Existing modifications: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`

- [ ] **Step 1: Review the pre-existing diff without changing it**

Confirm it contains only the already-approved National-tab total/count/share enhancement, its localized copy, cache-version bumps, styles, and matching tests. In particular, preserve `electionNationalVoteTotal`, `voteSummary`, raw vote count/share rendering, and the generated CSS.

- [ ] **Step 2: Run the focused baseline tests**

```sh
./mvnw -Dtest=ElectionResultIntegrationTest,NavigationConsistencyTest test
```

Expected: PASS before any Atlas code is added.

- [ ] **Step 3: Commit the approved baseline as its own checkpoint**

```sh
git add src/main/resources/static/css/app.css
git add src/main/resources/static/css/dist.css
git add src/main/resources/static/election-results.html
git add src/main/resources/static/js/election-results.js
git add src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git commit -m "feat: show national election vote totals"
```

## Task 1: Extend the database and API contract

**Files:**

- Create: `src/main/resources/db/migration/V59__add_election_party_ballot_components.sql`
- Modify: `src/main/java/dev/maboullaite/fhemni/election/ElectionResultRepository.java`
- Modify: `src/main/java/dev/maboullaite/fhemni/election/ElectionResultService.java`
- Modify: `src/test/java/dev/maboullaite/fhemni/election/ElectionResultSnapshotSqlTest.java`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java`

- [ ] **Step 1: Write failing API assertions**

Update the synthetic national helper so at least one party is inserted with explicit local/regional ballot components. Assert the endpoint returns both values and the existing combined value:

```java
.andExpect(jsonPath("$.parties[0].votes").value(420))
.andExpect(jsonPath("$.parties[0].localVotes").value(220))
.andExpect(jsonPath("$.parties[0].regionalVotes").value(200))
```

Also leave one synthetic party's two component fields null and assert they are absent/null according to the project's Jackson configuration. Add an assertion that a constituency winner exposes `allocatedSeats` from `election_constituencies`.

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```sh
./mvnw -Dtest=ElectionResultIntegrationTest test
```

Expected: assertions for `localVotes`, `regionalVotes`, and `allocatedSeats` fail because the fields do not yet exist.

- [ ] **Step 3: Add the migration**

Create the two non-negative nullable columns and a row-level reconciliation constraint that applies only when both components are present:

```sql
ALTER TABLE election_party_results
    ADD COLUMN local_votes BIGINT CHECK (local_votes IS NULL OR local_votes >= 0),
    ADD COLUMN regional_votes BIGINT CHECK (regional_votes IS NULL OR regional_votes >= 0),
    ADD CONSTRAINT election_party_results_vote_components_check
        CHECK (
            local_votes IS NULL
            OR regional_votes IS NULL
            OR (votes IS NOT NULL AND votes = local_votes + regional_votes)
        );
```

Do not require both columns to be populated globally; old and partial snapshots must remain readable. Complete-28 enforcement belongs in the Atlas audit and operational snapshot validation.

Update `ElectionResultSnapshotSqlTest` to execute V59 after V58, then add a PostgreSQL-backed test proving the database rejects a row whose populated components do not add to `votes` while still accepting null components for older snapshots.

- [ ] **Step 4: Thread the fields through the repository**

Add `result.local_votes` and `result.regional_votes` to `partyResults`, read them with `nullableLong`, and add `Long localVotes`/`Long regionalVotes` immediately after `votes` in `PartyResultRow`.

Add `constituency.allocated_seats` to `constituencyWinners`, map it with `nullableInteger`, and add `Integer allocatedSeats` to `ConstituencyWinnerRow`.

- [ ] **Step 5: Thread the fields through the service records**

Add `localVotes` and `regionalVotes` to public `PartyResult`, preserving `votes` and `voteShare`. Add `allocatedSeats` to public `ConstituencyWinner`. Do not calculate missing components in Java.

- [ ] **Step 6: Make the integration fixture explicit**

Change `insertNational` to accept component values and insert `local_votes`/`regional_votes`. Use reconciling synthetic values for the primary response test and nulls for the unavailable-field test. Update `insertConstituency` so the allocation used by the fixture is explicit.

- [ ] **Step 7: Run the focused test**

Run:

```sh
./mvnw -Dtest=ElectionResultIntegrationTest,ElectionResultSnapshotSqlTest test
```

Expected: PASS, including the PostgreSQL migration constraint, with existing endpoint and coalition tests unchanged.

- [ ] **Step 8: Commit only Task 1 files**

```sh
git add src/main/resources/db/migration/V59__add_election_party_ballot_components.sql
git add src/main/java/dev/maboullaite/fhemni/election/ElectionResultRepository.java
git add src/main/java/dev/maboullaite/fhemni/election/ElectionResultService.java
git add src/test/java/dev/maboullaite/fhemni/election/ElectionResultSnapshotSqlTest.java
git add src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java
git commit -m "feat: expose election ballot components"
```

## Task 2: Build and test ballot projections

**Files:**

- Create: `src/main/resources/static/js/election-insights.js`
- Create: `src/test/js/election-insights.test.js`

- [ ] **Step 1: Add a complete 28-entry test fixture**

Use the exact approved rows, including:

```js
[
  ['PAM', 1043781, 1065371, 2109152, 85, 12, 97],
  ['PI', 751672, 753944, 1505616, 52, 13, 65],
  ['PJD', 696031, 730315, 1426346, 44, 10, 54],
  ['RNI', 718630, 640468, 1359098, 57, 9, 66],
  ['MP', 356172, 346299, 702471, 20, 9, 29],
  ['USFP', 327741, 330284, 658025, 15, 11, 26],
  ['PPS', 247154, 241141, 488295, 10, 9, 19],
  ['UC', 225121, 219599, 444720, 13, 4, 17],
  ['MDS', 170472, 177861, 348333, 4, 4, 8],
  ['FGD', 134379, 136982, 271361, 3, 5, 8],
  ['FFD', 45988, 45158, 91146, 0, 2, 2],
  ['ND', 28882, 24161, 53043, 0, 1, 1],
  ['PE', 21551, 23405, 44956, 1, 1, 2],
  ['PML', 18816, 16451, 35267, 0, 0, 0],
  ['PUD', 24731, 4983, 29714, 1, 0, 1],
  ['PEDD', 15349, 14148, 29497, 0, 0, 0],
  ['UMD', 12866, 12615, 25481, 0, 0, 0],
  ['PDN', 12932, 11957, 24889, 0, 0, 0],
  ['PLJS', 6872, 7910, 14782, 0, 0, 0],
  ['ALAMAL', 7267, 6236, 13503, 0, 0, 0],
  ['PCI', 6347, 6301, 12648, 0, 0, 0],
  ['PVM', 6510, 4947, 11457, 0, 0, 0],
  ['PCS', 5412, 5910, 11322, 0, 0, 0],
  ['PA', 3213, 5319, 8532, 0, 0, 0],
  ['ANNAHDA', 5241, 2888, 8129, 0, 0, 0],
  ['PSD', 3544, 1827, 5371, 0, 0, 0],
  ['PRD', 2529, 1669, 4198, 0, 0, 0],
  ['IND', 1174, 0, 1174, 0, 0, 0]
]
```

The fixture must total 4,900,377 local, 4,838,149 regional, 9,738,526 combined, and 395 seats.

- [ ] **Step 2: Write failing audit tests**

Cover all of these independently:

- complete fixture is available;
- 27 rows is unavailable;
- one missing component is unavailable;
- one negative value is unavailable;
- one row-level sum mismatch is unavailable;
- one denominator mismatch is unavailable;
- a non-`FINAL`/`CORRECTED` election is unavailable while the tab remains discoverable;
- `PND` instead of `ND` is unavailable;
- `ND` and `PDN` stay separate;
- `IND.regionalVotes = 0` remains present and valid;
- the input snapshot is not mutated.

- [ ] **Step 3: Write failing ballot projection tests**

Assert:

- local and regional shares use different denominators;
- initial rows are the leading ten by combined votes;
- alternate sort modes use local or regional values and break ties by party code;
- the RNI/PJD raw reversal inputs are 22,599, 89,847, and 67,248;
- comparison rows expose `ballotShare`, `seatShare`, and neutral `difference` values;
- USFP's ballot and seat values retain their correct relative order, with no renderer-specific patch;
- tied seat totals receive a stable shared rank and then sort by party code;
- zero-seat entries remain present when expanded;
- concentration groups use fixed ballot-order membership and return the approved top-four/top-ten/remaining/zero-seat totals.

- [ ] **Step 4: Run the new test and confirm failure**

```sh
node --test src/test/js/election-insights.test.js
```

Expected: module-not-found or missing-export failures.

- [ ] **Step 5: Implement the browser/CommonJS module shell**

Follow the `election-region-filters.js` export pattern. Use integer validation and raw arithmetic only. Do not read `document`, `window.location`, locale, or formatting APIs in this module.

- [ ] **Step 6: Implement the ballot functions minimally**

Implement `auditSnapshot`, `deriveBallotComponents`, `deriveBallotSeatComparison`, and `deriveConcentration`. Return diagnostics such as `party-count`, `missing-component`, `row-total`, `local-total`, `regional-total`, and `canonical-code` rather than localized sentences.

- [ ] **Step 7: Run the ballot tests**

```sh
node --test src/test/js/election-insights.test.js
```

Expected: all ballot/audit tests PASS.

- [ ] **Step 8: Commit**

```sh
git add src/main/resources/static/js/election-insights.js
git add src/test/js/election-insights.test.js
git commit -m "feat: derive election ballot insights"
```

## Task 3: Build and test geography, constituency, and representative projections

**Files:**

- Modify: `src/main/resources/static/js/election-insights.js`
- Modify: `src/test/js/election-insights.test.js`

- [ ] **Step 1: Extend the fixture to the complete final shape**

Add 12 final regions, 14 represented party entries, explicit region allocations totaling 395, 305 local winners across 92 constituencies, and 90 regional-list winners. Keep at least one missing winner vote value and one published zero/value to test the distinction.

- [ ] **Step 2: Write failing geography tests**

Assert:

- `buildRegionMatrix` returns 12 rows × 14 represented parties and fills absent region-party combinations with genuine zero seats;
- the matrix total is 395 and uses a single global maximum;
- `deriveRegionDelegation` reports exact region share, represented-party count, delegation size, and all parties tied for largest delegation;
- `derivePartyGeography` includes all 12 regions, number of represented regions, national local/regional components, and distinct local-constituency breadth from unique identifiers;
- no region or party projection is available if the regional matrix does not reconcile to 395.

- [ ] **Step 3: Write failing constituency tests**

Assert the five bins are exactly:

```js
[
  { seats: 2, constituencies: 21 },
  { seats: 3, constituencies: 38 },
  { seats: 4, constituencies: 22 },
  { seats: 5, constituencies: 5 },
  { seats: 6, constituencies: 6 }
]
```

Also assert 92 unique constituencies, 305 allocated seats, explicit `allocatedSeats` consistency for every duplicate winner occurrence, and fail-closed behavior for missing/contradictory allocation metadata.

- [ ] **Step 4: Write failing representative-index tests**

Assert:

- 395 records: 305 local and 90 regional-list;
- local entries retain constituency and region;
- regional-list entries retain region and have no invented constituency;
- missing vote remains `null`, not `0`;
- filters compose across text, region, constituency, party, and seat type;
- repeated candidate identities are detected as a diagnostic instead of silently duplicated.

- [ ] **Step 5: Run and confirm failure**

```sh
node --test src/test/js/election-insights.test.js
```

- [ ] **Step 6: Implement the remaining pure functions**

Implement `buildRegionMatrix`, `deriveRegionDelegation`, `derivePartyGeography`, `deriveConstituencyDistribution`, and `indexRepresentatives`. Use maps/sets built from identifiers, not localized display names. Preserve stable code and source ordering for ties.

- [ ] **Step 7: Run all JavaScript tests**

```sh
node --test src/test/js/*.test.js
```

Expected: the existing region-filter tests and all new insight tests PASS.

- [ ] **Step 8: Commit**

```sh
git add src/main/resources/static/js/election-insights.js
git add src/test/js/election-insights.test.js
git commit -m "feat: derive election atlas geography"
```

## Task 4: Add the Graphs tab shell and lifecycle integration

**Files:**

- Modify: `src/main/resources/static/election-results.html`
- Modify: `src/main/resources/static/js/election-results.js`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java`

- [ ] **Step 1: Write failing static-page assertions**

Assert the page contains:

- `electionGraphsTab` between National and Coalition;
- `electionGraphsPanel` with `role="tabpanel"` and `#graphs` support;
- five section anchors and four local jump links;
- a live status element for Atlas filter/result updates;
- `/js/election-insights.js` before `/js/election-results.js`;
- no inline `style` or inline event handlers;
- no removed internal provenance paragraph text.

- [ ] **Step 2: Write failing controller-source assertions**

Assert `graphs` is in the valid tab-name list, the Atlas renderer is called from the existing `render()` path, and there is still only one results fetch URL and one polling scheduler.

- [ ] **Step 3: Run focused tests and confirm failure**

```sh
./mvnw -Dtest=NavigationConsistencyTest,ElectionResultIntegrationTest test
```

- [ ] **Step 4: Add semantic HTML shells**

Add the Graphs tab button after National. Add a full-width panel with:

- title/intro and jump navigation;
- `electionGraphBallots`;
- `electionGraphRepresentation`;
- `electionGraphGeography`;
- `electionGraphConstituencies`;
- `electionGraphRepresentatives`;
- one `aria-live="polite"` Atlas status node.

Leave detailed rows/controls to JavaScript so the server page remains a single shell.

- [ ] **Step 5: Load the insights module**

Insert a versioned script tag immediately before `election-results.js`. Bump the page-controller cache key once for the complete feature, not on every intermediate edit.

- [ ] **Step 6: Add localized copy and durable Atlas state**

Add Arabic, French, and English copy for all section headings, legends, controls, takeaways, unavailable states, captions, filter labels, active-filter chips, reset, pagination/disclosure, and missing-vote text.

Create one controller-level state object:

```js
const atlasState = {
  ballots: { expanded: false, query: '', order: 'combined' },
  representation: { expanded: false, query: '', order: 'ballots' },
  geography: { mode: 'region', regionCode: '', partyCode: '' },
  constituencySeats: null,
  representatives: {
    query: '', regionCode: '', constituencyCode: '', partyCode: '',
    seatType: 'all', page: 1
  }
};
```

Do not recreate it inside `render()`, `load()`, locale-change, or poll callbacks.

- [ ] **Step 7: Integrate hash and keyboard tab behavior**

Add `graphs` to both tab arrays. Preserve existing fragments and analytics. Ensure locale reload retains `#graphs` and active controls.

Make the active tab the only tab with `tabindex="0"`; all others use `-1`. Add ArrowLeft/ArrowRight navigation that follows document direction, plus Home/End. Moving with those keys focuses and activates the destination tab without producing a second listener on rerender.

- [ ] **Step 8: Run focused tests**

```sh
./mvnw -Dtest=NavigationConsistencyTest,ElectionResultIntegrationTest test
```

- [ ] **Step 9: Commit only shell/lifecycle files**

```sh
git add src/main/resources/static/election-results.html
git add src/main/resources/static/js/election-results.js
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git add src/test/java/dev/maboullaite/fhemni/web/ElectionResultIntegrationTest.java
git commit -m "feat: add election atlas tab"
```

## Task 5: Render ballot and representation sections

**Files:**

- Modify: `src/main/resources/static/js/election-results.js`
- Modify: `src/main/resources/static/css/app.css`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`

- [ ] **Step 1: Add failing structural assertions**

Assert the controller source contains dedicated render functions for ballot components and ballot/seat comparison, references the pure module exports, and uses controls with labels rather than anonymous click targets. Assert the stylesheet contains the expected semantic Atlas class hooks without inline widths.

- [ ] **Step 2: Run the focused static test and confirm failure**

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 3: Render “Two ballots, different results”**

Use `deriveBallotComponents(snapshot)` and render:

- visible local and regional denominators;
- initial top ten;
- search;
- order by combined/local/regional;
- working “Show all 28”/collapse control;
- paired bars using the existing CSP-safe width-class helper rather than a runtime `style` attribute;
- exact count/share text outside the bar;
- symbols/codes/names with `<bdi>` where scripts mix;
- a disclosure containing combined totals and a semantic figure table;
- the deterministic RNI/PJD arithmetic takeaway.

If unavailable, replace only this section's data area with the localized concise unavailable state and diagnostics-hidden-from-users; do not render partial bars.

- [ ] **Step 4: Render “Ballots and parliamentary representation”**

Use `deriveBallotSeatComparison(snapshot)` and `deriveConcentration(snapshot)` to render:

- initial top ten and complete 28-entry expansion;
- party search;
- order by combined ballots or total seats;
- circle/square markers plus labels;
- exact ballot count/share, seat count/share, local/regional seat components, and neutral percentage-point difference;
- a complete semantic “View figures” disclosure generated from the same ordered rows;
- deterministic top-four/top-ten/remaining/zero-seat takeaway.

Do not use “gain,” “loss,” “advantage,” “fairness,” “efficiency,” or proportional-entitlement wording.

- [ ] **Step 5: Preserve focus and controls during rerender**

Use stable `data-atlas-key` values. Before replacing a section, capture the active control key and selection; restore focus and selection afterward only when the user was inside that section. Do not move focus during background polling.

- [ ] **Step 6: Add CSS using logical properties**

Style paired bars, markers, exact-value columns, controls, disclosure tables, and takeaways. Bars grow from `inline-start`; do not hardcode left/right. Use existing party colors plus shape/text redundancy.

- [ ] **Step 7: Run JavaScript and static tests**

```sh
node --test src/test/js/*.test.js
```

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 8: Commit**

```sh
git add src/main/resources/static/js/election-results.js
git add src/main/resources/static/css/app.css
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git commit -m "feat: visualize ballots and representation"
```

## Task 6: Render the unified geography explorer

**Files:**

- Modify: `src/main/resources/static/js/election-results.js`
- Modify: `src/main/resources/static/css/app.css`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`

- [ ] **Step 1: Add failing structural assertions**

Assert region, party, and all-figures modes exist; region and party selectors have explicit labels; the matrix has a caption; and representative actions use visible filters.

- [ ] **Step 2: Run and confirm failure**

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 3: Implement By region**

Use `deriveRegionDelegation`. Default to the first valid region only when no preserved selection exists. Show all represented parties, exact seats, share of that region's full allocation, delegation size, represented-party count, and every party tied for largest delegation.

Begin the view with one generated takeaway and an explicit regional-delegation denominator.

- [ ] **Step 4: Implement By party**

Use `derivePartyGeography`. Default to the first represented party only when no preserved selection exists. Show all 12 regions including zeroes, share of the selected party's national seats, number of represented regions, national local/regional seat components, and distinct local-constituency breadth.

Begin the view with one generated breadth takeaway and an explicit selected-party national-seat denominator.

- [ ] **Step 5: Implement All figures**

Use `buildRegionMatrix`. Render a semantic table with 12 regions × 14 represented entries, exact numbers in every cell, visible zeroes, a caption, row/column headers, and one fixed set of CSP-safe intensity classes based on the global matrix maximum. Put the complete mobile matrix inside a disclosure so it never creates page-level horizontal overflow.

Provide a complete “View figures” disclosure for the currently selected region or party as well; it uses the same projection as the visible list and includes its denominator.

- [ ] **Step 6: Wire “See representatives”**

The action updates visible representative filters and scrolls/focuses the representative heading. It must not change Map-tab or Coalition state.

- [ ] **Step 7: Style all three modes**

Use the approved full-width card layout. On narrow screens, default to list modes; keep the complete matrix available in its disclosure.

- [ ] **Step 8: Run focused tests**

```sh
node --test src/test/js/*.test.js
```

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 9: Commit**

```sh
git add src/main/resources/static/js/election-results.js
git add src/main/resources/static/css/app.css
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git commit -m "feat: add election geography explorer"
```

## Task 7: Render constituency size and the 395-person directory

**Files:**

- Modify: `src/main/resources/static/js/election-results.js`
- Modify: `src/main/resources/static/css/app.css`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`

- [ ] **Step 1: Add failing structural assertions**

Assert the constituency section includes an exact-values table/list and the representative section includes search, region, constituency, party, seat-type, result count, active chips, reset, and pagination/progressive disclosure hooks.

- [ ] **Step 2: Run and confirm failure**

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 3: Render the five constituency bins**

Use `deriveConstituencyDistribution`. Compute bar height from a fixed SVG plotting height and the largest count, placing geometry in SVG attributes rather than inline styles. Keep the SVG decorative and provide the complete five-bin values in an adjacent semantic “View figures” disclosure. Label each bin with both seat size and constituency count. State that height counts constituencies. Selecting a bin reveals its complete constituency list and a visible action that applies that bin to the representative explorer.

Begin the section with one generated distribution takeaway and the 92-constituency/305-local-seat denominator statement.

If the audit is not exactly 92 constituencies/305 seats, render the localized unavailable state instead of partial bins.

- [ ] **Step 4: Render all representative filters**

Use `indexRepresentatives`. Filter in raw normalized text/code fields, then format. Compose filters rather than replacing each other. Update result count and removable chips. Reset returns all 395 when the snapshot is complete.

The visible 395-person/local-versus-regional result summary is this section's generated takeaway and denominator statement; do not add a second editorial claim.

- [ ] **Step 5: Render desktop table and mobile cards from one record list**

Desktop columns: name, party/list, seat type, region, constituency, published winner vote. Mobile cards retain the same essential fields. For regional-list members, show region and no invented constituency. For absent vote values, show localized “Not published.”

- [ ] **Step 6: Add progressive disclosure**

Render a bounded first page (for example 25) and a working next/load-more control with an announced result count. Changing filters returns to page 1. Never hide records permanently behind a visual-only control.

- [ ] **Step 7: Run focused tests**

```sh
node --test src/test/js/*.test.js
```

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 8: Commit**

```sh
git add src/main/resources/static/js/election-results.js
git add src/main/resources/static/css/app.css
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git commit -m "feat: add constituency and representative explorers"
```

## Task 8: Complete responsive, RTL, accessibility, and polling behavior

**Files:**

- Modify: `src/main/resources/static/css/app.css`
- Regenerate: `src/main/resources/static/css/dist.css`
- Modify: `src/main/resources/static/js/election-results.js`
- Modify: `src/main/resources/static/election-results.html`
- Modify: `src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java`

- [ ] **Step 1: Add regression assertions**

Add checks for:

- scrollable tab strip below 640 px;
- full-width stacked Atlas sections;
- logical properties/RTL selectors;
- reduced-motion handling;
- matrix disclosure/mobile cards;
- focus-visible styles;
- no extra endpoint URL or polling interval;
- Graphs state not initialized inside render/load functions;
- roving tab `tabindex` plus Arrow/Home/End handling;
- no external chart script, CSP relaxation, runtime style attribute, or inline handler;
- no removed internal provenance sentence.

- [ ] **Step 2: Run and confirm any missing behavior**

```sh
./mvnw -Dtest=NavigationConsistencyTest test
```

- [ ] **Step 3: Finish responsive CSS**

Verify layouts at 1440, 900, 768, 640, and 320 CSS pixels. Use label → plot → exact values on narrow rows. Prevent page-level horizontal overflow. At 200% zoom, every control and exact value remains reachable. Coarse-pointer controls are approximately 44 px.

- [ ] **Step 4: Finish keyboard and screen-reader behavior**

Verify tab order, explicit labels, captions, heading hierarchy, `aria-expanded`, `aria-controls`, result announcements, reset/filter chips, and focus return after disclosure changes. Decorative bars/markers are `aria-hidden`; adjacent text/table holds the data.

- [ ] **Step 5: Finish locale/RTL behavior**

Verify Arabic RTL, French, and English text. Use `<bdi>` around codes, mixed-script names, and isolated numeric phrases. Ensure locale changes preserve Graphs hash and control state.

- [ ] **Step 6: Verify polling behavior by inspection and targeted test**

Confirm the existing `load({ fresh: true })` path reruns Atlas projections from the updated snapshot without resetting `atlasState`, focus, or scroll. A transient refresh failure must retain the previous valid Atlas and use the existing stale-data notice. Confirm no new `fetch`, `setInterval`, or timeout loop was added for Atlas.

- [ ] **Step 7: Regenerate compiled CSS**

```sh
./scripts/build-css.sh
```

- [ ] **Step 8: Run all local tests**

```sh
node --test src/test/js/*.test.js
```

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

- [ ] **Step 9: Commit the completed UI**

```sh
git add src/main/resources/static/css/app.css
git add src/main/resources/static/css/dist.css
git add src/main/resources/static/js/election-results.js
git add src/main/resources/static/election-results.html
git add src/test/java/dev/maboullaite/fhemni/web/NavigationConsistencyTest.java
git commit -m "fix: harden election atlas accessibility"
```

## Task 9: Add ballot components to the private replayable snapshot

**Files:**

- Modify but never commit/push: `/private/tmp/fhemni-election-results/data/elections/2026/results.sql`
- Consult: `/private/tmp/fhemni-election-results/docs/operations/election-results.md`

- [ ] **Step 1: Re-open the runbook and current operational header**

Confirm the current final snapshot is still revision `2026-09-26 19:03:00+00`, the exact evidence URL is the Médias24 28-entry table, public labels/link remain fixed, and production has not advanced beyond the local snapshot.

- [ ] **Step 2: Record the identity/timestamp decision in comments**

Preserve the raw source revision as `26/09/2026 20:03 Africa/Casablanca` and its stored UTC equivalent `2026-09-26 19:03:00+00`. Document that source `PND` maps to canonical `ND`, remains distinct from `PDN`, and source `Parti du travail`/`حزب العمل` maps to canonical `PA — Parti de l’Action` by identity, not by matching total.

- [ ] **Step 3: Extend all 28 national rows**

Add `local_votes` and `regional_votes` to the `election_party_results` insert column list and populate every row with the complete source table. Preserve all existing `votes`, seat figures, regions, constituencies, and winners unchanged.

- [ ] **Step 4: Add transactional validation blocks**

Before `COMMIT`, make the SQL raise on:

```sql
count(*) <> 28
sum(local_votes) <> 4900377
sum(regional_votes) <> 4838149
sum(votes) <> 9738526
votes <> local_votes + regional_votes
```

Also assert canonical `ND`, `PDN`, `PA`, and `IND` rows exist; `PND` does not; and `IND.regional_votes = 0`.

- [ ] **Step 5: Validate against a disposable migrated database**

Apply all Flyway migrations to a disposable database, replay the private `results.sql`, and query the five audit totals plus all 28 row-level sums. A failed assertion must roll back the whole transaction.

- [ ] **Step 6: Keep the artifact private**

Run `git status --short` in `/private/tmp/fhemni-election-results` and confirm the SQL file remains modified/untracked only in the operational workspace. Do not stage, commit, or push it.

## Task 10: Review, deploy, back up, and verify production

**Files:** No new product files; production operation only after Tasks 1–9 pass review.

- [ ] **Step 1: Review the full diff against the approved spec**

Check every requirement and non-goal. Pay special attention to the existing National-tab vote-total/count/share edits so the Atlas work does not remove them. Run:

```sh
git diff --check
```

```sh
git status --short
```

- [ ] **Step 2: Run the complete verification suite again**

```sh
node --test src/test/js/*.test.js
```

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

- [ ] **Step 3: Perform browser QA**

With a final-snapshot API response, verify at 1440, 900, 768, 640, and 320 px in Arabic, French, and English:

- all 28 entries are reachable in both ballot panels;
- the three geography modes reconcile and link into visible representative filters;
- constituency bins show 92/305;
- the directory shows 395 and preserves missing votes as unpublished;
- hash navigation, keyboard navigation, focus, RTL, reduced motion, 200% zoom, and polling refresh behave as specified;
- there is no internal provenance paragraph.

- [ ] **Step 4: Deploy the reviewed application change first**

Use the existing production release process so Flyway V59 completes before the operational snapshot attempts to write the new columns. Verify the production schema exposes `local_votes` and `regional_votes` and the API remains healthy with null components during the short transition.

- [ ] **Step 5: Confirm production has not changed unexpectedly**

Compare the current API and PostgreSQL at `root@49.13.9.198` with the complete private snapshot. Stop with no write on a newer source timestamp, changed totals, source ambiguity, schema mismatch, or invariant failure.

- [ ] **Step 6: Create the required timestamped production backup**

Using the production host's existing secure database access, save a small data-only backup containing exactly:

- `elections`
- `election_regions`
- `election_party_results`
- `election_region_party_results`

Record its explicit host path and UTC timestamp. Do not copy credentials into the repository or logs.

- [ ] **Step 7: Apply the private snapshot transactionally**

Apply `/private/tmp/fhemni-election-results/data/elections/2026/results.sql` through the runbook's secure PostgreSQL path. Do not make a partial manual update. Any error must roll back.

- [ ] **Step 8: Verify database and public read model**

Query PostgreSQL and verify 28/4,900,377/4,838,149/9,738,526 plus all row sums. Then verify:

```sh
curl --fail --silent 'https://fhemni.ma/api/catalog/elections/2026/results?lang=ar'
```

```sh
curl --fail --silent 'https://fhemni.ma/elections/2026'
```

Confirm the API contains every component, public source labels/link remain fixed, regional rows total no more than 395, all five Atlas sections render, and the page makes no extra results request.

- [ ] **Step 9: Report the production result**

Include the exact Médias24 evidence URL and `26/09/2026 20:03 Africa/Casablanca` / `2026-09-26 19:03:00+00` timestamp, the 28-entry component totals added, the application/API/page verification outcome, and the backup path. If any post-write public verification fails, stop, report clearly, and use the saved backup/runbook recovery path rather than applying ad-hoc fixes.

## Completion Criteria

- All Node and Maven verification commands pass from a clean implementation diff.
- The National tab still shows its approved total and raw count/share enhancements.
- The Graphs tab contains exactly the five approved sections and no removed/duplicate panels.
- All 28 ballot rows, 12 regions, 92 constituencies, 305 local winners, 90 regional-list winners, and 395 representatives reconcile from the single API snapshot.
- Incomplete/mismatched ballot components produce a concise unavailable state, never a partial visualization.
- Production is backed up and verified, and the private replayable snapshot is updated but never committed or pushed.
