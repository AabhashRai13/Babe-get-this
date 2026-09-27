# `:baselineprofile`

A Macrobenchmark module that does two jobs.

## 1. Generates the baseline profile that ships in the release build

`BaselineProfileGenerator` drives app startup and a list scroll. The plugin runs
it against `prodNonMinifiedRelease` — release without shrinking — and AGP maps
the resulting method names through R8's mapping file when packaging the real
release. Generating against an unminified build is correct here, not a shortcut.

```bash
./gradlew :app:generateProdReleaseBaselineProfile
```

The output is committed at `app/src/prodRelease/generated/baselineProfiles/`, so
ordinary builds and CI need no device.

**Regenerate when the startup path changes materially** — a new first screen, new
blocking initialisation, a change to what the list screen renders. A stale
profile is not harmful, only less effective, so this is a periodic chore rather
than a release blocker.

## 2. Verifies that the minified app actually works

`MinifiedSmokeTest` launches the shrunk, obfuscated app and asserts it renders.

```bash
./gradlew :baselineprofile:connectedProdBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.babegetthis.android.baselineprofile.MinifiedSmokeTest
```

This is the only automated check that runs against a fully optimized build. The
app's own `androidTest` suite cannot: it calls app internals, and R8's optimizer
removes members no shipping code path reaches, so those tests fail for reasons
that are not defects. That suite stays on `debug`.

What the smoke test proves: the app starts, Hilt builds its graph, Room opens the
database, and the first screen composes, under full shrinking and obfuscation.

What it does not prove: the network, auth, voice transcription, and realtime sync
paths. Those need conditions a smoke test cannot create, and are checked by hand
against an installed `prodBenchmarkRelease` build before a release ships.

## Measuring the profile's effect

`StartupBenchmark` measures cold start with and without the profile.

```bash
./gradlew :baselineprofile:connectedProdBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.babegetthis.android.baselineprofile.StartupBenchmark
```

**This requires a physical device.** Macrobenchmark refuses to run on an
emulator, and it is right to: emulator timings do not transfer to real hardware,
and a suppressed error here would produce a number that reads as evidence while
meaning nothing. The suppression flag exists; do not use it.

Compare `startupNoProfile` against `startupWithProfile` from the same run on the
same device. Comparing across devices says nothing.
