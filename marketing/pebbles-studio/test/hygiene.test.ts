import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

// The repo is public: kit code and its docs must not leak personal paths or file names, or contain media.
const REPO = path.resolve(__dirname, '..', '..', '..');
const tracked = (...paths: string[]) =>
  execFileSync('git', ['ls-files', ...paths], { cwd: REPO, encoding: 'utf8' }).split('\n').filter(Boolean);
const AREAS = ['marketing/pebbles-studio', 'docs/superpowers/specs/2026-11-13-pebbles-prototype', 'docs/superpowers/specs/2026-11-13-pebbles-animation-kit-design.md', 'docs/superpowers/plans/2026-11-13-pebbles-animation-kit.md'];

describe('public-repo hygiene', () => {
  it('no home-directory paths in kit files or their docs', () => {
    let hits = '';
    try {
      hits = execFileSync('git', ['grep', '-nE', '/Users/[A-Za-z]', '--', ...AREAS, ':!*package-lock.json'], { cwd: REPO, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    } catch (e) {
      if ((e as { status?: number }).status !== 1) throw e; // 1 = no matches
    }
    expect(hits).toBe('');
  });

  it('no audio or video files are tracked', () => {
    expect(tracked(...AREAS).filter((f) => /\.(mp4|m4a|mp3|wav|aiff|mov)$/i.test(f))).toEqual([]);
  });
});
