import React from 'react';
import { AbsoluteFill } from 'remotion';
import { Pebble } from '../kit/characters/Pebble';
import { CHARACTERS } from '../kit/geometry';
import { EXPRESSIONS } from '../kit/expressions';
import { CAPTION_FONT } from '../kit/fonts';
import { POSES, bodyFor } from '../kit/poses';
import { Stick } from '../kit/props/DrumKit';
import type { PoseName, PropName, Who } from '../kit/types';

export const POSE_SHEET_W = 2160;
export const POSE_SHEET_H = 3072;
const COLS = 4;
const CELL_W = POSE_SHEET_W / COLS;
const CELL_H = 768;

const HOLDS: Partial<Record<PoseName, PropName>> = { holdPhone: 'phone', holdBag: 'bag', holdList: 'list' };
const T = 0.4; // a still moment; walkIn is shown mid-hop

/** Every pose for both characters, labelled, with held props. */
export const PoseSheet: React.FC = () => {
  const names = Object.keys(POSES) as PoseName[];
  return (
    <AbsoluteFill style={{ backgroundColor: '#E8D0B4' }}>
      <svg viewBox={`0 0 ${POSE_SHEET_W} ${POSE_SHEET_H}`} width={POSE_SHEET_W} height={POSE_SHEET_H}>
        {names.map((n, i) => {
          const cx = (i % COLS) * CELL_W;
          const cy = Math.floor(i / COLS) * CELL_H;
          const hug = n === 'hug';
          const place: Record<Who, { x: number; y: number; s: number }> = {
            mr: { x: cx + (hug ? 205 : 150), y: cy + 440, s: 0.5 },
            mrs: { x: cx + (hug ? 345 : 395), y: cy + 470, s: 0.55 },
          };
          return (
            <g key={n}>
              <rect x={cx + 8} y={cy + 8} width={CELL_W - 16} height={CELL_H - 16} rx={28} fill={i % 2 ? '#EDD9C2' : '#F0DFCB'} />
              <text x={cx + CELL_W / 2} y={cy + 78} textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={800} fontSize={52} fill="#263A44">
                {n}
              </text>
              {(['mr', 'mrs'] as Who[]).map((w) => (
                <Pebble key={w} who={w} x={place[w].x} y={place[w].y} scale={place[w].s} t={T}
                  face={hug ? EXPRESSIONS.love : EXPRESSIONS.content} body={bodyFor(w, n, T, T)} holding={HOLDS[n]} shadow
                  armExtra={n === 'drum' ? (k) => <Stick side={k === 0 ? -1 : 1} armRy={CHARACTERS[w].armRy} /> : undefined} />
              ))}
            </g>
          );
        })}
      </svg>
    </AbsoluteFill>
  );
};
