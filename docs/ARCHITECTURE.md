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

## Persistence

Flyway owns the schema. Local development uses file-backed H2 in PostgreSQL
compatibility mode; PostgreSQL is supported for container deployments.

Catalogue entries, external identities, suggestions, votes, AI usage records,
completed analysis revisions, and private Gemini video contexts are durable.
Follow-up conversation state is bounded and process-local. Restarting does not
remove a published report.

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
- the default allowance is five rounds per user per Monday-to-Monday UTC week;
- global ceilings default to 50 rounds per UTC hour and 500 per UTC day;
- question text, provider context depth, response tokens, and request time are
  bounded;
- the production kill switch remains authoritative regardless of the UI.

Chat answers are intentionally not shared or cached across users. They may
depend on private conversation context, while the underlying published video
analysis remains the reusable shared artifact.

## Application modules

- `catalog`: catalogue metadata, suggestions, votes, and batch selection
- `analysis`: lifecycle, durable revisions, conversations, and progress events
- `gemini`: provider clients, schemas, prompts, and usage extraction
- `identity`: OAuth/OIDC users, roles, and authorization helpers
- `cost`: durable request reservations and bounded usage policies
- `web`: public, authenticated, and administrator HTTP boundaries
- `video`: YouTube URL validation and canonicalization

## Trust rules

- A statement in a video is evidence of what was said, not proof it is true.
- Public readers can access only the explicitly published revision.
- Only authenticated users can submit or vote on suggestions.
- Only administrators can create, reprocess, review, or publish analyses.
- Provider keys remain server-side and are never returned to browser code.
- Automated tests use local stubs and must never consume external AI quota.
