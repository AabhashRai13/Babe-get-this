// Load a video's script.ts from Node (bundled with esbuild, so it is validated exactly as Remotion sees it),
// then stage its audio and build the lip-sync envelope.
import { build } from 'esbuild';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { analyzeAudio } from './analyze.mjs';
import { KIT, stageAudio } from './media.mjs';

/** The script object for src/videos/<slug>/script.ts. Throws the script's own validation error if it is invalid. */
export async function loadScript(slug) {
  const entry = path.join(KIT, 'src', 'videos', slug, 'script.ts');
  if (!fs.existsSync(entry)) throw new Error(`No script at ${entry}`);
  const out = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-script-')), 'script.mjs');
  await build({ entryPoints: [entry], bundle: true, format: 'esm', platform: 'node', outfile: out, logLevel: 'silent' });
  const mod = await import(pathToFileURL(out).href);
  const script = mod.default;
  if (script?.slug !== slug) throw new Error(`${entry} has slug "${script?.slug}"; it must match its folder "${slug}"`);
  return script;
}

/** Stage the script's audio into public/media/<slug>/ (replacing anything stale) and write envelope.json. */
export async function prepareAudio(slug, publicDir = path.join(KIT, 'public')) {
  const script = await loadScript(slug);
  fs.rmSync(path.join(publicDir, 'media', slug), { recursive: true, force: true });
  if (!script.audio) return script;
  stageAudio(slug, script.audio, publicDir);
  await analyzeAudio(slug, script.audio, publicDir);
  return script;
}
