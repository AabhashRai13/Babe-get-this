import React from 'react';

/** Warm paper background with grain and a soft vignette. Deterministic: same pixels every frame. */
export const Paper: React.FC<{ tone?: string }> = ({ tone = '#E8D0B4' }) => {
  const blotches: [number, number, number, boolean][] = [];
  let s = 11;
  const r = () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
  for (let i = 0; i < 40; i++) blotches.push([r() * 1080, r() * 1920, 120 + r() * 260, r() < 0.5]);
  return (
    <g>
      <defs>
        <radialGradient id="paper-dark"><stop offset="0" stopColor="rgb(150,100,60)" stopOpacity={0.03} /><stop offset="1" stopColor="rgb(150,100,60)" stopOpacity={0} /></radialGradient>
        <radialGradient id="paper-light"><stop offset="0" stopColor="rgb(255,245,230)" stopOpacity={0.05} /><stop offset="1" stopColor="rgb(255,245,230)" stopOpacity={0} /></radialGradient>
        <radialGradient id="paper-vignette" cx="540" cy="960" r="1440" gradientUnits="userSpaceOnUse">
          <stop offset="0.4" stopColor="rgb(90,55,25)" stopOpacity={0} />
          <stop offset="1" stopColor="rgb(90,55,25)" stopOpacity={0.16} />
        </radialGradient>
        <filter id="paper-grain" x="0" y="0" width="100%" height="100%">
          <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves={2} seed={11} result="n" />
          <feColorMatrix in="n" type="matrix" values="0 0 0 0 0.45  0 0 0 0 0.3  0 0 0 0 0.15  0 0 0 0.09 0" />
        </filter>
      </defs>
      <rect width={1080} height={1920} fill={tone} />
      {blotches.map(([x, y, rad, dark], i) => (
        <circle key={i} cx={x} cy={y} r={rad} fill={`url(#${dark ? 'paper-dark' : 'paper-light'})`} />
      ))}
      <rect width={1080} height={1920} filter="url(#paper-grain)" />
      <rect width={1080} height={1920} fill="url(#paper-vignette)" />
    </g>
  );
};

/** Shared soft-shadow blur filter; ids are global inside one SVG. */
export const SoftDefs: React.FC = () => (
  <defs>
    <filter id="soft-16" x="-50%" y="-200%" width="200%" height="500%"><feGaussianBlur stdDeviation={16} /></filter>
    <filter id="soft-26" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation={26} /></filter>
  </defs>
);
