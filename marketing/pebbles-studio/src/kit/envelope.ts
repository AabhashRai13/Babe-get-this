// Lip sync: the isolated vocals' voice-band loudness per video frame, mapped to how open the mouth is.
import type { Phrase } from './acting';
import { findPhrases } from './acting';
import type { Who } from './types';

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));

/** dB (from ffmpeg astats, voice band) to 0..1 on a fixed scale: -38 dB and below is closed, -16 dB is fully open. */
export function envelopeFromDb(db: number[]): number[] {
  return db.map((v) => (Number.isFinite(v) ? clamp01((v + 38) / 22) : 0));
}

/** The clip's loud vocal level: the 95th percentile of frames with any voice (above -60 dB); -20 if there is none. */
export function vocalReference(db: number[]): number {
  const v = db.filter((x) => Number.isFinite(x) && x > -60).sort((a, b) => a - b);
  if (v.length < 10) return -20;
  return v[Math.floor((v.length - 1) * 0.95)];
}

/**
 * How open, per frame, from the vocal stem's loudness. The scale is relative to the clip's own loud level, so a
 * quiet mix opens as wide as a loud one, and anything 26 dB or more below it (music bleed, breaths) stays shut.
 * Dips below the loudness of the last ~0.2 s (the gaps between sung syllables) close it part-way, so legato
 * singing still articulates instead of holding one open shape.
 */
export function openFromDb(db: number[], fps: number): number[] {
  const ref = vocalReference(db);
  const floor = ref - 26;
  const w = Math.max(1, Math.round(0.2 * fps));
  return db.map((v, i) => {
    if (!Number.isFinite(v)) return 0;
    const level = clamp01((v - floor) / 20);
    let peak = v;
    for (let j = Math.max(0, i - w); j <= Math.min(db.length - 1, i + w); j++) if (db[j] > peak) peak = db[j];
    const articulation = clamp01(1 - (peak - v) / 7);
    return level * (0.25 + 0.75 * articulation);
  });
}

/** How open the speaker's mouth is on this frame (the envelope is already smoothed). */
export function mouthOpen(env: number[], frame: number, speaking: boolean): number {
  if (!speaking || frame < 0 || frame >= env.length) return 0;
  return env[frame] ?? 0;
}

/** Opens fast (~20 ms) and closes slower (~75 ms), like a real jaw, instead of jittering frame to frame. */
export function smoothEnvelope(raw: number[], fps: number): number[] {
  const dt = 1 / fps;
  const up = 1 - Math.exp(-dt / 0.02);
  const down = 1 - Math.exp(-dt / 0.075);
  let y = 0;
  return raw.map((x) => (y += (x - y) * (x > y ? up : down)));
}

/** Mouth width from the voice's brightness: "ee" sounds widen it, "oo" sounds narrow it. ~0.75..1.3. */
export function mouthWidths(loDb: number[], hiDb: number[], fps: number): number[] {
  const lin = (d: number) => (Number.isFinite(d) ? Math.pow(10, d / 20) : 0);
  const k = 1 - Math.exp(-1 / fps / 0.05);
  let y = 1;
  return loDb.map((lo, i) => {
    const a = lin(lo);
    const b = lin(hiDb[i] ?? -99);
    const target = a + b > 1e-4 ? 0.75 + 0.55 * (b / (a + b)) : 1;
    return (y += (target - y) * k);
  });
}

/** Stressed syllables: frames where the mouth peaks after a clear dip. Drives nods and gestures. */
export function findAccents(open: number[], fps: number): number[] {
  const out: number[] = [];
  const back = Math.round(0.15 * fps);
  const gap = Math.round(0.18 * fps);
  for (let i = 1; i < open.length - 1; i++) {
    const v = open[i];
    if (v < 0.5 || v < open[i - 1] || v < open[i + 1]) continue;
    let lo = v;
    for (let j = Math.max(0, i - back); j < i; j++) lo = Math.min(lo, open[j]);
    if (v - lo < 0.3) continue;
    if (out.length && i - out[out.length - 1] < gap) continue;
    out.push(i);
  }
  return out;
}

/** A quick rise and an easing fall after the most recent accent. */
export function accentPulse(accents: number[], frame: number, fps: number): number {
  let last = -1;
  for (const a of accents) { if (a > frame) break; last = a; }
  if (last < 0) return 0;
  const dt = (frame - last) / fps;
  return dt < 0.03 ? dt / 0.03 : Math.exp(-(dt - 0.03) * 7);
}

/** Resample a per-frame array from one frame rate to another (old envelope files were 30 fps). */
export function resample(arr: number[], from: number, to: number): number[] {
  if (from === to) return arr;
  const n = Math.round((arr.length * to) / from);
  return Array.from({ length: n }, (_, i) => {
    const x = (i * from) / to;
    const a = Math.floor(x);
    const b = Math.min(arr.length - 1, a + 1);
    return (arr[a] ?? 0) + ((arr[b] ?? 0) - (arr[a] ?? 0)) * (x - a);
  });
}

/** Lip sync per frame: how open, how wide, how round, the stressed-syllable frames (accents), and the phrases. */
export type Lip = { open: number[]; width: number[]; round: number[]; accents: number[]; phrases: Phrase[] };

/** The JSON written by scripts/analyze.mjs. Old files have no `source` or music bands and may be 30 fps. */
export type EnvelopeFile = {
  fps?: number; source?: 'vocals' | 'mix'; db: number[]; lo?: number[]; hi?: number[];
  kick?: number[]; snare?: number[]; hat?: number[]; pluck?: number[];
};

/** Rounded lips ("oo") from a dark voice: the bright band's share of the two bands, low = round. 0..1, smoothed. */
export function mouthRound(loDb: number[], hiDb: number[], fps: number): number[] {
  const lin = (d: number) => (Number.isFinite(d) ? Math.pow(10, d / 20) : 0);
  const k = 1 - Math.exp(-1 / fps / 0.05);
  let y = 0;
  return loDb.map((lo, i) => {
    const a = lin(lo);
    const b = lin(hiDb[i] ?? -99);
    const target = a + b > 1e-4 ? clamp01((0.32 - b / (a + b)) / 0.2) : 0;
    return (y += (target - y) * k);
  });
}

/**
 * Smoothed lip sync at `fps` from an envelope file: openness, width, roundness, accents and phrases (all from the
 * voice). `ownerAt(sec)` says who the script has speaking then, so each phrase knows whose it is.
 */
export function lipFromEnvelope(j: EnvelopeFile, fps: number, ownerAt: (sec: number) => Who | null = () => null): Lip {
  const from = j.fps ?? 30;
  const open = smoothEnvelope(openFromDb(resample(j.db, from, fps), fps), fps);
  const lo = j.lo && j.hi ? resample(j.lo, from, fps) : null;
  const hi = j.lo && j.hi ? resample(j.hi, from, fps) : null;
  const width = lo && hi ? mouthWidths(lo, hi, fps) : open.map(() => 1);
  const round = lo && hi ? mouthRound(lo, hi, fps) : open.map(() => 0);
  // A phrase belongs to whoever the script has speaking; split it where the script hands over the line.
  const phrases: Phrase[] = [];
  for (const p of findPhrases(open, fps)) {
    let cur: Phrase = { start: p.start, end: p.end, who: ownerAt(p.start / fps) };
    for (let f = p.start + 1; f < p.end; f++) {
      const w = ownerAt(f / fps);
      if (w !== cur.who) {
        phrases.push({ ...cur, end: f });
        cur = { start: f, end: p.end, who: w };
      }
    }
    phrases.push(cur);
  }
  return { open, width, round, accents: findAccents(open, fps), phrases };
}
