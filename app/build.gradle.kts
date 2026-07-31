import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
    alias(libs.plugins.kover)
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
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.babegetthis.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.0-beta"

        // Custom runner so instrumented tests boot HiltTestApplication instead of
        // BabeGetThisApp — that is what allows @TestInstallIn modules to replace
        // the production DI graph.
        testInstrumentationRunner = "com.babegetthis.android.testing.HiltTestRunner"

        // Supabase config, exposed to Kotlin as BuildConfig.SUPABASE_URL / _ANON_KEY.
        // Lives in defaultConfig (not per-flavor) because all flavors point at the
        // same Supabase project for now. If we add separate dev/prod Supabase
        // projects later, these move into the productFlavors blocks like BASE_URL.
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
            buildConfigField("String", "BASE_URL", "\"https://babegetthisapis-production.up.railway.app/\"")
            // WS_URL is a placeholder — websockets aren't implemented yet. Repointed
            // off the dead babegetthis.com domains to the live Railway host so it
            // isn't misleading; revisit the exact /ws path when realtime sync lands.
            buildConfigField("String", "WS_URL", "\"wss://babegetthisapis-production.up.railway.app/ws\"")
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
val COVERAGE_THRESHOLD = 100

kover {
    reports {
        // Kover 0.8 allows exactly ONE filter set per report variant — a rule
        // cannot narrow it further. So this include-list serves double duty: it
        // is both what the report shows and what the gate measures. That means
        // the report IS the gated surface, which is the honest reading anyway.
        //
        // This include-list is the definition of "logic layer": code that can be
        // logically wrong and is therefore worth 100%.
        filters {
            includes {
                classes(
                    "com.babegetthis.android.**.ui.viewModels.*",
                    "com.babegetthis.android.**.ui.*ViewModel",
                    "com.babegetthis.android.**.data.repository.*",
                    "com.babegetthis.android.**.data.mapper.*",
                    "com.babegetthis.android.core.error.*",
                    "com.babegetthis.android.core.util.*",
                    "com.babegetthis.android.core.pin.data.*",
                    "com.babegetthis.android.core.auth.data.*",
                    "com.babegetthis.android.core.auth.ui.AuthValidationKt",
                    "com.babegetthis.android.**.share.*",
                )
            }
            // Everything NOT in the include-list above is out of the gate. The
            // notable ones, each for a stated reason:
            //
            //   *.ui.* composables  — no branchy logic; covered by Compose tests
            //                         (see specs/compose-ui-test-suite). Excluded
            //                         from the NUMBER, not from testing.
            //   *.di.*              — Hilt wiring; a test would assert the graph
            //                         compiles, which the compiler already does.
            //   *.ui.theme.*        — colour/type constants, no behavior.
            //   *.model.*           — data holders; mappers are gated instead.
            //   *.navigation.*      — route wiring; covered by the e2e suite.
            //   MainActivity / App  — framework entry points; e2e covers them.
            //
            // If conditional logic ever appears inside an excluded package, the
            // fix is to extract it into a gated one, NOT to widen this list.
            //
            // These excludes are generated code that the include-list would
            // otherwise sweep in — no author wrote these lines.
            excludes {
                classes(
                    "*_Factory", "*_Factory\$*",           // Dagger/Hilt generated
                    "*_HiltModules*", "*Hilt_*",           // Hilt generated
                    "*_Impl", "*_Impl\$*",                 // Room generated DAOs/DB
                    "*ComposableSingletons*",              // Compose compiler generated
                    "*\$\$serializer",                     // kotlinx.serialization generated

                    // Untestable on the JVM, not untested — both are covered by
                    // instrumented tests instead, and neither holds logic that a
                    // JVM test could meaningfully exercise.
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
                )
            }
        }

        verify {
            rule("Logic layer line coverage") {
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
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
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
    implementation(libs.ktor.client.okhttp)

    // Play Core in-app update (flexible/immediate flow).
    implementation(libs.play.app.update.ktx)
}
