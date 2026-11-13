// npm run render <slug>
// Stages the audio from ~/Documents/Pebbles Videos/<slug>/, builds the lip-sync envelope, renders the Reel,
// then writes a no-sound copy. Output: <media>/<slug>/out/reel.mp4 and no-sound.mp4.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { analyzeAudio } from './analyze.mjs';
import { KIT, mediaDir, stageAudio } from './media.mjs';

const slug = process.argv[2];
if (!slug) {
  console.error('usage: npm run render <slug>');
  process.exit(1);
}
const scriptFile = path.join(KIT, 'src', 'videos', slug, 'script.ts');
if (!fs.existsSync(scriptFile)) {
  console.error(`No script at ${scriptFile}`);
  process.exit(1);
}
const audio = fs.readFileSync(scriptFile, 'utf8').match(/\baudio:\s*['"]([^'"]+)['"]/)?.[1];

try {
  if (audio) {
    stageAudio(slug, audio);
    await analyzeAudio(slug, audio);
  }
} catch (e) {
  console.error(String(e.message ?? e));
  process.exit(1);
}

const outDir = path.join(mediaDir(slug), 'out');
fs.mkdirSync(outDir, { recursive: true });
const reel = path.join(outDir, 'reel.mp4');
execFileSync('npx', [
  'remotion', 'render', 'src/index.ts', `v-${slug}`, reel,
  '--codec=h264', '--crf=16', '--pixel-format=yuv420p', '--color-space=bt709',
  '--audio-codec=aac', '--audio-bitrate=192k', '--log=error',
], { cwd: KIT, stdio: 'inherit' });
execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', reel, '-an', '-c:v', 'copy', '-movflags', '+faststart', path.join(outDir, 'no-sound.mp4')]);
console.log(`wrote ${reel}\nwrote ${path.join(outDir, 'no-sound.mp4')}`);
