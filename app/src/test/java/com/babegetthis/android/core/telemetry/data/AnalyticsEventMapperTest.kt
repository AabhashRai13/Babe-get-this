package com.babegetthis.android.core.telemetry.data

import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.telemetry.model.AnalyticsEvent
import com.babegetthis.android.core.telemetry.model.CategorySource
import com.babegetthis.android.core.telemetry.model.InputMethod
import com.babegetthis.android.core.telemetry.model.JoinFailureReason
import com.babegetthis.android.core.telemetry.model.Screen
import com.babegetthis.android.core.telemetry.model.VoiceFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The mapper is the only place with real logic on the analytics path, and its
// failure mode is the worst kind: everything looks fine, the events upload,
// and the data is quietly wrong or missing when someone finally queries it
// weeks later. So the tests here are mostly about the whole catalog at once
// rather than individual events.
class AnalyticsEventMapperTest {

    // One instance of every event type. Kept explicit rather than reflected
    // because data-class events need real arguments — and the coverage test
    // below makes forgetting to extend this list a failure.
    private val everyEvent: List<AnalyticsEvent> = listOf(
        AnalyticsEvent.ScreenViewed(Screen.ShoppingList),
        AnalyticsEvent.VoiceSheetOpened,
        AnalyticsEvent.VoiceRecordingStarted,
        AnalyticsEvent.VoiceRecordingFailed,
        AnalyticsEvent.VoiceRecordingCancelled(elapsedMillis = 2_000L),
        AnalyticsEvent.VoiceTranscriptionCompleted(latencyMillis = 1_500L, itemCount = 4),
        AnalyticsEvent.VoiceTranscriptionFailed(VoiceFailureReason.Network, latencyMillis = 900L),
        AnalyticsEvent.VoiceItemsSaved(itemCount = 4),
        AnalyticsEvent.ShareCodeCreated,
        AnalyticsEvent.ShareCodeShared,
        AnalyticsEvent.ShareJoinAttempted,
        AnalyticsEvent.ShareJoinSucceeded,
        AnalyticsEvent.ShareJoinFailed(JoinFailureReason.InvalidCode),
        AnalyticsEvent.SharedListFirstEditByJoiner,
        AnalyticsEvent.AccountRegistered,
        AnalyticsEvent.AccountLoggedIn,
        AnalyticsEvent.FirstItemAdded(InputMethod.Voice),
        AnalyticsEvent.FirstListCompleted(itemCount = 12),
        AnalyticsEvent.ItemAdded(InputMethod.Manual, CategorySource.Auto),
        AnalyticsEvent.ItemCheckedOff,
        AnalyticsEvent.ListCompleted(itemCount = 7),
        AnalyticsEvent.CategoryAutoAssigned("cat-dairy-eggs"),
        AnalyticsEvent.CategoryCorrected("cat-dairy-eggs", "cat-frozen-foods"),
    )

    @Test
    fun `the sample covers every event in the catalog`() {
        // If this fails, an event was added to AnalyticsEvent without being
        // added above — meaning every other test in this file silently stopped
        // covering it.
        assertEquals(
            "AnalyticsEvent gained or lost a subtype — update everyEvent",
            AnalyticsEvent::class.sealedSubclasses.size,
            everyEvent.map { it::class }.distinct().size,
        )
    }

    @Test
    fun `every event name is one GA4 will accept`() {
        // GA4 rejects or silently drops names that are too long, start with a
        // digit, contain punctuation, or use a reserved prefix. All of ours
        // are compile-time constants, so this catches a bad one before it can
        // ship rather than after the data is already missing.
        everyEvent.forEach { event ->
            val name = AnalyticsEventMapper.map(event).name
            assertTrue("invalid GA4 event name: '$name' from ${event::class.simpleName}",
                AnalyticsEventMapper.isValidEventName(name))
        }
    }

    @Test
    fun `event names are unique across the catalog`() {
        // Two events mapping to the same name merge into one indistinguishable
        // series. Nothing warns you; the numbers are simply wrong.
        val names = everyEvent.map { AnalyticsEventMapper.map(it).name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `no event exceeds GA4's parameter limit`() {
        everyEvent.forEach { event ->
            val mapped = AnalyticsEventMapper.map(event)
            assertTrue(
                "too many params on ${mapped.name}",
                mapped.text.size + mapped.numbers.size <= 25,
            )
        }
    }

    @Test
    fun `no parameter value exceeds GA4's string length limit`() {
        everyEvent.forEach { event ->
            AnalyticsEventMapper.map(event).text.forEach { (key, value) ->
                assertTrue("$key too long on ${event::class.simpleName}", value.length <= 100)
            }
        }
    }

    @Test
    fun `known taxonomy categories pass through as themselves`() {
        // These ids are ours, fixed, and shipped with the app, so they are
        // safe and meaningful in a report.
        val known = DEFAULT_CATEGORIES.first().id
        val mapped = AnalyticsEventMapper.map(AnalyticsEvent.CategoryAutoAssigned(known))
        assertEquals(known, mapped.text["category"])
    }

    @Test
    fun `user-created categories collapse to a single label`() {
        // A category the user made has a generated id and a user-authored
        // name. Sending the id would be a useless high-cardinality value; the
        // point of the collapse is that "how often does our taxonomy have no
        // home for something?" is still answerable.
        val mapped = AnalyticsEventMapper.map(
            AnalyticsEvent.CategoryAutoAssigned("8f14e45f-ceea-467a-9f3e-2a1b7c9d4e50"),
        )
        assertEquals("custom", mapped.text["category"])
    }

    @Test
    fun `an absent category is distinguishable from a custom one`() {
        val mapped = AnalyticsEventMapper.map(AnalyticsEvent.CategoryAutoAssigned(null))
        assertEquals("none", mapped.text["category"])
    }

    @Test
    fun `category corrections label both sides`() {
        val mapped = AnalyticsEventMapper.map(
            AnalyticsEvent.CategoryCorrected(
                fromCategoryId = "cat-dairy-eggs",
                toCategoryId = "not-a-real-id",
            ),
        )
        assertEquals("cat-dairy-eggs", mapped.text["from_category"])
        assertEquals("custom", mapped.text["to_category"])
    }

    @Test
    fun `counts are sent bucketed as well as raw`() {
        // The bucket is the dimension you segment by; the raw number is the
        // metric you average. GA4 cannot derive either from the other.
        val mapped = AnalyticsEventMapper.map(
            AnalyticsEvent.VoiceTranscriptionCompleted(latencyMillis = 1_200L, itemCount = 14),
        )
        assertEquals("11-20", mapped.text["item_count_bucket"])
        assertEquals(14L, mapped.numbers["item_count"])
    }

    @Test
    fun `latency is only ever sent bucketed`() {
        // Raw millisecond latency is effectively unique per event, which makes
        // it a fingerprint rather than a dimension.
        val mapped = AnalyticsEventMapper.map(
            AnalyticsEvent.VoiceTranscriptionFailed(VoiceFailureReason.Timeout, latencyMillis = 8_432L),
        )
        assertEquals("5-10s", mapped.text["latency_bucket"])
        assertTrue(mapped.numbers.isEmpty())
    }

    @Test
    fun `failure reasons are sent as bounded labels`() {
        VoiceFailureReason.entries.forEach { reason ->
            val mapped = AnalyticsEventMapper.map(
                AnalyticsEvent.VoiceTranscriptionFailed(reason, latencyMillis = 100L),
            )
            assertEquals(reason.value, mapped.text["reason"])
        }
        JoinFailureReason.entries.forEach { reason ->
            val mapped = AnalyticsEventMapper.map(AnalyticsEvent.ShareJoinFailed(reason))
            assertEquals(reason.value, mapped.text["reason"])
        }
    }

    @Test
    fun `screen views map through the enum, never a raw route`() {
        Screen.entries.forEach { screen ->
            val mapped = AnalyticsEventMapper.map(AnalyticsEvent.ScreenViewed(screen))
            assertEquals("screen_view", mapped.name)
            assertEquals(screen.screenName, mapped.text["screen_name"])
        }
    }

    @Test
    fun `route patterns resolve to screens without touching arguments`() {
        // The route carries {listName}, which is user-authored. Resolution
        // works off the pattern prefix so the argument is never read.
        assertEquals(Screen.ShoppingItems, Screen.fromRoute("shopping_items/{listId}/{listName}"))
        assertEquals(Screen.ShoppingList, Screen.fromRoute("shopping_list"))
        assertEquals(Screen.Settings, Screen.fromRoute("settings"))
        assertEquals(Screen.Login, Screen.fromRoute("login"))
        assertEquals(Screen.Register, Screen.fromRoute("register"))
        assertEquals(Screen.ForgotPassword, Screen.fromRoute("forgot_password"))
    }

    @Test
    fun `an unrecognised route reports unknown rather than leaking itself`() {
        // A screen added without updating the enum must show up as a spike in
        // "unknown", not as its raw route string in GA4.
        assertEquals(Screen.Unknown, Screen.fromRoute("some_new_screen/secret-list-name"))
        assertEquals(Screen.Unknown, Screen.fromRoute(null))
    }

    @Test
    fun `no mapped parameter value contains free text`() {
        // A blunt backstop for the privacy rule. Every value the catalog can
        // produce is an enum label, a bucket, or a taxonomy id — so none of
        // them should contain a space, which is the cheapest proxy for prose.
        everyEvent.forEach { event ->
            val mapped = AnalyticsEventMapper.map(event)
            mapped.text.forEach { (key, value) ->
                assertFalse(
                    "'$value' under '$key' on ${mapped.name} looks like free text",
                    value.contains(' '),
                )
            }
        }
    }

    @Test
    fun `mapping is total`() {
        // The `when` in the mapper is exhaustive over a sealed type, so this
        // cannot fail at runtime — but it is the assertion that would catch
        // someone converting it to a partial map with a null default.
        everyEvent.forEach { assertNotNull(AnalyticsEventMapper.map(it)) }
    }
}
