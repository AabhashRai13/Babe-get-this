import { describe, expect, it } from 'vitest';
import type { Phrase } from '../src/kit/acting';
import { PERSONA, drumHitsFromBands, findPhrases, hitAction, listenReact, musicFromEnvelope, onsets, phraseLean, strokeAngle, strumAngle, windUp } from '../src/kit/acting';
import { lipFromEnvelope } from '../src/kit/envelope';

const FPS = 60;
const fill = (n: number, v: number) => Array.from({ length: n }, () => v);
/** A band at -99 with a spike at each frame in `at`, decaying `decay` dB per frame. */
function spikes(n: number, at: number[], peak = -20, decay = 4): number[] {
  const a = fill(n, -99);
  for (const f of at) for (let k = 0; f + k < n; k++) a[f + k] = Math.max(a[f + k], peak - k * decay);
  return a;
}

describe('events from the envelope', () => {
  it('findPhrases merges short gaps and drops blips', () => {
    const open = [...fill(30, 0.5), ...fill(10, 0), ...fill(30, 0.5), ...fill(60, 0), ...fill(3, 0.6), ...fill(60, 0)];
    expect(findPhrases(open, FPS)).toEqual([{ start: 0, end: 70 }]);
  });

  it('onsets finds sharp rises, not slow swells', () => {
    const sharp = [...fill(30, -60), ...fill(30, -20)];
    const swell = Array.from({ length: 60 }, (_, i) => -60 + i);
    expect(onsets(sharp, FPS)).toEqual([30]);
    expect(onsets(swell, FPS)).toEqual([]);
  });

  it('drumHitsFromBands labels kick, snare, hat and a sustained hat as crash', () => {
    const n = 300;
    const hat = spikes(n, [200], -20, 4);
    for (let k = 0; k < 20; k++) hat[150 + k] = -20 - k * 0.2; // rings: a crash
    const hits = drumHitsFromBands({ kick: spikes(n, [60, 120]), snare: spikes(n, [90]), hat }, FPS);
    expect(hits.map((h) => [Math.round(h.t * FPS), h.d])).toEqual([[60, 'kick'], [90, 'snare'], [120, 'kick'], [150, 'crash'], [200, 'hat']]);
  });

  it('tells a snare from hat bleed, and drops a kick that is only snare spill', () => {
    const n = 200;
    // A snare at 40 (its band louder than the hats') with spill into kick and hat; a hat at 100 bleeding into the snare band.
    const snare = spikes(n, [40], -20); const hat = spikes(n, [40], -28);
    const s2 = spikes(n, [100], -34); const h2 = spikes(n, [100], -29);
    const kick = spikes(n, [40], -33); const k2 = spikes(n, [160], -18);
    const max = (a: number[], b: number[]) => a.map((v, i) => Math.max(v, b[i]));
    const hits = drumHitsFromBands({ kick: max(kick, k2), snare: max(snare, s2), hat: max(hat, h2) }, FPS);
    expect(hits.map((h) => [Math.round(h.t * FPS), h.d])).toEqual([[40, 'snare'], [100, 'hat'], [160, 'kick']]);
  });

  it('musicFromEnvelope gives null hits for old envelopes but [] for a quiet stem', () => {
    expect(musicFromEnvelope({ fps: 60, db: fill(60, -99) }, FPS)).toEqual({ hits: null, strums: [] });
    const quiet = { fps: 60, db: fill(60, -99), kick: fill(60, -99), snare: fill(60, -99), hat: fill(60, -99), pluck: fill(60, -99) };
    expect(musicFromEnvelope(quiet, FPS)).toEqual({ hits: [], strums: [] });
  });

  it('strums come from the pluck band', () => {
    const env = { fps: 60, db: fill(300, -99), pluck: spikes(300, [30, 90, 150], -25, 2) };
    expect(musicFromEnvelope(env, FPS).strums).toEqual([0.5, 1.5, 2.5]);
  });
});

describe('lip extras', () => {
  const talk = (n: number) => Array.from({ length: n }, (_, i) => (i % 20 < 10 ? -18 : -34));

  it('phrases carry the script speaker at their start, null when nobody speaks', () => {
    const db = [...talk(60), ...fill(90, -95), ...talk(60), ...fill(60, -95)];
    const lip = lipFromEnvelope({ fps: 60, db }, FPS, (s) => (s < 2 ? 'mr' : null));
    expect(lip.phrases.map((p) => p.who)).toEqual(['mr', null]);
    expect(lip.phrases[1].start).toBeGreaterThan(140);
  });

  it('a phrase splits where the script hands the line to the other Pebble', () => {
    const db = [...fill(30, -95), ...talk(120), ...fill(60, -95)]; // one unbroken 2 s run of syllables
    const lip = lipFromEnvelope({ fps: 60, db }, FPS, (s) => (s < 1.5 ? 'mr' : 'mrs'));
    expect(lip.phrases.map((p) => p.who)).toEqual(['mr', 'mrs']);
    expect(lip.phrases[1].start).toBe(90);
    expect(lip.phrases[0].end).toBe(90);
  });

  it('round is high for dark vowels and low for bright ones', () => {
    const db = fill(120, -20);
    const dark = lipFromEnvelope({ fps: 60, db, lo: fill(120, -15), hi: fill(120, -40) }, FPS);
    const bright = lipFromEnvelope({ fps: 60, db, lo: fill(120, -30), hi: fill(120, -15) }, FPS);
    expect(dark.round[100]).toBeGreaterThan(0.7);
    expect(bright.round[100]).toBeLessThan(0.1);
  });
});

describe('motion curves', () => {
  it('windUp dips before the action and overshoots after', () => {
    expect(windUp(-0.5, 0.1)).toBe(0);
    expect(windUp(0, 0.1)).toBeCloseTo(-0.25, 6);
    let peak = 0;
    for (let t = 0; t < 0.6; t += 0.01) peak = Math.max(peak, windUp(t, 0.1));
    expect(peak).toBeGreaterThan(1.03);
    expect(windUp(0.7, 0.1)).toBeCloseTo(1, 2);
  });

  it('hitAction rises and then dies away', () => {
    expect(hitAction(0.12, 0.08, 4)).toBeGreaterThan(0.6);
    expect(hitAction(1.5, 0.08, 4)).toBeLessThan(0.05);
  });

  const phrases: Phrase[] = [{ start: 60, end: 180, who: 'mr' }];

  it('phraseLean leans back before the phrase, in during it, and lets go after', () => {
    expect(phraseLean(phrases, 'mr', 58, FPS, PERSONA.mr)).toBeLessThan(0);
    expect(phraseLean(phrases, 'mr', 120, FPS, PERSONA.mr)).toBeGreaterThan(0.9);
    expect(phraseLean(phrases, 'mr', 260, FPS, PERSONA.mr)).toBe(0);
  });

  it('phraseLean is 0 for the other Pebble\'s phrases', () => {
    for (const f of [58, 120, 200]) expect(phraseLean(phrases, 'mrs', f, FPS, PERSONA.mrs)).toBe(0);
  });

  it('only the listener reacts, and Mr reacts later than Mrs', () => {
    expect(listenReact(phrases, 'mr', 100, FPS, PERSONA.mr)).toEqual({ brow: 0, nod: 0, glance: 0 });
    const peakAt = (who: 'mr' | 'mrs') => {
      const own: Phrase[] = [{ start: 60, end: 180, who: who === 'mr' ? 'mrs' : 'mr' }];
      let best = 0; let at = 0;
      for (let f = 60; f < 140; f++) { const b = listenReact(own, who, f, FPS, PERSONA[who]).brow; if (b > best) { best = b; at = f; } }
      return at;
    };
    expect(peakAt('mr')).toBeGreaterThan(peakAt('mrs') + 6);
    expect(PERSONA.mr.brow).toBeLessThan(PERSONA.mrs.brow);
    expect(PERSONA.mr.tempo).toBeLessThan(PERSONA.mrs.tempo);
    expect(listenReact([{ start: 60, end: 180, who: null }], 'mr', 100, FPS, PERSONA.mr).brow).toBe(0);
  });

  it('the listener nods after the other\'s phrase ends', () => {
    const own: Phrase[] = [{ start: 60, end: 180, who: 'mr' }];
    expect(listenReact(own, 'mrs', 150, FPS, PERSONA.mrs).nod).toBe(0);
    expect(listenReact(own, 'mrs', 195, FPS, PERSONA.mrs).nod).toBeGreaterThan(0.5);
  });

  it('strokeAngle lifts before each hit and is down on it', () => {
    const hits = [1, 1.5, 2];
    expect(strokeAngle(hits, 1)).toBe(0);
    expect(strokeAngle(hits, 1.5)).toBe(0);
    expect(strokeAngle(hits, 1.3)).toBeGreaterThan(0.9);
    expect(strokeAngle(hits, 0.85)).toBeGreaterThan(strokeAngle(hits, 0.3)); // lifting into the first hit
    expect(strokeAngle(hits, 4)).toBeCloseTo(0.6, 2); // rests when the drumming stops
  });

  it('strokeAngle is continuous for 40 ms gaps', () => {
    const hits = Array.from({ length: 20 }, (_, i) => 1 + i * 0.04);
    let maxStep = 0;
    for (let f = 50; f < 120; f++) maxStep = Math.max(maxStep, Math.abs(strokeAngle(hits, (f + 1) / 60) - strokeAngle(hits, f / 60)));
    expect(maxStep).toBeLessThan(0.5);
  });

  it('strumAngle alternates direction per strum and holds between strums', () => {
    const strums = [1, 2];
    expect(strumAngle(strums, 0.5)).toBe(-1);
    expect(strumAngle(strums, 1.2)).toBe(1);
    expect(strumAngle(strums, 1.8)).toBe(1);
    expect(strumAngle(strums, 2.2)).toBe(-1);
    expect(strumAngle(strums, 0.985)).toBeLessThan(-1); // pulled back just before
  });
});
