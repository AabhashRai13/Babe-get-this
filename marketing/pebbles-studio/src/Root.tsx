import React from 'react';
import { Composition, Still } from 'remotion';
import { VideoComposition, calculateVideoMetadata } from './kit/scene/VideoComposition';
import { framesFor } from './kit/timeline';
import { ExpressionSheet, SHEET_H, SHEET_W } from './sheets/ExpressionSheet';
import { POSE_SHEET_H, POSE_SHEET_W, PoseSheet } from './sheets/PoseSheet';
import { SET_SHEET_H, SET_SHEET_W, SetSheet } from './sheets/SetSheet';
import { VIDEOS } from './videos';

export const RemotionRoot: React.FC = () => (
  <>
    <Still id="sheet-expressions" component={ExpressionSheet} width={SHEET_W} height={SHEET_H} />
    <Still id="sheet-poses" component={PoseSheet} width={POSE_SHEET_W} height={POSE_SHEET_H} />
    <Still id="sheet-sets" component={SetSheet} width={SET_SHEET_W} height={SET_SHEET_H} />
    {VIDEOS.map((v) => (
      <Composition key={v.slug} id={`v-${v.slug}`} component={VideoComposition} width={1080} height={1920} fps={30}
        durationInFrames={framesFor(v)} defaultProps={{ script: v, envelope: [], showSafeArea: false }}
        calculateMetadata={calculateVideoMetadata} />
    ))}
  </>
);
