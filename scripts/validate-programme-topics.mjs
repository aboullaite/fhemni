import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

// Validate the curated catalog against the archived extraction without rewriting
// either file. Topic choices are editorial; this check verifies their integrity.
const root = fileURLToPath(new URL('../', import.meta.url));
const research = resolve(root, 'data/government/2026-2031/research');
const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
const sourceRecords = readdirSync(research)
  .filter((name) => /^commitments-.*\.json$/.test(name))
  .sort()
  .flatMap((name) => readJson(resolve(research, name)).commitments);
const catalog = readJson(resolve(root, 'src/main/resources/government/programme-topics.json'));

assert.equal(catalog.version, 1);
assert.equal(sourceRecords.length, 595, 'Review source coverage when the archive changes');
const sourceBySlug = new Map(sourceRecords.map((row) => [`programme-${row.id}`, row]));
assert.equal(sourceBySlug.size, sourceRecords.length, 'Duplicate source identities');

const topics = new Map();
for (const topic of catalog.topics) {
  assert.match(topic.code, /^[A-Z][A-Z0-9_]*$/);
  assert(!topics.has(topic.code), `Duplicate topic ${topic.code}`);
  assert.deepEqual(Object.keys(topic.label).sort(), ['ar', 'en', 'fr']);
  for (const label of Object.values(topic.label)) {
    assert.equal(typeof label, 'string');
    assert(label.trim().length > 0, `Missing translation for ${topic.code}`);
  }
  topics.set(topic.code, 0);
}
assert(topics.has('OTHER'), 'Retain an explicit fallback for unresolved subjects');
assert(!topics.has('WATER_ENERGY_ENVIRONMENT'), 'Water, energy and climate require separate subjects');

const seen = new Set();
let unclassified = 0;
for (const entry of catalog.entries) {
  assert.deepEqual(Object.keys(entry).sort(), ['slug', 'sourceAnchor', 'titleAr', 'topicCodes']);
  assert(!seen.has(entry.slug), `Duplicate entry ${entry.slug}`);
  seen.add(entry.slug);
  const source = sourceBySlug.get(entry.slug);
  assert(source, `Unknown source identity ${entry.slug}`);
  assert.equal(entry.titleAr, source.titleAr, `Title mismatch for ${entry.slug}`);
  assert.equal(entry.sourceAnchor, source.sourceAnchor, `Anchor mismatch for ${entry.slug}`);
  assert(Array.isArray(entry.topicCodes));
  assert(entry.topicCodes.length >= 1 && entry.topicCodes.length <= 3, `Invalid topic count: ${entry.slug}`);
  assert.equal(new Set(entry.topicCodes).size, entry.topicCodes.length, `Duplicate assignment: ${entry.slug}`);
  for (const code of entry.topicCodes) {
    assert(topics.has(code), `Unknown topic ${code} on ${entry.slug}`);
    topics.set(code, topics.get(code) + 1);
  }
  if (entry.topicCodes.includes('OTHER')) {
    assert.equal(entry.topicCodes.length, 1, `OTHER cannot accompany a classified topic: ${entry.slug}`);
    unclassified++;
  }
}
assert.equal(seen.size, sourceBySlug.size, 'Every archived commitment must have a reviewed entry');
for (const slug of sourceBySlug.keys()) assert(seen.has(slug), `Missing entry ${slug}`);

const geography = readJson(resolve(root, 'src/main/resources/government/programme-geography.json'));
const regionCodes = new Set(['beni-mellal-khenifra','casablanca-settat','dakhla-oued-ed-dahab','draa-tafilalet','fes-meknes','guelmim-oued-noun','laayoune-sakia-el-hamra','marrakech-safi','oriental','rabat-sale-kenitra','souss-massa','tanger-tetouan-al-hoceima']);
const geographicSlugs = new Set(), mappedRegions = new Set();
for (const entry of geography.entries) {
  assert(!geographicSlugs.has(entry.slug), `Duplicate geography entry ${entry.slug}`);
  geographicSlugs.add(entry.slug);
  const source = sourceBySlug.get(entry.slug);
  assert(source, `Unknown geography entry ${entry.slug}`);
  assert.equal(entry.titleAr, source.titleAr);
  assert.equal(entry.sourceAnchor, source.sourceAnchor);
  assert(entry.places.length > 0);
  for (const place of entry.places) {
    assert(regionCodes.has(place.regionCode), `Unknown region ${place.regionCode}`);
    assert(source.pages.includes(place.printedPage), `Page mismatch ${entry.slug}`);
    assert(place.sourceExcerpt.trim());
    for (const locale of ['ar','fr','en']) assert(place.place[locale]?.trim());
    mappedRegions.add(place.regionCode);
  }
}

console.log(JSON.stringify({
  entries: seen.size,
  topics: topics.size,
  unclassified,
  geographicallyLinkedEntries: geographicSlugs.size,
  mappedRegions: mappedRegions.size,
  memberships: Object.fromEntries(topics),
}, null, 2));
