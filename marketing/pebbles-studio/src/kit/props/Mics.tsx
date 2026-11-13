import React from 'react';

/** Podcast mic with a round pop filter on a short boom (Mr's mic in the first video). Screen pixels. */
export const PopMic: React.FC<{ x: number; y: number; s: number; floorY: number }> = ({ x, y, s, floorY }) => (
  <g>
    <path d={`M ${x + 40 * s} ${floorY} L ${x + 40 * s} ${y + 130 * s} L ${x + 70 * s} ${y + 30 * s}`} stroke="#1E2428" strokeWidth={12 * s} strokeLinecap="round" strokeLinejoin="round" fill="none" />
    <ellipse cx={x + 40 * s} cy={floorY} rx={70 * s} ry={15 * s} fill="#1E2428" />
    <path d={`M ${x + 40 * s} ${floorY - 8 * s} C ${x + 110 * s} ${floorY - 20 * s} ${x + 130 * s} ${floorY + 30 * s} ${x + 200 * s} ${floorY + 18 * s}`} stroke="#1E2428" strokeWidth={5 * s} strokeLinecap="round" fill="none" />
    <rect transform={`translate(${x + 78 * s} ${y + 10 * s}) rotate(-20)`} x={-24 * s} y={-60 * s} width={48 * s} height={110 * s} rx={24 * s} fill="#2A3136" />
    <circle cx={x} cy={y} r={62 * s} fill="rgba(20,24,28,0.93)" stroke="#3A444A" strokeWidth={4 * s} />
    <path d={`M ${x + 48 * s * Math.cos(Math.PI * 1.1)} ${y + 48 * s * Math.sin(Math.PI * 1.1)} A ${48 * s} ${48 * s} 0 0 1 ${x + 48 * s * Math.cos(Math.PI * 1.45)} ${y + 48 * s * Math.sin(Math.PI * 1.45)}`} stroke="rgba(255,255,255,0.12)" strokeWidth={5 * s} strokeLinecap="round" fill="none" />
  </g>
);

/** Slim stage mic on a floor stand (Mrs's mic in the first video). Screen pixels. */
export const StandMic: React.FC<{ x: number; y: number; s: number; floorY: number }> = ({ x, y, s, floorY }) => (
  <g>
    <line x1={x} y1={floorY} x2={x} y2={y + 40 * s} stroke="#22292D" strokeWidth={11 * s} strokeLinecap="round" />
    <ellipse cx={x} cy={floorY} rx={66 * s} ry={14 * s} fill="#22292D" />
    <path d={`M ${x} ${floorY - 6 * s} C ${x - 60 * s} ${floorY - 10 * s} ${x - 90 * s} ${floorY + 30 * s} ${x - 170 * s} ${floorY + 20 * s}`} stroke="#22292D" strokeWidth={5 * s} strokeLinecap="round" fill="none" />
    <rect x={x - 22 * s} y={y - 70 * s} width={44 * s} height={118 * s} rx={22 * s} fill="#2B3236" />
    {[0, 1, 2, 3, 4].map((k) => (
      <line key={k} x1={x - 15 * s} y1={y - 52 * s + k * 10 * s} x2={x + 15 * s} y2={y - 52 * s + k * 10 * s} stroke="rgba(255,255,255,0.14)" strokeWidth={3 * s} />
    ))}
    <rect x={x - 22 * s} y={y + 4 * s} width={44 * s} height={8 * s} fill="#5A6266" />
  </g>
);
