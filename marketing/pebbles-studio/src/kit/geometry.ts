// Mr & Mrs Pebbles geometry in "art units" (the icon's 1024px space), origin at the body centre.
// Numbers are taken from public/icon.svg via the prototype (docs/superpowers/specs/2026-11-13-pebbles-prototype/scene.js).
// Mrs Pebbles is tilted ~8 degrees in the icon; toMrsLocal() maps icon coordinates into her upright frame.

type P = { x: number; y: number };
type Pt = [number, number];
export type Cubic = [Pt, Pt, Pt, Pt];

export interface CharacterDef {
  outer: { x: number; y: number; w: number; h: number };
  inner: { w: number; h: number };
  grad: [string, string];
  eyes: [P, P];
  eyeRx: number;
  eyeRy: number;
  pupilR: number;
  hlR: number;
  hlOff: P;
  blush: [P, P];
  blushRx: number;
  blushRy: number;
  brows: [Cubic, Cubic];
  browW: number;
  mouth: { x: number; y: number; hw: number; sag: number; w: number };
  feet: [{ x: number; y: number; rx: number; ry: number }, { x: number; y: number; rx: number; ry: number }];
  footColor: string;
  armColor: string;
  armRx: number;
  armRy: number;
  lashes: boolean;
  lidW: number;
  phones: { cx: number; cy: number; r: number; cupW: number; cupH: number; band: string; cup: string; cushion: string };
  bottom: number;
}

export const CREAM = '#F5F2EB';
export const LINE = '#263A44';
export const PUPIL = '#071923';
export const BLUSH = '#E99A99';

const A = 0.1393;
const CA = Math.cos(A);
const SA = Math.sin(A);
const WC = { x: 685.4, y: 487.5 };

export function toMrsLocal(x: number, y: number): P {
  const dx = x - WC.x;
  const dy = y - WC.y;
  return { x: dx * CA - dy * SA, y: dx * SA + dy * CA };
}
const rotV = (x: number, y: number): P => ({ x: x * CA - y * SA, y: x * SA + y * CA });
const Lp = (x: number, y: number): Pt => {
  const p = toMrsLocal(x, y);
  return [p.x, p.y];
};
const q2c = (p0: Pt, q: Pt, p2: Pt): Cubic => [
  p0,
  [p0[0] + (2 / 3) * (q[0] - p0[0]), p0[1] + (2 / 3) * (q[1] - p0[1])],
  [p2[0] + (2 / 3) * (q[0] - p2[0]), p2[1] + (2 / 3) * (q[1] - p2[1])],
  p2,
];

export const MR: CharacterDef = {
  outer: { x: -4.3, y: -8.1, w: 450.3, h: 647.6 },
  inner: { w: 403.3, h: 600.5 },
  grad: ['#6E8996', '#4C6673'],
  eyes: [{ x: -69.6, y: -99.5 }, { x: 61.5, y: -99.5 }],
  eyeRx: 54.9,
  eyeRy: 56.6,
  pupilR: 23.2,
  hlR: 8.9,
  hlOff: { x: 8.7, y: -8.7 },
  blush: [{ x: -88.3, y: -37.2 }, { x: 71.7, y: -39.5 }],
  blushRx: 28.6,
  blushRy: 16.5,
  brows: [
    [[-121.4, -191.6], [-88.6, -211.6], [-51.4, -211.6], [-21.4, -193.8]],
    [[12.9, -193.8], [42.9, -211.6], [82.9, -209.4], [107.2, -191.6]],
  ],
  browW: 15.5,
  mouth: { x: -2, y: -7.7, hw: 40.5, sag: 22, w: 15.5 },
  feet: [{ x: -99.6, y: 300, rx: 70.5, ry: 37.3 }, { x: 115.1, y: 300, rx: 70.5, ry: 37.3 }],
  footColor: '#415967',
  armColor: '#607D8B',
  armRx: 40,
  armRy: 78,
  lashes: false,
  lidW: 7,
  phones: { cx: -4, cy: -95, r: 236, cupW: 70, cupH: 156, band: '#2C3A41', cup: '#2F3D44', cushion: '#4B5B63' },
  bottom: 300,
};

export const MRS: CharacterDef = {
  outer: { ...toMrsLocal(688.4, 491.3), w: 360.2, h: 544.6 },
  inner: { w: 317.2, h: 497.8 },
  grad: ['#A7B8C0', '#80959F'],
  eyes: [toMrsLocal(613.85, 427.8), toMrsLocal(717.2, 413.2)],
  eyeRx: 43.3,
  eyeRy: 43.3,
  pupilR: 18.4,
  hlR: 7,
  hlOff: rotV(4.95, -9),
  blush: [toMrsLocal(598.1, 476.5), toMrsLocal(747.2, 455.0)],
  blushRx: 31.4,
  blushRy: 17.6,
  brows: [
    q2c(Lp(565.2, 369.8), Lp(601.35, 333.4), Lp(642.1, 356.6)),
    q2c(Lp(668.6, 352.8), Lp(700.8, 318.15), Lp(741.8, 344.9)),
  ],
  browW: 11,
  mouth: { x: -10.7, y: 9.65, hw: 37.5, sag: 14.2, w: 11 },
  feet: [{ x: -78, y: 249, rx: 60, ry: 32 }, { x: 60, y: 249, rx: 60, ry: 32 }],
  footColor: '#728992',
  armColor: '#90A4AE',
  armRx: 34,
  armRy: 64,
  lashes: true,
  lidW: 6,
  phones: { cx: -6, cy: -62, r: 192, cupW: 58, cupH: 126, band: '#4E7772', cup: '#5F8B85', cushion: '#4A6F6A' },
  bottom: 249,
};

export const CHARACTERS = { mr: MR, mrs: MRS } as const;
