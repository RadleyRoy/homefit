#!/usr/bin/env node
// Builds app/src/main/assets/content/exercises.json and its photos from catalog.mjs.
//
//   node tools/import-exercises.mjs [--dataset path/to/exercises.json]
//
// Without --dataset the free-exercise-db dist file is fetched from GitHub. Photos are only
// downloaded when missing, so re-running after a catalog edit is cheap. Exits non-zero on any
// catalog mistake (unknown source id, dangling easier/harder link, duplicate id) so a broken
// catalog never reaches the app.

import { mkdir, readFile, writeFile, access, readdir, rm } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { catalog } from './catalog.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const CONTENT = join(ROOT, 'app', 'src', 'main', 'assets', 'content');
const IMAGES = join(CONTENT, 'images');
const RAW = 'https://raw.githubusercontent.com/yuhonas/free-exercise-db/main';

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

async function downloadImage(srcPath, dest) {
  if (await exists(dest)) return false;
  const res = await fetch(`${RAW}/exercises/${srcPath}`);
  if (!res.ok) throw new Error(`image ${srcPath}: HTTP ${res.status}`);
  await mkdir(dirname(dest), { recursive: true });
  await writeFile(dest, Buffer.from(await res.arrayBuffer()));
  return true;
}

function fail(errors) {
  for (const e of errors) console.error(`  ✗ ${e}`);
  process.exit(1);
}

const dataset = await loadDataset();
const byId = new Map(dataset.map((d) => [d.id, d]));
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
if (errors.length) fail(errors);

let downloaded = 0;
const out = [];
for (const entry of catalog) {
  const src = entry.src ? byId.get(entry.src) : null;
  const images = [];
  if (src) {
    for (const [n, path] of src.images.entries()) {
      const rel = `${entry.id}/${n}.jpg`;
      if (await downloadImage(path, join(IMAGES, rel))) downloaded++;
      images.push(rel);
    }
  }

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
if (errors.length) fail(errors);

// Drop photo folders nothing references any more (an exercise removed, or rewritten without a
// source), so the APK only carries what it uses.
const photographed = new Set(out.filter((r) => r.images.length).map((r) => r.id));
if (await exists(IMAGES)) {
  for (const dir of await readdir(IMAGES)) {
    if (!photographed.has(dir)) await rm(join(IMAGES, dir), { recursive: true });
  }
}

await mkdir(CONTENT, { recursive: true });
await writeFile(join(CONTENT, 'exercises.json'), JSON.stringify(out, null, 1) + '\n');
const withPhotos = out.filter((r) => r.images.length).length;
console.log(`exercises.json: ${out.length} exercises (${withPhotos} with photos, ${downloaded} photos downloaded)`);
