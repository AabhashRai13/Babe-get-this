// Shared types for the Pebbles kit. Names here are used across every module.

export type Who = 'mr' | 'mrs';

export type ExpressionName =
  | 'neutral' | 'deadpan' | 'smug' | 'content' | 'happy' | 'laugh' | 'excited' | 'love' | 'smirk'
  | 'sideEye' | 'eyeRoll' | 'worried' | 'shock' | 'sad' | 'crying' | 'annoyed' | 'confused' | 'sleepy' | 'sing';

export type EffectName = 'tears' | 'sweat' | 'anger' | 'sparkles' | 'zzz' | 'hearts' | 'pop';
export type MouthShape = 'curve' | 'o' | 'grin' | 'wobble';

export interface Face {
  lid: number; // 0 open .. 1 closed
  lidTilt: number; // + = outer corner lower
  eyeScale: number;
  pupilScale: number;
  look: { x: number; y: number }; // pupil offset, art units
  brow: [number, number]; // per-brow lift, art units (+ = up)
  browTilt: number; // + = inner ends up (worried)
  smile: number; // -1 frown .. 1 smile
  open: number; // 0 closed .. 1 wide
  mouthWidth: number;
  mouthShape: MouthShape;
  blush: number; // 0..1 opacity
  happyEyes: boolean; // closed eyes drawn as ^ arcs
  effect?: EffectName;
  /** Rounded lips ("oo"), 0..1. Set per frame by lip sync. */
  round?: number;
  /** Face features slid inside the body (art units), so a lean reads as the head turning. */
  shift?: { x: number; y: number };
}

export type PoseName =
  | 'sit' | 'stand' | 'walkIn' | 'point' | 'wave' | 'shrug' | 'armsCrossed' | 'handsOnHips'
  | 'holdPhone' | 'holdBag' | 'holdList' | 'drum' | 'hug' | 'asleep';

export interface Arm { x: number; y: number; rot: number } // shoulder (art units) + radians
export interface Body {
  arms: [Arm, Arm];
  tilt: number;
  squash: number;
  dy: number;
  feet: 'together' | 'apart' | 'step';
  hand: 0 | 1;
  /** Drag (radians): headphones and the lute trail the body's rotation by this much, then catch up. */
  lag?: number;
  /** Lute kick from a strum (degrees). */
  recoil?: number;
}

export const PROP_NAMES = ['phone', 'bag', 'list', 'mug', 'sticks', 'lute'] as const;
export type PropName = (typeof PROP_NAMES)[number];
export type SetName = 'podcast' | 'band' | 'kitchen' | 'couch' | 'aisle' | 'stage';
export type FrameName = 'single-mr' | 'single-mrs' | 'two-shot' | 'wide' | 'split' | 'split-close' | 'close-mr' | 'close-mrs';

/** line/verse: sentence captions. pop/popYellow: one big shouted word. label: small word. hanzi: "字|pinyin". */
export type CaptionStyle = 'line' | 'verse' | 'pop' | 'popYellow' | 'label' | 'hanzi';
export interface CharCaption { text: string; style: CaptionStyle }
export type Look = 'camera' | 'left' | 'right' | 'other' | { x: number; y: number };

export interface CastState {
  expression?: ExpressionName | Partial<Face>;
  pose?: PoseName;
  /** Where the pupils point. Cleared (`null`, or any new expression without a look) back to the expression's own. */
  look?: Look | null;
  /** A held prop; `null` drops it (undefined would be lost when Remotion serialises props). */
  holding?: PropName | null;
  headphones?: boolean;
  /** Sunglasses. Carries over until set false. */
  glasses?: boolean;
  facing?: 'left' | 'right';
}

/** A change inside a shot, without a cut. `speaker: null` means nobody is talking. */
export interface Beat {
  at: number; mr?: CastState; mrs?: CastState; caption?: string; captionStyle?: CaptionStyle; speaker?: Who | null;
  /** Captions above one character (split and close shots). null clears it. */
  captions?: Partial<Record<Who, CharCaption | null>>;
}

export interface Shot {
  at: number;
  set: SetName;
  frame: FrameName;
  speaker?: Who;
  caption?: string;
  captionStyle?: CaptionStyle;
  captions?: Partial<Record<Who, CharCaption | null>>;
  drums?: boolean;
  mr?: CastState;
  mrs?: CastState;
  beats?: Beat[];
}

export interface DrumGrid { start: number; beat: number; end: number }

export interface VideoScript {
  slug: string;
  audio?: string;
  end: number;
  endCard?: { subtitle: string };
  drumGrid?: DrumGrid;
  shots: Shot[];
}

export interface Rect { x: number; y: number; w: number; h: number }
