plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.babegetthis.android.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark measures cold start, which needs a real ART profile
        // and a non-debuggable target. API 28 is the floor for that; the app
        // itself still supports 24, those devices simply get no profile.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Only the prod flavour is generated against — it is what ships, and
    // generating for dev and staging as well would triple the run for profiles
    // nobody installs.
    flavorDimensions += "environment"
    productFlavors {
        create("dev") { dimension = "environment" }
        create("staging") { dimension = "environment" }
        create("prod") { dimension = "environment" }
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The generator drives `benchmark`: minified and non-debuggable, so the
    // profile names methods that actually exist in the shipped APK. Generating
    // against a debug build produces a profile full of names R8 has since
    // renamed, which is worse than having no profile.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
