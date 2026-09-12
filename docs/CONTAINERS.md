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
: > .secrets/discord_client_id
: > .secrets/discord_client_secret
: > .secrets/mailgun_api_key
: > .secrets/admin_identities
: > .secrets/google_cloud_media_credentials
: > .secrets/google_cloud_media_writer_credentials
chmod 444 .secrets/*
```

Add Gemini, OpenAI, OAuth, or Mailgun API key values only to the corresponding
ignored file. Never
put them in `.env.container`, `compose.yaml`, an image build argument, or Git.
`GEMINI_CREDENTIAL_VERSION` is a non-secret cache-generation label: change it
whenever the Gemini key belongs to a different Google project. Existing public
reports remain unchanged, while chat stays unavailable for them until an admin
temporarily enables `FHEMNI_CONTEXT_MIGRATION_ENABLED` and runs the one-off
migration shown in the admin catalogue.

To enable Discord sign-in, create an application in the Discord Developer
Portal, add this exact OAuth2 redirect, and put the application's client ID and
client secret in the matching secret files:

```text
https://fhemni.ma/login/oauth2/code/discord
```

No bot token, guild installation, or server permission is required. Discord
sign-in requests only the `identify` and `email` scopes.

After deploying, add the public legal URLs to the Discord application's General
Information page:

```text
Terms of Service: https://fhemni.ma/terms
Privacy Policy:   https://fhemni.ma/privacy
```

Make sure `privacy@fhemni.ma` is an active mailbox or forwarding alias because
the Privacy Policy uses it for account and Discord-data deletion requests.

To enable email magic-link sign-in, set the Mailgun sending domain, regional API
base URL, and sender in `.env.container`; put only the private API key in
`.secrets/mailgun_api_key`. Then set `FHEMNI_MAGIC_LINK_ENABLED=true`. Use
`https://api.eu.mailgun.net` for an EU-region domain or
`https://api.mailgun.net` for a US-region domain. The production base URL is
already `https://fhemni.ma`, and links expire after 15 minutes by default. The
Mailgun API domain remains `fhemni.aboullaite.me`, while the visible production
sender is `Fhemni.ma <noreply@fhemni.ma>`. Keep the sender domain's SPF, DKIM,
and DMARC configuration valid before deployment.

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
docker compose --env-file .env.container --profile media-generation up --build media-worker
```

Stop the worker when the queued batch reaches media review. Narration alternates
by section between
`FHEMNI_PROGRAMME_MEDIA_TTS_VOICE` and
`FHEMNI_PROGRAMME_MEDIA_TTS_SECONDARY_VOICE`; the defaults are Charon and Kore.
The default narration model is `gemini-3.1-flash-tts-preview`.
Independent narration and illustration sections run with bounded parallelism;
`FHEMNI_PROGRAMME_MEDIA_PROVIDER_CONCURRENCY` defaults to `4` and can be lowered
when provider quotas are tight. Publishing a revised feasibility assessment does
not automatically replace an existing briefing, so regenerate and review the
party media whenever its published assessments materially change.

The opt-in `ProgrammeMediaRerenderBatch` reuses reviewed scripts and retained
illustrations without changing application rows. Run it only with
`FHEMNI_PROGRAMME_JOB_WORKER_ENABLED=false`; the batch refuses to start while the
assessment worker is enabled. Include the source media's `image_model` in every
JSONL input row so retained assets are resolved with their recorded provenance.
Narration cache entries are content-addressed by model, voice, pronunciation
version, and prepared text, and are written atomically, so a corrected script or
interrupted run cannot silently reuse partial audio.

Programme videos render Arabic with the bundled Noto Sans Arabic Medium and Bold
files under `src/main/resources/static/assets/fonts/`. Keep both `.ttf` files in
the Docker build context and application JAR; the renderer copies them from the
classpath and fails the render if either resource is missing. This keeps local
and production typography identical and avoids depending on host-installed fonts.

Browser fonts are durable, immutable public assets in the dedicated
`fhemni-public-assets-mohamed-playground` GCS bucket. The stylesheet references
checksum-versioned GCS objects for Arabswell, Tajawal, and the Video.js icon font;
never replace an object in place. Arabswell is the Moroccan display face used for
large Arabic headings and party names, while Tajawal remains the body and compact
card face. Run the font guard before a deployment and again after switching live
traffic:

```bash
./scripts/build-css.sh
./scripts/verify-web-fonts.sh
FHEMNI_SITE_URL=https://fhemni.ma ./scripts/verify-web-fonts.sh
```

The guard downloads every referenced font and checks its pinned SHA-256. With a
site URL it also verifies that the live page serves the expected cache-busted CSS,
that the live CSS still points to the approved Arabswell object, and that the
proxy's Content Security Policy permits GCS fonts. A deployment must be rolled
back if any check fails. The renderer's bundled Noto files are
also mirrored under the bucket's `fonts/rendering/` prefix as canonical backups,
but rendering deliberately remains local and does not depend on GCS availability.

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
