# R8 rules for the shrunk, obfuscated release build.
#
# Every block below exists because something resolves a type, member, or
# constructor BY NAME at runtime, which R8 cannot see and will therefore rename
# or remove. Rules without that justification are not rules, they are a way of
# turning shrinking off one class at a time — so each block says what breaks
# without it.
#
# Most libraries here ship their own consumer rules inside their artifacts
# (Room, Hilt, Retrofit, OkHttp). Those are applied automatically and are NOT
# repeated below. What remains is what those consumer rules do not cover.

# ---------------------------------------------------------------------------
# Crash reporting
# ---------------------------------------------------------------------------
# Without these two, every release stack trace arrives as obfuscated names with
# no line numbers and is unreadable even with the mapping file. The Crashlytics
# Gradle plugin uploads the mapping automatically; these attributes are what it
# has to work with.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kept for exception handling generally — several libraries here inspect
# generic signatures and annotations reflectively.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# The compiler plugin generates a `Companion.serializer()` and a `$$serializer`
# class per @Serializable type, and they are looked up reflectively. R8 sees no
# caller for them and strips them, which surfaces at runtime as
# "Serializer for class X is not found" — on the transcription response and on
# every Supabase row this app reads or writes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}
-keepclasseswithmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
# The generated serializer classes themselves.
-keep,includedescriptorclasses class **$$serializer { *; }

# ---------------------------------------------------------------------------
# Ktor — the HTTP engine underneath the Supabase SDK
# ---------------------------------------------------------------------------
# Ktor discovers its engine through a ServiceLoader and instantiates
# OkHttpEngineContainer by name. Stripped, Supabase auth fails at the first call
# with "Failed to find HTTP client engine implementation" rather than at build
# time.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
# Ktor and kotlinx-io reference optional JVM APIs that are absent on Android.
-dontwarn kotlinx.io.**
-dontwarn org.slf4j.**

# ---------------------------------------------------------------------------
# Supabase SDK
# ---------------------------------------------------------------------------
# Its request and response models are @Serializable and reach the network layer
# through reflection, and the realtime client decodes incoming payloads into
# them. Covered in part by the serialization rules above; kept wholesale because
# the SDK's own consumer rules do not cover its realtime message types, and a
# miss here shows up only when a shared list actually syncs.
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ---------------------------------------------------------------------------
# Retrofit — the transcription API client
# ---------------------------------------------------------------------------
# Retrofit builds its implementation from the interface's generic return types
# at runtime. Retrofit ships consumer rules for its own classes, but not for
# THIS app's service interfaces.
-keep,allowobfuscation interface com.babegetthis.android.core.voice.data.remote.TranscribeApiService
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
# Room's generated _Impl classes are loaded by name from the class that carries
# @Database. Room ships consumer rules covering this; the migration classes are
# named here because they are referenced only from a Gradle-configured array and
# an unreferenced Migration is a silently skipped upgrade rather than a crash.
-keep class com.babegetthis.android.core.data.local.Migrations { *; }
-keep class * extends androidx.room.migration.Migration { *; }

# ---------------------------------------------------------------------------
# App models
# ---------------------------------------------------------------------------
# Room entities and the DTOs crossing the network boundary are constructed
# reflectively at one end or the other. Their field NAMES are part of the
# contract, so they must not be renamed even though the classes may be.
-keepclassmembers class com.babegetthis.android.**.data.local.model.** { <fields>; }
-keepclassmembers class com.babegetthis.android.**.remote.dto.** { <fields>; }
-keepclassmembers class com.babegetthis.android.core.sync.data.model.** { <fields>; }

# ---------------------------------------------------------------------------
# Enums
# ---------------------------------------------------------------------------
# valueOf() is reflective. Room stores several of this app's enums by name.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Baseline profile installation
# ---------------------------------------------------------------------------
# profileinstaller is invoked by the framework and by the Macrobenchmark
# tooling, never by this app's own code, so R8 sees no caller and removes it —
# and the shipped baseline profile then silently never installs on the devices
# that need it installed at runtime.
-keep class androidx.profileinstaller.** { *; }
-keep class androidx.tracing.** { *; }

# ---------------------------------------------------------------------------
# Play Core in-app update
# ---------------------------------------------------------------------------
-dontwarn com.google.android.play.core.**

# Credential Manager loads its Play-services backend by reflection. Without
# this, R8 strips it and Google sign-in fails only in the minified build.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
