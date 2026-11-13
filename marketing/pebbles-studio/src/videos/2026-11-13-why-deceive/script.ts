import { video } from '../../kit/timeline';

// Trend: "Why would I deceive you?" (deadpan line, setup text on top for the whole clip).
// Mrs walks into the shop "just for milk" -> wide reveal of the overflowing trolley -> deadpan close-up
// for the line -> Mr's side-eye at the trolley.
// Audio: placeholder.m4a is a Mac TTS stand-in with the line at 3.0 s. When the real clip arrives,
// re-time the close-up (2.9) and the speaking beats to its speech onset (npm run analyze <slug>).
const SETUP = 'Me: "Just popping in for milk"';

export default video({
  slug: '2026-11-13-why-deceive',
  audio: 'placeholder.m4a',
  end: 6.8,
  endCard: { subtitle: 'Put it on the list.' },
  shots: [
    { at: 0, set: 'aisle', frame: 'single-mrs', caption: SETUP,
      mrs: { pose: 'walkIn', facing: 'left', holding: 'bag', expression: 'content', look: 'left' },
      beats: [{ at: 1.1, mrs: { pose: 'holdBag', facing: 'right', look: 'camera' } }] },
    // The reveal: she's next to a trolley that is very much not just milk.
    { at: 2.0, set: 'aisle', frame: 'wide', caption: SETUP,
      mr: { pose: 'stand', expression: 'sideEye', look: { x: 16, y: 2 } },
      mrs: { pose: 'stand', holding: null, expression: 'content', look: 'camera' } },
    // The line, deadpan, close.
    { at: 2.9, set: 'aisle', frame: 'single-mrs', caption: SETUP, mrs: { expression: 'deadpan', look: 'camera' },
      beats: [{ at: 3.0, speaker: 'mrs' }, { at: 4.4, speaker: null }] },
    // Mr: eyes to the trolley... then back to her.
    { at: 4.7, set: 'aisle', frame: 'single-mr', caption: SETUP, mr: { expression: 'sideEye', look: { x: 18, y: 8 } },
      beats: [{ at: 5.5, mr: { look: { x: 16, y: 0 }, expression: { lid: 0.62, smile: -0.1 } } }] },
  ],
});
