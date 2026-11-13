import { video } from '../../kit/timeline';

// Remake of the trending "Spanish is easy" podcast clip, on its original audio.
// Cuts and captions were measured from the reference (green-chair guy -> Mr, beige-chair guy -> Mrs).
// Mr keeps the "skillfully scratches..." line until the finale (owner fix: Mrs no longer mouths it).
// Media: ~/Documents/Pebbles Videos/2026-11-12-spanish-song/reference.mp4
export default video({
  slug: '2026-11-12-spanish-song',
  audio: 'reference.mp4',
  end: 35.27,
  endCard: { subtitle: 'Babe, get this. Got this.' },
  drumGrid: { start: 28.58, beat: 0.4, end: 35.27 },
  shots: [
    { at: 0, set: 'podcast', frame: 'single-mr', mr: { expression: 'smug' }, beats: [{ at: 0.1, caption: 'Spanish is easy, right?', speaker: 'mr' }] },
    { at: 1.7, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'deadpan' } },
    { at: 3.3, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'How do you say tomorrow in Spanish?' },
    { at: 5.43, set: 'podcast', frame: 'single-mrs', beats: [{ at: 6.5, caption: 'Mañana', speaker: 'mrs' }, { at: 7.27, caption: '', speaker: null }] },
    { at: 8.03, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Morning' },
    { at: 8.77, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Mañana', beats: [{ at: 9.5, caption: '', speaker: null }] },
    { at: 9.63, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Spider' },
    { at: 10.27, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Araña' },
    { at: 11.1, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Scratches' },
    { at: 11.73, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Scratches', beats: [{ at: 12.0, caption: 'Araña', speaker: 'mrs' }, { at: 12.6, caption: '', speaker: null }] },
    { at: 12.83, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Skill', beats: [{ at: 13.17, caption: '', speaker: null }] },
    { at: 13.53, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Maña', beats: [{ at: 14.2, caption: '', speaker: null }] },
    { at: 14.47, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Spiderweb' },
    { at: 15.13, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Telaraña' },
    { at: 15.87, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Telaraña', beats: [{ at: 16.07, caption: 'Strange', speaker: 'mr' }] },
    { at: 16.57, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Extraña' },
    { at: 17.37, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Extraña', beats: [{ at: 17.6, caption: 'Spain', speaker: 'mr' }] },
    { at: 18.23, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'España' },
    { at: 18.93, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'España', beats: [{ at: 19.07, caption: 'Mountain', speaker: 'mr' }] },
    { at: 19.63, set: 'podcast', frame: 'single-mrs', speaker: 'mrs', caption: 'Montaña' },
    { at: 20.8, set: 'podcast', frame: 'single-mr', beats: [{ at: 20.87, caption: 'Try this', speaker: 'mr' }, { at: 21.73, caption: 'Tomorrow morning', speaker: 'mr' }, { at: 23.43, caption: 'the strange spider from Spain', speaker: 'mr' }, { at: 25.43, caption: 'skillfully scratches the spider web on the mountain', speaker: 'mr' }] },
    { at: 28.47, set: 'band', frame: 'two-shot', speaker: 'mrs', captionStyle: 'verse', drums: true,
      caption: 'Mañana por la mañana, la extraña araña de España araña con maña la telaraña de la montaña',
      mr: { pose: 'drum', expression: 'content' }, mrs: { expression: 'sing' } },
  ],
});
