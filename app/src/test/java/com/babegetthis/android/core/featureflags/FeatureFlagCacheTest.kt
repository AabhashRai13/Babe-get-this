package com.babegetthis.android.core.featureflags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagCacheTest {

    private val cache = FeatureFlagCache()

    @Test
    fun `an unknown flag is off`() {
        // The fetch is best-effort and comes back empty offline, so "not
        // present" has to mean "off" or a flag could switch itself on by
        // losing signal.
        assertFalse(cache.isEnabled("voice_input"))
    }

    @Test
    fun `update makes a fetched flag visible`() {
        cache.update(mapOf("voice_input" to true, "in_app_review" to false))

        assertTrue(cache.isEnabled("voice_input"))
        assertFalse(cache.isEnabled("in_app_review"))
    }

    @Test
    fun `update replaces the previous set rather than merging`() {
        cache.update(mapOf("voice_input" to true))

        cache.update(mapOf("in_app_review" to true))

        assertFalse(cache.isEnabled("voice_input"))
    }
}
