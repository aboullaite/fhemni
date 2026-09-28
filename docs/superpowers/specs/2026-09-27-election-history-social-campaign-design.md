# Fhemni Election-History Social Campaign Design

## Purpose

Create a launch package for Fhemni's historical Moroccan legislative-election comparison page. The package should explain the product through evidence-led visuals, feel native to Fhemni, and be immediately usable on X and Instagram without exposing the clutter of the application interface.

The audience is Moroccan voters, journalists, researchers, and politically curious readers. The campaign should invite exploration rather than tell readers what political conclusions to draw.

## Deliverables

The campaign has six final image assets and one finished X thread:

1. Five landscape images for a five-post X thread, exported at 1600 x 900 pixels.
2. One global Instagram Story, exported at 1080 x 1920 pixels.
3. A five-post Darija X thread with each post at or below 280 characters.
4. A manifest that maps each post to its image and records the verified figures used in that asset.

The campaign creates files only. It does not publish to either platform.

## Editorial sequence

### 1. Global overview

Headline: `10 سنين ديال الانتخابات فمكان واحد`

Show a clear 2016 -> 2021 -> 2026 timeline and a short feature strip for parties, regions, elected-member profiles, and the electoral-quotient simulation. The visual introduces the archive and comparison controls without reproducing the application UI.

### 2. Party gains and losses

Headline: `شكون طلع وشكون هبط بين 2021 و2026؟`

Show total-seat comparisons for the strongest gains and losses. Gains and losses appear side by side and use one shared numeric scale. The total measure includes local seats plus the national or regional-list seats reported for that election. A compact note identifies the structural change from the 2016 national list to the 2021 and 2026 regional lists.

### 3. Regional shifts

Headline: `الانتخابات ما كتبدلش بنفس الشكل فكل جهة`

Use one recognizable flagship region and compare its party seat distribution across 2016, 2021, and 2026. The graphic must say `المقاعد المحلية فقط`. It must not imply that regional or national-list seats are allocated to these regions.

### 4. Elected-member profiles

Headline: `شكون كيمثلنا؟`

Lead with the published share of women among elected members in 2016, 2021, and 2026. Age and education are represented as small feature cues rather than squeezed into the same chart. Values are percentages published by elections.ma; the campaign does not infer headcounts from them.

### 5. Electoral-quotient simulation

Headline: `واش القاسم القديم كان غادي يبدل النتيجة؟`

Compare official 2026 seats with the modeled result under the 2016 rules. The orange label `محاكاة، ماشي نتيجة رسمية` is mandatory and remains visually adjacent to the headline. The graphic links the national result to the constituency-level calculation available in the product.

Party trajectory and the repeated-name explorer are mentioned in the closing copy and call to action. The repeated-name feature is always described as exact name matching, never as verified identity or proof that an individual changed parties.

## Visual system

- Canvas: cream `#F5F1E8` with the subtle Fhemni square grid.
- Chart surface: ivory `#FFFDF7`.
- Primary ink: `#132C2B`.
- Primary teal: `#176B63` and deep teal `#0B4F49`.
- Comparison orange: `#E86F3C`.
- Loss red: `#BC4B51`, used only for negative values.
- Typography: Tajawal Bold for headlines and Tajawal Medium for labels and notes.
- Direction: Arabic right-to-left, with Latin numerals and party abbreviations isolated correctly.
- Logo: the transparent Fhemni logo appears at the top right of every asset, 220 pixels wide on X images and 230 pixels wide on the Story.
- Attribution: every asset ends with `المصدر: elections.ma · التحليل: فهّمني`.

Landscape assets use 80-96 pixel safe margins, 64-72 pixel headlines, 28-32 pixel labels, and 40-52 pixel key figures. Each asset makes one claim and uses one dominant chart. Filters, navigation, tables, long methodology notes, and application controls are excluded.

## Instagram Story

The Story is one global 1080 x 1920 composition, not a collage of five screenshots.

- Keep essential content between y=240 and y=1640 to avoid Instagram interface overlays.
- Place the logo at the top right.
- Use the hook `10 سنين ديال الانتخابات… دابا كتقدر تقارنهم`.
- Make the party gain/loss comparison the dominant central visual.
- Add three compact feature cues: `12 جهة`, `خصائص المنتخبين`, and `محاكاة القاسم`.
- Close with `2016 · 2021 · 2026` and the call to action `جرّب المقارنة فـ fhemni.ma`.

## Accuracy and evidence rules

Before export, every number is recomputed or read from the checked-in `history.json` snapshot and reconciled against the page's existing insight functions. The campaign must not rely on manually transcribed values without a programmatic assertion.

- Do not include turnout or participation.
- Treat ballot vote totals as ballot votes, not unique voters.
- Label regional comparisons as local seats only.
- Do not silently merge coalitions or parties. Party continuity follows the exact published source-label policy used by the historical page.
- Keep the 2016 national-list and 2021/2026 regional-list distinction visible whenever totals are compared.
- Always identify the electoral-quotient comparison as a counterfactual simulation.
- Describe repeated names as `تطابق الاسم فقط`; never claim a verified party switch.
- Avoid causal claims. Charts show recorded differences and modeled outcomes.

## Asset production

Build the graphics from repository-native HTML, CSS, and SVG so charts remain reproducible and text stays sharp. Use the checked-in Fhemni logo and brand colors. Export final PNGs at their native dimensions through the local browser.

Each draft goes through a design-review loop covering hierarchy, RTL correctness, mobile legibility, clipping, chart scales, source labels, and factual accuracy. A draft is not final until the design reviewer finds no blocking issue and the lead verifies the underlying figures.

## X narrative rules

- Five numbered posts, one idea per post.
- Darija-first, direct, and factual.
- No hype or unsupported superlatives.
- The first post works as a standalone hook.
- The final post links to the historical comparison page and mentions party trajectories and the exact-name explorer.
- Each post remains within 280 characters and is delivered with its final character count.
- Use no more than two relevant hashtags in the closing post.

## Acceptance criteria

- All six PNGs have the specified native dimensions.
- The Fhemni logo, source line, and correct RTL layout appear on every image.
- Visual hierarchy remains readable at mobile-feed size.
- Every displayed number is covered by an executable assertion against the checked-in dataset.
- Regional and simulated figures carry their required qualifiers.
- The Story remains readable within Instagram's safe area.
- The design reviewer approves the final exports without blocking feedback.
- The five X posts are factual, correctly paired with their assets, and at or below 280 characters.
