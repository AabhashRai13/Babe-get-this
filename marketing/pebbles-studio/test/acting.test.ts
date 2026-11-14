import { describe, expect, it } from 'vitest';
import { drumHitsFromBands, findPhrases, musicFromEnvelope, onsets } from '../src/kit/acting';
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
