import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { exportPlatforms } from '../scripts/export.mjs';

let media: string;
const saved = process.env.PEBBLES_MEDIA;
const SLUG = 'demo';

beforeEach(() => {
  media = fs.mkdtempSync(path.join(os.tmpdir(), 'pebbles-export-'));
  process.env.PEBBLES_MEDIA = media;
});
afterEach(() => {
  if (saved === undefined) delete process.env.PEBBLES_MEDIA;
  else process.env.PEBBLES_MEDIA = saved;
});

const probe = (file: string) => {
  const out = execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'stream=codec_type,codec_name,width,height,r_frame_rate,sample_rate',
    '-of', 'json', file], { encoding: 'utf8' });
  const streams = JSON.parse(out).streams as Record<string, string | number>[];
  return { v: streams.find((s) => s.codec_type === 'video')!, a: streams.find((s) => s.codec_type === 'audio') };
};

describe('platform exports', () => {
  it('names the missing reel', () => {
    expect(() => exportPlatforms(SLUG)).toThrow(path.join(media, SLUG, 'out', 'reel.mp4'));
  });

  it('writes Instagram at 30 fps and TikTok at 60 fps, each with a silent copy', () => {
    const out = path.join(media, SLUG, 'out');
    fs.mkdirSync(out, { recursive: true });
    execFileSync('ffmpeg', ['-v', 'error', '-y', '-f', 'lavfi', '-i', 'testsrc2=size=1080x1920:rate=60:duration=1',
      '-f', 'lavfi', '-i', 'sine=frequency=440:duration=1:sample_rate=44100', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-shortest', path.join(out, 'reel.mp4')]);
    const files = exportPlatforms(SLUG);
    expect(files.map((f: string) => path.basename(f))).toEqual(['instagram.mp4', 'instagram-no-sound.mp4', 'tiktok.mp4', 'tiktok-no-sound.mp4']);

    const ig = probe(path.join(out, 'instagram.mp4'));
    expect(ig.v).toMatchObject({ codec_name: 'h264', width: 1080, height: 1920, r_frame_rate: '30/1' });
    expect(ig.a).toMatchObject({ codec_name: 'aac', sample_rate: '48000' });
    const tt = probe(path.join(out, 'tiktok.mp4'));
    expect(tt.v).toMatchObject({ codec_name: 'h264', width: 1080, height: 1920, r_frame_rate: '60/1' });
    expect(tt.a).toMatchObject({ codec_name: 'aac', sample_rate: '48000' });
    expect(probe(path.join(out, 'instagram-no-sound.mp4')).a).toBeUndefined();
    expect(probe(path.join(out, 'tiktok-no-sound.mp4')).a).toBeUndefined();
  }, 60000);
});
