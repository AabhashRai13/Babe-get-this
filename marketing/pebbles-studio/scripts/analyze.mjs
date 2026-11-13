// Lip-sync pre-pass: voice-band (300–3400 Hz) loudness per video frame -> public/media/<slug>/envelope.json.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { KIT } from './media.mjs';

const FPS = 30;
const SR = 44100;

export async function analyzeAudio(slug, file, publicDir = path.join(KIT, 'public')) {
  const src = path.join(publicDir, 'media', slug, file);
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-env-')), 'astats.txt');
  execFileSync('ffmpeg', [
    '-v', 'error', '-y', '-i', src, '-vn', '-ac', '1', '-ar', String(SR),
    '-af', `highpass=f=300,lowpass=f=3400,asetnsamples=${SR / FPS},astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level:file=${tmp}`,
    '-f', 'null', '-',
  ]);
  const db = fs.readFileSync(tmp, 'utf8').split('\n')
    .filter((l) => l.includes('RMS_level'))
    .map((l) => {
      const v = parseFloat(l.split('=')[1]);
      return Number.isFinite(v) ? +v.toFixed(2) : -99;
    });
  const out = path.join(publicDir, 'media', slug, 'envelope.json');
  fs.writeFileSync(out, JSON.stringify({ fps: FPS, db }));
  return out;
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const [slug, file] = process.argv.slice(2);
  console.log('wrote', await analyzeAudio(slug, file));
}
