import { EXPRESSIONS, blendFace, faceFor } from './expressions';
import { FRAMINGS, captionLayout } from './framings';
import { POSES } from './poses';
import { PROP_NAMES } from './types';
import type { CastState, CaptionStyle, CharCaption, ExpressionName, Face, FrameName, Look, PoseName, PropName, Shot, VideoScript, Who } from './types';

export const FPS = 30;
export const END_CARD_SEC = 3;
/** A mid-shot expression change eases over this long. Cuts snap. */
export const EASE_SEC = 4 / FPS;

export const FRAME_CAST: Record<FrameName, Who[]> = {
  'single-mr': ['mr'],
  'single-mrs': ['mrs'],
  'two-shot': ['mr', 'mrs'],
  wide: ['mr', 'mrs'],
  split: ['mr', 'mrs'],
  'split-close': ['mr', 'mrs'],
  'close-mr': ['mr'],
  'close-mrs': ['mrs'],
};

const WHO: Who[] = ['mr', 'mrs'];

export class ScriptError extends Error {}

export interface ResolvedCast {
  face: Face;
  pose: PoseName;
  look?: Look;
  holding?: PropName;
  headphones: boolean;
  glasses: boolean;
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
  captionStyle: CaptionStyle;
  /** Captions above each character (split and close shots). */
  captions: Partial<Record<Who, CharCaption>>;
  cast: Record<Who, ResolvedCast | null>;
  onEndCard: boolean;
}

/** Problems with a script, each as "shot <i>: <reason>". Empty means valid. */
export function validateScript(s: VideoScript): string[] {
  const errs: string[] = [];
  if (s.shots.length === 0) return ['shot 0: the script has no shots'];
  if (s.shots[0].at !== 0) errs.push('shot 0: the first shot must start at 0');
  const castErrors = (c: CastState | undefined, where: string) => {
    if (!c) return;
    if (typeof c.expression === 'string' && !(c.expression in EXPRESSIONS)) errs.push(`${where}: unknown expression ${c.expression}`);
    if (c.pose !== undefined && !(c.pose in POSES)) errs.push(`${where}: unknown pose ${c.pose}`);
    if (c.holding && !(PROP_NAMES as readonly string[]).includes(c.holding)) errs.push(`${where}: unknown prop ${c.holding}`);
  };
  s.shots.forEach((shot, i) => {
    const next = s.shots[i + 1]?.at ?? s.end;
    const framing = FRAMINGS[shot.set]?.[shot.frame];
    if (!framing) errs.push(`shot ${i}: no framing ${shot.frame} in set ${shot.set}`);
    const captionFits = (text: string | undefined, style: CaptionStyle = shot.captionStyle ?? 'line') =>
      !text || !framing || captionLayout(text, style, framing).fits;
    const charCaptions = (cs: Partial<Record<Who, CharCaption | null>> | undefined, where: string) => {
      for (const [w, c] of Object.entries(cs ?? {}) as [Who, CharCaption | null][]) {
        if (!FRAME_CAST[shot.frame].includes(w)) errs.push(`${where}: caption for ${w} who is not on screen in ${shot.frame}`);
        else if (c && framing && !captionLayout(c.text, c.style, framing, w).fits) errs.push(`${where}: caption for ${w} covers a face or the app UI`);
      }
    };
    charCaptions(shot.captions, `shot ${i}`);
    if (framing?.sub && (shot.caption || shot.beats?.some((b) => b.caption))) errs.push(`shot ${i}: split shots use per-character captions (captions: { mr, mrs }), not caption`);
    castErrors(shot.mr, `shot ${i}`);
    castErrors(shot.mrs, `shot ${i}`);
    if (!framing?.sub && !captionFits(shot.caption)) errs.push(`shot ${i}: caption covers a face or the app UI even at 40px; shorten it`);
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
      castErrors(b.mr, `shot ${i}: beat at ${b.at}`);
      castErrors(b.mrs, `shot ${i}: beat at ${b.at}`);
      if (!captionFits(b.caption, b.captionStyle ?? shot.captionStyle ?? 'line')) errs.push(`shot ${i}: beat at ${b.at}: caption covers a face or the app UI even at 40px; shorten it`);
      charCaptions(b.captions, `shot ${i}: beat at ${b.at}`);
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
  glasses: boolean;
  facing: 'left' | 'right';
}

const fresh = (): Running => {
  const face = faceFor('neutral');
  return { exprName: 'neutral', face, prevFace: face, changedAt: 0, eased: false, pose: 'sit', poseSince: 0, glasses: false, facing: 'right' };
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
  // Setting a pose (even the same one) restarts it, so a second walkIn walks again.
  if (c.pose !== undefined) { r.pose = c.pose; r.poseSince = at; }
  // A new expression brings back its own pupils unless the same change also sets a look.
  if (c.look !== undefined) r.look = c.look ?? undefined;
  else if (c.expression !== undefined) r.look = undefined;
  if (c.holding !== undefined) r.holding = c.holding ?? undefined;
  if (c.headphones !== undefined) r.headphones = c.headphones;
  if (c.glasses !== undefined) r.glasses = c.glasses;
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
  let captionStyle: CaptionStyle = shot.captionStyle ?? 'line';
  const captions: Partial<Record<Who, CharCaption>> = {};
  const applyCaps = (cs: Partial<Record<Who, CharCaption | null>> | undefined) => {
    for (const [w, c] of Object.entries(cs ?? {}) as [Who, CharCaption | null][]) {
      if (c) captions[w] = c;
      else delete captions[w];
    }
  };
  applyCaps(shot.captions);

  for (let i = 0; i <= idx; i++) {
    const sh = s.shots[i];
    for (const w of WHO) apply(run[w], sh[w], sh.at, false);
    for (const b of sh.beats ?? []) {
      if (i === idx && b.at > t) break;
      for (const w of WHO) apply(run[w], b[w], b.at, true);
      if (i === idx && b.caption !== undefined) caption = b.caption;
      if (i === idx && b.speaker !== undefined) speaker = b.speaker;
      if (i === idx && b.captionStyle !== undefined) captionStyle = b.captionStyle;
      if (i === idx) applyCaps(b.captions);
    }
  }

  const onScreen = FRAME_CAST[shot.frame];
  const defaultPhones = shot.set === 'podcast' || shot.set === 'band' || shot.set === 'stage';
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
      glasses: r.glasses,
      facing: r.facing,
      speaking: speaker === w,
      poseSince: r.poseSince,
    };
  }
  return { shotIndex: idx, shot, cutStart: shot.at, caption, captionStyle, captions, cast, onEndCard: !!s.endCard && t >= s.end };
}
