import { blendFace, faceFor } from './expressions';
import type { CastState, ExpressionName, Face, FrameName, Look, PoseName, PropName, Shot, VideoScript, Who } from './types';

export const FPS = 30;
export const END_CARD_SEC = 3;
/** A mid-shot expression change eases over this long. Cuts snap. */
export const EASE_SEC = 4 / FPS;

export const FRAME_CAST: Record<FrameName, Who[]> = {
  'single-mr': ['mr'],
  'single-mrs': ['mrs'],
  'two-shot': ['mr', 'mrs'],
  wide: ['mr', 'mrs'],
};

const WHO: Who[] = ['mr', 'mrs'];

export class ScriptError extends Error {}

export interface ResolvedCast {
  face: Face;
  pose: PoseName;
  look?: Look;
  holding?: PropName;
  headphones: boolean;
  facing: 'left' | 'right';
  speaking: boolean;
  /** When the current pose was set (seconds), for poses that animate in, such as walkIn. */
  poseSince: number;
}

export interface Resolved {
  shotIndex: number;
  shot: Shot;
  cutStart: number;
  caption: string;
  cast: Record<Who, ResolvedCast | null>;
  onEndCard: boolean;
}

/** Problems with a script, each as "shot <i>: <reason>". Empty means valid. */
export function validateScript(s: VideoScript): string[] {
  const errs: string[] = [];
  if (s.shots.length === 0) return ['shot 0: the script has no shots'];
  if (s.shots[0].at !== 0) errs.push('shot 0: the first shot must start at 0');
  s.shots.forEach((shot, i) => {
    const next = s.shots[i + 1]?.at ?? s.end;
    if (i > 0 && shot.at <= s.shots[i - 1].at) errs.push(`shot ${i}: shots must be in time order (at ${shot.at})`);
    if (shot.speaker && !FRAME_CAST[shot.frame].includes(shot.speaker)) {
      errs.push(`shot ${i}: speaker ${shot.speaker} is not on screen in ${shot.frame}`);
    }
    let prevBeat = shot.at;
    for (const b of shot.beats ?? []) {
      if (b.at < shot.at || b.at >= next) errs.push(`shot ${i}: beat at ${b.at} is outside the shot (${shot.at}–${next})`);
      if (b.at < prevBeat) errs.push(`shot ${i}: beats must be in time order (at ${b.at})`);
      if (b.speaker && !FRAME_CAST[shot.frame].includes(b.speaker)) {
        errs.push(`shot ${i}: beat at ${b.at}: speaker ${b.speaker} is not on screen in ${shot.frame}`);
      }
      prevBeat = b.at;
    }
  });
  const last = s.shots[s.shots.length - 1];
  if (s.end <= last.at) errs.push(`shot ${s.shots.length - 1}: end (${s.end}) must be after the last shot starts (${last.at})`);
  return errs;
}

/** Validates and returns the script. Throws "<slug> shot <i>: <reason>". */
export function video(s: VideoScript): VideoScript {
  const errs = validateScript(s);
  if (errs.length) throw new ScriptError(errs.map((e) => `${s.slug} ${e}`).join('\n'));
  return s;
}

export const framesFor = (s: VideoScript) => Math.ceil((s.end + (s.endCard ? END_CARD_SEC : 0)) * FPS);

interface Running {
  exprName: ExpressionName;
  face: Face;
  prevFace: Face;
  changedAt: number;
  eased: boolean;
  pose: PoseName;
  poseSince: number;
  look?: Look;
  holding?: PropName;
  headphones?: boolean;
  facing: 'left' | 'right';
}

const fresh = (): Running => {
  const face = faceFor('neutral');
  return { exprName: 'neutral', face, prevFace: face, changedAt: 0, eased: false, pose: 'sit', poseSince: 0, facing: 'right' };
};

function apply(r: Running, c: CastState | undefined, at: number, eased: boolean) {
  if (!c) return;
  if (c.expression !== undefined) {
    const next = typeof c.expression === 'string' ? faceFor(c.expression) : faceFor(c.expression, r.exprName);
    if (typeof c.expression === 'string') r.exprName = c.expression;
    r.prevFace = r.face;
    r.face = next;
    r.changedAt = at;
    r.eased = eased;
  }
  if (c.pose !== undefined && c.pose !== r.pose) { r.pose = c.pose; r.poseSince = at; }
  if (c.look !== undefined) r.look = c.look;
  if (c.holding !== undefined) r.holding = c.holding ?? undefined;
  if (c.headphones !== undefined) r.headphones = c.headphones;
  if (c.facing !== undefined) r.facing = c.facing;
}

function lookOffset(look: Look, who: Who): { x: number; y: number } {
  if (typeof look === 'object') return look;
  switch (look) {
    case 'camera': return { x: 0, y: 3 };
    case 'left': return { x: -12, y: 2 };
    case 'right': return { x: 12, y: 2 };
    case 'other': return { x: who === 'mr' ? 12 : -12, y: 2 };
  }
}

/** Everything about the video at time t: which shot, caption, and each on-screen character's state. */
export function resolveAt(s: VideoScript, t: number): Resolved {
  let idx = 0;
  for (let i = 0; i < s.shots.length; i++) if (s.shots[i].at <= t) idx = i;
  const shot = s.shots[idx];
  const run: Record<Who, Running> = { mr: fresh(), mrs: fresh() };
  let caption = shot.caption ?? '';
  let speaker: Who | null = shot.speaker ?? null;

  for (let i = 0; i <= idx; i++) {
    const sh = s.shots[i];
    for (const w of WHO) apply(run[w], sh[w], sh.at, false);
    for (const b of sh.beats ?? []) {
      if (i === idx && b.at > t) break;
      for (const w of WHO) apply(run[w], b[w], b.at, true);
      if (i === idx && b.caption !== undefined) caption = b.caption;
      if (i === idx && b.speaker !== undefined) speaker = b.speaker;
    }
  }

  const onScreen = FRAME_CAST[shot.frame];
  const defaultPhones = shot.set === 'podcast' || shot.set === 'band';
  const cast = { mr: null, mrs: null } as Record<Who, ResolvedCast | null>;
  for (const w of onScreen) {
    const r = run[w];
    let face = r.face;
    if (r.eased && t - r.changedAt < EASE_SEC) face = blendFace(r.prevFace, r.face, (t - r.changedAt) / EASE_SEC);
    if (r.look !== undefined) face = { ...face, look: lookOffset(r.look, w) };
    cast[w] = {
      face,
      pose: r.pose,
      look: r.look,
      holding: r.holding,
      headphones: r.headphones ?? defaultPhones,
      facing: r.facing,
      speaking: speaker === w,
      poseSince: r.poseSince,
    };
  }
  return { shotIndex: idx, shot, cutStart: shot.at, caption, cast, onEndCard: !!s.endCard && t >= s.end };
}
