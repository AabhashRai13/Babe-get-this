import React from 'react';
import { CHARACTERS, CREAM, LINE } from '../geometry';
import type { EffectName, Who } from '../types';

const TEAR = '#8FC3E6';
const ANGER = '#D9534F';
const SPARK = '#F2C14E';

export const HeartPath: React.FC<{ x: number; y: number; size: number; fill: string; opacity?: number }> = ({ x, y, size, fill, opacity = 1 }) => {
  const s = size / 100;
  return (
    <path
      transform={`translate(${x} ${y}) scale(${s})`}
      d="M 0 30 C -60 -10 -40 -70 0 -38 C 40 -70 60 -10 0 30 Z"
      fill={fill}
      opacity={opacity}
    />
  );
};

const Star: React.FC<{ x: number; y: number; r: number; fill: string }> = ({ x, y, r, fill }) => (
  <path
    d={`M ${x} ${y - r} Q ${x} ${y} ${x + r} ${y} Q ${x} ${y} ${x} ${y + r} Q ${x} ${y} ${x - r} ${y} Q ${x} ${y} ${x} ${y - r} Z`}
    fill={fill}
  />
);

/** Little cartoon extras next to the face, in the character's art units. Gently animated by t. */
export const Effects: React.FC<{ who: Who; effect: EffectName; t: number }> = ({ who, effect, t }) => {
  const C = CHARACTERS[who];
  const top = C.outer.y - C.outer.h / 2;
  const right = C.outer.x + C.outer.w / 2;
  const left = C.outer.x - C.outer.w / 2;
  const bob = Math.sin(t * 5) * 4;

  switch (effect) {
    case 'tears': {
      const fall = (t * 120) % 90;
      return (
        <g>
          {C.eyes.map((e, i) => (
            <g key={i}>
              <path d={`M ${e.x - C.eyeRx * 0.5} ${e.y + C.eyeRy * 0.6} q -6 30 0 50`} stroke={TEAR} strokeWidth={10} strokeLinecap="round" fill="none" opacity={0.9} />
              <ellipse cx={e.x - C.eyeRx * 0.5} cy={e.y + C.eyeRy * 0.8 + fall} rx={7} ry={10} fill={TEAR} opacity={1 - fall / 90} />
            </g>
          ))}
        </g>
      );
    }
    case 'sweat':
      return (
        <path
          transform={`translate(${right - 30} ${top + C.outer.h * 0.22 + bob})`}
          d="M 0 -26 C 14 -4 18 6 18 14 A 18 18 0 0 1 -18 14 C -18 6 -14 -4 0 -26 Z"
          fill={TEAR}
          stroke="#fff"
          strokeWidth={3}
        />
      );
    case 'anger': {
      const s = 1 + Math.sin(t * 8) * 0.08;
      return (
        <g transform={`translate(${right - 45} ${top + 70}) scale(${s})`} stroke={ANGER} strokeWidth={9} strokeLinecap="round" fill="none">
          <path d="M -22 -6 Q -8 -8 -6 -22" />
          <path d="M 6 -22 Q 8 -8 22 -6" />
          <path d="M 22 6 Q 8 8 6 22" />
          <path d="M -6 22 Q -8 8 -22 6" />
        </g>
      );
    }
    case 'sparkles':
      return (
        <g>
          {[[left + 10, top + 60, 22], [right - 5, top + 40, 28], [right + 30, top + 150, 16], [left - 25, top + 170, 14]].map(([x, y, r], i) => (
            <Star key={i} x={x} y={y + Math.sin(t * 6 + i) * 6} r={r * (0.8 + 0.2 * Math.sin(t * 9 + i))} fill={SPARK} />
          ))}
        </g>
      );
    case 'zzz':
      return (
        <g fontFamily="Figtree, sans-serif" fontWeight={800} fill={LINE}>
          {[0, 1, 2].map((i) => {
            const ph = (t * 0.6 + i / 3) % 1;
            return (
              <text key={i} x={right - 30 + ph * 80} y={top + 40 - ph * 140} fontSize={64 + i * 18} opacity={1 - ph}>
                z
              </text>
            );
          })}
        </g>
      );
    case 'hearts':
      return (
        <g>
          {[0, 1, 2].map((i) => {
            const ph = (t * 0.5 + i / 3) % 1;
            return <HeartPath key={i} x={(i - 1) * 90 + Math.sin(t * 3 + i) * 10} y={top - 20 - ph * 120} size={80 + i * 14} fill={i % 2 ? '#E99A99' : CREAM} opacity={Math.min(1, (1 - ph) * 1.6)} />;
          })}
        </g>
      );
    case 'pop':
      return (
        <g transform={`translate(${right - 10} ${top + 10 + bob})`}>
          <text x={0} y={0} fontFamily="Figtree, sans-serif" fontWeight={900} fontSize={110} fill={LINE} textAnchor="middle" stroke="#fff" strokeWidth={6} paintOrder="stroke">
            !
          </text>
        </g>
      );
  }
};
