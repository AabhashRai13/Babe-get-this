import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { loadScript, prepareAudio } from '../scripts/load-script.mjs';

const SLUG = '2026-11-12-spanish-song';
let media: string;
let pub: string;
const saved = process.env.PEBBLES_MEDIA;
const savedVocals = process.env.PEBBLES_VOCALS;

beforeEach(() => {
  media = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-media-'));
  pub = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-public-'));
  process.env.PEBBLES_MEDIA = media;
  process.env.PEBBLES_VOCALS = 'mix'; // vocal separation is covered in analyze.test.ts
});
afterEach(() => {
  if (saved === undefined) delete process.env.PEBBLES_MEDIA;
  else process.env.PEBBLES_MEDIA = saved;
  if (savedVocals === undefined) delete process.env.PEBBLES_VOCALS;
  else process.env.PEBBLES_VOCALS = savedVocals;
});

describe('loading a video script from Node', () => {
  it('reads the real script (not a regex) and validates it', async () => {
    const s = await loadScript(SLUG);
    expect(s.slug).toBe(SLUG);
    expect(s.audio).toBe('reference.mp4');
    expect(s.shots.length).toBeGreaterThan(10);
  });

  it('names the expected audio path when it is missing', async () => {
    await expect(prepareAudio(SLUG, pub)).rejects.toThrow(path.join(media, SLUG, 'reference.mp4'));
  });

  it('replaces stale staged audio and writes the envelope', async () => {
    fs.mkdirSync(path.join(media, SLUG));
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1', '-c:a', 'aac', path.join(media, SLUG, 'reference.mp4')]);
    const stale = path.join(pub, 'media', SLUG, 'old-song.m4a');
    fs.mkdirSync(path.dirname(stale), { recursive: true });
    fs.writeFileSync(stale, 'old');
    await prepareAudio(SLUG, pub);
    expect(fs.existsSync(stale)).toBe(false);
    const env = JSON.parse(fs.readFileSync(path.join(pub, 'media', SLUG, 'envelope.json'), 'utf8'));
    expect(env.db.length).toBeGreaterThanOrEqual(29);
  }, 30000);
});
