import React from 'react';
import { CREAM, LINE } from '../geometry';
import type { PropName } from '../types';

const TEAL = '#5F8B85';
const ROW = '#A7B7BE';

/** The phone shows a simplified Babe, Get This list: header, three rows, the first one ticked. */
export const Phone: React.FC = () => (
  <g transform="translate(0 -30) rotate(-8)">
    <rect x={-40} y={-75} width={80} height={150} rx={16} fill={LINE} />
    <rect x={-33} y={-62} width={66} height={124} rx={9} fill={CREAM} />
    <rect x={-33} y={-62} width={66} height={22} rx={9} fill="#6E8996" />
    {[0, 1, 2].map((i) => (
      <g key={i} transform={`translate(0 ${-22 + i * 26})`}>
        <circle cx={-20} cy={0} r={7} fill={i === 0 ? TEAL : 'none'} stroke={i === 0 ? TEAL : ROW} strokeWidth={3} />
        {i === 0 && <path d="M -23.5 0 L -20.5 3 L -16 -3" stroke="#fff" strokeWidth={2.5} fill="none" strokeLinecap="round" />}
        <rect x={-8} y={-3} width={34} height={6} rx={3} fill={ROW} />
      </g>
    ))}
  </g>
);

export const Bag: React.FC = () => (
  <g>
    <path d="M -34 22 Q -34 -14 -6 -14 M 34 22 Q 34 -14 6 -14" stroke="#9C7650" strokeWidth={7} fill="none" strokeLinecap="round" />
    <rect x={-22} y={8} width={14} height={60} rx={7} fill="#D9A86C" transform="rotate(-12 -15 38)" />
    <path d="M 10 18 q 10 -40 26 -10 q -16 -6 -18 20 Z" fill="#7FA36B" />
    <path d="M -60 22 H 60 L 54 150 Q 54 160 44 160 H -44 Q -54 160 -54 150 Z" fill="#C9A27A" />
    <path d="M -60 22 H 60 L 59 36 H -59 Z" fill="#B48C64" />
  </g>
);

export const ListPaper: React.FC = () => (
  <g transform="translate(0 -40) rotate(-6)">
    <rect x={-48} y={-62} width={96} height={128} rx={8} fill="#FBF7EF" stroke="#E2D6C3" strokeWidth={3} />
    {[0, 1, 2, 3].map((i) => (
      <g key={i} transform={`translate(0 ${-36 + i * 26})`}>
        <rect x={-26} y={-3} width={58} height={6} rx={3} fill={ROW} />
        {i === 0 && <path d="M -40 0 l 5 5 l 8 -10" stroke={TEAL} strokeWidth={4} fill="none" strokeLinecap="round" />}
      </g>
    ))}
  </g>
);

export const Mug: React.FC<{ t: number }> = ({ t }) => (
  <g transform="translate(0 -20)">
    {[0, 1].map((i) => (
      <path key={i} d={`M ${-10 + i * 16} -40 q 8 -12 0 -24 q -8 -12 0 -24`} stroke="rgba(255,255,255,0.7)" strokeWidth={5} fill="none" strokeLinecap="round"
        opacity={0.5 + 0.5 * Math.sin(t * 3 + i)} />
    ))}
    <path d="M 30 -16 q 26 0 26 20 q 0 20 -26 20" stroke="#E7C9A6" strokeWidth={9} fill="none" />
    <rect x={-32} y={-34} width={64} height={70} rx={12} fill="#F1E2CC" />
    <path d="M -10 0 C -22 -12 -14 -24 0 -14 C 14 -24 22 -12 10 0 L 0 10 Z" fill="#E99A99" />
  </g>
);

/** A pipa-style lute held across the body (concert set). Drawn at the body, not the hand. */
export const Lute: React.FC = () => (
  <g transform="rotate(-32)">
    <rect x={-14} y={-300} width={28} height={200} rx={8} fill="#8A5A34" />
    <rect x={-22} y={-340} width={44} height={56} rx={12} fill="#6E4428" />
    {[-1, 1].map((s) => [0, 1].map((k) => <circle key={`${s}${k}`} cx={s * 30} cy={-328 + k * 26} r={8} fill="#E9D5B5" />))}
    <path d="M 0 -120 C 95 -110 110 40 70 105 C 40 150 -40 150 -70 105 C -110 40 -95 -110 0 -120 Z" fill="#C68A4E" />
    <path d="M 0 -95 C 75 -85 88 35 55 90 C 30 125 -30 125 -55 90 C -88 35 -75 -85 0 -95 Z" fill="#D9A066" />
    <circle cx={0} cy={-10} r={16} fill="#6E4428" />
    <rect x={-34} y={70} width={68} height={14} rx={6} fill="#6E4428" />
    {[-9, -3, 3, 9].map((x) => <line key={x} x1={x} y1={-330} x2={x} y2={78} stroke="#F4EADC" strokeWidth={2} />)}
  </g>
);

const LooseStick: React.FC = () => <line x1={0} y1={-10} x2={0} y2={170} stroke="#B98D5E" strokeWidth={13} strokeLinecap="round" />;

/** A held prop, drawn with its grip point at the origin (the hand). */
export const HeldProp: React.FC<{ name: PropName; t: number }> = ({ name, t }) => (
  <g data-prop={name}>
    {name === 'phone' && <Phone />}
    {name === 'bag' && <Bag />}
    {name === 'list' && <ListPaper />}
    {name === 'mug' && <Mug t={t} />}
    {name === 'sticks' && <LooseStick />}
    {name === 'lute' && <Lute />}
  </g>
);
