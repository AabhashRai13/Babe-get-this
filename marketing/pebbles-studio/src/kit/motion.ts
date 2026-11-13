// Always-on "life" for the characters. Pure functions of time so every frame renders the same way twice.

export const SEED = { mr: 0.3, mrs: 1.1 } as const;

const wob = (x: number) => Math.sin(x * 1.7) * 0.5 + Math.sin(x * 2.9 + 1.3) * 0.3 + Math.sin(x * 4.3 + 2.1) * 0.2;

/** 0 = open, 1 = fully closed. A 0.2 s blink every 3.1–4.1 s depending on the seed. */
export function blink(t: number, seed: number): number {
  const period = 3.1 + seed * 0.9;
  const ph = (t + seed * 1.37) % period;
  return ph < 0.2 ? Math.sin((ph / 0.2) * Math.PI) : 0;
}

export const breathe = (t: number, seed: number) => Math.sin(t * 2.4 + seed) * 0.01;

/** Small damped bounce right after a cut. */
export const settle = (sinceCut: number) => Math.exp(-sinceCut * 9) * Math.sin(sinceCut * 22) * 0.03;

export const sway = (t: number, seed: number) => wob(t * 0.6 + seed) * 0.012;

/** Tiny pupil drift so eyes never look frozen. */
export const drift = (t: number, seed: number) => ({ x: wob(t * 0.7 + seed) * 2, y: wob(t * 0.5 + seed * 3) * 1.5 });
