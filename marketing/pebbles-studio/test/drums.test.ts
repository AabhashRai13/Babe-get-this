import { describe, expect, it } from 'vitest';
import { drumHits, pulse } from '../src/kit/drums';

const near = (xs: number[], ys: number[]) => {
  expect(xs.length).toBe(ys.length);
  xs.forEach((x, i) => expect(Math.abs(x - ys[i])).toBeLessThan(0.01));
};

describe('drum grid', () => {
  const hits = drumHits({ start: 28.58, beat: 0.4, end: 35.27 });

  it('matches the prototype kicks and snares', () => {
    near(hits.filter((h) => h.d === 'kick').map((h) => h.t), [28.58, 30.02, 30.18, 31.62, 31.78, 33.22, 33.38, 34.82, 34.98]);
    near(hits.filter((h) => h.d === 'snare').map((h) => h.t), [29.38, 30.98, 32.58, 34.18]);
  });

  it('starts with a crash and keeps hats on eighths', () => {
    expect(hits.find((h) => h.d === 'crash')?.t).toBeCloseTo(28.58, 6);
    const hats = hits.filter((h) => h.d === 'hat' || h.d === 'ohat');
    expect(hats[1].t - hats[0].t).toBeCloseTo(0.2, 6);
    expect(hats[7].d).toBe('ohat');
  });

  it('hits are sorted by time', () => {
    for (let i = 1; i < hits.length; i++) expect(hits[i].t).toBeGreaterThanOrEqual(hits[i - 1].t);
  });

  it('pulse decays after the last hit of that kind', () => {
    expect(pulse(hits, 28.58, ['kick'])).toBeCloseTo(1, 6);
    expect(pulse(hits, 28.58 + 0.1, ['kick'])).toBeCloseTo(Math.exp(-1.6), 6);
    expect(pulse(hits, 10, ['kick'])).toBe(0);
  });
});
