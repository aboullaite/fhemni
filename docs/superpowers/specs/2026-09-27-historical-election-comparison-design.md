# Historical legislative election comparison

## Outcome

Add a separate public page at `/elections/history` that lets readers compare the official elections.ma legislative records for 2016, 2021, and 2026. Keep the existing 2026 results page intact and link the two experiences.

The historical page answers five questions in order:

1. What changed nationally between two selected elections?
2. Which parties gained or lost seats?
3. How did a selected party move over all available elections?
4. Which regions changed their local-seat composition?
5. How did turnout and the published age, gender, and education profile of elected members change?
6. Which elected names recur across elections, and which official party abbreviations are recorded for them?

## Evidence contract

- The only public result source is `elections.ma`.
- Ship the user-provided official archive as a pinned, checksummed source snapshot and generate the browser payload from it.
- Preserve local, regional-list, and 2016 national-list ballots as distinct measures. Never add them and describe the sum as unique voters.
- National seats must reconcile to 395 for each election and ballot seats must reconcile to 305 + 90.
- Regional comparisons use local seats only. The 2016 national list cannot be assigned to regions, and the page must say so.
- Party continuity is explicit. Stable official abbreviations are compared as the same party. Coalitions and alliances with different source identities remain separate unless a reviewed lineage mapping says otherwise.
- Elections.ma records do not provide a cross-election person ID. Repeated names are presented as exact, unique name matches in the official elected rosters, never as proven identity or as a dated party switch.
- Missing records remain missing; they never become zero.

## Architecture

### Data pipeline

Vendor the canonical combined elections.ma archive at `data/elections/history/morocco-legislative-results.json`, together with a manifest containing its SHA-256 and source URLs. A Node generator verifies the source digest and all election invariants, then writes a compact public payload to `src/main/resources/static/data/elections/history.json`.

The payload contains:

- election metadata, source URLs, turnout, ballot totals, and capability flags;
- canonical party observations and national seats/votes by ballot;
- local-seat vectors by region and election;
- national turnout and only directly reported demographic percentages with their literal source text; inferred counts and percentages without source text stay out of the public payload;
- all official elected-candidate observations;
- deterministic repeated-name groups whose normalized name is unique within each election, marked `name_match_only`;
- complete local-constituency vote and official-seat observations for 2016 and 2026, retained only for a validated electoral-quotient counterfactual;
- generation metadata and source digest.

The checked-in generated payload makes deployment deterministic. Tests rerun the generator against the pinned source and verify byte-for-byte output.

### Page

Create `historical-elections.html`, `historical-elections.js`, and pure analysis helpers in `historical-election-insights.js`. Reuse the shared language mechanism and Fhemni visual tokens. The page owns no live-result polling or coalition state.

The page contains:

- a compact hero, earlier/later election selectors, and source/coverage summary;
- three national summary cards;
- anchor jump navigation;
- a diverging party-seat-change view with search, sorting, show-all, and exact figures;
- a focused party trajectory across all three elections;
- a counterfactual 2026 local-seat simulation under the 2016 rules, positioned after the party trajectory, with exact national deltas and a constituency-level calculation inspector;
- a region selector with before/after local-seat composition;
- turnout and sparse, directly reported elected-member demographic trajectories with provenance labels;
- a repeated-name elected-record explorer with party-change filter and 25-row pagination;
- a source and methodology disclosure with exact elections.ma links and the archive revision.

State is represented in the URL with `from`, `to`, and section-specific parameters. Unsupported values fall back visibly to the newest valid pair.

### Electoral-quotient proof gate

The primary counterfactual covers the 305 local seats, which are comparable constituency by constituency. Before any 2026 simulated local figure is exposed, the allocator must reproduce all 92 official 2016 local constituency allocations and all 305 seats exactly from the pinned elections.ma archive. The verified rule is a 3% threshold on valid votes, followed by a quotient calculated from eligible-list votes and largest-remainder allocation.

The page may also show a clearly separated 395-seat scenario. For that view only, it aggregates 2026 regional-list votes nationally and treats them as a hypothetical 2016-style national list. This is an additional modelling assumption, not a claim that the regional and national ballot structures are identical. The 90-seat stage has its own proof gate: the allocator must reproduce all 24 official 2016 national-list party allocations and all 90 seats exactly. The table decomposes official local, simulated local, official regional, hypothetical national, official total, and simulated total so readers can see where the assumption enters. An unresolved seat-boundary tie or any provenance/reconciliation failure makes the affected simulation unavailable; the client never invents a tie-break.

### Routing and security

- `PageController` forwards `/elections/history` to the new page.
- The page and generated data file are public in security configuration.
- Add contextual links between `/elections/2026` and `/elections/history`; do not add a fifth Atlas tab.

## Localization and accessibility

- Full Darija, French, and English interface copy.
- Preserve official Arabic names from elections.ma; do not invent translations.
- Use semantic sections, visible labels, native controls, tables with captions, `<bdi>` around names/codes/numbers, 44px targets, and polite status announcements.
- Charts are CSS/SVG-enhanced presentations of adjacent exact text, not the only source of meaning.
- On small screens, controls stack and representative records become chronological cards. No horizontal page scrolling.
- Language changes preserve filters and rerender in place.

## Testing and release gates

- Generator tests: pinned digest, 3 elections, 395 elected records per election, 305/90 seat split, ballot vote totals, unique keys, and deterministic payload.
- Insight tests: party deltas, alliance separation, regional local-only comparison, repeated-name evidence status, sorting, pagination, and null handling.
- Java integration tests: route, public access, static payload, source attribution, and page structure.
- Render checks at desktop and mobile widths in Arabic, French, and English.
- Full JavaScript suite, Maven verification under SDKMAN Java 26, and clean diff checks before review.

## Deferred work

- Verified person identity links and a true party-affiliation transition matrix.
- Constituency comparisons, until a reviewed boundary crosswalk exists.
- Historical urban/rural analysis, because the current public-source scope is elections.ma only.
- Maps, exports, and multi-party trajectory overlays.
