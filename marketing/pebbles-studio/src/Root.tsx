import React from 'react';
import { AbsoluteFill, Composition, Still } from 'remotion';
import { Pebble } from './kit/characters/Pebble';
import type { Body, Face } from './kit/types';
import { EXPRESSIONS } from './kit/expressions';
import { ExpressionSheet, SHEET_H, SHEET_W } from './sheets/ExpressionSheet';
import { VideoComposition, calculateVideoMetadata } from './kit/scene/VideoComposition';
import { framesFor, video } from './kit/timeline';
import { VIDEOS } from './videos';

// Dev-only fixture (no audio) for checking sets and framings; removed with debug-pebbles in Task 9.
const DEBUG_SCENE = video({
  slug: 'debug-scene',
  end: 8,
  endCard: { subtitle: 'Babe, get this. Got this.' },
  drumGrid: { start: 6, beat: 0.4, end: 8 },
  shots: [
    { at: 0, set: 'podcast', frame: 'single-mr', caption: 'Spanish is easy, right?', mr: { expression: 'smug' } },
    { at: 2, set: 'podcast', frame: 'single-mrs', caption: 'Mañana', mrs: { expression: 'deadpan' } },
    { at: 4, set: 'podcast', frame: 'two-shot', caption: 'skillfully scratches the spider web on the mountain', mr: { look: 'other' }, mrs: { look: 'other' } },
    { at: 6, set: 'band', frame: 'two-shot', captionStyle: 'verse', drums: true,
      caption: 'Mañana por la mañana, la extraña araña de España araña con maña la telaraña de la montaña',
      mr: { pose: 'drum', expression: 'content' }, mrs: { expression: 'sing' } },
  ],
});

const flatFace: Face = {
  lid: 0, lidTilt: 0, eyeScale: 1, pupilScale: 1, look: { x: 0, y: 0 }, brow: [0, 0], browTilt: 0,
  smile: 1, open: 0, mouthWidth: 1, mouthShape: 'curve', blush: 1, happyEyes: false,
};
const standBody = (armX: number): Body => ({
  arms: [{ x: -armX, y: 40, rot: 0.6 }, { x: armX, y: 40, rot: -0.6 }],
  tilt: 0, squash: 0, dy: 0, feet: 'apart', hand: 1,
});

const DebugPebbles: React.FC = () => (
  <AbsoluteFill style={{ backgroundColor: '#E8D0B4' }}>
    <svg viewBox="0 0 2160 1080" width={2160} height={1080}>
      <rect x={1080} y={0} width={1080} height={1080} fill="#C9D3D6" />
      {[0, 1].map((half) =>
        [true, false].map((outline, j) => (
          <g key={`${half}${j}`} transform={`translate(${half * 1080 + j * 540} 0)`}>
            <Pebble who="mr" x={150} y={500} scale={0.62} face={EXPRESSIONS.smug} body={standBody(170)} t={0} outline={outline} shadow={!outline} />
            <Pebble who="mrs" x={390} y={530} scale={0.68} face={EXPRESSIONS.happy} body={standBody(125)} t={0} outline={outline} shadow={!outline} />
            <text x={270} y={110} textAnchor="middle" fontFamily="Figtree, sans-serif" fontWeight={800} fontSize={44} fill="#263A44">{outline ? 'with rim' : 'no rim + shadow'}</text>
          </g>
        )),
      )}
    </svg>
  </AbsoluteFill>
);

export const RemotionRoot: React.FC = () => (
  <>
    <Still id="debug-pebbles" component={DebugPebbles} width={2160} height={1080} />
    <Still id="sheet-expressions" component={ExpressionSheet} width={SHEET_W} height={SHEET_H} />
    <Composition id="debug-scene" component={VideoComposition} width={1080} height={1920} fps={30}
      durationInFrames={framesFor(DEBUG_SCENE)} defaultProps={{ script: DEBUG_SCENE, envelope: [], showSafeArea: false }}
      calculateMetadata={calculateVideoMetadata} />
    {VIDEOS.map((v) => (
      <Composition key={v.slug} id={`v-${v.slug}`} component={VideoComposition} width={1080} height={1920} fps={30}
        durationInFrames={framesFor(v)} defaultProps={{ script: v, envelope: [], showSafeArea: false }}
        calculateMetadata={calculateVideoMetadata} />
    ))}
  </>
);
