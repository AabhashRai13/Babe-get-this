import { describe, expect, it } from 'vitest';
import { blink, settle } from '../src/kit/motion';

describe('motion', () => {
  it('blink closes briefly, 2 to 4 times in 10 seconds', () => {
    let runs = 0;
    let inRun = false;
    for (let f = 0; f <= 300; f++) {
      const closed = blink(f / 30, 0.3) > 0.9;
      if (closed && !inRun) runs++;
      inRun = closed;
    }
    expect(runs).toBeGreaterThanOrEqual(2);
    expect(runs).toBeLessThanOrEqual(4);
  });

  it('blink is open most of the time', () => {
    let open = 0;
    for (let f = 0; f < 300; f++) if (blink(f / 30, 1.1) === 0) open++;
    expect(open / 300).toBeGreaterThan(0.9);
  });

  it('settle dies out after a cut', () => {
    expect(Math.abs(settle(1))).toBeLessThan(0.001);
  });
});
