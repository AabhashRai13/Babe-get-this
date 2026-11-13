import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
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
