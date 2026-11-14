import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { analyzeAudio } from '../scripts/analyze.mjs';

// A stand-in for the demucs venv's python: logs each call, and "separates" by writing a silent vocals.wav
// where demucs would (<out>/htdemucs/<input name>/vocals.wav).
function fakeDemucs(dir: string): { python: string; calls: () => number } {
  const log = path.join(dir, 'calls.log');
  const python = path.join(dir, 'python');
  fs.writeFileSync(python, `#!/bin/sh
echo "$@" >> "${log}"
while [ $# -gt 0 ]; do case "$1" in -o) out="$2"; shift;; *) last="$1";; esac; shift; done
name=$(basename "$last"); name="\${name%.*}"
mkdir -p "$out/htdemucs/$name"
ffmpeg -v error -y -f lavfi -i anullsrc=r=44100:cl=stereo -t 1 "$out/htdemucs/$name/vocals.wav"
`);
  fs.chmodSync(python, 0o755);
  return { python, calls: () => (fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n').length : 0) };
}

let dir: string;
let pub: string;
const SLUG = 'demo';
const saved = process.env.PEBBLES_VOCALS;

beforeEach(() => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-analyze-'));
  pub = path.join(dir, 'public');
  fs.mkdirSync(path.join(pub, 'media', SLUG), { recursive: true });
  // A loud 1 s tone in the voice band: the mix is "loud", the fake vocal stem is silent.
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'lavfi', '-i', 'sine=frequency=800:duration=1', '-c:a', 'aac',
    path.join(pub, 'media', SLUG, 'song.m4a')]);
  delete process.env.PEBBLES_VOCALS;
});
afterEach(() => {
  if (saved === undefined) delete process.env.PEBBLES_VOCALS;
  else process.env.PEBBLES_VOCALS = saved;
});

const read = () => JSON.parse(fs.readFileSync(path.join(pub, 'media', SLUG, 'envelope.json'), 'utf8'));

describe('lip-sync analysis', () => {
  it('measures the isolated vocals, not the mix', async () => {
    const d = fakeDemucs(dir);
    await analyzeAudio(SLUG, 'song.m4a', pub, { python: d.python, cacheDir: path.join(dir, 'stems') });
    const env = read();
    expect(env.source).toBe('vocals');
    expect(env.fps).toBe(60);
    expect(env.db.length).toBeGreaterThanOrEqual(59);
    expect(Math.max(...env.db.slice(5, 50))).toBeLessThan(-60); // the tone is music: not in the vocal stem
    expect(d.calls()).toBe(1);
  }, 30000);

  it('separates each audio file once and reuses the stem', async () => {
    const d = fakeDemucs(dir);
    const opts = { python: d.python, cacheDir: path.join(dir, 'stems') };
    await analyzeAudio(SLUG, 'song.m4a', pub, opts);
    await analyzeAudio(SLUG, 'song.m4a', pub, opts);
    expect(d.calls()).toBe(1);
    expect(read().source).toBe('vocals');
  }, 30000);

  it('falls back to the mix, with a warning, when demucs is not installed', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    await analyzeAudio(SLUG, 'song.m4a', pub, { python: path.join(dir, 'no-such-python'), cacheDir: path.join(dir, 'stems') });
    expect(read().source).toBe('mix');
    expect(Math.max(...read().db.slice(5, 50))).toBeGreaterThan(-30);
    expect(String(warn.mock.calls[0]?.[0])).toMatch(/demucs/);
    warn.mockRestore();
  }, 30000);

  it('PEBBLES_VOCALS=mix skips separation', async () => {
    const d = fakeDemucs(dir);
    process.env.PEBBLES_VOCALS = 'mix';
    await analyzeAudio(SLUG, 'song.m4a', pub, { python: d.python, cacheDir: path.join(dir, 'stems') });
    expect(read().source).toBe('mix');
    expect(d.calls()).toBe(0);
  }, 30000);
});
