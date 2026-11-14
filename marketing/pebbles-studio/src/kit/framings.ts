import { CHARACTERS } from './geometry';
import type { CaptionStyle, FrameName, Rect, SetName, Who } from './types';

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
  /** Caption slots above each character (split shots). */
  sub?: Partial<Record<Who, { y: number; maxW: number }>>;
  /** Split shots: each half shows a window of that character's single shot, scaled by k, from source y0 to screen y top. */
  windows?: Partial<Record<Who, SplitWindow>>;
}
export interface SplitWindow { y0: number; k: number; top: number; height: number }

/** The head-and-mouth box of a placed character, in screen pixels. */
export function faceBox(who: Who, p: Placement): Rect {
  const C = CHARACTERS[who];
  const halfW = (C.inner.w / 2 + 20) * p.scale;
  const top = p.y - (C.inner.h / 2) * p.scale;
  const bottom = p.y + (C.mouth.y + C.mouth.sag + 30) * p.scale;
  return { x: p.x - halfW, y: top, w: halfW * 2, h: bottom - top };
}

const framing = (place: Partial<Record<Who, Placement>>, caption: { y: number; maxW: number }, extra: Partial<Framing> = {}): Framing => ({
  place,
  faces: Object.fromEntries(Object.entries(place).map(([w, p]) => [w, faceBox(w as Who, p!)])) as Framing['faces'],
  caption,
  ...extra,
});

const SINGLE: Record<Who, Placement> = { mr: { x: 540, y: 1054, scale: 1.02 }, mrs: { x: 540, y: 1081, scale: 1.12 } };
/** Where a character from its podcast single shot lands inside a split-screen window. */
export const throughWindow = (p: Placement, w: SplitWindow): Placement => ({ x: W / 2 + (p.x - W / 2) * w.k, y: w.top + (p.y - w.y0) * w.k, scale: p.scale * w.k });
const split = (windows: Record<Who, SplitWindow>, sub: Record<Who, { y: number; maxW: number }>) =>
  framing({ mr: throughWindow(SINGLE.mr, windows.mr), mrs: throughWindow(SINGLE.mrs, windows.mrs) }, { y: sub.mr.y, maxW: sub.mr.maxW }, { windows, sub });
const HALF = 615; // each half of a split: 270..885 and 885..1500, between the app UI zones

/** Where characters stand and captions sit, per set and frame. Every entry is checked by test/framings.test.ts. */
export const FRAMINGS: Partial<Record<SetName, Partial<Record<FrameName, Framing>>>> = {
  podcast: {
    'single-mr': framing({ mr: { x: 540, y: 1054, scale: 1.02 } }, { y: 510, maxW: 900 }),
    'single-mrs': framing({ mrs: { x: 540, y: 1081, scale: 1.12 } }, { y: 510, maxW: 900 }),
    'two-shot': framing({ mr: { x: 300, y: 1180, scale: 0.62 }, mrs: { x: 770, y: 1196, scale: 0.68 } }, { y: 560, maxW: 900 }),
    'close-mr': framing({ mr: { x: 547, y: 1175, scale: 1.75 } }, { y: 390, maxW: 900 }),
    'close-mrs': framing({ mrs: { x: 540, y: 1205, scale: 2.1 } }, { y: 420, maxW: 900 }),
    split: split(
      { mr: { y0: 640, k: 0.93, top: 270, height: HALF }, mrs: { y0: 640, k: 0.93, top: 885, height: HALF } },
      { mr: { y: 285, maxW: 800 }, mrs: { y: 900, maxW: 800 } },
    ),
    'split-close': split(
      { mr: { y0: 640, k: 1.2, top: 270, height: HALF }, mrs: { y0: 694, k: 1.2, top: 885, height: HALF } },
      { mr: { y: 280, maxW: 800 }, mrs: { y: 893, maxW: 800 } },
    ),
  },
  // Dark concert stage: the singer (Mrs) alone, close, or the band with Mr on drums.
  stage: {
    'single-mrs': framing({ mrs: { x: 540, y: 1131, scale: 1.1 } }, { y: 510, maxW: 900 }),
    'close-mrs': framing({ mrs: { x: 540, y: 1205, scale: 2.1 } }, { y: 420, maxW: 900 }),
    'two-shot': framing({ mr: { x: 284, y: 843, scale: 0.533 }, mrs: { x: 688, y: 1200, scale: 1.049 } }, { y: 330, maxW: 640 }),
  },
  // Standing sets: feet on a floor line at y≈1440, just above the bottom UI zone.
  kitchen: {
    'single-mr': framing({ mr: { x: 540, y: 1103, scale: 1.0 } }, { y: 510, maxW: 900 }),
    'single-mrs': framing({ mrs: { x: 540, y: 1131, scale: 1.1 } }, { y: 510, maxW: 900 }),
    'two-shot': framing({ mr: { x: 330, y: 1197, scale: 0.72 }, mrs: { x: 760, y: 1221, scale: 0.78 } }, { y: 560, maxW: 900 }),
  },
  couch: {
    'single-mr': framing({ mr: { x: 540, y: 1100, scale: 1.0 } }, { y: 510, maxW: 900 }),
    'single-mrs': framing({ mrs: { x: 540, y: 1125, scale: 1.1 } }, { y: 510, maxW: 900 }),
    'two-shot': framing({ mr: { x: 350, y: 1180, scale: 0.7 }, mrs: { x: 740, y: 1196, scale: 0.76 } }, { y: 560, maxW: 900 }),
  },
  aisle: {
    'single-mr': framing({ mr: { x: 540, y: 1103, scale: 1.0 } }, { y: 510, maxW: 900 }),
    'single-mrs': framing({ mrs: { x: 540, y: 1131, scale: 1.1 } }, { y: 510, maxW: 900 }),
    'two-shot': framing({ mr: { x: 330, y: 1197, scale: 0.72 }, mrs: { x: 760, y: 1221, scale: 0.78 } }, { y: 560, maxW: 900 }),
    wide: framing({ mr: { x: 290, y: 1255, scale: 0.55 }, mrs: { x: 540, y: 1271, scale: 0.6 } }, { y: 560, maxW: 900 }),
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

const STYLES: Record<CaptionStyle, { size: number; min: number; glyph: number; lh: number }> = {
  line: { size: 52, min: 40, glyph: 0.52, lh: 1.18 },
  verse: { size: 54, min: 40, glyph: 0.52, lh: 1.18 },
  pop: { size: 110, min: 64, glyph: 0.47, lh: 1.04 },
  popYellow: { size: 110, min: 64, glyph: 0.47, lh: 1.04 },
  label: { size: 46, min: 34, glyph: 0.56, lh: 1.18 },
  hanzi: { size: 84, min: 52, glyph: 1.0, lh: 1.0 },
};

function wrap(text: string, maxW: number, fs: number, glyph: number): string[] {
  const maxChars = Math.max(1, Math.floor(maxW / (glyph * fs)));
  const lines: string[] = [];
  let cur = '';
  for (const w of text.split(/\s+/).filter(Boolean)) {
    const next = cur ? `${cur} ${w}` : w;
    if (next.length > maxChars && cur) { lines.push(cur); cur = w; } else cur = next;
  }
  if (cur) lines.push(cur);
  return lines;
}

export const overlaps = (a: Rect, b: Rect) => a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;

export interface CaptionBox { fontSize: number; lines: string[]; box: Rect; fits: boolean; pinyin?: string }

/**
 * Lays out a caption for a framing (or for one character's slot in a split), stepping the size down until
 * the box is inside the safe area and clear of every face. `fits` is false when even the minimum size can't
 * manage it (validateScript rejects that). hanzi text is "字|pinyin": characters, with pinyin underneath.
 */
export function captionLayout(text: string, style: CaptionStyle, f: Framing, who?: Who): CaptionBox {
  const st = STYLES[style];
  const slot = (who && f.sub?.[who]) || f.caption;
  let out: CaptionBox = { fontSize: 0, lines: [], box: { x: 0, y: 0, w: 0, h: 0 }, fits: false };
  // Main hanzi captions (one character speaking alone) go big; in a split slot they stay compact.
  const base = style === 'hanzi' && !(who && f.sub?.[who]) ? 140 : st.size;
  for (let fs = base; fs >= st.min; fs -= 4) {
    let lines: string[];
    let w: number;
    let h: number;
    let pinyin: string | undefined;
    if (style === 'hanzi') {
      const [chars, py = ''] = text.split('|');
      lines = [chars];
      pinyin = py;
      w = Math.max(chars.length * fs, py.length * fs * 0.4 * 0.55);
      h = fs * 1.05 + fs * 0.42;
    } else {
      const t = style === 'pop' || style === 'popYellow' ? text.toUpperCase() : text;
      lines = wrap(t, slot.maxW, fs, st.glyph);
      w = Math.max(...lines.map((l) => l.length)) * st.glyph * fs;
      h = lines.length * fs * st.lh;
    }
    const box = { x: W / 2 - w / 2, y: slot.y, w, h };
    const fits = isSafe(box) && Object.values(f.faces).every((face) => !overlaps(box, face!));
    out = { fontSize: fs, lines, box, fits, pinyin };
    if (fits) break;
  }
  return out;
}
