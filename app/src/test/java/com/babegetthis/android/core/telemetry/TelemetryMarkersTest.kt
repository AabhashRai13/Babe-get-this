package com.babegetthis.android.core.telemetry

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric so the SharedPreferences are real: "once" has to survive the
// object being recreated, which is what a process restart looks like.
@RunWith(RobolectricTestRunner::class)
class TelemetryMarkersTest {

    private fun markers() = TelemetryMarkers(ApplicationProvider.getApplicationContext())

    @Test
    fun `the first claim succeeds and every later one fails`() {
        val markers = markers()

        assertTrue(markers.firstTime(Marker.FirstItemAdded, "user-1"))
        assertFalse(markers.firstTime(Marker.FirstItemAdded, "user-1"))
    }

    @Test
    fun `a claim is remembered across instances`() {
        markers().firstTime(Marker.FirstItemAdded, "user-1")

        assertFalse(markers().firstTime(Marker.FirstItemAdded, "user-1"))
    }

    @Test
    fun `scopes are independent`() {
        // A second account on the same device records its own activation.
        val markers = markers()
        markers.firstTime(Marker.FirstItemAdded, "user-1")

        assertTrue(markers.firstTime(Marker.FirstItemAdded, "user-2"))
    }

    @Test
    fun `markers are independent within a scope`() {
        val markers = markers()
        markers.firstTime(Marker.FirstItemAdded, "user-1")

        assertTrue(markers.firstTime(Marker.FirstListCompleted, "user-1"))
    }

    @Test
    fun `a signed-out user has an anonymous scope of their own`() {
        val markers = markers()

        assertTrue(markers.firstTime(Marker.FirstItemAdded, null))
        assertFalse(markers.firstTime(Marker.FirstItemAdded, null))
        assertTrue(markers.firstTime(Marker.FirstItemAdded, "user-1"))
    }

    @Test
    fun `mark records without claiming and has reads without claiming`() {
        val markers = markers()
        assertFalse(markers.has(Marker.JoinedList, "list-1"))

        markers.mark(Marker.JoinedList, "list-1")

        assertTrue(markers.has(Marker.JoinedList, "list-1"))
        assertTrue(markers.has(Marker.JoinedList, "list-1"))
    }
}
