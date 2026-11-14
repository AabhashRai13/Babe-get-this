// npm run export <slug>
// Platform copies of <media>/<slug>/out/reel.mp4: instagram.mp4 and tiktok.mp4, each with a silent copy for posting
// with the app's own sound. Instagram plays Reels at 30 fps, so it gets 30 fps rather than letting the app drop
// frames; TikTok keeps 60 fps. Both get 48 kHz AAC levelled to -14 LUFS, the loudness both apps target.
import { execFileSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { mediaDir } from './media.mjs';

export const PLATFORMS = {
  instagram: { fps: 30, maxrate: '10M' },
  tiktok: { fps: 60, maxrate: '14M' },
};

/**
 * Gain to -14 LUFS from a measurement of the reel, then a limiter holding peaks under -1 dB. (loudnorm on its own
 * falls back to dynamic levelling when the peaks don't allow a plain gain, and overshoots by a dB or more.)
 */
function loudnessFilter(reel) {
  const r = spawnSync('ffmpeg', ['-hide_banner', '-i', reel, '-af', 'loudnorm=I=-14:TP=-1:LRA=11:print_format=json', '-f', 'null', '-'],
    { encoding: 'utf8' });
  const json = r.stderr.match(/\{[^{}]*"input_i"[^{}]*\}/);
  const inputI = json ? Number(JSON.parse(json[0]).input_i) : NaN;
  const gain = Number.isFinite(inputI) ? -14 - inputI : 0;
  return `volume=${gain.toFixed(2)}dB,alimiter=limit=0.89:level=false`;
}

/** Writes the platform files next to reel.mp4 and returns their paths. Throws with the reel's path if it is missing. */
export function exportPlatforms(slug) {
  const out = path.join(mediaDir(slug), 'out');
  const reel = path.join(out, 'reel.mp4');
  if (!fs.existsSync(reel)) throw new Error(`No reel to export at ${reel}; run: npm run render ${slug}`);
  const audio = loudnessFilter(reel);
  const files = [];
  for (const [name, p] of Object.entries(PLATFORMS)) {
    const file = path.join(out, `${name}.mp4`);
    execFileSync('ffmpeg', [
      '-v', 'error', '-y', '-i', reel,
      '-vf', `fps=${p.fps}`, '-c:v', 'libx264', '-profile:v', 'high', '-preset', 'slow', '-crf', '17',
      '-maxrate', p.maxrate, '-bufsize', '20M', '-pix_fmt', 'yuv420p',
      '-color_primaries', 'bt709', '-color_trc', 'bt709', '-colorspace', 'bt709',
      '-af', audio, '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
      '-movflags', '+faststart', file,
    ]);
    const silent = path.join(out, `${name}-no-sound.mp4`);
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', file, '-an', '-c:v', 'copy', '-movflags', '+faststart', silent]);
    files.push(file, silent);
  }
  return files;
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  const slug = process.argv[2];
  if (!slug) {
    console.error('usage: npm run export <slug>');
    process.exit(1);
  }
  try {
    for (const f of exportPlatforms(slug)) console.log(`wrote ${f}`);
  } catch (e) {
    console.error(String(e.message ?? e));
    process.exit(1);
  }
}
