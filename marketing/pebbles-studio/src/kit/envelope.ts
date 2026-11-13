// Lip sync: the audio's voice-band loudness per video frame, mapped to how open the mouth is.

/** dB (from ffmpeg astats, voice band) to 0..1. -38 dB and below is closed, -16 dB and above is fully open. */
export function envelopeFromDb(db: number[]): number[] {
  return db.map((v) => (Number.isFinite(v) ? Math.max(0, Math.min(1, (v + 38) / 22)) : 0));
}

/** How open the speaker's mouth is on this frame. Holds a little of the previous frame so it doesn't flicker. */
export function mouthOpen(env: number[], frame: number, speaking: boolean): number {
  if (!speaking || frame < 0 || frame >= env.length) return 0;
  return Math.max(env[frame] ?? 0, (env[frame - 1] ?? 0) * 0.55);
}
