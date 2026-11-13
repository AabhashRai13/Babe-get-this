import { describe, expect, it } from 'vitest';
import { assetJobs } from '../src/assets/jobs';

describe('PNG asset export list', () => {
  const jobs = assetJobs();

  it('has every expression and pose for both characters, the duo hug, and the backgrounds', () => {
    expect(jobs.filter((j) => j.kind === 'expression')).toHaveLength(38);
    expect(jobs.filter((j) => j.kind === 'pose')).toHaveLength(25);
    expect(jobs.map((j) => j.file)).toEqual(expect.arrayContaining([
      'expressions/mr-smug.png', 'expressions/mrs-love.png', 'poses/mr-holdPhone.png', 'poses/hug.png',
      'backgrounds/kitchen.png', 'backgrounds/couch.png', 'backgrounds/aisle.png', 'backgrounds/paper.png',
    ]));
  });

  it('file names are unique pngs', () => {
    const files = jobs.map((j) => j.file);
    expect(new Set(files).size).toBe(files.length);
    expect(files.every((f) => f.endsWith('.png'))).toBe(true);
  });
});
