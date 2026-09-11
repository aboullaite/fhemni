# Run Fhemni with containers

The included Compose file runs the Spring Boot application with PostgreSQL. It
does not include a public reverse proxy or provider credentials.

Flyway also creates the Spring Session tables in PostgreSQL. Login sessions
therefore survive application-container replacement without requiring Redis.
The first upgrade from in-memory sessions signs existing users out once because
those old sessions only exist inside the retiring application process. Sessions
created after that upgrade survive normal restarts and deployments.

Copy the non-secret settings:

```bash
cp .env.container.example .env.container
mkdir -m 700 .secrets
```

Create the required local secret files. Empty optional files keep the matching
integration disabled:

```bash
openssl rand -base64 32 > .secrets/postgres_password
: > .secrets/gemini_api_key
: > .secrets/openai_api_key
: > .secrets/google_client_id
: > .secrets/google_client_secret
: > .secrets/admin_identities
: > .secrets/google_cloud_media_credentials
: > .secrets/google_cloud_media_writer_credentials
chmod 444 .secrets/*
```

Add Gemini, OpenAI, or OAuth values only to the corresponding ignored file. Never
put them in `.env.container`, `compose.yaml`, an image build argument, or Git.
`GEMINI_CREDENTIAL_VERSION` is a non-secret cache-generation label: change it
whenever the Gemini key belongs to a different Google project. Existing public
reports remain unchanged, while chat stays unavailable for them until an admin
temporarily enables `FHEMNI_CONTEXT_MIGRATION_ENABLED` and runs the one-off
migration shown in the admin catalogue.

Party-programme fact checking defaults to Gemini. To run the OpenAI-only or
consensus mode, put the key in `.secrets/openai_api_key` and set
`FHEMNI_PROGRAMME_FACT_CHECK_MODE=openai` or `consensus` in `.env.container`.
Switching back to `gemini` does not relabel or recompute cached assessments.

The standard production stack does not run programme-media generation. It requires
non-empty `FHEMNI_PROGRAMME_MEDIA_GCS_PROJECT` and
`FHEMNI_PROGRAMME_MEDIA_GCS_BUCKET` values in `.env.container`; Compose stops with
a clear configuration error when either is missing. It also needs a read-only signing identity in
`.secrets/google_cloud_media_credentials`. Give that identity object-viewer access
only to the configured private bucket. The web application uses it to issue
short-lived media links; no bucket or object needs public access.

Generate and review media before deployment with the opt-in `media-generation`
Compose profile and a separate writer identity in
`.secrets/google_cloud_media_writer_credentials`. That worker uploads immutable
assets to the same private bucket and is never started by the normal deployment:

```bash
docker compose --profile media-generation up --build media-worker
```

Stop the worker when the queued batch reaches media review. Narration alternates
by section between
`FHEMNI_PROGRAMME_MEDIA_TTS_VOICE` and
`FHEMNI_PROGRAMME_MEDIA_TTS_SECONDARY_VOICE`; the defaults are Charon and Kore.
For local development outside Compose, keep the default local media storage when
GCS-backed programme media is not needed.

Start the stack:

```bash
docker compose --env-file .env.container up -d --build
docker compose --env-file .env.container ps
```

Fhemni binds to `127.0.0.1:8080` by default and PostgreSQL has no published host
port. Stop it with:

```bash
docker compose --env-file .env.container down
```

The named PostgreSQL volume survives `down`. Do not use `down --volumes` unless
you intentionally want to remove the database.
