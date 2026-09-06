# Contributing to Fhemni

Thank you for helping make long public-interest videos easier to understand.

## Before starting

- Search existing issues before opening a new one.
- Use an issue to discuss substantial behavior, schema, API, or interface changes.
- Keep pull requests focused and explain the user-visible outcome.
- Never submit real API keys, OAuth credentials, access tokens, user data,
  database files, or deployment details.

Do not use a public issue for a vulnerability. Follow
[the security policy](.github/SECURITY.md).

## Development setup

Requirements: Java 26. Maven is available through the wrapper.

```bash
./run-local.sh
```

The application uses an ignored local H2 database by default. A Gemini key is
optional; without it, the project runs in demo mode. Copy `.env.example` to
`.env` for local settings and keep that file untracked.

For frontend work, install the pinned standalone Tailwind and DaisyUI tools,
then rebuild the committed stylesheet:

```bash
./scripts/setup-tailwind.sh
./scripts/build-css.sh
```

## Pull-request checklist

```bash
./mvnw verify
./scripts/build-css.sh
git diff --check
```

A pull request should:

- include tests for changed behavior;
- avoid real external AI calls in automated tests;
- preserve public/private analysis and authorization boundaries;
- preserve multilingual and RTL behavior;
- distinguish statements in a video from independently supported facts;
- avoid recommendations for a political party or candidate;
- update public documentation when configuration or behavior changes.

Generated reports, screenshots, and fixtures must not contain private user data.
Use invented identities and local stub services in tests.

## Contribution terms

By submitting a contribution for inclusion in Fhemni, you agree that your
contribution may be distributed under the [Elastic License 2.0](LICENSE). You
represent that you have the right to submit it under those terms.

Keep existing copyright, license, attribution, and third-party notices intact.
The license does not grant rights to use the Fhemni name or logo beyond accurate
reference to the original project; see [TRADEMARKS.md](TRADEMARKS.md).
