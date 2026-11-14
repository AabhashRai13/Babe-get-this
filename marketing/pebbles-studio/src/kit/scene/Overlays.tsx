import React from 'react';
import { staticFile } from 'remotion';
import { CAPTION_FONT, HANZI_FONT, POP_FONT } from '../fonts';
import type { Framing } from '../framings';
import { H, SAFE, W, captionLayout } from '../framings';
import { HeartPath } from '../props/Effects';
import type { CaptionStyle, Who } from '../types';

/** A caption in one of the kit's styles, laid out (and sized) to stay inside the safe area and off faces. */
export const Caption: React.FC<{ text: string; style: CaptionStyle; framing: Framing; who?: Who }> = ({ text, style, framing, who }) => {
  if (!text) return null;
  const c = captionLayout(text, style, framing, who);
  const x = W / 2;
  if (style === 'hanzi') {
    return (
      <g textAnchor="middle">
        <text x={x} y={c.box.y + c.fontSize * 0.9} fontFamily={HANZI_FONT} fontWeight={700} fontSize={c.fontSize} fill="#D7262E"
          stroke="#FFF7EC" strokeWidth={c.fontSize * 0.06} paintOrder="stroke">{c.lines[0]}</text>
        <text x={x} y={c.box.y + c.fontSize * 1.38} fontFamily={CAPTION_FONT} fontWeight={600} fontSize={c.fontSize * 0.4} fill="#1B1B1B">{c.pinyin}</text>
      </g>
    );
  }
  const pop = style === 'pop' || style === 'popYellow';
  const lh = c.fontSize * (pop ? 1.04 : 1.18);
  return (
    <g fontFamily={pop ? POP_FONT : CAPTION_FONT} fontWeight={pop ? 400 : style === 'label' ? 700 : 600} fontSize={c.fontSize} textAnchor="middle"
      fill={style === 'popYellow' ? '#F7C928' : pop ? '#FFFFFF' : '#1B1B1B'}
      stroke={pop ? '#1A1410' : undefined} strokeWidth={pop ? c.fontSize * 0.09 : undefined} strokeLinejoin="round" paintOrder="stroke">
      {c.lines.map((l, i) => (
        <text key={i} x={x} y={c.box.y + i * lh + c.fontSize * (pop ? 0.95 : 0.92)}>{l}</text>
      ))}
    </g>
  );
};

export const SafeAreaGuide: React.FC = () => (
  <g fill="rgba(220,40,40,0.28)">
    <rect x={0} y={0} width={W} height={SAFE.top} />
    <rect x={0} y={H - SAFE.bottom} width={W} height={SAFE.bottom} />
    <rect x={W - SAFE.right.w} y={SAFE.right.y0} width={SAFE.right.w} height={SAFE.right.y1 - SAFE.right.y0} />
  </g>
);

const clamp01 = (v: number) => Math.max(0, Math.min(1, v));
const easeOutBack = (t: number) => {
  const x = clamp01(t);
  const c1 = 1.70158;
  const c3 = c1 + 1;
  return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2);
};

/** Icon pops in, then "Babe, Get This" and the subtitle; fades to paper at the very end. p = seconds into the card. */
export const EndCard: React.FC<{ p: number; dur: number; subtitle: string; tone?: string }> = ({ p, dur, subtitle, tone = '#E8D0B4' }) => {
  const sc = easeOutBack(p / 0.55);
  const size = 560;
  const a1 = clamp01((p - 0.45) / 0.35);
  const a2 = clamp01((p - 0.8) / 0.35);
  const fade = clamp01((p - (dur - 0.35)) / 0.35);
  return (
    <g>
      <g transform={`translate(540 820) scale(${sc}) rotate(${(1 - sc) * 0.3 * (180 / Math.PI)})`}>
        <rect x={-size / 2 + 10} y={-size / 2 + 34} width={size - 20} height={size} rx={70} fill="rgba(90,55,25,0.25)" filter="url(#soft-26)" />
        <image href={staticFile('icon.svg')} x={-size / 2} y={-size / 2} width={size} height={size} />
      </g>
      <text x={540} y={1250 + (1 - a1) * 20} opacity={a1} textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={800} fontSize={88} fill="#263A44">
        Babe, Get This
      </text>
      <text x={540} y={1340 + (1 - a2) * 16} opacity={a2} textAnchor="middle" fontFamily={CAPTION_FONT} fontWeight={500} fontSize={50} fill="#4C6673">
        {subtitle}
      </text>
      {Array.from({ length: 6 }, (_, k) => {
        const ht = p - 0.3 - k * 0.22;
        if (ht < 0) return null;
        const pr = ht / 2.2;
        return <HeartPath key={k} x={540 + Math.cos(k * 1.9) * 360} y={1100 - pr * 500} size={40 + (k % 3) * 14} fill={k % 2 ? '#E99A99' : '#90A4AE'} opacity={clamp01(1 - pr) * 0.8} />;
      })}
      {fade > 0 && <rect width={W} height={H} fill={tone} opacity={fade} />}
    </g>
  );
};
