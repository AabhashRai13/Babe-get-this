# 003 — Voice-to-list requires authentication

**Status:** Accepted
**Date:** 2026-05-18
**Area:** v1 voice feature; auth boundary
**Relates to:** [002 — Voice-to-list replaces the home widget](002-voice-to-list-replaces-widget-for-v1.md), [technical-decisions/002 — Voice-to-list](../technical-decisions/002-voice-to-list-input.md)

## Context

The rest of the app is offline-first by deliberate design (`CLAUDE.md`): create a list, add items, tick them off — all of it works with no account and no internet. Login is optional for typed flows.

Voice-to-list breaks that pattern because it isn't a local operation. The audio capture is local, but the work that turns audio into items is server-side: a Whisper-class speech-to-text pass plus an LLM parser. Both are **paid per call** on the backend. There is no on-device equivalent we are willing to ship at v1 quality.

That creates a tension. The app's philosophy is "use it freely, no signup wall." The voice feature's economics are "every call costs us real money." The two collide at the moment the user taps the Voice tile.

The question is not whether voice can be technically gated — it can — but whether the friction of a login wall is justified by what we get for it.

## Decision

For v1, **voice-to-list requires the user to be authenticated.** The Voice tile in the create-list chooser shows a lock affordance when the user is signed out; tapping it routes to the existing login/register flow rather than opening the voice sheet.

Typing, list browsing, item check-off, and every other existing flow remain anonymous. **Only voice is gated.** The offline-first promise is preserved for all primary capture and consumption surfaces; voice is documented as an account-gated capability.

The backend voice endpoint requires `Authorization: Bearer {user_token}`. Unauthenticated calls return `401`. There is no anonymous device-token tier, no free-trial counter, and no other auth scheme in v1.

## What we want to accomplish

1. **Protect backend cost.** Anyone with the APK can extract the voice endpoint URL. Without an auth gate, a single bad actor with a script can drain weeks of API budget in an afternoon. Requiring a real account raises the cost of abuse beyond "free."
2. **Keep the feature's price honest.** Voice isn't valuable enough on its own to justify a paid tier or a "trial then pay" pattern. Users would resent paying for a feature that saves 20 seconds versus typing. But "create an account to access it" is a fair trade — the account unlocks future value (partner sync, multi-device, history) that voice alone does not.
3. **Preserve offline-first where it matters.** The offline-first promise was always about *capture and consumption never being blocked* — not "every feature must work without an account." Voice being account-gated does not break that promise; typing still works fully anonymous.
4. **Push complexity to v2, not v1.** Anonymous device tokens, trial counters, and conversion funnels are real product surfaces — each one a separate decision with its own UX, telemetry, and abuse considerations. v1 ships the simplest viable boundary; richer access models can replace it later if data justifies them.

## Alternatives considered

### Alternative A — Anonymous device tokens with per-device rate limits
First app launch generates a UUID, trades it for an opaque device token, and that token authenticates voice calls. Backend rate-limits per device.

Rejected for v1: it requires a new backend endpoint, client-side token storage on first launch, abuse handling (devices can re-register), and ongoing operational work to tune limits. The engineering cost is real and the user benefit over "just sign in" is small — the user still ends up signing in eventually to unlock partner sync, which is the real reason to have an account.

### Alternative B — Try-then-convert (N free voice uses for anonymous users, then signup wall)
Highest theoretical conversion to signup. Lets the user experience the magic before being asked for anything.

Rejected for v1: every free call costs us money, the abuse surface is large (uninstall/reinstall resets the counter unless we track devices), and the conversion UX is its own design problem. Worth revisiting in v2 if voice usage is high and signup conversion is low, but premature now.

### Alternative C — Open endpoint with IP-based rate limiting
Cleanest UX. No auth header at all on the voice endpoint.

Rejected: trivially bypassable. Mobile carriers rotate IPs, VPNs are commodity, and there is no way to distinguish a determined attacker from a legitimate user on the same NAT'd IP. The downside of getting this wrong is a directly-billed cloud cost, not a soft metric.

### Alternative D — Voice in a paid tier
Voice is a $X/month feature. No account, no voice; paid account, voice.

Rejected: see "Keep the feature's price honest" above. Per the project goal (portfolio app, not a revenue play — [memory: app_is_portfolio_for_hire]), monetization is explicitly out of scope. Asking users to pay for voice would be both off-mission and unjustified by the feature's standalone value.

## Consequences

### Positive
- **Backend cost is bounded by the size of the user base, not by the size of the internet.** Abuse requires creating accounts at scale, which is observable and stoppable.
- **The login flow gets a real reason to exist in v1.** Previously, login was optional and rarely engaged. Voice creates a natural moment where signup unlocks tangible value, which is better than asking for signup cold.
- **Decision surface stays small.** One boolean ("is the user authenticated?") gates the feature. No counter to maintain, no trial logic to reason about, no device-token lifecycle to manage.
- **Aligns with where the user is anyway.** v2's marquee is partner sync, which inherently requires accounts. v1 gating voice on the same account is consistent rather than additive.

### Negative / known tradeoffs
- **A user who opens the app, taps Voice, and hits a login wall may bounce.** This is the real cost. Mitigation: copy on the gate screen has to explain *why* signing up is worth it ("sync your lists with your partner") rather than just demanding it. The gate is a product surface, not a system message.
- **Voice usage will be lower than it would be on an open endpoint.** Acceptable in v1 — we would rather have lower usage of a sustainable feature than higher usage of a feature that bankrupts the backend.
- **The offline-first marketing line gets a footnote.** "Works fully offline — except voice, which needs an account and internet." Honest, but no longer a single-sentence pitch.
- **If we ever want to remove the gate later, the migration is mostly UX.** Backend stays the same (already auth-aware); client just stops showing the lock and routes anonymous taps somewhere reasonable. Reversible.

## Success criteria

Validated if, after launch:
- Voice abuse incidents (script-driven endpoint hammering, anonymized in logs) stay at zero or near-zero.
- Account creations attributable to the voice CTA make up a meaningful share of total signups — evidence the gate is a conversion driver, not just a blocker.
- Voice usage among authenticated users is high enough that the feature earns its place in v1 despite the gate (i.e. signed-in users do come back to it).

## When to revisit

- Voice signups stall, but voice usage among signed-in users is strong → the gate may be too cold; consider Alternative B (try-then-convert) with a small free quota.
- Backend voice cost is far lower per call than projected → consider opening a small anonymous quota with device tokens (Alternative A).
- Backend voice cost is far higher than projected → tighten further: per-account daily limits, queue, or temporary disabling.
- A second voice consumer is added (e.g. voice-add-to-existing-list, per technical-decisions/002 future-extensibility note) → confirm the same auth gate applies, or document the exception explicitly.
