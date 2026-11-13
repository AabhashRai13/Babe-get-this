import React from 'react';
import { CHARACTERS } from '../geometry';
import type { Who } from '../types';

const arc = (cx: number, cy: number, r: number, a0: number, a1: number) => {
  const p0 = [cx + r * Math.cos(a0), cy + r * Math.sin(a0)];
  const p1 = [cx + r * Math.cos(a1), cy + r * Math.sin(a1)];
  return `M ${p0[0]} ${p0[1]} A ${r} ${r} 0 0 1 ${p1[0]} ${p1[1]}`;
};

/** Over-ear headphones in the character's art units (ported from the prototype's drawPhones). */
export const Headphones: React.FC<{ who: Who }> = ({ who }) => {
  const P = CHARACTERS[who].phones;
  return (
    <g>
      <path d={arc(P.cx, P.cy, P.r, Math.PI * 1.07, Math.PI * 1.93)} stroke={P.band} strokeWidth={26} strokeLinecap="round" fill="none" />
      <path d={arc(P.cx, P.cy, P.r + 5, Math.PI * 1.22, Math.PI * 1.45)} stroke="rgba(255,255,255,0.16)" strokeWidth={6} strokeLinecap="round" fill="none" />
      {[-1, 1].map((s) => {
        const x = P.cx + s * P.r;
        const y = P.cy;
        const cupX = x + s * P.cupW * 0.12;
        return (
          <g key={s}>
            <rect x={x - s * P.cupW * 0.38 - P.cupW * 0.3} y={y - P.cupH * 0.44} width={P.cupW * 0.6} height={P.cupH * 0.88} rx={P.cupW * 0.3} fill={P.cushion} />
            <rect x={cupX - P.cupW / 2} y={y - P.cupH / 2} width={P.cupW} height={P.cupH} rx={P.cupW / 2.1} fill={P.cup} />
            <line x1={cupX + s * P.cupW * 0.22} y1={y - P.cupH * 0.28} x2={cupX + s * P.cupW * 0.22} y2={y + P.cupH * 0.05} stroke="rgba(255,255,255,0.18)" strokeWidth={5} strokeLinecap="round" />
          </g>
        );
      })}
    </g>
  );
};
