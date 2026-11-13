import React from 'react';
import { AbsoluteFill } from 'remotion';
import { Pebble } from '../kit/characters/Pebble';
import { EXPRESSIONS } from '../kit/expressions';
import { CHARACTERS } from '../kit/geometry';
import { bodyFor } from '../kit/poses';
import { Stick } from '../kit/props/DrumKit';
import { AisleBack, CouchBack, CouchFront, KitchenBack } from '../kit/sets/HomeSets';
import { Paper, SoftDefs } from '../kit/sets/Paper';
import type { PoseName, PropName, Who } from '../kit/types';
import type { AssetJob } from './jobs';

const HOLDS: Partial<Record<PoseName, PropName>> = { holdPhone: 'phone', holdBag: 'bag', holdList: 'list' };
const T = 0.4;
const SCALE = 2;
const CY = 1150; // body centre: room above for effects (hearts, zzz, "!") and below for feet

const Character: React.FC<{ who: Who; x: number; pose: PoseName; expr: keyof typeof EXPRESSIONS }> = ({ who, x, pose, expr }) => (
  <Pebble who={who} x={x} y={CY + (who === 'mrs' ? 40 : 0)} scale={SCALE} t={T}
    face={EXPRESSIONS[expr]} body={bodyFor(who, pose, T, T)} holding={HOLDS[pose]}
    armExtra={pose === 'drum' ? (k) => <Stick side={k === 0 ? -1 : 1} armRy={CHARACTERS[who].armRy} /> : undefined} />
);

/** A single exportable asset: a character on a transparent canvas, or an empty set backdrop. */
export const AssetStill: React.FC<{ job: AssetJob }> = ({ job }) => (
  <AbsoluteFill>
    <svg viewBox={`0 0 ${job.width} ${job.height}`} width={job.width} height={job.height}>
      <SoftDefs />
      {job.kind === 'expression' && <Character who={job.who} x={job.width / 2} pose="stand" expr={job.name} />}
      {job.kind === 'pose' && job.who !== 'duo' && <Character who={job.who} x={job.width / 2} pose={job.name} expr="content" />}
      {job.kind === 'pose' && job.who === 'duo' && (
        <>
          <Character who="mr" x={job.width / 2 - 170} pose="hug" expr="love" />
          <Character who="mrs" x={job.width / 2 + 170} pose="hug" expr="love" />
        </>
      )}
      {job.kind === 'background' && (
        <>
          <Paper />
          {job.name === 'kitchen' && <KitchenBack />}
          {job.name === 'aisle' && <AisleBack />}
          {job.name === 'couch' && <><CouchBack frame="single-mr" /><CouchFront frame="single-mr" /></>}
        </>
      )}
    </svg>
  </AbsoluteFill>
);
