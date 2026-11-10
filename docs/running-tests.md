# Running the tests

Three suites. Most of the time you only need the first.

| Suite | Where | Needs an emulator? | Speed |
|---|---|---|---|
| Unit + Compose | `app/src/test/` | No | seconds |
| End-to-end | `app/src/androidTest/` | Yes | ~1 min |
| Database (pgTAP) | `supabase/tests/database/` | No, but needs Docker | ~1 min after the first start |

## Terminal

```bash
./gradlew testDevDebugUnitTest      # everything that doesn't need a device
./gradlew koverVerifyDevDebug       # coverage gate (fails under 100% outside the exclusions)
./gradlew connectedDevDebugAndroidTest   # end-to-end, needs an emulator running
```

One class or one test:

```bash
./gradlew testDevDebugUnitTest --tests "*ShoppingListViewModelTest*"
./gradlew testDevDebugUnitTest --tests "*ShoppingListViewModelTest.setSelectedTab*"
```

## Android Studio

- **Green ▶ in the gutter** next to a test function or class — runs just that.
- **Right-click a test folder** (e.g. `app/src/test/java` or `.../androidTest/java`,
  or a package/class inside it) → the exact wording varies by Studio version
  ("Run 'All Tests'", "Run Tests in '...'"), but it's always the top item in
  the context menu when a test source folder or class is selected.
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

**`The component was not created. Check that you have added the HiltAndroidRule`**
as a crash before any test runs — the app was in the foreground on the emulator
when the test build was installed over it, and Android relaunched its activity
under the Hilt test application. Force-stop it first:

```bash
adb shell am force-stop com.babegetthis.android.dev
```

**`NoSuchMethodException: InputManager.getInstance`** — Espresso is older than the
emulator's Android version. Bump `espressoCore` in `gradle/libs.versions.toml`.

**`Space characters in SimpleName ... not allowed prior to DEX version 040`** — a
test in `androidTest/` used a backticked name with spaces. Instrumented tests are
dexed and this app is minSdk 24, so those must be camelCase. Backticked names are
fine in `src/test/`.

## Database tests

The SQL in `supabase/migrations/` is tested with pgTAP against a local Supabase
stack. **This needs Docker** (Docker Desktop or OrbStack) and the Supabase CLI;
without Docker none of these commands can run.

```bash
supabase start            # first time pulls the images; applies migrations + seed.sql
supabase db reset --local # re-apply every migration and seed.sql to a clean database
supabase test db --local  # run every file in supabase/tests/database
```

`supabase/seed.sql` defines the helpers the tests use (`tests.make_user`,
`tests.act_as`, `tests.act_as_anon`). Each test file runs in one transaction
and rolls back, so files never depend on each other.

Always pass `--local`. The CLI can also target a linked hosted project, and the
production project must never be reset or tested from here.

CI runs the same suite in `.github/workflows/db-tests.yml` on pull requests that
touch `supabase/`.

## Coverage

`./gradlew koverHtmlReportDevDebug` → open
`app/build/reports/kover/htmlDevDebug/index.html`.

The gate covers everything that is not explicitly excluded in `app/build.gradle.kts`
— a denylist, so a new package is gated by default. The exclusions are generated
code, framework entry points and Hilt wiring, declarative Compose UI (covered by
the Compose tests instead), plain data holders, and platform wrappers the JVM
cannot run (keystore, Play services, vendor SDKs), each with its reason next to
it. The threshold is one constant in the same file; lowering it, or adding an
exclusion, has to be a visible commit.

It is a floor against *forgetting* to test something, not proof the tests are
good. A line can run without being asserted on.
