# Election-History Social Campaign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce five verified Fhemni X graphics, one global Instagram Story, and a five-post Darija X thread promoting the historical election comparison.

**Architecture:** A pure data projector derives a small campaign model from the checked-in historical snapshot and the existing insight engines. A standalone repository-native HTML/CSS/JavaScript composition renders six fixed-size cards, and a CDP exporter captures them as PNGs only after fonts and data are ready. Machine-readable copy and a manifest bind every claim to an asset; Node tests pin the numbers, qualifiers, dimensions, and platform limits.

**Tech Stack:** Node.js 23+, `node:test`, existing historical insight modules, HTML/CSS/SVG, Chrome DevTools Protocol, Tajawal fonts, PNG.

**Spec:** `docs/superpowers/specs/2026-09-27-election-history-social-campaign-design.md`

## Global Constraints

- Export five X images at exactly 1600 x 900 pixels and one Instagram Story at exactly 1080 x 1920 pixels.
- Use cream `#F5F1E8`, ivory `#FFFDF7`, ink `#132C2B`, teal `#176B63`/`#0B4F49`, orange `#E86F3C`, and loss red `#BC4B51`.
- Use the checked-in Tajawal fonts and the transparent Fhemni logo; the logo is 220 pixels wide on X images and 230 pixels wide on the Story.
- Keep all visual copy Darija-first, right-to-left, with Latin numerals and party abbreviations isolated.
- Put `المصدر: elections.ma · التحليل: فهّمني` on every asset.
- Do not include turnout or participation.
- Treat ballot vote totals as ballot votes, not unique voters.
- Label regional comparisons `المقاعد المحلية فقط`.
- Do not silently merge coalitions or party labels.
- Identify the quotient comparison with `محاكاة، ماشي نتيجة رسمية`.
- Describe repeated names as exact name matches, never verified identity or a verified party switch.
- Keep every X post at or below 280 Unicode code points and use at most two hashtags in the closing post.

## Review Focus

- **Historical snapshot or insight audit fails:** campaign generation must stop with diagnostics and must not retain an older output file; Task 1 pins this.
- **A party label is absent in one endpoint year:** zero is used only after the existing complete-roster audit establishes absence, and distinct source labels remain distinct; Task 1 pins this.
- **Arabic text, Latin abbreviations, or large values overflow:** each card must preserve RTL isolation and stay within its fixed viewport; Tasks 2 and 3 pin DOM direction, dimensions, and clipping checks.
- **Fonts, logo, or campaign data are not ready at capture time:** export must wait for `document.fonts.ready` and an explicit render-ready flag, then fail on missing assets; Task 3 pins this.
- **Thread copy and images drift apart:** every post references one existing asset, every asset has alt text, and the manifest records the verified data digest and PNG dimensions; Task 4 pins this.

---

### Task 1: Verified campaign data projection

**Files:**
- Create: `scripts/social/election-history-campaign-data.js`
- Create: `scripts/social/generate-election-history-campaign-data.mjs`
- Create: `src/test/js/election-history-social-data.test.js`
- Create: `docs/social/election-history-launch/campaign-data.json`

**Interfaces:**
- Consumes: `auditHistoricalPayload`, `derivePartyDeltas`, `deriveRegionComparison`, and `deriveDemographicTrends` from `historical-election-insights.js`; `derive2026Full2016SystemCounterfactual` from `historical-electoral-quotient.js`; the checked-in `history.json` bytes.
- Produces: `buildCampaignData(payload, { insights, quotient, sourceSha256 }) -> CampaignData`, `writeCampaignData({ sourcePath, outputPath }) -> CampaignData`, and a deterministic `campaign-data.json` with `overview`, `partyMovement`, `region`, `demographics`, `quotient`, and `provenance` objects.

- [ ] **Step 1: Write the failing projection tests**

Add tests named:

- `campaign projection fails closed when the historical audit fails`
- `campaign projection preserves exact-label party gains and losses for 2021 to 2026`
- `campaign projection labels Casablanca-Settat as local seats only`
- `campaign projection preserves reported women percentages without inferred counts`
- `campaign projection preserves the full-house simulation qualifier and 395-seat totals`

Assert the following dataset anchors:

```js
assert.deepEqual(data.overview, {
  years: [2016, 2021, 2026],
  electedRecordCount: 1185,
  electionCount: 3
});
assert.deepEqual(data.partyMovement.gains.slice(0, 2).map(row =>
  [row.abbreviation, row.earlierSeats, row.laterSeats, row.delta]), [
  ['PJD', 13, 54, 41],
  ['PAM', 87, 97, 10]
]);
assert.deepEqual(data.partyMovement.losses.slice(0, 3).map(row =>
  [row.abbreviation, row.earlierSeats, row.laterSeats, row.delta]), [
  ['RNI', 102, 66, -36],
  ['PI', 81, 65, -16],
  ['USFP', 34, 26, -8]
]);
assert.equal(data.region.regionId, '6');
assert.equal(data.region.regionNameAr, 'الدار البيضاء-سطات');
assert.equal(data.region.ballotType, 'local');
assert.deepEqual(data.demographics.women.map(point =>
  [point.year, point.percentage]), [[2016, 20.51], [2021, 24.3], [2026, 27.09]]);
assert.equal(data.quotient.officialSeatTotal, 395);
assert.equal(data.quotient.simulatedSeatTotal, 395);
assert.equal(data.quotient.qualifier, 'محاكاة، ماشي نتيجة رسمية');
assert.equal(Object.hasOwn(data, 'turnout'), false);
```

Also mutate the payload to break the complete party roster and assert `buildCampaignData` throws an error carrying the audit diagnostics before writing output.

- [ ] **Step 2: Run the projection tests to verify they fail**

Run: `node --test src/test/js/election-history-social-data.test.js`

Expected: FAIL because `election-history-campaign-data.js` does not exist.

- [ ] **Step 3: Implement the pure projection**

Implement `buildCampaignData` in `scripts/social/election-history-campaign-data.js`. Use only audited outputs from the existing insight engines. Rank party gains descending by delta and losses ascending by delta, with deterministic code-point tie breakers. Select region `6`; keep the six parties with the largest maximum local-seat observation across the three elections. Copy published demographic percentages without deriving counts. Include the full-house simulation rows with non-zero deltas and the mandatory qualifier.

- [ ] **Step 4: Implement deterministic JSON generation**

Implement `writeCampaignData` in `generate-election-history-campaign-data.mjs`. Hash the exact `history.json` bytes with SHA-256, call `buildCampaignData`, serialize with two-space indentation and a trailing newline, and replace `campaign-data.json` only after successful projection. On failure, leave no temporary file and do not overwrite an existing output.

- [ ] **Step 5: Run the focused tests and generate the JSON**

Run: `node --test src/test/js/election-history-social-data.test.js`

Expected: PASS.

Run: `node scripts/social/generate-election-history-campaign-data.mjs`

Expected: writes `docs/social/election-history-launch/campaign-data.json` and reports its source SHA-256.

- [ ] **Step 6: Commit the verified data projection**

```bash
git add scripts/social/election-history-campaign-data.js \
  scripts/social/generate-election-history-campaign-data.mjs \
  src/test/js/election-history-social-data.test.js \
  docs/social/election-history-launch/campaign-data.json
git commit -m "feat: derive verified election campaign data"
```

### Task 2: Fixed-size campaign compositions

**Files:**
- Create: `docs/social/election-history-launch/campaign.html`
- Create: `docs/social/election-history-launch/campaign.css`
- Create: `docs/social/election-history-launch/campaign.js`
- Create: `src/test/js/election-history-social-page.test.js`

**Interfaces:**
- Consumes: `campaign-data.json`, `src/main/resources/static/assets/brand/fhemni-logo.png`, and checked-in Tajawal font files.
- Produces: six DOM roots with IDs `campaign-01-overview`, `campaign-02-parties`, `campaign-03-regions`, `campaign-04-profiles`, `campaign-05-quotient`, and `campaign-story`; each root sets `data-export-width`, `data-export-height`, and `data-ready="true"` after rendering.

- [ ] **Step 1: Write the failing static-contract tests**

Add tests named:

- `campaign page exposes five X cards and one global Story in narrative order`
- `campaign cards use fixed export dimensions and the approved brand tokens`
- `every card contains the Fhemni logo and elections.ma attribution`
- `regional and quotient cards retain their mandatory qualifiers`
- `campaign script isolates Latin numbers and abbreviations inside RTL copy`

Assert five 1600 x 900 roots, one 1080 x 1920 root, `dir="rtl"`, the approved color tokens, six references to the Fhemni logo, six source lines, `المقاعد المحلية فقط`, `محاكاة، ماشي نتيجة رسمية`, and explicit `<bdi>` creation for party abbreviations and numeric labels.

- [ ] **Step 2: Run the page tests to verify they fail**

Run: `node --test src/test/js/election-history-social-page.test.js`

Expected: FAIL because the campaign page does not exist.

- [ ] **Step 3: Implement the campaign shell and shared visual system**

Create `campaign.html` with the six ordered roots and local CSS/JavaScript references. In `campaign.css`, define the approved colors, local `@font-face` rules, fixed root dimensions, 80-96 pixel X safe margins, the Story safe area from y=240 to y=1640, the subtle square grid, common logo/header/footer patterns, and overflow clipping.

- [ ] **Step 4: Implement the six data-driven compositions**

In `campaign.js`, export `renderCampaign(document, data)`. Build:

- the three-election overview timeline and four feature labels;
- a shared-scale, side-by-side gain/loss bar chart for 2021 to 2026;
- the Casablanca-Settat local-seat chart with one color per year;
- three women-percentage rings and small age/education cues;
- official-versus-simulated bars for the largest quotient changes;
- the Story with one dominant compact party-movement chart and three feature cues.

Set `data-ready="true"` only after the card is populated. Set `window.__FHEMNI_CAMPAIGN_READY__ = true` only when all six roots are ready.

- [ ] **Step 5: Run the focused page tests**

Run: `node --test src/test/js/election-history-social-page.test.js`

Expected: PASS.

- [ ] **Step 6: Open the campaign sheet locally for a first visual check**

Run a local static server from the repository root and open `docs/social/election-history-launch/campaign.html` at a desktop viewport. Confirm all six cards render, the Story safe area is visible, and no application controls or long methodology notes appear.

- [ ] **Step 7: Commit the campaign compositions**

```bash
git add docs/social/election-history-launch/campaign.html \
  docs/social/election-history-launch/campaign.css \
  docs/social/election-history-launch/campaign.js \
  src/test/js/election-history-social-page.test.js
git commit -m "feat: compose election history social cards"
```

### Task 3: Reproducible PNG export and visual validation

**Files:**
- Create: `scripts/social/render-election-history-campaign.mjs`
- Create: `src/test/js/election-history-social-renderer.test.js`
- Create: `docs/social/election-history-launch/assets/01-overview.png`
- Create: `docs/social/election-history-launch/assets/02-party-movement.png`
- Create: `docs/social/election-history-launch/assets/03-regions.png`
- Create: `docs/social/election-history-launch/assets/04-profiles.png`
- Create: `docs/social/election-history-launch/assets/05-quotient.png`
- Create: `docs/social/election-history-launch/assets/instagram-story.png`

**Interfaces:**
- Consumes: a CDP browser endpoint, the locally served campaign page, and the six ready DOM roots from Task 2.
- Produces: `renderCampaignAssets({ cdpUrl, pageUrl, outputDir }) -> Promise<AssetResult[]>`, `readPngDimensions(buffer) -> { width, height }`, and six final PNGs.

- [ ] **Step 1: Write failing renderer-helper tests**

Add tests named:

- `renderer maps every campaign root to the approved filename and dimensions`
- `PNG dimension reader rejects malformed files and reports exact dimensions`
- `renderer refuses capture when fonts, logo, data, or ready flags are missing`

The first test asserts the five 1600 x 900 mappings and one 1080 x 1920 mapping. The malformed-PNG test must throw instead of accepting a zero or missing dimension.

- [ ] **Step 2: Run the renderer tests to verify they fail**

Run: `node --test src/test/js/election-history-social-renderer.test.js`

Expected: FAIL because the renderer does not exist.

- [ ] **Step 3: Implement the CDP exporter**

Implement a minimal CDP client with Node's built-in `WebSocket`. Navigate to the campaign sheet, wait for `document.fonts.ready`, `window.__FHEMNI_CAMPAIGN_READY__`, successful logo decoding, and non-empty bounding boxes. For each root, set the exact viewport, capture the element's clip rectangle, validate the PNG header dimensions, and write through a temporary path before renaming.

- [ ] **Step 4: Run focused tests and export all assets**

Run: `node --test src/test/js/election-history-social-renderer.test.js`

Expected: PASS.

Run: `node scripts/social/render-election-history-campaign.mjs --cdp http://127.0.0.1:9333 --page http://127.0.0.1:8099/docs/social/election-history-launch/campaign.html`

Expected: six PNGs with the specified filenames and dimensions.

- [ ] **Step 5: Inspect every PNG at native size**

Check all six images for clipping, RTL ordering, logo clarity, contrast, shared bar scales, safe-area placement, source attribution, and mobile-size legibility. Record any issue before the design-review handoff.

- [ ] **Step 6: Commit the exporter and first complete asset set**

```bash
git add scripts/social/render-election-history-campaign.mjs \
  src/test/js/election-history-social-renderer.test.js \
  docs/social/election-history-launch/assets
git commit -m "feat: export election history campaign assets"
```

### Task 4: Design-review loop and platform narrative

**Files:**
- Create: `docs/social/election-history-launch/thread.json`
- Create: `docs/social/election-history-launch/thread.md`
- Create: `docs/social/election-history-launch/campaign-manifest.json`
- Create: `src/test/js/election-history-social-package.test.js`
- Modify: campaign source and generated PNGs only when required by design review.

**Interfaces:**
- Consumes: the six PNGs, campaign data digest, and the approved five-post editorial sequence.
- Produces: `thread.json` with `posts[{ number, text, asset, altText }]` and `instagramStory{ asset, copy, altText }`; a human-readable `thread.md`; `campaign-manifest.json` with source digest, asset dimensions, and claim keys.

- [ ] **Step 1: Send all six exports to the design expert**

Ask the existing design-review agent to evaluate hierarchy, RTL, mobile readability, chart comparability, clipping, brand consistency, source lines, and factual qualifiers. Require blockers and non-blocking polish to be separated.

- [ ] **Step 2: Resolve every blocking design finding and re-export**

Update only the affected composition/CSS/data projection, run its focused tests, export all six assets again, and return the revised paths to the same design reviewer. Repeat until the reviewer reports no blockers.

- [ ] **Step 3: Draft the failing package tests**

Add tests named:

- `X thread has five numbered posts at or below 280 code points`
- `thread maps one accessible asset to every post`
- `closing post uses at most two hashtags and links to the historical comparison`
- `simulation and repeated-name claims retain their qualifiers`
- `manifest digest matches campaign data and every PNG has the approved dimensions`

Count X characters with `Array.from(text).length`. Assert unique post numbers 1 through 5, non-empty Darija alt text, existing asset paths, one historical-comparison URL in post 5, no turnout wording, the simulation disclaimer in post 5, and `تطابق الاسم فقط` wherever the repeated-name feature is mentioned.

- [ ] **Step 4: Run the package tests to verify they fail**

Run: `node --test src/test/js/election-history-social-package.test.js`

Expected: FAIL because the narrative and manifest files do not exist.

- [ ] **Step 5: Write the five-post Darija narrative and Story copy**

Write `thread.json` first, pairing posts 1-5 to images 01-05. Keep one idea per post: archive scope, party movement, regional differences, elected-member profiles, then quotient simulation plus the product CTA and smaller-feature mention. Generate `thread.md` from the JSON so the visible copy and tested copy cannot diverge. Add descriptive Darija alt text for each image and the Story.

- [ ] **Step 6: Write and validate the campaign manifest**

Record the source SHA-256 from `campaign-data.json`, the exact filenames and dimensions, the data keys used by each asset, and the final design-review status. Do not mark the status approved until the reviewer has no blockers.

- [ ] **Step 7: Run focused and full verification**

Run: `node --test src/test/js/election-history-social-package.test.js`

Expected: PASS.

Run: `node --test src/test/js/*.test.js`

Expected: all JavaScript tests PASS.

Run: `source /Users/maboullaite/.sdkman/bin/sdkman-init.sh && sdk use java 26.0.2-tem && ./mvnw test`

Expected: Maven test suite PASS under SDKMAN Java 26.0.2-tem.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 8: Request final whole-package review**

Have a fresh reviewer inspect the branch for factual claims, evidence qualifiers, test coverage, asset/copy drift, and output usability. Fix blockers, re-run verification, and regenerate any affected PNG.

- [ ] **Step 9: Commit the final campaign package**

```bash
git add docs/social/election-history-launch \
  src/test/js/election-history-social-package.test.js
git commit -m "feat: package election history social campaign"
```
