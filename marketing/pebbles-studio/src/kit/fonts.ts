import { loadFont } from '@remotion/google-fonts/Figtree';

// Figtree (free, Google Fonts) stands in for the prototype's Avenir Next, which is a macOS system font.
export const { fontFamily: CAPTION_FONT } = loadFont('normal', {
  weights: ['500', '600', '700', '800', '900'],
  subsets: ['latin', 'latin-ext'],
});
