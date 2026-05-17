# 002 — Voice-to-list replaces the home widget as the v1 marquee feature

**Status:** Accepted
**Date:** 2026-05-05
**Area:** First release scope; v1 marquee feature
**Supersedes:** [001 — Ship the home screen widget for v1](001-home-widget-over-list-sharing-for-v1.md)

## Context

Decision 001 chose the home-screen widget as v1's marquee feature, betting on the **in-store moment** — basket in one hand, phone in the other, glancing at a grocery list. The widget was sized at 3–4 days and pitched as the cleanest "why native" demonstration available without the backend.

That reasoning still holds in isolation. What changed is our read of which user-friction moment is actually the worst.

The widget improves **list consumption** — finding the list and checking items off. The harder moment in day-to-day usage of a grocery app is **capture**: the user remembers they need eggs while loading the dishwasher, while the toddler is yelling, while one hand is wet. Typing "Eggs" into a phone keyboard at that moment is the friction that causes items to never make it onto the list at all — and an item that is never captured cannot be checked off, widget or no widget.

Voice-to-list directly addresses capture. Mic tap → "milk, eggs, bananas" → confirm is achievable one-handed and eyes-off, in environments where typing is not. The technical design lives in [technical-decisions/002 — Voice-to-list](../technical-decisions/002-voice-to-list-input.md).

## Decision

For v1 we will ship **voice-to-list**. The home widget moves to v2.

Concretely:
- All v1 marquee scope, marketing copy, and "why native" answers reframe around voice capture, not widget consumption.
- The widget plan in 001 (Glance dependencies, `GlanceAppWidgetReceiver`, `EntryPoint` pattern, single-list selector) is parked, not deleted. It becomes the v2 marquee paired with partner real-time sync — both surfaces benefit from a richer Room state to read, and v1's voice capture is what populates it.
- We will **not** ship both in v1. Splitting effort across two unfamiliar native surfaces (Glance + `SpeechRecognizer`) doubles the device-test matrix and bug surface without doubling the value.

## What we want to accomplish

1. **Solve capture friction, not consumption friction.** An item captured ugly is recoverable; an item never captured is invisible. v1 lowers the cost of getting items onto the list by an order of magnitude.
2. **Keep the offline-first promise.** `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`, plus a pure-Kotlin parser, means the feature degrades only at "no internet AND no on-device model," not at "no internet."
3. **Maintain the "why native" answer.** Mic permission flow, framework `SpeechRecognizer`, on-device speech, and the OS-level audio stack are platform work that a cross-platform plugin would only thinly wrap. The differentiator stays concrete and demoable — even if it is less photogenic than a home-screen widget.
4. **Set v2 up cleanly.** v2 ships the widget and partner sync together. Both want a populated Room database to feel useful; v1 voice capture is what populates it.

## Alternatives considered

### Alternative A — Keep 001, ship the widget for v1, defer voice to v2
The original plan. Solves the consumption moment but leaves capture friction unaddressed. A widget on a sparsely populated list is a curiosity, not a marquee feature. Rejected: the upstream bottleneck is capture, not consumption.

### Alternative B — Ship both
Tempting on paper. In practice, two unfamiliar native surfaces in one v1 doubles risk without doubling value — the voice-captured list still has to be opened in-app to be useful, and the widget has nothing meaningful on it without voice-driven capture. Rejected as scope inflation.

### Alternative C — Defer the widget indefinitely
Not the call. The widget is still valuable; it pairs with v2's partner sync to make partner activity glanceable. "Later," not "never."

### Alternative D — Ship voice-to-list as a hidden/secondary feature, keep widget as marquee
Compromise that pleases nobody. If voice is good enough to ship, it is good enough to lead with; if it is not, it is not in v1.

## Consequences

### Positive
- v1 attacks the highest-friction moment in the user's day.
- Marquee scope shrinks to one well-bounded native feature instead of two.
- Voice-captured items immediately make the rest of the existing app (lists, check-off, completed tab) more useful — the marginal improvement is broader than the widget alone would have delivered.
- v2 becomes a natural pairing: voice-in (v1) → partner sync + widget visibility (v2).

### Negative / known tradeoffs
- **The "why native" demo shifts from visible (a widget on a home screen) to less visible (a permission flow + a Compose sheet).** Marketing has to work harder to make voice capture feel native rather than "every app does that." The honest platform-integration story lives in the recognition stack and offline behavior, not in the surface.
- **Recognition quality is device-dependent in a way widget rendering is not.** A user on a budget device with no Wi-Fi has a worse v1 experience than a Pixel user. Mitigated by clear fallback messaging in the sheet, but the variance is real.
- **001's engineering plan is sunk effort if we never come back to it.** We are committing to revisit it for v2; if v2 priorities shift again, retire the widget plan explicitly rather than let it linger.
- **Mic permission is a denial risk.** Some users will deny `RECORD_AUDIO` and never re-grant. The "Type instead" fallback path must be obvious so denial does not break the app's primary creation flow.

## Success criteria

Validated if, after launch:
- A meaningful share of new items are added via voice rather than keyboard.
- Average items-per-list rises versus the keyboard-only baseline (capture friction removed → more captures).
- The "why native" question gets a concrete demo answer in interviews and conversations, even if the demo surface is less photogenic than a widget would have been.

## When to revisit

- Voice usage stays low after launch → investigate discoverability and recognition quality before concluding the bet was wrong.
- Backend readiness changes timeline → revisit whether v2 should bundle the widget *with* partner sync or ship them separately.
- Recognition-quality complaints dominate → escalate per the technical-decisions/002 "Open questions" section (cloud speech, LLM parser).
- A second v1 marquee feature becomes available cheaply → not a reason to add scope; v1 stays focused.
