# 004 — Logout semantics for shared lists

**Status:** ✅ ACCEPTED 2026-08-08 — "shared lists are account data": evict on
explicit sign-out only, rediscover on sign-in. Rationale for acceptance:
shopping apps see rare, deliberate sign-outs — the eviction cost lands on an
uncommon path while the simplification pays on every path.
**Context:** realtime-list-sharing v1 (openspec/changes/realtime-list-sharing).

## The question

A shared list has a local Room replica. What happens to it when the signed-in
user logs out — and when someone (same person, or a different account) signs
in on that device later?

Forced by a real testing session (2026-08-08): user logged out mid-test,
kept editing, signed back in, and every edge fired at once.

---

## 🎤 Presentation note: what the AI suggested, and where it was flawed

Keep this section — it's the honest story of AI-assisted design.

**AI's first proposal:** on login, compare against `last_synced_user_id`;
if a *different* account signs in, **wipe all shared replicas** from the
device. Rationale: kills three failure modes at once (cross-account data
visibility, stuck dirty rows, edits pushed as the wrong user). Tidy, simple,
one pref + one cleanup call.

**The human's counter-question that broke it:** *"So if my wife logs in
with her account on the same phone…?"*

The flaw: the most likely account-switch on this app's target audience —
a couple sharing one device — is a switch between two accounts that are
**both members of the same list**. The wipe rule would delete the replica
on every switch and force a share-code re-entry every time. The AI had
optimized for the adversarial case (stranger's account) and completely
missed that the common case is friendly.

**The repair (also worth presenting):** stop inventing local policy and ask
the authority that already exists — the server's membership table. On
account switch, fetch each shared list as the new user; RLS answers
"member" (rows) or "not a member" (empty). Members keep the replica
seamlessly; strangers get it evicted. No second bookkeeping to drift.

Moral for the slide: AI proposes mechanisms confidently; the domain edge
that invalidates them ("whose phone is it, actually?") came from the human.
And the best fix deleted logic instead of adding it.

**Third act:** the repaired design (membership-probe on account switch) was
*also* rejected — "still doesn't sound like a proper solution." It was another
patch on the real contradiction: treating shared lists as device data AND
account data simultaneously. Every edge case was that contradiction leaking.
The accepted fix refuses the premise: shared lists are account data, local
lists are device data, and the two get different rules instead of one rule
with exceptions. Two wrong designs weren't wasted — each rejection named the
constraint the final design had to satisfy.

---

## ACCEPTED design: shared lists are account data, local lists are device data

The contradiction both rejected designs patched around: a shared list's truth
is collective (any member can change it from anywhere), so "my device owns
this data" was already surrendered at share time. Stop pretending otherwise.

| Event | Shared replicas | Local-only lists |
|---|---|---|
| Offline (any duration) | Untouched; edits queue; full local-first behavior | Untouched |
| Session dies on its own (token expiry/revocation) | **Untouched** — sync freezes, app prompts re-login. Data never vanishes on a technicality | Untouched |
| Explicit "Sign out" tap | Best-effort final push, then **evicted** from the device (+ sync points cleared) | Untouched — they belong to the device, no account required, forever |
| Sign in (any account) | **Discovery**: one RLS-scoped `select * from lists` returns exactly the account's member lists → pull + catch up → replicas materialize | Untouched |

What falls out for free:
- **Multi-device**: sign in on a new phone → your shared lists appear.
- **Account switch on one device** (the couple case): just sign-out eviction +
  sign-in discovery. No `last_synced_user_id`, no membership probes, no
  discarded-edits rules. Members see the list; non-members never do.
- **No "dead" shared lists**: a signed-out device holds none, so a shared
  list can never silently sit there not syncing.
- **Privacy**: a signed-out phone shows only its own local lists.
- Discovery runs on every sync kick (not a special login event), so
  `catchUpAllShared` gets simpler, not fancier.

Storage note (asked explicitly): this stores nothing new. Shared lists (and
only shared lists) have lived in Supabase Postgres since the feature's first
migration — that is what sharing IS. Local-only lists never leave the device
(verified on staging 2026-08-08).

**Accepted trade-offs:**
- No browsing/editing shared lists while signed OUT (signed-in-offline is
  unaffected — that's the aisle-7 case and it keeps full local-first behavior).
- Explicit sign-out while offline with unpushed shared edits loses those
  edits (final push can't run). Rare² — a warning dialog is a deferred
  nice-to-have, recorded in tasks.md group 8.

## Elaborated 2026-08-11: why eviction, not device-persistence (the borrowed-phone chain)

The tempting alternative — shared replicas persist on the device across
logout, like local lists do — fails a concrete chain. A and B share a list;
on B's phone, B signs out and C signs in:

1. **C sees A and B's list.** Privacy leak; a household list is intimate data.
2. **C can JOIN it.** The replica carries the share code (the share dialog
   re-shows it from the local row, no server round-trip). C reads the code,
   enters it under C's own account, and is now a legitimate server-side
   member — then invites D the same way. The device leaked the KEY, not
   just the view. A and B invited nobody.
3. There is no "C gets a different copy": the server holds ONE list. C and D
   edit THE list — which is why B returns to find it changed.
4. Even a C who never joins leaves damage: edits to the lingering replica
   mark `pendingSync`, push as C, are rejected by RLS, and retry as stuck
   dirty rows on every kick, forever.
5. B's eventual return resurrects the "whose replica is this" ambiguity that
   took this document three designs to escape.

Eviction dissolves the chain at link 1: C sees nothing, learns no code,
joins nothing; B's sign-in rediscovers the list in seconds. The price —
a shared list needs a session on that device, and local-then-shared is a
one-way conversion from device asset to account asset — is coherent: the
moment a second person can edit a list, "this device owns it" was already
fiction. A list the user wants truly device-owned is a list they don't share.

## Implementation checklist

- [x] Record decision (this doc)
- [ ] `SyncEngine.evictSharedReplicas()` + best-effort push, called from the
      explicit logout path ONLY (session-loss path in `BabeGetThisApp` must
      not evict)
- [ ] Discovery in `catchUpAllShared()` via `SharedListRemote.fetchAllLists()`
- [ ] `SyncPointStore.clear()`
- [ ] Tests (coverage gate) + update design.md / tasks.md 8.1–8.3
