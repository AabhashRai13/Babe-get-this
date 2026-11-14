import { describe, expect, it } from 'vitest';
import type { Phrase } from '../src/kit/acting';
import type { Hit } from '../src/kit/drums';
import type { LiveCtx } from '../src/kit/scene/live';
import { liveBody, liveFace } from '../src/kit/scene/live';
import { NO_LIP } from '../src/kit/scene/VideoComposition';
import { FPS, resolveAt, video } from '../src/kit/timeline';
import type { Who } from '../src/kit/types';

const script = video({ slug: 'demo', end: 20, shots: [
  { at: 0, set: 'podcast', frame: 'two-shot', speaker: 'mr' },
  { at: 10, set: 'stage', frame: 'single-mrs', speaker: 'mrs', drums: true, mrs: { pose: 'stand', holding: 'lute' } },
] });

function ctxAt(t: number, o: Partial<LiveCtx> = {}, phrases: Phrase[] = []): LiveCtx {
  return {
    t, frame: Math.round(t * FPS), lip: { ...NO_LIP, phrases }, speaker: 'mr', strums: [], sinceCut: t, hits: [],
    beat: 0.5, drums: false, ...o,
  };
}
const cast = (who: Who, t = 1) => resolveAt(script, t).cast[who]!;
const phrases: Phrase[] = [{ start: 60, end: 180, who: 'mr' }];

describe('speaking', () => {
  it('the speaker leans in during a phrase, after a small lean back', () => {
    const at = (f: number, ph: Phrase[]) => liveBody('mr', cast('mr'), ctxAt(f / FPS, {}, ph), 'chair').dy;
    expect(at(57, phrases)).toBeLessThan(at(57, []));
    expect(at(120, phrases) - at(120, [])).toBeGreaterThan(3);
  });

  it('the face slides with the lean', () => {
    const f = liveFace('mr', cast('mr'), ctxAt(2, {}, phrases));
    expect(f.shift!.y).toBeGreaterThan(2);
  });
});

describe('listening', () => {
  it('the listener lifts brows only after the other\'s phrase starts', () => {
    const mrs = cast('mrs');
    const brow = (f: number, ph: Phrase[]) => liveFace('mrs', mrs, ctxAt(f / FPS, {}, ph)).brow[0];
    expect(brow(55, phrases)).toBeCloseTo(brow(55, []), 6);
    expect(brow(78, phrases) - brow(78, [])).toBeGreaterThan(2);
  });

  it('glances toward the speaker', () => {
    const mrs = cast('mrs');
    const x = (ph: Phrase[]) => liveFace('mrs', mrs, ctxAt(2.5, {}, ph)).look.x;
    expect(x(phrases)).toBeLessThan(x([]) - 3); // Mr is to her left
  });
});

describe('personalities', () => {
  it('Mrs fidgets faster than Mr when idle', () => {
    const turns = (who: Who) => {
      let n = 0; let prev = 0; let prevD = 0;
      for (let f = 0; f < 10 * FPS; f++) {
        const tilt = liveBody(who, cast(who), ctxAt(f / FPS, { speaker: null }), 'chair').tilt;
        const d = tilt - prev;
        if (f > 1 && Math.sign(d) !== Math.sign(prevD)) n++;
        prev = tilt; prevD = d;
      }
      return n;
    };
    expect(turns('mrs')).toBeGreaterThan(turns('mr'));
  });
});

describe('secondary motion', () => {
  it('lag is 0 right after a cut and follows a lean otherwise', () => {
    expect(liveBody('mr', cast('mr'), ctxAt(1.0, { sinceCut: 0.05 }, phrases), 'chair').lag).toBe(0);
    expect(Math.abs(liveBody('mr', cast('mr'), ctxAt(1.05, { sinceCut: 1.05 }, phrases), 'chair').lag ?? 0)).toBeGreaterThan(0.001);
  });
});

describe('instruments', () => {
  it('drum sticks are up just before a hit and down on it', () => {
    const drummer = { ...cast('mr'), pose: 'drum' as const };
    const hits: Hit[] = [{ t: 11, d: 'snare' }, { t: 11.5, d: 'snare' }];
    const rot = (t: number) => liveBody('mr', drummer, ctxAt(t, { drums: true, hits }), 'drum').arms[0].rot;
    expect(rot(11.3)).toBeGreaterThan(rot(11.0) + 0.4);
    expect(rot(11.5)).toBeCloseTo(rot(11.0), 6);
  });

  it('the strum arm moves on strums, not between them', () => {
    const mrs = cast('mrs', 11);
    const rot = (t: number) => liveBody('mrs', mrs, ctxAt(t, { strums: [12, 13], speaker: 'mrs' }), 'mic').arms[1].rot;
    expect(rot(12.3)).toBeCloseTo(rot(12.8), 6);
    expect(Math.abs(rot(12.3) - rot(11.5))).toBeGreaterThan(0.1);
  });
});
