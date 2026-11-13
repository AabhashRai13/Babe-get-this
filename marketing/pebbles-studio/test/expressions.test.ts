import { describe, expect, it } from 'vitest';
import { EXPRESSIONS, blendFace, faceFor } from '../src/kit/expressions';
import type { ExpressionName } from '../src/kit/types';

const NAMES: ExpressionName[] = [
  'neutral', 'deadpan', 'smug', 'content', 'happy', 'laugh', 'excited', 'love', 'smirk', 'sideEye',
  'eyeRoll', 'worried', 'shock', 'sad', 'crying', 'annoyed', 'confused', 'sleepy', 'sing',
];

describe('expression library', () => {
  it('every expression name resolves', () => {
    expect(Object.keys(EXPRESSIONS).sort()).toEqual([...NAMES].sort());
    for (const n of NAMES) {
      const f = faceFor(n);
      expect(f.lid, n).toBeGreaterThanOrEqual(0);
      expect(f.lid, n).toBeLessThanOrEqual(1);
      expect(f.open, n).toBeGreaterThanOrEqual(0);
      expect(f.open, n).toBeLessThanOrEqual(1);
      expect(f.eyeScale, n).toBeGreaterThanOrEqual(0.8);
      expect(f.eyeScale, n).toBeLessThanOrEqual(1.3);
    }
  });

  it('prototype moods keep their values', () => {
    expect(EXPRESSIONS.smug).toMatchObject({ lid: 0.46, smile: 0.75 });
    expect(EXPRESSIONS.deadpan).toMatchObject({ lid: 0.52, smile: 0.12 });
    expect(EXPRESSIONS.shock).toMatchObject({ lid: 0, eyeScale: 1.1, pupilScale: 0.7, open: 0.3 });
    expect(EXPRESSIONS.love).toMatchObject({ lid: 1, happyEyes: true });
  });

  it('partial override merges over the base expression', () => {
    const f = faceFor({ smile: -1 }, 'smug');
    expect(f.lid).toBe(0.46);
    expect(f.smile).toBe(-1);
  });

  it('blend midpoint is halfway', () => {
    const a = faceFor('neutral');
    const b = faceFor('shock');
    expect(blendFace(a, b, 0.5).eyeScale).toBeCloseTo((a.eyeScale + b.eyeScale) / 2, 6);
    expect(blendFace(a, b, 0.4).mouthShape).toBe(a.mouthShape);
    expect(blendFace(a, b, 0.6).mouthShape).toBe(b.mouthShape);
  });
});
