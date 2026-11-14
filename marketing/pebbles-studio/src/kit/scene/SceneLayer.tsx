import React from 'react';
import { BandSet } from '../sets/Band';
import { PodcastSet } from '../sets/PodcastSet';
import { StagedSet } from '../sets/StagedSet';
import { StageSet } from '../sets/StageSet';
import type { ResolvedCast } from '../timeline';
import type { FrameName, SetName, Who } from '../types';
import type { LiveCtx } from './live';

/** One frame of a set with its cast, in 1080x1920 screen space (no paper, captions or end card). */
export const SceneLayer: React.FC<{ set: SetName; frame: FrameName; cast: Record<Who, ResolvedCast | null>; ctx: LiveCtx }> = ({ set, frame, cast, ctx }) => {
  switch (set) {
    case 'podcast': return <PodcastSet frame={frame} cast={cast} ctx={ctx} />;
    case 'band': return <BandSet cast={cast} ctx={ctx} />;
    case 'stage': return <StageSet frame={frame} cast={cast} ctx={ctx} />;
    default: return <StagedSet set={set} frame={frame} cast={cast} ctx={ctx} />;
  }
};
