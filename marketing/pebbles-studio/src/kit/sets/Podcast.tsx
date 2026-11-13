import React from 'react';

export const CHAIR = {
  mr: { base: '#BCCAB8', dark: '#A3B39F', light: '#CDD8C9' },
  mrs: { base: '#F1E6DA', dark: '#D3BFA8', light: '#F8F1E8' },
};
type ChairColors = (typeof CHAIR)['mr'];

/** Chair back + seat + floor shadow: drawn behind the character. Chair-local units, origin at the seat back. */
export const ChairBack: React.FC<{ x: number; y: number; s: number; col: ChairColors }> = ({ x, y, s, col }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <ellipse cx={0} cy={430} rx={450} ry={46} fill="rgba(110,70,35,0.22)" filter="url(#soft-16)" />
    <rect x={-340} y={-150} width={680} height={570} rx={84} fill={col.dark} />
    <rect x={-308} y={-122} width={616} height={470} rx={70} fill={col.base} />
    <rect x={-318} y={210} width={636} height={130} rx={50} fill={col.light} />
  </g>
);

/** Front of the seat and both armrests: drawn over the character's body. */
export const ChairFront: React.FC<{ x: number; y: number; s: number; col: ChairColors }> = ({ x, y, s, col }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    <rect x={-350} y={300} width={700} height={122} rx={36} fill={col.base} />
    <rect x={-350} y={380} width={700} height={42} rx={20} fill="rgba(80,60,40,0.08)" />
    {[-430, 290].map((x0) => (
      <g key={x0}>
        <rect x={x0} y={40} width={140} height={382} rx={62} fill={col.dark} />
        <rect x={x0 + 6} y={46} width={128} height={128} rx={58} fill={col.base} />
      </g>
    ))}
  </g>
);
