# Run Fhemni with containers

The included Compose file runs the Spring Boot application with PostgreSQL. It
does not include a public reverse proxy or provider credentials.

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
