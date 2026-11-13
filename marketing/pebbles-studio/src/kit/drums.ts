import type { DrumGrid } from './types';

export type DrumKind = 'kick' | 'snare' | 'hat' | 'ohat' | 'crash';
export interface Hit { t: number; d: DrumKind }

/** The first video's groove: crash on the downbeat, kick each bar + a pickup, snare on 3, eighth-note hats. */
export function drumHits(g: DrumGrid): Hit[] {
  const hits: Hit[] = [{ t: g.start, d: 'crash' }];
  const bar = 4 * g.beat;
  for (let n = 0; ; n++) {
    const t = g.start + n * bar;
    if (t >= g.end) break;
    hits.push({ t, d: 'kick' });
    if (t + 3.6 * g.beat < g.end) hits.push({ t: t + 3.6 * g.beat, d: 'kick' });
    if (t + 2 * g.beat < g.end) hits.push({ t: t + 2 * g.beat, d: 'snare' });
  }
  for (let n = 0; ; n++) {
    const t = g.start + (n * g.beat) / 2;
    if (t >= g.end) break;
    hits.push({ t, d: n % 8 === 7 ? 'ohat' : 'hat' });
  }
  return hits.sort((a, b) => a.t - b.t);
}

/** 1 right on a hit of one of `kinds`, decaying as exp(-(t - last) * k); 0 before the first. */
export function pulse(hits: Hit[], t: number, kinds: DrumKind[], k = 16): number {
  let last = -1;
  for (const h of hits) {
    if (h.t > t) break;
    if (kinds.includes(h.d)) last = h.t;
  }
  return last < 0 ? 0 : Math.exp(-(t - last) * k);
}
