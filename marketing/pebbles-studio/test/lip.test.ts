import { describe, expect, it } from 'vitest';
import { lipFromEnvelope, openFromDb, vocalReference } from '../src/kit/envelope';

const FPS = 60;
const SILENT = -95; // what the isolated vocal stem reads between words
const fill = (n: number, v: number) => Array.from({ length: n }, () => v);

describe('vocal reference level', () => {
  it('is the loud end of the frames that have any voice, ignoring silence', () => {
    const db = [...fill(600, SILENT), ...fill(90, -30), ...fill(10, -14)];
    expect(vocalReference(db)).toBeGreaterThan(-31);
    expect(vocalReference(db)).toBeLessThanOrEqual(-14);
  });

  it('falls back to a typical level when there is no voice at all', () => {
    expect(vocalReference(fill(300, SILENT))).toBe(-20);
  });
});

describe('mouth opening from the vocal stem', () => {
  // 1 s of a spoken word at -20 dB between silences.
  const word = [...fill(60, SILENT), ...fill(60, -20), ...fill(60, SILENT)];

  it('is shut in silence and open on the voice', () => {
    const o = openFromDb(word, FPS);
    expect(Math.max(...o.slice(0, 55))).toBe(0);
    expect(Math.max(...o.slice(125))).toBe(0);
    expect(o[90]).toBeGreaterThan(0.7);
  });

  it('stays shut on faint bleed from the music left in the stem', () => {
    const db = [...fill(60, -20), ...fill(120, -52)];
    const o = openFromDb(db, FPS);
    expect(Math.max(...o.slice(80))).toBe(0);
  });

  it('adapts to how loud the clip is (a quieter mix opens just as wide)', () => {
    const quiet = word.map((v) => (v === SILENT ? v : v - 10));
    expect(openFromDb(quiet, FPS)[90]).toBeCloseTo(openFromDb(word, FPS)[90], 6);
  });

  it('closes part-way on the dips between sung syllables', () => {
    // Legato singing: loud the whole time, with 7 dB dips between syllables every 0.25 s.
    const sung = Array.from({ length: 240 }, (_, i) => (i % 15 < 4 ? -23 : -16));
    const o = openFromDb(sung, FPS);
    const peaks = o.filter((_, i) => i % 15 === 10).slice(1, -1);
    const dips = o.filter((_, i) => i % 15 === 2).slice(1, -1);
    expect(Math.min(...peaks)).toBeGreaterThan(0.8);
    expect(Math.max(...dips)).toBeLessThan(0.65);
  });
});

describe('lip sync from envelope.json', () => {
  // A clip where the vocal stem is silent during a music break (2-4 s): drums and music don't reach it.
  const db = [
    ...Array.from({ length: 120 }, (_, i) => (i % 20 < 10 ? -18 : -34)), // talking, 3 syllables a second
    ...fill(120, SILENT), // music only
    ...Array.from({ length: 120 }, (_, i) => (i % 20 < 10 ? -18 : -34)),
  ];
  const env = { fps: FPS, source: 'vocals' as const, db, lo: fill(db.length, -25), hi: fill(db.length, -25) };
  const lip = lipFromEnvelope(env, FPS);

  it('keeps the mouth shut through the music break', () => {
    expect(Math.max(...lip.open.slice(135, 240))).toBeLessThan(0.02); // after the jaw's ~0.1 s close
  });

  it('moves the mouth on every syllable', () => {
    const talk = lip.open.slice(0, 120);
    expect(Math.max(...talk)).toBeGreaterThan(0.7);
    expect(Math.min(...talk.slice(20))).toBeLessThan(0.4);
  });

  it('puts accents (nods, brows, gestures) only on speech', () => {
    expect(lip.accents.length).toBeGreaterThan(4);
    expect(lip.accents.filter((a) => a >= 125 && a < 240)).toEqual([]);
  });

  it('reads old 30 fps envelopes', () => {
    const half = db.filter((_, i) => i % 2 === 0);
    const old = lipFromEnvelope({ fps: 30, db: half }, FPS);
    expect(old.open.length).toBe(db.length);
    expect(old.width.every((w) => w === 1)).toBe(true);
  });
});
