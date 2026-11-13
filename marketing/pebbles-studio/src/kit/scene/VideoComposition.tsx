import React, { useMemo } from 'react';
import type { CalculateMetadataFunction } from 'remotion';
import { AbsoluteFill, Audio, staticFile, useCurrentFrame } from 'remotion';
import { drumHits } from '../drums';
import { envelopeFromDb } from '../envelope';
import { framingFor } from '../framings';
import { Paper, SoftDefs } from '../sets/Paper';
import { END_CARD_SEC, FPS, framesFor, resolveAt } from '../timeline';
import type { VideoScript } from '../types';
import type { LiveCtx } from './live';
import { Caption, EndCard, SafeAreaGuide } from './Overlays';
import { SceneLayer } from './SceneLayer';

export type VideoProps = { script: VideoScript; envelope: number[]; showSafeArea?: boolean };

const AUDIO_FADE = 0.25;

export const VideoComposition: React.FC<VideoProps> = ({ script, envelope, showSafeArea }) => {
  const frame = useCurrentFrame();
  const t = frame / FPS;
  const r = resolveAt(script, t);
  const hits = useMemo(() => (script.drumGrid ? drumHits(script.drumGrid) : []), [script]);
  const ctx: LiveCtx = {
    t, frame, env: envelope, sinceCut: t - r.cutStart, hits, beat: script.drumGrid?.beat ?? 0.5, drums: !!r.shot.drums,
  };
  const set = r.shot.set;
  const framing = framingFor(set, r.shot.frame);
  return (
    <AbsoluteFill style={{ backgroundColor: '#E8D0B4' }}>
      {script.audio && (
        <Audio
          src={staticFile(`media/${script.slug}/${script.audio}`)}
          volume={(f) => {
            const s = f / FPS;
            if (s >= script.end) return 0;
            return Math.min(1, (script.end - s) / AUDIO_FADE);
          }}
        />
      )}
      <svg viewBox="0 0 1080 1920" width={1080} height={1920}>
        <SoftDefs />
        <Paper />
        {r.onEndCard ? (
          <EndCard p={t - script.end} dur={END_CARD_SEC} subtitle={script.endCard?.subtitle ?? ''} />
        ) : (
          <>
            <SceneLayer set={set} frame={r.shot.frame} cast={r.cast} ctx={ctx} />
            <Caption text={r.caption} style={r.shot.captionStyle ?? 'line'} framing={framing} />
          </>
        )}
        {showSafeArea && <SafeAreaGuide />}
      </svg>
    </AbsoluteFill>
  );
};

/** Loads the lip-sync envelope written by scripts/analyze.mjs; a missing one means closed mouths, with a warning. */
export const calculateVideoMetadata: CalculateMetadataFunction<VideoProps> = async ({ props }) => {
  let envelope: number[] = [];
  if (props.script.audio) {
    try {
      const res = await fetch(staticFile(`media/${props.script.slug}/envelope.json`));
      if (res.ok) envelope = envelopeFromDb((await res.json()).db);
      else console.warn(`No lip-sync envelope for ${props.script.slug}; run: npm run render ${props.script.slug}`);
    } catch {
      console.warn(`No lip-sync envelope for ${props.script.slug}; run: npm run render ${props.script.slug}`);
    }
  }
  return { durationInFrames: framesFor(props.script), props: { ...props, envelope } };
};
