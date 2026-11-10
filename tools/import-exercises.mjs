#!/usr/bin/env node
// Builds app/src/main/assets/content/exercises.json, the exercise photos, the workout covers
// and credits.json from catalog.mjs and photos.mjs.
//
//   node tools/import-exercises.mjs [--dataset path/to/exercises.json]
//
// Without --dataset the free-exercise-db dist file is fetched from GitHub. A photo is only
// downloaded when its file is missing or its source changed (tools/photo-sources.json remembers
// which source each file came from), so re-running after a catalog edit is cheap. Exits
// non-zero on any catalog mistake (unknown source id, dangling easier/harder link, duplicate
// id) so a broken catalog never reaches the app.

import { mkdir, readFile, writeFile, access, readdir, rm, unlink } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { catalog } from './catalog.mjs';
import { exercisePhotos, covers } from './photos.mjs';

const TOOLS = dirname(fileURLToPath(import.meta.url));
const ROOT = join(TOOLS, '..');
const CONTENT = join(ROOT, 'app', 'src', 'main', 'assets', 'content');
const IMAGES = join(CONTENT, 'images');
const COVERS = join(CONTENT, 'covers');
const SOURCES = join(TOOLS, 'photo-sources.json');
const RAW = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main';
// Wikimedia asks API clients to identify themselves.
const UA = 'HomeFitContentImporter/1.0 (personal fitness app content import)';
const EXERCISE_WIDTH = 720;
const COVER_WIDTH = 960;

const LEVELS = { beginner: 'BEGINNER', intermediate: 'INTERMEDIATE', expert: 'ADVANCED' };
const FOCUS = new Set(['ABS', 'CHEST', 'ARMS', 'LEGS', 'BUTT', 'SHOULDERS_BACK', 'FULL_BODY']);
const EQUIPMENT = new Set(['NONE', 'CHAIR', 'WALL', 'DUMBBELL', 'BAR', 'OUTDOORS']);
const CATEGORIES = new Set(['STRENGTH', 'CARDIO', 'PLYOMETRIC', 'STRETCH']);

async function loadDataset() {
  const i = process.argv.indexOf('--dataset');
  if (i > 0) return JSON.parse(await readFile(process.argv[i + 1], 'utf8'));
  const res = await fetch(`${RAW}/dist/exercises.json`);
  if (!res.ok) throw new Error(`dataset download failed: HTTP ${res.status}`);
  return res.json();
}

const exists = (p) => access(p).then(() => true, () => false);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function fail(errors) {
  for (const e of errors) console.error(`  ✗ ${e}`);
  process.exit(1);
}

/** Commons author, licence and a resized URL for every file named in photos.mjs, in batches. */
async function commonsInfo(files, width) {
  const info = new Map();
  const list = [...new Set(files)];
  for (let i = 0; i < list.length; i += 40) {
    const u = new URL('https://commons.wikimedia.org/w/api.php');
    Object.entries({
      action: 'query', format: 'json', prop: 'imageinfo', iiprop: 'url|extmetadata',
      iiurlwidth: String(width), iiextmetadatafilter: 'LicenseShortName|Artist|ObjectName',
      titles: list.slice(i, i + 40).map((f) => `File:${f}`).join('|'),
    }).forEach(([k, v]) => u.searchParams.set(k, v));
    const res = await fetch(u, { headers: { 'User-Agent': UA } });
    if (!res.ok) throw new Error(`Commons API: HTTP ${res.status}`);
    const json = await res.json();
    const renamed = new Map((json.query.normalized ?? []).map((n) => [n.to, n.from]));
    for (const page of Object.values(json.query.pages)) {
      const requested = (renamed.get(page.title) ?? page.title).replace(/^File:/, '');
      const ii = page.imageinfo?.[0];
      if (!ii) continue;
      const m = ii.extmetadata ?? {};
      info.set(requested, {
        url: ii.thumburl ?? ii.url,
        title: (m.ObjectName?.value ?? requested).replace(/<[^>]+>/g, '').trim(),
        author: (m.Artist?.value ?? 'Unknown').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim(),
        license: m.LicenseShortName?.value ?? 'see source',
        page: ii.descriptionurl,
      });
    }
    await sleep(1000);
  }
  return info;
}

const dataset = await loadDataset();
const byId = new Map(dataset.map((d) => [d.id, d]));
const catalogIds = new Set(catalog.map((c) => c.id));
const ids = new Set();
const errors = [];

for (const entry of catalog) {
  if (ids.has(entry.id)) errors.push(`duplicate id ${entry.id}`);
  ids.add(entry.id);
  if (entry.src && !byId.has(entry.src)) errors.push(`${entry.id}: unknown src "${entry.src}"`);
  if (!/^(x\d+|\d+s)$/.test(entry.measure)) errors.push(`${entry.id}: bad measure "${entry.measure}"`);
}
for (const entry of catalog) {
  for (const link of ['easier', 'harder']) {
    if (entry[link] && !ids.has(entry[link])) errors.push(`${entry.id}: ${link} -> unknown "${entry[link]}"`);
  }
}
for (const [id, spec] of Object.entries(exercisePhotos)) {
  if (!catalogIds.has(id)) errors.push(`photos.mjs: unknown exercise "${id}"`);
  if (spec.kind === 'fedb' && !byId.has(spec.id)) errors.push(`photos.mjs: ${id} -> unknown dataset id "${spec.id}"`);
  if (spec.kind === 'same' && !catalogIds.has(spec.id)) errors.push(`photos.mjs: ${id} -> unknown exercise "${spec.id}"`);
}
if (errors.length) fail(errors);

const commonsFiles = [
  ...Object.values(exercisePhotos).filter(Array.isArray).flat(),
  ...Object.values(covers),
].filter((p) => p.kind === 'commons').map((p) => p.file);
const commonsExercise = await commonsInfo(commonsFiles, EXERCISE_WIDTH);
const commonsCover = await commonsInfo(Object.values(covers).filter((p) => p.kind === 'commons').map((p) => p.file), COVER_WIDTH);
for (const f of commonsFiles) if (!commonsExercise.has(f)) errors.push(`Commons file not found: ${f}`);
if (errors.length) fail(errors);

const sources = (await exists(SOURCES)) ? JSON.parse(await readFile(SOURCES, 'utf8')) : {};
const wanted = new Set();
const credits = [];
let downloaded = 0;

/** Resolves a photo spec to {key, url, ext, credit} — key is what photo-sources.json remembers. */
function resolve(photo, width) {
  if (photo.kind === 'pexels') {
    return {
      key: `pexels:${photo.id}`,
      url: `https://images.pexels.com/photos/${photo.id}/pexels-photo-${photo.id}.jpeg?auto=compress&cs=tinysrgb&w=${width}`,
      ext: 'jpg',
      credit: { title: `Pexels photo ${photo.id}`, author: 'Pexels contributor', license: 'Pexels License', page: `https://www.pexels.com/photo/${photo.id}/` },
    };
  }
  const info = (width === COVER_WIDTH ? commonsCover : commonsExercise).get(photo.file);
  return {
    key: `commons:${photo.file}@${width}`,
    url: info.url,
    ext: /\.png$/i.test(photo.file) ? 'png' : 'jpg',
    credit: { title: info.title, author: info.author, license: info.license, page: info.page },
  };
}

async function fetchTo(rel, key, url) {
  const dest = join(CONTENT, rel);
  wanted.add(rel);
  if (sources[rel] === key && (await exists(dest))) return;
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`${rel}: HTTP ${res.status} from ${url}`);
  await mkdir(dirname(dest), { recursive: true });
  await writeFile(dest, Buffer.from(await res.arrayBuffer()));
  sources[rel] = key;
  downloaded++;
}

/** Downloads one exercise's photos and returns their paths relative to images/. */
async function photosFor(entry, src) {
  const spec = exercisePhotos[entry.id];
  if (spec?.kind === 'same') return null; // filled in once the other exercise is done
  const fromDataset = spec?.kind === 'fedb' ? byId.get(spec.id) : spec ? null : src;
  const out = [];
  if (fromDataset) {
    const frames = spec?.frames ?? fromDataset.images.map((_, n) => n);
    for (const [n, frame] of frames.entries()) {
      const path = fromDataset.images[frame];
      const rel = `images/${entry.id}/${n}.jpg`;
      await fetchTo(rel, `fedb:${path}`, `${RAW}/exercises/${path}`);
      out.push(relative(IMAGES, join(CONTENT, rel)).replaceAll('\\', '/'));
    }
  } else if (Array.isArray(spec)) {
    for (const [n, photo] of spec.entries()) {
      const r = resolve(photo, EXERCISE_WIDTH);
      const rel = `images/${entry.id}/${n}.${r.ext}`;
      await fetchTo(rel, r.key, r.url);
      credits.push({ file: rel, ...r.credit });
      out.push(`${entry.id}/${n}.${r.ext}`);
    }
  }
  return out;
}

const out = [];
for (const entry of catalog) {
  const src = entry.src ? byId.get(entry.src) : null;
  const images = await photosFor(entry, src);

  const record = {
    id: entry.id,
    name: entry.name,
    category: entry.category ?? (src?.category === 'stretching' ? 'STRETCH' : 'STRENGTH'),
    level: entry.level ?? LEVELS[src?.level] ?? 'BEGINNER',
    equipment: entry.equipment ?? 'NONE',
    focus: entry.focus,
    primaryMuscles: entry.primaryMuscles ?? src?.primaryMuscles ?? [],
    secondaryMuscles: entry.secondaryMuscles ?? src?.secondaryMuscles ?? [],
    measure: entry.measure,
    perSide: entry.perSide ?? false,
    met: entry.met,
    noJump: entry.noJump ?? true,
    instructions: entry.instructions ?? src?.instructions ?? [],
    tips: entry.tips ?? [],
    images,
    easier: entry.easier ?? null,
    harder: entry.harder ?? null,
  };

  if (!record.focus?.length || !record.focus.every((f) => FOCUS.has(f))) errors.push(`${entry.id}: bad focus`);
  if (!EQUIPMENT.has(record.equipment)) errors.push(`${entry.id}: bad equipment ${record.equipment}`);
  if (!CATEGORIES.has(record.category)) errors.push(`${entry.id}: bad category ${record.category}`);
  if (typeof record.met !== 'number') errors.push(`${entry.id}: missing met`);
  if (!record.instructions.length) errors.push(`${entry.id}: no instructions`);
  out.push(record);
}
for (const record of out) {
  const spec = exercisePhotos[record.id];
  if (spec?.kind === 'same') record.images = out.find((r) => r.id === spec.id).images;
}
if (errors.length) fail(errors);

for (const [key, photo] of Object.entries(covers)) {
  const r = resolve(photo, COVER_WIDTH);
  const rel = `covers/${key}.jpg`;
  await fetchTo(rel, r.key, r.url);
  credits.push({ file: rel, ...r.credit });
}

// Drop files nothing references any more (an exercise removed or re-sourced), so the APK only
// carries what it uses.
for (const dir of [IMAGES, COVERS]) {
  if (!(await exists(dir))) continue;
  for (const entry of await readdir(dir, { recursive: true, withFileTypes: true })) {
    if (!entry.isFile()) continue;
    const rel = relative(CONTENT, join(entry.parentPath, entry.name)).replaceAll('\\', '/');
    if (!wanted.has(rel)) await unlink(join(CONTENT, rel));
  }
  for (const sub of await readdir(dir, { withFileTypes: true })) {
    if (sub.isDirectory() && !(await readdir(join(dir, sub.name))).length) await rm(join(dir, sub.name), { recursive: true });
  }
}
for (const rel of Object.keys(sources)) if (!wanted.has(rel)) delete sources[rel];

await mkdir(CONTENT, { recursive: true });
await writeFile(join(CONTENT, 'exercises.json'), JSON.stringify(out, null, 1) + '\n');
credits.sort((a, b) => a.file.localeCompare(b.file));
await writeFile(join(CONTENT, 'credits.json'), JSON.stringify(credits, null, 1) + '\n');
await writeFile(SOURCES, JSON.stringify(Object.fromEntries(Object.entries(sources).sort()), null, 1) + '\n');

const withPhotos = out.filter((r) => r.images.length);
const without = out.filter((r) => !r.images.length).map((r) => r.id);
console.log(`exercises.json: ${out.length} exercises (${withPhotos.length} with photos), ${Object.keys(covers).length} covers, ${downloaded} files downloaded`);
if (without.length) console.log(`no photo yet: ${without.join(', ')}`);
