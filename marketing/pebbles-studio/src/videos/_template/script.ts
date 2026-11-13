import { video } from '../../kit/timeline';

// Template: copy this folder to src/videos/<yyyy-mm-dd>-<slug>/, change the slug, put the audio in
// ~/Documents/Pebbles Videos/<yyyy-mm-dd>-<slug>/, then add it to src/videos/index.ts.
// Not registered: it is an example only.
export default video({
  slug: 'yyyy-mm-dd-template',
  audio: 'audio.m4a',
  end: 6,
  endCard: { subtitle: 'Put it in the app.' },
  shots: [
    // Mrs walks into the kitchen holding the list; Mr is already there.
    { at: 0, set: 'kitchen', frame: 'two-shot', speaker: 'mrs', caption: 'Babe, get this.',
      mr: { expression: 'content', look: 'other' },
      mrs: { pose: 'walkIn', facing: 'left', holding: 'list', expression: 'smug', look: 'other' },
      beats: [
        { at: 1.2, mrs: { pose: 'holdList' } },
        { at: 1.6, speaker: 'mr', caption: 'Got this.', mr: { expression: 'happy', pose: 'holdPhone', holding: 'phone' } },
      ] },
    // Cut to Mr's reaction in the aisle.
    { at: 3, set: 'aisle', frame: 'single-mr', mr: { expression: 'shock', pose: 'shrug', holding: null } },
    { at: 4.5, set: 'aisle', frame: 'wide', speaker: 'mrs', caption: 'And the thing.', mrs: { expression: 'smirk', pose: 'stand', holding: null } },
  ],
});
