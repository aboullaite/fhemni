> **Audit scope and attribution:** This report audits the user-provided historical archive derived from elections.ma. Source facts remain attributable to elections.ma; every cross-year comparison, normalization, identity link, and analytical conclusion is Fhemni analysis based on those official-source records.

# Fhemni historical legislative-election data audit

**Scope:** Morocco legislative elections, 2016, 2021, and 2026  
**Audit date:** 2026-09-27  
**Mode:** Read-only review; no repository, dataset, branch, or production changes  
**Public-source constraint:** Only facts defensibly attributable to `elections.ma` are approved for public attribution in the first historical product

## Executive decision

**Recommendation: conditional GO for a staged historical product; NO-GO for a bulk data replacement or automatic “party switcher” product.**

The archive is internally strong. Its checksums pass, its CSV/JSON/SQLite representations reconcile, the three elections each contain 395 elected mandates split into 305 local seats and 90 list seats, and all 92 local constituencies retain the same source identifier and seat allocation across 2016, 2021, and 2026. That supports a useful first release centered on national party composition, local-seat swing, regional local-seat change, constituency gain/loss, turnout, and local-vote-share versus local-seat-share.

There are three material limits:

1. **The list ballot changed level.** In 2016 the 90 list seats came from one national-list contest; in 2021 and 2026 they came from 12 regional-list contests. A 2016 “regional total including list seats” does not exist in the source and must not be inferred. Three-election regional comparisons must use local seats only.
2. **Much of the archive’s demographic output is derived.** All inferred demographic counts and 1,414 percentage rows without literal source percentage text must not be presented as directly reported by `elections.ma`. The safest first release omits inferred demographic counts entirely.
3. **Candidate continuity is not a source fact.** The archive has names, but no stable person identifier or date of birth. Exact normalized-name matching yields useful lower bounds, including 33 high-confidence same-constituency cases where an elected name appears under different party/list labels. These are review candidates, not automatically publishable claims that an MP “changed parties.”

Before historical ingestion, Fhemni should also close a replayability gap: the committed `origin/main` 2026 snapshot is an empty counting snapshot, while the private operational snapshot contains the complete 2026 result. The historical importer should reconcile against the operational snapshot and create a proper official-source ingestion path rather than treating the stale committed file as current truth.

## 1. Materials reviewed

### 1.1 Historical archive

Root:

`/Users/maboullaite/Downloads/morocco_legislative_all_years/`

Documentation and integrity files:

- `README.md`
- `DATA_DICTIONARY.md`
- `API_ACCESS.md`
- `SHA256SUMS.txt`

Representations:

- 11 combined CSV tables under `csv/`
- combined JSON export
- combined SQLite database
- per-year JSON exports for 2016, 2021, and 2026
- compressed raw captures under `raw/2016.json.gz`, `raw/2021.json.gz`, and `raw/2026.json.gz`
- schema and validation outputs

The archive documentation describes a browser-UI collection workflow against official `elections.ma` election pages. It exposes names of internal ASMX endpoints and election codes, but expressly does not claim a verified HTTP request/response contract for those endpoints. The raw and structured files, not an invented API client, are therefore the evidence reviewed here.

### 1.2 Current Fhemni implementation

Repository:

`/Users/maboullaite/Projects/med-blog/gemini-agentic-video/fhemni`

Latest merged state reviewed:

- `origin/main` at commit `8fca415`
- election schema migrations V51, V52, V53, V57, V58, and V59
- the merged election API/domain implementation
- committed 2026 replayable snapshot at `data/elections/2026/results.sql`
- private operational snapshot at `/private/tmp/fhemni-election-results/data/elections/2026/results.sql`

The private operational snapshot was used only for read-only reconciliation. It is explicitly operational and private and must not be committed merely as a consequence of this review.

## 2. Integrity and reproducibility

### 2.1 File integrity

Every entry listed in `SHA256SUMS.txt` passes SHA-256 verification. This establishes that the reviewed archive is internally unchanged relative to its manifest; it does not independently prove external authenticity.

### 2.2 Cross-format agreement

The CSV, combined JSON, per-year JSON, and SQLite representations agree on row counts and structured content for the audited tables. SQLite reports a healthy database. No format-specific divergence was found.

### 2.3 Included validation suites

All archived validation checks pass:

| Election | Checks | Failures |
|---|---:|---:|
| 2016 | 499 | 0 |
| 2021 | 555 | 0 |
| 2026 | 346 | 0 |
| Combined | 54 | 0 |

These checks provide strong internal consistency evidence. They should be retained, but Fhemni should reproduce the critical invariants in its own importer rather than trusting a precomputed validation file.

## 3. Exact archive inventory

### 3.1 Combined table counts

| Table | Rows |
|---|---:|
| `elections` | 3 |
| `regions` | 36 |
| `provinces` | 249 |
| `constituencies` | 301 |
| `parties` | 86 |
| `votes` | 4,959 |
| `elected_candidates` | 1,185 |
| `seat_results` | 1,535 |
| `turnout` | 775 |
| `demographics` | 3,800 |
| `queries` | 392 |

### 3.2 Counts by election

| Table | 2016 | 2021 | 2026 |
|---|---:|---:|---:|
| regions | 12 | 12 | 12 |
| provinces | 83 | 83 | 83 |
| constituencies | 93 | 104 | 104 |
| parties | 28 | 30 | 28 |
| vote rows | 1,407 | 1,705 | 1,847 |
| elected mandates | 395 | 395 | 395 |
| seat-result rows | 420 | 532 | 583 |
| turnout rows | 229 | 253 | 293 |
| demographic rows | 1,160 | 1,280 | 1,360 |
| source queries | 116 | 128 | 148 |

The apparent constituency-count difference is expected: 2016 has 92 local constituencies plus one national-list contest; 2021 and 2026 each have the same 92 local constituencies plus 12 regional-list contests.

### 3.3 Source-query coverage

| Election | Available | No data returned | Total |
|---|---:|---:|---:|
| 2016 | 116 | 0 | 116 |
| 2021 | 128 | 0 | 128 |
| 2026 | 136 | 12 | 148 |

The 12 2026 `no_data_returned` records are province-filter variants of regional-list views. They are not missing contests: the canonical regional-list query for each region is present, and all 104 actual 2026 contests are covered. The importer should preserve those 12 outcomes as query metadata, not coerce them into zero-valued election facts.

## 4. Fields and provenance classification

The archive combines direct source observations, reversible parsing, collection metadata, and analyst-derived fields. Those categories must remain explicit.

### 4.1 Defensibly attributable to elections.ma

Subject to retaining the raw capture/query reference, the following are direct or mechanically parsed source facts:

- election Arabic label and date label displayed by the official election page;
- official page URL and source query/filter context;
- source geography identifiers and Arabic labels in the official selectors;
- constituency/list labels and allocated seats shown in result tables;
- party/list Arabic names shown in tables or charts;
- candidate Arabic names and elected-member rows shown by the official site;
- original vote text and its reversibly parsed integer value;
- directly displayed constituency party seats;
- directly displayed 2021 and 2026 regional-list seat results;
- turnout percentages when the source percentage string is retained;
- demographic percentages only where the archive marks them `reported` and preserves the literal source percentage text.

Mechanical parsing is acceptable under elections.ma attribution only if Fhemni keeps the raw string, the parser version, and a reversible check. The archive notes that vote digits were sometimes encountered in right-to-left DOM groups; the parsed integer should never survive without the original representation.

### 4.2 Not direct elections.ma facts

The following are collector, normalization, or analytical products and must not be attributed as though elections.ma directly published them:

- archive-generated hashes and internal identifiers such as `election_id`, `party_id`, `elected_id`, and `query_id`;
- normalized whitespace and normalized lookup keys;
- inferred geography joins or classification labels;
- row-order metadata and collection timestamps;
- coverage/status diagnostics;
- cross-year party equivalence or coalition lineage;
- cross-election person links;
- labels such as “party switcher” or “retained MP”;
- inferred demographic counts;
- any percentage or count marked derived rather than reported;
- translations or French/English names not explicitly present on the official source page.

Those values can be used internally if their provenance is explicit. Public analytical statements derived from official facts should be labelled, for example, **“Fhemni analysis based on elections.ma results”**, not presented as an elections.ma statement.

### 4.3 Derived demographic rows that fail the strict public-attribution test

All 3,800 `count_inferred_from_percentage` values are calculated counts, not source-reported counts.

There are 1,414 demographic rows with no literal `source_percentage_text`:

| Election | Main categories represented among these rows |
|---|---|
| 2016 | 415 baseline/seat-total rows, 4 constituency-count rows, 12 other-category rows, 2 zero-height chart rows |
| 2021 | 460 baseline rows, 2 constituency-count rows, 9 other-category rows |
| 2026 | 500 baseline rows, 2 regional-constituency-count rows, 8 other-category rows |

These are not eligible for the initial public product under the current attribution rule. The safest policy is to omit them rather than add a complicated derived-data legend at launch.

### 4.4 The 2016 seat-result caveat

All 420 2016 `seat_results` rows are marked `derived_from_reported_chart_percentage_and_total`. They should not be described as directly reported seat counts. For 2016 party-seat aggregation, use the directly published constituency seat counts and/or the 395 elected rows, and cross-check the two. If the derived chart rows are retained internally, keep them in a separate derived-fact layer.

### 4.5 API-access caveat

`API_ACCESS.md` records endpoint names and election codes seen in the official web application, but the archive does not establish a durable public API contract. Direct unauthenticated HTTP attempts were not treated as evidence of such a contract. Fhemni should not build a production ingestion client by guessing request parameters. For now, ingest the archived source captures and preserve the official page/query provenance.

## 5. Completeness, nulls, duplicates, and conflicts

### 5.1 Material nulls

| Table/field | Null count | Interpretation |
|---|---:|---|
| `constituencies.region_id` | 1 | 2016 national-list contest has no regional allocation |
| `constituencies.province_id` | 25 | national/regional list contests are not provincial local contests |
| `elected_candidates.region_id` | 90 | 2016 national-list winners cannot be assigned to source regions |
| `elected_candidates.province_id` | 270 | list-seat winners lack a local province by design |
| `parties.abbreviation` | 48 | abbreviation is not always shown/available and is not a stable identity key |
| `queries.seat_total` | 12 | the 12 2026 no-data filter combinations |
| `demographics.source_percentage_text` | 1,414 | derived rows; not source-reported percentages |
| `votes.region_id` | 24 | non-regional list structure, primarily the 2016 national list |
| `votes.province_id` | 492 | list ballots are not local provincial contests |

No null above should be imputed. Each reflects either the ballot design or unavailable source data.

### 5.2 Duplicate audit

- No duplicate vote rows were found on the election/contest/party natural key.
- No duplicate elected-candidate name occurs within the same election after exact normalized Arabic comparison.
- Eleven repeated `list_lead_name_ar` values occur across vote rows, but inspection shows that these are legitimate repeated people/list leaders rather than duplicated result records.
- Source IDs repeat between elections. Every join and uniqueness constraint must therefore include `election_id`.

### 5.3 Conflicting records

No internal conflict was found between archive representations or between the archive’s vote-seat structure and elected-mandate totals. The main conflicts are semantic-model conflicts, not numerical ones:

- 2016 has a national list, while the current Fhemni model calls the second ballot regional;
- the current Fhemni `valid_votes` field is used for a sum of two ballot totals in the operational 2026 snapshot, even though that number is not a count of individual valid voters;
- the committed 2026 snapshot is empty while the operational one is populated;
- cross-year party labels include coalitions and changing abbreviations that must not be collapsed automatically.

## 6. Ballot structure and valid cross-year denominators

### 6.1 Official contest structure

| Election | Local constituencies | Local seats | List contests | List type | List seats |
|---|---:|---:|---:|---|---:|
| 2016 | 92 | 305 | 1 | national list | 90 |
| 2021 | 92 | 305 | 12 | regional lists | 90 |
| 2026 | 92 | 305 | 12 | regional lists | 90 |

### 6.2 Ballot totals

| Election | Local-ballot votes | List-ballot votes | List type |
|---|---:|---:|---|
| 2016 | 5,790,552 | 5,806,004 | national |
| 2021 | 7,588,505 | 7,571,623 | regional |
| 2026 | 4,900,377 | 4,838,149 | regional |

These are separate ballot totals. Adding them produces a count of ballot marks across two ballots, not a count of individual voters or a conventional “valid votes” denominator. Fhemni may show a combined ballot-mark comparison only when it explicitly says so. It must not label the sum as people, voters, turnout, or a single-ballot valid-vote total.

### 6.3 Safe seat comparisons

The following are safe:

- national total seats by party within each election;
- local seats by party across all three elections;
- local seats by party and region across all three elections;
- local constituency seat gains/losses across all three elections;
- regional-list seats by party and region between 2021 and 2026 only;
- total seats by region between 2021 and 2026 only, because both use regional lists.

The following are not safe without changing the question:

- assigning 2016 national-list seats to regions;
- comparing 2016 region totals that include list seats with 2021/2026 region totals;
- calling the list ballot “regional” for 2016;
- using local plus list vote sums as voter totals.

## 7. Geographic comparability

### 7.1 Stable source geography

Across the archive:

- all 12 region source IDs and Arabic labels are stable;
- all 83 province/prefecture source IDs, Arabic labels, and region assignments are stable;
- all 92 local constituency source IDs are stable;
- every local constituency keeps the same allocated-seat count in all three elections;
- the total local allocation remains exactly 305.

This is unusually favorable for constituency-level longitudinal analysis.

### 7.2 One local-name spelling change

Local constituency ID 20 appears as:

- 2016 and 2021: `بزو - واويزاغت`
- 2026: `بزو- واويزغت`

It remains in the same region and province and retains three seats. The stable source constituency ID should be the primary key; the name variants should be retained as election-specific labels rather than “corrected” by silently rewriting history.

### 7.3 2021/2026 region allocations

The local and regional-list allocations are identical in 2021 and 2026:

| Region ID | Local seats | Regional-list seats | Total |
|---:|---:|---:|---:|
| 1 | 29 | 8 | 37 |
| 2 | 23 | 7 | 30 |
| 3 | 37 | 10 | 47 |
| 4 | 39 | 10 | 49 |
| 5 | 25 | 7 | 32 |
| 6 | 57 | 12 | 69 |
| 7 | 36 | 10 | 46 |
| 8 | 17 | 6 | 23 |
| 9 | 21 | 7 | 28 |
| 10 | 8 | 5 | 13 |
| 11 | 9 | 5 | 14 |
| 12 | 4 | 3 | 7 |

For 2016, only the local allocations in the middle column are attributable to regions. The 90 national-list seats must remain unassigned geographically.

## 8. Party normalization and lineage

### 8.1 What the archive’s party ID means

The archive party ID is a hash of the normalized exact Arabic source label. It is reproducible, but it identifies an election label, not necessarily a stable legal organization across time.

Exact Arabic label reuse:

- 23 party labels appear in all three elections;
- 4 labels appear in exactly two elections;
- 9 labels are year-specific.

Examples appearing in two elections include `حزب النهضة والفضيلة` (2016/2021), `الحزب المغربي الحر` (2021/2026), `حزب الإنصاف` (2021/2026), and `حزب الخضر المغربي` (2021/2026).

### 8.2 Labels requiring explicit treatment

Year-specific or structurally sensitive labels include:

- 2016 independent: `بدون انتماء سياسي`
- 2026 independent: `بدون انتماء حزبي`
- 2016: `تحالف أحزاب فيدرالية اليسار الديمقراطي`
- 2021: `تحالف فيدرالية اليسار`
- 2021: `الحزب الاشتراكي الموحد`
- 2026: `تحالف اليسار`
- 2016: `تحالف العهد و التجديد`
- 2016: `حزب اليسار الأخضر المغربي`
- 2021: `حزب العهد الديمقراطي`

Abbreviations are not reliable identity keys. In particular, `PSU` is associated with a 2016 alliance in elected rows and with a 2021 party. The archive’s 2016 APFGD/PSU interpretation is analyst normalization, not an explicit cross-year legal lineage published by elections.ma.

### 8.3 Required party model

Use three layers:

1. `party_entity`: a curated long-lived legal/organizational entity, when established.
2. `election_party`: the exact source party/list/coalition label in one election, always retained.
3. `party_lineage`: a manually approved relation between election-party records, with relation type such as `same_legal_entity`, `rename`, `coalition_successor`, `merge`, `split`, or `unknown`, plus evidence and reviewer status.

Unknown mappings must remain unresolved. A graph that spans elections can either operate on exact election-party labels or require an approved lineage. It must never silently merge coalitions, members, successors, and similarly abbreviated parties.

## 9. National party examples and sanity checks

The following seat totals provide useful aggregate reconciliation points. They are not a substitute for row-level import validation.

### 2016

| Party/list | Total seats | Local | National list |
|---|---:|---:|---:|
| PJD | 125 | 98 | 27 |
| PAM | 102 | 81 | 21 |
| PI | 46 | 35 | 11 |
| RNI | 37 | 28 | 9 |
| MP | 27 | — | — |
| USFP | 20 | — | — |
| UC | 19 | — | — |
| PPS | 12 | — | — |
| MDS | 3 | — | — |
| federal-left alliance | 2 | — | — |
| PUD | 1 | — | — |
| PGVM | 1 | — | — |

### 2021

| Party/list | Total seats | Local | Regional list |
|---|---:|---:|---:|
| RNI | 102 | 86 | 16 |
| PAM | 87 | 75 | 12 |
| PI | 81 | 68 | 13 |
| USFP | 34 | — | — |
| MP | 28 | — | — |
| PPS | 22 | — | — |
| UC | 18 | — | — |
| PJD | 13 | 4 | 9 |
| MDS | 5 | — | — |
| FFD | 3 | — | — |
| CNI alliance | 1 | — | — |
| PSU | 1 | — | — |

### 2026

| Party/list | Total seats | Local | Regional list |
|---|---:|---:|---:|
| PAM | 97 | 85 | 12 |
| RNI | 66 | 57 | 9 |
| PI | 65 | 52 | 13 |
| PJD | 54 | 44 | 10 |
| MP | 29 | — | — |
| USFP | 26 | — | — |
| PPS | 19 | — | — |
| UC | 17 | — | — |
| MDS | 8 | — | — |
| AG | 8 | — | — |
| FFD | 2 | — | — |
| PE | 2 | — | — |
| ND | 1 | — | — |
| PUD | 1 | — | — |

The importer must still verify all parties, not only the parties shown here, and must reconcile the full local/list/total sums to 305/90/395.

## 10. Candidate identity and “party switcher” analysis

### 10.1 What can be measured conservatively

Names are unique within each election after exact normalized-Arabic comparison. Across elections:

- 220 exact normalized names occur in at least two elections;
- 69 occur in all three elections;
- 151 occur in exactly two elections;
- conservative alef/yaa normalization does not add new matches, so the exact-name result is already the strongest unambiguous automated lower bound.

Pairwise lower-bound matches:

| Election pair | Exact names | Same party label | Different party label | Same constituency | Same region |
|---|---:|---:|---:|---:|---:|
| 2016 → 2021 | 114 | 92 | 22 | 107 | 108 |
| 2021 → 2026 | 155 | 142 | 13 | 142 | 154 |
| 2016 → 2026 | 89 | 68 | 21 | 78 | 81 |

These counts are lower bounds on returning elected people because spelling changes can produce false negatives. They are not proof of identity in every exact-name case because homonyms remain possible.

### 10.2 Different-party-label review set

Thirty-nine recurring exact names appear under more than one election-party identifier:

- **Tier A:** 33 exact unique names with the same local constituency — strongest automated candidate set, still requiring human review.
- **Tier B:** 3 exact names remaining in the same region but moving between regional/local ballot routes or constituency contexts.
- **Tier C:** 3 exact names moving across regions or between the 2016 national list and a later local contest.

Tier B examples:

- `ريم شباط`: 2021 FFD regional list in Fès-Meknès → 2026 MP local, Fès North.
- `زينب السيمو`: 2021 RNI regional list in Tanger-Tétouan-Al Hoceïma → 2026 PAM local, Larache.
- `نبيلة منيب`: 2021 PSU regional list in Casablanca-Settat → 2026 Alliance de gauche local, Casablanca-Anfa. Coalition/party lineage makes an automatic “switch” label particularly unsafe here.

Tier C examples:

- `خالد الشناق`: 2016 RNI national list → 2021/2026 PI local, Inzegane-Aït Melloul.
- `محمد صديقي`: 2016 PJD local, Rabat-Océan → 2021/2026 RNI local, Berkane.
- `مريم وحساة`: 2016 PAM national list → 2021/2026 PPS local, Beni Mellal.

Tier A exact-name same-constituency review candidates:

`احمد الغزوي`، `ادريس السنتيسي`، `الخطاط ينجا`، `امبارك حمية`، `خالد المنصوري`، `رضوان النوينو`، `سعيد التدلاوي`، `سعيد انميلي`، `عبد الرحمان العمري`، `عبد الصماد خناني`، `عبد العزيز الوادكي`، `عبد الغني مخداد`، `عبد الفتاح اهل المكي`، `عبد الفتاح عمار`، `عبد الله أبركى`، `عبد الله بيلات`، `محمد احويط`، `محمد الزموري`، `محمد السيمو`، `محمد العربي احنين`، `محمد امغار`، `محمد بوبكر`، `محمد زكراني`، `مروان شبعتو`، `مصطفى العمري`، `مصطفى توتو`، `مولاي زبير حبدي`، `نبيل صبري`، `نور الدين الهروشي`، `نور الدين رفيق`، `نور الدين قشيبل`، `هاشم امين الشفيق`، `يونس بن سليمان`.

### 10.3 Why these are not yet publishable “party switchers”

The archive only includes elected mandates. It does not provide:

- a stable source person ID;
- date of birth or another identity discriminator;
- full candidacy history, including losing candidacies;
- party membership history between election dates;
- evidence of the legal relationship among coalitions, renamed parties, mergers, or splits.

Therefore the most accurate public wording, after manual verification, is:

> “The same reviewed person name appears elected under different party or list labels in the elections.ma result snapshots.”

It is not defensible to automatically say that the person defected, changed formal membership, or moved at a specific date.

### 10.4 Required identity workflow

1. Import each elected mandate with its exact source name and election-specific context.
2. Leave `person_id` null by default.
3. Generate review candidates using exact normalized name plus constituency/region/ballot route.
4. Require a human reviewer to accept/reject the link and record evidence.
5. Classify match strength and preserve alternate spellings as aliases.
6. Separately review party lineage before deriving a switch label.
7. Publish only approved links, with an analytical attribution to Fhemni.

Fuzzy matching may be used to propose review candidates but must never create person links or public switch claims automatically.

## 11. Direct 2026 reconciliation with Fhemni

### 11.1 Snapshot state

The committed `origin/main` 2026 `results.sql` is an empty `COUNTING` snapshot with no party rows and no `source_updated_at`. The private operational snapshot contains the complete current result:

- 28 national party rows;
- 96 region-party rows;
- 92 local constituencies;
- 305 local winners;
- 90 regional-list winners.

This discrepancy must be resolved deliberately before a historical importer treats the repository snapshot as authoritative.

### 11.2 Numerical reconciliation

The official-source archive and the private operational snapshot reconcile exactly on:

- all 28 national party totals;
- total, local, and list seats;
- total, local, and regional vote values;
- all 96 region-party seat rows;
- all 92 local constituency counts and allocations;
- all 305 local winner seat slots by constituency and party;
- all 12 regional-list party result multisets;
- all 90 regional-list winner seat slots.

The corresponding live-2026 aggregate reconciliation is also exact: **9,738,526 combined ballot marks, 395 seats, and 28 election-party rows**, comprising **4,900,377 local-ballot votes / 305 local seats** and **4,838,149 regional-ballot votes / 90 regional-list seats**. This validates the production aggregate structure against the archive. It does not change the semantic warning below: 9,738,526 is a sum across two ballots, not a count of unique voters.

### 11.3 Explicit constituency-name aliases needed for reconciliation

Ten name/spelling mappings were required; no seat or vote inference was required:

| Fhemni concept | elections.ma/archive label difference |
|---|---|
| Nador | `الناضور` vs Fhemni `الناظور` |
| Oujda-Angad | `أنجاد` vs Fhemni `أنكاد` |
| Kénitra-El Gharb | Fhemni combined display name maps to source `الغرب` |
| Khémisset-Oulmès | source `أولماس` vs Fhemni `والماس` |
| Ben M'Sick | source `ابن امسيك` vs Fhemni `بن مسيك` |
| Sidi Othmane-Moulay Rachid | Fhemni combined name maps to source `مولاي رشيد` |
| Guéliz | maps to source `جليز - النخيل` |
| Sidi Youssef Ben Ali | maps to source `المدينة - سيدي يوسف بن علي` |
| Agadir Ida-Outanane | spacing variant |
| Tiznit | source `تيزنيت` vs Fhemni `تزنيت` |

These aliases should be stored as reviewed source mappings, not implemented as lossy global name normalization.

### 11.4 Candidate names and cross-script identity

Although the seat slots reconcile, exact raw candidate-name equality exists for only:

- 36 of 305 local winners;
- 53 of 90 regional-list winners;
- 89 of 395 overall.

The other 306 Fhemni/archive name pairs are typically Latin-versus-Arabic or spelling/transliteration differences. Seat-slot reconciliation can safely confirm that the same result position is represented. It cannot by itself establish a reusable cross-election person identity. Store aliases explicitly and review them before using them for historical person matching.

### 11.5 Two repeated party slots on a regional list

In Laâyoune-Sakia El Hamra, two PI regional-list seats cannot be distinguished by party/region alone. Both source Arabic names — `مفيدة وداد` and `للارقية شريف` — resolve the two slots exactly. This demonstrates why winner-level reconciliation needs names plus source row context whenever a party wins multiple list seats.

### 11.6 The `valid_votes` semantic problem

The private operational snapshot stores `valid_votes = 9,738,526`, which equals:

`4,900,377 local ballot votes + 4,838,149 regional ballot votes`.

That number is a combined count of marks on two ballots, not a number of voters. The current API’s party `voteShare = votes / validVotes` is mathematically coherent only as **share of all local and regional ballot marks combined**. The field name and any UI copy must not imply unique voters or a single valid-vote pool.

For historical work, store separate ballot denominators and calculate local vote share, national-list vote share, or regional-list vote share independently. A combined ballot-mark measure should be opt-in and explicitly labelled.

## 12. Current Fhemni schema gaps

The current schema is effective for the 2026 product but is not a safe canonical historical model:

1. `election_party_results` has fixed `local_*` and `regional_*` columns. It cannot faithfully represent the 2016 national list without mislabelling it regional.
2. `election_constituencies.region_code` is non-null, so the 2016 national-list contest has no natural representation.
3. Local and regional-list winners are stored in separate tables, complicating generic mandate history.
4. `candidate_key` is useful within the current dataset but is not a verified cross-election person ID.
5. Provenance is largely election-level; result and winner rows need source-capture provenance.
6. There is no election-specific party label plus curated cross-year party lineage layer.
7. There is no general scoped-observation model for turnout and demographics.
8. Candidate name storage mixes Arabic and Latin/transliterated forms without a person-name/alias model.
9. French and English translations are useful Fhemni content but are not, by default, facts attributable to elections.ma.
10. The committed replayable 2026 snapshot is stale relative to the operational data.

## 13. Recommended canonical relational model

### 13.1 Source and election layer

**`elections`**

- election key, date, chamber, legal seat total, source election code, status;
- source fact fields separated from Fhemni labels/translations.

**`source_captures`**

- official URL, capture/retrieval timestamp, checksum, media/raw-file reference, collector version;
- immutable after ingestion.

**`source_queries`**

- capture, official filter identifiers, response/view status, raw source text, query-level provenance.

### 13.2 Geography and contest layer

**`geographies`**

- durable internal concepts for region/province/constituency where continuity has been reviewed.

**`election_geography_versions`**

- election, source geography type/id, exact source name, parent source geography, reviewed equivalence.

**`contests`**

- election, source constituency/list ID, exact source label, ballot type (`LOCAL`, `NATIONAL_LIST`, `REGIONAL_LIST`), nullable region/province, allocated seats.

The 2016 national list is one `NATIONAL_LIST` contest with null region and province. It must not be encoded as 12 inferred regional contests.

### 13.3 Party layer

**`party_entities`**

- curated stable entity, only when identity is established.

**`election_parties`**

- exact source party/list label, source abbreviation if present, source/hash identifier, election, nullable stable entity.

**`party_lineage`**

- from/to election party, relation type, evidence, review state, reviewer and timestamp.

### 13.4 Results and mandates

**`contest_party_results`**

- contest, election party, original vote text, parsed votes, seats, fact status (`REPORTED`, `PARSED`, `DERIVED`), source query/capture.

**`elected_mandates`**

- one row per awarded seat, contest, election party, exact source candidate name, source row/order, nullable reviewed person.

National and regional aggregates should be materialized views or query products over contests, not separately edited canonical facts.

### 13.5 People and aliases

**`people`**

- durable internal person only after review.

**`person_names`**

- exact source spelling, script/language, election/context, normalized search form.

**`person_link_reviews`**

- mandate pair/candidate link, proposed method, confidence tier, accepted/rejected state, evidence and reviewer.

### 13.6 Turnout and demographic observations

**`observations`**

- election, geographic/contest scope, metric, dimension/category, value, unit, source text, reported/derived state, source query.

Derived counts must either remain outside the public fact store or be clearly isolated from `REPORTED` observations.

## 14. Import and reconciliation rules

1. **Stage first.** Load immutable source captures and typed staging rows before touching public tables.
2. **Verify checksums and schemas.** Reject unknown or changed archive files.
3. **Preserve raw strings.** Keep exact Arabic names, vote strings, percentage strings, and source IDs.
4. **Partition identity by election.** Every source identifier and natural-key join includes `election_id`.
5. **Keep ballot types distinct.** Never coerce `NATIONAL_LIST` into `REGIONAL_LIST`.
6. **Do not infer missing geography.** Null region/province on list records remains null when the source does not assign it.
7. **Use direct 2016 facts.** Aggregate 2016 seats from reported contest rows/elected mandates, not derived chart percentages.
8. **Manual party lineage.** Import source labels immediately; leave stable party entity unresolved until reviewed.
9. **No automatic people.** Import mandates with `person_id = null`; generate review candidates separately.
10. **Exclude inferred demographics at launch.** Direct reported percentages may be imported with exact source text.
11. **Reconcile 2026 before replacement.** Compare every national, regional, contest, and winner slot against the operational snapshot and fail on any unexplained difference.
12. **Separate publication time from retrieval time.** Archive retrieval timestamps are not automatically official source-revision timestamps.
13. **Separate source attribution from analysis attribution.** Direct facts cite elections.ma; cross-year calculations say they are Fhemni analysis based on elections.ma data.
14. **Atomic publish.** Promote only a complete validated election version, with a replayable snapshot and rollback artifact.

## 15. Fail-closed invariants

The importer and publishing pipeline should reject a release when any applicable check fails.

### 15.1 Provenance

- every public fact links to a source capture/query;
- every direct public source URL is on `elections.ma` under the current policy;
- checksums match the reviewed manifest;
- no `DERIVED` record passes a `REPORTED` filter;
- translations or Fhemni classifications are never tagged as elections.ma facts;
- parsed vote values round-trip to the preserved source text under the declared parser.

### 15.2 Seat structure

For each election:

- elected mandates = 395;
- local mandates = 305;
- list mandates = 90;
- each contest’s mandate count equals its allocation;
- each contest’s party-seat sum equals its allocation;
- national party total seats = 395;
- national party local seats = 305;
- national party list seats = 90.

For 2021 and 2026:

- regional-list seats sum to 90;
- region-level local/list/total aggregates reconcile to contest rows.

For 2016:

- exactly one national-list contest has 90 seats;
- no national-list seat may receive an inferred region.

### 15.3 Votes and observations

- local and list vote totals remain separate;
- no ballot sum is called a voter count;
- values are nonnegative;
- percentages are within 0–100;
- a `REPORTED` percentage requires its source percentage text;
- a no-data query does not generate zero-valued facts.

### 15.4 Identity and lineage

- party lineage cannot affect public aggregates until approved;
- unresolved coalitions remain election-specific labels;
- no person link is created solely by fuzzy matching;
- no “party switch” is published without an approved person link and reviewed party/list interpretation;
- one mandate maps to at most one reviewed person;
- aliases retain their source language/script and context.

### 15.5 Operational reconciliation

- 2026 archive-versus-production diffs must be zero or explicitly adjudicated before publish;
- all 92 constituencies and 395 winner slots remain present;
- expected source-name aliases are explicit and versioned;
- source timestamps move forward only when tied to an actual source revision;
- the replayable snapshot and database transaction represent the same version.

## 16. Ranked catalogue of defensible comparisons and graphs

### Tier 1 — recommended first release

1. **National seat composition by election**  
   Show 395 seats per year, with local/list split. Label the 2016 list as national and the 2021/2026 lists as regional.

2. **National local-seat swing by party, 2016 → 2021 → 2026**  
   This uses a stable 305-seat denominator and avoids list-system incompatibility. Exact labels can be shown immediately; long-term party trend lines require approved lineage.

3. **Regional local-seat leader/change map across all three elections**  
   Safe because the 12 regions and all 305 local-seat allocations are stable. Use local seats only.

4. **Constituency party-seat gain/loss**  
   All 92 source constituency IDs and allocations are stable. Use source IDs as keys and show election-specific name labels.

5. **National turnout over time**  
   Direct reported values: 42.29% in 2016, 50.86% in 2021, and 38.08% in 2026.

6. **Local vote share versus local seat share by party and year**  
   Use only the local-ballot denominator and local seats. This avoids double-ballot and list-system ambiguity.

### Additional validated counterfactual

**2026 under the 2016 quotient rules**  
The local scenario is publishable because the allocator exactly reproduces all 92 official 2016 local contests and all 305 seats before it is applied to 2026. A broader 395-seat scenario is also calculable: the same allocator reproduces all 24 official 2016 national-list party rows and all 90 seats, then treats aggregated 2026 regional-list votes as a hypothetical national list. The broader result must remain visibly labelled as an extra modelling assumption and decomposed into local, regional/national, and total columns. It is not a cross-year regional-seat comparison.

### Tier 1 regional leader examples

The following local-seat leaders illustrate the available story, with ties preserved rather than broken:

| Region ID | 2016 | 2021 | 2026 |
|---:|---|---|---|
| 1 | PAM 8 | RNI / PI / PAM, 7 each | PAM 8 |
| 2 | PAM 8 | PAM 8 | PAM 8 |
| 3 | PJD 13 | RNI 11 | PAM 11 |
| 4 | PJD 15 | RNI 11 | PAM 10 |
| 5 | PAM 6 | RNI 6 | PAM 6 |
| 6 | PJD 22 | RNI / PI, 15 each | PAM 14 |
| 7 | PAM / PJD, 13 each | PI 10 | PAM 10 |
| 8 | PAM / PJD, 5 each | RNI 5 | PAM 4 |
| 9 | PJD 9 | RNI 7 | RNI / PI, 6 each |
| 10 | USFP 3 | PAM 4 | PAM 4 |
| 11 | PAM 4 | PI 4 | PI 4 |
| 12 | MP / USFP / PAM / PJD, 1 each | RNI / PI, 2 each | PI / PAM, 2 each |

### Tier 2 — after party-lineage review

7. **Total seats and vote trajectories for stable party entities**  
   Requires approved lineage; do not merge left alliances or renamed labels automatically.

8. **Party rank and seat-share changes**  
   Use local seats for a three-election comparison; total seats are acceptable per election but list-system differences need a visible note.

9. **2021 versus 2026 region total-seat comparison**  
   These two elections share the regional-list system and identical region allocations.

10. **Combined ballot-mark comparison**  
    Optional only with a prominent label that it combines two ballots and is not a voter count. Separate views are preferable.

### Tier 3 — after person-link review

11. **Returning elected representatives**  
    Start with exact-name lower bounds; publish only reviewed person links.

12. **Elected under different party/list labels**  
    Use the Tier A/B/C review structure. Avoid “defection” language unless independently documented.

13. **Ballot-route changes**  
    Examples include national-list to local, regional-list to local, or local constituency changes. These are meaningful only after identity review.

14. **Gender, age, or education trends**  
    Use only directly reported percentage observations. Do not expose inferred counts as official counts.

### Tier 4 — no-go with current evidence

- automatic fuzzy candidate matching;
- automatic definitive “party switcher” or “defector” labels;
- a single cross-year FGD/PSU/CNI/Alliance de gauche trend without reviewed lineage;
- 2016 region totals that include national-list seats;
- voter counts derived by adding two ballot totals;
- historical rural/urban comparisons unless a separately sourced, election-versioned constituency/commune crosswalk is added with its own attribution.

## 17. Phased delivery plan

### Phase 0 — freeze and document

- retain the archive checksums and immutable raw captures;
- register source captures and provenance;
- document direct versus parsed versus derived fields;
- resolve the committed-versus-operational 2026 snapshot gap.

**Exit:** identical raw checksums, documented provenance, and no unexplained production diff.

### Phase 1 — canonical direct-fact ingestion

- introduce contest-level ballot types;
- ingest direct official facts for 2016, 2021, and 2026;
- exclude inferred demographic counts;
- reconcile seats, votes, geographies, and mandates;
- create an auditable import report for each election.

**Exit:** all fail-closed seat, vote, source, and coverage invariants pass.

### Phase 2 — geography aliases and party lineage

- record the ten reviewed 2026 constituency aliases;
- create election-party records from exact source labels;
- manually review only the lineage relationships needed by the first graphs;
- leave ambiguous coalitions unresolved.

**Exit:** every cross-year party line is backed by an approved mapping or clearly remains election-specific.

### Phase 3 — Tier 1 historical graphs

- national seats;
- local-seat swing;
- regional local-seat map;
- constituency gain/loss;
- turnout;
- local vote share versus local seat share.

**Exit:** every value has row-level elections.ma provenance, every derived comparison is labelled Fhemni analysis, and no graph mixes incompatible ballot scopes.

### Phase 4 — reviewed people and party-label changes

- build candidate-link review tooling;
- review exact-name Tier A first, then Tier B/C;
- record aliases and evidence;
- publish carefully worded, reviewed comparisons only.

**Exit:** no automated public identity or party-switch claim remains.

### Phase 5 — optional enriched analyses

- add direct demographic percentage trends;
- add external rural/urban or commune data only through separately versioned source tables and attribution;
- consider derived demographic measures only with a distinct analytical label and methodology page.

## 18. Final go/no-go table

| Work item | Decision | Conditions |
|---|---|---|
| Preserve/archive the dataset | GO | Keep checksums and raw captures immutable |
| Build a canonical historical importer | GO | Contest model, row provenance, fail-closed validation |
| Publish Tier 1 graphs | GO after Phase 1–2 | Direct facts only; correct ballot scope and labels |
| Use archive to validate 2026 numbers | GO | Reconcile against the operational snapshot, not stale committed state |
| Replace production 2026 wholesale | NO-GO now | Resolve provenance/timestamp and replayable snapshot first |
| Publish inferred demographic counts | NO-GO | Not direct elections.ma facts |
| Publish automatic party switchers | NO-GO | Manual person and party-lineage review required |
| Compare all-in regional seats across 2016–2026 | NO-GO | 2016 list seats are national, not regional |
| Add historical rural/urban analysis | DEFER | Requires versioned external crosswalk and separate attribution |
| Publish 2026 local-seat counterfactual under 2016 rules | GO | Exact 92/92 and 305/305 reproduction gate; clearly labelled Fhemni analysis |
| Publish 395-seat 2016-system scenario for 2026 | GO WITH CAVEAT | Exact 24/24 and 90/90 national-list reproduction gate; aggregated 2026 regional votes explicitly labelled as a hypothetical national list |

## 19. Bottom line

This archive is good enough to become the evidence base for a serious historical-election product, provided Fhemni preserves the distinction between source facts and analysis. The best immediate product is not “everything in the archive.” It is a narrow, highly defensible historical layer built from direct elections.ma observations, using local seats as the common three-election geographic denominator.

The data strongly supports stories about national composition, regional/local shifts, constituency changes, turnout, and vote-to-seat conversion. It also contains a promising lower-bound set of returning elected names and possible changes in party/list label, but those need a person-resolution workflow and careful wording. The archive does not support assigning 2016 national-list seats to regions, treating two ballot totals as a voter count, or attributing inferred demographic counts directly to elections.ma.

The right sequence is therefore: ship the current rural/urban feature, close the 2026 replayability gap, ingest direct historical facts into a contest-based model, release Tier 1 graphs, then add reviewed party and person history.
