# Running the tests

Two suites. Most of the time you only need the first.

| Suite | Where | Needs an emulator? | Speed |
|---|---|---|---|
| Unit + Compose | `app/src/test/` | No | seconds |
| End-to-end | `app/src/androidTest/` | Yes | ~1 min |

## Terminal

```bash
./gradlew testDevDebugUnitTest      # everything that doesn't need a device
./gradlew koverVerifyDevDebug       # coverage gate (fails under 100% on logic)
./gradlew connectedDevDebugAndroidTest   # end-to-end, needs an emulator running
```

One class or one test:

```bash
./gradlew testDevDebugUnitTest --tests "*ShoppingListViewModelTest*"
./gradlew testDevDebugUnitTest --tests "*ShoppingListViewModelTest.setSelectedTab*"
```

## Android Studio

- **Green ▶ in the gutter** next to a test function or class — runs just that.
- **Right-click a folder** → Run 'All Tests'.
- Results appear in the Run panel as a clickable tree.

**Set Build Variants (bottom-left) to `devDebug`.** On a `release` variant every
Compose test fails with `Unable to resolve activity for Intent ...
ComponentActivity` — the test-manifest artifact is debug-only on purpose, so it
never ships in a release APK.

## Before running end-to-end tests

Turn animations off, or they get flaky:

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

No credentials needed. The instrumented tests replace auth, Supabase and voice
transcription with local fakes, so nothing reaches the network.

## If something goes wrong

**`INSTALL_FAILED_VERSION_DOWNGRADE`** — the emulator has a newer build than the
branch you're on. Reinstall in place instead of uninstalling:

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest
adb install -r -d app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r -d app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
```

`-r` keeps existing app data, `-d` permits the older version.

**`NoSuchMethodException: InputManager.getInstance`** — Espresso is older than the
emulator's Android version. Bump `espressoCore` in `gradle/libs.versions.toml`.

**`Space characters in SimpleName ... not allowed prior to DEX version 040`** — a
test in `androidTest/` used a backticked name with spaces. Instrumented tests are
dexed and this app is minSdk 24, so those must be camelCase. Backticked names are
fine in `src/test/`.

## Coverage

`./gradlew koverHtmlReportDevDebug` → open
`app/build/reports/kover/htmlDevDebug/index.html`.

The gate covers the logic layer only — ViewModels, repositories, mappers, error
handling, PIN, auth. Composables are excluded and covered by Compose tests
instead. The threshold is one constant in `app/build.gradle.kts`; lowering it has
to be a visible commit.

It is a floor against *forgetting* to test something, not proof the tests are
good. A line can run without being asserted on.
