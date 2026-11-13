import { EXPRESSIONS } from '../kit/expressions';
import { POSES } from '../kit/poses';
import type { ExpressionName, PoseName, Who } from '../kit/types';

/** One PNG to export. Characters are drawn at 2x art scale on a transparent, fixed-size canvas. */
export type AssetJob =
  | { kind: 'expression'; who: Who; name: ExpressionName; file: string; width: number; height: number }
  | { kind: 'pose'; who: Who | 'duo'; name: PoseName; file: string; width: number; height: number }
  | { kind: 'background'; name: 'kitchen' | 'couch' | 'aisle' | 'paper'; file: string; width: number; height: number };

export const CHAR_W = 1400;
export const CHAR_H = 1900;
export const DUO_W = 1800;

const WHO: Who[] = ['mr', 'mrs'];

export function assetJobs(): AssetJob[] {
  const jobs: AssetJob[] = [];
  for (const who of WHO) {
    for (const name of Object.keys(EXPRESSIONS) as ExpressionName[]) {
      jobs.push({ kind: 'expression', who, name, file: `expressions/${who}-${name}.png`, width: CHAR_W, height: CHAR_H });
    }
  }
  for (const name of Object.keys(POSES) as PoseName[]) {
    if (name === 'hug') {
      jobs.push({ kind: 'pose', who: 'duo', name, file: 'poses/hug.png', width: DUO_W, height: CHAR_H });
      continue;
    }
    for (const who of WHO) jobs.push({ kind: 'pose', who, name, file: `poses/${who}-${name}.png`, width: CHAR_W, height: CHAR_H });
  }
  for (const name of ['kitchen', 'couch', 'aisle', 'paper'] as const) {
    jobs.push({ kind: 'background', name, file: `backgrounds/${name}.png`, width: 1080, height: 1920 });
  }
  return jobs;
}
