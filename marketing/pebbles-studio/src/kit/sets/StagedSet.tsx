import React from 'react';
import { Headphones } from '../characters/Headphones';
import { Pebble } from '../characters/Pebble';
import { framingFor } from '../framings';
import { walkOffset } from '../poses';
import type { LiveCtx } from '../scene/live';
import { liveBody, liveFace } from '../scene/live';
import type { ResolvedCast } from '../timeline';
import type { FrameName, SetName, Who } from '../types';
import { AisleBack, CouchBack, CouchFront, KitchenBack, Trolley } from './HomeSets';

type HomeSet = Extract<SetName, 'kitchen' | 'couch' | 'aisle'>;

/** Kitchen, couch and aisle: backdrop, characters (standing, or seated on the couch), then anything in front. */
export const StagedSet: React.FC<{ set: HomeSet; frame: FrameName; cast: Record<Who, ResolvedCast | null>; ctx: LiveCtx }> = ({ set, frame, cast, ctx }) => {
  const f = framingFor(set, frame);
  const seated = set === 'couch';
  const z = 1 + Math.min(0.03, 0.012 * ctx.sinceCut);
  const who = (['mr', 'mrs'] as Who[]).filter((w) => cast[w] && f.place[w]);
  const draw = (w: Who, layers: ('body' | 'feet' | 'arms' | 'phones')[]) => {
    const rc = cast[w]!;
    const p = f.place[w]!;
    const dir = rc.facing === 'left' ? -1 : 1;
    const x = rc.pose === 'walkIn' ? p.x + dir * walkOffset(ctx.t - rc.poseSince) * p.scale : p.x;
    return (
      <Pebble key={`${w}-${layers.join()}`} who={w} x={x} y={p.y} scale={p.scale} t={ctx.t} layers={layers}
        face={liveFace(w, rc, ctx)} body={liveBody(w, rc, ctx, seated ? 'chair' : 'stand')}
        holding={rc.holding} facing={rc.facing} glasses={rc.glasses} shadow={!seated}
        phonesSlot={rc.headphones ? <Headphones who={w} /> : null} />
    );
  };
  return (
    <g transform={`translate(540 1200) scale(${z}) translate(-540 -1200)`}>
      {set === 'kitchen' && <KitchenBack />}
      {set === 'aisle' && <AisleBack />}
      {set === 'couch' && <CouchBack frame={frame} />}
      {seated ? (
        <>
          {who.map((w) => draw(w, ['body']))}
          <CouchFront frame={frame} />
          {who.map((w) => draw(w, ['feet', 'arms', 'phones']))}
        </>
      ) : (
        who.map((w) => draw(w, ['body', 'feet', 'arms', 'phones']))
      )}
      {set === 'aisle' && frame === 'wide' && <Trolley x={810} s={0.9} />}
    </g>
  );
};
