# Pebbles animation kit — design

**Date:** 2026-11-13 · **Status:** approved 2026-11-13 · **Code:** `marketing/pebbles-studio/` · **Media:** `~/Documents/Pebbles Videos/` (local only)

## Summary

A ready-made kit for making short animated videos with Mr and Mrs Pebbles,
built on Remotion (video in React code). The kit holds the two characters, a
library of named expressions and full-body poses, a handful of sets (podcast,
band, kitchen, couch, grocery aisle), the props they use, captions and a
Reels/TikTok export. A new video is one short script file plus an audio file;
one command renders a post-ready MP4.

Claude makes every video. The owner only posts the result on Instagram and
TikTok. Nobody else opens or edits the kit.

## Why

The first Pebbles video (a remake of a trending "Spanish is easy" podcast
clip) worked, but every piece was drawn from scratch for that one video. The
plan is to post animated videos often. Doing that from scratch each time is
slow, and the characters would drift off-model between videos. With the
characters, expressions, poses and sets built once, each new video is mostly
choosing pieces and timing them to audio.

## Goals

- **Fast turnaround.** A new 15–40 second video, from audio and idea to a
  post-ready MP4, should take Claude well under an hour, mostly spent on
  timing. Rendering should take a few minutes at most on this Mac.
- **On-model every time.** Mr and Mrs Pebbles always match the app icon:
  same shapes, colours, proportions and face layout. The reference is the
  icon SVG the owner supplied, saved as
  `docs/superpowers/specs/2026-11-13-pebbles-prototype/icon.svg` and copied
  into the kit as `public/icon.svg`. *(Claude's call: this is the owner's
  own artwork. The Android launcher resources are not re-derived.)*
- **A sketch kit, not one format.** It covers the podcast format from the
  first video and also everyday couple sketches: at home, in the kitchen, on
  the couch, in the shops.
- **Post-ready output.** 1080×1920 vertical, 30fps. Faces, captions and the
  end card always sit clear of the Instagram/TikTok buttons and text.
- **Easy to review.** Expression, pose and set "sheets" render as single
  stills, so the owner can approve the look of the kit at a glance.

## Non-goals

- No in-app animation (Rive or Lottie). The kit makes videos only.
- No 3D, and no AI image generation for the characters.
- No editing UI for the owner. Remotion Studio can be opened for previewing,
  but nobody is expected to edit there.
- No voice generation. Videos use audio the owner provides (trending sounds,
  voice notes) or existing tracks. Mac text-to-speech is fine for drafts only.
- No posting or scheduling. The owner posts by hand.

## Anti-goals (a failure even if it technically works)

- Characters that look different from video to video, or from the icon.
- A kit where each new video still needs new hand-drawn art for common
  situations (talking, reacting, walking in, holding a phone).
- Important content (faces, captions, punchlines) hidden under the app UI on
  Instagram or TikTok.
- Mouths that move during silence, or the wrong character mouthing a line.

## Constraints

- **Remotion license.** Remotion is free for individuals and companies of up
  to 3 people (https://www.remotion.dev/docs/license/pricing.md). The owner
  confirmed on 2026-11-13 that Babe, Get This is 3 people or fewer. If the
  team grows past that, a paid Company License is needed.
- **Runs on this Mac.** Node 24 is installed, and so is `ffmpeg`. Renders run
  locally.
- **Code in this repo, media on the Mac only.** The repo
  (`AabhashRai13/Babe-get-this`) is public. The kit's code lives in its own
  folder (`marketing/pebbles-studio/`) with its own `package.json`, kept apart
  from the Android build. All media lives outside the repo, in
  `~/Documents/Pebbles Videos/`, and is never committed: audio, reference
  clips and finished renders. Nothing under `marketing/pebbles-studio/` may
  contain audio or video files, apart from tiny generated test fixtures if a
  test needs them. `node_modules` and any temporary copies are git-ignored.
  This was the owner's decision on 2026-11-13, and losing the Mac means losing
  the media, which the owner accepted.
- **Audio rights.** Trending sounds belong to their creators. For brand
  posts, the safe route is to post the no-sound export and attach the sound
  inside Instagram or TikTok. So every render also produces a no-sound copy.

## Approaches considered

1. **Grow the custom renderer from the first video.** This is a canvas
   drawing script, driven by a headless browser and stitched with `ffmpeg`.
   It's proven and light, but hand-built, with no preview or timeline tools.
2. **Rebuild on Remotion (chosen).** The Pebbles become React components,
   and Remotion handles the timeline, audio, preview (Remotion Studio) and
   rendering. It has an official Claude Code plugin and agent skills. It's a
   sturdier foundation that's easier to grow into a kit, at the cost of
   porting what works today and a heavier toolchain. The owner chose this.
3. **Rive, Lottie or Blender.** Dropped. They mean redrawing the characters
   or living with free-tier export limits, and nobody else opens the files.

## What exists today

The first video's media is saved in `~/Documents/Pebbles Videos/`:
`2026-11-12-spanish-song/` holds the reference clip and the final Reel and
no-sound renders, and `drafts/2026-11-12-grocery-list-v1/` holds the earlier
grocery-script cut and its script.

The first video's renderer is saved in
`docs/superpowers/specs/2026-11-13-pebbles-prototype/`:

- `scene.js` holds the character geometry, taken from the icon SVG. Mrs
  Pebbles is tilted about 8° in the icon, so the prototype maps her into an
  upright frame. It also has the face drawing (eyes with lids and blink,
  pupils, brows, blush, mouth with open/smile), 8 moods, the chairs, mics,
  headphones, drum kit, paper background, captions and end card.
- `build_ref.mjs` builds the shot and caption timeline and the lip-sync
  envelope from the reference audio.
- `render.mjs` handles frame capture and the H.264/AAC encode with the
  correct colour settings.

The kit ports this geometry and behaviour. It does not redraw from scratch.

## The kit

### Characters

Two components, `<MrPebbles>` and `<MrsPebbles>`, drawn as SVG and built on
the same base. The props they take:

- `expression`: a name from the expression library, or partial overrides.
  A change of expression eases over a few frames instead of snapping.
- `pose`: a name from the pose library.
- `look`: where the pupils point (left, right, camera, or the other
  character).
- `speaking`: whether this character lip-syncs to the current audio.
- `holding`: a prop attached to a hand (phone, bag, list, mug, sticks).
- `headphones`: on or off.
- `facing`: left or right, which flips the body but keeps the face readable.
- Position and scale come from the set's framing, not from hand-placed
  numbers.

Always-on life, all deterministic per frame: blinking at a natural irregular
rate, breathing, a small settle bounce on each cut, a subtle sway.

### Expressions (first set, about 18)

neutral, deadpan, smug, content, happy, laugh, excited, love, smirk,
side-eye, eye-roll, worried, shock, sad, crying, annoyed, confused, sleepy,
plus `sing` for musical bits.

Each expression is a set of face settings: lid height and tilt, eye size,
pupil size and direction, each brow's lift and tilt, mouth shape (line,
smile, open, "o", teeth-grin, wobble), blush strength, and an optional
effect (tears, sweat drop, anger mark, sparkles, "zzz", floating hearts).
New expressions are added as new entries, not new drawing code.

### Poses (first set)

sit, stand, walk-in (a waddle hop, since Pebbles have feet but no legs),
point, wave, shrug, arms-crossed, hands-on-hips, hold-phone, hold-bag,
hold-list, drum, and a duo hug. A pose sets the arm and feet positions and
the body's tilt, squash and bounce. A held prop follows the hand.

### Sets (first set)

Each set is a background in the same flat palette and paper texture as the
first video, with named framings already placed inside the safe area:

- **podcast**: two armchairs, mics, headphones. Framings: single Mr, single
  Mrs, two-shot.
- **band**: drum kit and stand mic (the finale from the first video).
- **kitchen**: counter and fridge. Framings: single, two-shot.
- **couch**: living room with a TV glow. Framings: single, two-shot.
- **grocery aisle**: shelves and a trolley. Framings: single, two-shot,
  wide.

### Props and effects

phone (showing a simple Babe, Get This list screen), shopping bag, shopping
list, mug, trolley, drum kit, mics, headphones, hearts, sparkles, a "!" pop.

### Captions and end card

- Captions use the style of the first video: centred, bold, dark, placed by
  the framing so they stay in the safe area. The verse style is for long
  multi-line text.
- The end card shows the app icon popping in, then "Babe, Get This" and a
  per-video subtitle.

### Safe area

A guide overlay that can be switched on in Studio and in still renders,
using the zones from the first video: top 270px, bottom 420px, and the right
140px between y=900 and y=1500. Every framing must keep faces and captions
outside these zones.

## Making a video

Each video has two halves, matched by the same `<yyyy-mm-dd>-<slug>` name:

- **Script (in the repo):** `src/videos/<yyyy-mm-dd>-<slug>/script.ts`.
- **Media (on the Mac only):** `~/Documents/Pebbles Videos/<yyyy-mm-dd>-<slug>/`,
  holding the audio (and a reference clip, if the video remakes one). Renders
  go in its `out/` subfolder.

The script names its audio by file name only, and the kit finds it in the
media folder. How Remotion gets at a file outside its `public/` folder (a
git-ignored symlink or copy made at preview and render time, or a local file
server) is left to the builder. A script whose audio is missing must fail
with a clear message naming the expected path.

The script is plain data. It is not drawing code. An example of what it
says:

```ts
export default video({
  audio: 'spanish-song.m4a',
  endCard: { subtitle: 'Babe, get this. Got this.' },
  shots: [
    { at: 0.0, set: 'podcast', frame: 'single-mr', speaker: 'mr',
      caption: 'Spanish is easy, right?', mr: { expression: 'smug' } },
    { at: 1.7, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'deadpan' } },
    { at: 28.47, set: 'band', frame: 'two-shot', speaker: 'mrs',
      caption: 'Mañana por la mañana…', mr: { pose: 'drum' }, mrs: { expression: 'sing' },
      beats: [{ at: 30.0, mrs: { expression: 'love' } }] },
  ],
});
```

- A shot runs until the next shot starts. `beats` change things mid-shot
  without a cut.
- **Lip sync** comes from the audio. When the audio is one mixed track, as
  trending sounds are, the shot's `speaker` mouths it and everyone else keeps
  their mouth closed. The mouth closes during silence.
- **Drumming and music beats** come from beat times, either detected from
  the audio or listed in the script. They drive things like the drum hits
  and swaying in the first video.
- A shot with no caption is a silent reaction shot.

## Rendering

- One command per video (for example `npm run render <video>`) writes
  `~/Documents/Pebbles Videos/<video>/out/reel.mp4` and `no-sound.mp4`
  beside it.
- 1080×1920, 30fps, H.264 High profile, yuv420p, BT.709 colour tagged as TV
  range, AAC 192 kbps at 44.1 kHz, faststart. The first video showed that
  without the BT.709 tagging, colours can shift on upload.
- The same file is used for Instagram Reels and TikTok.

## Review sheets

Stills that can be rendered at any time:

- **Expression sheet:** every expression, for both characters, labelled.
- **Pose sheet:** every pose, for both characters.
- **Set sheet:** every set and framing, with the safe-area guide on.

The owner approves the expression sheet before poses and sets are built, so
the look is settled early.

## First deliverables, in order

1. The Remotion project set up, with the characters ported from the
   prototype and checked against the icon.
2. Expressions plus the expression sheet, which goes to the owner for
   sign-off.
3. Lip sync, captions, end card, safe-area guide, render command.
4. The podcast and band sets. Then remake the Spanish-song video in the kit
   and compare it side by side with the prototype's final social render
   (`~/Documents/Pebbles Videos/2026-11-12-spanish-song/out/reel.mp4`, the
   safe-area layout). The source audio is `reference.mp4` in the same
   folder. This proves nothing
   was lost in the port. *(Claude's call: the check is a visual one, by
   Claude and then the owner, at matching timestamps. It is not a
   pixel-tolerance test, so SVG may differ from the canvas prototype in small
   details such as grain and blur.)*
5. Poses plus the pose sheet.
6. The kitchen, couch and grocery-aisle sets, props, and the set sheet.
7. A short "how to make a video" note in the kit's README, written for
   Claude in a future session.

## Left to the builder

- The Remotion version and project layout inside `marketing/pebbles-studio/`.
- Whether to install Remotion's agent skills into the project's
  `.claude/skills`. This is preferred over a user-level plugin, so the user's
  Claude settings stay untouched.
- How lip sync reads the audio: Remotion's audio helpers at render time, or
  a pre-pass that writes an envelope file, as the prototype does.
- How beat times are detected, if at all, versus listed by hand.
- The caption font. The prototype used Avenir Next, a macOS system font.
  Bundling a free font with a similar feel is fine.
- The exact look of each expression, pose and set, within the style of the
  icon and the first video. The sheets are where the owner reacts.
- Tests: at least a type check and a script that renders the three sheets as
  a smoke test. The smoke test must not need any real audio from the media
  folder.
