# Pebbles acting pass: design

Approved by the owner on 2026-11-14 ("Yes, go ahead") after the proposal in chat.

## Why

Outside feedback on the Chinese video said it reads as AI-generated: rigid bodies, mechanical expressions, an
instrument that isn't played, drums without weight. The kit is already a rig (separate SVG parts moved in code)
and already renders at 60 fps, so neither of those changes. What's missing is acting: anticipation, follow-through,
secondary motion, listener reactions, distinct personalities, real mouth shapes, and instruments driven by the
music actually in the clip.

## Goals

- Every video gets the acting automatically from its audio. No new script work is needed, and scripts can still override expressions and poses as today.
- Talking: each phrase starts with a small wind-up (lean back), then a lean toward the mic with raised brows, then
  a settle with overshoot. Nods land on stressed syllables. Hand gestures wind up first and follow through after.
- Listening: when the other Pebble talks, the listener reacts a beat later (brow lift, glance over, small nod at
  the end of the phrase). The two Pebbles never share the same idle.
- Personalities: Mr is laid-back, with slower, smaller, later moves and lazier eye darts. Mrs is quick, with bigger brows and eyes, faster reactions and more darts.
- Head feel: the face features slide a few units inside the body in the direction of a lean. Headphones and the lute lag the body's rotation by a few frames (drag), then settle.
- Mouth shapes: closed, small, wide "ee", round "oo" and open "ah", read from the vocal stem's tone, blended continuously so nothing pops.
- Concert: drum hits come from the song's own drums (demucs drums stem): kick, snare, hats, crash. The sticks lift
  ahead of each hit and accelerate into it, then rebound. Cymbals wobble on their hits, and on each kick the body drops and the head bobs a moment later. Mrs' strumming hand strums on the actual instrument notes (demucs "other" stem) and the lute recoils slightly.

## Non-goals

- New art: no new characters, sets or props.
- Changes to scripts' timing or captions.
- Frame rate changes.

## Anti-goals

- Constant motion. Idle stays calm; acting happens around speech and music events, then settles.
- Characters that move in lockstep. Same-time, same-shape motion on both is the failure mode to avoid.
- Mouths or drums moving on sound that isn't theirs: the vocal-only rule from the lip-sync fix still holds.

## Approaches considered

1. **Automatic from audio, with script overrides (chosen).** Phrases, accents, mouth shapes, drum hits and strums
   come from the stems. It works for every future video with no extra work.
2. **Authored acting beats only.** The script vocabulary would get `lean`, `react` and `nod`. It's precise, but it's work for every video, and the owner only posts results.
3. **Hybrid authored-first.** Rejected for the same reason as 2. Overrides stay available through the existing expression and pose fields.

## How

- **Analysis** (`scripts/analyze.mjs`): demucs runs in 4-stem mode (drums, bass, other, vocals; same install, about the same time), with each stem cached.
  `envelope.json` adds per-frame dB bands on the drums stem (`kick` 40–120 Hz, `snare` 1.5–4 kHz, `hat` 7–12 kHz)
  and on the other stem (`pluck` 200–4000 Hz). Without stems, these are absent, and the old behaviour applies (the drumGrid groove, no strums).
- **Envelope / events** (`src/kit/envelope.ts`, new `src/kit/acting.ts`): pure functions.
  - `findPhrases(open)`: speech segments, merging gaps under 0.3 s.
  - `onsets(db)`: rises over the recent level that are local peaks, with a minimum gap.
  - `drumHitsFromBands`: kick, snare and hat onsets. A hat onset whose level holds is a crash.
  - `strumsFrom(pluck)`.
  - `action(dt, persona)`: the shared anticipation, spring overshoot and decay curve.
  - `PERSONA` for Mr and Mrs.
- **Live motion** (`src/kit/scene/live.ts`): speaker lean and brows per phrase; accent nods and gestures with
  wind-up; listener reactions keyed to the other speaker's phrases; persona-scaled idle (sway, breathing,
  saccades); `faceShift` and `lag` added to the body.
- **Rig** (`Pebble.tsx`, `Face.tsx`): the face group translates by `faceShift`, and the headphones and lute rotate by
  `lag` about the top of the head and the lute's grip. Mouth: a continuous `round` (from the stem's tone) and the existing width give the ee/oo/ah shapes. Lip data carries per-frame `round`.
- **Concert** (`StageSet`, `Band.tsx`, `DrumKit.tsx`): when the stem hits exist they replace `drumHits(drumGrid)`. The stick
  angle comes from `strokeAngle(hits, t)` (rebound after a hit, hold, accelerate down into the next). The strum arm
  angle comes from `strumAngle(strums, t)`, with a small lute recoil.

## Left to the builder

The exact amplitudes and timings, tuned by eye on frame strips of the Chinese video. Tests pin the behaviour
(shapes, orderings, "only when"), not exact numbers.
