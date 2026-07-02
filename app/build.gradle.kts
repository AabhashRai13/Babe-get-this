import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android.plugin)
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

android {
    namespace = "com.babegetthis.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.babegetthis.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
}
