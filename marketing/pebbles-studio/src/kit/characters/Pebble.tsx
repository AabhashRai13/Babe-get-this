import React from 'react';
import { CHARACTERS, CREAM } from '../geometry';
import type { Body, Face as FaceT, Who } from '../types';
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
  /** Optional slot rendered in the phones layer (headphones). */
  phonesSlot?: React.ReactNode;
  /** Optional slot rendered after the arms (held props). */
  handSlot?: React.ReactNode;
}

const capsule = (cx: number, cy: number, w: number, h: number, fill: string) => (
  <rect x={cx - w / 2} y={cy - h / 2} width={w} height={h} rx={w / 2} ry={w / 2} fill={fill} />
);

/** A Pebble drawn in art units, placed at (x, y) = body centre on screen. Layers let sets interleave props. */
export const Pebble: React.FC<PebbleProps> = ({ who, x, y, scale, face, body, layers = ALL, t, outline = false, shadow = false, phonesSlot, handSlot }) => {
  const C = CHARACTERS[who];
  const sq = body.squash;
  const gradId = `pebble-grad-${who}`;
  const has = (l: PebbleLayer) => layers.includes(l);
  return (
    <g transform={`translate(${x} ${y}) scale(${scale})`}>
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
            {shadow && <ellipse cx={0} cy={C.bottom + 34} rx={C.inner.w * 0.62} ry={22} fill="rgba(90,55,25,0.18)" />}
            {outline && capsule(C.outer.x, C.outer.y, C.outer.w, C.outer.h, CREAM)}
            {capsule(0, 0, C.inner.w, C.inner.h, `url(#${gradId})`)}
            <Face who={who} face={face} t={t} />
          </g>
        )}
        {has('feet') &&
          C.feet.map((f, i) => <ellipse key={i} cx={f.x} cy={f.y} rx={f.rx} ry={f.ry} fill={C.footColor} />)}
        {has('arms') &&
          body.arms.map((a, i) => (
            <g key={i} transform={`translate(${a.x} ${a.y}) rotate(${(a.rot * 180) / Math.PI})`}>
              <ellipse cx={0} cy={C.armRy * 0.6} rx={C.armRx} ry={C.armRy} fill={C.armColor} />
            </g>
          ))}
        {has('arms') && handSlot}
        {has('phones') && phonesSlot}
      </g>
    </g>
  );
};
