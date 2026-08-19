# 001 — Ship the home screen widget for v1, defer real-time list sharing to v2

**Status:** Superseded by [002 — Voice-to-list replaces the home widget as the v1 marquee feature](002-voice-to-list-replaces-widget-for-v1.md) (2026-05-05)
**Date:** 2026-05-01
**Area:** First release scope; partner-sharing roadmap

> **Superseded.** The widget is no longer v1's marquee feature. Voice-to-list takes that slot; the widget moves to v2 alongside partner real-time sync. The reasoning below is preserved for historical context — see 002 for the current decision and why it changed.

## Context

BabeGetThis is positioned as a shared grocery list app for couples. The marquee feature on paper is **real-time partner sharing** — both people see the same list, edits sync instantly, items appear/disappear as the partner shops.

That feature requires backend infrastructure (auth, realtime sync, conflict resolution, an invite/code flow). The backend is being built by a separate developer and is **not ready**. Waiting on it would block release for an indeterminate amount of time.

The app today is a fully functional offline-first grocery list:

- Create / rename / delete lists
- Add / edit / delete / mark-picked-up items
- Active vs Completed tabs
- Local-only auth scaffold (login / register routes exist but resolve against a fake repository in dev)

We need a v1 that:

1. Ships *now*, with no dependency on the backend.
2. Solves a real user moment, not a checklist of features.
3. Differentiates the app from a generic Flutter/cross-platform notes-style list.

## Decision

For v1 we will build a **home screen widget** (Jetpack Glance) that shows a single active shopping list and lets the user toggle items as picked-up directly from the home screen. Tapping anywhere else on the widget opens the app at that list.

We will **not** ship partner real-time sharing in v1. It moves to v2, gated on backend readiness.

We will also **not** ship a system Share Intent receiver in v1. It was considered as a smaller "ingest external text into a list" feature, and rejected as glorified-deeplink that does not address the core in-store moment.

## What we want to accomplish

1. **Solve the in-store moment.** The hardest part of using a grocery app is in the supermarket: basket in one hand, phone in the other, screen turning off, fumbling to unlock and re-find the list every aisle. A widget collapses that workflow to "wake phone → tap row." The app's value is not in list creation, it is in list *consumption*.
2. **Ship without backend dependency.** The widget reads from the local Room database. Nothing on the widget path needs the API to exist.
3. **Establish a defensible answer to "why native, not Flutter?"** Home widgets in Flutter require dropping into native code anyway; Glance is the native-Compose path with no plugin abstraction. The widget is the cleanest demonstration that this app earns its native stack.
4. **Set the foundation for v2 partner sync without rework.** When the backend lands, partner edits become Room writes (via a sync worker / socket service). The widget already redraws on Room changes, so partner activity surfaces automatically. v2 only adds *highlighting* of partner-driven changes — not new widget plumbing.

## Alternatives considered

### Alternative A — Wait for backend, ship list sharing as v1
Aligns the marquee feature with the launch. But ship date becomes "whenever the backend is done," which is not a date. We would also be shipping a v1 that does the *same thing as every other shared-list app on the market*, with no native differentiation. Rejected: blocks release on a dependency we do not control, and produces an undifferentiated launch.

### Alternative B — System Share Intent receiver ("share text from any app into a list")
Considered as a smaller-scope native-only feature. Useful for the recipe / partner-message ingest case ("WhatsApp text → parsed items"), but does not solve the in-store moment, and the partner-sharing primitive will be code-based invites anyway, not OS share-sheet. Rejected for v1 as "glorified deeplink." May revisit post-launch if user feedback flags ingest friction.

### Alternative C — App Shortcuts (long-press app icon → quick add item)
Saves one tap. Does not change what the user can do, only how fast they do it. Rejected as filler.

### Alternative D — Persistent shopping notification with checkable item actions
Solves the same in-store moment as the widget, via a different surface (lock screen / shade). More technically interesting (RemoteInput, foreground service, BroadcastReceiver-to-Room wiring) but materially bigger scope, overlaps with the widget's value, and risks delaying launch. Deferred to v2 as a complement to, not a replacement for, the widget.

## Plan

Concrete steps, in order. Each is tracked as a task in the working session.

1. **Add Glance dependencies** to `gradle/libs.versions.toml` and `app/build.gradle.kts`. Sync.
2. **Receiver + manifest registration.** Create `GlanceAppWidgetReceiver` subclass and `res/xml/shopping_list_widget_info.xml`. Register receiver in `AndroidManifest.xml` so the widget appears in the launcher's widget picker.
3. **Widget UI (`provideGlance`)**. Header with list name, `LazyColumn` of items, each row showing a checkbox + item name. Reads from Room via Hilt `EntryPoint`.
4. **Toggle action.** `ActionRunCallback` that flips `isPickedUp` for the tapped item via `ShoppingItemRepository.togglePickedUp`, then refreshes the widget.
5. **Open-app affordance.** Tap on header / non-checkbox area uses `actionStartActivity` with a deeplink intent to land on `ShoppingItemsScreen` for that list.
6. **App-side refresh trigger.** When items change inside the app, call `ShoppingListWidget().updateAll(context)` so the widget reflects in-app edits immediately, not on its own schedule.
7. **List selection.** Persist a single `widget_target_list_id` in DataStore. v1 default: most-recently-opened list. Empty state if none. A proper widget configuration screen is v1.5.

## Consequences

### Positive
- Ships without backend dependency.
- v1 solves the in-store moment, which is the actual job of the app.
- Native differentiation is concrete and demo able, not a slide.
- Widget architecture is forward-compatible with v2 partner sync (Room is the integration point).

### Negative / known tradeoffs

- **Marquee feature ships in v2, not v1.** The product story at launch is "best offline grocery widget," not "shared list with your partner." Marketing copy and store listing must match.
- **Widget complexity has a learning cost.** Glance is similar-but-not-equal to Compose; Hilt does not work in widgets out of the box (requires `EntryPoint` accessor pattern). Estimated 3–4 days, but unfamiliar territory may stretch this.
- **Single-list widget for v1.** Multi-list / picker-driven widget is deferred. Acceptable because most couples maintain one active list at a time; if usage data says otherwise, this revisits quickly.
- **Widget-to-Room coupling is direct.** Widget action callbacks call the repository directly (via `EntryPoint`), not through a ViewModel. This is the right pattern for widgets but is a layering exception worth documenting if the repository surface grows.

## Success criteria

We consider this decision validated if, after launch:

- A meaningful share of active users pin the widget to their home screen.
- In-app session length *drops* for shopping events while completion rate stays flat or rises (the user is doing the work on the widget instead of in the app — a good thing).
- The "why native" question gets a concrete demo answer in interviews and conversations.

## When to revisit

- Backend readiness changes timeline assumptions → re-evaluate whether v2 (real-time sharing + widget partner-highlights) should ship together or separately.
- Usage data shows the widget is not being used → investigate discoverability (do users know it exists?) before concluding the bet was wrong.
- A second active list per user becomes common → bring forward the widget configuration screen and multi-list support.
- iOS port is on the table → revisit whether a native Android-only widget is still the right marquee feature, or whether parity pressure pushes toward a cross-platform real-time-sync feature instead.
