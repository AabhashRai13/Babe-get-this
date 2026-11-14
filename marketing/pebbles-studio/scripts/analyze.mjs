// Lip-sync pre-pass: voice-band (300–3400 Hz) loudness per video frame -> public/media/<slug>/envelope.json.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { KIT } from './media.mjs';

const FPS = 60;
const SR = 44100;

function bandDb(src, filter) {
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-env-')), 'astats.txt');
  execFileSync('ffmpeg', [
    '-v', 'error', '-y', '-i', src, '-vn', '-ac', '1', '-ar', String(SR),
    '-af', `${filter},asetnsamples=${SR / FPS},astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level:file=${tmp}`,
    '-f', 'null', '-',
  ]);
  return fs.readFileSync(tmp, 'utf8').split('\n')
    .filter((l) => l.includes('RMS_level'))
    .map((l) => {
      const v = parseFloat(l.split('=')[1]);
      return Number.isFinite(v) ? +v.toFixed(2) : -99;
    });
}

/**
 * Lip-sync pre-pass at 60 fps: voice-band loudness (how open) plus a dark band and a bright band
 * (mouth width: "oo" vs "ee") -> public/media/<slug>/envelope.json.
 */
export async function analyzeAudio(slug, file, publicDir = path.join(KIT, 'public')) {
  const src = path.join(publicDir, 'media', slug, file);
  const db = bandDb(src, 'highpass=f=300,lowpass=f=3400');
  const lo = bandDb(src, 'highpass=f=250,lowpass=f=1000');
  const hi = bandDb(src, 'highpass=f=2000,lowpass=f=5000');
  const out = path.join(publicDir, 'media', slug, 'envelope.json');
  fs.writeFileSync(out, JSON.stringify({ fps: FPS, db, lo, hi }));
  return out;
}

// npm run analyze <slug>: stage the script's audio and write the envelope (for timing shots before a render).
if (process.argv[1] === new URL(import.meta.url).pathname) {
  const { prepareAudio } = await import('./load-script.mjs');
  const slug = process.argv[2];
  try {
    const s = await prepareAudio(slug);
    console.log(s.audio ? `wrote public/media/${slug}/envelope.json (db per frame at ${FPS}fps)` : `${slug} has no audio`);
  } catch (e) {
    console.error(String(e.message ?? e));
    process.exit(1);
  }
}
