# 001 — No attribution on shared-list edits (v1)

**Status:** accepted for realtime-list-sharing v1 · **Revisit:** next version of sharing

## The tradeoff

Shared lists record *what* changed but not *who* changed it. There is no
"added by", no "deleted by", no activity history. Every edit — including
deletes — is anonymous.

## The failure mode this accepts

Two people share a list. One deletes "milk" and later claims it was never
added. The app cannot settle the argument: the tombstone row proves the item
existed and when it was deleted, but stores no user id. In a two-person list
the culprit is obvious by elimination — which is exactly why this is
survivable in v1, where lists are effectively couples. It degrades as
member count grows.

## Why we accepted it

- v1 scope was cut to the minimum that syncs (see
  `openspec/changes/realtime-list-sharing/proposal.md`, deferred list —
  "who added this" attribution was cut explicitly).
- Attribution without a place to *show* it is dead weight: it really wants
  an activity/history UI, which is its own feature.
- Nothing about the v1 schema blocks adding it later (see below) — deferring
  costs no rework.

## The upgrade path (when we do it)

1. Additive columns both sides: `created_by uuid` / `updated_by uuid` on
   `items` in Supabase (server fills `updated_by` from `auth.uid()` in the
   same trigger that sets `updated_at`), mirrored columns in Room via a
   normal additive migration.
2. Tombstones then carry "deleted by" for free — a delete is just an update.
3. Minimal UI: a "added by ✦" line in the item detail / a simple activity
   sheet per list. That's the real cost of this feature, not the columns.

## Tripwire

First real user complaint about a disputed edit — or the moment lists grow
past two members in practice.
