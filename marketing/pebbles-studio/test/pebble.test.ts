import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Face } from '../src/kit/characters/Face';
import { Pebble } from '../src/kit/characters/Pebble';
import { EXPRESSIONS } from '../src/kit/expressions';
import { CREAM } from '../src/kit/geometry';
import type { Body } from '../src/kit/types';

const body: Body = { arms: [{ x: -150, y: 40, rot: 0.6 }, { x: 150, y: 40, rot: -0.6 }], tilt: 0, squash: 0, dy: 0, feet: 'apart', hand: 1 };
const draw = (outline?: boolean) =>
  renderToStaticMarkup(React.createElement('svg', null, React.createElement(Pebble, { who: 'mr', x: 0, y: 0, scale: 1, face: EXPRESSIONS.neutral, body, t: 0, outline })));

describe('Pebble', () => {
  it('has no cream rim by default (owner decision 2026-11-13)', () => {
    expect(draw()).not.toContain(CREAM);
  });

  it('draws the rim only when asked', () => {
    expect(draw(true)).toContain(CREAM);
  });
});

describe('rig inputs', () => {
  const svg = (el: React.ReactElement) => renderToStaticMarkup(React.createElement('svg', null, el));
  const pebble = (face = EXPRESSIONS.neutral, b: Body = body, phones?: React.ReactNode) =>
    svg(React.createElement(Pebble, { who: 'mrs', x: 0, y: 0, scale: 1, face, body: b, t: 0, phonesSlot: phones }));

  it('shifts the face features by face.shift', () => {
    expect(pebble({ ...EXPRESSIONS.neutral, shift: { x: 5, y: -3 } })).toContain('translate(5 -3)');
  });

  /** Width of the filled open-mouth path (its first and fourth x coordinates are the corners). */
  const mouthWidth = (round: number, mouthWidth = 1) => {
    const html = svg(React.createElement(Face, { who: 'mrs', t: 0, face: { ...EXPRESSIONS.neutral, lid: 0, open: 0.8, round, mouthWidth } })); // lid 0: no eyelid path before the mouth
    const d = html.match(/<path d="(M [^"]+Z)"/)![1];
    const xs = [...d.matchAll(/(-?[\d.]+) (-?[\d.]+)/g)].map((m) => Number(m[1]));
    return Math.max(...xs) - Math.min(...xs);
  };

  it('rounds the open mouth', () => {
    expect(mouthWidth(1)).toBeLessThan(0.6 * mouthWidth(0));
  });

  it('rotates headphones by body.lag', () => {
    const html = pebble(EXPRESSIONS.neutral, { ...body, lag: 0.1 }, React.createElement('g', { id: 'phones' }));
    expect(html).toMatch(/<g transform="rotate\(-5\.729\d* [^"]*"><g id="phones"/);
  });
});

describe('sunglasses', () => {
  const whites = (who: 'mr' | 'mrs', face: typeof EXPRESSIONS.neutral, glasses: boolean) =>
    (renderToStaticMarkup(React.createElement('svg', null, React.createElement(Face, { who, t: 0, face, glasses })))
      .match(/fill="#fff"/g) ?? []).length;

  it('hide the eyes completely, for every expression (nothing white peeks out under the lenses)', () => {
    for (const who of ['mr', 'mrs'] as const) {
      for (const [name, e] of Object.entries(EXPRESSIONS)) {
        const face = { ...e, mouthShape: 'curve' as const }; // a grin's teeth are white too; keep them out of the count
        expect(whites(who, face, false), `${who} ${name} without glasses`).toBeGreaterThan(0);
        expect(whites(who, face, true), `${who} ${name} with glasses`).toBe(0);
      }
    }
  });

  it('keep Mrs\' lashes showing above the frames', () => {
    const lashes = (glasses: boolean) => (renderToStaticMarkup(React.createElement('svg', null,
      React.createElement(Face, { who: 'mrs', t: 0, face: EXPRESSIONS.sing, glasses }))).match(/<line /g) ?? []).length;
    expect(lashes(true)).toBeGreaterThanOrEqual(6);
  });
});
