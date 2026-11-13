import React from 'react';
import { AbsoluteFill, Still } from 'remotion';
import { Pebble } from './kit/characters/Pebble';
import type { Body, Face } from './kit/types';

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
    <svg viewBox="0 0 1080 1080" width={1080} height={1080}>
      <Pebble who="mr" x={300} y={520} scale={1} face={flatFace} body={standBody(170)} t={0} />
      <Pebble who="mrs" x={780} y={570} scale={1} face={flatFace} body={standBody(125)} t={0} />
    </svg>
  </AbsoluteFill>
);

export const RemotionRoot: React.FC = () => (
  <>
    <Still id="debug-pebbles" component={DebugPebbles} width={1080} height={1080} />
  </>
);
