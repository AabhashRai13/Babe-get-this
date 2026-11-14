import React, { useId } from 'react';
import { BLUSH, CHARACTERS, LINE, PUPIL } from '../geometry';
import type { CharacterDef } from '../geometry';
import type { Face as FaceT, Who } from '../types';
import { Effects } from '../props/Effects';

const clamp = (v: number, a: number, b: number) => Math.max(a, Math.min(b, v));

/** The face, in the character's art units. Ported from the prototype's drawFace/drawMouth. */
export const Face: React.FC<{ who: Who; face: FaceT; t: number; glasses?: boolean }> = ({ who, face, t, glasses }) => {
  const C = CHARACTERS[who];
  const gradUrl = `url(#pebble-grad-${who})`;
  const es = face.eyeScale;
  const lid = clamp(face.lid, 0, 1);
  const uid = `${who}${useId().replace(/[^a-zA-Z0-9]/g, '')}`;
  return (
    <g>
      {C.blush.map((b, i) => (
        <ellipse key={i} cx={b.x} cy={b.y} rx={C.blushRx} ry={C.blushRy} fill={BLUSH} opacity={face.blush} />
      ))}
      {C.eyes.map((e, i) => (
        <Eye key={i} uid={uid} C={C} i={i} e={e} es={es} lid={lid} face={face} gradUrl={gradUrl} />
      ))}
      {glasses && <Sunglasses C={C} />}
      <Brows C={C} face={face} />
      <Mouth C={C} face={face} uid={uid} />
      {face.effect && <Effects who={who} effect={face.effect} t={t} />}
    </g>
  );
};

const Eye: React.FC<{
  uid: string; C: CharacterDef; i: number; e: { x: number; y: number }; es: number; lid: number; face: FaceT; gradUrl: string;
}> = ({ uid, C, i, e, es, lid, face, gradUrl }) => {
  const side = i === 0 ? -1 : 1;
  const rx = C.eyeRx * es;
  const ry = C.eyeRy * es;
  const pr = C.pupilR * face.pupilScale;
  const maxOff = Math.min(rx, ry) - pr - 3;
  let lx = face.look.x;
  let ly = face.look.y;
  const m = Math.hypot(lx, ly);
  if (m > maxOff) { lx *= maxOff / m; ly *= maxOff / m; }
  const px = e.x + lx;
  const py = e.y + ly;
  const k = pr / C.pupilR;
  const eyeClip = `${uid}-eye-${i}`;
  const lidClip = `${uid}-lid-${i}`;

  const ey = e.y - ry + 2 * ry * lid;
  const tilt = face.lidTilt;
  const yo = ey + tilt * ry * 0.22;
  const yi = ey - tilt * ry * 0.22;
  const xo = e.x + side * rx * 1.1;
  const xi = e.x - side * rx * 1.1;
  const cy = (yo + yi) / 2 + ry * 0.1;
  const edge = `M ${xo} ${yo} Q ${e.x} ${cy} ${xi} ${yi}`;

  let lashes: React.ReactNode = null;
  if (C.lashes) {
    let ly0: number;
    let lxo: number;
    if (lid > 0.86) {
      ly0 = e.y + ry * 0.15;
      lxo = e.x + side * rx * 0.78;
    } else {
      const kk = Math.max(lid, 0.22);
      ly0 = e.y - ry + 2 * ry * kk + tilt * ry * 0.22;
      const u = clamp((ly0 - e.y) / ry, -1, 1);
      lxo = e.x + side * rx * Math.sqrt(1 - u * u);
    }
    lashes = [0, 1, 2].map((j) => {
      const bx = lxo - side * j * 7;
      const by = ly0 - j * 7;
      const ang = -0.5 - j * 0.35;
      return <line key={j} x1={bx} y1={by} x2={bx + side * Math.cos(ang) * 15} y2={by + Math.sin(ang) * 15} stroke={LINE} strokeWidth={6} strokeLinecap="round" />;
    });
  }

  return (
    <g>
      <defs>
        <clipPath id={eyeClip}><ellipse cx={e.x} cy={e.y} rx={rx} ry={ry} /></clipPath>
        <clipPath id={lidClip}><ellipse cx={e.x} cy={e.y} rx={rx + 2.5} ry={ry + 2.5} /></clipPath>
      </defs>
      <ellipse cx={e.x} cy={e.y} rx={rx} ry={ry} fill="#fff" />
      <g clipPath={`url(#${eyeClip})`}>
        <circle cx={px} cy={py} r={pr} fill={PUPIL} />
        <circle cx={px + C.hlOff.x * k} cy={py + C.hlOff.y * k} r={C.hlR * k} fill="#fff" />
      </g>
      {lid > 0.01 && (
        <g clipPath={`url(#${lidClip})`}>
          <path d={`M ${xo} ${e.y - ry - 12} L ${xo} ${yo} Q ${e.x} ${cy} ${xi} ${yi} L ${xi} ${e.y - ry - 12} Z`} fill={gradUrl} />
          <path d={edge} stroke={LINE} strokeWidth={C.lidW * 2} strokeLinecap="round" fill="none" />
        </g>
      )}
      {lid > 0.86 && (
        <path
          d={`M ${e.x - rx * 0.78} ${e.y + ry * 0.15} Q ${e.x} ${e.y + ry * 0.15 + (face.happyEyes ? -ry * 0.55 : ry * 0.25)} ${e.x + rx * 0.78} ${e.y + ry * 0.15}`}
          stroke={LINE} strokeWidth={C.lidW * 1.4} strokeLinecap="round" fill="none"
        />
      )}
      {lashes}
    </g>
  );
};

/** Dark round sunglasses over both eyes (the "too cool" look). */
const Sunglasses: React.FC<{ C: CharacterDef }> = ({ C }) => {
  const [l, r] = C.eyes;
  const rx = C.eyeRx * 1.2;
  const ry = C.eyeRy * 0.82;
  return (
    <g>
      <path d={`M ${l.x + rx * 0.7} ${l.y - ry * 0.35} Q ${(l.x + r.x) / 2} ${l.y - ry * 0.75} ${r.x - rx * 0.7} ${r.y - ry * 0.35}`} stroke="#141414" strokeWidth={C.browW * 0.7} fill="none" strokeLinecap="round" />
      {[l, r].map((e, i) => (
        <g key={i}>
          <rect x={e.x - rx} y={e.y - ry} width={rx * 2} height={ry * 1.9} rx={ry * 0.85} fill="#141414" />
          <path d={`M ${e.x - rx * 0.55} ${e.y - ry * 0.45} L ${e.x - rx * 0.1} ${e.y - ry * 0.45}`} stroke="rgba(255,255,255,0.35)" strokeWidth={C.browW * 0.5} strokeLinecap="round" />
        </g>
      ))}
      {[[l, -1], [r, 1]].map(([e, s]) => {
        const p = e as { x: number; y: number };
        const side = s as number;
        return <line key={side} x1={p.x + side * rx} y1={p.y - ry * 0.4} x2={p.x + side * (rx + 40)} y2={p.y - ry * 0.6} stroke="#141414" strokeWidth={C.browW * 0.6} strokeLinecap="round" />;
      })}
    </g>
  );
};

const Brows: React.FC<{ C: CharacterDef; face: FaceT }> = ({ C, face }) => (
  <g>
    {C.brows.map((b, i) => {
      const innerSign = i === 0 ? 1 : -1;
      const xs = b.map((q) => q[0]);
      const bcx = (Math.min(...xs) + Math.max(...xs)) / 2;
      const bhw = (Math.max(...xs) - Math.min(...xs)) / 2;
      const lift = face.brow[i] + (face.eyeScale - 1) * C.eyeRy * 1.2;
      const p = b.map((q) => {
        const u = ((q[0] - bcx) / bhw) * innerSign;
        return [q[0], q[1] - lift - face.browTilt * u * 12] as const;
      });
      return (
        <path key={i} d={`M ${p[0][0]} ${p[0][1]} C ${p[1][0]} ${p[1][1]} ${p[2][0]} ${p[2][1]} ${p[3][0]} ${p[3][1]}`}
          stroke={LINE} strokeWidth={C.browW} strokeLinecap="round" fill="none" />
      );
    })}
  </g>
);

const Mouth: React.FC<{ C: CharacterDef; face: FaceT; uid: string }> = ({ C, face, uid }) => {
  const M = C.mouth;
  const open = clamp(face.open, 0, 1);
  const hw = M.hw * (1 - open * 0.22) * face.mouthWidth;
  const x0 = M.x - hw;
  const x1 = M.x + hw;
  const y = M.y;
  const sag = M.sag * face.smile;
  const clipId = `${uid}-mouth`;

  if (face.mouthShape === 'o') {
    const oy = y + sag * 0.3 + 6;
    const ry = Math.max(M.hw * 0.32, open * M.hw * 0.95);
    const rx = Math.max(M.hw * 0.3, hw * 0.75);
    return (
      <g>
        <defs><clipPath id={clipId}><ellipse cx={M.x} cy={oy} rx={rx} ry={ry} /></clipPath></defs>
        <ellipse cx={M.x} cy={oy} rx={rx} ry={ry} fill={LINE} />
        <ellipse cx={M.x} cy={oy + ry * 0.75} rx={rx * 0.7} ry={ry * 0.45} fill={BLUSH} clipPath={`url(#${clipId})`} />
      </g>
    );
  }

  if (open < 0.06) {
    if (face.mouthShape === 'wobble') {
      const pts: string[] = [];
      for (let k = 0; k <= 16; k++) {
        const u = k / 16;
        const x = x0 + (x1 - x0) * u;
        const yy = y + sag * 0.6 * Math.sin(u * Math.PI) + Math.sin(u * Math.PI * 4) * 4;
        pts.push(`${k === 0 ? 'M' : 'L'} ${x} ${yy}`);
      }
      return <path d={pts.join(' ')} stroke={LINE} strokeWidth={M.w} strokeLinecap="round" strokeLinejoin="round" fill="none" />;
    }
    return (
      <path d={`M ${x0} ${y} C ${M.x - hw * 0.45} ${y + sag} ${M.x + hw * 0.45} ${y + sag} ${x1} ${y}`}
        stroke={LINE} strokeWidth={M.w} strokeLinecap="round" fill="none" />
    );
  }

  const top = y + sag * 0.45 - open * 4;
  const bot = y + Math.max(sag, 4) + open * M.hw * 1.45;
  const d = `M ${x0} ${y} C ${M.x - hw * 0.45} ${top} ${M.x + hw * 0.45} ${top} ${x1} ${y} C ${M.x + hw * 0.55} ${bot} ${M.x - hw * 0.55} ${bot} ${x0} ${y} Z`;
  return (
    <g>
      <defs><clipPath id={clipId}><path d={d} /></clipPath></defs>
      <path d={d} fill={LINE} stroke={LINE} strokeWidth={M.w * 0.7} strokeLinejoin="round" />
      <g clipPath={`url(#${clipId})`}>
        {face.mouthShape === 'grin' && <rect x={x0} y={Math.min(top, y) - 4} width={x1 - x0} height={(bot - y) * 0.32 + 6} fill="#fff" />}
        <ellipse cx={M.x} cy={bot - 2} rx={hw * 0.6} ry={open * M.hw * 0.42 + 4} fill={BLUSH} />
      </g>
    </g>
  );
};
