# Pebbles Studio

A Remotion kit for short Mr & Mrs Pebbles videos (Instagram Reels / TikTok, 1080×1920, 30fps).
Each video is a short data script plus an audio file; one command renders a post-ready MP4.

Design: `docs/superpowers/specs/2026-11-13-pebbles-animation-kit-design.md`.

## The one rule: media never goes in git

This repo is public. Audio, reference clips and renders live only on the Mac:

```
~/Documents/Pebbles Videos/            (override with $PEBBLES_MEDIA)
  <yyyy-mm-dd>-<slug>/
    <audio file>                       named by the script's `audio` field
    out/reel.mp4                       written by npm run render
    out/no-sound.mp4                   same picture, no audio
  _sheets/                             expression, pose and set sheets
```

`public/media/` is a git-ignored staging copy made at render time. Never commit it.

## Make a video

1. `cp -r src/videos/_template src/videos/<yyyy-mm-dd>-<slug>` and set `slug` to the folder name.
2. Put the audio in `~/Documents/Pebbles Videos/<yyyy-mm-dd>-<slug>/`.
3. Time the shots to the audio (below), and write captions and expressions.
4. Register it in `src/videos/index.ts`.
5. `npm test` (validates every registered script), then `npm run render <yyyy-mm-dd>-<slug>`.
6. Check a few frames: `ffmpeg -ss 3 -i ".../out/reel.mp4" -frames:v 1 check.png`.

To preview: `npm run studio`, then open `v-<slug>`. Set `showSafeArea` in the props panel to see the UI zones.

### Timing shots to audio

Speech onsets come from the voice-band loudness, the same pass the lip sync uses:

```
node scripts/analyze.mjs <slug> <audio>    # after staging; npm run render stages it
```

That writes `public/media/<slug>/envelope.json` (`db` per frame at 30fps). Values above about −30 dB are speech.
For a remake of a reference clip, measure its cuts and caption changes from the video, as
`docs/superpowers/specs/2026-11-13-pebbles-prototype/build_ref.mjs` did for the Spanish-song video.

## Script fields

Shot fields:

- `at`: start time in seconds. The shot runs until the next one. The first shot is at 0.
- `set`: `podcast` | `band` | `kitchen` | `couch` | `aisle`.
- `frame`: `single-mr` | `single-mrs` | `two-shot` | `wide` (`wide` is aisle only; band is two-shot only).
- `speaker`: who lip-syncs to the audio (`mr` | `mrs`). They must be on screen. Everyone else keeps their mouth shut.
- `caption`: the text above them. `captionStyle: 'verse'` is for long multi-line text.
- `mr` / `mrs`: cast state. It carries over to later shots until changed. The fields are:
  - `expression`: a name, or `{ ...partial face }` over the current one
  - `pose`
  - `look`: `camera` | `left` | `right` | `other` | `{x,y}`
  - `holding`: `phone` | `bag` | `list` | `mug` | `sticks`, or `null` to drop it
  - `headphones`: on by default in podcast and band only
  - `facing`: `left` | `right`
- `beats`: `[{ at, mr?, mrs?, caption?, speaker? }]`. These are changes inside a shot, with no cut. Expressions ease over 4 frames. Use `speaker: null` to stop the mouth moving.
- `drums: true` makes Mr drum to `drumGrid` (`{ start, beat, end }`; the groove is the first video's).

Video fields:

- `end`: where the audio stops. `endCard: { subtitle }` adds a 3 s end card after it.

Expressions: neutral, deadpan, smug, content, happy, laugh, excited, love, smirk, sideEye, eyeRoll, worried,
shock, sad, crying, annoyed, confused, sleepy, sing.

Poses: sit, stand, walkIn (walks in from the side opposite `facing`), point, wave, shrug, armsCrossed,
handsOnHips, holdPhone, holdBag, holdList, drum, hug (two-shot).

In standing sets (kitchen, aisle), `sit` is drawn as standing. On the couch and in the podcast chairs, `sit` and `stand` rest the arms on the armrests.

## Commands

| Command | What it does |
|---|---|
| `npm run render <slug>` | Stages the audio, builds the lip-sync envelope, renders `reel.mp4` (H.264 High, BT.709, AAC 44.1 kHz), then writes `no-sound.mp4`. |
| `npm run sheets` | Renders the expression, pose and set sheets to `_sheets/`. |
| `npm run smoke` | Typecheck, tests, then the sheets. Needs no audio. |
| `npm run studio` | Remotion Studio preview. |

If npm complains about a root-owned cache in `~/.npm`, add `--cache <some temp dir>` to the install.

## Posting

Use `reel.mp4` for Reels and TikTok, which take the same file. For trending sounds you don't own, post
`no-sound.mp4` and attach the sound inside the app, so the platform handles the licensing.
Faces and captions are kept out of the zones the apps cover: the top 270 px, the bottom 420 px, and the
right 140 px between y = 900 and y = 1500. `test/framings.test.ts` enforces this for every framing.

## Adding to the kit

- **Expression:** add an entry in `src/kit/expressions.ts`. It shows up on the expression sheet.
- **Pose:** add an entry in `src/kit/poses.ts`.
- **Set:** add the art in `src/kit/sets/`, framings in `src/kit/framings.ts` (the tests check them automatically), and a case in `SceneLayer`. Keep set colours warm. Nothing in the Pebbles' blue-grey range, since the characters have no outline.
