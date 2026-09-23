# 2026 election-result updates

The public result page reads directly from PostgreSQL. A result update therefore does not require an application build or deployment.

The durable recovery artifact is [`data/elections/2026/results.sql`](../../data/elections/2026/results.sql). It must always describe the complete current snapshot, not an incremental patch. This makes the database reproducible after a restore or accidental data loss.

## Update workflow

1. Copy each figure from an official published source. Record its URL and publication time in `incoming_election_snapshot` at the top of the SQL file.
2. Edit the `incoming_election_regions`, `incoming_election_party_results`, and `incoming_election_region_party_results` staging tables in `data/elections/2026/results.sql` with the full snapshot. Never infer missing seats or votes. An older `source_updated_at` is rejected before any result row changes. Reusing the same timestamp is accepted only when every staged value exactly matches the stored snapshot; use a newer official revision timestamp for changed figures.
3. Review these invariants before touching production:
   - every party code exists in `political_parties`;
   - `total_seats = local_seats + regional_list_seats` for every row;
   - national declared seats never exceed 395;
   - a `FINAL` or `CORRECTED` snapshot totals exactly 395 seats;
   - `UNKNOWN` never holds national or regional seats;
   - every `FINAL` region totals exactly its configured allocation;
   - a party's regional total never exceeds its national total;
   - the source URL and source timestamp match the figures being entered.
4. Apply the committed file through the existing secure PostgreSQL access path:

   ```sh
   psql "$DATABASE_URL" --file data/elections/2026/results.sql
   ```

   `ON_ERROR_STOP`, a transaction, an advisory lock, and validation blocks make a failed update roll back as one unit.
5. Verify the public read model:

   ```sh
   curl --fail --silent 'https://fhemni.ma/api/catalog/elections/2026/results?lang=ar'
   curl --fail --silent 'https://fhemni.ma/elections/2026'
   ```

6. Commit the exact applied snapshot. Do not commit database credentials, dumps, or temporary source files.

## Recovery

On a database where Flyway migrations have completed, run the latest committed `results.sql`. It upserts election and region metadata, replaces the complete national and regional result snapshot, validates it, and commits atomically.

The application displays a counting state when the snapshot contains no party rows. That is deliberate: the repository contains no invented election result. Replaying an unchanged snapshot leaves election, region, and result-row `updated_at` values untouched. Rows whose values genuinely change receive the transaction time.

## Corrections

For an official correction, update the whole snapshot, set the election status to `CORRECTED`, update `source_updated_at`, and reapply the file. Keep the previous Git revision as the audit trail.
