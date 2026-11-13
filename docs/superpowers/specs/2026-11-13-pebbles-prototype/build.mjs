// Builds audio/mix.wav and timeline.json from script.mjs.
// Voices come from macOS `say` (placeholders), drums are synthesized here.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { VOICES, DIALOGUE, BPM, VERSE, VERSE_CAPTION, TAG, END_CARD } from './script.mjs';

const DIR = path.dirname(new URL(import.meta.url).pathname);
const AUD = path.join(DIR, 'audio');
fs.mkdirSync(AUD, { recursive: true });
const SR = 44100;
const FPS = 30;

function tts(who, text, idx, rateOverride) {
  const v = VOICES[who];
  const aiff = path.join(AUD, `l${idx}.aiff`);
  const raw = path.join(AUD, `l${idx}.f32`);
  execFileSync('say', ['-v', v.voice, '-r', String(rateOverride ?? v.rate), '-o', aiff, text]);
  execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', aiff, '-ac', '1', '-ar', String(SR), '-f', 'f32le', raw]);
  const buf = fs.readFileSync(raw);
  let s = new Float32Array(buf.buffer, buf.byteOffset, buf.length / 4);
  // trim leading/trailing silence
  const thr = 0.01;
  let a = 0, b = s.length - 1;
  while (a < b && Math.abs(s[a]) < thr) a++;
  while (b > a && Math.abs(s[b]) < thr) b--;
  a = Math.max(0, a - 400); b = Math.min(s.length, b + 1200);
  return Float32Array.from(s.subarray(a, b));
}

// ---------- drum synthesis ----------
let seed = 7;
const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296) * 2 - 1;
function kick() {
  const n = Math.floor(SR * 0.35), out = new Float32Array(n);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const f = 48 + 110 * Math.exp(-t * 28);
    ph += (2 * Math.PI * f) / SR;
    out[i] = Math.sin(ph) * Math.exp(-t * 9) * 0.95 + rnd() * Math.exp(-t * 300) * 0.15;
  }
  return out;
}
function snare() {
  const n = Math.floor(SR * 0.25), out = new Float32Array(n);
  let lp = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR;
    const nz = rnd(); lp += 0.6 * (nz - lp);
    out[i] = (nz - lp) * Math.exp(-t * 18) * 0.55 + Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t * 25) * 0.35;
  }
  return out;
}
function hat(open) {
  const n = Math.floor(SR * (open ? 0.3 : 0.06)), out = new Float32Array(n);
  let prev = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR, nz = rnd();
    out[i] = (nz - prev) * Math.exp(-t * (open ? 10 : 70)) * 0.22; prev = nz;
  }
  return out;
}
function crash() {
  const n = Math.floor(SR * 1.6), out = new Float32Array(n);
  let prev = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR, nz = rnd();
    out[i] = (nz - prev) * Math.exp(-t * 2.6) * 0.3; prev = nz;
  }
  return out;
}
function tom(f0) {
  const n = Math.floor(SR * 0.3), out = new Float32Array(n);
  let ph = 0;
  for (let i = 0; i < n; i++) {
    const t = i / SR; ph += (2 * Math.PI * (f0 + 60 * Math.exp(-t * 20))) / SR;
    out[i] = Math.sin(ph) * Math.exp(-t * 11) * 0.6;
  }
  return out;
}
const DR = { kick: kick(), snare: snare(), hat: hat(false), ohat: hat(true), crash: crash(), tomHi: tom(180), tomLo: tom(110) };

// ---------- timeline ----------
const parts = []; // {t, samples, gain, who}
const shots = [];
const hits = [];
let cur = 0;
let idx = 0;
const LEAD = 0.1, TAIL = 0.3;

function addLine(l, scene) {
  if (l.hold) {
    shots.push({ start: cur, end: cur + l.hold, scene, who: l.who, mood: l.mood || 'stare', caption: '' });
    cur += l.hold;
    return;
  }
  const s = tts(l.who, l.say || l.text, idx++);
  const dur = s.length / SR;
  parts.push({ t: cur + LEAD, samples: s, gain: 1, who: l.who });
  const end = cur + LEAD + dur + TAIL;
  shots.push({ start: cur, end, scene, who: l.who, mood: l.mood || 'stare', caption: l.text });
  cur = end;
}

for (const l of DIALOGUE) addLine(l, 'single');

// finale
const beat = 60 / BPM, bar = beat * 4;
const fStart = cur;
const bars = VERSE.length + 1;
// pattern per bar in 8ths
for (let b = 0; b < bars; b++) {
  const t0 = fStart + b * bar;
  for (let e = 0; e < 8; e++) {
    const t = t0 + e * beat / 2;
    const isLast = b === bars - 1;
    if (e === 0 || e === 3 || e === 5) hits.push({ t, d: 'kick' });
    if (e === 2 || e === 6) hits.push({ t, d: 'snare' });
    if (isLast && e >= 6) { hits.push({ t, d: 'tomHi' }); hits.push({ t: t + beat / 4, d: 'tomLo' }); }
    else hits.push({ t, d: e === 7 && b % 2 === 1 ? 'ohat' : 'hat' });
  }
  if (b === 0) hits.push({ t: t0, d: 'crash' });
}
const fEnd = fStart + bars * bar;
hits.push({ t: fEnd, d: 'crash' });
hits.push({ t: fEnd, d: 'kick' });
VERSE.forEach((line, i) => {
  let s = tts('W', line, idx++, 190);
  // squeeze to fit the bar if needed
  if (s.length / SR > bar * 0.95) s = tts('W', line, idx++, Math.round(190 * (s.length / SR) / (bar * 0.9)));
  parts.push({ t: fStart + (i + 1) * bar, samples: s, gain: 1, who: 'W' });
});
shots.push({ start: fStart, end: fEnd + 0.9, scene: 'band', who: 'W', mood: 'sing', caption: VERSE_CAPTION, drums: true });
cur = fEnd + 0.9;
for (const l of TAG) addLine(l, 'duo');
shots.push({ start: cur, end: cur + END_CARD, scene: 'end', caption: '' });
cur += END_CARD;

const duration = cur;
const total = Math.ceil(duration * SR);
const mix = new Float32Array(total);
const env = { M: new Float32Array(Math.ceil(duration * FPS)), W: new Float32Array(Math.ceil(duration * FPS)) };
for (const p of parts) {
  const o = Math.floor(p.t * SR);
  for (let i = 0; i < p.samples.length && o + i < total; i++) mix[o + i] += p.samples[i] * p.gain;
  // per-frame RMS for lip sync
  const spf = SR / FPS;
  for (let f = 0; f * spf < p.samples.length; f++) {
    let sum = 0, n = 0;
    for (let i = Math.floor(f * spf); i < Math.min(p.samples.length, (f + 1) * spf); i++) { sum += p.samples[i] ** 2; n++; }
    const fi = Math.floor(p.t * FPS) + f;
    if (fi < env[p.who].length) env[p.who][fi] = Math.max(env[p.who][fi], Math.sqrt(sum / n));
  }
}
for (const h of hits) {
  const o = Math.floor(h.t * SR), s = DR[h.d];
  const g = h.d === 'kick' ? 0.75 : h.d === 'snare' ? 0.6 : 0.5;
  for (let i = 0; i < s.length && o + i < total; i++) mix[o + i] += s[i] * g * 0.8;
}
let peak = 0;
for (const v of mix) peak = Math.max(peak, Math.abs(v));
const gain = 0.89 / peak;
const pcm = Buffer.alloc(total * 2);
for (let i = 0; i < total; i++) pcm.writeInt16LE(Math.max(-32768, Math.min(32767, Math.round(mix[i] * gain * 32767))), i * 2);
const hdr = Buffer.alloc(44);
hdr.write('RIFF', 0); hdr.writeUInt32LE(36 + pcm.length, 4); hdr.write('WAVE', 8); hdr.write('fmt ', 12);
hdr.writeUInt32LE(16, 16); hdr.writeUInt16LE(1, 20); hdr.writeUInt16LE(1, 22); hdr.writeUInt32LE(SR, 24);
hdr.writeUInt32LE(SR * 2, 28); hdr.writeUInt16LE(2, 32); hdr.writeUInt16LE(16, 34); hdr.write('data', 36); hdr.writeUInt32LE(pcm.length, 40);
fs.writeFileSync(path.join(AUD, 'mix.wav'), Buffer.concat([hdr, pcm]));

// normalize envelopes to 0..1 mouth openness
const mouth = {};
for (const k of ['M', 'W']) {
  const e = env[k];
  let mx = 0; for (const v of e) mx = Math.max(mx, v);
  mouth[k] = Array.from(e, (v) => +(Math.min(1, (v / mx) * 1.6)).toFixed(3));
}
fs.writeFileSync(path.join(DIR, 'timeline.json'), JSON.stringify({ fps: FPS, duration, beat, shots, hits, mouth }));
console.log('duration', duration.toFixed(2), 's  shots', shots.length, 'hits', hits.length);
