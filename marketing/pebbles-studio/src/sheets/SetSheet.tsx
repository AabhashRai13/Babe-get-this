import React from 'react';
import { AbsoluteFill } from 'remotion';
import { faceFor } from '../kit/expressions';
import { CAPTION_FONT } from '../kit/fonts';
import { FRAMINGS } from '../kit/framings';
import type { LiveCtx } from '../kit/scene/live';
import { Caption, SafeAreaGuide } from '../kit/scene/Overlays';
import { SceneLayer } from '../kit/scene/SceneLayer';
import { Paper, SoftDefs } from '../kit/sets/Paper';
import type { ResolvedCast } from '../kit/timeline';
import type { FrameName, SetName, Who } from '../kit/types';

export const SET_SHEET_W = 2160;
export const SET_SHEET_H = 4320;
const COLS = 4;
const CELL_W = 540;
const CELL_H = 1080; // 960 frame + label

const LONG_LINE = 'skillfully scratches the spider web on the mountain';
const VERSE = 'Mañana por la mañana, la extraña araña de España araña con maña la telaraña de la montaña';

const cast = (set: SetName, who: Who): ResolvedCast => ({
  face: faceFor('neutral'), pose: set === 'band' && who === 'mr' ? 'drum' : 'sit', headphones: set === 'podcast' || set === 'band',
  facing: 'right', speaking: false, poseSince: 0,
});
const ctx: LiveCtx = { t: 0.5, frame: 15, env: [], sinceCut: 2, hits: [], beat: 0.4, drums: false };

/** Every set and framing, neutral cast, the longest test caption and the safe-area guide. */
export const SetSheet: React.FC = () => {
  const cells = Object.entries(FRAMINGS).flatMap(([set, frames]) =>
    Object.entries(frames ?? {}).map(([frame, f]) => ({ set: set as SetName, frame: frame as FrameName, f: f! })),
  );
  return (
    <AbsoluteFill style={{ backgroundColor: '#3B3330' }}>
      <svg viewBox={`0 0 ${SET_SHEET_W} ${SET_SHEET_H}`} width={SET_SHEET_W} height={SET_SHEET_H}>
        <SoftDefs />
        {cells.map(({ set, frame, f }, i) => {
          const x = (i % COLS) * CELL_W;
          const y = Math.floor(i / COLS) * CELL_H;
          const c: Record<Who, ResolvedCast | null> = {
            mr: f.place.mr ? cast(set, 'mr') : null,
            mrs: f.place.mrs ? cast(set, 'mrs') : null,
          };
          return (
            <g key={`${set}-${frame}`}>
              <text x={x + CELL_W / 2} y={y + 80} textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={800} fontSize={44} fill="#F4EADC">
                {set} / {frame}
              </text>
              <svg x={x + 10} y={y + 110} width={CELL_W - 20} height={(CELL_W - 20) * (1920 / 1080)} viewBox="0 0 1080 1920">
                <Paper />
                <SceneLayer set={set} frame={frame} cast={c} ctx={ctx} />
                <Caption text={set === 'band' ? VERSE : LONG_LINE} style={set === 'band' ? 'verse' : 'line'} framing={f} />
                <SafeAreaGuide />
              </svg>
            </g>
          );
        })}
      </svg>
    </AbsoluteFill>
  );
};
