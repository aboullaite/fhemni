# Constituency-level urban/rural representation

## Goal

Replace the regional-average urbanization proxy with an auditable measure tied to the 92 local electoral constituencies. The feature describes the territorial context of local seats. It does not estimate how urban or rural residents voted.

## Data model

- Maintain a versioned crosswalk from each election constituency code to the HCP commune or arrondissement codes that legally compose it.
- Use the 2024 HCP urban and rural population published for those units through Morocco Communes dataset 1.8.0.
- Precompute one immutable browser snapshot per constituency: total, urban, and rural population; allocated local seats; component unit codes; and evidence metadata.
- Keep the official decree and HCP/Morocco Communes provenance in the snapshot header.

The crosswalk must be curated from Decree 2.11.603. Name matching may assist preparation but can never become runtime evidence. Split constituencies such as Fes, Rabat-Sale, Marrakech, Taroudant, Khemisset, Kenitra, Taounate, and Azilal require explicit component lists.

## Measure

For a party with local winners in constituencies `c`:

`urban index = sum(local seats won in c * urban population share of c) / party local seats`

The national comparison is the same index across all 305 allocated local seats, not the raw national population share. This compares a party's local-seat footprint with the geography of the local-seat map.

The 90 regional-list seats are excluded because they are not assigned to a local constituency. The interface must say this in Arabic, French, and English. Vote claims are out of scope because the published election snapshot does not provide commune-level votes.

## Validation and failure behavior

The feature fails closed unless all of the following hold:

- exactly 92 unique constituencies and 305 allocated local seats;
- exactly 305 local winners, with each constituency's winner count equal to its allocation;
- every current constituency has exactly one demographic record and no extra record exists;
- each demographic record has positive integer population figures and `urban + rural = total`;
- every party's computed local-seat count reconciles with the election snapshot;
- dataset version, census year, source URL, decree URL, and revision date are present.

There is no fallback to regional population averages.

## Interface

- Replace the current region-based title and explanation with a constituency/local-seat definition.
- Plot each party on a rural-to-urban continuum using local seats only.
- Mark the 305-local-seat baseline.
- Show the party's local-seat count beside the marker.
- The detail table lists constituency, population, urban share, and local seats won.
- The source and method note links to the pinned HCP/Morocco Communes source and the official constituency decree.

## Testing

- Unit tests cover a mixed urban/rural constituency, exclusion of regional-list seats, the 305-seat baseline, and input immutability.
- Negative tests cover missing, duplicate, extra, and inconsistent constituency demographics and mismatched local winners.
- Dataset tests audit the full 92/305 snapshot and source metadata.
- Rendering tests assert the 305/90 limitation and constituency wording in Arabic, French, and English.

