package com.babegetthis.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// @HiltAndroidApp tells Hilt "this is the root of the app, start here."
// It's like calling GetIt.instance.init() or wrapping your Flutter app in ProviderScope.
// Hilt generates code at compile time to wire up all your dependencies.
@HiltAndroidApp
class BabeGetThisApp : Application()
