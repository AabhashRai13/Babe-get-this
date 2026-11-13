// npm run render <slug>
// Stages the audio from ~/Documents/Pebbles Videos/<slug>/, builds the lip-sync envelope, renders the Reel,
// then writes a no-sound copy. Output: <media>/<slug>/out/reel.mp4 and no-sound.mp4.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { prepareAudio } from './load-script.mjs';
import { KIT, mediaDir } from './media.mjs';

const slug = process.argv[2];
if (!slug) {
  console.error('usage: npm run render <slug>');
  process.exit(1);
}
try {
  await prepareAudio(slug); // validates the script, replaces stale staged audio, writes the envelope
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
  '--audio-codec=aac', '--audio-bitrate=192k', '--sample-rate=44100', '--log=error',
], { cwd: KIT, stdio: 'inherit' });
execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', reel, '-an', '-c:v', 'copy', '-movflags', '+faststart', path.join(outDir, 'no-sound.mp4')]);
console.log(`wrote ${reel}\nwrote ${path.join(outDir, 'no-sound.mp4')}`);
