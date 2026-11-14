// Per-frame acting on top of the resolved script state: blinking and eye darts, lip sync, leaning into phrases,
// reacting while listening, nods and gestures with wind-up and follow-through, breathing, secondary motion,
// drumming and strumming. Each Pebble moves to its own persona (acting.ts).
import { PERSONA, hitAction, listenReact, phraseLean, strokeAngle, strumAngle, windUp } from '../acting';
import type { Hit } from '../drums';
import { pulse } from '../drums';
import type { Lip } from '../envelope';
import { mouthOpen } from '../envelope';
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
const clamp01 = (v: number) => clamp(v, 0, 1);

const FPS_OF = (ctx: LiveCtx) => (ctx.t > 0 ? ctx.frame / ctx.t : 60);

/** Screen side of each Pebble's mic, and of the other Pebble (Mr sits left of Mrs). */
const MIC_SIDE: Record<Who, number> = { mr: 1, mrs: -1 };
const OTHER_SIDE: Record<Who, number> = { mr: 1, mrs: -1 };

/** Index of the most recent accent at or before `frame` (for alternating nod direction), or -1. */
function lastAccent(acc: number[], frame: number): number {
  let k = -1;
  for (let i = 0; i < acc.length && acc[i] <= frame; i++) k = i;
  return k;
}

/**
 * An action on each stressed syllable with anticipation: it winds up `wind` seconds before the accent (which we
 * know ahead, the audio is analysed offline), fires on it, and dies away at `decay` per second.
 */
function accentAction(acc: number[], frame: number, fps: number, wind: number, decay: number): number {
  const k = lastAccent(acc, frame);
  const after = k >= 0 ? hitAction((frame - acc[k]) / fps, wind, decay) : 0;
  const next = acc[k + 1];
  const before = next !== undefined && (next - frame) / fps < wind ? windUp((frame - next) / fps, wind) : 0;
  return after + before;
}

/** Drum kinds each stick plays: left hand hats and snare, right hand the downbeats (kick) and crashes. */
const STICK: [Hit['d'][], Hit['d'][]] = [['snare', 'hat', 'ohat'], ['kick', 'crash']];

export function liveFace(who: Who, rc: ResolvedCast, ctx: LiveCtx): Face {
  const f = rc.face;
  const fps = FPS_OF(ctx);
  const p = PERSONA[who];
  const phrases = ctx.lip.phrases;
  const open = Math.max(f.open, mouthOpen(ctx.lip.open, ctx.frame, rc.speaking));
  const width = rc.speaking ? ctx.lip.width[ctx.frame] ?? 1 : 1;
  const round = rc.speaking ? ctx.lip.round[ctx.frame] ?? 0 : 0;
  const accent = rc.speaking ? accentAction(ctx.lip.accents, ctx.frame, fps, 0.06, 7) : 0;
  const lean = phraseLean(phrases, who, ctx.frame, fps, p);
  const listen = listenReact(phrases, who, ctx.frame, fps, p);
  const wideAwake = f.lid === 0 && f.eyeScale > 1.05; // shock: no blinking
  const blinkNow = Math.max(blink(ctx.t, SEED[who]), cutBlink(ctx.sinceCut));
  const lid = f.happyEyes || wideAwake ? f.lid : Math.max(f.lid, blinkNow);
  const d = saccade(ctx.t / p.dart, SEED[who]);
  // On the kit, the head bobs a moment after each kick.
  const bob = rc.pose === 'drum' && ctx.drums ? pulse(ctx.hits, ctx.t - 0.05, ['kick'], 10) : 0;
  const brow = open * 3 + (accent * 7 + Math.max(0, lean) * 5 + listen.brow * 6) * p.brow;
  return {
    ...f,
    lid,
    open,
    round,
    mouthWidth: f.mouthWidth * (open > 0.05 ? width : 1),
    look: { x: f.look.x + d.x + OTHER_SIDE[who] * 6 * listen.glance, y: f.look.y + d.y },
    brow: [f.brow[0] + brow, f.brow[1] + brow],
    shift: {
      x: MIC_SIDE[who] * 9 * lean * p.amp,
      y: 6 * lean * p.amp - 4 * listen.nod + 6 * bob,
    },
  };
}

export type BodyMode = 'chair' | 'drum' | 'mic' | 'stand' | 'free';

/** Poses that leave the hand arm free to gesture while talking. */
const RESTING = new Set(['sit', 'stand']);

/** The body without secondary motion (liveBody samples it twice to find the drag). */
function bodyNow(who: Who, rc: ResolvedCast, ctx: LiveCtx, mode: BodyMode): Body {
  const C = CHARACTERS[who];
  const seed = SEED[who];
  const p = PERSONA[who];
  const fps = FPS_OF(ctx);
  const open = mouthOpen(ctx.lip.open, ctx.frame, rc.speaking);
  const accent = rc.speaking ? accentAction(ctx.lip.accents, ctx.frame, fps, 0.08 / p.tempo, 7 * p.tempo) : 0;
  const nodDir = lastAccent(ctx.lip.accents, ctx.frame) % 2 === 0 ? 1 : -1;
  const gesture = rc.speaking ? clamp(accentAction(ctx.lip.accents, ctx.frame, fps, 0.12 / p.tempo, 2.5 * p.tempo), -0.3, 1.1) : 0;
  const lean = phraseLean(ctx.lip.phrases, who, ctx.frame, fps, p);
  const listen = listenReact(ctx.lip.phrases, who, ctx.frame, fps, p);
  let b = bodyFor(who, rc.pose, ctx.t, ctx.t - rc.poseSince);
  // Mid-shot pose changes travel there on a spring instead of snapping.
  if (rc.prevPose && rc.poseBlend < 1) b = blendBody(bodyFor(who, rc.prevPose, ctx.t, 0), b, rc.poseBlend);
  const resting = RESTING.has(rc.pose);

  if (mode === 'chair' && resting) b = bodyFor(who, 'sit', ctx.t, 0);
  if (mode === 'stand' && rc.pose === 'sit') b = bodyFor(who, 'stand', ctx.t, 0);
  let recoil = 0;
  if (mode === 'mic' && resting) {
    const sx = C.inner.w * 0.5 - 34;
    if (rc.holding === 'lute') {
      // Fretting hand on the neck; strumming hand over the body, sweeping on each note the instrument plays.
      const s = strumAngle(ctx.strums, ctx.t);
      recoil = 2 * s * pulse(ctx.strums.map((t) => ({ t, d: 'hat' as const })), ctx.t, ['hat'], 12);
      b = { ...b, arms: [{ x: -sx, y: 40, rot: -1.1 + Math.sin(ctx.t * 1.3) * 0.03 }, { x: sx, y: 40, rot: 0.4 - 0.18 * s }] };
    } else {
      b = { ...b, arms: [{ x: -sx, y: 30, rot: 0.75 }, { x: sx, y: 30, rot: -0.75 }] };
    }
  }
  if (rc.pose === 'drum') {
    const on = ctx.drums;
    const stick = (i: 0 | 1) => (on ? strokeAngle(ctx.hits.filter((h) => STICK[i].includes(h.d)).map((h) => h.t), ctx.t) : 0.4);
    const kick = on ? pulse(ctx.hits, ctx.t, ['kick'], 12) : 0;
    const snare = on ? pulse(ctx.hits, ctx.t, ['snare'], 14) : 0;
    b = {
      ...b,
      // Weight drops into each kick; the snare rocks the body a little.
      dy: b.dy + kick * 12,
      squash: b.squash + kick * 0.025,
      tilt: b.tilt + snare * 0.02,
      arms: [
        { x: -150, y: 40, rot: 0.2 + stick(0) * 0.6 },
        { x: 150, y: 40, rot: -0.2 - stick(1) * 0.6 },
      ],
    };
  }

  const arms: [Body['arms'][0], Body['arms'][1]] = [{ ...b.arms[0] }, { ...b.arms[1] }];
  if (resting && mode !== 'drum' && !(mode === 'mic' && rc.holding === 'lute')) {
    arms[0].rot += Math.sin(ctx.t * 3 * p.tempo) * 0.02;
    arms[1].rot += -gesture * 0.55 * p.amp - Math.sin(ctx.t * 2.3 * p.tempo) * 0.02;
  }
  const swayBeat = mode === 'mic' && ctx.drums ? Math.sin((ctx.sinceCut * Math.PI) / ctx.beat) * 0.035 : 0;
  const nod = accent * 0.03 * nodDir * p.amp + listen.nod * 0.035;
  return {
    ...b,
    arms,
    recoil,
    // Idle sway and breathing at each Pebble's own tempo; a lean toward the mic for each phrase; nods.
    tilt: b.tilt + sway(ctx.t * p.tempo, seed) * p.amp + swayBeat + nod + lean * 0.05 * MIC_SIDE[who] * p.amp,
    squash: b.squash + breathe(ctx.t * p.tempo, seed) + settle(ctx.sinceCut) + open * 0.01 - accent * 0.012 - lean * 0.012,
    dy: b.dy - open * 4 + accent * 9 + lean * 8 * p.amp + listen.nod * 6,
  };
}

/**
 * The resolved pose plus life: breathing, sway, the settle after a cut, leaning into phrases, nods, gestures,
 * drumming and strumming. `lag` is how far the headphones and lute trail the body's rotation (0 just after a cut).
 * `mode` lets a set adapt resting poses (armrests in the podcast chairs, drumming, holding a stand mic).
 */
export function liveBody(who: Who, rc: ResolvedCast, ctx: LiveCtx, mode: BodyMode): Body {
  const b = bodyNow(who, rc, ctx, mode);
  const fps = FPS_OF(ctx);
  const back = Math.round(0.08 * fps);
  const before = bodyNow(who, rc, { ...ctx, t: ctx.t - back / fps, frame: ctx.frame - back, sinceCut: ctx.sinceCut - back / fps }, mode);
  const sinceCutOk = clamp01((ctx.sinceCut - 0.1) / 0.1);
  return { ...b, lag: sinceCutOk > 0 ? (b.tilt - before.tilt) * 1.5 * sinceCutOk : 0 };
}
