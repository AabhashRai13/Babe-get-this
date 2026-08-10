# 005 — Require login to use the app

**Status:** ⛔ WITHDRAWN 2026-08-11, before any implementation — reversed by
the product owner on activation grounds: "nobody logs in to jot down *bring
a bottle of water*." First value must precede identity in a notes-shaped
app. The freemium shape stays: local lists free and instant, account
features (voice, sharing) gated behind sign-in. The real lesson from the
gate bugs that motivated this decision was "auth gates need tests," not
"auth gates need deleting" — see the mic-gate regression test.
The signed-out trade-offs this doc catalogued remain real and remain paid;
decision 004's eviction model is what keeps them bounded (see 004's
"borrowed phone" elaboration). No login-wall branch will be created.

**(Original accepted text below, kept for the record — and for the
presentation: accepted on day one, withdrawn on day three, and the code
never had to move because the withdrawal matched what was already built.)**

---

## The decision

The app requires a signed-in account. The anonymous, signed-out usage mode
is removed.

## Why

One day of device testing on realtime sharing produced this bill for the
signed-out state: an ungated voice FAB that shipped (the gate with a test
worked; the gate without one didn't exist), the entire logout-semantics saga
(decision 004 took three designs), a discovery-never-ran-after-login bug
(login fires neither a foreground nor a connectivity event), three
`AuthPromptDialog` flows, `isAuthenticated()` branches in every feature, and
a doubled test matrix. Against that: anonymous mode delivers a plain local
list app — neither of this product's differentiators (sharing, voice) works
without an account. It is a second product maintained for nobody.

## What the login-wall change will contain (the separate branch)

- Start destination: no session → Login; session → Home. Sessions persist
  offline indefinitely — only the FIRST launch ever needs network.
- Delete: `AuthPromptDialog` usages (voice ×2, share, join), the
  `isAuthenticated()` UI branches, signed-out journey tests.
- Local Room data is untouched by the wall — existing users' lists survive;
  local-only (non-synced) lists remain a feature. The wall kills the
  signed-out STATE, not device-owned data.
- Dev flavor: `FakeAuthRepository` must present a fake signed-in session or
  dev builds strand at the wall.
- 004 stays intact and gets simpler: explicit sign-out still evicts shared
  replicas, then lands on Login.

## Accepted trade-offs

- Signup friction before first value. Defensible for a partner-invite
  product ("install this and enter my code" arrives with a reason to sign
  up). If activation metrics later disagree, the escape hatch is Supabase
  anonymous auth (silent account, email attached later) — explicitly NOT
  built now: it reintroduces half the two-state complexity.
- First-ever launch while offline is unusable (nothing to authenticate
  with). Rare; accepted.
