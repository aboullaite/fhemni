# Run Fhemni with containers

The included Compose file runs the Spring Boot application with PostgreSQL. It
does not include a public reverse proxy or provider credentials.

Flyway also creates the Spring Session tables in PostgreSQL. Login sessions
therefore survive application-container replacement without requiring Redis.

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
: > .secrets/google_client_id
: > .secrets/google_client_secret
: > .secrets/admin_identities
chmod 444 .secrets/*
```

Add a Gemini key or OAuth values only to the corresponding ignored file. Never
put them in `.env.container`, `compose.yaml`, an image build argument, or Git.
`GEMINI_CREDENTIAL_VERSION` is a non-secret cache-generation label: change it
whenever the Gemini key belongs to a different Google project. Existing public
reports remain unchanged, while chat stays unavailable for them until an admin
temporarily enables `FHEMNI_CONTEXT_MIGRATION_ENABLED` and runs the one-off
migration shown in the admin catalogue.

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
