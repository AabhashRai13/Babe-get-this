// node render.mjs              -> out/pebbles-podcast.mp4
// node render.mjs --stills 1.2 5 33   -> out/still_<t>.jpg for quick review
import { chromium } from 'playwright-core';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const DIR = path.dirname(new URL(import.meta.url).pathname);
const OUT = path.join(DIR, 'out'); fs.mkdirSync(OUT, { recursive: true });
const HS = path.join(os.homedir(), 'Library/Caches/ms-playwright/chromium_headless_shell-1248/chrome-headless-shell-mac-arm64/chrome-headless-shell');
const tl = JSON.parse(fs.readFileSync(path.join(DIR, 'timeline.json'), 'utf8'));
const icon = 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(DIR, 'icon.svg')).toString('base64');

const browser = await chromium.launch({ executablePath: HS });
const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
page.on('pageerror', (e) => { console.error('PAGE ERROR', e.message); process.exitCode = 1; });
await page.setContent('<html><body style="margin:0"></body></html>');
await page.evaluate(([t, i]) => { window.TL = t; window.ICON_SRC = i; }, [tl, icon]);
await page.addScriptTag({ path: path.join(DIR, 'scene.js') });
await page.evaluate(() => window.ready);

const frame = async (i) => Buffer.from((await page.evaluate((n) => window.renderFrame(n), i)).split(',')[1], 'base64');

const si = process.argv.indexOf('--stills');
if (si > 0) {
  for (const ts of process.argv.slice(si + 1)) {
    fs.writeFileSync(path.join(OUT, `still_${ts}.jpg`), await frame(Math.round(parseFloat(ts) * tl.fps)));
  }
  console.log('stills written');
} else {
  const n = Math.ceil(tl.duration * tl.fps);
  const out = path.join(OUT, 'pebbles-podcast.mp4');
  const ff = spawn('ffmpeg', ['-v', 'error', '-y', '-f', 'image2pipe', '-framerate', String(tl.fps), '-c:v', 'mjpeg', '-i', '-',
    '-i', path.join(DIR, 'audio/mix.wav'), '-vf', 'scale=in_color_matrix=bt601:out_color_matrix=bt709:in_range=full:out_range=tv,format=yuv420p', '-c:v', 'libx264', '-profile:v', 'high', '-level', '4.1', '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv', '-crf', '16', '-preset', 'slow', '-g', '60',
    '-c:a', 'aac', '-b:a', '192k', '-ar', '44100', '-shortest', '-movflags', '+faststart', out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const t0 = Date.now();
  for (let i = 0; i < n; i++) {
    const buf = await frame(i);
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r));
    if (i % 150 === 0) console.log(`frame ${i}/${n} ${((Date.now() - t0) / 1000).toFixed(0)}s`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on('close', r));
  console.log('wrote', out);
}
await browser.close();
