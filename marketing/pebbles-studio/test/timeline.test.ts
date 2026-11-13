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

  it('rejects a frame the set does not have', () => {
    const s = fixture();
    s.shots[2].frame = 'wide'; // kitchen has no wide framing
    expect(validateScript(s).join(' ')).toMatch(/shot 2: no framing wide in set kitchen/);
  });

  it('rejects unknown expression, pose and prop names in shots and beats', () => {
    const s = fixture();
    (s.shots[0].mr as Record<string, unknown>).expression = 'sideeye';
    (s.shots[1].beats![0].mrs as Record<string, unknown>).pose = 'dance';
    (s.shots[2] as unknown as Record<string, unknown>).mrs = { holding: 'cake' };
    const errs = validateScript(s).join(' ');
    expect(errs).toMatch(/shot 0: unknown expression sideeye/);
    expect(errs).toMatch(/shot 1: beat at 3: unknown pose dance/);
    expect(errs).toMatch(/shot 2: unknown prop cake/);
  });

  it('rejects a caption too long to fit above the faces', () => {
    const s = fixture();
    s.shots[0].caption = 'word '.repeat(80).trim();
    expect(validateScript(s).join(' ')).toMatch(/shot 0: caption covers a face/);
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

  it('a beat can change or clear the speaker without a cut', () => {
    const t = video({ ...fixture(), shots: [{ at: 0, set: 'couch', frame: 'two-shot', speaker: 'mr',
      beats: [{ at: 1, speaker: 'mrs' }, { at: 2, speaker: null }] }] });
    expect(resolveAt(t, 0.5).cast.mr!.speaking).toBe(true);
    expect(resolveAt(t, 1.5).cast.mr!.speaking).toBe(false);
    expect(resolveAt(t, 1.5).cast.mrs!.speaking).toBe(true);
    expect(resolveAt(t, 2.5).cast.mrs!.speaking).toBe(false);
  });

  it('rejects a beat speaker who is off screen', () => {
    const s = fixture();
    s.shots[1].beats![0].speaker = 'mr';
    expect(validateScript(s).join(' ')).toMatch(/shot 1.*not on screen/);
  });

  it('holding: null drops the prop, even after the JSON round-trip Remotion does on props', () => {
    const t = video({ ...fixture(), shots: [
      { at: 0, set: 'kitchen', frame: 'single-mr', mr: { holding: 'phone' } },
      { at: 2, set: 'kitchen', frame: 'single-mr', mr: { holding: null } },
    ] });
    const viaJson = JSON.parse(JSON.stringify(t));
    expect(resolveAt(viaJson, 1).cast.mr!.holding).toBe('phone');
    expect(resolveAt(viaJson, 3).cast.mr!.holding).toBeUndefined();
  });

  it('a new expression brings back its own pupils after an earlier look', () => {
    const t = video({ ...fixture(), shots: [
      { at: 0, set: 'couch', frame: 'two-shot', mr: { look: 'other' } },
      { at: 2, set: 'couch', frame: 'two-shot', mr: { expression: 'eyeRoll' } },
    ] });
    expect(resolveAt(t, 1).cast.mr!.face.look).toEqual({ x: 12, y: 2 });
    expect(resolveAt(t, 3).cast.mr!.face.look).toEqual(faceFor('eyeRoll').look);
  });

  it('look: null clears an earlier look', () => {
    const t = video({ ...fixture(), shots: [
      { at: 0, set: 'couch', frame: 'two-shot', mr: { expression: 'smug', look: 'other' } },
      { at: 2, set: 'couch', frame: 'two-shot', mr: { look: null } },
    ] });
    expect(resolveAt(JSON.parse(JSON.stringify(t)), 3).cast.mr!.face.look).toEqual(faceFor('smug').look);
  });

  it('setting walkIn again restarts the walk', () => {
    const t = video({ ...fixture(), shots: [
      { at: 0, set: 'kitchen', frame: 'single-mr', mr: { pose: 'walkIn' } },
      { at: 4, set: 'kitchen', frame: 'single-mrs' },
      { at: 5.5, set: 'kitchen', frame: 'single-mr', mr: { pose: 'walkIn' } },
    ] });
    expect(resolveAt(t, 5.6).cast.mr!.poseSince).toBe(5.5);
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
