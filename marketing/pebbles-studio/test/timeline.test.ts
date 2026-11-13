import { describe, expect, it } from 'vitest';
import { faceFor } from '../src/kit/expressions';
import { END_CARD_SEC, framesFor, resolveAt, validateScript, video } from '../src/kit/timeline';
import type { VideoScript } from '../src/kit/types';

const fixture = (): VideoScript => ({
  slug: 'demo',
  end: 6,
  endCard: { subtitle: 'Got this.' },
  shots: [
    { at: 0, set: 'podcast', frame: 'single-mr', speaker: 'mr', caption: 'Hi', mr: { expression: 'smug' } },
    { at: 2, set: 'podcast', frame: 'single-mrs', mrs: { expression: 'deadpan' },
      beats: [{ at: 3, mrs: { expression: 'shock' }, caption: 'What?' }] },
    { at: 4, set: 'kitchen', frame: 'two-shot', speaker: 'mr', caption: 'Later' },
  ],
});

describe('validateScript', () => {
  it('accepts the fixture', () => {
    expect(validateScript(fixture())).toEqual([]);
  });

  it('rejects unsorted shots', () => {
    const s = fixture();
    s.shots[2].at = 1;
    expect(validateScript(s).join(' ')).toMatch(/shot 2/);
  });

  it('rejects first shot not at 0', () => {
    const s = fixture();
    s.shots[0].at = 0.5;
    expect(validateScript(s).join(' ')).toMatch(/shot 0/);
  });

  it('rejects end before last shot', () => {
    const s = fixture();
    s.end = 3.5;
    expect(validateScript(s).length).toBeGreaterThan(0);
  });

  it('rejects off-screen speaker', () => {
    const s = fixture();
    s.shots[1].speaker = 'mr';
    const errs = validateScript(s).join(' ');
    expect(errs).toContain('shot 1');
    expect(errs).toContain('not on screen');
  });

  it('rejects beat outside its shot', () => {
    const s = fixture();
    s.shots[1].beats![0].at = 4.5;
    expect(validateScript(s).join(' ')).toMatch(/shot 1/);
  });

  it('video() throws with the slug and shot index', () => {
    const s = fixture();
    s.shots[1].speaker = 'mr';
    expect(() => video(s)).toThrow(/demo shot 1/);
  });
});

describe('resolveAt', () => {
  const s = video(fixture());

  it('caption follows beats', () => {
    expect(resolveAt(s, 2.5).caption).toBe('');
    expect(resolveAt(s, 3.1).caption).toBe('What?');
    expect(resolveAt(s, 0.5).caption).toBe('Hi');
  });

  it('cast state carries over cuts, with defaults', () => {
    const r = resolveAt(s, 5);
    expect(r.cast.mr!.face).toEqual(faceFor('smug'));
    expect(r.cast.mrs!.face).toEqual(faceFor('shock'));
    expect(r.cast.mr!.pose).toBe('sit');
    expect(r.cast.mr!.headphones).toBe(false); // kitchen
    expect(resolveAt(s, 0.5).cast.mr!.headphones).toBe(true); // podcast
  });

  it('only on-screen characters are resolved', () => {
    expect(resolveAt(s, 0.5).cast.mrs).toBeNull();
  });

  it('expression eases over 4 frames within a shot', () => {
    const mid = resolveAt(s, 3 + 2 / 30).cast.mrs!.face.eyeScale;
    const from = faceFor('deadpan').eyeScale;
    const to = faceFor('shock').eyeScale;
    expect(mid).toBeGreaterThan(from);
    expect(mid).toBeLessThan(to);
    expect(resolveAt(s, 3 + 5 / 30).cast.mrs!.face.eyeScale).toBeCloseTo(to, 6);
  });

  it('expression snaps at a cut', () => {
    expect(resolveAt(s, 2).cast.mrs!.face).toEqual(faceFor('deadpan'));
  });

  it('only the speaker speaks', () => {
    const r = resolveAt(s, 5);
    expect(r.cast.mr!.speaking).toBe(true);
    expect(r.cast.mrs!.speaking).toBe(false);
  });

  it('look other points toward the other character', () => {
    const t = video({ ...fixture(), shots: [{ at: 0, set: 'couch', frame: 'two-shot', mr: { look: 'other' }, mrs: { look: 'other' } }] });
    const r = resolveAt(t, 1);
    expect(r.cast.mr!.face.look.x).toBe(12);
    expect(r.cast.mrs!.face.look.x).toBe(-12);
  });

  it('end card after end', () => {
    expect(resolveAt(s, 6.1).onEndCard).toBe(true);
    expect(resolveAt(s, 5.9).onEndCard).toBe(false);
  });

  it('frames include the end card', () => {
    expect(framesFor(s)).toBe(Math.ceil((6 + END_CARD_SEC) * 30));
  });
});
