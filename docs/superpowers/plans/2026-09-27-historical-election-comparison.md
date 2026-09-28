# Historical legislative election comparison implementation plan

**Goal:** Ship a locally reviewable, separate historical election page backed by a pinned elections.ma archive for 2016, 2021, and 2026.

**Architecture:** A deterministic Node pipeline turns the checksummed official archive into a compact browser payload. Pure JavaScript analysis helpers power a multilingual static page, while Spring only supplies routing and public access.

## Task 1: Pin and validate the official archive

- Add the combined elections.ma JSON and a manifest under `data/elections/history/`.
- Add `scripts/elections/generate-historical-election-data.mjs`.
- Write failing generator tests for the digest, election counts, 395-seat totals, 305/90 split, ballot totals, and deterministic output.
- Generate `src/main/resources/static/data/elections/history.json`, including national turnout and only directly reported demographic percentages with literal source evidence; do not publish inferred counts.
- Verify the generated payload contains no non-elections.ma result source.

## Task 2: Build pure historical comparison analysis

- Add `src/main/resources/static/js/historical-election-insights.js`.
- Write tests for national party deltas, stable party mapping, alliance separation, trajectories, regional local-seat comparisons, repeated-name evidence labeling, filters, and pagination.
- Implement only enough analysis to pass the tests.

## Task 3: Build the historical page

- Add `historical-elections.html` and `historical-elections.js`.
- Add full Darija/French/English copy, year-pair URL state, loading/error states, national cards, party changes, trajectories, region comparison, turnout/demographic trends, elected-record explorer, and source/coverage disclosure.
- Add responsive and accessible styles to `css/app.css`, rebuild `css/dist.css`, and add structural tests.

## Task 4: Wire routing and cross-links

- Write failing Spring tests for `/elections/history`, public access, and data delivery.
- Add the `PageController` route and security allowlist.
- Add contextual links between current results and history without changing Atlas tab order.
- Update navigation consistency expectations for the new public page.

## Task 5: Verify locally

- Run all JavaScript tests.
- Run Maven verification using SDKMAN Java 26.
- Start the application locally, verify the historical page and generated data endpoint, then inspect Arabic desktop/mobile and one LTR locale.
- Leave the local server running and provide the review URL.
- Run a final code/data review, record any deferred limitations, and keep the branch unmerged for user review.

## Task 6: Add the validated 2016-rule counterfactual

- Retain complete elections.ma local-contest inputs for 2016 and 2026 in the generated browser payload.
- Implement a pure 3% threshold, eligible-vote quotient, and largest-remainder allocator with fail-closed tie handling.
- Require exact reproduction of 92/92 official 2016 constituencies and 305/305 local seats before calculating 2026.
- Add the section after party trajectory, not at the top of the page.
- Show changed national local-seat totals, exact all-party figures, and a constituency inspector with votes, eligibility, quotient seats, remainders, and simulated versus official seats.
- Label the output as Fhemni counterfactual analysis and retain elections.ma as the only public result source.
- Keep the primary comparison local-only, then add a separately labelled 395-seat scenario that aggregates 2026 regional-list votes into a hypothetical 2016 national list.
- Gate the 90-seat scenario on exact reproduction of all 24 official 2016 national-list party rows and all 90 seats, and decompose every total so regional and national list seats are never presented as the same object.
