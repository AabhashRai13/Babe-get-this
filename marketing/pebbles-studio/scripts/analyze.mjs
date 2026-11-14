// Lip-sync pre-pass: isolate the vocals (demucs), then measure them per 60 fps frame -> public/media/<slug>/envelope.json.
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { KIT } from './media.mjs';

const FPS = 60;
const SR = 44100;
const CACHE = path.join(os.homedir(), '.cache', 'pebbles-studio');
/** The demucs venv's python (see README: "Vocal isolation"). Override with $PEBBLES_DEMUCS_PYTHON. */
export const DEMUCS_PYTHON = process.env.PEBBLES_DEMUCS_PYTHON ?? path.join(CACHE, 'demucs-venv', 'bin', 'python');
const STEMS = path.join(CACHE, 'stems');

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
      return Number.isFinite(v) ? +Math.max(-99, v).toFixed(2) : -99;
    });
}

/**
 * The vocals alone, separated from the music with demucs (htdemucs, two stems), cached by the audio's content
 * in ~/.cache/pebbles-studio/stems. The mix's drums and music would otherwise move the mouths.
 * Returns null (use the mix) when PEBBLES_VOCALS=mix or demucs isn't installed.
 */
export function vocalsStem(src, { python = DEMUCS_PYTHON, cacheDir = STEMS } = {}) {
  if (process.env.PEBBLES_VOCALS === 'mix') return null;
  if (!fs.existsSync(python)) {
    console.warn(`demucs not found at ${python}: lip sync will follow the whole mix, music included.\n`
      + 'Set it up once (README, "Vocal isolation"), or set PEBBLES_DEMUCS_PYTHON.');
    return null;
  }
  const hash = createHash('sha1').update(fs.readFileSync(src)).digest('hex').slice(0, 16);
  const out = path.join(cacheDir, `${hash}.vocals.wav`);
  if (fs.existsSync(out)) return out;
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-demucs-'));
  const wav = path.join(work, 'mix.wav');
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', src, '-vn', '-ac', '2', '-ar', String(SR), wav]);
  execFileSync(python, ['-m', 'demucs', '--two-stems', 'vocals', '-n', 'htdemucs', '-o', work, wav],
    { stdio: ['ignore', 'ignore', 'pipe'] });
  fs.mkdirSync(cacheDir, { recursive: true });
  fs.copyFileSync(path.join(work, 'htdemucs', 'mix', 'vocals.wav'), out);
  fs.rmSync(work, { recursive: true, force: true });
  return out;
}

/**
 * Lip-sync pre-pass at 60 fps, measured on the isolated vocals: voice-band loudness (how open) plus a dark band
 * and a bright band (mouth width: "oo" vs "ee") -> public/media/<slug>/envelope.json.
 * `source` records whether it measured the vocals or (without demucs) the whole mix.
 */
export async function analyzeAudio(slug, file, publicDir = path.join(KIT, 'public'), opts = {}) {
  const mix = path.join(publicDir, 'media', slug, file);
  const vocals = vocalsStem(mix, opts);
  const src = vocals ?? mix;
  const db = bandDb(src, 'highpass=f=300,lowpass=f=3400');
  const lo = bandDb(src, 'highpass=f=250,lowpass=f=1000');
  const hi = bandDb(src, 'highpass=f=2000,lowpass=f=5000');
  const out = path.join(publicDir, 'media', slug, 'envelope.json');
  fs.writeFileSync(out, JSON.stringify({ fps: FPS, source: vocals ? 'vocals' : 'mix', db, lo, hi }));
  return out;
}

// npm run analyze <slug>: stage the script's audio and write the envelope (for timing shots before a render).
// No top-level await: load-script.mjs imports this module, and awaiting it here would deadlock the cycle.
if (process.argv[1] === new URL(import.meta.url).pathname) {
  const slug = process.argv[2];
  import('./load-script.mjs')
    .then(({ prepareAudio }) => prepareAudio(slug))
    .then((s) => console.log(s.audio ? `wrote public/media/${slug}/envelope.json (vocal db per frame at ${FPS}fps)` : `${slug} has no audio`))
    .catch((e) => {
      console.error(String(e.message ?? e));
      process.exit(1);
    });
}
