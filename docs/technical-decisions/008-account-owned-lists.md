# 008 — Account-owned lists, guest lists, and the Welcome screen

**Status:** ✅ ACCEPTED 2026-10-14. Supersedes the "local-only lists" half of
[004](004-logout-semantics-for-shared-lists.md): device-owned lists now exist
only for guests.
**Spec:** [`docs/superpowers/specs/2026-10-14-guest-and-account-lists-design.md`](../superpowers/specs/2026-10-14-guest-and-account-lists-design.md)

## Context

**Got This** — a per-person record of what you have already bought ("you
already bought this", "you're probably running out of eggs") — is personal
history, and the old ownership model broke it. Lists that weren't shared
belonged to the *phone*: anyone using it, signed out or signed in as any
account, saw and ticked the same lists. Purchases would land in the wrong
person's history, and a guest would see the account owner's history in a
warning.

The first design of Got This was lost with the machine it lived on, along with
these decisions. They are recorded here so that can't happen twice.

## Decisions

| # | Decision | Rejected alternative, and why |
|---|---|---|
| D1 | **Signed-in lists belong to the account and sync; guest lists belong to the device and never leave it.** | Keep "local lists belong to the phone" — breaks Got This (above). |
| D2 | **Local-first with sync, not online-first.** Screens read and write Room; `SyncEngine` pushes and pulls in the background. | Online-first (screens wait on the server) — adding or ticking items with no signal in a store would fail, which is the case that must work. |
| D3 | **A guest who signs in takes their guest lists into the account** (uploaded), with a one-time notice. | Keep them on the phone, visible only when signed out — two sets of lists that swap on sign-in, and lists that seem to vanish. |
| D4 | **Existing signed-in users' local lists move to their account on update**, with the same notice; the privacy policy and the Play Data safety form change **before** release. | Ask each user first — a "no" would leave their lists hidden whenever signed in. |
| D5 | **Got This is per person, not shared between list members.** (For the Got This spec; not built here.) | Household-level history — list members are not necessarily one household (a flatmate, a friend, a coworker for one trip). *Revised by the Got This spec's D4 ([design](../superpowers/specs/2026-11-09-got-this-design.md)): history is still per person, but a tick on a shared list now reaches everyone on the list at that time.* |
| D6 | **A lost session keeps the account's lists visible and editable**; only an explicit sign-out removes them. New `AuthState.SessionExpired(userId)`. | Treat session loss like sign-out — lists vanish over a token expiry, which 004 already ruled out. |
| D7 | **A server-side list no longer needs a share code**: `lists.share_code` is nullable and write-once. | A code for every list at creation — every private list joinable by anyone who guesses six characters. |
| D8 | **Background catch-up is account-wide**: one discovery request, one request for the account's items changed since one account sync point (RLS-scoped, like discovery), full history only for lists new to the phone. Every multi-row read pages, since PostgREST caps a response at 1000 rows. | Per-list catch-up for every list — two requests per list on every app open once every list syncs. Batches of list ids — a sync point taken across batches read at different times skips changes made in between. |

## Ownership, by event

Replaces 004's table.

| Event | Account lists (shared or not) | Guest lists |
|---|---|---|
| Offline (any duration) | Full local-first behaviour; edits queue | Untouched |
| Session lost (token expired/revoked) | **Kept, visible, editable**; sync waits; banner asks to sign in | n/a — none while an account owns the device |
| Sign in | Discovery pulls the account's lists. Another account's leftovers are dropped | **Move into the account** and upload; one-time notice |
| Explicit sign-out | Final push; if anything is still unsynced the user is **warned first**; then removed from the phone; back to Welcome | Untouched |
| Account deletion | As sign-out; the server deletes lists nobody else joined | Untouched |

Mechanically every account list is what a shared list already was: a Room
replica with a `pendingSync` outbox. A new Room column `accountId` decides
ownership (`NULL` = guest) and replaces `shareCode != null` as "goes through
sync"; `shareCode` now only means "shared".

## Accepted trade-offs

- Signing out with unsynced changes loses them — after a warning, by choice.
- Session lost, then a **different** account signs in: the old account's
  unsynced changes are lost without a warning.
- Guest lists move into whichever account signs in on that phone.
- Guest lists are device-only: uninstalling loses them, as before.
- Old app versions can't decode a list without a share code: once the account
  has one, an old version's background sync of shared lists fails. This release
  ships at Play in-app update priority 5 (IMMEDIATE, decision 003) so old clients
  update first.
