// Acting: events found in the analysed audio (phrases, onsets, drum hits, strums), and the motion curves that turn
// them into performance (anticipation, follow-through, reactions, sticks and strums). Pure functions of time.
import type { Hit } from './drums';
import { springProgress } from './motion';
import type { EnvelopeFile } from './envelope';
import type { Who } from './types';

/** A stretch of speech or singing, in frames, owned by whoever the script says is speaking at its start. */
export type Phrase = { start: number; end: number; who: Who | null };

/** Drum hits and strums heard in the clip's own stems. `hits` is null when the envelope has no drum bands. */
export type Music = { hits: Hit[] | null; strums: number[] };
export const NO_MUSIC: Music = { hits: null, strums: [] };

/** Speech segments (frames) where the mouth is open; gaps under 0.3 s merge, segments under 0.1 s are dropped. */
export function findPhrases(open: number[], fps: number): { start: number; end: number }[] {
  const out: { start: number; end: number }[] = [];
  const gap = 0.3 * fps;
  let s = -1;
  for (let i = 0; i <= open.length; i++) {
    const on = i < open.length && open[i] > 0.08;
    if (on && s < 0) s = i;
    if (!on && s >= 0) {
      const last = out[out.length - 1];
      if (last && s - last.end < gap) last.end = i;
      else out.push({ start: s, end: i });
      s = -1;
    }
  }
  return out.filter((p) => p.end - p.start >= 0.1 * fps);
}

const percentile = (a: number[], q: number) => {
  const v = a.filter(Number.isFinite).sort((x, y) => x - y);
  return v.length ? v[Math.round((v.length - 1) * q)] : -99;
};

/**
 * Frames where a band jumps sharply: at least `rise` dB over the lowest of the 4 frames before, a local peak
 * (±2 frames), within `range` dB of the band's loud level, and `gapSec` after the previous onset.
 */
export function onsets(db: number[], fps: number, rise = 6, gapSec = 0.09, range = 30): number[] {
  const loud = percentile(db.filter((v) => v > -90), 0.95);
  const out: number[] = [];
  for (let i = 1; i < db.length; i++) {
    const v = db[i];
    if (v <= loud - range) continue;
    let lo = Infinity;
    for (let j = Math.max(0, i - 4); j < i; j++) lo = Math.min(lo, db[j]);
    if (v - lo < rise) continue;
    let peak = true;
    for (let j = Math.max(0, i - 2); j <= Math.min(db.length - 1, i + 2); j++) if (db[j] > v) peak = false;
    // A flat top counts once, at its first frame.
    if (!peak || (i > 0 && db[i - 1] === v)) continue;
    if (out.length && i - out[out.length - 1] < gapSec * fps) continue;
    out.push(i);
  }
  return out;
}

const peakNear = (a: number[], f: number) => Math.max(...a.slice(f, f + 4));

/**
 * Kick, snare and hat hits from the drums stem's bands. The bands overlap, so: a snare is an onset where the snare
 * band beats the hat band (otherwise it is hat noise), a hat at the same moment as a snare is part of the snare,
 * a kick must be within 10 dB of the loud kicks (quieter ones are spill from the snare and hats), and a hat that keeps ringing
 * (within 6 dB for 0.25 s) is a crash.
 */
export function drumHitsFromBands(b: { kick: number[]; snare: number[]; hat: number[] }, fps: number): Hit[] {
  const hits: Hit[] = [];
  const snares = onsets(b.snare, fps).filter((f) => peakNear(b.snare, f) > peakNear(b.hat, f) + 2);
  const kicks = onsets(b.kick, fps);
  const kickRef = percentile(kicks.map((f) => peakNear(b.kick, f)), 0.9);
  for (const f of kicks) if (peakNear(b.kick, f) >= kickRef - 10) hits.push({ t: f / fps, d: 'kick' });
  for (const f of snares) hits.push({ t: f / fps, d: 'snare' });
  const ring = Math.round(0.25 * fps);
  for (const f of onsets(b.hat, fps)) {
    if (snares.some((s) => Math.abs(s - f) <= 2)) continue;
    let rings = f + ring < b.hat.length;
    for (let k = 1; rings && k <= ring; k++) if (b.hat[f + k] < b.hat[f] - 6) rings = false;
    hits.push({ t: f / fps, d: rings ? 'crash' : 'hat' });
  }
  return hits.sort((x, y) => x.t - y.t);
}

/** Strum times (seconds): note onsets in the "other" stem (guitar, lute, keys). */
export function strumsFrom(pluck: number[], fps: number): number[] {
  return onsets(pluck, fps, 5, 0.1).map((f) => f / fps);
}

/** Drum hits and strums from envelope.json (at its own frame rate; times are seconds). */
export function musicFromEnvelope(j: EnvelopeFile, _fps: number): Music {
  const from = j.fps ?? 30;
  const hits = j.kick && j.snare && j.hat ? drumHitsFromBands({ kick: j.kick, snare: j.snare, hat: j.hat }, from) : null;
  return { hits, strums: j.pluck ? strumsFrom(j.pluck, from) : [] };
}

// ---------------------------------------------------------------------------------------------------------------
// Motion curves

/** How each Pebble moves. Mr is laid-back (slower, smaller, later); Mrs is quick (bigger brows, faster reactions). */
export type Persona = {
  /** Speed of idle motion and of springs (1 = neutral). */
  tempo: number;
  /** Size of leans, sways and nods. */
  amp: number;
  /** Seconds before a listener reacts to the other's phrase. */
  react: number;
  /** Size of brow lifts. */
  brow: number;
  /** How long eyes hold a point between darts (1 = neutral). */
  dart: number;
};
export const PERSONA: Record<Who, Persona> = {
  mr: { tempo: 0.75, amp: 0.85, react: 0.32, brow: 0.7, dart: 1.5 },
  mrs: { tempo: 1.25, amp: 1.15, react: 0.15, brow: 1.3, dart: 0.7 },
};

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
const smooth = (u: number) => { const x = clamp01(u); return x * x * (3 - 2 * x); };
const easeOut = (u: number) => 1 - Math.pow(1 - clamp01(u), 3);

/**
 * An action with anticipation and follow-through, dt seconds from the moment it fires: 0 before `wind` seconds
 * ahead, easing back to -0.25 (the wind-up), then springing to 1 with a small overshoot and settling.
 */
export function windUp(dt: number, wind: number): number {
  if (dt < -wind) return 0;
  if (dt < 0) return -0.25 * Math.sin((((dt + wind) / wind) * Math.PI) / 2);
  return -0.25 + 1.25 * springProgress(dt);
}

/** windUp that dies away after 0.15 s at `decay` per second: a nod, a brow pop, a gesture. */
export function hitAction(dt: number, wind: number, decay: number): number {
  return windUp(dt, wind) * (dt > 0.15 ? Math.exp(-(dt - 0.15) * decay) : 1);
}

/**
 * How far `who` leans toward the mic: a small lean back just before each of their phrases, a lean in that holds
 * while it lasts, then easing out over 0.45 s (scaled by tempo) after it ends. Other speakers' phrases don't count.
 */
export function phraseLean(phrases: Phrase[], who: Who, frame: number, fps: number, p: Persona): number {
  const t = frame / fps;
  const wind = 0.12 / p.tempo;
  let ph: Phrase | undefined;
  for (const q of phrases) if (q.who === who && q.start / fps - wind <= t) ph = q;
  if (!ph) return 0;
  const s = ph.start / fps;
  const e = ph.end / fps;
  if (t < e) return windUp((t - s) * p.tempo, wind * p.tempo);
  const atEnd = windUp((e - s) * p.tempo, wind * p.tempo);
  return atEnd * (1 - smooth((t - e) / (0.45 / p.tempo)));
}

/**
 * The listener's reactions to the other Pebble's latest phrase, `react` seconds late: a brow pop at its start,
 * a glance toward the speaker held while they talk, and a nod once they finish.
 */
export function listenReact(phrases: Phrase[], who: Who, frame: number, fps: number, p: Persona): { brow: number; nod: number; glance: number } {
  const t = frame / fps;
  let ph: Phrase | undefined;
  for (const q of phrases) if (q.who !== null && q.who !== who && q.start / fps + p.react - 0.1 <= t) ph = q;
  if (!ph) return { brow: 0, nod: 0, glance: 0 };
  const s = ph.start / fps + p.react;
  const e = ph.end / fps + p.react;
  const brow = Math.max(0, hitAction(t - s, 0.1, 3));
  const nod = t < e - 0.1 ? 0 : Math.max(0, hitAction(t - e, 0.08, 5));
  const glance = t < e ? clamp01(windUp(t - s, 0.1)) : clamp01(windUp(e - s, 0.1)) * (1 - smooth((t - e) / 0.4));
  return { brow, nod, glance };
}

/**
 * A drum stick for a list of hit times: 0 = down on the drum, 1 = raised. It rebounds after each hit, holds,
 * then accelerates down into the next one. Fast runs become small taps (the lift shrinks with the gap),
 * and it settles at 0.6 when the drumming stops.
 */
export function strokeAngle(hits: number[], t: number): number {
  let prev = -Infinity;
  let next = Infinity;
  for (const h of hits) { if (h <= t) prev = h; else { next = h; break; } }
  const hasPrev = t - prev < 1;
  const hasNext = next - t < 1;
  const gap = hasPrev && hasNext ? next - prev : 1;
  const up = Math.min(0.12, gap / 3);
  const down = Math.min(0.15, gap / 2);
  const rise = hasPrev
    ? (hasNext ? easeOut((t - prev) / up) : Math.min(easeOut((t - prev) / up), 1 - 0.4 * smooth((t - prev - 0.3) / 0.4)))
    : 0.6 + (hasNext ? 0.4 * smooth(1 - (next - t) / 0.3) : 0);
  const fall = hasNext ? Math.min(1, Math.pow((next - t) / down, 2)) : 1;
  return Math.min(rise, fall) * Math.min(1, gap / 0.25);
}

/**
 * A strumming hand for a list of strum times, -1 (up) .. 1 (down). Strums alternate down and up; each pulls back
 * a little for 0.03 s, then sweeps across in 0.07 s and holds there until the next.
 */
export function strumAngle(strums: number[], t: number): number {
  let k = -1;
  for (let i = 0; i < strums.length && strums[i] - 0.03 <= t; i++) k = i;
  if (k < 0) return -1;
  const d = k % 2 === 0 ? 1 : -1;
  const s = strums[k];
  if (t < s) return -d * (1 + 0.15 * Math.sin((((t - (s - 0.03)) / 0.03) * Math.PI) / 2));
  const from = -1.15 * d;
  return from + (d - from) * easeOut((t - s) / 0.07);
}
