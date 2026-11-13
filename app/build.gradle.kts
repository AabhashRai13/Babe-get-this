import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
    alias(libs.plugins.kover)
    alias(libs.plugins.baselineprofile)
    // Reads the per-flavor google-services.json and generates the Firebase
    // config resources. Must come before the crashlytics plugin, which depends
    // on what it generates.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// Read Supabase credentials from local.properties (which is gitignored), so the
// project URL and anon key never get committed. This is like reading from a
// .env file in Flutter. If the keys are missing we fall back to empty strings
// so the project still builds — auth calls just won't work until they're set.
// Note: the anon key is safe to ship inside the app (it's public by design);
// real protection comes from Supabase Row-Level Security, not from hiding it.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL") ?: ""
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""
// The staging flavor has its own Supabase project, so its data and its
// destructive manual tests never touch production. Empty when absent (CI
// builds only prod and dev), which fails loudly at runtime rather than
// silently pointing staging at production.
val stagingSupabaseUrl: String = localProperties.getProperty("STAGING_SUPABASE_URL") ?: ""
val stagingSupabaseAnonKey: String = localProperties.getProperty("STAGING_SUPABASE_ANON_KEY") ?: ""

// Release signing (upload key for Play App Signing). Also read from
// local.properties so the keystore path/passwords never get committed.
// Absent on machines that haven't set it up (e.g. CI, other devs) — release
// builds there just come out unsigned rather than failing configuration.
val releaseStoreFile: String? = localProperties.getProperty("RELEASE_STORE_FILE")
val releaseStorePassword: String? = localProperties.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias: String? = localProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = localProperties.getProperty("RELEASE_KEY_PASSWORD")

android {
    namespace = "com.babegetthis.android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.babegetthis.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 11
        versionName = "1.2.0"

        // Custom runner so instrumented tests boot HiltTestApplication instead of
        // BabeGetThisApp — that is what allows @TestInstallIn modules to replace
        // the production DI graph.
        testInstrumentationRunner = "com.babegetthis.android.testing.HiltTestRunner"

        // Supabase config, exposed to Kotlin as BuildConfig.SUPABASE_URL / _ANON_KEY.
        // defaultConfig holds the production project (dev and prod use it);
        // the staging flavor overrides both with its own project below.
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    // Product flavors for environment switching.
    // Like Flutter's --dart-define or --flavor flag.
    // Each flavor gets its own BASE_URL, WS_URL, and app ID suffix
    // so dev/staging/prod can be installed side-by-side on the same device.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // 10.0.2.2 = host machine's localhost from the Android emulator.
            // No "/api/" suffix — the transcribe backend serves POST /transcribe at the root.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("String", "WS_URL", "\"ws://10.0.2.2:8080/ws\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            // Railway's `staging` environment: same backend code, but it validates
            // tokens against the staging Supabase project below. Pointing staging at
            // the production backend 401s every call and signs the user out.
            buildConfigField("String", "BASE_URL", "\"https://babegetthisapis-staging.up.railway.app/\"")
            // WS_URL is a placeholder — websockets aren't implemented yet. Repointed
            // off the dead babegetthis.com domains to the live Railway host so it
            // isn't misleading; revisit the exact /ws path when realtime sync lands.
            buildConfigField("String", "WS_URL", "\"wss://babegetthisapis-production.up.railway.app/ws\"")
            buildConfigField("String", "SUPABASE_URL", "\"$stagingSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$stagingSupabaseAnonKey\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://babegetthisapis-production.up.railway.app/\"")
            // Placeholder — see staging note above. Unused until websockets land.
            buildConfigField("String", "WS_URL", "\"wss://babegetthisapis-production.up.railway.app/ws\"")
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lint as a blocking gate, in the same tier as the unit-test and coverage
    // gates. It had never run on this codebase before, so the findings that
    // existed at adoption are recorded in lint-baseline.xml rather than fixed
    // here — the gate protects new code from day one and the backlog stays
    // enumerated instead of invisible. Shrinking that baseline is tracked in
    // TODO.md.
    lint {
        abortOnError = true
        // Warnings are errors. The baseline absorbs whatever the first run
        // produced, so a large starting count is a reason for a larger
        // baseline, not a reason for a weaker gate.
        warningsAsErrors = true
        checkDependencies = true
        baseline = file("lint-baseline.xml")
        // Dependency-freshness checks are Renovate's job (see renovate.json).
        // Leaving them on means every new upstream release breaks the build for
        // a reason that has nothing to do with the commit that triggered it,
        // and baselining them means re-baselining on every upgrade. Disabled
        // here so exactly one system owns "is this dependency current".
        disable += setOf(
            "NewerVersionAvailable",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )
        // A finding that only appears in a report nobody opens is not a gate.
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged Android resources/manifest on the unit
            // test classpath — without this, inflating anything (or booting a
            // Compose rule) fails at runtime rather than at compile time.
            isIncludeAndroidResources = true
        }
    }
}

// Line-coverage floor for the logic layer. ONE constant, deliberately — lowering
// the bar has to be a visible edit here in a reviewable commit. There is no
// per-class or per-test suppression mechanism, on purpose.
//
// It is a FLOOR against omissions, not evidence the tests are good. A line can
// be executed without being asserted on; reviewers still have to read the tests.
// What it does buy is that new logic cannot land untouched by accident.
@Suppress("ktlint:standard:property-naming") // Deliberately a constant, see above.
val COVERAGE_THRESHOLD = 100

kover {
    reports {
        // Kover 0.8 allows exactly ONE filter set per report variant — a rule
        // cannot narrow it further. So this filter serves double duty: it is
        // both what the report shows and what the gate measures. That means
        // the report IS the gated surface, which is the honest reading anyway.
        //
        // DENYLIST, deliberately. Everything is gated unless excluded here, so
        // a new package is covered by default and the only way out of the gate
        // is a visible, reasoned line in this file. (It used to be an
        // include-list of "logic layer" packages, which meant a new core/foo
        // package silently bypassed verification until someone remembered to
        // add it — issue #24.)
        //
        // Each exclusion below is one of three kinds, and says which:
        //   generated  — no author wrote these lines
        //   no-logic   — nothing here can be logically wrong (wiring, constants,
        //                data holders, declarative UI); covered by other suites
        //   untestable — depends on a platform the JVM cannot provide; covered
        //                by instrumented tests instead
        // If conditional logic ever appears inside an excluded class, the fix
        // is to extract it into a gated one, NOT to widen this list.
        filters {
            excludes {
                classes(
                    // -- generated --
                    "*_Factory", "*_Factory\$*", // Dagger/Hilt
                    "*_HiltModules*", "*Hilt_*", "*_MembersInjector", // Hilt
                    "*_Impl", "*_Impl\$*", // Room DAOs/DB
                    "*ComposableSingletons*", // Compose compiler
                    "*\$\$serializer", // kotlinx.serialization
                    "hilt_aggregated_deps.*", "dagger.hilt.internal.**", // Hilt aggregation
                    "com.babegetthis.android.BuildConfig",
                    "com.babegetthis.android.R", "com.babegetthis.android.R\$*",
                    "com.babegetthis.android.DataBinderMapperImpl*",

                    // -- no-logic: framework entry points; the e2e suite drives them --
                    "com.babegetthis.android.BabeGetThisApp", "com.babegetthis.android.BabeGetThisApp\$*",
                    "com.babegetthis.android.MainActivity", "com.babegetthis.android.MainActivity\$*",
                    "com.babegetthis.android.navigation.*", // route wiring

                    // -- no-logic: Hilt wiring; the compiler proves the graph --
                    "com.babegetthis.android.**.di.*",

                    // -- no-logic: declarative Compose UI. Covered by the Compose
                    // tests (see specs/compose-ui-test-suite), excluded from the
                    // NUMBER, not from testing. ViewModels live in the same `ui`
                    // packages and stay gated: only file-level composable classes
                    // (*Kt and their lambdas), component/theme packages, and the
                    // shared UI helpers are listed.
                    "com.babegetthis.android.**.ui.*Kt", "com.babegetthis.android.**.ui.*Kt\$*",
                    "com.babegetthis.android.**.ui.components.*",
                    "com.babegetthis.android.ui.theme.*",
                    "com.babegetthis.android.core.ui.*", "com.babegetthis.android.core.ui.haptics.*",

                    // -- no-logic: data holders. Mappers are gated instead. --
                    "com.babegetthis.android.core.auth.model.*",
                    "com.babegetthis.android.core.model.*",
                    "com.babegetthis.android.core.sync.data.model.*",
                    "com.babegetthis.android.core.voice.model.*",
                    "com.babegetthis.android.core.voice.data.remote.dto.*",
                    "com.babegetthis.android.feature.*.model.*",
                    // Room entities and the schema itself. Migrations are SQL,
                    // guarded by androidTest/MigrationTest against real tables.
                    "com.babegetthis.android.**.data.local.model.*",
                    "com.babegetthis.android.**.data.local.dao.*",
                    "com.babegetthis.android.core.data.local.*",
                    // Retrofit/Supabase interface declarations and the adapters
                    // that are call-and-forward over an SDK; the e2e suite
                    // exercises them against a real backend.
                    "com.babegetthis.android.**.data.remote.*",

                    // -- untestable on the JVM; instrumented tests cover them --
                    //
                    // PinStore is a typed wrapper over EncryptedSharedPreferences,
                    // whose MasterKey requires the AndroidKeyStore provider.
                    // Robolectric has no such provider, so constructing one throws
                    // "KeyStoreException: AndroidKeyStore not found". Covered by
                    // androidTest/PinStoreTest; PinRepository's own logic is
                    // covered on the JVM against an in-memory stand-in.
                    "com.babegetthis.android.core.pin.data.PinStore",
                    // SystemPinClock is two one-line passthroughs to
                    // System.currentTimeMillis() and SystemClock.elapsedRealtime().
                    // The interface exists so tests can substitute a fake clock —
                    // testing the real one would be testing the platform.
                    "com.babegetthis.android.core.pin.data.SystemPinClock",
                    // TokenManager is the same story as PinStore: an
                    // EncryptedSharedPreferences wrapper, so AndroidKeyStore, so
                    // not constructible under Robolectric. Covered by
                    // androidTest/TokenManagerTest; AuthStateManager's logic is
                    // covered on the JVM against an in-memory stand-in.
                    "com.babegetthis.android.core.auth.data.TokenManager",
                    // SupabaseAuthRepository is an adapter over the Supabase SDK,
                    // reachable only through extension properties
                    // (`supabaseClient.auth`) that are brittle to stub. Its actual
                    // DECISION-MAKING was extracted to AuthErrorMapper.kt, which
                    // IS gated and is at 100% — per this spec's rule that logic
                    // trapped in unreachable code gets extracted rather than the
                    // exclude-list widened. What remains here is call-and-forward
                    // over the SDK, covered by the e2e auth journey.
                    "com.babegetthis.android.core.auth.data.SupabaseAuthRepository",
                    "com.babegetthis.android.core.auth.data.SupabaseAuthRepository\$*",
                    // Dev-flavour only: a stub AuthRepository so dev builds run
                    // without a backend. It never ships (app/src/dev/), and it is
                    // a development aid rather than product behaviour — testing it
                    // would be testing the test double.
                    "com.babegetthis.android.core.auth.data.FakeAuthRepository",
                    // Credential Manager needs Play services and a real Google
                    // account for its sheet. The one piece of logic in the
                    // requester — the nonce hash — is extracted to sha256Hex,
                    // which is gated; LoginViewModel's Google flow is tested
                    // through the GoogleTokenRequester seam.
                    "com.babegetthis.android.core.auth.ui.CredentialManagerTokenRequester",
                    "com.babegetthis.android.core.auth.ui.CredentialManagerTokenRequester\$*",
                    // FeatureFlagRepository is the same shape as
                    // SupabaseAuthRepository: one Postgrest call through an
                    // extension property that is brittle to stub, with
                    // "anything fails → empty" as its only branch. The cache it
                    // feeds is gated.
                    "com.babegetthis.android.core.featureflags.data.*",
                    // Vendor SDK calls with no branches: Firebase Analytics and
                    // Crashlytics. The decisions around them (AnalyticsEventMapper,
                    // ErrorReportingPolicy) are gated.
                    "com.babegetthis.android.core.telemetry.data.FirebaseAnalyticsRepository",
                    "com.babegetthis.android.core.telemetry.data.CrashlyticsCrashReporter",
                    // Play Core (in-app review, in-app update) and MediaRecorder:
                    // need Play services or a microphone.
                    "com.babegetthis.android.core.review.*",
                    "com.babegetthis.android.core.update.*",
                    "com.babegetthis.android.core.voice.data.AudioRecorder",
                    "com.babegetthis.android.core.voice.data.AudioRecorder\$*",
                    // Platform passthroughs with no branches: WorkManager enqueue
                    // calls, the NotificationManagerCompat check, the process
                    // lifecycle state, and System.currentTimeMillis(). Each sits
                    // behind an interface so the gated logic is tested against a
                    // fake instead.
                    "com.babegetthis.android.core.reminder.WorkManagerEnqueuer",
                    "com.babegetthis.android.core.reminder.AndroidNotificationGate",
                    "com.babegetthis.android.core.reminder.ProcessForegroundState",
                    "com.babegetthis.android.core.reminder.SystemReminderClock",
                    "com.babegetthis.android.feature.gotthis.reminder.WorkManagerRunningLowEnqueuer",
                )
            }
        }

        verify {
            rule("Line coverage outside the exclusions above") {
                bound {
                    minValue = COVERAGE_THRESHOLD
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile on first run for devices that do not get it
    // from Play. Without it the profile ships but is never applied on sideloads
    // and on older API levels.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Process-wide lifecycle — used to re-lock a locked list only when the whole
    // app is backgrounded, not on in-app back navigation.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    // Drives ReminderWorker and the scheduler against a synchronous WorkManager.
    testImplementation(libs.androidx.work.testing)
    // TestNavHostController drives the reminder-tap back stack without composing screens.
    testImplementation(libs.androidx.navigation.testing)
    // Robolectric lets Compose screen tests and Room DAO tests run on the JVM,
    // so CI needs no emulator for anything except the end-to-end suite.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    // Compose test artifacts on the *unit* test classpath — that's what makes
    // createComposeRule() work under Robolectric.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-manifest declares the ComponentActivity that createComposeRule()
    // launches, and it works by MERGING INTO THE APP MANIFEST — so it has to be
    // applied per build type, not via testImplementation (an AAR on the test
    // classpath contributes no manifest entry). Debug only, deliberately:
    // applying it to release would merge test scaffolding into the shipped APK.
    //
    // Consequence, and the reason CI runs testProdDebugUnitTest rather than
    // testProdReleaseUnitTest: Compose tests cannot run on a release variant.
    // They fail with "Unable to resolve activity for Intent ...
    // androidx.activity.ComponentActivity".
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    // Initialises WorkManager with the Hilt worker factory in instrumented tests.
    androidTestImplementation(libs.androidx.work.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    // Shopping reminders are scheduled through WorkManager (list sync
    // deliberately is not). hilt-work lets @HiltWorker workers take injected
    // dependencies; its compiler generates the assisted factories.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.navigation.compose)

    // Networking — Retrofit + OkHttp (like Dio in Flutter)
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Serialization — kotlinx.serialization (like json_serializable in Flutter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Secure token storage — EncryptedSharedPreferences (like flutter_secure_storage)
    implementation(libs.security.crypto)

    // Supabase — authentication now, realtime later. The BOM (platform()) pins
    // every supabase module to one compatible version. auth-kt is the login client;
    // ktor-client-okhttp is the HTTP engine it sends requests through.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    // postgrest-kt is only here for the delete_user RPC (account deletion).
    implementation(libs.supabase.postgrest)
    // realtime-kt powers live updates for shared lists.
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    // Google sign-in. play-services-auth is the Credential Manager backend on
    // devices without a built-in provider (everything below Android 14).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Play Core in-app update (flexible/immediate flow).
    implementation(libs.play.app.update.ktx)
    // Play Core in-app review, requested when a list is completed.
    implementation(libs.play.review.ktx)

    // Firebase — analytics and crash reporting. The BOM pins every firebase
    // module to one mutually-compatible version, same idea as the Compose and
    // Supabase BOMs above.
    //
    // dev and staging report to the babe-get-this-stg project, prod reports to
    // babe-get-this; each flavor resolves its own google-services.json from
    // app/src/<flavor>/. Collection stays ON in staging on purpose — it is the
    // only place we can prove the pipeline works before trusting prod numbers.
    //
    // No firebase-messaging here yet: push is a separate change, and this one
    // only creates the projects it will build on.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Consumes the profile generated by :baselineprofile and merges it into the
    // release artifact.
    baselineProfile(project(":baselineprofile"))
}
