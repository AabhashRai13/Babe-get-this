// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt.android.plugin) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    // Applied here, NOT `apply false` — formatting is a whole-repo concern and
    // the configuration below covers every module, including ones added later.
    alias(libs.plugins.spotless)
}

// One formatter for every module. The baseline rules live in .editorconfig at
// the repo root so an IDE reformat agrees with `spotlessApply` — the point is
// that formatting stops being something a reviewer has an opinion about.
//
// The overrides below are passed explicitly rather than left to .editorconfig
// discovery: Spotless resolves that file relative to each target, and rules set
// there were silently not reaching ktlint. Explicit beats "should be picked up".
val ktlintRules = mapOf(
    // Matches the IDE this codebase was written in. ktlint_official would
    // reformat nearly every file for wrapping rules with no correctness benefit.
    "ktlint_code_style" to "intellij_idea",
    "max_line_length" to "120",
    // Composables are PascalCase by convention; the naming rule does not know that.
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    // Disabled deliberately. The rule wants a file holding one class to be named
    // after that class, which here would rename DateGrouping.kt (grouping
    // functions plus one small enum) to TimePeriod.kt, and Haptics.kt to
    // Haptic.kt — both making the filename describe the smallest thing in the
    // file rather than what the file is for. Broader naming consistency is
    // tracked in TODO.md, where it can be decided deliberately.
    "ktlint_standard_filename" to "disabled",
)

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.claude/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**", "**/.claude/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}
