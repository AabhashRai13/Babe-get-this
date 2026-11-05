package com.babegetthis.android.core.featureflags

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// isKnownOff separates "the fetch said off" from "no fetch has landed yet", so
// an offline cold start does not read as a remote kill switch.
class FeatureFlagCacheTest {

    @Test
    fun `an unloaded cache does not know the key is off`() {
        val cache = FeatureFlagCache()

        assertFalse(cache.isKnownOff(KEY))
        assertFalse(cache.isEnabled(KEY))
    }

    @Test
    fun `a loaded cache with the key true is not known off`() {
        val cache = FeatureFlagCache().apply { update(mapOf(KEY to true)) }

        assertFalse(cache.isKnownOff(KEY))
        assertTrue(cache.isEnabled(KEY))
    }

    @Test
    fun `a loaded cache with the key false is known off`() {
        val cache = FeatureFlagCache().apply { update(mapOf(KEY to false)) }

        assertTrue(cache.isKnownOff(KEY))
    }

    @Test
    fun `a loaded cache without the key is known off`() {
        val cache = FeatureFlagCache().apply { update(mapOf("other" to true)) }

        assertTrue(cache.isKnownOff(KEY))
    }

    private companion object {
        const val KEY = "some_flag"
    }
}
