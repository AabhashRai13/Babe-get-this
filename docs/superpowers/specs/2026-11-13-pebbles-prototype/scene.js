// Draws one frame of the Pebbles podcast clip onto a 1080x1920 canvas.
// Pure function of time: renderFrame(i) -> JPEG data URL.
(() => {
const W = 1080, H = 1920;
const cv = document.createElement('canvas'); cv.width = W; cv.height = H;
document.body.appendChild(cv);
const ctx = cv.getContext('2d');
const TL = window.TL;
const FPS = TL.fps;

const LINE = '#263A44', PUPIL = '#071923', BLUSH = '#E99A99', CREAM = '#F5F2EB';
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const easeOut = (t) => 1 - Math.pow(1 - clamp(t, 0, 1), 3);
const easeOutBack = (t) => { t = clamp(t, 0, 1); const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); };
const wob = (x) => Math.sin(x * 1.7) * 0.5 + Math.sin(x * 2.9 + 1.3) * 0.3 + Math.sin(x * 4.3 + 2.1) * 0.2;

// ---------- characters (art units, origin = body centre) ----------
// Mrs Pebbles is tilted ~8deg in the icon; L() maps icon coords into her upright frame.
const A = 0.1393, CA = Math.cos(A), SA = Math.sin(A);
const WC = { x: 685.4, y: 487.5 };
const L = (x, y) => { const dx = x - WC.x, dy = y - WC.y; return { x: dx * CA - dy * SA, y: dx * SA + dy * CA }; };
const Lv = (x, y) => ({ x: x * CA - y * SA, y: x * SA + y * CA });
const Lp = (x, y) => { const p = L(x, y); return [p.x, p.y]; };
const q2c = (p0, q, p2) => [p0, [p0[0] + 2 / 3 * (q[0] - p0[0]), p0[1] + 2 / 3 * (q[1] - p0[1])], [p2[0] + 2 / 3 * (q[0] - p2[0]), p2[1] + 2 / 3 * (q[1] - p2[1])], p2];

const MR = {
  outer: { x: -4.3, y: -8.1, w: 450.3, h: 647.6 }, inner: { w: 403.3, h: 600.5 },
  grad: ['#6E8996', '#4C6673'],
  eyes: [{ x: -69.6, y: -99.5 }, { x: 61.5, y: -99.5 }], eyeRx: 54.9, eyeRy: 56.6,
  pupilR: 23.2, hlR: 8.9, hlOff: { x: 8.7, y: -8.7 },
  blush: [{ x: -88.3, y: -37.2 }, { x: 71.7, y: -39.5 }], blushRx: 28.6, blushRy: 16.5,
  brows: [[[-121.4, -191.6], [-88.6, -211.6], [-51.4, -211.6], [-21.4, -193.8]], [[12.9, -193.8], [42.9, -211.6], [82.9, -209.4], [107.2, -191.6]]],
  browW: 15.5,
  mouth: { x: -2, y: -7.7, hw: 40.5, sag: 22, w: 15.5 },
  feet: [{ x: -99.6, y: 300, rx: 70.5, ry: 37.3 }, { x: 115.1, y: 300, rx: 70.5, ry: 37.3 }], footColor: '#415967',
  armColor: '#607D8B', armRx: 40, armRy: 78, lashes: false, lidW: 7,
  phones: { cx: -4, cy: -95, r: 236, cupW: 70, cupH: 156, band: '#2C3A41', cup: '#2F3D44', cushion: '#4B5B63' },
  bottom: 300,
};
const MRS = {
  outer: { ...L(688.4, 491.3), w: 360.2, h: 544.6 }, inner: { w: 317.2, h: 497.8 },
  grad: ['#A7B8C0', '#80959F'],
  eyes: [L(613.85, 427.8), L(717.2, 413.2)], eyeRx: 43.3, eyeRy: 43.3,
  pupilR: 18.4, hlR: 7, hlOff: Lv(4.95, -9),
  blush: [L(598.1, 476.5), L(747.2, 455.0)], blushRx: 31.4, blushRy: 17.6,
  brows: [q2c(Lp(565.2, 369.8), Lp(601.35, 333.4), Lp(642.1, 356.6)), q2c(Lp(668.6, 352.8), Lp(700.8, 318.15), Lp(741.8, 344.9))],
  browW: 11,
  mouth: { x: -10.7, y: 9.65, hw: 37.5, sag: 14.2, w: 11 },
  feet: [{ x: -78, y: 249, rx: 60, ry: 32 }, { x: 60, y: 249, rx: 60, ry: 32 }], footColor: '#728992',
  armColor: '#90A4AE', armRx: 34, armRy: 64, lashes: true, lidW: 6,
  phones: { cx: -6, cy: -62, r: 192, cupW: 58, cupH: 126, band: '#4E7772', cup: '#5F8B85', cushion: '#4A6F6A' },
  bottom: 249,
};

function capsule(c, cx, cy, w, h) { c.beginPath(); c.roundRect(cx - w / 2, cy - h / 2, w, h, w / 2); }

// layers: Set of 'body','feet','arms','phones'
function drawPebble(c, C, st, layers) {
  c.save();
  c.translate(st.x, st.y); c.scale(st.s, st.s);
  const sq = st.sq || 0;
  c.translate(0, C.bottom); c.rotate(st.sway || 0); c.scale(1 - sq * 0.5, 1 + sq); c.translate(0, -C.bottom);
  c.translate(0, st.dy || 0);
  const g = c.createLinearGradient(0, -C.inner.h / 2, 0, C.inner.h / 2);
  g.addColorStop(0, C.grad[0]); g.addColorStop(1, C.grad[1]);
  if (layers.has('body')) {
    capsule(c, C.outer.x, C.outer.y, C.outer.w, C.outer.h); c.fillStyle = CREAM; c.fill();
    capsule(c, 0, 0, C.inner.w, C.inner.h); c.fillStyle = g; c.fill();
    drawFace(c, C, st, g);
  }
  if (layers.has('feet')) for (const f of C.feet) { c.beginPath(); c.ellipse(f.x, f.y, f.rx, f.ry, 0, 0, Math.PI * 2); c.fillStyle = C.footColor; c.fill(); }
  if (layers.has('arms')) drawArms(c, C, st);
  if (layers.has('phones')) drawPhones(c, C);
  c.restore();
}

function drawArms(c, C, st) {
  for (let i = 0; i < 2; i++) {
    const side = i === 0 ? -1 : 1;
    const a = st.arms[i];
    c.save(); c.translate(a.x, a.y); c.rotate(a.rot);
    if (a.stick) {
      c.save(); c.translate(0, C.armRy * 1.25); c.rotate(side * (a.stickRot ?? 1.0));
      c.strokeStyle = '#B98D5E'; c.lineWidth = 13; c.lineCap = 'round';
      c.beginPath(); c.moveTo(0, -10); c.lineTo(0, 175); c.stroke();
      c.strokeStyle = '#E9D5B5'; c.lineWidth = 15; c.beginPath(); c.moveTo(0, 168); c.lineTo(0, 178); c.stroke();
      c.restore();
    }
    c.beginPath(); c.ellipse(0, C.armRy * 0.6, C.armRx, C.armRy, 0, 0, Math.PI * 2);
    c.fillStyle = C.armColor; c.fill();
    c.restore();
  }
}

function drawPhones(c, C) {
  const P = C.phones;
  c.lineCap = 'round';
  c.strokeStyle = P.band; c.lineWidth = 26;
  c.beginPath(); c.arc(P.cx, P.cy, P.r, Math.PI * 1.07, Math.PI * 1.93); c.stroke();
  c.strokeStyle = 'rgba(255,255,255,0.16)'; c.lineWidth = 6;
  c.beginPath(); c.arc(P.cx, P.cy, P.r + 5, Math.PI * 1.22, Math.PI * 1.45); c.stroke();
  for (const s of [-1, 1]) {
    const x = P.cx + s * P.r, y = P.cy;
    c.fillStyle = P.cushion;
    c.beginPath(); c.roundRect(x - s * P.cupW * 0.38 - P.cupW * 0.3, y - P.cupH * 0.44, P.cupW * 0.6, P.cupH * 0.88, P.cupW * 0.3); c.fill();
    c.fillStyle = P.cup;
    c.beginPath(); c.roundRect(x + s * P.cupW * 0.12 - P.cupW / 2, y - P.cupH / 2, P.cupW, P.cupH, P.cupW / 2.1); c.fill();
    c.strokeStyle = 'rgba(255,255,255,0.18)'; c.lineWidth = 5;
    c.beginPath(); c.moveTo(x + s * P.cupW * 0.12 + s * P.cupW * 0.22, y - P.cupH * 0.28); c.lineTo(x + s * P.cupW * 0.12 + s * P.cupW * 0.22, y + P.cupH * 0.05); c.stroke();
  }
}

function drawFace(c, C, st, g) {
  const es = st.eyeScale || 1;
  c.fillStyle = BLUSH;
  for (const b of C.blush) { c.beginPath(); c.ellipse(b.x, b.y, C.blushRx, C.blushRy, 0, 0, Math.PI * 2); c.fill(); }

  const lid = clamp(st.lid ?? 0, 0, 1);
  C.eyes.forEach((e, i) => {
    const side = i === 0 ? -1 : 1;
    const rx = C.eyeRx * es, ry = C.eyeRy * es;
    c.save();
    c.beginPath(); c.ellipse(e.x, e.y, rx, ry, 0, 0, Math.PI * 2); c.fillStyle = '#fff'; c.fill();
    c.clip();
    const pr = C.pupilR * (st.pupilScale || 1);
    const maxOff = Math.min(rx, ry) - pr - 3;
    let lx = st.look?.x || 0, ly = st.look?.y || 0;
    const m = Math.hypot(lx, ly); if (m > maxOff) { lx *= maxOff / m; ly *= maxOff / m; }
    const px = e.x + lx, py = e.y + ly, k = pr / C.pupilR;
    c.beginPath(); c.arc(px, py, pr, 0, Math.PI * 2); c.fillStyle = PUPIL; c.fill();
    c.beginPath(); c.arc(px + C.hlOff.x * k, py + C.hlOff.y * k, C.hlR * k, 0, Math.PI * 2); c.fillStyle = '#fff'; c.fill();
    c.restore();
    let yo = 0;
    if (lid > 0.01) {
      c.save();
      c.beginPath(); c.ellipse(e.x, e.y, rx + 2.5, ry + 2.5, 0, 0, Math.PI * 2); c.clip();
      const ey = e.y - ry + 2 * ry * lid;
      const tilt = (st.lidTilt || 0);
      yo = ey + tilt * ry * 0.22; const yi = ey - tilt * ry * 0.22;
      const xo = e.x + side * rx * 1.1, xi = e.x - side * rx * 1.1;
      const cy = (yo + yi) / 2 + ry * 0.1;
      c.beginPath(); c.moveTo(xo, e.y - ry - 12); c.lineTo(xo, yo); c.quadraticCurveTo(e.x, cy, xi, yi); c.lineTo(xi, e.y - ry - 12); c.closePath();
      c.fillStyle = g; c.fill();
      c.strokeStyle = LINE; c.lineWidth = C.lidW * 2; c.lineCap = 'round';
      c.beginPath(); c.moveTo(xo, yo); c.quadraticCurveTo(e.x, cy, xi, yi); c.stroke();
      c.restore();
    }
    c.strokeStyle = LINE; c.lineCap = 'round';
    if (lid > 0.86) {
      c.lineWidth = C.lidW * 1.4;
      const by = e.y + ry * 0.15, bend = st.happy ? -ry * 0.55 : ry * 0.25;
      c.beginPath(); c.moveTo(e.x - rx * 0.78, by); c.quadraticCurveTo(e.x, by + bend, e.x + rx * 0.78, by); c.stroke();
    }
    if (C.lashes) {
      let ly0, lxo;
      if (lid > 0.86) { ly0 = e.y + ry * 0.15; lxo = e.x + side * rx * 0.78; }
      else {
        const kk = Math.max(lid, 0.22);
        ly0 = e.y - ry + 2 * ry * kk + (st.lidTilt || 0) * ry * 0.22;
        const u = clamp((ly0 - e.y) / ry, -1, 1);
        lxo = e.x + side * rx * Math.sqrt(1 - u * u);
      }
      c.lineWidth = 6;
      for (let j = 0; j < 3; j++) {
        const bx = lxo - side * j * 7, by = ly0 - j * 7;
        const ang = -0.5 - j * 0.35;
        c.beginPath(); c.moveTo(bx, by); c.lineTo(bx + side * Math.cos(ang) * 15, by + Math.sin(ang) * 15); c.stroke();
      }
    }
  });

  c.strokeStyle = LINE; c.lineWidth = C.browW; c.lineCap = 'round';
  C.brows.forEach((b, i) => {
    const innerSign = i === 0 ? 1 : -1;
    const xs = b.map((p) => p[0]);
    const bcx = (Math.min(...xs) + Math.max(...xs)) / 2, bhw = (Math.max(...xs) - Math.min(...xs)) / 2;
    const lift = (st.brow || 0) + (st.browLR ? st.browLR[i] : 0) + (es - 1) * C.eyeRy * 1.2;
    const p = b.map((q) => { const u = (q[0] - bcx) / bhw * innerSign; return [q[0], q[1] - lift - (st.browTilt || 0) * u * 12]; });
    c.beginPath(); c.moveTo(...p[0]); c.bezierCurveTo(...p[1], ...p[2], ...p[3]); c.stroke();
  });
  drawMouth(c, C, st);
}

function drawMouth(c, C, st) {
  const M = C.mouth;
  const open = clamp(st.open || 0, 0, 1);
  const sm = st.smile ?? 1;
  const hw = M.hw * (1 - open * 0.22) * (st.mouthW || 1);
  const x0 = M.x - hw, x1 = M.x + hw, y = M.y;
  const sag = M.sag * sm;
  c.strokeStyle = LINE; c.lineCap = 'round'; c.lineJoin = 'round'; c.lineWidth = M.w;
  if (open < 0.06) {
    c.beginPath(); c.moveTo(x0, y); c.bezierCurveTo(M.x - hw * 0.45, y + sag, M.x + hw * 0.45, y + sag, x1, y); c.stroke();
    return;
  }
  const top = y + sag * 0.45 - open * 4;
  const bot = y + Math.max(sag, 4) + open * M.hw * 1.45;
  c.beginPath(); c.moveTo(x0, y);
  c.bezierCurveTo(M.x - hw * 0.45, top, M.x + hw * 0.45, top, x1, y);
  c.bezierCurveTo(M.x + hw * 0.55, bot, M.x - hw * 0.55, bot, x0, y);
  c.closePath(); c.fillStyle = LINE; c.fill(); c.lineWidth = M.w * 0.7; c.stroke();
  c.save(); c.clip();
  c.beginPath(); c.ellipse(M.x, bot - 2, hw * 0.6, open * M.hw * 0.42 + 4, 0, 0, Math.PI * 2); c.fillStyle = BLUSH; c.fill();
  c.restore();
}

// ---------- props ----------
const CHAIRS = {
  M: { base: '#BCCAB8', dark: '#A3B39F', light: '#CDD8C9' },
  W: { base: '#F1E6DA', dark: '#D3BFA8', light: '#F8F1E8' },
};
function chairBack(c, x, y, s, col) {
  c.save(); c.translate(x, y); c.scale(s, s);
  c.save(); c.filter = 'blur(16px)'; c.fillStyle = 'rgba(110,70,35,0.22)';
  c.beginPath(); c.ellipse(0, 430, 450, 46, 0, 0, Math.PI * 2); c.fill(); c.restore();
  c.fillStyle = col.dark; c.beginPath(); c.roundRect(-340, -150, 680, 570, 84); c.fill();
  c.fillStyle = col.base; c.beginPath(); c.roundRect(-308, -122, 616, 470, 70); c.fill();
  c.fillStyle = col.light; c.beginPath(); c.roundRect(-318, 210, 636, 130, 50); c.fill();
  c.restore();
}
function chairFront(c, x, y, s, col) {
  c.save(); c.translate(x, y); c.scale(s, s);
  c.fillStyle = col.base; c.beginPath(); c.roundRect(-350, 300, 700, 122, 36); c.fill();
  c.fillStyle = 'rgba(80,60,40,0.08)'; c.beginPath(); c.roundRect(-350, 380, 700, 42, [0, 0, 36, 36]); c.fill();
  for (const sd of [-1, 1]) {
    const x0 = sd < 0 ? -430 : 290;
    c.fillStyle = col.dark; c.beginPath(); c.roundRect(x0, 40, 140, 382, 62); c.fill();
    c.fillStyle = col.base; c.beginPath(); c.roundRect(x0 + 6, 46, 128, 128, 58); c.fill();
  }
  c.restore();
}
function popMic(c, x, y, s, floorY) {
  c.save();
  c.strokeStyle = '#1E2428'; c.lineCap = 'round';
  c.lineWidth = 12 * s;
  c.beginPath(); c.moveTo(x + 40 * s, floorY); c.lineTo(x + 40 * s, y + 130 * s); c.lineTo(x + 70 * s, y + 30 * s); c.stroke();
  c.fillStyle = '#1E2428'; c.beginPath(); c.ellipse(x + 40 * s, floorY, 70 * s, 15 * s, 0, 0, Math.PI * 2); c.fill();
  c.lineWidth = 5 * s; c.beginPath(); c.moveTo(x + 40 * s, floorY - 8 * s); c.bezierCurveTo(x + 110 * s, floorY - 20 * s, x + 130 * s, floorY + 30 * s, x + 200 * s, floorY + 18 * s); c.stroke();
  c.save(); c.translate(x + 78 * s, y + 10 * s); c.rotate(-0.35);
  c.fillStyle = '#2A3136'; c.beginPath(); c.roundRect(-24 * s, -60 * s, 48 * s, 110 * s, 24 * s); c.fill();
  c.restore();
  c.fillStyle = 'rgba(20,24,28,0.93)'; c.beginPath(); c.arc(x, y, 62 * s, 0, Math.PI * 2); c.fill();
  c.strokeStyle = '#3A444A'; c.lineWidth = 4 * s; c.stroke();
  c.strokeStyle = 'rgba(255,255,255,0.12)'; c.lineWidth = 5 * s; c.beginPath(); c.arc(x, y, 48 * s, Math.PI * 1.1, Math.PI * 1.45); c.stroke();
  c.restore();
}
function standMic(c, x, y, s, floorY) {
  c.save();
  c.strokeStyle = '#22292D'; c.lineCap = 'round'; c.lineWidth = 11 * s;
  c.beginPath(); c.moveTo(x, floorY); c.lineTo(x, y + 40 * s); c.stroke();
  c.fillStyle = '#22292D'; c.beginPath(); c.ellipse(x, floorY, 66 * s, 14 * s, 0, 0, Math.PI * 2); c.fill();
  c.lineWidth = 5 * s; c.beginPath(); c.moveTo(x, floorY - 6 * s); c.bezierCurveTo(x - 60 * s, floorY - 10 * s, x - 90 * s, floorY + 30 * s, x - 170 * s, floorY + 20 * s); c.stroke();
  c.fillStyle = '#2B3236'; c.beginPath(); c.roundRect(x - 22 * s, y - 70 * s, 44 * s, 118 * s, 22 * s); c.fill();
  c.strokeStyle = 'rgba(255,255,255,0.14)'; c.lineWidth = 3 * s;
  for (let k = 0; k < 5; k++) { c.beginPath(); c.moveTo(x - 15 * s, y - 52 * s + k * 10 * s); c.lineTo(x + 15 * s, y - 52 * s + k * 10 * s); c.stroke(); }
  c.fillStyle = '#5A6266'; c.fillRect(x - 22 * s, y + 4 * s, 44 * s, 8 * s);
  c.restore();
}
function heart(c, x, y, size, color, alpha) {
  c.save(); c.globalAlpha = alpha; c.translate(x, y); c.scale(size / 100, size / 100);
  c.beginPath(); c.moveTo(0, 30);
  c.bezierCurveTo(-60, -10, -40, -70, 0, -38);
  c.bezierCurveTo(40, -70, 60, -10, 0, 30);
  c.fillStyle = color; c.fill(); c.restore();
}

// ---------- drums ----------
const HITS = TL.hits.slice().sort((a, b) => a.t - b.t);
function pulse(t, kinds, k = 16) {
  let last = -1;
  for (const h of HITS) { if (h.t > t) break; if (kinds.includes(h.d)) last = h.t; }
  return last < 0 ? 0 : Math.exp(-(t - last) * k);
}
function drum(c, x, y, w, h, shell, head) {
  c.fillStyle = shell; c.beginPath(); c.roundRect(x - w / 2, y, w, h, [0, 0, 14, 14]); c.fill();
  c.fillStyle = 'rgba(0,0,0,0.12)'; c.fillRect(x - w / 2, y + h * 0.15, w, 5); c.fillRect(x - w / 2, y + h * 0.8, w, 5);
  c.fillStyle = head; c.beginPath(); c.ellipse(x, y, w / 2, w * 0.13, 0, 0, Math.PI * 2); c.fill();
  c.strokeStyle = '#8D7458'; c.lineWidth = 4; c.stroke();
}
function cymbal(c, x, y, r, rot, standTo) {
  c.strokeStyle = '#3B3F42'; c.lineWidth = 6; c.beginPath(); c.moveTo(x, y); c.lineTo(x, standTo); c.stroke();
  c.save(); c.translate(x, y); c.rotate(rot);
  c.fillStyle = '#CDAE72'; c.beginPath(); c.ellipse(0, 0, r, r * 0.17, 0, 0, Math.PI * 2); c.fill();
  c.strokeStyle = '#A88A52'; c.lineWidth = 3; c.stroke();
  c.fillStyle = '#B39459'; c.beginPath(); c.ellipse(0, -2, r * 0.2, r * 0.06, 0, 0, Math.PI * 2); c.fill();
  c.restore();
}
function drumKitBack(c, x, y, s, t, on) {
  c.save(); c.translate(x, y); c.scale(s, s);
  const crash = on ? pulse(t, ['crash'], 5) : 0, hat = on ? pulse(t, ['hat', 'ohat'], 20) : 0;
  cymbal(c, -300, -230, 120, -0.18 + Math.sin(t * 30) * 0.08 * crash, 250);
  cymbal(c, 300, -250, 112, 0.15 + Math.sin(t * 26) * 0.06 * crash, 250);
  cymbal(c, -360, -40 + hat * 6, 92, -0.05, 260);
  c.restore();
}
function drumKitFront(c, x, y, s, t, on) {
  c.save(); c.translate(x, y); c.scale(s, s);
  c.save(); c.filter = 'blur(14px)'; c.fillStyle = 'rgba(110,70,35,0.2)'; c.beginPath(); c.ellipse(0, 255, 460, 40, 0, 0, Math.PI * 2); c.fill(); c.restore();
  const kick = on ? pulse(t, ['kick'], 14) : 0, sn = on ? pulse(t, ['snare'], 16) : 0, tom = on ? pulse(t, ['tomHi', 'tomLo'], 14) : 0;
  drum(c, -110, -110 + tom * 4, 150, 90, '#C69C6D', '#F1EADC');
  drum(c, 105, -115 + tom * 4, 160, 95, '#C69C6D', '#F1EADC');
  drum(c, -255, 10 + sn * 5, 170, 70, '#D9D3C7', '#F4EFE6');
  drum(c, 270, 20, 180, 170, '#C69C6D', '#F1EADC');
  // bass drum
  const r = 190 * (1 + kick * 0.025);
  c.fillStyle = '#A9794B'; c.beginPath(); c.arc(0, 60, r, 0, Math.PI * 2); c.fill();
  c.fillStyle = '#F1EADC'; c.beginPath(); c.arc(0, 60, r - 18, 0, Math.PI * 2); c.fill();
  c.strokeStyle = 'rgba(0,0,0,0.12)'; c.lineWidth = 4; c.beginPath(); c.arc(0, 60, r - 22, 0, Math.PI * 2); c.stroke();
  heart(c, 0, 66, 120, '#90A4AE', 1);
  c.fillStyle = '#8D7458';
  for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2; c.beginPath(); c.arc(Math.cos(a) * (r - 9), 60 + Math.sin(a) * (r - 9), 6, 0, Math.PI * 2); c.fill(); }
  c.restore();
}

// ---------- background & text ----------
const paper = document.createElement('canvas'); paper.width = W; paper.height = H;
(function buildPaper() {
  const p = paper.getContext('2d');
  p.fillStyle = '#E8D0B4'; p.fillRect(0, 0, W, H);
  let s = 11; const r = () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
  for (let i = 0; i < 70; i++) {
    const x = r() * W, y = r() * H, rad = 80 + r() * 260;
    const g = p.createRadialGradient(x, y, 0, x, y, rad);
    const dark = r() < 0.5;
    g.addColorStop(0, dark ? 'rgba(150,100,60,0.025)' : 'rgba(255,245,230,0.04)'); g.addColorStop(1, 'rgba(0,0,0,0)');
    p.fillStyle = g; p.fillRect(x - rad, y - rad, rad * 2, rad * 2);
  }
  for (let i = 0; i < 26000; i++) {
    p.fillStyle = r() < 0.5 ? `rgba(120,80,40,${0.03 + r() * 0.05})` : `rgba(255,250,240,${0.04 + r() * 0.06})`;
    p.fillRect(r() * W, r() * H, 1 + r() * 2, 1 + r() * 2);
  }
  const v = p.createRadialGradient(W / 2, H * 0.5, H * 0.3, W / 2, H * 0.5, H * 0.75);
  v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(90,55,25,0.16)');
  p.fillStyle = v; p.fillRect(0, 0, W, H);
})();

function caption(c, text, y, maxW, size = 52) {
  if (!text) return;
  c.save();
  c.font = `600 ${size}px "Avenir Next", "Helvetica Neue", sans-serif`;
  c.fillStyle = '#1B1B1B'; c.textAlign = 'center'; c.textBaseline = 'top';
  const words = text.split(' '); const lines = []; let cur = '';
  for (const w of words) { const tryL = cur ? cur + ' ' + w : w; if (c.measureText(tryL).width > maxW && cur) { lines.push(cur); cur = w; } else cur = tryL; }
  if (cur) lines.push(cur);
  const lh = size * 1.18;
  lines.forEach((l, i) => c.fillText(l, W / 2, y + i * lh));
  c.restore();
}

// ---------- animation helpers ----------
const MOODS = {
  smug: { lid: 0.46, smile: 0.75, browLR: [0, 12], look: { x: 0, y: 6 } },
  stare: { lid: 0.52, smile: 0.12, brow: -3, browTilt: -0.35, look: { x: 0, y: 3 } },
  ok: { lid: 0.42, smile: 0.35, look: { x: 3, y: 5 } },
  worried: { lid: 0.1, smile: -0.3, brow: 10, browTilt: 1, look: { x: -9, y: 0 } },
  shock: { lid: 0, smile: -0.5, brow: 18, browTilt: 0.5, eyeScale: 1.1, pupilScale: 0.7, look: { x: 0, y: 0 }, open: 0.3, mouthW: 0.5 },
  smirk: { lid: 0.48, smile: 0.95, browLR: [14, -2], look: { x: 7, y: 3 } },
  sing: { lid: 0.5, smile: 0.8, brow: 5, look: { x: -6, y: -3 } },
  love: { lid: 1, happy: true, smile: 1.25, brow: 6 },
};
function blink(t, seed) {
  const period = 3.1 + seed * 0.9, ph = (t + seed * 1.37) % period;
  return ph < 0.2 ? Math.sin((ph / 0.2) * Math.PI) : 0;
}
function mouthAt(who, t) {
  const arr = TL.mouth[who]; const f = t * FPS; const i = Math.floor(f);
  const a = arr[i] || 0, b = arr[i + 1] || 0;
  const prev = arr[i - 1] || 0;
  return Math.max(lerp(a, b, f - i), prev * 0.55);
}
function talkEnergy(who, t) { let s = 0; for (let k = 0; k < 12; k++) s += mouthAt(who, t - k / FPS); return s / 12; }

function stateFor(C, who, moodName, t, shotT, opts = {}) {
  const m = MOODS[moodName] || MOODS.stare;
  const seed = who === 'M' ? 0.3 : 1.1;
  const open = Math.max(m.open || 0, opts.silent ? 0 : mouthAt(who, t));
  const breathe = Math.sin(t * 2.4 + seed) * 0.01;
  const settle = Math.exp(-shotT * 9) * Math.sin(shotT * 22) * 0.03;
  let lid = m.lid; if (!m.happy) lid = Math.max(lid, blink(t, seed));
  if (moodName === 'shock') lid = 0;
  const look = { x: (m.look?.x || 0) + wob(t * 0.7 + seed) * 2, y: (m.look?.y || 0) + wob(t * 0.5 + seed * 3) * 1.5 };
  if (opts.lookX !== undefined) look.x = opts.lookX;
  const st = {
    lid, happy: m.happy, smile: m.smile, brow: (m.brow || 0) + open * 4, browLR: m.browLR, browTilt: m.browTilt,
    eyeScale: m.eyeScale, pupilScale: m.pupilScale, look, open, mouthW: m.mouthW,
    sq: breathe + settle + open * 0.012, dy: -open * 7, sway: wob(t * 0.6 + seed) * 0.012,
  };
  if (moodName === 'shock') { st.sway += Math.sin(t * 60) * 0.004; }
  return st;
}

// ---------- scenes ----------
function sceneSingle(c, t, sh) {
  const who = sh.who, C = who === 'M' ? MR : MRS;
  const shotT = t - (sh.cutStart ?? sh.start);
  c.drawImage(paper, 0, 0);
  // camera
  let z = 1 + Math.min(0.04, 0.015 * shotT);
  if (sh.mood === 'shock') z = 1 + 0.16 * easeOut(shotT / 0.25);
  c.save();
  const sdy = TL.safe ? -260 : 0;
  c.translate(0, sdy);
  const fx = 540, fy = 1250;
  c.translate(fx, fy); c.scale(z, z); c.translate(-fx, -fy);
  const chairY = 1290, cs = 1.0;
  const s = who === 'M' ? 1.02 : 1.12;
  const st = stateFor(C, who, sh.mood, t, shotT, { silent: !sh.caption });
  st.x = 540; st.s = s; st.y = chairY + 330 * cs - C.bottom * s;
  const e = talkEnergy(who, t);
  const g = clamp(e * 2.2, 0, 1);
  const shoulderX = C.inner.w * 0.5 - 34, shoulderY = C.bottom * 0.1;
  st.arms = [
    { x: -shoulderX, y: shoulderY, rot: 0.95 + Math.sin(t * 3) * 0.02 },
    { x: shoulderX, y: shoulderY, rot: -0.95 - g * 0.55 - Math.sin(t * 2.3) * 0.02 },
  ];
  chairBack(c, 540, chairY, cs, CHAIRS[who]);
  drawPebble(c, C, st, new Set(['body']));
  chairFront(c, 540, chairY, cs, CHAIRS[who]);
  drawPebble(c, C, st, new Set(['feet', 'arms', 'phones']));
  if (who === 'M') popMic(c, 540 + 150, st.y + 95 * s, 1.0, 1745);
  else standMic(c, 540 - 22, st.y + 150 * s, 1.05, 1748);
  c.restore();
  caption(c, sh.caption, 770 + sdy, 900);
}

function sceneBand(c, t, sh, drumsOn) {
  const shotT = t - sh.start;
  c.drawImage(paper, 0, 0);
  const z = 1 + 0.02 * (shotT / (sh.end - sh.start));
  c.save(); c.translate(540, 1500); c.scale(z, z); c.translate(-540, -1500);
  if (TL.safe) { c.translate(0, -275); c.scale(0.86, 0.86); }
  // Mr on drums, back left
  const ms = 0.62, mx = 330, my = 1300;
  const beatPh = drumsOn ? pulse(t, ['kick'], 12) : 0;
  const mMood = drumsOn ? 'ok' : (sh.who === 'M' ? 'ok' : 'ok');
  const mst = stateFor(MR, 'M', mMood, t, shotT, { silent: sh.who !== 'M' || drumsOn, lookX: drumsOn ? 14 : 14 });
  mst.x = mx; mst.y = my; mst.s = ms; mst.dy += beatPh * 10; mst.sq += beatPh * 0.02;
  if (!drumsOn && sh.who === 'W') mst.smile = 0.6;
  const lV = drumsOn ? pulse(t, ['snare', 'hat', 'ohat'], 18) : 0;
  const rV = drumsOn ? pulse(t, ['crash', 'tomHi', 'tomLo', 'kick'], 16) : 0;
  mst.arms = [
    { x: -150, y: 40, rot: drumsOn ? 0.2 + (1 - lV) * 0.6 : 0.45, stick: true, stickRot: 0.75 },
    { x: 150, y: 40, rot: drumsOn ? -0.2 - (1 - rV) * 0.6 : -0.45, stick: true, stickRot: 0.75 },
  ];
  drumKitBack(c, 360, 1560, 0.9, t, drumsOn);
  drawPebble(c, MR, mst, new Set(['body', 'phones']));
  drumKitFront(c, 360, 1560, 0.9, t, drumsOn);
  drawPebble(c, MR, mst, new Set(['arms']));
  // Mrs front right at the mic
  const wMood = drumsOn ? 'sing' : (sh.who === 'W' ? sh.mood : 'stare');
  const wst = stateFor(MRS, 'W', wMood, t, shotT, { silent: sh.who !== 'W' && !drumsOn, lookX: !drumsOn && sh.who === 'M' ? -14 : undefined });
  wst.x = 800; wst.y = 1715; wst.s = 1.22;
  if (drumsOn) {
    const sway = Math.sin((t - sh.start) * Math.PI / TL.beat) * 0.035;
    wst.sway += sway; const bl = wst.lid > 0.55 ? wst.lid : 0; wst.lid = Math.max(0.28 + wst.open * 0.12, bl);
  }
  const we = talkEnergy('W', t);
  wst.arms = [
    { x: -MRS.inner.w * 0.5 + 34, y: 30, rot: 0.75 },
    { x: MRS.inner.w * 0.5 - 34, y: 30, rot: -0.75 - clamp(we * 2.5, 0, 1) * 0.6 },
  ];
  if (TL.safe) { c.save(); c.filter = 'blur(16px)'; c.fillStyle = 'rgba(110,70,35,0.22)'; c.beginPath(); c.ellipse(800, 2062, 300, 34, 0, 0, Math.PI * 2); c.fill(); c.restore(); }
  drawPebble(c, MRS, wst, new Set(['body', 'feet', 'arms', 'phones']));
  standMic(c, 690, 1795, 1.15, TL.safe ? 2066 : 2100);
  // hearts for the love beat
  if (!drumsOn && sh.who === 'W' && sh.mood === 'love') {
    for (let k = 0; k < 5; k++) {
      const ht = shotT - k * 0.18; if (ht < 0) continue;
      const pr = ht / 1.3;
      heart(c, 800 + Math.sin(k * 2.1) * 120 + Math.sin(ht * 4 + k) * 14, 1330 - pr * 380 - k * 10, 60 + k * 8, k % 2 ? '#E99A99' : '#F5F2EB', clamp(1 - pr, 0, 1) * clamp(ht * 6, 0, 1));
    }
  }
  c.restore();
  if (drumsOn) caption(c, sh.caption, 330, 640, 54);
  else caption(c, sh.caption, 560, 900);
}

let ICON = null;
function sceneEnd(c, t, sh) {
  const p = t - sh.start, dur = sh.end - sh.start;
  c.drawImage(paper, 0, 0);
  const sc = easeOutBack(p / 0.55);
  const size = 560;
  c.save(); c.translate(540, 820); c.scale(sc, sc); c.rotate((1 - sc) * 0.3);
  c.save(); c.filter = 'blur(26px)'; c.fillStyle = 'rgba(90,55,25,0.25)'; c.beginPath(); c.roundRect(-size / 2 + 10, -size / 2 + 34, size - 20, size, 70); c.fill(); c.restore();
  if (ICON) c.drawImage(ICON, -size / 2, -size / 2, size, size);
  c.restore();
  const a1 = clamp((p - 0.45) / 0.35, 0, 1), a2 = clamp((p - 0.8) / 0.35, 0, 1);
  c.save(); c.globalAlpha = a1; c.translate(0, (1 - a1) * 20);
  c.font = '700 88px "Avenir Next", "Helvetica Neue", sans-serif'; c.fillStyle = '#263A44'; c.textAlign = 'center';
  c.fillText('Babe, Get This', 540, 1250); c.restore();
  c.save(); c.globalAlpha = a2; c.translate(0, (1 - a2) * 16);
  c.font = '500 50px "Avenir Next", "Helvetica Neue", sans-serif'; c.fillStyle = '#4C6673'; c.textAlign = 'center';
  c.fillText(TL.endSub || 'Put it in the app.', 540, 1340); c.restore();
  for (let k = 0; k < 6; k++) {
    const ht = p - 0.3 - k * 0.22; if (ht < 0) continue; const pr = ht / 2.2;
    heart(c, 540 + Math.cos(k * 1.9) * 360, 1100 - pr * 500, 40 + (k % 3) * 14, k % 2 ? '#E99A99' : '#90A4AE', clamp(1 - pr, 0, 1) * 0.8);
  }
  const fadeOut = clamp((p - (dur - 0.35)) / 0.35, 0, 1);
  if (fadeOut > 0) { c.fillStyle = `rgba(232,208,180,${fadeOut})`; c.fillRect(0, 0, W, H); }
}

function shotAt(t) { for (const s of TL.shots) if (t >= s.start && t < s.end) return s; return TL.shots[TL.shots.length - 1]; }

window.renderFrame = (i) => {
  const t = i / FPS;
  const sh = shotAt(t);
  ctx.save();
  if (sh.scene === 'single') sceneSingle(ctx, t, sh);
  else if (sh.scene === 'band') sceneBand(ctx, t, sh, true);
  else if (sh.scene === 'duo') sceneBand(ctx, t, sh, false);
  else sceneEnd(ctx, t, sh);
  ctx.restore();
  return cv.toDataURL('image/jpeg', 0.93);
};
window.ready = (async () => {
  const img = new Image(); img.src = window.ICON_SRC; await img.decode(); ICON = img;
  await document.fonts.load('600 52px "Avenir Next"');
  return true;
})();
})();
