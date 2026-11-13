// npm run assets: every expression and pose for both Pebbles as transparent PNGs, plus empty set backdrops.
// Output: <media root>/assets/{expressions,poses,backgrounds}/*.png (outside git).
import { bundle } from '@remotion/bundler';
import { renderStill, selectComposition } from '@remotion/renderer';
import fs from 'node:fs';
import path from 'node:path';
import { importTs } from './load-script.mjs';
import { KIT, mediaRoot } from './media.mjs';

const { assetJobs } = await importTs(path.join(KIT, 'src', 'assets', 'jobs.ts'));
const jobs = assetJobs();
const outRoot = path.join(mediaRoot(), 'assets');
const serveUrl = await bundle({ entryPoint: path.join(KIT, 'src', 'index.ts') });

let n = 0;
for (const job of jobs) {
  const inputProps = { job };
  const composition = await selectComposition({ serveUrl, id: 'asset', inputProps });
  const output = path.join(outRoot, job.file);
  fs.mkdirSync(path.dirname(output), { recursive: true });
  await renderStill({ composition, serveUrl, output, inputProps, imageFormat: 'png', overwrite: true });
  if (++n % 10 === 0) console.log(`${n}/${jobs.length}`);
}
console.log(`wrote ${jobs.length} PNGs to ${outRoot}`);
