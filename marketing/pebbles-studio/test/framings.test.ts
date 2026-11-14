import { describe, expect, it } from 'vitest';
import { FRAMINGS, SAFE, captionLayout, isSafe, overlaps } from '../src/kit/framings';
import type { FrameName, SetName, Who } from '../src/kit/types';

const VERSE = 'Mañana por la mañana, la extraña araña de España araña con maña la telaraña de la montaña';
const LONG_LINE = 'skillfully scratches the spider web on the mountain';

const all = Object.entries(FRAMINGS).flatMap(([set, frames]) =>
  Object.entries(frames ?? {}).map(([frame, f]) => ({ name: `${set}/${frame}`, set: set as SetName, frame: frame as FrameName, f: f! })),
);

describe('safe area', () => {
  it('uses the zones from the first video', () => {
    expect(SAFE).toEqual({ top: 270, bottom: 420, right: { w: 140, y0: 900, y1: 1500 } });
    expect(isSafe({ x: 100, y: 300, w: 400, h: 400 })).toBe(true);
    expect(isSafe({ x: 100, y: 200, w: 400, h: 400 })).toBe(false); // top
    expect(isSafe({ x: 100, y: 1300, w: 400, h: 300 })).toBe(false); // bottom
    expect(isSafe({ x: 800, y: 1000, w: 200, h: 100 })).toBe(false); // right-side buttons
    expect(isSafe({ x: 800, y: 300, w: 200, h: 100 })).toBe(true); // right but above the buttons
  });

  it('covers every set in the spec', () => {
    expect(all.map((a) => a.name)).toEqual(
      expect.arrayContaining([
        'podcast/single-mr', 'podcast/single-mrs', 'podcast/two-shot', 'band/two-shot',
        'kitchen/single-mr', 'kitchen/single-mrs', 'kitchen/two-shot',
        'couch/single-mr', 'couch/single-mrs', 'couch/two-shot',
        'aisle/single-mr', 'aisle/single-mrs', 'aisle/two-shot', 'aisle/wide',
        'podcast/split', 'podcast/split-close', 'podcast/close-mr', 'podcast/close-mrs',
        'stage/single-mrs', 'stage/close-mrs', 'stage/two-shot',
      ]),
    );
  });

  for (const { name, f } of all) {
    it(`${name}: every framing keeps faces safe`, () => {
      for (const who of Object.keys(f.place) as Who[]) {
        const face = f.faces[who];
        expect(face, `${name} ${who} has a face box`).toBeDefined();
        expect(isSafe(face!), `${name} ${who} face ${JSON.stringify(face)}`).toBe(true);
      }
    });

    it.skipIf(!!f.sub)(`${name}: the longest verse stays safe`, () => {
      const c = captionLayout(VERSE, 'verse', f);
      expect(isSafe(c.box), JSON.stringify(c.box)).toBe(true);
      expect(c.fontSize).toBeGreaterThanOrEqual(40);
    });

    it.skipIf(!!f.sub)(`${name}: long captions shrink rather than cover a face`, () => {
      const text = 'Babe can you also grab the oat milk not the almond one and the eggs and bananas that are not too green please';
      const c = captionLayout(text, 'line', f);
      for (const face of Object.values(f.faces)) expect(overlaps(c.box, face!), JSON.stringify(c.box)).toBe(false);
      expect(c.fits).toBe(true);
    });

    it(`${name}: per-character captions stay safe and off the faces`, () => {
      for (const who of Object.keys(f.sub ?? {}) as Who[]) {
        for (const [text, style] of [['Sleep', 'label'], ['睡觉|shuìjiào', 'hanzi'], ['PANDA', 'popYellow']] as const) {
          const c = captionLayout(text, style, f, who);
          expect(c.fits, `${name} ${who} ${style} ${JSON.stringify(c.box)}`).toBe(true);
        }
      }
    });

    it.skipIf(!!f.sub)(`${name}: a single long line stays safe`, () => {
      const c = captionLayout(LONG_LINE, 'line', f);
      expect(isSafe(c.box), JSON.stringify(c.box)).toBe(true);
      expect(c.lines.join(' ')).toBe(LONG_LINE);
    });
  }
});
