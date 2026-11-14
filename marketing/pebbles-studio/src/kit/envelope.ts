// Lip sync: the audio's voice-band loudness per video frame, mapped to how open the mouth is.

/** dB (from ffmpeg astats, voice band) to 0..1. -38 dB and below is closed, -16 dB and above is fully open. */
export function envelopeFromDb(db: number[]): number[] {
  return db.map((v) => (Number.isFinite(v) ? Math.max(0, Math.min(1, (v + 38) / 22)) : 0));
}

/** How open the speaker's mouth is on this frame (the envelope is already smoothed). */
export function mouthOpen(env: number[], frame: number, speaking: boolean): number {
  if (!speaking || frame < 0 || frame >= env.length) return 0;
  return env[frame] ?? 0;
}

/** Opens fast (~20 ms) and closes slower (~75 ms), like a real jaw, instead of jittering frame to frame. */
export function smoothEnvelope(raw: number[], fps: number): number[] {
  const dt = 1 / fps;
  const up = 1 - Math.exp(-dt / 0.02);
  const down = 1 - Math.exp(-dt / 0.075);
  let y = 0;
  return raw.map((x) => (y += (x - y) * (x > y ? up : down)));
}

/** Mouth width from the voice's brightness: "ee" sounds widen it, "oo" sounds narrow it. ~0.75..1.3. */
export function mouthWidths(loDb: number[], hiDb: number[], fps: number): number[] {
  const lin = (d: number) => (Number.isFinite(d) ? Math.pow(10, d / 20) : 0);
  const k = 1 - Math.exp(-1 / fps / 0.05);
  let y = 1;
  return loDb.map((lo, i) => {
    const a = lin(lo);
    const b = lin(hiDb[i] ?? -99);
    const target = a + b > 1e-4 ? 0.75 + 0.55 * (b / (a + b)) : 1;
    return (y += (target - y) * k);
  });
}

/** Stressed syllables: frames where the mouth peaks after a clear dip. Drives nods and gestures. */
export function findAccents(open: number[], fps: number): number[] {
  const out: number[] = [];
  const back = Math.round(0.15 * fps);
  const gap = Math.round(0.18 * fps);
  for (let i = 1; i < open.length - 1; i++) {
    const v = open[i];
    if (v < 0.5 || v < open[i - 1] || v < open[i + 1]) continue;
    let lo = v;
    for (let j = Math.max(0, i - back); j < i; j++) lo = Math.min(lo, open[j]);
    if (v - lo < 0.3) continue;
    if (out.length && i - out[out.length - 1] < gap) continue;
    out.push(i);
  }
  return out;
}

/** A quick rise and an easing fall after the most recent accent. */
export function accentPulse(accents: number[], frame: number, fps: number): number {
  let last = -1;
  for (const a of accents) { if (a > frame) break; last = a; }
  if (last < 0) return 0;
  const dt = (frame - last) / fps;
  return dt < 0.03 ? dt / 0.03 : Math.exp(-(dt - 0.03) * 7);
}

/** Resample a per-frame array from one frame rate to another (old envelope files were 30 fps). */
export function resample(arr: number[], from: number, to: number): number[] {
  if (from === to) return arr;
  const n = Math.round((arr.length * to) / from);
  return Array.from({ length: n }, (_, i) => {
    const x = (i * from) / to;
    const a = Math.floor(x);
    const b = Math.min(arr.length - 1, a + 1);
    return (arr[a] ?? 0) + ((arr[b] ?? 0) - (arr[a] ?? 0)) * (x - a);
  });
}
