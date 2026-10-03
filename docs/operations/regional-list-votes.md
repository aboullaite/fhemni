# Regional-list vote backfill

Regional-list votes belong to a party's list in a specific region. Every elected member of that list shares the count; it must never be summed across members as if each had personal votes.

Migration V60 adds nullable `election_region_party_results.regional_votes`. The API exposes that value on the region/party and as `listVotes` on its regional-list winners. Missing evidence stays null, not zero. The winner's existing source fields describe the winner announcement; the vote evidence is the elections.ma archive identified by the backfill script.

## Evidence and validation

`scripts/elections/backfill-regional-votes.mjs` verifies the actual bytes of the committed historical dataset against a pinned SHA-256 before reading it. Its regional observations come from the archived elections.ma 2026 results page, with the original regional query IDs retained in the generated SQL. The script checks all 235 list rows across 12 contests against each contest's vote/seat totals and against the national regional-list components: 4,838,149 ballots and 90 seats. Source IDs are explicitly mapped to database codes for this election only.

The backfill updates only matching, existing region/party rows. It does not add parties without seats to the regional seat presentation. There are 94 matching rows in the current snapshot; every one of the 90 regional winners belongs to a verified list. An absent regional contest for a party is not synthesized as zero.

## Applying

1. Deploy the code and apply V60 normally through Flyway.
2. Save a pre-update database backup and confirm the target is the intended environment.
3. Generate the operational SQL outside the checkout:

   ```sh
   node scripts/elections/backfill-regional-votes.mjs > /private/tmp/regional-list-votes.sql
   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 --file /private/tmp/regional-list-votes.sql
   ```

   The transaction locks relevant tables, verifies national totals, regional allocations and all 90 winner-to-list mappings, then changes only `regional_votes`. Conflicting non-null counts abort rather than silently overwriting a correction. An identical replay updates zero rows. It does not alter seats, winner identities, local votes, timestamps, turnout, or public source labels.

4. Check `/api/catalog/elections/2026/results?lang=ar` (also `fr` and `en`): every regional winner has its region/party's `listVotes`, all 305 local-winner votes are unchanged, and all national figures remain unchanged. Check both the regional cards and “Who represents me?” on `/elections/2026`.
5. Retain the applied SQL and a complete post-update election dump as operational artifacts, not committed files. On a fresh restoration using a snapshot without this column, reapply this validated backfill after restoring the full election snapshot. The repository's empty `results.sql` template is not a production backup.

No source publication time is inferred from retrieval or backfill time.
