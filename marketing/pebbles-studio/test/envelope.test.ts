import { describe, expect, it } from 'vitest';
import { envelopeFromDb, mouthOpen } from '../src/kit/envelope';

describe('lip-sync envelope', () => {
  it('maps the prototype dB range to 0..1', () => {
    expect(envelopeFromDb([-38, -16, -60, -Infinity, NaN])).toEqual([0, 1, 0, 0, 0]);
  });

  it('mouth is closed past the end of the envelope', () => {
    expect(mouthOpen([0.5], 5, true)).toBe(0);
  });

  it('mouth is closed when not speaking', () => {
    expect(mouthOpen([1], 0, false)).toBe(0);
  });

  it('mouth holds a little of the previous frame', () => {
    expect(mouthOpen([1, 0], 1, true)).toBeCloseTo(0.55, 6);
  });
});
