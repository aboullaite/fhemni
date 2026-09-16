# Moroccan Priority Questionnaire Design

Status: Draft for user review
Date: 2026-09-16
Target: Fhemni 2026 election experience

## 1. Purpose

Fhemni will add a public, mobile-first questionnaire that helps a person understand
their own policy priorities and trade-offs in the Moroccan 2027–2031 context. The
experience uses 18 evidence-backed questions covering purchasing power, jobs,
education, healthcare, water and energy, social protection, governance, territorial
equality, and rights.

The result is a transparent personal policy profile. It is not a voting instruction,
party endorsement, ideological label, or opaque AI judgment. Users can continue from
their profile into standalone Fhemni evidence dossiers containing programme promises,
feasibility assessments, and sources for the topics they want to explore.

The feature must be available in all three Fhemni languages:

- `ar`: Moroccan Darija, written in accessible Arabic script;
- `fr`: French;
- `en`: English.

Every public content field is required in all three languages. The public API and UI
must never silently fall back from one language to another.

## 2. Goals

1. Make 18 substantial Moroccan policy choices understandable in approximately six
   minutes on a phone.
2. Explain the real benefit, cost, limitation, or implementation trade-off behind each
   choice.
3. Produce the same result for the same edition and answers every time.
4. Make every question, interpretation, and methodology auditable.
5. Work without authentication and without retaining anonymous answers on the server.
6. Allow an authenticated user to save a completed profile only after explicit consent.
7. Reuse Fhemni's reviewed policy taxonomy, programme promises, evidence, and
   editorial practices.
8. Permit AI-assisted drafting without making public availability or scoring depend on
   an AI provider.

## 3. Non-goals

- Ranking, recommending, or endorsing political parties or candidates.
- Producing a single left/right coordinate or ideological label.
- Predicting how a person will vote.
- Inferring sensitive traits from answers.
- Generating live result prose with Gemini or another model.
- Treating a skipped answer as neutral.
- Treating a programme's silence as support, opposition, or neutrality.
- Collecting demographic data in the first release.
- Building a statistically representative public-opinion survey.

## 4. Evidence and editorial basis

The initial allocation is based on three independent signals:

1. **Public salience:** nationally relevant citizen research, especially Arab
   Barometer's Morocco Wave VIII.
2. **Structural urgency:** current indicators from HCP, CESE, the Ministry of Health,
   the Competition Council, Bank Al-Maghrib where applicable, and international
   institutions publishing Morocco-specific diagnostics.
3. **Programme coverage:** Fhemni's reviewed 2026 corpus, currently 307 promises from
   12 published programmes.

The aggregate Fhemni corpus contains direct coverage across the following broad topics:

| Topic | Programmes with coverage | Direct promises |
| --- | ---: | ---: |
| Employment | 12/12 | 60 |
| Water, energy, and environment | 12/12 | 46 |
| Healthcare | 12/12 | 45 |
| Education | 11/12 | 58 |
| Governance | 11/12 | 32 |
| Regional development | 11/12 | 31 |
| Social protection | 11/12 | 21 |
| Purchasing power | 11/12 | 18 |

Programme frequency is a coverage check, not a proxy for public importance. A subject
enters the questionnaire only if it also represents a consequential policy choice within
government or parliamentary competence.

### 4.1 Important purchasing-power distinction

Question wording must distinguish the current inflation rate from the accumulated
price level and loss of real purchasing power. Falling inflation means prices are rising
more slowly, or sometimes falling over a short interval; it does not mean that household
income has recovered the purchasing power lost during previous shocks.

The purchasing-power section therefore covers different policy mechanisms instead of
repeating a generic claim that prices are high.

## 5. Approved 18-question allocation

| Theme | Count | Intended policy tensions |
| --- | ---: | --- |
| Purchasing power, prices, and household income | 4 | Subsidies and targeting; wages and employment costs; tax relief and public revenue; competition enforcement and price controls |
| Jobs and economic opportunity | 3 | Private job creation and public programmes; school-to-work pathways; women, care infrastructure, and formalisation |
| Education and skills | 2 | Learning outcomes and accountability; preschool, dropout, and territorial equality |
| Healthcare | 2 | Local staffing and access; public capacity, medicines, and contracted private provision |
| Water, food, and energy resilience | 2 | Allocation among uses; infrastructure and desalination versus demand and agricultural reform |
| Social protection | 1 | Universal guarantees versus tightly targeted assistance and gradual benefit withdrawal |
| Governance, corruption, and regional accountability | 2 | Transparency and enforcement; central delivery versus regional power and accountability |
| Housing, mobility, and territorial equality | 1 | Territorial investment, affordable housing, and public transport priorities |
| Rights, gender, and family policy | 1 | Legal and economic equality, care responsibilities, and family-policy reform |

Question authors must not turn these headings directly into statements. Each final item
needs one proposition, one material trade-off, a defined 2027–2031 horizon, and evidence
that supports the context without telling the user which answer is correct.

## 6. Product principles

### 6.1 Deterministic runtime, optional AI before publication

The live public path is ordinary Java and SQL. Questionnaire retrieval, validation,
profile calculation, tension detection, and explanation selection are deterministic.
The service works when Gemini is disabled or unavailable.

AI may help an editor privately to:

- draft alternative neutral wording from reviewed sources;
- find ambiguous, double-barrelled, or emotionally loaded language;
- propose Darija, French, and English translations from one semantic specification;
- identify existing published promises and evidence relevant to a question;
- detect contradictions between the three localisations.

AI output remains a draft. Publication requires a human reviewer, complete sources,
three approved localisations, and deterministic scoring metadata. V1 does not need a
new live AI integration: editors may use the existing internal tooling and import the
reviewed result. An in-product `QuestionDraftAssistant` can be added later behind an
opt-in configuration property if the editorial workload justifies it.

### 6.2 One semantic specification, three localisations

Each question has a language-independent specification describing:

- the exact policy decision;
- jurisdiction and 2027–2031 time horizon;
- positive and negative policy poles;
- material benefit and material cost on each side;
- accepted evidence and data cutoff;
- scoring dimensions and result rules.

Darija, French, and English are reviewed renderings of that specification. Darija is
the primary product language and must use familiar Moroccan vocabulary rather than
unnecessarily formal Arabic. Translation review uses back-translation to detect semantic
drift. A publication transaction fails if any public field is empty in any locale.

### 6.3 Explainability over artificial precision

The profile shows theme-level tendencies, priorities, and curated tensions. It does not
present a pseudo-scientific overall percentage. Every statement in the result links back
to the answers and rules that produced it.

## 7. User experience

### 7.1 Entry

The public entry point is `/priorities`, with a short Darija-first title such as
`شنو مهم عندك؟`. It explains that:

- the experience contains 18 questions;
- it takes about six minutes;
- answers remain on the device unless the user explicitly saves them;
- the result explains priorities and trade-offs rather than telling anyone how to vote.

No login wall appears before or during the questionnaire.

### 7.2 Question flow

The browser fetches the complete immutable edition in one small request. The interface
then presents one question at a time:

1. theme label and progress, for example `5 / 18`;
2. a short proposition;
3. an optional, expandable context card containing the trade-off and source date;
4. five response choices from strong disagreement to strong agreement;
5. `مازال ما حسمتش` / `Je ne sais pas encore` / `I haven't decided yet`;
6. an optional `مهم عندي بزاف` importance control appearing after an answer.

The importance control does not add another screen. Back and next remain available,
keyboard and screen-reader navigation are supported, and touch targets meet a minimum
44px size. Motion is restrained and respects `prefers-reduced-motion`.

Answers are written to `localStorage` after every change using a key scoped to the
edition ID. A returning user resumes at the first unanswered question. The interface
does not poll or reload the page.

### 7.3 Results

The browser posts the final answer set once to the stateless profile endpoint. The result
contains:

- completion and skipped-answer counts;
- ranked personal priorities based on explicit importance and answered coverage;
- theme-level policy tendencies with human-readable pole labels;
- up to three curated tensions or reinforcing patterns;
- an explanation of which answers produced every observation;
- a methodology link;
- links into standalone evidence dossiers for topics the user chooses to explore.

The result is rendered as a narrative profile and compact cards. It avoids a dense radar
chart, a generic political compass, and party ordering. Users can change an answer and
recalculate immediately.

### 7.4 Saving

Anonymous results are not stored server-side. A signed-in user may choose `Save this
profile` after seeing the result. Saving records the edition, answers, and deterministic
result snapshot so later question revisions cannot silently change it. A user can delete
saved profiles from their account.

## 8. Backend architecture

The feature is a bounded `civicprofile` module with clear interfaces:

- `QuestionnaireCatalogService`: retrieves current and immutable editions;
- `QuestionnaireProfileService`: validates answers and calculates results;
- `QuestionnaireEditorialService`: manages drafts, review, and publication;
- `SavedCivicProfileService`: explicit authenticated persistence and deletion;
- `QuestionnaireRepository`: JDBC persistence;
- `QuestionnaireController`: public catalogue and stateless profile APIs;
- `AccountCivicProfileController`: authenticated save/list/delete APIs;
- `AdminQuestionnaireController`: protected editorial APIs.

The module depends on the existing policy-topic catalogue and published programme
services only through narrow read interfaces. Programme ingestion, assessment jobs,
chat, and media generation do not depend on the questionnaire module.

### 8.1 Public APIs

```text
GET  /api/catalog/questionnaires/current?lang=ar
GET  /api/catalog/questionnaires/{editionId}?lang=ar
GET  /api/catalog/questionnaires/{editionId}/methodology?lang=ar
POST /api/catalog/questionnaires/{editionId}/profile
```

`GET current` returns the published edition ID, version, content digest, question count,
and canonical immutable URL. The edition response includes only published content in
the requested supported locale.

The profile request contains exactly one entry per answered or skipped question:

```json
{
  "answers": [
    {"questionKey": "prices-subsidy-targeting", "value": 1, "important": true},
    {"questionKey": "jobs-school-transition", "value": null, "skipped": true}
  ]
}
```

Values are integers from `-2` through `2`. Skip is explicit; absence is an unanswered
question. The backend rejects unknown keys, duplicate keys, contradictory skip/value
states, unsupported editions, and oversized payloads.

The response uses `Cache-Control: no-store`. Public edition responses use an ETag and
a short cache on `current`; immutable edition URLs may use a long cache keyed by their
content digest.

### 8.2 Account APIs

```text
PUT    /api/account/civic-profiles/{editionId}
GET    /api/account/civic-profiles
DELETE /api/account/civic-profiles/{profileId}
```

These endpoints require authentication, CSRF protection where applicable, ownership
checks, and `Cache-Control: no-store`. Saving is an explicit action and never occurs as
a side effect of calculating a profile.

### 8.3 Admin APIs

Admin endpoints support edition creation, question ordering, localisation editing,
source management, preview calculation, review, publication, supersession, and reader
feedback. Publication is transactional and immutable: corrections produce a new edition
or question revision rather than rewriting a published edition.

## 9. Persistence model

The schema follows Fhemni's existing explicit `*_ar`, `*_fr`, and `*_en` convention to
remain readable and compatible with H2 tests and PostgreSQL production.

### 9.1 `civic_questionnaire_editions`

- `id UUID PRIMARY KEY`
- `version INTEGER UNIQUE`
- `title_ar`, `title_fr`, `title_en`
- `intro_ar`, `intro_fr`, `intro_en`
- `methodology_ar`, `methodology_fr`, `methodology_en`
- `methodology_version`
- `data_cutoff DATE`
- `content_sha256 VARCHAR(64)`
- `editorial_status`: `DRAFT`, `IN_REVIEW`, `PUBLISHED`, `SUPERSEDED`
- timestamps and publishing reviewer metadata

Only one edition may be current and published. Repository publication obtains a database
lock and updates the old and new markers atomically.

### 9.2 `civic_questions`

- `id UUID PRIMARY KEY`
- `edition_id` foreign key
- stable `question_key`
- existing `policy_topics.code` foreign key
- `sort_order`
- semantic specification and reviewer notes
- `prompt_ar`, `prompt_fr`, `prompt_en`
- `context_ar`, `context_fr`, `context_en`
- positive-pole labels in all three languages
- negative-pole labels in all three languages
- `importance_enabled`
- `editorial_status`
- timestamps

The edition enforces unique question keys and ordering. Publication requires exactly 18
reviewed questions.

### 9.3 `civic_question_sources`

- `id UUID PRIMARY KEY`
- `question_id` foreign key
- publisher, title, URL, optional publication date
- source type and note
- retrieval timestamp and verification status
- sort order

Every published question requires at least one verified authoritative source and a data
cutoff. Programme citations may additionally link to published promise IDs, but are not
required to make the questionnaire available.

### 9.4 Dimensions and deterministic rules

`civic_profile_dimensions` defines edition-specific, Moroccan policy dimensions with
three-language positive and negative pole labels. These are concrete tensions such as
immediate household relief versus longer-term structural investment, not universal
ideological axes.

`civic_question_dimension_weights` maps a question to one or more dimensions using small
reviewed integer coefficients. `civic_profile_tensions` and normalized condition rows
describe editorially reviewed combinations of answers that merit an explanation. Rules
are data-driven but intentionally limited to value ranges and conjunctions; V1 does not
introduce an arbitrary expression language.

### 9.5 Saved profiles and feedback

`saved_civic_profiles` contains user ID, edition ID, validated answer payload, result
snapshot, content digest, and timestamps. It is unique per user and edition unless the
product later supports history.

`civic_question_feedback` records authenticated or rate-limited public feedback against
the exact edition and question key. It follows the existing assessment-report admin
visibility pattern, including open and dismissed states.

## 10. Deterministic calculation

1. Load the immutable published edition by ID.
2. Validate every submitted answer against the edition.
3. Exclude skipped and unanswered questions from denominators.
4. Apply an importance multiplier of `2` only when the question permits it and the user
   selected it; otherwise use `1`.
5. Calculate each theme and dimension only from its answered questions.
6. Suppress a dimension when fewer than its configured minimum number of questions were
   answered.
7. Select curated explanation bands and matching tension rules.
8. Include the contributing question keys in every result observation.
9. Return the edition digest and calculation-methodology version.

No floating-point value is exposed with misleading precision. Displayed normalised
values are rounded consistently and accompanied by words. Unit and property tests verify
that skipped answers have no effect, importance changes only intended weights, ordering
does not change a result, and every explanation is traceable to submitted answers.

## 11. Editorial workflow

1. Create a draft edition cloned from the previous edition or from an empty 18-question
   template.
2. Attach the semantic specification and authoritative sources.
3. Draft all three localisations.
4. Run automated checks for length, double questions, negation, unsupported numbers,
   missing source dates, and cross-language completeness.
5. Preview the complete mobile flow and deterministic results against golden answer sets.
6. Require a second human reviewer for neutrality, source fidelity, scoring direction,
   and translation equivalence.
7. Publish atomically with a content digest.
8. Accept feedback against the immutable question revision; corrections create a new
   edition.

Question selection must not be driven solely by programme frequency. Each item must pass
four gates: public salience, structural evidence, a real policy trade-off, and actionability
within the electoral term.

## 12. Privacy, security, and analytics

- Anonymous answers stay in the browser and are sent only to the stateless calculation
  endpoint; they are not written to application logs, analytics, or the database.
- Analytics may record edition view, question-flow start, aggregate progress milestones,
  completion, skip count, context expansion, and save intent. It must never record answer
  values, inferred dimensions, or result text.
- The profile endpoint accepts a small bounded JSON body, validates all keys and values,
  and returns `no-store`.
- Saved profiles require authentication, explicit consent, ownership enforcement, and
  deletion.
- Admin publication and edits use the existing admin authorization and audit patterns.
- Local storage keys contain edition identifiers but no email, user ID, or authentication
  material.
- Source URLs are rendered safely with the existing external-link protections.

## 13. Failure behaviour

- If the current-edition request fails, show a retry state without losing a locally saved
  in-progress edition.
- If a submitted edition is no longer accepted, return `409` with the current edition ID;
  do not silently reinterpret old answers against new questions.
- If profile calculation fails, keep all answers locally and offer retry.
- If a locale is unsupported, return `400`; never substitute another language silently.
- If editorial validation finds missing localisation or evidence, publication fails with a
  field-level admin error.
- AI-provider failure cannot affect a published questionnaire or public result.

## 14. Testing strategy

### Backend

- Repository integration tests for draft, review, atomic publication, immutable retrieval,
  and saved-profile ownership.
- Unit tests for answer validation, skip handling, weighting, dimension thresholds, tension
  rules, rounding, and traceability.
- Golden tests for representative response sets across all three locales.
- Contract tests proving that every published public field exists in `ar`, `fr`, and `en`.
- Security tests for anonymous calculation, account persistence, CSRF, admin boundaries,
  oversized payloads, and unsupported editions.
- Flyway migration tests in H2 compatibility mode and PostgreSQL container verification.

### Frontend

- Mobile and desktop flow tests in Darija RTL, French LTR, and English LTR.
- Resume-after-refresh, back-navigation, skipped-answer, importance, and recalculation tests.
- Accessibility checks for keyboard order, focus management, labels, contrast, reduced
  motion, and 200% text zoom.
- Tests proving analytics payloads never contain answer or result values.

### Editorial fixtures

- Exactly 18 published questions.
- Three complete localisations per question and result rule.
- At least one verified source per question.
- Balanced positive and negative statement direction across the edition.
- Readability and card-length budgets in all three languages.

## 15. Rollout

1. Add the schema, domain services, deterministic calculation, and protected admin preview
   behind `fhemni.civic-profile.enabled=false`.
2. Curate the first 18 questions and sources in all three languages.
3. Add the mobile-first public flow and methodology page.
4. Run internal golden-result review and a small comprehension pilot across different ages,
   regions, and education levels. The pilot measures wording comprehension and completion,
   not voting intention.
5. Correct ambiguous questions by publishing a new draft revision.
6. Enable the public route, initially labelled beta.
7. Monitor completion, per-question skip rate, time, context-card use, retry rate, and reader
   feedback without collecting answers.

The normal production deploy must continue to run the existing font-integrity guard. This
feature introduces no new font asset or external client runtime.

## 16. Success criteria

- At least 65% of users who answer the first question complete the 18-question flow during
  the beta period.
- Median completion time remains below eight minutes.
- No question has an unexplained skip rate materially above the questionnaire median.
- Every public statement in a result can be traced to submitted answers, a published rule,
  and the immutable edition.
- All content and error states are complete in Darija, French, and English.
- Public calculation remains available with all AI integrations disabled.
- No anonymous answer value appears in application analytics or persistent storage.

## 17. Primary research references

- Arab Barometer, Morocco Wave VIII:
  https://www.arabbarometer.org/wp-content/uploads/AB8-Morocco-Report-ENG.pdf
- HCP, labour market Q2 2026:
  https://www.hcp.ma/attachment/2897232/
- HCP, consumer prices July 2026:
  https://www.hcp.ma/L-Indice-des-prix-a-la-consommation-IPC-du-mois-de-Juillet-2026_a4344.html
- CESE, annual report 2024:
  https://www.cese.ma/media/2025/10/Rapport-annuel-2024-web-1.pdf
- CESE, equitable access to primary healthcare:
  https://www.cese.ma/docs/soins-de-sante-de-base-vers-un-acces-equitable-et-generalise/
- World Bank, Morocco water security and resilience:
  https://www.worldbank.org/en/news/press-release/2023/07/24/new-world-bank-program-in-morocco-supports-efforts-to-boost-water-security-and-resilience-for-all
- Moroccan Competition Council, fuel-price transmission, March 2026:
  https://conseil-concurrence.ma/en/note-on-the-evolution-of-diesel-and-gasoline-prices-on-international-markets-and-their-impact-on-pump-prices-in-the-domestic-market-period-from-march-1-to-march-16-2026/
- Finance Ministry, 2026 compensation report:
  https://www.finances.gov.ma/Publication/db/2026/Rapport-Compensation_Fr.pdf
