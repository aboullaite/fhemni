# Architecture

Fhemni is one Spring Boot application with a small vanilla web frontend. It is
kept as a modular monolith so the public catalogue, identity boundary, analysis
workflow, and persistence model remain easy to run and inspect.

## Processing flow

1. A public YouTube URL is parsed and canonicalized.
2. Free YouTube metadata is cached under the video ID.
3. An administrator starts an analysis in Moroccan Darija, French, or English.
4. Gemini Interactions performs agentic video understanding and returns a
   structured briefing plus an interaction ID.
5. Checkable claims are passed to a separate Spring AI fact-checking client with
   Google Search grounding.
6. The completed result is saved as an immutable analysis revision.
7. An administrator reviews and publishes one revision for the catalogue entry.
8. Published reports are public; drafts and processing events remain private.

## Integration boundary

`VideoIntelligenceGateway` isolates provider-specific calls from controllers and
workflow orchestration.

The Google Gen AI Java SDK handles the Interactions API because Fhemni needs
agentic video processing, processing steps, and continuation through a previous
interaction ID. Spring AI handles the independent evidence pass through
`ChatClient`, provider-native structured output, and Google Search grounding.

This separation is intentional: video understanding answers “what was said?”,
while fact checking asks “what does external evidence support?”.

Party-programme feasibility has a separate provider boundary. The configured
mode is `gemini`, `openai`, or `consensus`. Consensus runs an independent Gemini
assessment, an independent OpenAI assessment with web search, and an OpenAI
reconciliation that must resolve disagreements from cited evidence. The final
draft records its methodology, mode, and model names before editorial review.
Changing mode affects only future drafts; stored and published assessments keep
their original attribution. OpenAI is never used for video analysis or chat.
Party-programme chat is a separate Gemini interaction with no search tool. Its
context is assembled only from one party's verified, published 2026 programme,
published promises, and published feasibility assessments and evidence.

## Persistence

Flyway owns the schema. Local development uses file-backed H2 in PostgreSQL
compatibility mode; PostgreSQL is supported for container deployments.

Catalogue entries, external identities, suggestions, votes, AI usage records,
completed analysis revisions, and private Gemini video contexts are durable.
Login sessions are stored in the same database through Spring Session JDBC, so
an application restart or traffic switch does not sign users out. Video and
party-programme follow-up conversation state is bounded and process-local; it
must move to shared storage before multiple application instances can serve
chat traffic concurrently. Restarting does not remove a published report.

A completed revision is reused only when the video, language, prompt versions,
model identities, and live/demo mode match. Reprocessing creates a new draft and
does not replace the published revision until explicit publication.

Report reuse is independent from the Gemini credential. Chat contexts are keyed
separately by video, language, model, context-prompt version, and a non-secret
credential-generation label. Rotating to a key from another Google project can
therefore rebuild chat contexts once without replacing reviewed public reports
or repeating the independent fact-check stage.

Reuse is checked before reserving AI usage. Repeated submissions return the
stored revision, while duplicate in-flight requests on the single application
instance join the existing session. Opening a catalogue report never starts a
new Gemini analysis.

## Chat cost boundary

Chat is protected independently from analysis:

- only authenticated users can submit questions;
- each submitted question is one billable chat round, whether it succeeds or
  the provider fails after accepting it;
- the default allowance is 20 rounds per user per UTC day, with a 100-round
  Monday-to-Monday UTC weekly ceiling;
- global ceilings default to 50 rounds per UTC hour and 500 per UTC day;
- question text, provider context depth, response tokens, and request time are
  bounded;
- the production kill switch remains authoritative regardless of the UI.

Chat answers are intentionally not shared or cached across users. They may
depend on private conversation context, while the underlying published video
analysis and party-programme evidence remain reusable shared artifacts. Party
chat labels whether an answer came from the official programme, the published
feasibility review, both, or was not found. Returned citations are checked
against server-owned source identifiers before an answer is exposed.

## Application modules

- `catalog`: catalogue metadata, suggestions, votes, batch selection, and the
  public party sheets aggregated from published analyses
- `analysis`: lifecycle, durable revisions, conversations, and progress events
- `gemini`: provider clients, schemas, prompts, and usage extraction
- `identity`: OAuth/OIDC users, roles, and authorization helpers
- `cost`: durable request reservations and bounded usage policies
- `web`: public, authenticated, and administrator HTTP boundaries
- `video`: YouTube URL validation and canonicalization

## Trust rules

- A statement in a video is evidence of what was said, not proof it is true.
- Public readers can access only the explicitly published revision.
- Guest and party sheets are computed only from explicitly published revisions.
  Speaker-to-party affiliations come from a database-backed editorial directory
  (Flyway-seeded from verified sources, changes reviewed like code);
  unaffiliated speakers never appear on party sheets, and passages shown side
  by side are never labelled as contradictions.
- Member names follow the site language using the verified French/Arabic
  spellings; unverified names are shown exactly as written in the episode.
  Statements keep their exact wording from the episode.
- Only authenticated users can submit or vote on suggestions.
- Only administrators can create, reprocess, review, or publish analyses.
- Provider keys remain server-side and are never returned to browser code.
- Party chat never mixes parties, performs live web research, or gives voting
  recommendations. Feasibility answers require published evidence citations.
- Automated tests use local stubs and must never consume external AI quota.
