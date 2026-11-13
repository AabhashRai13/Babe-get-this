import React from 'react';
import type { Hit } from '../drums';
import { pulse } from '../drums';
import { HeartPath } from './Effects';

const Drum: React.FC<{ x: number; y: number; w: number; h: number; shell: string; head: string }> = ({ x, y, w, h, shell, head }) => (
  <g>
    <path d={`M ${x - w / 2} ${y} H ${x + w / 2} V ${y + h - 14} Q ${x + w / 2} ${y + h} ${x + w / 2 - 14} ${y + h} H ${x - w / 2 + 14} Q ${x - w / 2} ${y + h} ${x - w / 2} ${y + h - 14} Z`} fill={shell} />
    <rect x={x - w / 2} y={y + h * 0.15} width={w} height={5} fill="rgba(0,0,0,0.12)" />
    <rect x={x - w / 2} y={y + h * 0.8} width={w} height={5} fill="rgba(0,0,0,0.12)" />
    <ellipse cx={x} cy={y} rx={w / 2} ry={w * 0.13} fill={head} stroke="#8D7458" strokeWidth={4} />
  </g>
);

const Cymbal: React.FC<{ x: number; y: number; r: number; rot: number; standTo: number }> = ({ x, y, r, rot, standTo }) => (
  <g>
    <line x1={x} y1={y} x2={x} y2={standTo} stroke="#3B3F42" strokeWidth={6} />
    <g transform={`translate(${x} ${y}) rotate(${(rot * 180) / Math.PI})`}>
      <ellipse cx={0} cy={0} rx={r} ry={r * 0.17} fill="#CDAE72" stroke="#A88A52" strokeWidth={3} />
      <ellipse cx={0} cy={-2} rx={r * 0.2} ry={r * 0.06} fill="#B39459" />
    </g>
  </g>
);

interface KitProps { x: number; y: number; s: number; t: number; hits: Hit[] }

/** Cymbals and hi-hat: drawn behind the drummer. */
export const DrumKitBack: React.FC<KitProps> = ({ x, y, s, t, hits }) => {
  const crash = pulse(hits, t, ['crash'], 5);
  const hat = pulse(hits, t, ['hat', 'ohat'], 20);
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <Cymbal x={-300} y={-230} r={120} rot={-0.18 + Math.sin(t * 30) * 0.08 * crash} standTo={250} />
      <Cymbal x={300} y={-250} r={112} rot={0.15 + Math.sin(t * 26) * 0.06 * crash} standTo={250} />
      <Cymbal x={-360} y={-40 + hat * 6} r={92} rot={-0.05} standTo={260} />
    </g>
  );
};

/** Toms, snare, floor tom and the bass drum (with a heart): drawn in front of the drummer. */
export const DrumKitFront: React.FC<KitProps> = ({ x, y, s, t, hits }) => {
  const kick = pulse(hits, t, ['kick'], 14);
  const sn = pulse(hits, t, ['snare'], 16);
  const r = 190 * (1 + kick * 0.025);
  return (
    <g transform={`translate(${x} ${y}) scale(${s})`}>
      <ellipse cx={0} cy={255} rx={460} ry={40} fill="rgba(110,70,35,0.2)" filter="url(#soft-16)" />
      <Drum x={-110} y={-110} w={150} h={90} shell="#C69C6D" head="#F1EADC" />
      <Drum x={105} y={-115} w={160} h={95} shell="#C69C6D" head="#F1EADC" />
      <Drum x={-255} y={10 + sn * 5} w={170} h={70} shell="#D9D3C7" head="#F4EFE6" />
      <Drum x={270} y={20} w={180} h={170} shell="#C69C6D" head="#F1EADC" />
      <circle cx={0} cy={60} r={r} fill="#A9794B" />
      <circle cx={0} cy={60} r={r - 18} fill="#F1EADC" />
      <circle cx={0} cy={60} r={r - 22} fill="none" stroke="rgba(0,0,0,0.12)" strokeWidth={4} />
      <HeartPath x={0} y={66} size={120} fill="#90A4AE" />
      {Array.from({ length: 8 }, (_, k) => {
        const a = (k / 8) * Math.PI * 2;
        return <circle key={k} cx={Math.cos(a) * (r - 9)} cy={60 + Math.sin(a) * (r - 9)} r={6} fill="#8D7458" />;
      })}
    </g>
  );
};

/** A drum stick, drawn inside an arm's transform: from the hand, angled inward. */
export const Stick: React.FC<{ side: -1 | 1; armRy: number; angle?: number }> = ({ side, armRy, angle = 0.75 }) => (
  <g transform={`translate(0 ${armRy * 1.25}) rotate(${(side * angle * 180) / Math.PI})`}>
    <line x1={0} y1={-10} x2={0} y2={175} stroke="#B98D5E" strokeWidth={13} strokeLinecap="round" />
    <line x1={0} y1={168} x2={0} y2={178} stroke="#E9D5B5" strokeWidth={15} strokeLinecap="round" />
  </g>
);
