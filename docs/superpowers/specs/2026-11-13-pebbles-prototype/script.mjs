// The whole clip as data. Edit lines here and re-run build.mjs + render.mjs.
// who: M = Mr Pebbles, W = Mrs Pebbles. `say` overrides the spoken text.
// `hold` = silent reaction shot (seconds). `mood` drives the face.
export const VOICES = {
  M: { voice: 'Daniel', rate: 165 },
  W: { voice: 'Samantha', rate: 160 },
};

export const DIALOGUE = [
  { who: 'M', text: 'Grocery shopping is easy, right?', mood: 'smug' },
  { who: 'W', hold: 1.3, mood: 'stare' },
  { who: 'M', text: 'So what do I get?', mood: 'smug' },
  { who: 'W', text: 'Milk.' },
  { who: 'M', text: 'Milk.', mood: 'smug' },
  { who: 'W', text: 'Oat milk.' },
  { who: 'M', text: 'Oat milk.', mood: 'ok' },
  { who: 'W', text: 'Not the almond one.' },
  { who: 'M', text: 'Not the almond one.', say: 'Not the... almond one.', mood: 'ok' },
  { who: 'W', text: 'Eggs.' },
  { who: 'M', text: 'Eggs.', mood: 'ok' },
  { who: 'W', text: 'Free-range.', say: 'Free range.' },
  { who: 'M', text: 'Free... range.', say: 'Free... range.', mood: 'worried' },
  { who: 'W', text: 'Bananas. Not too green.' },
  { who: 'M', text: 'Not too green.', mood: 'worried' },
  { who: 'W', text: 'Not too yellow.' },
  { who: 'M', hold: 1.4, mood: 'shock' },
  { who: 'W', text: 'And the thing.', mood: 'smirk' },
  { who: 'M', text: 'What thing?', mood: 'shock' },
  { who: 'W', text: 'You know. The thing.', say: 'You know. ... The thing.', mood: 'smirk' },
  { who: 'M', text: 'Okay. All together.', mood: 'ok' },
];

// Drum finale: one bar of drums, then one sung line per bar.
export const BPM = 118;
export const VERSE = [
  'Oat milk, not almond,',
  'eggs, free-range,',
  'bananas not green, not yellow,',
  'and the thing!',
];
export const VERSE_CAPTION =
  'Oat milk, not almond, eggs, free-range, bananas not green, not yellow, and the thing!';

export const TAG = [
  { who: 'M', text: 'Babe... just put it in the app.', say: 'Babe... just put it in the app.', mood: 'ok' },
  { who: 'W', hold: 1.4, mood: 'love' },
];
export const END_CARD = 3.0;
