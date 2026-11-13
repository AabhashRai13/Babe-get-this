import React from 'react';
import { Headphones } from '../characters/Headphones';
import { Pebble } from '../characters/Pebble';
import { framingFor } from '../framings';
import { CHARACTERS } from '../geometry';
import { DrumKitBack, DrumKitFront, Stick } from '../props/DrumKit';
import { StandMic } from '../props/Mics';
import type { LiveCtx } from '../scene/live';
import { liveBody, liveFace } from '../scene/live';
import type { ResolvedCast } from '../timeline';
import type { Who } from '../types';

// The band layout is the prototype's art (drawn for 1080x1920) seen through translate(0,-275) scale(0.86).
const K = 0.86;
const DY = -275;
const toArt = (x: number, y: number) => ({ x: x / K, y: (y - DY) / K });

/** Mr on drums (back left), Mrs singing at a stand mic (front right). Two-shot only. */
export const BandSet: React.FC<{ cast: Record<Who, ResolvedCast | null>; ctx: LiveCtx }> = ({ cast, ctx }) => {
  const f = framingFor('band', 'two-shot');
  const z = 1 + Math.min(0.02, 0.003 * ctx.sinceCut);
  const mr = cast.mr;
  const mrs = cast.mrs;
  const mp = toArt(f.place.mr!.x, f.place.mr!.y);
  const wp = toArt(f.place.mrs!.x, f.place.mrs!.y);
  const mScale = f.place.mr!.scale / K;
  const wScale = f.place.mrs!.scale / K;
  return (
    <g transform={`translate(540 1500) scale(${z}) translate(-540 -1500) translate(0 ${DY}) scale(${K})`}>
      <DrumKitBack x={360} y={1560} s={0.9} t={ctx.t} hits={ctx.drums ? ctx.hits : []} />
      {mr && (
        <Pebble who="mr" x={mp.x} y={mp.y} scale={mScale} face={liveFace('mr', mr, ctx)} body={liveBody('mr', mr, ctx, 'drum')} t={ctx.t}
          layers={['body', 'phones']} phonesSlot={mr.headphones ? <Headphones who="mr" /> : null} />
      )}
      <DrumKitFront x={360} y={1560} s={0.9} t={ctx.t} hits={ctx.drums ? ctx.hits : []} />
      {mr && (
        <Pebble who="mr" x={mp.x} y={mp.y} scale={mScale} face={liveFace('mr', mr, ctx)} body={liveBody('mr', mr, ctx, 'drum')} t={ctx.t}
          layers={['arms']} armExtra={(i) => <Stick side={i === 0 ? -1 : 1} armRy={CHARACTERS.mr.armRy} />} />
      )}
      <ellipse cx={wp.x} cy={2062} rx={300} ry={34} fill="rgba(110,70,35,0.22)" filter="url(#soft-16)" />
      {mrs && (
        <Pebble who="mrs" x={wp.x} y={wp.y} scale={wScale} face={liveFace('mrs', mrs, ctx)} body={liveBody('mrs', mrs, ctx, 'mic')} t={ctx.t}
          phonesSlot={mrs.headphones ? <Headphones who="mrs" /> : null} />
      )}
      <StandMic x={690} y={1795} s={1.15} floorY={2066} />
    </g>
  );
};
