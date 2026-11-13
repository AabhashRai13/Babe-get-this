// Renders the review sheets (stills) to <media root>/_sheets/<id>.png. Needs no audio.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const KIT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const mediaRoot = process.env.PEBBLES_MEDIA ?? path.join(os.homedir(), 'Documents', 'Pebbles Videos');
export const SHEETS = ['sheet-expressions'];

const outDir = path.join(mediaRoot, '_sheets');
fs.mkdirSync(outDir, { recursive: true });
for (const id of SHEETS) {
  const out = path.join(outDir, `${id.replace('sheet-', '')}.png`);
  execFileSync('npx', ['remotion', 'still', 'src/index.ts', id, out, '--log=error'], { cwd: KIT, stdio: 'inherit' });
  console.log('wrote', out);
}
