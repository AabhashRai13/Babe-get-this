// Where a video's media lives (on the Mac, never in git) and how it gets into Remotion's public/ folder.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

export const KIT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');

/** ~/Documents/Pebbles Videos, or $PEBBLES_MEDIA. */
export function mediaRoot() {
  return process.env.PEBBLES_MEDIA ?? path.join(os.homedir(), 'Documents', 'Pebbles Videos');
}

export function mediaDir(slug) {
  return path.join(mediaRoot(), slug);
}

/**
 * Copy <media>/<slug>/<file> to <publicDir>/media/<slug>/<file> (git-ignored) so Remotion can serve it.
 * Throws "Audio not found: <full expected path>" if it isn't there.
 */
export function stageAudio(slug, file, publicDir = path.join(KIT, 'public')) {
  const src = path.join(mediaDir(slug), file);
  if (!fs.existsSync(src)) throw new Error(`Audio not found: ${src}`);
  const dst = path.join(publicDir, 'media', slug, file);
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  fs.copyFileSync(src, dst);
  return dst;
}
