# 2026 election-result updates

The public result page reads directly from PostgreSQL. A result update therefore does not require an application build or deployment.

The durable recovery artifact is [`data/elections/2026/results.sql`](../../data/elections/2026/results.sql). It must always describe the complete current snapshot, not an incremental patch. This makes the database reproducible after a restore or accidental data loss.

## Update workflow

1. Copy each figure from an official published source. Record its URL and publication time in `incoming_election_snapshot` at the top of the SQL file. Before adding the first national or regional result row, `source_updated_at` must be non-null; `NULL` is allowed only for the empty initial counting snapshot.
2. Edit the `incoming_election_regions`, `incoming_election_party_results`, `incoming_election_region_party_results`, `incoming_election_constituencies`, and `incoming_election_constituency_winners` staging tables in `data/elections/2026/results.sql` with the full snapshot. Never infer missing seats, winners, or votes. Preserve the source spelling of candidate names and use a stable `candidate_key` across corrections. An older `source_updated_at` is rejected before any result row changes. Reusing the same timestamp is accepted only when every staged value exactly matches the stored snapshot; use a newer official revision timestamp for changed figures.
   - Set `turnout_percent` directly when the source publishes an exact turnout percentage without the underlying registered-voter and vote-cast totals. Leave those totals `NULL`; do not reverse-engineer them from the percentage. When `turnout_percent` is `NULL`, the API keeps deriving turnout from the two totals for backwards compatibility.
   - If a source publishes complete party totals before publishing the local/regional-list split, keep each party's known local count in `local_seats`, set `regional_list_seats` to `NULL`, and put the published complete figure in `total_seats`. The API will expose the regional-list metric as unavailable instead of inventing a split.
3. Review these invariants before touching production:
   - every party code exists in `political_parties`;
   - when `regional_list_seats` is present, `total_seats = local_seats + regional_list_seats`; when it is `NULL`, `local_seats` is a verified partial component and must not exceed `total_seats`;
   - national declared seats never exceed 395;
   - when `valid_votes` is present, every party vote is populated and the party total reconciles exactly; keep `valid_votes` `NULL` until the complete party breakdown is available;
   - a `FINAL` or `CORRECTED` snapshot totals exactly 395 seats;
   - `UNKNOWN` never holds national or regional seats;
   - for every region with a known allocation, named winners, summed constituency allocations, and summed regional party seats never exceed that allocation, including while the region is `PARTIAL`;
   - every `FINAL` region totals exactly its configured allocation;
   - once every region is marked `FINAL`, regional allocations total exactly 395 seats; a final national snapshot also requires every region to be final;
   - a party's regional local, regional-list, and total seats never exceed the corresponding national figures;
   - every constituency winner belongs to a party with a declared local seat in the same region;
   - constituency winners never exceed the corresponding regional or national local-seat totals;
   - a provisional or official constituency with a known allocation lists exactly that many winners; partial constituencies may list fewer, never more;
   - a stable candidate key appears in at most one constituency;
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

On a database where Flyway migrations have completed, run the latest committed `results.sql`. It upserts election, region, and constituency metadata; replaces the complete national, regional, and winner snapshot; validates it; and commits atomically.

The application displays a counting state when the snapshot contains no party rows. That is deliberate: the repository contains no invented election result. Replaying an unchanged snapshot leaves election, region, and result-row `updated_at` values untouched. Rows whose values genuinely change receive the transaction time.

## Coalition endpoint capacity

The coalition-alignment endpoint applies both a 60-request-per-minute client limit and a 600-request-per-minute global limit for the active application instance. IPv6 clients are grouped by `/64`, and a full client tracker does not place unrelated visitors in a shared penalty bucket. The current blue-green deployment exposes one active instance, so this protects the database-backed calculation. If the application is horizontally scaled to multiple active replicas, add a shared limit at the gateway or in a distributed store.

## Corrections

For an official correction, update the whole snapshot, set the election status to `CORRECTED`, update `source_updated_at`, and reapply the file. Keep the previous Git revision as the audit trail.
