// Renders the review sheets (stills) to <media root>/_sheets/<id>.png. Needs no audio.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

import { KIT, mediaRoot } from './media.mjs';

export const SHEETS = ['sheet-expressions', 'sheet-poses', 'sheet-sets'];

const outDir = path.join(mediaRoot(), '_sheets');
fs.mkdirSync(outDir, { recursive: true });
for (const id of SHEETS) {
  const out = path.join(outDir, `${id.replace('sheet-', '')}.png`);
  execFileSync('npx', ['remotion', 'still', 'src/index.ts', id, out, '--log=error'], { cwd: KIT, stdio: 'inherit' });
  console.log('wrote', out);
}
