// Lip-sync and music pre-pass: split the clip into stems (demucs), then measure them per 60 fps frame -> envelope.json.
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

const STEMS_USED = ['vocals', 'drums', 'other'];

/**
 * The clip split into stems with demucs (htdemucs: vocals, drums, bass, other), cached by the audio's content in
 * ~/.cache/pebbles-studio/stems. Vocals drive the mouths (the mix's drums and music would otherwise move them),
 * drums drive Mr's sticks, and "other" (guitar, lute, keys) drives strumming.
 * Returns null (use the mix) when PEBBLES_VOCALS=mix or demucs isn't installed.
 */
export function stemsFor(src, { python = DEMUCS_PYTHON, cacheDir = STEMS } = {}) {
  if (process.env.PEBBLES_VOCALS === 'mix') return null;
  if (!fs.existsSync(python)) {
    console.warn(`demucs not found at ${python}: lip sync will follow the whole mix, music included.\n`
      + 'Set it up once (README, "Vocal isolation"), or set PEBBLES_DEMUCS_PYTHON.');
    return null;
  }
  const hash = createHash('sha1').update(fs.readFileSync(src)).digest('hex').slice(0, 16);
  const out = Object.fromEntries(STEMS_USED.map((st) => [st, path.join(cacheDir, `${hash}.${st}.wav`)]));
  if (STEMS_USED.every((st) => fs.existsSync(out[st]))) return out;
  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-demucs-'));
  const wav = path.join(work, 'mix.wav');
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', src, '-vn', '-ac', '2', '-ar', String(SR), wav]);
  execFileSync(python, ['-m', 'demucs', '-n', 'htdemucs', '-o', work, wav], { stdio: ['ignore', 'ignore', 'pipe'] });
  fs.mkdirSync(cacheDir, { recursive: true });
  for (const st of STEMS_USED) fs.copyFileSync(path.join(work, 'htdemucs', 'mix', `${st}.wav`), out[st]);
  fs.rmSync(work, { recursive: true, force: true });
  return out;
}

/**
 * Lip-sync and music pre-pass at 60 fps -> public/media/<slug>/envelope.json.
 * From the vocals: voice-band loudness (how open) plus a dark and a bright band (mouth width and roundness).
 * From the drums stem: kick, snare and hat bands; from the "other" stem: a pluck band (strums).
 * `source` records whether it measured the stems or (without demucs) the whole mix, which has no music bands.
 */
export async function analyzeAudio(slug, file, publicDir = path.join(KIT, 'public'), opts = {}) {
  const mix = path.join(publicDir, 'media', slug, file);
  const stems = stemsFor(mix, opts);
  const voice = stems?.vocals ?? mix;
  const env = {
    fps: FPS,
    source: stems ? 'vocals' : 'mix',
    db: bandDb(voice, 'highpass=f=300,lowpass=f=3400'),
    lo: bandDb(voice, 'highpass=f=250,lowpass=f=1000'),
    hi: bandDb(voice, 'highpass=f=2000,lowpass=f=5000'),
  };
  if (stems) {
    const fit = (a) => env.db.map((_, i) => a[i] ?? -99);
    env.kick = fit(bandDb(stems.drums, 'highpass=f=40,lowpass=f=120'));
    env.snare = fit(bandDb(stems.drums, 'highpass=f=1500,lowpass=f=4000'));
    env.hat = fit(bandDb(stems.drums, 'highpass=f=7000,lowpass=f=12000'));
    env.pluck = fit(bandDb(stems.other, 'highpass=f=200,lowpass=f=4000'));
  }
  const out = path.join(publicDir, 'media', slug, 'envelope.json');
  fs.writeFileSync(out, JSON.stringify(env));
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
