import React from 'react';
import { CHARACTERS, CREAM } from '../geometry';
import { handAnchor } from '../poses';
import { HeldProp } from '../props/HeldProps';
import type { Body, Face as FaceT, PropName, Who } from '../types';
import { Face } from './Face';

export type PebbleLayer = 'body' | 'feet' | 'arms' | 'phones';
const ALL: PebbleLayer[] = ['body', 'feet', 'arms', 'phones'];

export interface PebbleProps {
  who: Who;
  x: number;
  y: number;
  scale: number;
  face: FaceT;
  body: Body;
  layers?: PebbleLayer[];
  t: number;
  /** Cream rim from the app icon. Off by default: it was only there to separate the icon's two characters. */
  outline?: boolean;
  /** Soft contact shadow under the feet. */
  shadow?: boolean;
  /** Mirrors body, arms, feet and held props. The face is not mirrored; point its pupils with face.look. */
  facing?: 'left' | 'right';
  /** A prop held in the hand arm (body.hand). The lute is held across the body instead. */
  holding?: PropName;
  /** Sunglasses. */
  glasses?: boolean;
  /** Optional slot rendered in the phones layer (headphones). */
  phonesSlot?: React.ReactNode;
  /** Drawn inside each arm's transform, behind the arm (e.g. drum sticks). */
  armExtra?: (i: 0 | 1) => React.ReactNode;
}

const capsule = (cx: number, cy: number, w: number, h: number, fill: string) => (
  <rect x={cx - w / 2} y={cy - h / 2} width={w} height={h} rx={w / 2} ry={w / 2} fill={fill} />
);

/** A Pebble drawn in art units, placed at (x, y) = body centre on screen. Layers let sets interleave props. */
export const Pebble: React.FC<PebbleProps> = ({
  who, x, y, scale, face, body, layers = ALL, t, outline = false, shadow = false, facing = 'right', holding, glasses = false, phonesSlot, armExtra,
}) => {
  const C = CHARACTERS[who];
  const sq = body.squash;
  const gradId = `pebble-grad-${who}`;
  const has = (l: PebbleLayer) => layers.includes(l);
  const flip = facing === 'left';
  const anchor = holding && holding !== 'lute' ? handAnchor(who, body) : null;
  const footLift = (i: number) => (body.feet === 'step' && Math.floor(t * 8) % 2 === i ? -14 : 0);
  const footX = (fx: number) => (body.feet === 'together' ? fx * 0.7 : fx);
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
      {shadow && has('feet') && <ellipse cx={0} cy={C.bottom + 34} rx={C.inner.w * 0.62} ry={22} fill="rgba(90,55,25,0.18)" />}
      <g transform={flip ? 'scale(-1 1)' : undefined}>
        <g
          transform={`translate(0 ${C.bottom}) rotate(${(body.tilt * 180) / Math.PI}) scale(${1 - sq * 0.5} ${1 + sq}) translate(0 ${-C.bottom}) translate(0 ${body.dy})`}
        >
          <defs>
            <linearGradient id={gradId} x1="0" y1={-C.inner.h / 2} x2="0" y2={C.inner.h / 2} gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor={C.grad[0]} />
              <stop offset="1" stopColor={C.grad[1]} />
            </linearGradient>
          </defs>
          {has('body') && (
            <g>
              {outline && capsule(C.outer.x, C.outer.y, C.outer.w, C.outer.h, CREAM)}
              {capsule(0, 0, C.inner.w, C.inner.h, `url(#${gradId})`)}
              <g transform={flip ? 'scale(-1 1)' : undefined}>
                <Face who={who} face={face} t={t} glasses={glasses} />
              </g>
            </g>
          )}
          {has('feet') &&
            C.feet.map((f, i) => <ellipse key={i} cx={footX(f.x)} cy={f.y + footLift(i)} rx={f.rx} ry={f.ry} fill={C.footColor} />)}
          {has('arms') && holding === 'lute' && (
            <g transform={`translate(${C.inner.w * 0.08} ${C.bottom * 0.42})`}><HeldProp name="lute" t={t} /></g>
          )}
          {has('arms') && anchor && holding !== 'bag' && (
            <g transform={`translate(${anchor.x} ${anchor.y})`}><HeldProp name={holding!} t={t} /></g>
          )}
          {has('arms') &&
            body.arms.map((a, i) => (
              <g key={i} transform={`translate(${a.x} ${a.y}) rotate(${(a.rot * 180) / Math.PI})`}>
                {armExtra?.(i as 0 | 1)}
                <ellipse cx={0} cy={C.armRy * 0.6} rx={C.armRx} ry={C.armRy} fill={C.armColor} />
              </g>
            ))}
          {has('arms') && anchor && holding === 'bag' && (
            <g transform={`translate(${anchor.x} ${anchor.y})`}><HeldProp name="bag" t={t} /></g>
          )}
          {has('phones') && phonesSlot}
        </g>
      </g>
    </g>
  );
};
