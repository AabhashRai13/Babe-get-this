# Pebbles Animation Kit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Remotion kit in `marketing/pebbles-studio/` that renders post-ready Mr & Mrs Pebbles videos from a short script file plus audio kept on the Mac.

**Architecture:** Pure TypeScript modules (geometry, expressions, poses, timeline, drums, envelope, framings) are unit-tested with Vitest. React SVG components draw characters, sets and captions from those modules. Node scripts stage media from `~/Documents/Pebbles Videos/<slug>/` into a git-ignored `public/media/`, pre-compute the lip-sync envelope with `ffmpeg`, call `remotion render`, and write a no-sound copy.

**Tech Stack:** Remotion 4.0.543 (`remotion`, `@remotion/cli`, `@remotion/google-fonts`), React 19, TypeScript 5 (strict), Vitest 5, Node 24, ffmpeg (Homebrew, already installed).

**Spec:** `docs/superpowers/specs/2026-11-13-pebbles-animation-kit-design.md`. The prototype it ports is in `docs/superpowers/specs/2026-11-13-pebbles-prototype/` (`scene.js` holds the geometry and drawing, and `build_ref.mjs` holds the Spanish-song timings).

## Global Constraints

- All kit code lives under `marketing/pebbles-studio/` with its own `package.json`. The Android build is untouched.
- **No media in git, ever.** No audio, video or envelope files under `marketing/pebbles-studio/`. `public/media/`, `node_modules/` and `out/` are git-ignored.
- The media root is `process.env.PEBBLES_MEDIA ?? ~/Documents/Pebbles Videos`. A video's media is `<root>/<slug>/` and its renders go in `<root>/<slug>/out/`.
- Output is 1080×1920 at 30fps, H.264 High profile, yuv420p, BT.709, AAC 192 kbps at 44.1 kHz, with faststart. A render writes `reel.mp4` plus a `no-sound.mp4` copy.
- Safe area: no faces or captions in the top 270px, the bottom 420px, or the right 140px between y=900 and y=1500.
- Characters must match `public/icon.svg`, which is copied from the prototype folder.
- Colours come from the icon. Mr: outer `#F5F2EB`, body gradient `#6E8996`→`#4C6673`, feet `#415967`, arm `#607D8B`. Mrs: body gradient `#A7B8C0`→`#80959F`, feet `#728992`, arm `#90A4AE`. Shared: line `#263A44`, pupil `#071923`, blush `#E99A99`.
- Commit messages have no Co-Authored-By or "Generated with" lines (owner's standing rule). Never push without asking the owner first.
- Remotion composition ids are `v-<slug>` for videos, plus `sheet-expressions`, `sheet-poses` and `sheet-sets`.

## Review Focus

1. **A script names an audio file that isn't in the media folder.** The render must stop before Remotion runs, with a message naming the full expected path. Covered by a test in Task 4.
2. **A speaker who isn't on screen**, for example `speaker: 'mrs'` on a `single-mr` frame. Script validation must reject it, naming the shot index. Covered by a test in Task 3.
3. **A beat timed outside its shot**, or shots out of order. Validation must reject it. Covered by a test in Task 3.
4. **Audio shorter or longer than the script's `end`.** Past the end of the envelope, mouths stay closed. The video length follows the script, not the audio. Covered by a test in Task 4.
5. **A long caption, such as the verse, on any framing.** The caption box must stay inside the safe area at the font size it is drawn with. Covered by a data-driven framing test in Task 5. Task 8's sets are covered automatically.

---

## File structure (`marketing/pebbles-studio/`)

```
package.json, tsconfig.json, remotion.config.ts, vitest.config.ts, .gitignore, README.md
public/icon.svg
scripts/media.mjs          media root + stage audio (+ missing-file error)
scripts/analyze.mjs        ffmpeg voice-band envelope -> public/media/<slug>/envelope.json
scripts/render.mjs         stage + analyze + remotion render + no-sound copy
scripts/sheets.mjs         renders the three sheet stills to <root>/_sheets/
src/index.ts               registerRoot
src/Root.tsx               registers video + sheet compositions
src/kit/types.ts           shared types (below)
src/kit/geometry.ts        MR / MRS art-unit geometry ported from scene.js
src/kit/expressions.ts     EXPRESSIONS, faceFor, blendFace
src/kit/motion.ts          blink, breathe, settle, sway (pure, per time)
src/kit/poses.ts           POSES, bodyFor, walk-in
src/kit/timeline.ts        video(), validateScript, resolveAt
src/kit/drums.ts           drumHits, pulse
src/kit/envelope.ts        envelopeFromDb, mouthOpen
src/kit/framings.ts        FRAMINGS per set, SAFE, captionLayout
src/kit/characters/{Pebble,Face,Arms,Headphones}.tsx
src/kit/props/{Phone,Bag,ListPaper,Mug,Sticks,Mics,DrumKit,Effects}.tsx
src/kit/sets/{Paper,Podcast,Band,Kitchen,Couch,Aisle}.tsx
src/kit/scene/{VideoComposition,Caption,EndCard,SafeAreaGuide}.tsx
src/sheets/{ExpressionSheet,PoseSheet,SetSheet}.tsx
src/videos/index.ts        registry: VideoScript[]
src/videos/2026-11-12-spanish-song/script.ts
src/videos/_template/script.ts
test/*.test.ts
```

Shared types, fixed here so every task uses the same names (`src/kit/types.ts`):

```ts
export type Who = 'mr' | 'mrs';
export type ExpressionName = 'neutral'|'deadpan'|'smug'|'content'|'happy'|'laugh'|'excited'|'love'|'smirk'|'sideEye'|'eyeRoll'|'worried'|'shock'|'sad'|'crying'|'annoyed'|'confused'|'sleepy'|'sing';
export type EffectName = 'tears'|'sweat'|'anger'|'sparkles'|'zzz'|'hearts'|'pop';
export type MouthShape = 'curve'|'o'|'grin'|'wobble';
export interface Face { lid: number; lidTilt: number; eyeScale: number; pupilScale: number;
  look: { x: number; y: number }; brow: [number, number]; browTilt: number; smile: number;
  open: number; mouthWidth: number; mouthShape: MouthShape; blush: number; happyEyes: boolean; effect?: EffectName }
export type PoseName = 'sit'|'stand'|'walkIn'|'point'|'wave'|'shrug'|'armsCrossed'|'handsOnHips'|'holdPhone'|'holdBag'|'holdList'|'drum'|'hug';
export interface Arm { x: number; y: number; rot: number }        // shoulder (art units) + radians
export interface Body { arms: [Arm, Arm]; tilt: number; squash: number; dy: number; feet: 'together'|'apart'|'step'; hand: 0 | 1 }
export type PropName = 'phone'|'bag'|'list'|'mug'|'sticks';
export type SetName = 'podcast'|'band'|'kitchen'|'couch'|'aisle';
export type FrameName = 'single-mr'|'single-mrs'|'two-shot'|'wide';
export type Look = 'camera'|'left'|'right'|'other'|{ x: number; y: number };
export interface CastState { expression?: ExpressionName | Partial<Face>; pose?: PoseName; look?: Look;
  holding?: PropName; headphones?: boolean; facing?: 'left'|'right' }
export interface Beat { at: number; mr?: CastState; mrs?: CastState; caption?: string }
export interface Shot { at: number; set: SetName; frame: FrameName; speaker?: Who; caption?: string;
  captionStyle?: 'line'|'verse'; drums?: boolean; mr?: CastState; mrs?: CastState; beats?: Beat[] }
export interface DrumGrid { start: number; beat: number; end: number }
export interface VideoScript { slug: string; audio?: string; end: number; endCard?: { subtitle: string };
  drumGrid?: DrumGrid; shots: Shot[] }
export interface Rect { x: number; y: number; w: number; h: number }
```

---

### Task 1: Project setup, geometry port, static Pebbles

**Files:**
- Create: `package.json`, `tsconfig.json`, `remotion.config.ts`, `vitest.config.ts`, `.gitignore`, `public/icon.svg`, `src/index.ts`, `src/Root.tsx`, `src/kit/types.ts`, `src/kit/geometry.ts`, `src/kit/characters/Pebble.tsx`
- Test: `test/geometry.test.ts`

**Interfaces:**
- Produces: `MR: CharacterDef` and `MRS: CharacterDef` (in `geometry.ts`), plus `toMrsLocal(x: number, y: number): {x: number; y: number}`. `CharacterDef` mirrors the prototype's `MR` and `MRS` objects: `outer, inner, grad, eyes, eyeRx, eyeRy, pupilR, hlR, hlOff, blush, blushRx, blushRy, brows (cubic point lists), browW, mouth {x,y,hw,sag,w}, feet, footColor, armColor, armRx, armRy, lashes, lidW, phones, bottom`.
- Produces: `<Pebble who: Who; x: number; y: number; scale: number; face: Face; body: Body; layers?: ('body'|'feet'|'arms'|'phones')[]; t: number />`. At this point it draws the body, feet and arms only. Task 2 adds the face.

- [ ] **Step 1:** Create the project files. `package.json` scripts are `studio` (`remotion studio`), `test` (`vitest run`), `typecheck` (`tsc --noEmit`), `render` (`node scripts/render.mjs`), `sheets` (`node scripts/sheets.mjs`) and `smoke` (`npm run typecheck && npm test && npm run sheets`). `.gitignore` covers `node_modules/`, `out/`, `public/media/` and `*.mp4 *.m4a *.mp3 *.wav *.aiff`. Copy `icon.svg` from the prototype folder. Run `npm install remotion@4.0.543 @remotion/cli@4.0.543 @remotion/google-fonts@4.0.543 react react-dom` and `npm install -D typescript @types/react vitest@5`.
- [ ] **Step 2: Write the failing test** `test/geometry.test.ts`:
  - `mrs eyes are level once straightened`: `Math.abs(MRS.eyes[0].y - MRS.eyes[1].y) < 1`. This catches a wrong tilt sign, with α = 0.1393 rad about centre (685.4, 487.5).
  - `mr outer capsule matches icon`: `MR.outer.w ≈ 450.3` and `MR.outer.h ≈ 647.6` (±0.5).
  - `mrs mouth corners level`: `|toMrsLocal(638.98,503.8).y - toMrsLocal(713.3,493.3).y| < 1`.
- [ ] **Step 3:** Run `npm test`. Expected: FAIL, because the module is missing.
- [ ] **Step 4:** Port `MR`, `MRS`, `L`, `Lv` and `q2c` from `scene.js` lines 20–60 into `geometry.ts`, keeping the numbers exactly. Write `Pebble.tsx` as SVG: a capsule is a `<rect rx=w/2>`, the gradient is a `<linearGradient>` with an id scoped by `who`, and feet and arms are ellipses. Draw the arm ellipse centred at `(0, armRy*0.6)` after rotating about the shoulder, as `drawArms` does. Register a temporary `Root.tsx` still, `debug-pebbles`, that draws both characters standing side by side.
- [ ] **Step 5:** Run `npm test && npm run typecheck`. Expected: PASS.
- [ ] **Step 6: Visual check.** Run `npx remotion still src/index.ts debug-pebbles out/debug.png`. Compare it with the icon rendered at the same scale (`qlmanage -t -s 1024 public/icon.svg`). Body proportions and colours must match. Mrs must be upright.
- [ ] **Step 7: Commit** `feat(pebbles-studio): set up Remotion kit and port Pebbles geometry`.

### Task 2: Faces, expression library, expression sheet (owner sign-off)

**Files:**
- Create: `src/kit/expressions.ts`, `src/kit/motion.ts`, `src/kit/characters/Face.tsx`, `src/kit/props/Effects.tsx`, `src/sheets/ExpressionSheet.tsx`, `scripts/sheets.mjs`
- Modify: `src/kit/characters/Pebble.tsx` (draw `<Face>` inside the body layer), `src/Root.tsx` (register `sheet-expressions`)
- Test: `test/expressions.test.ts`, `test/motion.test.ts`

**Interfaces:**
- Produces: `EXPRESSIONS: Record<ExpressionName, Face>`, `faceFor(e: ExpressionName | Partial<Face> | undefined, base?: ExpressionName): Face` (a partial is merged over `base`, which defaults to `neutral`), and `blendFace(a: Face, b: Face, k: number): Face` (numbers lerp; `mouthShape`, `happyEyes` and `effect` switch at k ≥ 0.5).
- Produces (`motion.ts`): `blink(t: number, seed: number): number` (0..1, with the prototype's period `3.1 + seed*0.9` and a 0.2 s close), `breathe(t, seed): number`, `settle(sinceCut: number): number`, `sway(t, seed): number`. Seeds are `mr = 0.3` and `mrs = 1.1`.
- Produces: `<Face who face t />`.

- [ ] **Step 1: Write the failing tests.**
  - `every expression name resolves`: for each of the 19 names, `faceFor(name)` returns a Face with `lid` and `open` in [0,1] and `eyeScale` in [0.8,1.3].
  - `prototype moods keep their values`: `EXPRESSIONS.smug` has `lid 0.46` and `smile 0.75`; `deadpan` has `lid 0.52` and `smile 0.12`; `shock` has `lid 0`, `eyeScale 1.1`, `pupilScale 0.7` and `open 0.3`; `love` has `lid 1` and `happyEyes true`.
  - `partial override merges`: `faceFor({ smile: -1 }, 'smug').lid === 0.46`.
  - `blend midpoint`: `blendFace(faceFor('neutral'), faceFor('shock'), 0.5).eyeScale` is halfway between them.
  - `blink closes briefly`: over 0–10 s at 30fps, `blink(t, 0.3) > 0.9` happens on at least 2 and at most 4 separate runs.
- [ ] **Step 2:** Run `npm test`. Expected: FAIL.
- [ ] **Step 3:** Implement `expressions.ts` and `motion.ts`. Port `drawFace` and `drawMouth` from `scene.js` (lines 114–210) to SVG in `Face.tsx`:
  - Use `<clipPath>` per eye, with the lid clip enlarged by 2.5 units, as the prototype's fix for halo rings does.
  - Mrs gets lashes. Brows are drawn with lift and tilt. Add `MouthShape` `o` (an ellipse), `grin` (an open mouth with a white teeth band) and `wobble` (a sine-wave line).
  - `Effects.tsx` draws the seven effects next to the face. `pop` is the spec's "!" pop: a bold "!" that scales in above the head.
  - Effects on the new expressions: crying→`tears`, worried→`sweat`, annoyed→`anger`, excited→`sparkles`, sleepy→`zzz`, love→`hearts`.
- [ ] **Step 4:** Build `ExpressionSheet`: a 2160×3840 still with a grid of 19 rows by 2 characters, each labelled with the expression name in the caption font. Write `scripts/sheets.mjs`, which renders the registered sheets to `<mediaRoot>/_sheets/<id>.png`. The media-root helper comes in Task 4, so inline the same default here and switch to `media.mjs` in Task 4.
- [ ] **Step 5:** Run `npm test && npm run typecheck && npm run sheets`. Expected: PASS, and `expressions.png` exists.
- [ ] **Step 6: Visual check.** Open `expressions.png`. Every face must read clearly at phone size, and the smug, deadpan, shock and love rows must match the prototype's look.
- [ ] **Step 7: Commit** `feat(pebbles-studio): add face rig, 19 expressions and expression sheet`.
- [ ] **Step 8: STOP for owner sign-off.** Send `expressions.png` to the owner and apply any changes they ask for before starting Task 5. Tasks 3 and 4 don't depend on the look, so they may go ahead while waiting.

### Task 3: Script format, validation and timeline resolution

**Files:**
- Create: `src/kit/timeline.ts`
- Test: `test/timeline.test.ts`

**Interfaces:**
- Consumes: `faceFor`, `blendFace` (Task 2).
- Produces:
  - `video(s: VideoScript): VideoScript` validates, then returns `s`. It throws `ScriptError` with message `"<slug> shot <i>: <reason>"`.
  - `validateScript(s): string[]` (an empty array means valid).
  - `resolveAt(s: VideoScript, t: number): Resolved`, where `Resolved = { shotIndex: number; shot: Shot; cutStart: number; caption: string; cast: Record<Who, ResolvedCast | null>; onEndCard: boolean }` and `ResolvedCast = { face: Face; pose: PoseName; look: Look; holding?: PropName; headphones: boolean; facing: 'left'|'right'; speaking: boolean }`.
  - `END_CARD_SEC = 3`.
  - `framesFor(s) = Math.ceil((s.end + (s.endCard ? END_CARD_SEC : 0)) * 30)`.
- `FRAME_CAST: Record<FrameName, Who[]>`: `single-mr`→`['mr']`, `single-mrs`→`['mrs']`, `two-shot` and `wide`→`['mr','mrs']`.

- [ ] **Step 1: Write the failing tests** (a fixture script with 3 shots and one beat):
  - `rejects unsorted shots` and `rejects first shot not at 0`.
  - `rejects end before last shot`.
  - `rejects off-screen speaker`: `{frame:'single-mr', speaker:'mrs'}` gives an error containing `shot 1` and `not on screen`.
  - `rejects beat outside its shot`.
  - `caption follows beats`: a beat with a `caption` replaces the shot caption from `beat.at` onwards.
  - `cast state carries over cuts`: an expression set in shot 0 for `mr` still applies in shot 2 if shot 2 doesn't set it. Defaults are `neutral` and `sit`, with headphones on in `podcast` and `band` and off elsewhere.
  - `expression eases over 4 frames within a shot`: at `beat.at + 2/30`, the face is between the old and the new.
  - `expression snaps at a cut`: at `shot.at` exactly, the face equals the new expression.
  - `only the speaker speaks`: `resolveAt(...).cast.mrs.speaking === false` on a shot where `speaker` is `'mr'`.
  - `end card after end`: `resolveAt(s, s.end + 0.1).onEndCard === true` when `endCard` is set.
- [ ] **Step 2:** Run `npx vitest run test/timeline.test.ts`. Expected: FAIL.
- [ ] **Step 3:** Implement `timeline.ts`. `look: 'other'` resolves to a horizontal pupil offset of 12 toward the other character's side, and `'camera'` resolves to `{x:0,y:3}`.
- [ ] **Step 4:** Run `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(pebbles-studio): add video script format, validation and timeline`.

### Task 4: Media staging, lip-sync envelope, drum grid

**Files:**
- Create: `scripts/media.mjs`, `scripts/analyze.mjs`, `src/kit/envelope.ts`, `src/kit/drums.ts`
- Modify: `scripts/sheets.mjs` (use `mediaRoot()`)
- Test: `test/media.test.ts`, `test/envelope.test.ts`, `test/drums.test.ts`

**Interfaces:**
- Produces (`media.mjs`):
  - `mediaRoot(): string`.
  - `mediaDir(slug): string`.
  - `stageAudio(slug: string, file: string, publicDir?: string): string`. This copies `<mediaDir>/<file>` to `public/media/<slug>/<file>` and returns the staged path. If the source is missing, it throws `Error("Audio not found: <full expected path>")`.
- Produces (`analyze.mjs`): `analyzeAudio(slug, file): Promise<void>`, which writes `public/media/<slug>/envelope.json` as `{ fps: 30, db: number[] }`. Use the prototype's ffmpeg chain: `highpass=f=300,lowpass=f=3400,asetnsamples=1470,astats=metadata=1:reset=1,ametadata=print:key=lavfi.astats.Overall.RMS_level`, where 1470 = 44100/30.
- Produces (`envelope.ts`): `envelopeFromDb(db: number[]): number[]` (`clamp((db + 38) / 22, 0, 1)`, with non-finite values giving 0) and `mouthOpen(env: number[], frame: number, speaking: boolean): number` (0 when not speaking or out of range; otherwise `max(env[f], env[f-1]*0.55)`).
- Produces (`drums.ts`):
  - `drumHits(g: DrumGrid): { t: number; d: 'kick'|'snare'|'hat'|'ohat'|'crash' }[]`. The pattern comes from `build_ref.mjs`: a crash at `start`; a kick every `4*beat` and again at `+3.6*beat`; a snare at `+2*beat`; hats every `beat/2`, with every 8th one open.
  - `pulse(hits, t, kinds, k = 16): number`, which decays as `exp(-(t - last) * k)`.

- [ ] **Step 1: Write the failing tests.**
  - `stageAudio throws with expected path`: with `PEBBLES_MEDIA` set to a temp dir and the file absent, the message contains `<tmp>/demo/song.m4a`.
  - `stageAudio copies`: with a 10-byte dummy file, the staged file exists with 10 bytes.
  - `envelopeFromDb maps prototype range`: `[-38, -16, -60, -Infinity]` gives `[0, 1, 0, 0]`.
  - `mouth closed past envelope`: `mouthOpen([0.5], 5, true) === 0`.
  - `mouth closed when not speaking`: `mouthOpen([1], 0, false) === 0`.
  - `drum grid matches prototype`: `drumHits({start:28.58, beat:0.4, end:35.27})` has kicks within 0.01 of `28.58, 30.02, 30.18, 31.62, 31.78, 33.22, 33.38, 34.82, 34.98`, and snares at `29.38, 30.98, 32.58, 34.18`.
- [ ] **Step 2:** Run `npm test`. Expected: FAIL.
- [ ] **Step 3:** Implement. Scripts are ESM `.mjs`. The tests import them directly.
- [ ] **Step 4:** Run `npm test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(pebbles-studio): add media staging, lip-sync envelope and drum grid`.

### Task 5: Scene, podcast and band sets, captions, end card, render command

**Files:**
- Create: `src/kit/framings.ts`, `src/kit/sets/{Paper,Podcast,Band}.tsx`, `src/kit/characters/Headphones.tsx`, `src/kit/props/{Mics,DrumKit,Sticks}.tsx`, `src/kit/scene/{VideoComposition,Caption,EndCard,SafeAreaGuide}.tsx`, `src/videos/index.ts`, `scripts/render.mjs`
- Modify: `src/Root.tsx`
- Test: `test/framings.test.ts`

**Interfaces:**
- Consumes: `resolveAt`, `framesFor` (Task 3), `mouthOpen`, `envelopeFromDb`, `drumHits`, `pulse` (Task 4), `faceFor` and motion helpers (Task 2), `<Pebble>`.
- Produces (`framings.ts`):
  - `SAFE = { top: 270, bottom: 420, right: { w: 140, y0: 900, y1: 1500 } }` and `isSafe(r: Rect): boolean`.
  - `FRAMINGS: Partial<Record<SetName, Partial<Record<FrameName, Framing>>>>`, where `Framing = { place: Partial<Record<Who, { x: number; y: number; scale: number }>>; faces: Partial<Record<Who, Rect>>; caption: { y: number; maxW: number } }`.
  - `captionLayout(text: string, style: 'line'|'verse', f: Framing): { fontSize: number; lines: string[]; box: Rect }`. Wrapping is by an average glyph width of `0.52 * fontSize`. The base size is 52 for `line` and 54 for `verse`, stepping down 4 at a time (minimum 40) until the box is safe.
  - Values come from the prototype's safe layout (`TL.safe`): singles are shifted up 260px, and the band scene uses `translate(0,-275) scale(0.86)`.
- Produces: `VideoComposition` props `{ script: VideoScript; envelope: number[]; showSafeArea?: boolean }`, with `calculateMetadata` fetching `staticFile('media/<slug>/envelope.json')`. When the envelope is missing, it uses `[]` and logs one warning.
- Produces (`render.mjs`): `node scripts/render.mjs <slug>` does the following in order:
  1. stage the audio
  2. analyze it
  3. run `npx remotion render src/index.ts v-<slug> <mediaDir>/out/reel.mp4 --codec=h264 --crf=16 --pixel-format=yuv420p --color-space=bt709 --audio-codec=aac --audio-bitrate=192k`
  4. run `ffmpeg -i reel.mp4 -an -c:v copy -movflags +faststart no-sound.mp4`

- [ ] **Step 1: Write the failing tests** (`framings.test.ts`, data-driven over every entry in `FRAMINGS`):
  - `every framing keeps faces safe`: `isSafe(f.faces[who])` holds for each placed character.
  - `the longest verse stays safe`: `captionLayout(<the 95-char Spanish verse>, 'verse', f).box` is safe, with `fontSize >= 40`.
  - `a single long line stays safe`: the same check for `'skillfully scratches the spider web on the mountain'` with `'line'`.
- [ ] **Step 2:** Run `npm test`. Expected: FAIL.
- [ ] **Step 3:** Implement the framings and sets, porting `chairBack`, `chairFront`, `popMic`, `standMic`, `drumKitBack`, `drumKitFront` and `buildPaper` from `scene.js`.
  - The paper texture is generated once from a seeded RNG into an SVG `<pattern>` or a cached canvas data URL. It must be deterministic.
  - Load the caption font with `@remotion/google-fonts/Figtree` (weights 600/700). This is the builder's call from the spec.
  - Mr in `drum` pose uses `pulse` exactly as `sceneBand` does: left arm on snare and hats, right arm on crash, toms and kick.
- [ ] **Step 4:** Implement `VideoComposition`. It renders the shot's set at its framing, places characters from the framing, and passes `face` from `resolveAt` with `open` taken from `mouthOpen`. It also applies blink, breathe, settle and sway, draws `<Audio>` from `staticFile('media/<slug>/<audio>')`, the caption and the end card. It shows `SafeAreaGuide` only when `showSafeArea` is set.
- [ ] **Step 5:** Run `npm test && npm run typecheck`. Expected: PASS.
- [ ] **Step 6: Commit** `feat(pebbles-studio): add podcast and band sets, captions, end card and render command`.

### Task 6: Spanish-song remake (parity check)

**Files:**
- Create: `src/videos/2026-11-12-spanish-song/script.ts`
- Modify: `src/videos/index.ts`

**Interfaces:**
- Consumes: `video()` (Task 3) and the render command (Task 5).

- [ ] **Step 0:** Rename the prototype render so the new render can't overwrite it: in `~/Documents/Pebbles Videos/2026-11-12-spanish-song/out/`, rename `reel.mp4` → `prototype-reel.mp4` and `no-sound.mp4` → `prototype-no-sound.mp4`.
- [ ] **Step 1:** Write the script from `build_ref.mjs`, using its `CUTS` and `CAPS` arrays (`['M',20.80,FINALE]` is one cut, so Mr keeps the line until 28.47). Map `M`→`mr` with expression `smug` and `W`→`mrs` with `deadpan`. A caption change inside a cut becomes a `beat`, not a new shot, so the settle bounce doesn't repeat. Set `speaker` to the character on screen whenever there's a caption.
  - Finale: `{ at: 28.47, set: 'band', frame: 'two-shot', speaker: 'mrs', captionStyle: 'verse', drums: true, mr: { pose: 'drum' }, mrs: { expression: 'sing' } }`.
  - `drumGrid: { start: 28.58, beat: 0.4, end: 35.27 }`, `end: 35.27`, `audio: 'reference.mp4'`, `endCard: { subtitle: 'Babe, get this. Got this.' }`.
  - `sing` keeps the eyes about 70% open (lid ≤ 0.4, apart from blinks), per the owner's fix.
- [ ] **Step 2:** Run `npm test` (the registry loads and validates the script). Then run `npm run render 2026-11-12-spanish-song`. Expected: `reel.mp4` and `no-sound.mp4` in `~/Documents/Pebbles Videos/2026-11-12-spanish-song/out/`, each 38.27 s ± 0.05, checked with `ffprobe`.
- [ ] **Step 3: Parity check.** Extract frames at 1.0, 4.0, 12.0, 24.0, 27.5, 31.0 and 36.5 s from both the new `reel.mp4` and `out/prototype-reel.mp4`. Tile the frames side by side and check:
  - the same cuts and captions
  - Mr still talking at 27.5 s
  - Mrs' eyes open in the finale
  - faces clear of the safe area
  - lips moving during speech and closed in gaps
- [ ] **Step 4: Commit** `feat(pebbles-studio): remake the Spanish-song video in the kit`. Send the side-by-side image to the owner.

### Task 7: Poses, props, pose sheet

**Files:**
- Create: `src/kit/poses.ts`, `src/kit/props/{Phone,Bag,ListPaper,Mug}.tsx`, `src/sheets/PoseSheet.tsx`
- Modify: `src/kit/characters/Pebble.tsx` (body from `bodyFor`; draws `holding` at the hand; `facing: 'left'` mirrors body, arms and props but draws the face unmirrored with pupils pointed left), `src/Root.tsx`
- Test: `test/poses.test.ts`

**Interfaces:**
- Produces:
  - `POSES: Record<PoseName, (who: Who, t: number, sincePose: number) => Body>`.
  - `bodyFor(who, pose, t, sincePose): Body`.
  - `handAnchor(who: Who, body: Body): { x: number; y: number; rot: number }`, the tip of arm `body.hand` in art units.
  - `walkIn` moves the character in from off-frame over 1.0 s. It returns `dy` hops at 4 Hz with alternating `feet: 'step'`, and its x offset comes from `walkOffset(sincePose): number`, which goes from -700 to 0, ease-out.
  - `hug` is a duo pose. Each character leans 0.15 rad toward the other and its inner arm wraps across.
- Phone shows a simplified Babe, Get This list: a header bar, 3 rows with a tick on row 1, and the palette from the icon.

- [ ] **Step 1: Write the failing tests:**
  - `every pose defined for both`: each of the 13 names returns a `Body` for both `mr` and `mrs`, with finite numbers.
  - `walkIn arrives`: `walkOffset(0) === -700` and `walkOffset(1.0) === 0`.
  - `hold poses use a hand`: `handAnchor` for `holdPhone`, `holdBag` and `holdList` lies within the character's outer width + 120 art units.
- [ ] **Step 2:** Run `npm test`. Expected: FAIL.
- [ ] **Step 3:** Implement the poses and props. Build `PoseSheet` (`sheet-poses`): 13 rows by 2 characters, each with a label, with hold poses showing their prop.
- [ ] **Step 4:** Run `npm test && npm run typecheck && npm run sheets`. Expected: PASS, and `poses.png` exists. Look at it: hands meet the props, and nothing floats.
- [ ] **Step 5: Commit** `feat(pebbles-studio): add 13 poses, props and pose sheet`. Send `poses.png` to the owner.

### Task 8: Kitchen, couch and grocery-aisle sets, set sheet

**Files:**
- Create: `src/kit/sets/{Kitchen,Couch,Aisle}.tsx`, `src/sheets/SetSheet.tsx`
- Modify: `src/kit/framings.ts` (add `kitchen`: single-mr, single-mrs, two-shot; `couch`: single-mr, single-mrs, two-shot; `aisle`: single-mr, single-mrs, two-shot, wide), `src/Root.tsx`

**Interfaces:**
- Consumes: `FRAMINGS` and `captionLayout` (Task 5). The Task 5 framing tests cover the new entries automatically.

- [ ] **Step 1:** Add the framings. Run `npm test`. Expected: the Task 5 framing tests now FAIL only where a new framing is unsafe. Adjust the framings until they PASS.
- [ ] **Step 2:** Draw the sets in the flat icon palette over `Paper`:
  - kitchen: counter, fridge, a window
  - couch: sofa, lamp, a TV glow at the edge
  - aisle: shelves of simple product blocks, a trolley
  
  Characters stand (`stand` pose) in kitchen and aisle, and sit on the couch.
- [ ] **Step 3:** Build `SetSheet` (`sheet-sets`): every set × framing, cast in `neutral`, with `SafeAreaGuide` on and the longest test caption.
- [ ] **Step 4:** Run `npm test && npm run typecheck && npm run sheets`. Expected: PASS, and `sets.png` exists. Look at it: nothing important sits under the guide.
- [ ] **Step 5: Commit** `feat(pebbles-studio): add kitchen, couch and aisle sets with set sheet`. Send `sets.png` to the owner.

### Task 9: Template, README, smoke command

**Files:**
- Create: `src/videos/_template/script.ts` (a 3-shot example using a kitchen two-shot, a beat and a walk-in, with `audio: 'audio.m4a'`; it is not registered), `README.md`
- Modify: `src/Root.tsx` (remove the `debug-pebbles` still from Task 1)

- [ ] **Step 1:** Write `README.md` for a future Claude session. Cover these points:
  - Where media lives, and the rule that media is never committed.
  - How to start a video: copy `_template`, rename it `<yyyy-mm-dd>-<slug>`, put the audio in `~/Documents/Pebbles Videos/<slug>/`, and register it in `src/videos/index.ts`.
  - The full lists of expression, pose, set and framing names.
  - Script field reference, one line each.
  - How to time shots to audio: find speech onsets with `ffmpeg ... astats` as `build_ref.mjs` does.
  - `npm run render <slug>`, `npm run sheets` and `npm run smoke`.
  - Posting advice: use the no-sound file and attach the sound in the app.
- [ ] **Step 2:** Run `npm run smoke` from a fresh shell with `PEBBLES_MEDIA` pointed at an empty temp dir. Expected: the typecheck, tests and three sheet renders all PASS without any audio.
- [ ] **Step 3: Commit** `docs(pebbles-studio): add video template and how-to README`.
