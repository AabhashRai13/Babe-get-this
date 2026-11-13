import { describe, expect, it } from 'vitest';
import { MR, MRS, toMrsLocal } from '../src/kit/geometry';

describe('geometry ported from the icon', () => {
  it('mrs eyes are level once straightened', () => {
    expect(Math.abs(MRS.eyes[0].y - MRS.eyes[1].y)).toBeLessThan(1);
  });

  it('mr outer capsule matches icon', () => {
    expect(MR.outer.w).toBeCloseTo(450.3, 0);
    expect(MR.outer.h).toBeCloseTo(647.6, 0);
  });

  it('mrs mouth corners level', () => {
    const a = toMrsLocal(638.98, 503.8);
    const b = toMrsLocal(713.3, 493.3);
    expect(Math.abs(a.y - b.y)).toBeLessThan(1);
  });
});
