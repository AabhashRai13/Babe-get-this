// Acting: events found in the analysed audio (phrases, onsets, drum hits, strums), and the motion curves that turn
// them into performance (anticipation, follow-through, reactions, sticks and strums). Pure functions of time.
import type { Hit } from './drums';
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
