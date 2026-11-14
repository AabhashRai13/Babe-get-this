import { loadFont } from '@remotion/google-fonts/Figtree';

// Figtree (free, Google Fonts) stands in for the prototype's Avenir Next, which is a macOS system font.
export const { fontFamily: CAPTION_FONT } = loadFont('normal', {
  weights: ['500', '600', '700', '800', '900'],
  subsets: ['latin', 'latin-ext'],
});

// Big shouted single words (the "CHINESE / EASY / RIGHT?" style).
import { loadFont as loadAnton } from '@remotion/google-fonts/Anton';
export const { fontFamily: POP_FONT } = loadAnton('normal', { weights: ['400'], subsets: ['latin'] });

// Chinese characters for hanzi captions.
import { loadFont as loadNotoSC } from '@remotion/google-fonts/NotoSansSC';
export const { fontFamily: HANZI_FONT } = loadNotoSC('normal', { weights: ['700'], subsets: ['chinese-simplified'] });
