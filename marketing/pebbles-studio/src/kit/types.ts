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
}

export type PoseName =
  | 'sit' | 'stand' | 'walkIn' | 'point' | 'wave' | 'shrug' | 'armsCrossed' | 'handsOnHips'
  | 'holdPhone' | 'holdBag' | 'holdList' | 'drum' | 'hug';

export interface Arm { x: number; y: number; rot: number } // shoulder (art units) + radians
export interface Body {
  arms: [Arm, Arm];
  tilt: number;
  squash: number;
  dy: number;
  feet: 'together' | 'apart' | 'step';
  hand: 0 | 1;
}

export type PropName = 'phone' | 'bag' | 'list' | 'mug' | 'sticks';
export type SetName = 'podcast' | 'band' | 'kitchen' | 'couch' | 'aisle';
export type FrameName = 'single-mr' | 'single-mrs' | 'two-shot' | 'wide';
export type Look = 'camera' | 'left' | 'right' | 'other' | { x: number; y: number };

export interface CastState {
  expression?: ExpressionName | Partial<Face>;
  pose?: PoseName;
  look?: Look;
  holding?: PropName;
  headphones?: boolean;
  facing?: 'left' | 'right';
}

export interface Beat { at: number; mr?: CastState; mrs?: CastState; caption?: string }

export interface Shot {
  at: number;
  set: SetName;
  frame: FrameName;
  speaker?: Who;
  caption?: string;
  captionStyle?: 'line' | 'verse';
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
