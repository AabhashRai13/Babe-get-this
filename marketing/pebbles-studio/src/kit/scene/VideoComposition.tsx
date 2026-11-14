import React, { useMemo } from 'react';
import type { CalculateMetadataFunction } from 'remotion';
import { AbsoluteFill, Audio, staticFile, useCurrentFrame } from 'remotion';
import { drumHits } from '../drums';
import type { Music } from '../acting';
import { NO_MUSIC, musicFromEnvelope } from '../acting';
import type { Lip } from '../envelope';
import { lipFromEnvelope } from '../envelope';
import { framingFor } from '../framings';
import { Paper, SoftDefs } from '../sets/Paper';
import { END_CARD_SEC, FPS, framesFor, resolveAt } from '../timeline';
import type { CharCaption, VideoScript, Who } from '../types';
import type { LiveCtx } from './live';
import { Caption, EndCard, SafeAreaGuide } from './Overlays';
import { SceneLayer } from './SceneLayer';

export type { Lip };
export type VideoProps = { script: VideoScript; lip: Lip; music?: Music; showSafeArea?: boolean };
export const NO_LIP: Lip = { open: [], width: [], round: [], accents: [], phrases: [] };
export { NO_MUSIC };

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
const easeInOut = (v: number) => (v < 0.5 ? 2 * v * v : 1 - Math.pow(-2 * v + 2, 2) / 2);
const POP = new Set(['pop', 'popYellow', 'hanzi']);

/** A gentle push-in over the shot, a small landing after each cut, and a punch-in when a big word pops. */
function cameraZoom(sinceCut: number, captionAge: number | null): number {
  const push = 0.03 * easeInOut(clamp01(sinceCut / 3));
  const land = 0.03 * Math.exp(-sinceCut * 7);
  const punch = captionAge === null ? 0 : 0.045 * (captionAge < 0.06 ? captionAge / 0.06 : Math.exp(-(captionAge - 0.06) * 4));
  return 1 + push + land + punch;
}

const AUDIO_FADE = 0.25;

export const VideoComposition: React.FC<VideoProps> = ({ script, lip, music = NO_MUSIC, showSafeArea }) => {
  const frame = useCurrentFrame();
  const t = frame / FPS;
  const r = resolveAt(script, t);
  // Real hits from the clip's drums stem; the first video's groove only when there are no stems.
  const hits = useMemo(() => music.hits ?? (script.drumGrid ? drumHits(script.drumGrid) : []), [script, music]);
  const ctx: LiveCtx = {
    t, frame, lip, speaker: r.speaker, strums: music.strums, sinceCut: t - r.cutStart, hits,
    beat: script.drumGrid?.beat ?? 0.5, drums: !!r.shot.drums,
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
            {(() => {
              // Camera moves for everything except split screens (their halves must stay put).
              const popAge = r.caption && POP.has(r.captionStyle) ? t - r.captionSince : null;
              const z = framing.windows ? 1 : cameraZoom(t - r.cutStart, popAge);
              const faces = Object.values(framing.faces);
              const fx = faces.reduce((a, f) => a + f!.x + f!.w / 2, 0) / Math.max(1, faces.length);
              const fy = faces.reduce((a, f) => a + f!.y + f!.h / 2, 0) / Math.max(1, faces.length);
              return (
                <g transform={`translate(${fx} ${fy}) scale(${z}) translate(${-fx} ${-fy})`}>
                  <SceneLayer set={set} frame={r.shot.frame} cast={r.cast} ctx={ctx} />
                </g>
              );
            })()}
            <Caption text={r.caption} style={r.captionStyle} framing={framing} age={t - r.captionSince} />
            {(Object.entries(r.captions) as [Who, CharCaption][]).map(([w, c]) => (
              <Caption key={w} text={c.text} style={c.style} framing={framing} who={framing.sub?.[w] ? w : undefined} age={t - (r.captionsSince[w] ?? r.cutStart)} />
            ))}
          </>
        )}
        {showSafeArea && <SafeAreaGuide />}
      </svg>
    </AbsoluteFill>
  );
};

/** Loads the envelope written by scripts/analyze.mjs (the isolated vocals) and turns it into lip sync; missing = closed mouths. */
export const calculateVideoMetadata: CalculateMetadataFunction<VideoProps> = async ({ props }) => {
  let lip: Lip = NO_LIP;
  let music: Music = NO_MUSIC;
  if (props.script.audio) {
    try {
      const res = await fetch(staticFile(`media/${props.script.slug}/envelope.json`));
      if (res.ok) {
        const j = await res.json();
        if (j.source !== 'vocals') console.warn(`Lip sync for ${props.script.slug} follows the whole mix (no vocal stem); music will move mouths.`);
        lip = lipFromEnvelope(j, FPS, (sec) => resolveAt(props.script, sec).speaker);
        music = musicFromEnvelope(j, FPS);
      } else console.warn(`No lip-sync envelope for ${props.script.slug}; run: npm run analyze ${props.script.slug}`);
    } catch {
      console.warn(`No lip-sync envelope for ${props.script.slug}; run: npm run analyze ${props.script.slug}`);
    }
  }
  return { durationInFrames: framesFor(props.script), props: { ...props, lip, music } };
};
