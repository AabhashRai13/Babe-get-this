import React from 'react';
import { AbsoluteFill } from 'remotion';
import { Pebble } from '../kit/characters/Pebble';
import { EXPRESSIONS } from '../kit/expressions';
import { CAPTION_FONT } from '../kit/fonts';
import type { Body, ExpressionName } from '../kit/types';

export const SHEET_W = 2160;
export const SHEET_H = 3840;
const COLS = 4;
const CELL_W = SHEET_W / COLS;
const CELL_H = 768;

const restBody = (armX: number): Body => ({
  arms: [{ x: -armX, y: 40, rot: 0.6 }, { x: armX, y: 40, rot: -0.6 }],
  tilt: 0, squash: 0, dy: 0, feet: 'apart', hand: 1,
});

/** Every expression for both characters, labelled. Owner signs this off before poses and sets are built. */
export const ExpressionSheet: React.FC = () => {
  const names = Object.keys(EXPRESSIONS) as ExpressionName[];
  return (
    <AbsoluteFill style={{ backgroundColor: '#E8D0B4' }}>
      <svg viewBox={`0 0 ${SHEET_W} ${SHEET_H}`} width={SHEET_W} height={SHEET_H}>
        {names.map((n, i) => {
          const cx = (i % COLS) * CELL_W;
          const cy = Math.floor(i / COLS) * CELL_H;
          const face = EXPRESSIONS[n];
          return (
            <g key={n}>
              <rect x={cx + 8} y={cy + 8} width={CELL_W - 16} height={CELL_H - 16} rx={28} fill={i % 2 ? '#EDD9C2' : '#F0DFCB'} />
              <text x={cx + CELL_W / 2} y={cy + 78} textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={800} fontSize={52} fill="#263A44">
                {n}
              </text>
              <Pebble who="mr" x={cx + 150} y={cy + 440} scale={0.5} face={face} body={restBody(170)} t={0.5} />
              <Pebble who="mrs" x={cx + 395} y={cy + 470} scale={0.55} face={face} body={restBody(125)} t={0.5} />
            </g>
          );
        })}
      </svg>
    </AbsoluteFill>
  );
};
