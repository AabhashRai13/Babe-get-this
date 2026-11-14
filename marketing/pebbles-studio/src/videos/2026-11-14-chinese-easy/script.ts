import { video } from '../../kit/timeline';

// Remake of the "Chinese is easy, right?" panda podcast (PanPan), on its original audio.
// Learner (green chair) -> Mr Pebbles; teacher (red chair, later the singer) -> Mrs Pebbles.
// Cuts, word timings and the vocab split were measured from the reference (ffmpeg scene + caption-band changes).
// Captions: spoken lines and single vocab words only. The song's lyrics are not captioned (they are the
// creator's lyrics); the concert shows a LIVE sign instead.
// Media: ~/Documents/Pebbles Videos/2026-11-14-chinese-easy/reference.mp4
export default video({
  slug: '2026-11-14-chinese-easy',
  audio: 'reference.mp4',
  end: 57.85,
  endCard: { subtitle: 'Put it on the list.' },
  drumGrid: { start: 41.6, beat: 0.5, end: 53.1 },
  shots: [
    // "Chinese easy, right?"
    { at: 0, set: 'podcast', frame: 'single-mr', captionStyle: 'popYellow', mr: { expression: 'smug' },
      beats: [
        { at: 0.85, speaker: 'mr', caption: 'Chinese' },
        { at: 2.57, caption: 'Easy,' },
        { at: 3.03, caption: 'Right?', captionStyle: 'pop' },
      ] },
    { at: 3.5, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'deadpan' } },
    // "Do morning in Chinese?"
    { at: 5.4, set: 'podcast', frame: 'single-mr', captionStyle: 'pop', mr: { expression: 'content' },
      beats: [
        { at: 5.57, speaker: 'mr', caption: 'Do' },
        { at: 5.9, caption: 'Morning', captionStyle: 'popYellow' },
        { at: 6.5, caption: 'In', captionStyle: 'pop' },
        { at: 6.9, caption: 'Chinese?', captionStyle: 'popYellow' },
      ] },
    // 早
    { at: 7.7, set: 'podcast', frame: 'single-mrs', captionStyle: 'hanzi',
      beats: [{ at: 9.3, speaker: 'mrs', caption: '早|zǎo' }] },
    // Vocab round: English on top (Mr), Chinese below (Mrs).
    { at: 10.8, set: 'podcast', frame: 'split', mr: { expression: 'happy' },
      beats: [
        { at: 11.0, speaker: 'mr', captions: { mr: { text: 'Bath', style: 'label' }, mrs: null } },
        { at: 11.4, speaker: 'mrs', captions: { mrs: { text: '澡|zǎo', style: 'hanzi' } } },
        { at: 11.73, speaker: 'mr', captions: { mr: { text: 'Bun', style: 'label' }, mrs: null } },
        { at: 12.2, speaker: 'mrs', captions: { mrs: { text: '包|bāo', style: 'hanzi' } } },
        { at: 12.53, speaker: 'mr', captions: { mr: { text: 'Full', style: 'label' }, mrs: null } },
        { at: 12.97, speaker: 'mrs', captions: { mrs: { text: '饱|bǎo', style: 'hanzi' } } },
        { at: 13.3, speaker: 'mr', captions: { mr: { text: 'Hug', style: 'label' }, mrs: null } },
        { at: 13.77, speaker: 'mrs', captions: { mrs: { text: '抱|bào', style: 'hanzi' } } },
        { at: 14.1, speaker: 'mr', captions: { mr: { text: 'Hat', style: 'label' }, mrs: null } },
        { at: 14.57, speaker: 'mrs', captions: { mrs: { text: '帽|mào', style: 'hanzi' } } },
        { at: 14.9, speaker: 'mr', captions: { mr: { text: 'Run', style: 'label' }, mrs: null } },
        { at: 15.33, speaker: 'mrs', captions: { mrs: { text: '跑|pǎo', style: 'hanzi' } } },
        { at: 15.67, speaker: 'mr', captions: { mr: { text: 'Sleep', style: 'label' }, mrs: null } },
        { at: 16.13, speaker: 'mrs', captions: { mrs: { text: '睡觉|shuìjiào', style: 'hanzi' } } },
        { at: 16.47, speaker: 'mr', captions: { mr: { text: 'Panda', style: 'label' }, mrs: null } },
        { at: 16.93, speaker: 'mrs', captions: { mrs: { text: '熊猫|xióngmāo', style: 'hanzi' } } },
      ] },
    { at: 17.27, set: 'podcast', frame: 'split-close', speaker: 'mr', mr: { expression: 'smug' },
      captions: { mr: { text: 'PANDA', style: 'popYellow' } },
      beats: [{ at: 17.7, speaker: 'mrs', captions: { mrs: { text: '熊猫|xióngmāo', style: 'hanzi' } } }] },
    { at: 18.53, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'sideEye' } },
    // "Try this..." (his attempt is the song's lyrics: not captioned)
    { at: 20.03, set: 'podcast', frame: 'single-mr', speaker: 'mr', captionStyle: 'pop', caption: 'Try', mr: { expression: 'smug' },
      beats: [
        { at: 20.43, caption: 'This', captionStyle: 'popYellow' },
        { at: 21.4, caption: '' },
        { at: 21.9, mr: { expression: 'excited' } },
        { at: 24.0, mr: { expression: 'happy' } },
        { at: 26.0, mr: { expression: 'smug' } },
      ] },
    { at: 28.43, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'smirk', glasses: true } },
    { at: 29.37, set: 'podcast', frame: 'single-mr', speaker: 'mr', mr: { expression: 'excited' } },
    // The concert.
    { at: 31.43, set: 'stage', frame: 'single-mrs', speaker: 'mrs',
      mrs: { expression: 'sing', pose: 'stand', holding: 'lute', glasses: true } },
    { at: 38.1, set: 'stage', frame: 'close-mrs', speaker: 'mrs' },
    { at: 41.6, set: 'stage', frame: 'two-shot', speaker: 'mrs', drums: true,
      mr: { pose: 'drum', expression: 'content', glasses: true } },
    { at: 43.3, set: 'stage', frame: 'close-mrs', speaker: 'mrs' },
    { at: 47.5, set: 'stage', frame: 'single-mrs', speaker: 'mrs' },
    { at: 50.8, set: 'stage', frame: 'close-mrs', speaker: 'mrs' },
    // Big nap.
    { at: 53.1, set: 'stage', frame: 'two-shot', speaker: 'mrs',
      beats: [
        { at: 54.2, speaker: null,
          mr: { pose: 'asleep', expression: { lid: 1, smile: 0.3, effect: 'zzz' } },
          mrs: { pose: 'asleep', expression: { lid: 1, smile: 0.3, effect: 'zzz' } } },
      ] },
  ],
});
