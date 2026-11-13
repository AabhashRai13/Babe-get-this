// Kitchen, couch and grocery-aisle backdrops. Flat, warm palette (creams, woods, terracotta, soft greens):
// nothing in the Pebbles' own blue-grey range, so they read clearly without an outline.
import React from 'react';
import { HeartPath } from '../props/Effects';
import type { FrameName } from '../types';

export const FLOOR_Y = 1440;
const WOOD = '#C99A6B';
const WOOD_DARK = '#A97D52';
const CREAMY = '#F4EADC';

const Floor: React.FC<{ fill: string; line: string; planks?: boolean }> = ({ fill, line, planks = true }) => (
  <g>
    <rect x={0} y={FLOOR_Y} width={1080} height={1920 - FLOOR_Y} fill={fill} />
    <rect x={0} y={FLOOR_Y} width={1080} height={10} fill="rgba(90,55,25,0.08)" />
    {planks
      ? [0, 1, 2, 3].map((i) => <line key={i} x1={0} y1={FLOOR_Y + 70 + i * 110} x2={1080} y2={FLOOR_Y + 70 + i * 110} stroke={line} strokeWidth={3} />)
      : [0, 1, 2, 3, 4, 5].map((i) => <line key={i} x1={i * 216} y1={FLOOR_Y} x2={i * 216 - 120} y2={1920} stroke={line} strokeWidth={3} />)}
  </g>
);

const Plant: React.FC<{ x: number; y: number; s?: number }> = ({ x, y, s = 1 }) => (
  <g transform={`translate(${x} ${y}) scale(${s})`}>
    {[-0.6, -0.2, 0.25, 0.65].map((a, i) => (
      <ellipse key={i} cx={Math.sin(a) * 40} cy={-55 - Math.cos(a) * 20} rx={16} ry={48} transform={`rotate(${a * 57} ${Math.sin(a) * 40} ${-55 - Math.cos(a) * 20})`} fill={i % 2 ? '#8DAF7E' : '#7A9E6C'} />
    ))}
    <path d="M -34 -12 H 34 L 26 40 H -26 Z" fill="#C4785A" />
  </g>
);

export const KitchenBack: React.FC = () => (
  <g>
    <line x1={540} y1={0} x2={540} y2={330} stroke="#8A6B4F" strokeWidth={4} />
    <path d="M 480 400 Q 540 300 600 400 Z" fill="#E9C46A" />
    <ellipse cx={540} cy={420} rx={90} ry={26} fill="rgba(255,230,170,0.35)" />
    {/* fridge */}
    <rect x={-20} y={560} width={250} height={880} rx={26} fill="#F2ECE3" />
    <rect x={-20} y={560} width={250} height={880} rx={26} fill="none" stroke="#E2D6C6" strokeWidth={4} />
    <line x1={-20} y1={860} x2={230} y2={860} stroke="#E2D6C6" strokeWidth={5} />
    <rect x={195} y={640} width={12} height={150} rx={6} fill="#CFC4B5" />
    <rect x={195} y={900} width={12} height={180} rx={6} fill="#CFC4B5" />
    <HeartPath x={110} y={680} size={50} fill="#E99A99" />
    <rect x={80} y={720} width={60} height={70} rx={4} fill="#FBF7EF" transform="rotate(-6 110 755)" />
    {/* window */}
    <rect x={700} y={620} width={320} height={340} rx={18} fill={CREAMY} />
    <rect x={722} y={642} width={276} height={296} rx={10} fill="#F8E6C8" />
    <line x1={860} y1={642} x2={860} y2={938} stroke={CREAMY} strokeWidth={12} />
    <line x1={722} y1={790} x2={998} y2={790} stroke={CREAMY} strokeWidth={12} />
    <rect x={690} y={958} width={340} height={22} rx={8} fill="#E8D9C4" />
    <Plant x={800} y={950} s={0.9} />
    {/* tiles + counter + cabinets */}
    <rect x={0} y={1000} width={1080} height={180} fill="#F3E3CF" />
    {Array.from({ length: 10 }, (_, i) => <line key={i} x1={i * 108} y1={1000} x2={i * 108} y2={1180} stroke="#E6D2BA" strokeWidth={3} />)}
    <line x1={0} y1={1090} x2={1080} y2={1090} stroke="#E6D2BA" strokeWidth={3} />
    <rect x={0} y={1170} width={1080} height={28} fill="#EFE3D3" />
    <rect x={0} y={1198} width={1080} height={FLOOR_Y - 1198} fill="#D9B48A" />
    {[0, 1, 2, 3].map((i) => (
      <g key={i}>
        <rect x={20 + i * 265} y={1220} width={245} height={200} rx={14} fill="#E2C39C" />
        <rect x={20 + i * 265 + 105} y={1240} width={36} height={10} rx={5} fill={WOOD_DARK} />
      </g>
    ))}
    <Floor fill="#D9B892" line="#C9A57D" />
  </g>
);

const SofaParts = (cx: number, seatY: number, width: number) => {
  const half = width / 2;
  return {
    back: (
      <g>
        <rect x={cx - half + 40} y={seatY - 360} width={width - 80} height={380} rx={70} fill="#D4876A" />
        {[-1, 0, 1].map((k) => (
          <rect key={k} x={cx + k * ((width - 200) / 3) - (width - 260) / 6} y={seatY - 330} width={(width - 260) / 3} height={300} rx={50} fill="#DC9476" />
        ))}
      </g>
    ),
    front: (
      <g>
        <rect x={cx - half + 60} y={seatY - 10} width={width - 120} height={110} rx={36} fill="#C97A5E" />
        {[-1, 1].map((sd) => (
          <rect key={sd} x={sd < 0 ? cx - half : cx + half - 130} y={seatY - 210} width={130} height={320} rx={56} fill="#BF7055" />
        ))}
        {[-1, 1].map((sd) => (
          <rect key={`l${sd}`} x={cx + sd * (half - 90) - 12} y={seatY + 100} width={24} height={40} rx={8} fill={WOOD_DARK} />
        ))}
      </g>
    ),
  };
};

export function couchSeatY(frame: FrameName): number {
  return frame === 'two-shot' ? 1392 : 1400;
}

export const CouchBack: React.FC<{ frame: FrameName }> = ({ frame }) => {
  const sofa = SofaParts(545, couchSeatY(frame), frame === 'two-shot' ? 980 : 860);
  return (
    <g>
      <defs>
        <radialGradient id="tv-glow" cx="0" cy="1050" r="520" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="rgb(255,248,235)" stopOpacity={0.55} />
          <stop offset="1" stopColor="rgb(255,248,235)" stopOpacity={0} />
        </radialGradient>
        <radialGradient id="lamp-glow" cx="960" cy="760" r="300" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="rgb(255,226,160)" stopOpacity={0.5} />
          <stop offset="1" stopColor="rgb(255,226,160)" stopOpacity={0} />
        </radialGradient>
      </defs>
      <rect x={0} y={0} width={1080} height={1920} fill="url(#tv-glow)" />
      <rect x={0} y={0} width={1080} height={1920} fill="url(#lamp-glow)" />
      <rect x={390} y={660} width={300} height={220} rx={10} fill={WOOD} />
      <rect x={408} y={678} width={264} height={184} rx={6} fill={CREAMY} />
      <HeartPath x={540} y={782} size={110} fill="#E99A99" />
      <line x1={960} y1={830} x2={960} y2={FLOOR_Y} stroke="#8A6B4F" strokeWidth={8} />
      <ellipse cx={960} cy={FLOOR_Y} rx={60} ry={12} fill="#8A6B4F" />
      <path d="M 900 830 L 1020 830 L 990 720 L 930 720 Z" fill="#F4D9A8" />
      <Floor fill="#D9B892" line="#C9A57D" />
      <ellipse cx={545} cy={1560} rx={470} ry={70} fill="#E8C9A0" />
      {sofa.back}
    </g>
  );
};

export const CouchFront: React.FC<{ frame: FrameName }> = ({ frame }) =>
  SofaParts(545, couchSeatY(frame), frame === 'two-shot' ? 980 : 860).front;

const PRODUCT = ['#E07A5F', '#F2CC8F', '#81B29A', '#F4F1DE', '#E9C46A', '#D4A373', '#C9705A', '#A3C4A0'];

export const AisleBack: React.FC = () => {
  let seed = 7;
  const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
  // Shelving starts below the caption band (captions sit at y≈510–650) so text never lands on products.
  const top = 700;
  const shelves = [870, 1040, 1210, 1380];
  return (
    <g>
      <rect x={0} y={top} width={1080} height={FLOOR_Y - top} fill="#F2E3CC" />
      {[0, 360, 720, 1080].map((x) => <rect key={x} x={x - 12} y={top} width={24} height={FLOOR_Y - top} fill="#B98D5E" />)}
      <rect x={0} y={top - 10} width={1080} height={24} fill={WOOD} />
      {shelves.map((sy) => {
        const items: React.ReactNode[] = [];
        let x = 20;
        while (x < 1060) {
          const w = 50 + rnd() * 60;
          const h = 80 + rnd() * 60;
          const c = PRODUCT[Math.floor(rnd() * PRODUCT.length)];
          if (x + w > 1065) break;
          items.push(
            <g key={x}>
              <rect x={x} y={sy - h} width={w} height={h} rx={rnd() < 0.4 ? w / 2.5 : 8} fill={c} />
              <rect x={x + 6} y={sy - h * 0.6} width={w - 12} height={h * 0.22} rx={4} fill="rgba(255,255,255,0.55)" />
            </g>,
          );
          x += w + 10 + rnd() * 10;
        }
        return (
          <g key={sy}>
            {items}
            <rect x={0} y={sy} width={1080} height={20} fill={WOOD} />
            <rect x={0} y={sy + 20} width={1080} height={6} fill="rgba(90,55,25,0.12)" />
          </g>
        );
      })}
      <Floor fill="#EADBC4" line="#DCC9AE" planks={false} />
    </g>
  );
};

export const Trolley: React.FC<{ x: number; s?: number }> = ({ x, s = 1 }) => (
  <g transform={`translate(${x} ${FLOOR_Y}) scale(${s})`} stroke="#6B5E55" strokeLinecap="round" strokeLinejoin="round" fill="none">
    {/* an overflowing load: cereal, milk, bottles, bananas, bread, greens */}
    <g stroke="none">
      <rect x={-118} y={-420} width={70} height={120} rx={6} fill="#E07A5F" transform="rotate(-8 -83 -360)" />
      <rect x={-108} y={-395} width={50} height={22} rx={4} fill="rgba(255,255,255,0.6)" transform="rotate(-8 -83 -360)" />
      <rect x={-40} y={-400} width={52} height={100} rx={8} fill="#F4F1DE" />
      <path d="M -40 -400 l 26 -24 l 26 24 Z" fill="#E9E4CC" />
      <rect x={-34} y={-370} width={40} height={26} rx={4} fill="#81B29A" />
      <rect x={20} y={-430} width={30} height={130} rx={14} fill="#A3C4A0" />
      <rect x={28} y={-458} width={14} height={30} rx={5} fill="#7A9E6C" />
      <rect x={58} y={-395} width={64} height={95} rx={10} fill="#F2CC8F" transform="rotate(10 90 -347)" />
      <path d="M -10 -315 q 40 -50 95 -25 q -45 -6 -90 40 Z" fill="#F2CC8F" stroke="#D9A86C" strokeWidth={3} />
      <rect x={-75} y={-345} width={34} height={100} rx={12} fill="#D9A86C" transform="rotate(-25 -58 -295)" />
      <path d="M 95 -330 q 30 -70 60 -20 q -40 -12 -45 30 Z" fill="#7FA36B" />
    </g>
    <path d="M -130 -300 H 130 L 105 -120 H -105 Z" strokeWidth={10} />
    {[-80, -30, 20, 70].map((vx) => <line key={vx} x1={vx} y1={-300} x2={vx * 0.82} y2={-120} strokeWidth={5} />)}
    <line x1={-120} y1={-240} x2={120} y2={-240} strokeWidth={5} />
    <line x1={-112} y1={-180} x2={112} y2={-180} strokeWidth={5} />
    <path d="M 130 -300 L 170 -360 H 200" strokeWidth={10} />
    <path d="M -105 -120 L -95 -40 H 95 L 105 -120" strokeWidth={8} />
    {[-80, 80].map((wx) => <circle key={wx} cx={wx} cy={-20} r={20} fill="#4E4540" stroke="none" />)}
  </g>
);
