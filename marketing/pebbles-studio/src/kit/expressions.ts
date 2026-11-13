import type { ExpressionName, Face } from './types';

const base: Face = {
  lid: 0.15, lidTilt: 0, eyeScale: 1, pupilScale: 1, look: { x: 0, y: 4 }, brow: [0, 0], browTilt: 0,
  smile: 1, open: 0, mouthWidth: 1, mouthShape: 'curve', blush: 1, happyEyes: false,
};
const f = (o: Partial<Face>): Face => ({ ...base, ...o });

/** The expression library. smug/deadpan/content/worried/shock/smirk/sing/love come from the first video's moods. */
export const EXPRESSIONS: Record<ExpressionName, Face> = {
  neutral: f({}),
  deadpan: f({ lid: 0.52, smile: 0.12, brow: [-3, -3], browTilt: -0.35, look: { x: 0, y: 3 } }),
  smug: f({ lid: 0.46, smile: 0.75, brow: [0, 12], look: { x: 0, y: 6 } }),
  content: f({ lid: 0.42, smile: 0.35, look: { x: 3, y: 5 } }),
  happy: f({ lid: 0.08, smile: 1.2, brow: [6, 6], look: { x: 0, y: 2 } }),
  laugh: f({ lid: 1, happyEyes: true, smile: 1, open: 0.7, mouthShape: 'grin', brow: [10, 10] }),
  excited: f({ lid: 0, eyeScale: 1.12, pupilScale: 0.85, smile: 1.1, open: 0.45, brow: [16, 16], effect: 'sparkles' }),
  love: f({ lid: 1, happyEyes: true, smile: 1.25, brow: [6, 6], effect: 'hearts' }),
  smirk: f({ lid: 0.48, smile: 0.95, brow: [14, -2], look: { x: 7, y: 3 } }),
  sideEye: f({ lid: 0.5, smile: 0, brow: [-2, 8], look: { x: -16, y: 2 } }),
  eyeRoll: f({ lid: 0.3, smile: -0.25, brow: [10, 10], look: { x: 4, y: -18 } }),
  worried: f({ lid: 0.1, smile: -0.3, brow: [10, 10], browTilt: 1, look: { x: -9, y: 0 }, effect: 'sweat' }),
  shock: f({
    lid: 0, smile: -0.5, brow: [18, 18], browTilt: 0.5, eyeScale: 1.1, pupilScale: 0.7, look: { x: 0, y: 0 },
    open: 0.3, mouthWidth: 0.5, mouthShape: 'o', effect: 'pop',
  }),
  sad: f({ lid: 0.35, lidTilt: 0.5, smile: -0.6, brow: [4, 4], browTilt: 1, look: { x: 0, y: 8 }, blush: 0.7 }),
  crying: f({
    lid: 0.55, lidTilt: 0.5, smile: -0.8, brow: [6, 6], browTilt: 1.2, open: 0.15, mouthShape: 'wobble',
    look: { x: 0, y: 6 }, effect: 'tears',
  }),
  annoyed: f({ lid: 0.45, lidTilt: -0.3, smile: -0.4, brow: [-6, -6], browTilt: -1, look: { x: 0, y: 2 }, blush: 0.6, effect: 'anger' }),
  confused: f({ lid: 0.2, smile: -0.1, brow: [16, -4], mouthShape: 'wobble', look: { x: 6, y: -4 } }),
  sleepy: f({ lid: 0.82, smile: 0.1, brow: [-2, -2], look: { x: 0, y: 6 }, effect: 'zzz' }),
  sing: f({ lid: 0.3, smile: 0.8, brow: [5, 5], look: { x: -6, y: -3 } }),
};

/** A named expression, or a partial face merged over `base` (default neutral). */
export function faceFor(e: ExpressionName | Partial<Face> | undefined, baseName: ExpressionName = 'neutral'): Face {
  if (e === undefined) return { ...EXPRESSIONS[baseName] };
  if (typeof e === 'string') return { ...EXPRESSIONS[e] };
  return { ...EXPRESSIONS[baseName], ...e };
}

const lerp = (a: number, b: number, k: number) => a + (b - a) * k;

/** Numbers interpolate; discrete fields switch at the halfway point. */
export function blendFace(a: Face, b: Face, k: number): Face {
  const late = k >= 0.5;
  return {
    lid: lerp(a.lid, b.lid, k),
    lidTilt: lerp(a.lidTilt, b.lidTilt, k),
    eyeScale: lerp(a.eyeScale, b.eyeScale, k),
    pupilScale: lerp(a.pupilScale, b.pupilScale, k),
    look: { x: lerp(a.look.x, b.look.x, k), y: lerp(a.look.y, b.look.y, k) },
    brow: [lerp(a.brow[0], b.brow[0], k), lerp(a.brow[1], b.brow[1], k)],
    browTilt: lerp(a.browTilt, b.browTilt, k),
    smile: lerp(a.smile, b.smile, k),
    open: lerp(a.open, b.open, k),
    mouthWidth: lerp(a.mouthWidth, b.mouthWidth, k),
    mouthShape: late ? b.mouthShape : a.mouthShape,
    blush: lerp(a.blush, b.blush, k),
    happyEyes: late ? b.happyEyes : a.happyEyes,
    effect: late ? b.effect : a.effect,
  };
}
