// Full-body poses. Arms are single ellipses hung from a shoulder; rot is radians, + swings the hand
// toward the character's left (screen left), - toward screen right. Arm index 1 is the "hand" arm.
import { CHARACTERS } from './geometry';
import type { Arm, Body, PoseName, Who } from './types';

type PoseFn = (who: Who, t: number, sincePose: number) => Body;

const sh = (who: Who) => {
  const C = CHARACTERS[who];
  const sx = C.inner.w / 2 - 20;
  return { sx, chairX: C.inner.w / 2 - 34, bottom: C.bottom, holdX: sx + (who === 'mrs' ? 15 : 0) };
};
const body = (arms: [Arm, Arm], o: Partial<Body> = {}): Body => ({ arms, tilt: 0, squash: 0, dy: 0, feet: 'apart', hand: 1, ...o });
const easeOut = (p: number) => 1 - Math.pow(1 - Math.max(0, Math.min(1, p)), 3);

export const WALK_SEC = 1.0;

/** Horizontal offset for walkIn: -700 art units (off-frame) to 0 over one second, easing out. */
export function walkOffset(sincePose: number): number {
  const p = sincePose / WALK_SEC;
  if (p >= 1) return 0;
  return -700 * (1 - easeOut(p));
}

export const POSES: Record<PoseName, PoseFn> = {
  sit: (who) => {
    const { chairX, bottom } = sh(who);
    return body([{ x: -chairX, y: bottom * 0.1, rot: 0.95 }, { x: chairX, y: bottom * 0.1, rot: -0.95 }]);
  },
  stand: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: sx, y: 40, rot: -0.45 }]);
  },
  walkIn: (who, t, since) => {
    const { sx } = sh(who);
    const walking = since < WALK_SEC;
    const swing = walking ? Math.sin(t * Math.PI * 8) * 0.35 : 0;
    return body([{ x: -sx, y: 40, rot: 0.45 + swing }, { x: sx, y: 40, rot: -0.45 + swing }], {
      dy: walking ? -Math.abs(Math.sin(t * Math.PI * 4)) * 22 : 0,
      tilt: walking ? 0.06 : 0,
      feet: walking ? 'step' : 'apart',
    });
  },
  point: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: sx, y: 20, rot: -2.0 }]);
  },
  wave: (who, t) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: sx, y: 10, rot: -2.6 + Math.sin(t * 10) * 0.25 }]);
  },
  shrug: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 20, rot: 1.9 }, { x: sx, y: 20, rot: -1.9 }], { dy: -6, squash: -0.02 });
  },
  armsCrossed: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx + 10, y: 70, rot: -1.25 }, { x: sx - 10, y: 85, rot: 1.25 }]);
  },
  handsOnHips: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx - 30, y: 40, rot: -0.55 }, { x: sx + 30, y: 40, rot: 0.55 }]);
  },
  // Held props sit at chest height, to the side of the mouth, so the face stays readable.
  holdPhone: (who) => {
    const { sx, holdX } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: holdX, y: 60, rot: 1.3 }]);
  },
  holdBag: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: sx, y: 40, rot: -0.25 }]);
  },
  holdList: (who) => {
    const { sx, holdX } = sh(who);
    return body([{ x: -sx, y: 40, rot: 0.45 }, { x: holdX, y: 70, rot: 1.2 }]);
  },
  // Slumped forward and sunk down, arms hanging: asleep where they sat or stood.
  asleep: (who) => {
    const { sx } = sh(who);
    return body([{ x: -sx, y: 60, rot: 0.15 }, { x: sx, y: 60, rot: -0.15 }], { tilt: who === 'mr' ? 0.32 : -0.32, squash: -0.06, dy: 24, feet: 'together' });
  },
  drum: () => body([{ x: -150, y: 40, rot: 0.45 }, { x: 150, y: 40, rot: -0.45 }]),
  hug: (who) => {
    const { sx } = sh(who);
    const lean = who === 'mr' ? 1 : -1;
    // The inner arm (toward the partner) reaches across; the outer arm rests.
    const inner: Arm = { x: lean * sx, y: 30, rot: -lean * 1.45 };
    const outer: Arm = { x: -lean * sx, y: 40, rot: lean * 0.45 };
    return body(lean > 0 ? [outer, inner] : [inner, outer], { tilt: lean * 0.15, hand: lean > 0 ? 1 : 0 });
  },
};

export function bodyFor(who: Who, pose: PoseName, t: number, sincePose: number): Body {
  return POSES[pose](who, t, sincePose);
}

/** The tip of the hand arm (body.hand), in the character's art units, plus that arm's rotation. */
export function handAnchor(who: Who, b: Body): { x: number; y: number; rot: number } {
  const a = b.arms[b.hand];
  const d = CHARACTERS[who].armRy * 1.45;
  return { x: a.x - d * Math.sin(a.rot), y: a.y + d * Math.cos(a.rot), rot: a.rot };
}

const lerp = (a: number, b: number, k: number) => a + (b - a) * k;
/** Between two bodies (k may overshoot past 1 slightly with a spring). */
export function blendBody(a: Body, b: Body, k: number): Body {
  const arm = (i: 0 | 1) => ({ x: lerp(a.arms[i].x, b.arms[i].x, k), y: lerp(a.arms[i].y, b.arms[i].y, k), rot: lerp(a.arms[i].rot, b.arms[i].rot, k) });
  return {
    arms: [arm(0), arm(1)],
    tilt: lerp(a.tilt, b.tilt, k),
    squash: lerp(a.squash, b.squash, k),
    dy: lerp(a.dy, b.dy, k),
    feet: k < 0.5 ? a.feet : b.feet,
    hand: k < 0.5 ? a.hand : b.hand,
  };
}
