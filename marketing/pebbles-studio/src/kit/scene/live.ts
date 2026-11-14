// Per-frame "life" on top of the resolved script state: blinking, lip sync, breathing, gestures.
import type { Hit } from '../drums';
import { pulse } from '../drums';
import { mouthOpen } from '../envelope';
import { CHARACTERS } from '../geometry';
import { SEED, blink, breathe, drift, settle, sway } from '../motion';
import type { ResolvedCast } from '../timeline';
import { bodyFor } from '../poses';
import type { Body, Face, Who } from '../types';

export interface LiveCtx {
  t: number;
  frame: number;
  env: number[];
  sinceCut: number;
  hits: Hit[];
  beat: number;
  drums: boolean;
}

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));

/** Average mouth opening over the last 12 frames: drives talking gestures. */
export function talkEnergy(env: number[], frame: number, speaking: boolean): number {
  let s = 0;
  for (let k = 0; k < 12; k++) s += mouthOpen(env, frame - k, speaking);
  return s / 12;
}

export function liveFace(who: Who, rc: ResolvedCast, ctx: LiveCtx): Face {
  const f = rc.face;
  const open = Math.max(f.open, mouthOpen(ctx.env, ctx.frame, rc.speaking));
  const wideAwake = f.lid === 0 && f.eyeScale > 1.05; // shock: no blinking
  const lid = f.happyEyes || wideAwake ? f.lid : Math.max(f.lid, blink(ctx.t, SEED[who]));
  const d = drift(ctx.t, SEED[who]);
  return {
    ...f,
    lid,
    open,
    look: { x: f.look.x + d.x, y: f.look.y + d.y },
    brow: [f.brow[0] + open * 4, f.brow[1] + open * 4],
  };
}

export type BodyMode = 'chair' | 'drum' | 'mic' | 'stand' | 'free';

/** Poses that leave the hand arm free to gesture while talking. */
const RESTING = new Set(['sit', 'stand']);

/**
 * The resolved pose plus life: breathing, sway, the settle after a cut, talking bob and hand gestures.
 * `mode` lets a set adapt resting poses (armrests in the podcast chairs, drumming, holding a stand mic).
 */
export function liveBody(who: Who, rc: ResolvedCast, ctx: LiveCtx, mode: BodyMode): Body {
  const C = CHARACTERS[who];
  const seed = SEED[who];
  const open = mouthOpen(ctx.env, ctx.frame, rc.speaking);
  const g = clamp(talkEnergy(ctx.env, ctx.frame, rc.speaking) * 2.2, 0, 1);
  let b = bodyFor(who, rc.pose, ctx.t, ctx.t - rc.poseSince);
  const resting = RESTING.has(rc.pose);

  if (mode === 'chair' && resting) b = bodyFor(who, 'sit', ctx.t, 0);
  if (mode === 'stand' && rc.pose === 'sit') b = bodyFor(who, 'stand', ctx.t, 0);
  if (mode === 'mic' && resting) {
    const sx = C.inner.w * 0.5 - 34;
    b = { ...b, arms: [{ x: -sx, y: 30, rot: 0.75 }, { x: sx, y: 30, rot: -0.75 }] };
  }
  if (rc.pose === 'drum') {
    const on = ctx.drums;
    const lV = on ? pulse(ctx.hits, ctx.t, ['snare', 'hat', 'ohat'], 18) : 0;
    const rV = on ? pulse(ctx.hits, ctx.t, ['crash', 'kick'], 16) : 0;
    const kick = on ? pulse(ctx.hits, ctx.t, ['kick'], 12) : 0;
    b = {
      ...b,
      dy: b.dy + kick * 10,
      squash: b.squash + kick * 0.02,
      arms: [
        { x: -150, y: 40, rot: on ? 0.2 + (1 - lV) * 0.6 : 0.45 },
        { x: 150, y: 40, rot: on ? -0.2 - (1 - rV) * 0.6 : -0.45 },
      ],
    };
  }

  const arms: [Body['arms'][0], Body['arms'][1]] = [{ ...b.arms[0] }, { ...b.arms[1] }];
  if (resting && mode !== 'drum') {
    arms[0].rot += Math.sin(ctx.t * 3) * 0.02;
    arms[1].rot += -g * 0.55 - Math.sin(ctx.t * 2.3) * 0.02;
  }
  const swayBeat = mode === 'mic' && ctx.drums ? Math.sin((ctx.sinceCut * Math.PI) / ctx.beat) * 0.035 : 0;
  return {
    ...b,
    arms,
    tilt: b.tilt + sway(ctx.t, seed) + swayBeat,
    squash: b.squash + breathe(ctx.t, seed) + settle(ctx.sinceCut) + open * 0.012,
    dy: b.dy - open * 7,
  };
}
