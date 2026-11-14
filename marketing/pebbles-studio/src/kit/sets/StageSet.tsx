import React from 'react';
import { Headphones } from '../characters/Headphones';
import { Pebble } from '../characters/Pebble';
import { CAPTION_FONT } from '../fonts';
import { framingFor } from '../framings';
import { StandMic } from '../props/Mics';
import type { LiveCtx } from '../scene/live';
import { liveBody, liveFace } from '../scene/live';
import type { ResolvedCast } from '../timeline';
import type { FrameName, Who } from '../types';
import { BandSet } from './Band';

const FLOOR = 1440;

/** Dark concert stage: warm spotlights, red bokeh, a glossy floor and a neon LIVE sign. */
export const StageBackdrop: React.FC<{ t: number; sparks: boolean }> = ({ t, sparks }) => {
  let seed = 3;
  const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
  const bokeh = Array.from({ length: 26 }, () => ({ x: rnd() * 1080, y: 300 + rnd() * 900, r: 18 + rnd() * 50, ph: rnd() * 6, red: rnd() < 0.6 }));
  return (
    <g>
      <defs>
        <linearGradient id="stage-bg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2B140E" />
          <stop offset="0.7" stopColor="#170C09" />
          <stop offset="1" stopColor="#0E0807" />
        </linearGradient>
        <radialGradient id="stage-glow" cx="540" cy="1250" r="800" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="rgb(255,140,60)" stopOpacity={0.45} />
          <stop offset="1" stopColor="rgb(255,90,40)" stopOpacity={0} />
        </radialGradient>
        <linearGradient id="stage-floor" x1="0" y1={FLOOR} x2="0" y2="1920" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#3A2016" />
          <stop offset="1" stopColor="#120A08" />
        </linearGradient>
        <linearGradient id="beam" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="rgb(255,220,170)" stopOpacity={0.35} />
          <stop offset="1" stopColor="rgb(255,220,170)" stopOpacity={0} />
        </linearGradient>
        <filter id="neon" x="-20%" y="-50%" width="140%" height="200%"><feGaussianBlur stdDeviation={10} /></filter>
      </defs>
      <rect width={1080} height={1920} fill="url(#stage-bg)" />
      <rect width={1080} height={1920} fill="url(#stage-glow)" />
      {bokeh.map((b, i) => (
        <circle key={i} cx={b.x} cy={b.y} r={b.r} fill={b.red ? '#FF4A2E' : '#FFB25C'} opacity={0.18 + 0.12 * Math.sin(t * 2 + b.ph)} filter="url(#soft-16)" />
      ))}
      {[200, 540, 880].map((x, i) => (
        <path key={x} d={`M ${x - 30} 270 L ${x + 30} 270 L ${x + 260 - i * 130} ${FLOOR} L ${x - 260 + (2 - i) * 130 - 130} ${FLOOR} Z`} fill="url(#beam)"
          opacity={0.5 + 0.3 * Math.sin(t * 1.3 + i)} />
      ))}
      {[200, 540, 880].map((x) => <rect key={`l${x}`} x={x - 40} y={250} width={80} height={36} rx={10} fill="#2E2E2E" />)}
      <g transform="translate(540 340)" textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={900} fontSize={64}>
        <text fill="#FF4A6E" filter="url(#neon)" opacity={0.9}>LIVE ♪</text>
        <text fill="#FFD6E0">LIVE ♪</text>
      </g>
      <rect x={0} y={FLOOR} width={1080} height={1920 - FLOOR} fill="url(#stage-floor)" />
      <ellipse cx={540} cy={FLOOR + 40} rx={460} ry={40} fill="rgba(255,150,80,0.25)" filter="url(#soft-16)" />
      {sparks && [130, 950].map((x) =>
        Array.from({ length: 22 }, (_, k) => {
          const ph = (t * 1.6 + k / 22) % 1;
          const sx = x + Math.sin(k * 7.3) * 40 * ph;
          const sy = FLOOR - ph * (520 + (k % 5) * 40);
          return <circle key={`${x}-${k}`} cx={sx} cy={sy} r={5 - ph * 3} fill="#FFE29A" opacity={1 - ph} />;
        }),
      )}
    </g>
  );
};

/** The concert: Mrs sings with her lute (wide or close), or the full band with Mr on drums. */
export const StageSet: React.FC<{ frame: FrameName; cast: Record<Who, ResolvedCast | null>; ctx: LiveCtx }> = ({ frame, cast, ctx }) => {
  const f = framingFor('stage', frame);
  if (frame === 'two-shot') {
    return (
      <g>
        <StageBackdrop t={ctx.t} sparks={ctx.drums} />
        <BandSet cast={cast} ctx={ctx} />
      </g>
    );
  }
  const rc = cast.mrs;
  const p = f.place.mrs!;
  const k = p.scale / 1.1; // 1 at the wide single
  return (
    <g>
      <StageBackdrop t={ctx.t} sparks={false} />
      {rc && (
        <>
          <ellipse cx={p.x} cy={p.y + 300 * p.scale} rx={260 * p.scale} ry={30 * p.scale} fill="rgba(0,0,0,0.35)" filter="url(#soft-16)" />
          <Pebble who="mrs" x={p.x} y={p.y} scale={p.scale} t={ctx.t} face={liveFace('mrs', rc, ctx)} body={liveBody('mrs', rc, ctx, 'mic')}
            holding={rc.holding} glasses={rc.glasses} facing={rc.facing} phonesSlot={rc.headphones ? <Headphones who="mrs" /> : null} />
          <StandMic x={p.x - 110 * k} y={p.y + 60 * p.scale} s={1.15 * k} floorY={FLOOR + 40 * k} />
        </>
      )}
    </g>
  );
};
