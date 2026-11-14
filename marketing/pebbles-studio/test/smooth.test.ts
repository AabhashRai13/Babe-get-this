import { describe, expect, it } from 'vitest';
import { accentPulse, findAccents, smoothEnvelope, mouthWidths } from '../src/kit/envelope';
import { blink, saccade, springProgress, cutBlink } from '../src/kit/motion';
import { blendBody, bodyFor } from '../src/kit/poses';
import { FPS, resolveAt, video } from '../src/kit/timeline';
import type { VideoScript } from '../src/kit/types';

describe('springs', () => {
  it('starts at 0, overshoots a little, settles at 1', () => {
    expect(springProgress(0)).toBe(0);
    let peak = 0;
    for (let t = 0; t < 0.6; t += 0.01) peak = Math.max(peak, springProgress(t));
    expect(peak).toBeGreaterThan(1.03);
    expect(peak).toBeLessThan(1.2);
    expect(springProgress(0.6)).toBeCloseTo(1, 2);
  });
});

describe('pose and look transitions', () => {
  const s: VideoScript = video({ slug: 'demo', end: 6, shots: [
    { at: 0, set: 'kitchen', frame: 'single-mr', mr: { pose: 'stand', look: 'left' },
      beats: [{ at: 2, mr: { pose: 'shrug', look: 'right' } }] },
    { at: 4, set: 'kitchen', frame: 'single-mr', mr: { pose: 'wave' } },
  ] });

  it('a mid-shot pose change blends from the old pose', () => {
    const r = resolveAt(s, 2.05).cast.mr!;
    expect(r.prevPose).toBe('stand');
    expect(r.poseBlend).toBeGreaterThan(0);
    expect(r.poseBlend).toBeLessThan(1);
    expect(resolveAt(s, 2.8).cast.mr!.poseBlend).toBe(1);
  });

  it('a pose change on a cut snaps', () => {
    expect(resolveAt(s, 4.01).cast.mr!.poseBlend).toBe(1);
  });

  it('a mid-shot look change moves the pupils over a few frames', () => {
    const mid = resolveAt(s, 2.05).cast.mr!.face.look.x;
    expect(mid).toBeGreaterThan(-12);
    expect(mid).toBeLessThan(12);
    expect(resolveAt(s, 2.8).cast.mr!.face.look.x).toBeCloseTo(12, 1);
  });

  it('blendBody interpolates arms and tilt', () => {
    const a = bodyFor('mr', 'stand', 0, 0);
    const b = bodyFor('mr', 'shrug', 0, 0);
    const m = blendBody(a, b, 0.5);
    expect(m.arms[1].rot).toBeCloseTo((a.arms[1].rot + b.arms[1].rot) / 2, 6);
  });
});

describe('captions remember when they appeared', () => {
  it('captionSince marks the beat that set the caption', () => {
    const s = video({ slug: 'demo', end: 4, shots: [
      { at: 0, set: 'podcast', frame: 'single-mr', caption: 'Chinese', captionStyle: 'pop', beats: [{ at: 1, caption: 'Easy,' }] },
    ] });
    expect(resolveAt(s, 0.5).captionSince).toBe(0);
    expect(resolveAt(s, 1.5).captionSince).toBe(1);
  });
});

describe('lip sync', () => {
  it('opens fast and closes slower than the raw envelope', () => {
    const raw = [0, 1, 1, 0, 0, 0, 0, 0];
    const sm = smoothEnvelope(raw, 60);
    expect(sm[1]).toBeGreaterThan(0.4);
    expect(sm[3]).toBeGreaterThan(0.3); // still closing
    expect(sm[7]).toBeLessThan(sm[3]);
  });

  it('bright sounds widen the mouth, dark sounds narrow it', () => {
    const w = mouthWidths([-20, -20], [-40, -10], 60);
    expect(w[1]).toBeGreaterThan(w[0]);
  });

  it('finds stressed syllables as accents', () => {
    const open = Array.from({ length: 120 }, (_, i) => (i % 30 < 8 ? Math.sin((i % 30) / 8 * Math.PI) : 0));
    const acc = findAccents(open, 60);
    expect(acc.length).toBe(4);
    expect(accentPulse(acc, acc[0] + 2, 60)).toBeGreaterThan(0.5);
    expect(accentPulse(acc, acc[0] + 25, 60)).toBeLessThan(0.2);
  });
});

describe('eyes', () => {
  it('blinks at irregular intervals', () => {
    const starts: number[] = [];
    let prev = 0;
    for (let f = 0; f < 60 * 20; f++) {
      const v = blink(f / 60, 0.3);
      if (v > 0.5 && prev <= 0.5) starts.push(f / 60);
      prev = v;
    }
    const gaps = starts.slice(1).map((t, i) => t - starts[i]);
    expect(gaps.length).toBeGreaterThan(3);
    expect(Math.max(...gaps) - Math.min(...gaps)).toBeGreaterThan(0.8);
  });

  it('blinks just after a cut', () => {
    expect(cutBlink(0.15)).toBeGreaterThan(0.5);
    expect(cutBlink(0.6)).toBe(0);
  });

  it('saccades hold still most of the time, then jump', () => {
    let still = 0;
    let jumps = 0;
    const n = 10 * FPS;
    for (let f = 0; f < n; f++) {
      const a = saccade(f / FPS, 0.3);
      const b = saccade((f + 1) / FPS, 0.3);
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      if (d < 0.05) still++;
      if (d > 0.3) jumps++;
    }
    expect(still / n).toBeGreaterThan(0.85);
    expect(jumps).toBeGreaterThanOrEqual(3);
  });
});
