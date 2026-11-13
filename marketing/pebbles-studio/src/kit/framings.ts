import { CHARACTERS } from './geometry';
import type { FrameName, Rect, SetName, Who } from './types';

export const W = 1080;
export const H = 1920;

/** Instagram/TikTok UI zones measured for the first video: keep faces and captions out of them. */
export const SAFE = { top: 270, bottom: 420, right: { w: 140, y0: 900, y1: 1500 } };

export function isSafe(r: Rect): boolean {
  if (r.x < 0 || r.x + r.w > W) return false;
  if (r.y < SAFE.top || r.y + r.h > H - SAFE.bottom) return false;
  const inRightBand = r.x + r.w > W - SAFE.right.w && r.y < SAFE.right.y1 && r.y + r.h > SAFE.right.y0;
  return !inRightBand;
}

export interface Placement { x: number; y: number; scale: number }
export interface Framing {
  place: Partial<Record<Who, Placement>>;
  faces: Partial<Record<Who, Rect>>;
  caption: { y: number; maxW: number };
}

/** The head-and-mouth box of a placed character, in screen pixels. */
export function faceBox(who: Who, p: Placement): Rect {
  const C = CHARACTERS[who];
  const halfW = (C.inner.w / 2 + 20) * p.scale;
  const top = p.y - (C.inner.h / 2) * p.scale;
  const bottom = p.y + (C.mouth.y + C.mouth.sag + 30) * p.scale;
  return { x: p.x - halfW, y: top, w: halfW * 2, h: bottom - top };
}

const framing = (place: Partial<Record<Who, Placement>>, caption: { y: number; maxW: number }): Framing => ({
  place,
  faces: Object.fromEntries(Object.entries(place).map(([w, p]) => [w, faceBox(w as Who, p!)])) as Framing['faces'],
  caption,
});

/** Where characters stand and captions sit, per set and frame. Every entry is checked by test/framings.test.ts. */
export const FRAMINGS: Partial<Record<SetName, Partial<Record<FrameName, Framing>>>> = {
  podcast: {
    'single-mr': framing({ mr: { x: 540, y: 1054, scale: 1.02 } }, { y: 510, maxW: 900 }),
    'single-mrs': framing({ mrs: { x: 540, y: 1081, scale: 1.12 } }, { y: 510, maxW: 900 }),
    'two-shot': framing({ mr: { x: 300, y: 1180, scale: 0.62 }, mrs: { x: 770, y: 1196, scale: 0.68 } }, { y: 560, maxW: 900 }),
  },
  band: {
    // The prototype's band layout (art at 1080x1920) drawn through translate(0,-275) scale(0.86).
    'two-shot': framing({ mr: { x: 284, y: 843, scale: 0.533 }, mrs: { x: 688, y: 1200, scale: 1.049 } }, { y: 330, maxW: 640 }),
  },
};

export function framingFor(set: SetName, frame: FrameName): Framing {
  const f = FRAMINGS[set]?.[frame];
  if (!f) throw new Error(`No framing "${frame}" in set "${set}"`);
  return f;
}

const GLYPH = 0.52; // average glyph width / font size for the caption font

function wrap(text: string, maxW: number, fs: number): string[] {
  const maxChars = Math.max(1, Math.floor(maxW / (GLYPH * fs)));
  const lines: string[] = [];
  let cur = '';
  for (const w of text.split(/\s+/).filter(Boolean)) {
    const next = cur ? `${cur} ${w}` : w;
    if (next.length > maxChars && cur) { lines.push(cur); cur = w; } else cur = next;
  }
  if (cur) lines.push(cur);
  return lines;
}

/** Wraps a caption for a framing, stepping the size down (min 40) until the box is inside the safe area. */
export function captionLayout(text: string, style: 'line' | 'verse', f: Framing): { fontSize: number; lines: string[]; box: Rect } {
  let out = { fontSize: 0, lines: [] as string[], box: { x: 0, y: 0, w: 0, h: 0 } };
  for (let fs = style === 'verse' ? 54 : 52; fs >= 40; fs -= 4) {
    const lines = wrap(text, f.caption.maxW, fs);
    const w = Math.max(...lines.map((l) => l.length)) * GLYPH * fs;
    const box = { x: W / 2 - w / 2, y: f.caption.y, w, h: lines.length * fs * 1.18 };
    out = { fontSize: fs, lines, box };
    if (isSafe(box)) break;
  }
  return out;
}
