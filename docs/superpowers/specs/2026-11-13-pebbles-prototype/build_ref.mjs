// Timeline + audio built from the reference video's own soundtrack.
// Shots/captions were measured from the reference (chair colour + caption-band changes);
// green-chair guy -> Mr Pebbles, beige-chair guy -> Mrs Pebbles.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const DIR = path.dirname(new URL(import.meta.url).pathname);
const REF = '/Users/aabhashrai/Downloads/WhatsApp Video 2026-11-12 at 18.26.06.mp4';
const AUD = path.join(DIR, 'audio'); fs.mkdirSync(AUD, { recursive: true });
const FPS = 30, SR = 44100;
const SONG_END = 35.27, END_CARD = 3.0;
const FINALE = 28.47;

const CUTS = [ // [who, start, end]
  ['M', 0, 1.70], ['W', 1.70, 3.30], ['M', 3.30, 5.43], ['W', 5.43, 8.03], ['M', 8.03, 8.77], ['W', 8.77, 9.63],
  ['M', 9.63, 10.27], ['W', 10.27, 11.10], ['M', 11.10, 11.73], ['W', 11.73, 12.83], ['M', 12.83, 13.53],
  ['W', 13.53, 14.47], ['M', 14.47, 15.13], ['W', 15.13, 15.87], ['M', 15.87, 16.57], ['W', 16.57, 17.37],
  ['M', 17.37, 18.23], ['W', 18.23, 18.93], ['M', 18.93, 19.63], ['W', 19.63, 20.80], ['M', 20.80, FINALE], // the reference cuts to a silent reaction at 27.0, but the line is still Mr's until ~28.1
];
const CAPS = [ // [start, end, text]
  [0.10, 1.73, 'Spanish is easy, right?'], [3.30, 5.43, 'How do you say tomorrow in Spanish?'],
  [6.50, 7.27, 'Mañana'], [8.03, 8.77, 'Morning'], [8.77, 9.50, 'Mañana'], [9.63, 10.27, 'Spider'],
  [10.27, 11.13, 'Araña'], [11.13, 12.00, 'Scratches'], [12.00, 12.60, 'Araña'], [12.87, 13.17, 'Skill'],
  [13.53, 14.20, 'Maña'], [14.50, 15.17, 'Spiderweb'], [15.17, 16.07, 'Telaraña'], [16.07, 16.57, 'Strange'],
  [16.57, 17.60, 'Extraña'], [17.60, 18.27, 'Spain'], [18.27, 19.07, 'España'], [19.07, 19.63, 'Mountain'],
  [19.63, 20.80, 'Montaña'], [20.87, 21.73, 'Try this'], [21.73, 23.43, 'Tomorrow morning'],
  [23.43, 25.43, 'the strange spider from Spain'], [25.43, 28.50, 'skillfully scratches the spider web on the mountain'],
];
const VERSE = 'Mañana por la mañana, la extraña araña de España araña con maña la telaraña de la montaña';
const MOOD = { M: 'smug', W: 'stare' };

// Shots: split each cut wherever the caption changes, so the renderer sees one caption per shot.
const shots = [];
for (const [who, s, e] of CUTS) {
  const bounds = new Set([s, e]);
  for (const [cs, ce] of CAPS) { if (cs > s && cs < e) bounds.add(cs); if (ce > s && ce < e) bounds.add(ce); }
  const b = [...bounds].sort((x, y) => x - y);
  for (let i = 0; i < b.length - 1; i++) {
    const mid = (b[i] + b[i + 1]) / 2;
    const cap = CAPS.find(([cs, ce]) => mid >= cs && mid < ce);
    shots.push({ start: b[i], end: b[i + 1], scene: 'single', who, mood: MOOD[who], caption: cap ? cap[2] : '', cutStart: s });
  }
}
shots.push({ start: FINALE, end: SONG_END, scene: 'band', who: 'W', mood: 'sing', caption: VERSE, drums: true });
shots.push({ start: SONG_END, end: SONG_END + END_CARD, scene: 'end', caption: '' });
const duration = SONG_END + END_CARD;

// Drum hits for the animation, on the song's own grid (kick every 1.6s from 28.58, beat 0.4s).
const beat = 0.4, k0 = 28.58;
const hits = [{ t: FINALE, d: 'crash' }];
for (let t = k0; t < SONG_END; t += 1.6) {
  hits.push({ t, d: 'kick' });
  if (t + 1.44 < SONG_END) hits.push({ t: t + 1.44, d: 'kick' });
  if (t + 0.8 < SONG_END) hits.push({ t: t + 0.8, d: 'snare' });
}
for (let t = k0; t < SONG_END; t += 0.2) hits.push({ t: +t.toFixed(3), d: Math.round((t - k0) / 0.2) % 8 === 7 ? 'ohat' : 'hat' });

// Audio: the reference soundtrack, then a short fade into the end card.
const mix = path.join(AUD, 'mix.wav');
execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', REF, '-vn', '-ac', '2', '-ar', String(SR),
  '-af', `afade=t=out:st=${SONG_END - 0.25}:d=0.25,apad=whole_dur=${duration}`, mix]);

// Lip-sync envelope from the voice band (300-3400 Hz), dB per video frame.
const envTxt = path.join(AUD, 'voice_env.txt');
execFileSync('ffmpeg', ['-v', 'error', '-y', '-i', REF, '-vn', '-ac', '1', '-ar', String(SR),
  '-af', `highpass=f=300,lowpass=f=3400,asetnsamples=${SR / FPS},astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level:file=${envTxt}`,
  '-f', 'null', '-']);
const db = fs.readFileSync(envTxt, 'utf8').split('\n').filter((l) => l.includes('RMS')).map((l) => { const v = parseFloat(l.split('=')[1]); return isFinite(v) ? v : -99; });
const n = Math.ceil(duration * FPS);
const env = Array.from({ length: n }, (_, i) => +(Math.max(0, Math.min(1, ((db[i] ?? -99) + 38) / 22))).toFixed(3));
fs.writeFileSync(path.join(DIR, 'timeline.json'), JSON.stringify({ fps: FPS, duration, beat, safe: true, endSub: 'Babe, get this. Got this.', shots, hits, mouth: { M: env, W: env } }));
console.log('duration', duration.toFixed(2), 'shots', shots.length, 'hits', hits.length);
