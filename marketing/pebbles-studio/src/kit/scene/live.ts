// Per-frame "life" on top of the resolved script state: blinking, lip sync, breathing, gestures.
import type { Hit } from '../drums';
import { pulse } from '../drums';
import type { Lip } from '../envelope';
import { accentPulse, mouthOpen } from '../envelope';
import { CHARACTERS } from '../geometry';
import { SEED, blink, breathe, cutBlink, saccade, settle, sway } from '../motion';
import type { ResolvedCast } from '../timeline';
import { blendBody, bodyFor } from '../poses';
import type { Body, Face, Who } from '../types';

export interface LiveCtx {
  t: number;
  frame: number;
  lip: Lip;
  /** Who the script has speaking now (even off screen), so the other Pebble can react. */
  speaker: Who | null;
  /** Strum times (seconds) heard in the clip's instrument stem. */
  strums: number[];
  sinceCut: number;
  hits: Hit[];
  beat: number;
  drums: boolean;
}

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));

const FPS_OF = (ctx: LiveCtx) => (ctx.t > 0 ? ctx.frame / ctx.t : 60);

/** Index of the most recent accent (for alternating nod direction), or -1. */
function lastAccent(acc: number[], frame: number): number {
  let k = -1;
  for (let i = 0; i < acc.length && acc[i] <= frame; i++) k = i;
  return k;
}

/** A slower swell after each stressed syllable: drives hand gestures. */
function gestureLevel(acc: number[], frame: number, fps: number): number {
  const k = lastAccent(acc, frame);
  if (k < 0) return 0;
  const dt = (frame - acc[k]) / fps;
  return dt < 0.08 ? dt / 0.08 : Math.exp(-(dt - 0.08) * 2.5);
}

export function liveFace(who: Who, rc: ResolvedCast, ctx: LiveCtx): Face {
  const f = rc.face;
  const fps = FPS_OF(ctx);
  const open = Math.max(f.open, mouthOpen(ctx.lip.open, ctx.frame, rc.speaking));
  const width = rc.speaking ? ctx.lip.width[ctx.frame] ?? 1 : 1;
  const accent = rc.speaking ? accentPulse(ctx.lip.accents, ctx.frame, fps) : 0;
  const wideAwake = f.lid === 0 && f.eyeScale > 1.05; // shock: no blinking
  const blinkNow = Math.max(blink(ctx.t, SEED[who]), cutBlink(ctx.sinceCut));
  const lid = f.happyEyes || wideAwake ? f.lid : Math.max(f.lid, blinkNow);
  const d = saccade(ctx.t, SEED[who]);
  return {
    ...f,
    lid,
    open,
    mouthWidth: f.mouthWidth * (open > 0.05 ? width : 1),
    look: { x: f.look.x + d.x, y: f.look.y + d.y },
    brow: [f.brow[0] + open * 3 + accent * 7, f.brow[1] + open * 3 + accent * 7],
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
  const fps = FPS_OF(ctx);
  const open = mouthOpen(ctx.lip.open, ctx.frame, rc.speaking);
  const accent = rc.speaking ? accentPulse(ctx.lip.accents, ctx.frame, fps) : 0;
  const nodDir = lastAccent(ctx.lip.accents, ctx.frame) % 2 === 0 ? 1 : -1;
  const g = rc.speaking ? clamp(gestureLevel(ctx.lip.accents, ctx.frame, fps), 0, 1) : 0;
  let b = bodyFor(who, rc.pose, ctx.t, ctx.t - rc.poseSince);
  // Mid-shot pose changes travel there on a spring instead of snapping.
  if (rc.prevPose && rc.poseBlend < 1) b = blendBody(bodyFor(who, rc.prevPose, ctx.t, 0), b, rc.poseBlend);
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
    // Talking: a small nod and tilt on each stressed syllable, plus a little lift with the voice.
    tilt: b.tilt + sway(ctx.t, seed) + swayBeat + accent * 0.025 * nodDir,
    squash: b.squash + breathe(ctx.t, seed) + settle(ctx.sinceCut) + open * 0.01 - accent * 0.012,
    dy: b.dy - open * 4 + accent * 9,
  };
}
