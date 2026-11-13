import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { mediaDir, mediaRoot, stageAudio } from '../scripts/media.mjs';

let tmp: string;
let pub: string;
const saved = process.env.PEBBLES_MEDIA;

beforeEach(() => {
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-media-'));
  pub = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-public-'));
  process.env.PEBBLES_MEDIA = tmp;
});
afterEach(() => {
  if (saved === undefined) delete process.env.PEBBLES_MEDIA;
  else process.env.PEBBLES_MEDIA = saved;
});

describe('media staging', () => {
  it('media root follows PEBBLES_MEDIA', () => {
    expect(mediaRoot()).toBe(tmp);
    expect(mediaDir('demo')).toBe(path.join(tmp, 'demo'));
  });

  it('stageAudio throws with the expected path when the file is missing', () => {
    expect(() => stageAudio('demo', 'song.m4a', pub)).toThrow(path.join(tmp, 'demo', 'song.m4a'));
  });

  it('stageAudio copies into public/media/<slug>/', () => {
    fs.mkdirSync(path.join(tmp, 'demo'));
    fs.writeFileSync(path.join(tmp, 'demo', 'song.m4a'), Buffer.alloc(10));
    const staged = stageAudio('demo', 'song.m4a', pub);
    expect(staged).toBe(path.join(pub, 'media', 'demo', 'song.m4a'));
    expect(fs.statSync(staged).size).toBe(10);
  });
});
