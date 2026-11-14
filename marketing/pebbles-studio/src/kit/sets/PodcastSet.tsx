import React from 'react';
import { Headphones } from '../characters/Headphones';
import { Pebble } from '../characters/Pebble';
import { framingFor } from '../framings';
import { CHARACTERS } from '../geometry';
import { PopMic, StandMic } from '../props/Mics';
import type { LiveCtx } from '../scene/live';
import { liveBody, liveFace } from '../scene/live';
import type { ResolvedCast } from '../timeline';
import type { FrameName, Who } from '../types';
import { CHAIR, ChairBack, ChairFront } from './Podcast';

const SHIFT = -260; // the prototype's single shots were lifted 260px into the safe area
const SINGLE_SCALE: Record<Who, number> = { mr: 1.02, mrs: 1.12 };

/** A character in their podcast chair with their mic. cs = chair scale (1 at the single-shot size). */
const Seated: React.FC<{ who: Who; rc: ResolvedCast; ctx: LiveCtx; x: number; y: number; s: number; cs: number }> = ({ who, rc, ctx, x, y, s, cs }) => {
  const C = CHARACTERS[who];
  const chairY = y - 330 * cs + C.bottom * s;
  const face = liveFace(who, rc, ctx);
  const body = liveBody(who, rc, ctx, 'chair');
  const phones = rc.headphones ? <Headphones who={who} /> : null;
  return (
    <g>
      <ChairBack x={x} y={chairY} s={cs} col={CHAIR[who]} />
      <Pebble who={who} x={x} y={y} scale={s} face={face} body={body} t={ctx.t} layers={['body']} facing={rc.facing} glasses={rc.glasses} />
      <ChairFront x={x} y={chairY} s={cs} col={CHAIR[who]} />
      <Pebble who={who} x={x} y={y} scale={s} face={face} body={body} t={ctx.t} layers={['feet', 'arms', 'phones']} phonesSlot={phones}
        holding={rc.holding} facing={rc.facing} />
      {who === 'mr'
        ? <PopMic x={x + 150 * cs} y={y + 95 * s} s={cs} floorY={chairY + 455 * cs} />
        : <StandMic x={x - 22 * cs} y={y + 150 * s} s={1.05 * cs} floorY={chairY + 458 * cs} />}
    </g>
  );
};

/** Podcast: armchairs, mics, headphones. Single, two-shot, close-up, or split screen (Mr top, Mrs bottom). */
export const PodcastSet: React.FC<{ frame: FrameName; cast: Record<Who, ResolvedCast | null>; ctx: LiveCtx }> = ({ frame, cast, ctx }) => {
  const f = framingFor('podcast', frame);
  if (frame === 'two-shot') {
    const z = 1 + Math.min(0.03, 0.01 * ctx.sinceCut);
    return (
      <g transform={`translate(540 1200) scale(${z}) translate(-540 -1200)`}>
        {(['mr', 'mrs'] as Who[]).map((w) => {
          const p = f.place[w]!;
          return cast[w] && <Seated key={w} who={w} rc={cast[w]!} ctx={ctx} x={p.x} y={p.y} s={p.scale} cs={0.6} />;
        })}
      </g>
    );
  }
  if (frame === 'split' || frame === 'split-close') {
    // Each half is a window onto that character's own single shot; a dark rule divides them.
    return (
      <g>
        {(['mr', 'mrs'] as Who[]).map((w) => {
          const win = f.windows![w]!;
          const p = f.place[w]!;
          const id = `split-${frame}-${w}`;
          return cast[w] && (
            <g key={w}>
              <defs><clipPath id={id}><rect x={0} y={win.top} width={1080} height={win.height} /></clipPath></defs>
              <g clipPath={`url(#${id})`}>
                <Seated who={w} rc={cast[w]!} ctx={ctx} x={p.x} y={p.y} s={p.scale} cs={p.scale / SINGLE_SCALE[w]} />
              </g>
            </g>
          );
        })}
        <rect x={0} y={f.windows!.mrs!.top - 3} width={1080} height={6} fill="#2A2420" />
      </g>
    );
  }
  if (frame === 'close-mr' || frame === 'close-mrs') {
    const who: Who = frame === 'close-mr' ? 'mr' : 'mrs';
    const p = f.place[who]!;
    return cast[who] ? <Seated who={who} rc={cast[who]!} ctx={ctx} x={p.x} y={p.y} s={p.scale} cs={p.scale / SINGLE_SCALE[who]} /> : null;
  }
  const who: Who = frame === 'single-mr' ? 'mr' : 'mrs';
  const p = f.place[who]!;
  const rc = cast[who];
  const z = 1 + Math.min(0.04, 0.015 * ctx.sinceCut);
  return (
    <g transform={`translate(0 ${SHIFT}) translate(540 1250) scale(${z}) translate(-540 -1250)`}>
      {rc && <Seated who={who} rc={rc} ctx={ctx} x={p.x} y={p.y - SHIFT} s={p.scale} cs={1} />}
    </g>
  );
};
