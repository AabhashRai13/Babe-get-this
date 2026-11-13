import type { VideoScript } from '../kit/types';
import spanishSong from './2026-11-12-spanish-song/script';
import whyDeceive from './2026-11-13-why-deceive/script';

/** Every registered video. Add new ones here; each becomes composition `v-<slug>`. */
export const VIDEOS: VideoScript[] = [spanishSong, whyDeceive];
