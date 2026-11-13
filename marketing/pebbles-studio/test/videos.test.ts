import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { validateScript } from '../src/kit/timeline';
import { VIDEOS } from '../src/videos';

describe('registered videos', () => {
  it('includes the Spanish-song remake', () => {
    expect(VIDEOS.map((v) => v.slug)).toContain('2026-11-12-spanish-song');
  });

  for (const v of VIDEOS) {
    it(`${v.slug} validates and lives in its own folder`, () => {
      expect(validateScript(v)).toEqual([]);
      expect(fs.existsSync(path.join(__dirname, '..', 'src', 'videos', v.slug, 'script.ts'))).toBe(true);
    });
  }
});
