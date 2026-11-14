// Always-on "life" for the characters. Pure functions of time so every frame renders the same way twice.

export const SEED = { mr: 0.3, mrs: 1.1 } as const;

const wob = (x: number) => Math.sin(x * 1.7) * 0.5 + Math.sin(x * 2.9 + 1.3) * 0.3 + Math.sin(x * 4.3 + 2.1) * 0.2;

/** Deterministic 0..1 noise for an integer index and a seed. */
export const hash = (i: number, seed: number) => {
  const v = Math.sin(i * 12.9898 + seed * 78.233) * 43758.5453;
  return v - Math.floor(v);
};

/** A damped spring from 0 to 1: quick, overshoots ~12%, settled after SPRING_SEC. */
export const SPRING_SEC = 0.6;
export function springProgress(t: number): number {
  if (t <= 0) return 0;
  if (t >= SPRING_SEC) return 1;
  const zeta = 0.55;
  const w = 2 * Math.PI * 3;
  const wd = w * Math.sqrt(1 - zeta * zeta);
  return 1 - Math.exp(-zeta * w * t) * (Math.cos(wd * t) + ((zeta * w) / wd) * Math.sin(wd * t));
}

const BLINK = 0.18;
const blinkCurve = (ph: number) => (ph >= 0 && ph < BLINK ? Math.sin((ph / BLINK) * Math.PI) : 0);

/** 0 = open, 1 = fully closed. Blinks at irregular 2.2–5.2 s intervals, different for each seed. */
export function blink(t: number, seed: number): number {
  let start = 0.6 + seed * 1.37;
  for (let i = 0; start <= t; i++) {
    if (t < start + BLINK) return blinkCurve(t - start);
    start += 2.2 + 3 * hash(i, seed);
  }
  return 0;
}

/** Animators blink a character just after a cut; it hides the jump and reads as natural. */
export const cutBlink = (sinceCut: number) => blinkCurve(sinceCut - 0.06);

/** Eyes hold a point, then dart to a new one (a saccade), rather than drifting. Offsets in art units. */
export function saccade(t: number, seed: number): { x: number; y: number } {
  const target = (i: number) => ({ x: (hash(i, seed + 3) - 0.5) * 7, y: (hash(i, seed + 7) - 0.5) * 5 });
  let start = 0;
  for (let i = 0; ; i++) {
    const len = 0.7 + 1.3 * hash(i, seed + 11);
    if (t < start + len) {
      const k = Math.min(1, (t - start) / 0.05);
      const a = i === 0 ? target(i) : target(i - 1);
      const b = target(i);
      return { x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k };
    }
    start += len;
  }
}

export const breathe = (t: number, seed: number) => Math.sin(t * 2.4 + seed) * 0.01;

/** Small damped bounce right after a cut. */
export const settle = (sinceCut: number) => Math.exp(-sinceCut * 9) * Math.sin(sinceCut * 22) * 0.03;

export const sway = (t: number, seed: number) => wob(t * 0.6 + seed) * 0.012;

/** Tiny pupil drift so eyes never look frozen. */
export const drift = (t: number, seed: number) => ({ x: wob(t * 0.7 + seed) * 2, y: wob(t * 0.5 + seed * 3) * 1.5 });
