# Pebbles Acting Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pebbles act like hand-animated characters: phrase-level leans with anticipation, listener reactions, distinct
personalities, secondary motion, ee/oo/ah mouth shapes, and concert instruments driven by the clip's own drums and notes.

**Architecture:** The analysis gets 4 demucs stems and writes extra per-frame bands. Pure functions in `src/kit/acting.ts` turn the
bands into events (phrases, onsets, drum hits, strums) and motion curves (wind-up, strokes, strums, personas).
`live.ts` combines them per frame, and the rig (`Pebble.tsx`, `Face.tsx`) gets three new inputs: `face.round`, `face.shift` and `body.lag`.

**Tech Stack:** Remotion 4.0.543, React SVG, TypeScript, vitest, ffmpeg, demucs (htdemucs) in `~/.cache/pebbles-studio/demucs-venv`.

**Spec:** `docs/superpowers/specs/2026-11-14-pebbles-acting-pass-design.md`

## Global Constraints

- The kit lives in `marketing/pebbles-studio/`. Run `npm test` and `npx tsc --noEmit` there; both must be green after every task.
- Media never goes in git. Commits carry no Co-Authored-By or "Generated with" lines. Commit to `feat/pebbles-kit`, and never push without asking.
- Everything is a pure function of time and frame (Remotion renders frames out of order).
- Mouths and accents still move only on the vocal stem (the previous fix holds).
- Old `envelope.json` files without the new bands must still render: there are then no strums, the drums use `drumHits(drumGrid)`, and `round` is 0.

## Review Focus

- **A shot with no speaker, or an off-screen speaker:** the listener still reacts only to the other Pebble's phrases. Nobody reacts to music. (Task 2: phrase owner is `null` when the script speaker is `null`.)
- **Drum hits closer together than the stroke time** (fast hats): the stick must not jump. `strokeAngle` stays continuous. (Task 3 test.)
- **A drums shot when the stem has no hits** (quiet section): fall back to `drumHits(drumGrid)` only when the bands are absent, not when they are quiet. (Task 2 test.)
- **The lute and headphones lag at a cut:** `lag` must be 0 at a cut instead of carrying motion across shots. (Task 5 test.)
- **Mr and Mrs in the same frame:** their idle sway and reactions are not identical. (Task 5 test.)

---

### Task 1: Four stems and extra bands in the analysis

**Files:** Modify `scripts/analyze.mjs` and `README.md`. Test: `test/analyze.test.ts`.

**Interfaces:**
- Produces: `envelope.json` = `{ fps: 60, source, db, lo, hi, kick?, snare?, hat?, pluck? }`. The four optional fields are dB-per-frame arrays, present when stems exist.
- `stemsFor(src, opts) -> { vocals, drums, other } | null`, which replaces `vocalsStem`. The cache files are `<hash>.<stem>.wav`.

- [ ] Fake demucs writes `drums.wav`, `bass.wav`, `other.wav` and `vocals.wav` under `<out>/htdemucs/mix/`. Tests: `writes drum and instrument bands from the stems` (kick/snare/hat/pluck arrays the same length as db) and `has no instrument bands without demucs`. Update the existing tests.
- [ ] Implement it. demucs runs without `--two-stems`. Bands, as ffmpeg filters on the stem: kick `highpass=f=40,lowpass=f=120` on drums; snare `highpass=f=1500,lowpass=f=4000` on drums; hat `highpass=f=7000,lowpass=f=12000` on drums; pluck `highpass=f=200,lowpass=f=4000` on other.
- [ ] README: drums and strums come from the stems. Then run the tests and commit.

### Task 2: Events from the envelope

**Files:** Create `src/kit/acting.ts`. Modify `src/kit/envelope.ts`, `src/kit/timeline.ts` (`Resolved.speaker`) and `src/kit/scene/VideoComposition.tsx`. Test: `test/acting.test.ts`.

**Interfaces:**
- `type Phrase = { start: number; end: number; who: Who | null }` (frames).
- `findPhrases(open: number[], fps: number): { start: number; end: number }[]`: open > 0.08, gaps under 0.3 s merged, segments under 0.1 s dropped.
- `onsets(db: number[], fps: number, rise = 6, gapSec = 0.09): number[]` (frames): db[i] − min(db[i−4..i−1]) ≥ rise, db[i] is the local max over ±2 frames, db[i] > (95th percentile of db) − 30, and at least gapSec since the previous onset.
- `drumHitsFromBands(b: { kick: number[]; snare: number[]; hat: number[] }, fps): Hit[]`: kick, snare and hat onsets. A hat onset whose band stays within 6 dB of its onset level for 0.25 s is a `crash`.
- `strumsFrom(pluck: number[], fps): number[]` (seconds): onsets with rise 5 and gap 0.1.
- `type Music = { hits: Hit[] | null; strums: number[] }`, and `musicFromEnvelope(j, fps): Music`. `hits` is null when the bands are absent.
- `Lip` gains `round: number[]` and `phrases: Phrase[]`. `lipFromEnvelope(j, fps, ownerAt?: (sec: number) => Who | null)`.
- `Resolved.speaker: Who | null` (the script speaker even when off screen).
- `VideoProps` gains `music: Music`. `NO_MUSIC = { hits: null, strums: [] }`.

- [ ] Tests:
  - `findPhrases merges short gaps and drops blips`
  - `onsets finds sharp rises, not slow swells`
  - `drumHitsFromBands labels kick, snare, hat and a sustained hat as crash`
  - `musicFromEnvelope gives null hits for old envelopes but [] for a quiet stem`
  - `phrases carry the script speaker at their start, null when nobody speaks`
  - `round is high for dark vowels and low for bright ones`: round = clamp01((0.32 − hi/(lo+hi) in linear amplitude) / 0.2), smoothed like width.
- [ ] Implement them. In `calculateVideoMetadata`, use `ownerAt = (s) => resolveAt(script, s).speaker`, and set `ctx.hits` = `music.hits ?? drumHits(drumGrid)` for drums shots. Then run the tests and commit.

### Task 3: Motion curves and personas

**Files:** Modify `src/kit/acting.ts`. Test: `test/acting.test.ts`.

**Interfaces:**
- `type Persona = { tempo: number; amp: number; react: number; brow: number; dart: number }`.
- `PERSONA: Record<Who, Persona>`: mr `{ tempo: 0.75, amp: 0.85, react: 0.32, brow: 0.7, dart: 1.5 }`, mrs `{ tempo: 1.25, amp: 1.15, react: 0.15, brow: 1.3, dart: 0.7 }`. `dart` scales the saccade hold time.
- `windUp(dt: number, wind: number): number`: 0 before −wind; eases to −0.25 at 0 (anticipation); then `-0.25 + 1.25 * springProgress(dt)` (overshoot, settles at 1).
- `hitAction(dt, wind, decay): number` = windUp × (dt > 0.15 ? exp(−(dt − 0.15) × decay) : 1).
- `phraseLean(phrases: Phrase[], who: Who, frame, fps, p: Persona): number`: windUp from the phrase start (wind 0.12 / tempo), held while the phrase runs, eased out to 0 over 0.45 / tempo s after its end. Only `who`'s phrases count.
- `listenReact(phrases, who, frame, fps, p): { brow: number; nod: number; glance: number }`: reacts to the other Pebble's phrases. brow = hitAction at start + p.react; glance = the same curve held for the phrase; nod = hitAction at end + p.react.
- `strokeAngle(hits: number[], t: number): number` (0 = stick down on the head, 1 = up): 0 at a hit; rises to 1 over min(0.12, gap / 3); holds; falls ease-in (quadratic) over the last min(0.15, gap / 2) into the next hit. It is 0.6 when nothing comes within 1 s. Continuous for any gap.
- `strumAngle(strums: number[], t: number): number` in −1..1: alternating down and up strokes, each one easing out over 0.07 s, starting with a 0.03 s pull back.

- [ ] Tests:
  - `windUp dips before the action and overshoots after`
  - `phraseLean is 0 for the other Pebble's phrases`
  - `Mr reacts later and smaller than Mrs`
  - `strokeAngle lifts before each hit and is continuous for 40 ms gaps` (max frame-to-frame step at 60 fps < 0.5)
  - `strumAngle alternates direction per strum`
- [ ] Implement them. Then run the tests and commit.

### Task 4: Rig inputs

**Files:** Modify `src/kit/types.ts`, `src/kit/characters/Face.tsx`, `src/kit/characters/Pebble.tsx` and `src/kit/props/HeldProps.tsx` (lute recoil prop). Test: `test/pebble.test.ts`.

**Interfaces:**
- `Face.round?: number` (0..1), `Face.shift?: { x: number; y: number }`.
- `Body.lag?: number` (radians: the headphones and lute rotate by −lag about their attachment).
- `Lute` takes `{ recoil?: number }` (degrees).

- [ ] Tests:
  - `shifts the face features by face.shift`: the markup contains `translate(5 -3)` for shift (5, −3).
  - `rounds the open mouth`: the open-mouth path's width at round 1 is under 0.6× its width at round 0, for the same open.
  - `rotates headphones by body.lag`
- [ ] Implement them. For the mouth: round narrows hw by up to 45% and makes the top and bottom more symmetric. A width over 1 flattens the bottom (ee). Then run the tests and commit.

### Task 5: Live acting

**Files:** Modify `src/kit/scene/live.ts`, `src/kit/scene/VideoComposition.tsx` (`ctx.speaker`, `ctx.music`), `src/kit/sets/StageSet.tsx`, `src/kit/sets/Band.tsx` and `src/kit/props/DrumKit.tsx`. Test: `test/live.test.ts`.

**Interfaces:**
- `LiveCtx` gains `phrases` (from lip), `speaker: Who | null` and `strums: number[]`. `ctx.hits` is unchanged in type.
- liveFace adds: brows += lean × 5 × p.brow + listen.brow × 6 × p.brow; look.x += glance toward the other Pebble × 6; saccade on t / p.dart; shift = { x: tilt-direction × 6 × lean, y: 4 × lean }; round from lip.
- liveBody adds:
  - Speaker: dy += lean × 6 × p.amp; squash −= lean × 0.012; tilt += lean × 0.03 × micSide (mr +1, mrs −1).
  - Accent nod and gesture use `hitAction` (wind 0.08), not the instant pulse.
  - Listener: nod tilt and dy from listen.nod.
  - Idle: sway and breathe at t × p.tempo with amplitude × p.amp.
  - `lag` = (tilt(t) − tilt(t − 0.08)) × 1.5, 0 within 0.1 s after a cut.
  - Drum mode: each arm angle from `strokeAngle` over its hit kinds (left: snare/hat/ohat; right: kick/crash). Kick: dy += 12 × pulse, head shift y lags 0.05 s.
  - Mic mode with the lute: the strum arm rot from `strumAngle`; the lute recoil is 2° × the strum pulse.

- [ ] Tests:
  - `the speaker leans in during a phrase, after a small lean back`
  - `the listener lifts brows only after the other's phrase starts`
  - `Mr and Mrs idle differently at the same t`
  - `lag is 0 right after a cut`
  - `drum sticks are up just before a hit and down on it`
  - `the strum arm moves on strums, not between them`
- [ ] Implement them. Tune the amplitudes on Remotion stills of the Chinese video (frames around 1.0 s, 11–13 s, 33 s and 42–46 s). Then run the tests and commit.

### Task 6: Render and verify

- [ ] Run `npm run render` for all three slugs, keeping the previous `reel.mp4` as `reel-before-acting.mp4`.
- [ ] Check frame strips: speaker lean, listener reaction in the split section, drum strokes against the audio's hits, strums.
- [ ] Make a side-by-side of the Chinese video (before/after), send it to the owner, and commit.
