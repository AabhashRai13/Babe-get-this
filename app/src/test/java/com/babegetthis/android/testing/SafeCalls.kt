package com.babegetthis.android.testing

import com.babegetthis.android.core.error.ErrorReporter
import com.babegetthis.android.core.error.SafeCall

// A SafeCall that maps errors exactly like production but reports nothing.
// For repository tests, which are about the Result they return, not about what
// reaches Crashlytics — SafeCallReportingTest covers that side.
fun unreportedSafeCall(): SafeCall = SafeCall(ErrorReporter { _, _ -> })
