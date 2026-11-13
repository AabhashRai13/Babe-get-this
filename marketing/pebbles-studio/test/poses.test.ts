import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { Pebble } from '../src/kit/characters/Pebble';
import { EXPRESSIONS } from '../src/kit/expressions';
import { CHARACTERS } from '../src/kit/geometry';
import { POSES, bodyFor, handAnchor, walkOffset } from '../src/kit/poses';
import type { PoseName, Who } from '../src/kit/types';

const NAMES: PoseName[] = ['sit', 'stand', 'walkIn', 'point', 'wave', 'shrug', 'armsCrossed', 'handsOnHips', 'holdPhone', 'holdBag', 'holdList', 'drum', 'hug'];
const WHO: Who[] = ['mr', 'mrs'];

describe('pose library', () => {
  it('every pose is defined for both characters', () => {
    expect(Object.keys(POSES).sort()).toEqual([...NAMES].sort());
    for (const who of WHO) {
      for (const p of NAMES) {
        const b = bodyFor(who, p, 0.4, 0.4);
        const nums = [b.tilt, b.squash, b.dy, ...b.arms.flatMap((a) => [a.x, a.y, a.rot])];
        expect(nums.every(Number.isFinite), `${who} ${p}`).toBe(true);
      }
    }
  });

  it('walkIn arrives from off-frame in one second', () => {
    expect(walkOffset(0)).toBe(-700);
    expect(walkOffset(1.0)).toBe(0);
    expect(walkOffset(2.5)).toBe(0);
    expect(walkOffset(0.5)).toBeGreaterThan(-700);
    expect(walkOffset(0.5)).toBeLessThan(0);
  });

  it('hold poses put the hand near the body', () => {
    for (const who of WHO) {
      const C = CHARACTERS[who];
      for (const p of ['holdPhone', 'holdBag', 'holdList'] as PoseName[]) {
        const a = handAnchor(who, bodyFor(who, p, 0, 0));
        expect(Math.abs(a.x), `${who} ${p} x`).toBeLessThanOrEqual(C.outer.w / 2 + 120);
        expect(Math.abs(a.y), `${who} ${p} y`).toBeLessThanOrEqual(C.outer.h / 2 + 120);
      }
    }
  });

  it('hug leans each character toward the other', () => {
    expect(bodyFor('mr', 'hug', 0, 0).tilt).toBeGreaterThan(0.1);
    expect(bodyFor('mrs', 'hug', 0, 0).tilt).toBeLessThan(-0.1);
  });

  it('facing left mirrors the body but not the face', () => {
    const svg = renderToStaticMarkup(
      React.createElement('svg', null, React.createElement(Pebble, {
        who: 'mr', x: 0, y: 0, scale: 1, face: EXPRESSIONS.neutral, body: bodyFor('mr', 'stand', 0, 0), t: 0, facing: 'left', holding: 'phone',
      })),
    );
    expect(svg.match(/scale\(-1 1\)/g)?.length).toBe(2);
    expect(svg).toContain('data-prop="phone"');
  });
});
