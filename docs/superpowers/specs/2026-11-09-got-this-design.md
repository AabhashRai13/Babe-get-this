# Got This — design

**Date:** 2026-11-09 · **Status:** approved 2026-11-10; updated 2026-11-10 with decisions made during the build · **Branch:** `feat/got-this`

## Summary

Got This remembers what each person has bought, from the items they tick, and
uses it two ways:

1. **"Bought 3 days ago"** — a quiet line under an unticked item that was
   bought recently, so nobody buys the same thing twice by accident.
2. **"Running low?"** — the items a person buys regularly, suggested on the
   list screen when they are due again, plus at most one notification a week.

Nobody sets anything up. There is no "for home" mark on lists, no family
groups and no "picked up by". Whether something is a household regular is
worked out from the buying pattern: eggs come back every week, BBQ sausages
bought once never become a regular. All judging runs on the phone with simple
rules. Both parts ship in the same release.

Every tick is recorded as its own event, sent through an outbox like the rest
of sync, so the history matches what happened even when phones are offline.

A paid tier ("Plus") is planned in its own spec. This release leaves one seam
for it (`GotThisAccess`) and records nothing for it.

## Why

People buy things they already have. A retailer survey found 68% of people
had bought food without realising the same item was waiting at home. A
shared list makes it worse: one partner ticks milk on Monday, the other adds
milk on Tuesday.

The first Got This design was lost with the machine it lived on, and its
first replacement depended on users marking each list as "for home" or not.
In practice people will not do that, and they do not name lists in a way the
app could read either. So the app has to tell a weekly shop from a BBQ on
its own, from what is bought and when.

## Decisions

Recorded with the alternative that was rejected, so they are not re-argued.

| # | Decision | Rejected alternative, and why |
|---|---|---|
| D1 | **Household regulars are inferred from repeat purchases.** No list mark, no list-name parsing. | A "for home" mark on lists — users will not set it, and list names are too inconsistent to parse. |
| D2 | **No family groups.** | Family groups with invites and their own server tables — a large build, and a shared list already covers the couples this app is for (tradeoff 001 describes lists as "effectively couples"). |
| D3 | **No "picked up by".** Nobody records who ticked an item. | Attribution (`updated_by` from tradeoff 001) — it says who ticked, not whether the thing went home, and D4 makes the ticker irrelevant. It also only keeps the last editor. |
| D4 | **A tick becomes a purchase for every person on the list at the time of the tick: the list's creator, and members who joined at or before the tick.** Each owns their own copy. History survives list deletion; a person who joins later gets nothing from before; account deletion removes only that person's copies. This revises 008's D5: history is still per person, but a shared list's ticks reach everyone on it. | History for the ticker only — the partner who did not shop gets no "bought 3 days ago" for the milk the other bought, which is the main case. History scoped to the list — lost when the list is deleted, and finished lists get deleted. Membership when the server processes the tick — an offline tick uploaded days later would reach someone who joined in between. |
| D5 | **Plain rules on the phone, no machine learning, no server model.** | A trained model — published comparisons find simple "what you buy most" methods beat deep models for repeat grocery items, and the one production system with numbers was trained on about 100M shoppers. Rules are explainable and testable. |
| D6 | **The hint states a fact ("Bought 3 days ago") and never blocks or asks.** The item is always added. | A warning dialog on add — friction on every false hint. "You have this" wording — false after a BBQ, which is the case that started this. "You bought" — we do not know who ticked (D3). |
| D7 | **Both parts ship in one release.** | Hint first, running-out later — rejected by the product owner. |
| D8 | **Every tick is an event**, with a deterministic tick id (from the item id and the tick time) and the item's name and category at that moment. The phone stores each event in an outbox and uploads it with `record_ticks`; the server creates the purchases from the event, never from the item row. Item rows still carry the latest tick time, for old app versions and for the 2-hour rule on other phones. | Purchases derived from the item row — the row holds only the latest tick, so a tick, a later untick and a re-tick made offline lose the first purchase, and a rename before sync relabels the purchase. Every device derives purchases from synced changes — misses ticks between pulls, and two phones would each write one. Random ids — the same purchase would be created twice on different paths. |
| D9 | **Unticking within 2 hours of the tick undoes it; a later untick keeps it.** The undo is part of the same event: the outbox sends the event again with `undone_at` set. The server accepts it only when `undone_at` is within 2 hours after the tick time, and an undone event stays undone whichever copy arrives first. | Any untick deletes the purchase — people reuse lists by unticking last week's items. A separate undo call that tombstones existing purchases — if it arrives before the tick, there is nothing to tombstone, and the tick then creates purchases that should not exist. Inferring the undo from the item row — the app sends null fields (`explicitNulls = true` in `SupabaseModule`), so a stale push would look like an undo. |
| D10 | **Backfill from items already ticked**, as tick events with the item's `updated_at` as the tick time. Backfilled ticks then follow the same rules as new ones, including D4. | Start empty — running-out would say nothing for weeks after release. Insert backfilled purchases directly — their ids would not match the ones the claim produces, which is how duplicates appear. |
| D11 | **Item matching is conservative**: clean the name, then match exactly or by one typo. | Fuzzy matching on word overlap — "milk" would match "coconut milk". A missed match costs one missing hint; a wrong match puts a wrong one on every list. |
| D12 | **Doubt means silence** (as in the reminders spec, D9): an item that does not meet every rule shows nothing, and a notification that cannot be confirmed as the person's only one this week is not sent. | Hedged suggestions for borderline items — wrong reminders train people to ignore all of them. |
| D13 | **Nothing is counted for Plus in this release. When Plus ships, everyone using the app at that point starts with the Plus trial.** | Count free weeks now on the phone — lost on reinstall and doubled across two phones. Count them on the server — a table and an RPC built for a product that does not exist yet, when a trial for everyone keeps the same promise. |
| D14 | **A 10% holdout per person for the first 8 weeks after release**: history is recorded, nothing is shown, and telemetry logs what would have been shown. A signed-in person's group comes from their account id; a guest's from a number drawn once on the phone. The holdout ends when the `got_this_holdout` flag is switched off. | No holdout — nothing to compare removal and add rates against. Per install — one person's two phones could land in different groups. A holdout period per person — new users after the measurement window would sit in it for no purpose. |
| D15 | **Clearing history deletes it.** The person's purchases up to the moment they pressed Clear are deleted on the server and every phone, along with what was learned from them on the phone. A reset time, which only ever moves forward, stops older ticks still waiting on an offline phone from bringing them back. Purchases after the reset are kept. Tick events not yet sent are **not** cleared: they still owe other people on the list their purchases, and the reset stops them creating the person's own. | A tombstone that blanks the name — keeps item ids, categories and times, so it is not what "clear" says. Clearing on one phone only — the next sync brings it back. Deleting everything on each phone when a reset arrives — loses ticks made after it. Dropping unsent tick events with the history — the partner would never get the milk this person bought. |
| D16 | **"At most one notification a week" is per person.** A signed-in phone claims the week on the server before posting; whichever phone claims first posts. Guests count on the phone. | Per phone — two phones on one account would both post. |
| D17 | **The database triggers and RPCs have executable tests** (pgTAP, run in CI against a local Supabase). | Manual checks written as SQL comments, as in earlier migrations — the history rules live in those functions, and a regression there produces plausible wrong answers that no UI test notices. |
| D18 | **List members are trusted with each other's tick events**, as they already are with the list (every member is a full editor, `001_realtime_list_sharing.sql`). The server checks what it can: the caller is a member of the item's list, the item is on that list, the event is no more than 30 days old, and an undo is within 2 hours of the tick by the event's own times. | Proof of when an untick happened — the server only has the phone's clock either way. A member who fabricates times can at most undo a tick from the last 30 days, which is less than they can already do by deleting items. |
| D19 | **Ticks on a list that never reached the server become the person's own purchases.** When the push drops such a list (`deleteNeverSentDeletedLists`), its unsent tick events are turned into uploads through `upload_own_purchases` first, in the same transaction. Only its creator was ever on it, since sharing needs the server, so nobody else is owed anything. Lists and items that did reach the server are only tombstoned, never purged (`001_realtime_list_sharing.sql`), so their events still validate after deletion. | Uploading the deleted list to validate its events — the push deliberately never sends such lists, because their rows would come back on every discovery. Letting the events expire — the person loses that shop. |
| D20 | **Unsent tick events count as unsynced changes.** The sign-out warning (`hasUnsyncedChanges`) counts them, so signing out offline with unsent ticks is the same warned choice as with unsent edits (008). | Keeping them across sign-out — they belong to the account, and the next person on the phone must not send them. |
| D21 | **Privileged database functions are not callable by clients.** Internal helpers live in a `got_this_private` schema that PostgREST does not expose, with `EXECUTE` revoked from `public`, `anon` and `authenticated`. Every `security definer` function sets `search_path` to an empty value and names objects with their schema. RPCs grant `EXECUTE` to `authenticated` only, as `join_list_by_code` does. | Relying on the RPC being the only caller — Postgres grants `EXECUTE` to `public` by default, so a helper in `public` would be callable directly and skip every check in `record_ticks`. |

## History invariants

Every part of the data lifecycle below must keep these true. The database
tests (D17) and the repository tests check them directly.

| # | Invariant |
|---|---|
| I1 | A tick is identified by `tick_id = id(item id, tick time)`. The same tick has the same id everywhere. |
| I2 | A tick produces at most one purchase per person: `purchase_id = id(person, tick_id)`. |
| I3 | The people credited with a tick are the list's creator, the members whose `joined_at` is at or before the tick time, and the person who sent the tick (their phone's clock may put the tick before they joined). Nothing else changes who is credited. |
| I4 | A tick that is undone has no live purchases, whichever of its tick and undo reaches the server first. An undo affects that tick's purchases and nothing else. |
| I5 | Every tick made on a current app version reaches the server as its own event, carrying the name and category the item had when it was ticked, provided the phone reconnects within 30 days of the tick and the person does not choose to sign out with it unsent (D20). A tick on a list that never reached the server reaches it as that person's own purchase instead (D19). Clearing history never discards an unsent event. |
| I6 | After a person's reset time, they have no purchase with a tick time at or before it, on the server or any phone. Purchases with a later tick time are kept. On a phone, a clear that has not reached the server yet counts as a reset from the moment it is pressed. |
| I7 | A person's reset time only moves forward. |

## What users see

### "Bought 3 days ago"

An unticked item whose name matches a recent purchase shows one extra line
in the item row, in the secondary text colour:

> Milk
> Bought 3 days ago

- Wording by age: "Bought today", "Bought yesterday", "Bought N days ago".
- It appears on any unticked item, however it got there: typed, spoken, or
  added by the partner. It is worked out when the row is drawn.
- Tapping the line opens a small menu: **Remove from list** (deletes the item
  through the existing delete, with its undo) and **Don't remind me about
  this** (mutes the item for 60 days).
- An item that is ticked while its hint is showing counts as "kept". Two kept
  in a row mute the item for 60 days on that phone.
- Ticked items never show the line.
- A partner's tick shows up after the next catch-up (foreground or
  reconnect), not instantly.

### "Usually bought around now"

The items screen of any list shows a row above the items when anything is
due:

> **Usually bought around now**
> [+ Eggs] [+ Milk] [+ Bread] [+3]

- Tapping a chip adds the item to this list with quantity "1" and the
  category it was last bought with, through the existing add path. The chip
  leaves the row.
- A finished list is not a separate state: a list counts as finished only
  while every item on it is ticked (`isCompleted` is derived). Adding a chip
  to a finished list makes it unfinished, so it moves back to the active
  lists and its progress card resets, exactly as adding any item does today.
- A regular whose most recent purchase came from an item on a PIN-locked
  list is not suggested, here or in the weekly notification, while that list
  stays locked: a locked list's contents are not named elsewhere.
- An item already unticked on any of the person's lists is never suggested.
  "Already on a list" uses the same matching as hints, so an unticked
  "bannana" counts for bananas.
- **Not now** (an icon at the end of the row) hides the row on every list for
  24 hours.
- Up to 6 chips, most overdue first; the rest collapse into "+N", which
  expands the row.
- When notifications are not allowed, the row ends with **Get a weekly
  heads-up**, which opens the existing permission sheet with Got This copy.
  This request is separate from the shopping-reminder one: saying no to it
  does not snooze the shopping-reminder prompt, and saying yes does not arm
  a shopping reminder.

### The weekly notification

At most one a week per person (D16), at about 18:00 on the evening before the
person's usual shopping day:

> **Running low?**
> Eggs, milk and bread are usually bought around now.

- It names up to 3 items that are due by the next day, most overdue first.
  With more than 3: "Eggs, milk, bread and 2 more".
- Tapping it opens the items screen of the most recently edited list, finished
  or not, where the suggestions row is. "Edited" counts changes to the
  list's items as well as to the list itself. With no lists at all, it opens
  the list catalog with the create-list dialog open; the new list shows the
  row. Reopening the app from Recents does not repeat either.
- With no clear usual shopping day (see Running low), it goes out at about
  18:00 on the first day something becomes due, still at most once a week.
- A week is an ISO week (Monday to Sunday) in the phone's time zone.
- The check runs in the background, usually with the app closed. It takes
  the signed-in person from the stored identity, and claims the week only
  with a session the server client has actually loaded; if it cannot
  confirm one, it stays silent.
- Its own notification channel, "Running-low reminders", separate from
  shopping reminders, so either can be muted in system settings.

### Settings

A new **Got This** section:

- **Bought-recently hints** — switch, on by default.
- **Running-low reminders** — switch, on by default. Shows "Notifications are
  off for this app" with a link to system settings when the permission is
  missing, like the shopping reminders row. The switch stays on in that
  state, because it also controls the in-app suggestions row, which works
  without notifications.
- **Clear purchase history** — confirmation dialog: "This deletes your
  purchase history on all your devices. Your lists stay as they are, and
  people you share lists with keep their own history."

### Guests

Everything works the same, from the phone's own history. When a guest signs
in, their history moves into the account with their lists (008 D3).

## How judging works

Everything below is pure Kotlin with no Android types, in
`feature/gotthis/domain/`, so it can be tested and replayed against recorded
data on the JVM. The numbers are starting values, set in one file
(`GotThisRules.kt`) and tuned from telemetry (see Measuring whether it works).

### Same item?

`ItemKey.of(name)` turns a name into a key:

1. Unicode NFKD, strip accents, lowercase with `Locale.ROOT`.
2. Punctuation to spaces, collapse whitespace, trim. Letters, digits and
   the combining marks of any script are kept, so names in Devanagari or
   Arabic stay whole.
3. Drop a leading or trailing quantity: numbers, fractions, and the units
   `x g kg ml l pack packs bag bags bottle bottles can cans box boxes dozen`,
   plus a following "of". "2 bags of rice" → "rice", "milk 2l" → "milk".
4. Drop the size and freshness words `fresh organic large small big medium`.
5. Make each word singular with plain English rules: "-ies" → "-y"
   (berries), "-oes" → "-o" (tomatoes), "-ses/-xes/-ches/-shes" lose "-es",
   a final "s" goes unless the word ends in "ss", "us" or "is".

A name that normalises to nothing ("2 kg", "!!!") keeps its trimmed,
lowercased form as its key, so it still matches itself.

Two keys match when they are equal, or when both are at least 5 characters,
have the same number of words, and differ by one edit (Damerau–Levenshtein
distance 1: "bannana" and "banana"). "Milk" and "coconut milk" never match.

The key is stored with each purchase in Room. `ItemKey.VERSION` is stored in
prefs. When it changes, start-up recomputes every purchase's key from its
stored name, and clears what was stored by key and cannot be recomputed:
mutes, hint impressions and the pending reminder follow-ups.

**Item groups.** Typo matching applies to the history as well as to hints,
so three purchases spelled "banana", "bannana", "banana" are one item with
three purchases. `ItemGroups` builds the groups from the owner's distinct
keys, the same way everywhere:

1. Sort the keys by number of live purchases (most first), then by most
   recent purchase, then alphabetically.
2. Take each key in that order. It joins the first existing group whose
   **canonical key** it matches; otherwise it starts a new group and is that
   group's canonical key.

Matching is always against the canonical key, never against other members,
so groups cannot chain ("banana" → "bannana" → "bannanna"). An unticked
list item belongs to the group whose canonical key its own key matches,
taking the first in the same order. `ItemJudge` always receives all the
purchases of one group. A group's display name, used for chips and the
notification, is the name of the most recent purchase under its canonical
key.

Names in other languages still match themselves exactly; only the plural
rules are English.

### What counts as a purchase

- A tick that is not undone within 2 hours (D9). Each tick is one purchase,
  so a list that is reused and ticked again next week records a new one.
- Purchases of the same key are grouped into **shops**: a purchase less than
  2 days after the first day of the current shop joins it. A BBQ bought over
  Friday and Saturday is one shop.
- Days are local calendar days.
- A purchase dated after today (the phone's clock was ahead when it was
  ticked) is ignored until its date arrives.

### Regular or one-off?

Every test uses the same **window: the item's last 7 shops** (up to 6 gaps
between them). An item is **regular** when, within the window:

| Rule | Value |
|---|---|
| Shops | at least 3 |
| First shop to last shop | at least 14 days |
| Steady gaps | at least two thirds of the gaps (rounded up) lie between half and twice the median gap |

Its **usual gap** *m* is the median of the gaps in the window (the mean of
the middle two when there is an even number). An item is **lapsed** when the
days since its last shop exceed 2.5 × *m*: the person has stopped buying it,
and it is treated as not regular until it is bought again.

Because the window rolls and only two thirds of gaps must be steady, one odd
gap does not break an item:

- Weekly eggs with one extra purchase two days after a shop: gaps 7, 2, 5,
  7, 7, 7. Median 7; five of six gaps lie between 3.5 and 14. Still regular.
- Eggs not bought for 60 days, then bought again: gaps 7, 7, 7, 7, 7, 60.
  Median 7; five of six are steady. Regular again from that purchase, and
  the 60-day gap leaves the window after six more shops.
- A party prepared over three days in one week fails the 14-day rule.
- A monthly BBQ host's sausages do become a regular (see Accepted
  trade-offs).

### "Bought N days ago": the window

The hint shows when the last purchase of the item was fewer than *w* days
ago (today is 0 days ago):

- **Regular, not lapsed:** *w* = ⌊*m* / 2⌋, at least 1. Weekly eggs get 3
  days; monthly fuel (*m* = 30) gets 15.
- **Anything else:** a window by the category of its most recent purchase
  that had one:

| Group | *w* | Default categories (ids without `cat-`) |
|---|---|---|
| Fresh | 3 days | fruits-vegetables, meat-seafood, bakery-bread |
| Short-life | 5 days | dairy-eggs, ready-meals |
| Weeks | 14 days | snacks, sweets-chocolate, beverages, breakfast-cereal, alcohol |
| Long-life and consumables | 30 days | frozen-foods, pantry-dry-goods, canned-jarred, baking-supplies, spices-seasonings, sauces-condiments, oils-vinegars, spreads, coffee-tea, household-cleaning, paper-disposables, toiletries-personal-care, health-pharmacy, baby-kids, pet-supplies, tobacco-vaping, office-stationery, automotive |
| Durable | 90 days | kitchen-dining, appliances, furniture-storage, home-decor, bedding-bath, lighting-electrical, hardware-diy, garden-outdoor, clothing, underwear-sleepwear, shoes, accessories-jewelry, bags-luggage, electronics, toys-games, books-media, sports-outdoors, arts-crafts, party-gifts |
| Unknown | 7 days | other, any custom category, no category |

The BBQ case: sausages (Fresh) re-added a week later get no hint; charcoal
(Long-life) re-added 12 days later does, and is worth asking about.

A test fails if a default category id is missing from this table, so a new
default category cannot ship without a window.

### Running low: when something is due

A regular, not lapsed, not muted item is **due** when the days since its last
shop are at least ⌈0.9 × *m*⌉. It stays due until it is bought, it is
unticked on one of the person's lists, or it lapses.

**Usual shopping day:** among the distinct days in the last 56 that had at
least one purchase, take the weekday with the most. It counts only with at
least 4 such days and when that weekday has at least 40% of them. People buy
on shopping days, which is why repeat orders cluster at 7, 14 and about 30
days.

### Scoring predictions

The forecast is the one the app held right after the previous shop: if the
item was regular as judged from its purchases up to and including that shop,
its predicted day was that shop's day plus *m*, rounded to the nearest day.
When the next shop of the item begins, that forecast is scored, **whether or
not the item lapsed in between**, so very late purchases count against
accuracy instead of dropping out. The error is the new shop's day minus the
predicted day: positive means bought later than predicted. Nothing is stored
for this; the earlier purchases are already in Room. A shop is scored once,
on the phone that made the tick.

## Components

All new code lives in `feature/gotthis/`, following the existing feature
layout.

| Unit | Does | Depends on |
|---|---|---|
| `domain/ItemKey` | Name → key, and key matching | nothing |
| `domain/ItemGroups` | Distinct keys with counts → groups with a canonical key; which group a name belongs to | `ItemKey` |
| `domain/GotThisIds` | Tick id, purchase id, holdout number (MD5) | nothing |
| `domain/GotThisRules` | Every threshold and the category window table | nothing |
| `domain/ItemJudge` | Purchases of one item group → `ItemProfile` (shops, regular, *m*, lapsed, due, window) for a given day, and prediction scoring | `GotThisRules` |
| `domain/ShopDay` | Purchase days → usual shopping weekday or none | `GotThisRules` |
| `data/local/PurchaseDao`, entities | Room access, including the tick and untick transactions | Room |
| `data/GotThisRepository` | Hints for a list's items, due items for an owner, clearing history | DAO, judge |
| `data/GotThisSync` | Tick outbox push, own-purchase upload, pending clear, never-sent list conversion (D19), purchases pull, reset handling | remote, DAO |
| `data/GotThisBackfill` | One-time guest backfill and key recompute | DAO, prefs |
| `data/GotThisAccess` | Whether hints and running-low may show: kill switch, holdout, the Settings switches. The seam Plus will extend. | flags, prefs |
| `reminder/RunningLowWorker`, `RunningLowScheduler` | Daily 18:00 check and the weekly notification | WorkManager, repository, remote |
| `ui/BoughtHintLine`, `ui/UsualsRow` | The two pieces of UI | view models |

`ItemProfile` is computed on demand. With at most a few thousand purchases
per person, this stays in memory and in one or two queries:

- **List screen:** read the owner's distinct item keys with their purchase
  count and latest purchase time (one grouped query on the `accountId,
  itemKey` index, observed as a Flow so a pull refreshes it), and build the
  groups with `ItemGroups`. Find each unticked item's group in memory, which
  is where one-typo matches like "bannana" are found. Then read the
  purchases for every key of the matched groups in one query.
- **Daily worker:** read all of the owner's live purchases and group them
  with the same `ItemGroups`.

Mutes are written under the group's canonical key. A group is muted while
any of its keys has a live mute, so a mute survives the canonical key
changing.

### Repository hooks

Both are single Room transactions, so a crash cannot leave an item, its
purchase and its outbox entry disagreeing.

- **Tick** (`togglePickedUp(itemId, true)`): set `isPickedUp`, set
  `pickedUpAt = now`, insert the local purchase for the current owner (the
  signed-in user, or the guest) with the ids from I1 and I2, and, on an
  account list, insert an outbox event with the item's current name and
  category. The local purchase is the same row the server will create, so
  the pull overwrites it rather than adding one. A tick on an item that is
  already ticked (a double tap before the screen updates) writes nothing.
- **Untick** (`togglePickedUp(itemId, false)`): clear `isPickedUp`; leave
  `pickedUpAt` as it is. If the tick was less than 2 hours ago (0 ≤ now −
  `pickedUpAt` < 2 hours; a tick time that is now in the future, after a
  clock correction, is not undone):
  - tombstone the local purchases with that tick id;
  - on an account list, set `undoneAt = now` on the outbox event for that
    tick, or insert it again with `undoneAt` set when it was already sent.
    The name and category come from the local purchase, which holds the
    values at tick time;
  - on a guest list, delete the purchase.

  An untick of an item that is already unticked writes nothing.
- The `ShoppingItemsViewModel` tick and delete handlers report the hint
  outcome when the item has a hint impression, after their own work is
  done. Telemetry never throws into them.

## Data model

### Ids

All ids are MD5 digests of UTF-8 text, written as a UUID: the 16 digest
bytes as hex, without setting UUID version bits, so Kotlin and SQL agree.

| Id | Text hashed |
|---|---|
| `tick_id` | `"<item id>:<tick time in epoch millis>"` |
| `purchase_id` (account) | `"<user id>:<tick_id>"` |
| `purchase_id` (guest) | `"guest:<tick_id>"`, re-keyed to the account form at claim |

In SQL: `md5(item_id::text || ':' || floor(extract(epoch from ticked_at) *
1000)::bigint)::uuid`. Tick times are written in milliseconds by the app, so
the conversion is exact. A test pins one input to its expected ids in both
Kotlin and SQL.

### Room (version 5 → 6, additive)

`shopping_items` gains `pickedUpAt INTEGER` (nullable, epoch millis): the time
of the current or most recent tick. It is never cleared by an untick.

`ItemRow` gains `@SerialName("picked_up_at") pickedUpAt: String?`;
`ItemRow.toEntity` and the push mapper copy it.

New table `purchases`:

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | `purchase_id` |
| `tickId` | TEXT | indexed |
| `accountId` | TEXT, null | owner; null means a guest purchase on this phone |
| `sourceItemId` | TEXT | the ticked item; no foreign key, history outlives items |
| `name` | TEXT | as ticked, for display and for chips |
| `itemKey` | TEXT | computed locally; index (`accountId`, `itemKey`) |
| `categoryId` | TEXT, null | the item's category at tick time |
| `boughtAt` | INTEGER | the tick time, epoch millis |
| `deletedAt` | INTEGER, null | tombstone from an undo |
| `pendingUpload` | INTEGER | to send with `upload_own_purchases`: a guest-era purchase after sign-in, or a tick on a list that never reached the server (D19) |

New table `tick_outbox`, the events not yet accepted by the server:

| Column | Type | Notes |
|---|---|---|
| `tickId` | TEXT PK | |
| `accountId` | TEXT | the account whose push sends it |
| `itemId`, `listId` | TEXT | |
| `tickedAt` | INTEGER | epoch millis |
| `name`, `categoryId` | TEXT | snapshot at tick time; the name is cut to 500 characters, the server's limit |
| `undoneAt` | INTEGER, null | set by an undo |
| `revision` | INTEGER | bumped on every change, for compare-and-delete after a push |

New tables on this phone only, never synced; guests use the empty string as
`accountId`:

- `item_mutes`: (`accountId`, `itemKey`) primary key, `keptInARow`,
  `mutedUntil`.
- `hint_impressions`: `itemId` primary key, `accountId`, `canonicalKey`,
  `shownAt`, `holdout`. Written when a hint first applies to an item; read and deleted
  when that item is removed, ticked or muted, to send the outcome event.
  Rows older than 30 days are pruned at start-up.
- `reminder_followups`: (`accountId`, `isoWeek`) primary key, `postedAt`,
  `itemKeys` (the canonical keys named), `holdout`. One row per posted or
  would-have-posted notification. The daily worker scores each row once it
  is 7 days old, sends `GotThisReminderFollowUp`, and deletes it. A new
  notification never overwrites an unscored row; with one notification per
  ISO week, at most two are pending at once.

Device prefs (`GotThisPrefs`): `keyVersion`, `backfillDone`, the guest
holdout number (0–99, drawn once), the last applied reset time per account,
a pending clear (its time) per account waiting to be sent,
`usualsHiddenUntil`, the ISO week a guest last got a notification, and the
two Settings switches.

### Server (Supabase migration `006_got_this.sql`)

**Tables**

1. **`items.picked_up_at timestamptz`** (nullable): the latest tick time.
2. **`ticks`**: `id uuid primary key` (the tick id), `item_id uuid not
   null`, `list_id uuid not null references lists on delete cascade`,
   `ticked_at timestamptz not null`, `name text not null`, `category_id
   text`, `undone_at timestamptz`, `source text not null` (`app`, `legacy`
   or `backfill`), `created_at timestamptz not null default now()`. Indexes
   on `list_id` and `created_at`.
3. **`purchases`**: `id uuid primary key`, `user_id uuid not null references
   auth.users on delete cascade`, `tick_id uuid not null`, `source_item_id
   uuid not null` (no foreign key), `name text not null`, `category_id text`,
   `bought_at timestamptz not null`, `updated_at timestamptz not null default
   now()` (the existing `set_updated_at` trigger), `deleted_at timestamptz`.
   Indexes on (`user_id`, `updated_at`) and (`tick_id`).
4. **`purchase_history_resets`**: `user_id uuid primary key references
   auth.users on delete cascade`, `reset_at timestamptz not null`.
5. **`running_low_notices`**: `user_id uuid references auth.users on delete
   cascade`, `iso_week text` (for example `2026-W46`), `posted_at
   timestamptz not null default now()`, primary key (`user_id`, `iso_week`).
6. **RLS** on `ticks`, `purchases`, `purchase_history_resets` and
   `running_low_notices`: select on the last three where `user_id =
   auth.uid()`, nothing on `ticks`. No insert, update or delete policies on
   any of them; every write goes through a trigger or an RPC.

**Turning ticks into purchases**

7. **After insert or update on `ticks`**, security definer:
   - on insert of a live row (`undone_at` is null): insert one purchase for
     each person credited by I3 — the list's `created_by`, and
     every `list_members` row with `joined_at <= ticked_at` — skipping anyone
     whose `reset_at >= ticked_at` (I6), `on conflict (id) do nothing`;
   - when `undone_at` is set, on insert or update: set `deleted_at = now()`
     on every purchase with that tick id, for every person (I4).

   An update can only take a row from live to undone (step 8), so purchases
   are only ever created by the first copy of a live event.
8. **`ticks` rows are written only through `got_this_private.record_tick_rows`**
   (an internal function used by the RPC, the legacy path and the backfill,
   not callable by clients, D21): `insert ... on
   conflict (id) do update set undone_at = coalesce(ticks.undone_at,
   excluded.undone_at)`. The name, category and tick time are write-once; the
   first copy to arrive keeps them. Once undone, a tick stays undone, so an
   undo that arrives first is never overridden by the tick arriving later,
   and step 7 never creates purchases for it (I4).

**RPCs**

9. **`record_ticks(p_events jsonb)`**: at most 500 events per call, or the
   whole call is rejected (the phone sends in batches of 500). For each
   event, accept it only when
   - its `id` equals the tick id recomputed from `item_id` and `ticked_at`
     (I1), so a caller cannot choose ids;
   - `name` is 1 to 500 characters and `category_id` at most 100;
   - the caller is in `list_members` for `list_id` (creators always are:
     `add_creator_membership` adds them when the list is created);
   - an item with `item_id` exists on that list (items are pushed before
     ticks, so a missing item means a retry, not a rejection);
   - `ticked_at` is within the last 30 days and no more than 1 day ahead of
     the server clock;
   - if `undone_at` is set, it is between `ticked_at` and `ticked_at + 2
     hours` (D9, D18); an undo outside that is ignored and the tick kept.

   Accepted events go through `record_tick_rows` with `source = 'app'`. The
   reply puts every event in one of three classes:

   | Class | When | The phone |
   |---|---|---|
   | accepted | every check passed | deletes the outbox row |
   | retry | the item is not on the server yet, or `ticked_at` is between 1 and 30 days ahead of the server clock | keeps the row unchanged and sends it again on later pushes |
   | rejected | anything else: wrong id, size limits, not a member, older than 30 days, more than 30 days ahead | deletes the outbox row; the phone then sends its own live purchase as an own purchase, except for more than 30 days ahead |

   An event's time and id never change. A future-dated event is accepted as
   it stands once the server clock comes within a day of its time.
10. **`upload_own_purchases(p_rows jsonb)`**: for the caller only, insert
    each purchase with the account-form id, which the server recomputes from
    the caller and the row's tick id, skipping rows at or before the caller's
    `reset_at`, `on conflict (id) do nothing`. At most 500 rows per call, with
    the same name and category limits as step 9. It carries the two kinds of
    history that cannot go through `ticks`: guest-era purchases, made before
    their lists were on the server, and ticks on lists that never reached it
    (D19). It credits only the caller, which is right for both: nobody else
    was on those lists.
11. **`clear_purchase_history(p_cleared_at timestamptz) returns timestamptz`**
    (the resulting `reset_at`): with `t =
    least(p_cleared_at, now())`, delete the caller's purchases with
    `bought_at <= t`, then set their `reset_at = greatest(existing reset_at,
    t)` (I7). The phone sends the moment the person pressed Clear, so a clear
    queued offline neither wipes ticks made after it nor, when two clears
    arrive in reverse order, moves the reset back.
12. **`claim_running_low_week(p_week text) returns boolean`**: insert the
    caller's row for that week, `on conflict do nothing`; true only when this
    call inserted it. `p_week` must match `YYYY-Www`.

**Permissions and cleanup**

- Every RPC above is `security definer` with `set search_path = ''`, checks
  `auth.uid()` is not null, and is granted to `authenticated` only (revoked
  from `public` and `anon`). Trigger functions follow the same `search_path`
  rule.
- `got_this_private` holds `record_tick_rows` and the id helpers. It is not in
  PostgREST's exposed schemas, `usage` on it is revoked from `public`, `anon`
  and `authenticated`, and so is `execute` on its functions.
- A daily `pg_cron` job (`got_this_purge_ticks`, 03:00 UTC) deletes `ticks`
  rows older than 60 days, whether or not anyone is active. No event can
  reach them by then (the 30-day limit), and the privacy policy promises 60
  days. It also deletes `running_low_notices` rows older than 8 weeks.

**Old app versions**

13. **Before insert or update on `items`** (`items_track_picked_up_at`).
    Clients push items with upsert (`INSERT … ON CONFLICT DO UPDATE`), and
    Postgres fires the before-insert trigger for every proposed row before
    it detects the conflict, so the two paths are kept apart:
    - **Insert:** if a row with that id already exists, do nothing here; the
      update path decides. Otherwise, a ticked item that arrives with no
      `picked_up_at` came from an app version that does not know about
      ticks: set `picked_up_at := now()` and write a tick through
      `record_tick_rows` with `source = 'legacy'`, the item's current name
      and category, and `ticked_at` equal to that value.
    - **Update:** when `is_picked_up` turns from false to true, the
      incoming `picked_up_at` is unchanged, and the push did not carry a
      tick time, the writer is an old app version (they never send the
      column): set it and write a legacy tick the same way. Whether the push
      carried one is read at the before-insert call, which sees the proposed
      row: for an existing row it leaves a transaction-local note,
      `got_this.t<item id>` = `yes` when the row carried `picked_up_at` and
      `no` when it did not. A plain UPDATE leaves no note and counts as an old
      app's. A stale whole-row push from a current app (the server holds
      false with tick time P after another phone's untick, and the push says
      true with P) carries P, so it records no tick.
    - A tick (false to true) that carries a time of its own keeps it, even
      when it is older than the stored one: that phone's clock is behind, and
      it derives the tick id for a later undo from its own time. In every
      other case, `new.picked_up_at := greatest(new.picked_up_at,
      old.picked_up_at)`. `greatest` ignores nulls, so a null or older value
      from any other write leaves the newer one in place.

    Current app versions always send a `picked_up_at` with a tick, so the
    legacy path never fires for them and never duplicates their event.
    Pushing an already-ticked item again (a rename, say) writes no tick.

**Backfill, once, inside the migration**

14. For every item with `is_picked_up`, no `deleted_at` and no
    `picked_up_at`: write a tick through `record_tick_rows` with `source =
    'backfill'`, `ticked_at = updated_at` and the item's name and category,
    which step 7 turns into purchases for the people on the list then (D10,
    I3). Then set `picked_up_at = updated_at` on those items with
    `items_set_updated_at` disabled, so every phone does not re-download
    every ticked item.

`delete_user()` needs no change: purchases, resets and notices cascade with
`auth.users`, and a deleted solo list takes its ticks with it.

Nothing lets a member leave a list today. If leaving is added, `list_members`
needs a `left_at`, and step 7 must credit only people who had not left by
the tick time.

## Sync

### Push

Before `deleteNeverSentDeletedLists` in `SyncEngine.pushLocked`, in the same
transaction as that deletion: the outbox events of the lists about to be
dropped are deleted, and their live local purchases are marked
`pendingUpload` (D19). Undone ones are already tombstoned and stay local.

Then, after the existing item push, in this order:

1. A pending clear goes to `clear_purchase_history` with its stored time. It
   stays pending until the server answers. On success the phone drops the
   pending clear, unless Clear was pressed again meanwhile. If the server
   answers with a later reset than the phone had applied (another phone
   cleared since), the phone applies it at once, exactly as a pulled reset
   is applied, before storing it. On failure the push carries on with the next steps and
   the clear is sent again next time.
2. `tick_outbox` rows go to `record_ticks` in batches of 500. Accepted and
   rejected rows are deleted only if their `revision` is unchanged since they
   were read, so an undo made during the call is sent next time. Rows to
   retry stay. A live purchase whose event was rejected (and not more than
   30 days ahead), and the live purchases of rows dropped for being more than
   30 days old, get `pendingUpload` so that step 3 sends them as the person's
   own: the phone keeps showing them, so the server and the person's other
   phones must hear of them.
3. Purchases with `pendingUpload` (guest-era, and D19) go to
   `upload_own_purchases` in batches of 500.

`hasUnsyncedChanges` also counts outbox rows, `pendingUpload` purchases and a
pending clear for the account (D20).

Item rows carry `picked_up_at` as before. An untick sends the same
`picked_up_at`, which changes nothing on the server.

### Pull

`catchUpAccount` gains a step after items, in `GotThisSync`.

The phone's **watermark** for an account is the later of the reset time it
last applied from the server and its own pending clear, if it has one. No
purchase with `boughtAt` at or before the watermark is ever written to Room
(I6). The watermark is read in the same transaction that writes the pulled
rows, so a Clear pressed while a pull is in flight still wins. This is what keeps cleared history from coming back when the clear
call fails but the pull succeeds: the server has not heard of the clear yet
and still returns the old purchases.

1. Read the caller's `purchase_history_resets` row. If it is newer than the
   reset time this phone last applied for the account: delete the account's
   purchases with `boughtAt <= reset_at`, but **not** outbox events, which
   other people on the list are still owed (the server skips this person for
   them, I6); clear the mutes, hint impressions and pending follow-ups, since
   they were learned from the deleted history; clear the purchases sync
   point; store the new reset time. Purchases with a later tick time stay.
2. Fetch purchases changed since the account's purchases sync point
   (`purchases:<user id>`; one per account, so a second account on the phone
   never starts from the first one's point), paged like every multi-row read
   (`fetchAll`, PostgREST's 1000-row cap). Discard every row with `boughtAt`
   at or before the watermark. Store the sync point as the newest `updated_at`
   seen minus 5 minutes, and never earlier than the stored point: the server
   stamps a row with its transaction's start time, so a slower transaction
   can commit a row older than one this pull has already seen, and the lag
   makes the next pull fetch it again (upserts are idempotent). Upsert the rest into Room by id with their
   keys computed locally; a tombstone replaces the row, but a live row does
   not replace a local tombstone whose undo is still in the outbox. The
   point moves only after everything landed, as for items; discarded rows
   need no second look, because the server deletes them when the clear
   arrives.

### Sign-in, sign-out, session loss

- **Sign-in from guest** (`claimDeviceLists`): guest purchases get the user id,
  the account-form id (from the unchanged tick id), and `pendingUpload =
  true`. The uploaded guest lists' items carry their `picked_up_at`, which is
  newer than the server's null, so step 13 writes no legacy tick for them.
  Exactly one purchase exists per guest tick. The guest backfill (below)
  always runs before a claim, so no guest item is uploaded ticked without a
  tick time. Guest purchases at or before the account's watermark are
  dropped, not claimed. If another account's history is still on the phone
  (its session was lost and a different account signs in), that history, its
  outbox and its sync point are removed first, as its lists already are.
- **Explicit sign-out** (`evictAccountLists`): the account's purchases, outbox
  events, mutes, impressions and follow-ups leave the phone with the lists,
  after the
  final push the eviction already attempts; the purchases sync point is
  cleared, so signing back in pulls the history again. Unsent events are part
  of the existing unsynced-changes warning (D20); a person who signs out
  offline anyway loses them, as with unsent edits. An unsent clear is the
  exception: it stays stored for its account and is sent when that account
  next syncs, so signing out cannot bring cleared history back (I6).
- **Session lost** (008 D6): history stays and keeps working; the outbox
  waits.

### Backfill on the phone

`GotThisBackfill` runs once, off the main thread, for guest lists only: at
start-up, or at the start of a sign-in claim if that comes first. The two
are serialised. Every item with `isPickedUp`, no `deletedAt` and no `pickedUpAt` gets
`pickedUpAt = updatedAt`, and a guest purchase with that tick time, in one
transaction per list. Account lists are backfilled on the server (step 14)
and arrive with the next pull.

### Clearing history

- **Signed in:** the phone deletes the account's purchases up to now, plus
  the mutes, impressions and follow-ups, stores the clear time as pending and
  sends it on the next push, before any tick events (push step 1). Until the
  server acknowledges it, the pending clear is part of the phone's watermark,
  so a pull cannot bring the cleared purchases back. Unsent tick
  events stay in the outbox: when they arrive they still create purchases for
  the other people on the list, and none for this person (I6). Other phones
  apply the reset on their next pull.
- **Guest:** the same local deletion; nothing to send.

## Measuring whether it works

### Telemetry

New events in `AnalyticsEvent`, following its rules: bounded enums, buckets
and booleans, never an item name or key.

| Event | Fires | Parameters |
|---|---|---|
| `GotThisHintShown` | first time a hint applies to an item id on this phone | `source` (regular / category), `daysAgo` bucket, `holdout` |
| `GotThisHintOutcome` | that item is deleted, ticked, or muted while it has an impression | `outcome` (removed / ticked / muted), `holdout` |
| `GotThisUsualsShown` | the suggestions row first shows on a day | `count` bucket, `holdout` |
| `GotThisUsualAdded` | a chip is tapped | none |
| `GotThisReminderPosted` | the weekly notification posts, or would have (holdout) | `itemCount` bucket, `holdout` |
| `GotThisReminderOpened` | the notification is tapped | none |
| `GotThisReminderFollowUp` | 7 days after a posted or would-have-posted reminder | `addedAny` (was an item belonging to any named group added to a list since the posting), `holdout` |
| `GotThisPredictionScored` | a new shop begins for an item that had a forecast | `errorDays` bucket (< −7, −7..−3, −2..2, 3..7, > 7; positive is late) |
| `GotThisWeeklySummary` | once a week from the daily worker | `regulars` bucket, `purchases` bucket |

The holdout reports the same events with `holdout = true` for what it would
have seen, which gives the baseline: a holdout "removed" rate is how often
people delete such items anyway. A holdout "would have posted" also claims
the week (D16), so it is counted once per person.

`GotThisPredictionScored` works without showing anything: every real repeat
purchase grades the earlier forecast, so accuracy is known even for people
who never open the suggestions.

### Holdout

For a signed-in person the group number is the first four bytes of
MD5(account id) read as an unsigned 32-bit integer, modulo 100. The leftover
bias is under one part in 40 million. A guest uses the phone's drawn number
(0–99). Below 10 means holdout, while the `got_this_holdout` flag is on. A
guest who signs in takes the account's number, so they may change group once;
every event carries `holdout`, so the analysis follows them. The flag is
switched off 8 weeks after release, which ends the holdout for everyone.

The holdout is measured under the same conditions as everyone else, so the
two groups differ only in what is shown: the "kept twice" mute applies to it
too, a "would have posted" is logged only when notifications are allowed,
and a "would have shown" only when the matching Settings switch is on.

### Targets, to review 4 weeks after release

| Measure | Target |
|---|---|
| Hint removal rate | at least 10%, and above the holdout's |
| Reminder follow-up (`addedAny`) | at least 50%, and above the holdout's |
| Predictions within ±2 days | at least 60% |

Missing a target means tuning `GotThisRules` (windows, the 90% due point),
not adding UI. Hints that are removed less than 5% of the time after 4 weeks
mean the windows are too long.

### Before release, if licensing allows

Replay `ItemJudge` on a public household purchase dataset (dunnhumby "The
Complete Journey" is the candidate; its licence for this use must be
confirmed first) with a JVM test harness that is not shipped, to sanity-check
the thresholds. Without a usable dataset, the release relies on
`GotThisPredictionScored`.

## Feature flags

- `got_this` (kill switch): off hides hints, suggestions and notifications,
  and stops all Got This traffic to the server (outbox, own purchases,
  pending clear, purchases pull). Recording on the phone continues and the
  queues are sent once it is on again. A missing row reads as off, so the row
  is inserted as `true` at release.
- `got_this_holdout`: on at release, off after 8 weeks (D14).

Both are copied to `GotThisPrefs` when the flags load in the UI process, and
every reader, including the background worker, uses that copy: the flag
cache is empty on a background cold start (reminders spec, D8). Before any
load, the feature is off (killed) and the holdout applies, so a fresh
install shows and sends nothing until the flag has been read. A failed fetch
leaves the stored copy as it was.

Plus adds its own flag and gate in `GotThisAccess` in its own spec. Hints and
history will never be gated.

## Privacy and release

- **Privacy policy:** a new section on purchase history: what is kept (item
  names, categories and times of ticked items), that it outlives list
  deletion, that it is shared with the people on the list it was ticked on at
  that time, that "Clear purchase history" deletes it, that account deletion
  deletes it, and that it is never sold or used for advertising. Tick events
  themselves are list data, deleted by a daily job once they are 60 days
  old.
- **`pg_cron`:** the migration enables the extension if it is not already on
  (`create extension if not exists pg_cron`) on staging and production, and
  schedules the purge job. The local stack used by the database tests has it
  too.
- **Play Data safety form:** declare purchase history (Financial info →
  Purchase history, the closest category; confirm at submission), collected,
  not shared, for app functionality, deletable. Both change **before**
  release, as in 008 D4.
- **Supabase projects.** The names are misleading:

  | Role | Project name | Ref |
  |---|---|---|
  | Production | `babegetthis-staging` | `fpzjyusgqdzqituokrwq` |
  | Staging | `babe-get-this-real-staging` | `vdiheceziirgvbudxdxg` (paused since 2026-10-31) |

- **Release order:** the database tests pass in CI against a local Supabase
  stack, which is the automated gate. Production's schema is compared with
  migrations 001–005 (read-only), since those were applied by hand. Then
  `006_got_this.sql` runs on production (`fpzjyusgqdzqituokrwq`) with
  explicit confirmation, on staging first if it has been resumed. The
  two-phone checks below run against that backend, and then the app is
  released. Old app versions keep working: they never send
  `picked_up_at`, and step 13 records their ticks.
- **Notification channel:** created on first use, "Running-low reminders".

## Error handling and edge cases

- **Offline:** everything except the cross-device part works from Room. Each
  offline tick waits in the outbox as its own event, so a tick, an untick
  after 2 hours and a re-tick made offline reach the server as two
  purchases (I5).
- **Rename or recategorise after the tick, before sync:** the event carries
  the name and category from the moment of the tick, so the purchase keeps
  them (I5).
- **Offline tick, membership changes before upload:** credit follows the tick
  time (I3), not the upload time. Someone who joined after the tick gets
  nothing.
- **Undo before the tick reaches the server** (the partner unticks on their
  phone first, or the outbox merges both): the undone event is stored and the
  tick, when it arrives, cannot revive it (step 8).
- **Undo on another phone:** the second phone has the item with its
  `pickedUpAt`, so it applies the same 2-hour rule and builds the same tick
  id. The snapshot comes from the pulled purchase, or from the item's current
  name and category if the purchase has not arrived yet.
- **Clock skew:** tick times use the phone's clock. A phone that is days off
  records the wrong day and may credit or skip a member who joined near that
  time.
- **A tick made while the phone's clock was ahead:** the event keeps its
  recorded time and id; correcting the clock rewrites nothing. Up to 30 days
  ahead, the server answers "retry" and accepts the event unchanged once its
  own clock comes within a day of that time, so the purchase carries the
  recorded date. Until that date arrives the phone ignores the purchase when
  judging, and an untick does not undo it. More than 30 days ahead, the
  server rejects it for good and the phone deletes both the outbox row and
  the local purchase.
- **An outbox event older than 30 days:** rejected by the server; the phone
  drops it and sends its purchase as the person's own instead.
- **Item or list deleted after the tick:** the purchase stays (no foreign
  key). If the item or list reached the server, it is still there as a
  tombstone when its tick is sent, so the event validates. Items added
  offline to a list that is then deleted offline are still pushed (the
  pending-items query does not filter on the list's tombstone), so their
  events validate too.
- **The clear call fails but the pull succeeds:** the pending clear is in the
  watermark, so the old purchases the server still returns are discarded. If the list never
  reached the server, its ticks go up as the person's own purchases (D19).
- **Clear history with ticks still unsent:** the ticks still reach the other
  people on the list; this person gets nothing from them (D15, I6).
- **Sign out offline with ticks unsent:** the existing warning lists them as
  unsynced changes (D20).
- **A partner on an old app version:** their ticks count through step 13;
  their unticks never undo, so an accidental tick by them stands.
- **Weekly claim fails (offline, server error):** no notification that day
  (D12); the next daily check tries again.
- **Custom categories:** use the Unknown window.
- **The worker finds nothing due, or no notification permission:** nothing
  posts and nothing retries.

## Testing

### Database (`supabase/tests/`, pgTAP, new CI workflow `db-tests.yml`)

The repository has no `supabase/config.toml` yet. The first task is
`supabase init` and confirming that migrations 001–006 apply in order to an
empty local database; the CI job then runs `supabase start` and `supabase test
db`. Tests act as specific users by setting the JWT claims and role, so RLS is
exercised, not bypassed.

| Test | Expected |
|---|---|
| A records a tick on a list shared with B | one purchase each, same tick id |
| The same event recorded twice | still one purchase each |
| Two events for one item (tick, then re-tick days later) arrive in one call | two purchases each (I5) |
| An undo within 2 hours after the tick | both purchases tombstoned |
| The undone event arrives before the live one | no live purchases, ever (I4) |
| An undo more than 2 hours after the tick | ignored; purchases stay |
| A second copy of an event with a different name | the first name stays |
| A member joins between tick and undo | the joiner gets nothing; the undo touches only the tick's purchases |
| A tick time before a member's `joined_at` | that member gets nothing |
| The list creator records and undoes a tick | accepted (creators are members) |
| A stranger calls `record_ticks` for A's list | rejected; nothing changes |
| An event older than 30 days, or more than 30 days ahead | rejected |
| An event 5 days ahead | returned for retry; the same event is accepted once the clock is within a day of it |
| An event for an item not yet on the server | returned for retry |
| An event for an item on a tombstoned list shared with B | accepted; A and B each get the purchase |
| `clear_purchase_history` | returns the resulting `reset_at` |
| An old-app push (no `picked_up_at`) that ticks | `picked_up_at` set to now; one legacy tick; purchases created |
| An old-app push that edits or unticks | nothing changes in history |
| A new-app push with `picked_up_at` null or older, on an item that stays ticked | `picked_up_at` unchanged |
| A new-app re-tick whose time is older than the stored one | its own time is kept; no legacy tick |
| A stale whole-row push from a current app (false, P on the server; true, P pushed) | no legacy tick; `picked_up_at` unchanged |
| A member whose clock puts the tick before they joined sends it | they are credited too |
| A new-app tick (newer `picked_up_at`) plus its event | exactly one tick, source `app` |
| A guest item with a backfilled tick, then claim and `upload_own_purchases` | exactly one purchase; no legacy tick |
| Clear history, then an event from before the reset | no purchase (I6); an event after it creates one |
| A clear sent late with an earlier `p_cleared_at` | purchases ticked after that time survive |
| Two clears arriving in reverse order | `reset_at` is the later one (I7) |
| A clears history, then A's event from before the clear arrives on a list shared with B | B gets the purchase; A does not |
| An event whose `id` is not the recomputed tick id | rejected |
| 501 events in one call, or a 501-character name | rejected |
| `upload_own_purchases` with a row for another user's id | only the caller's purchase is created, under the recomputed id |
| An authenticated client calls `got_this_private.record_tick_rows` directly | permission denied |
| `anon` calls any Got This RPC | permission denied |
| The purge job | removes `ticks` older than 60 days and keeps newer ones |
| B cannot select A's purchases, resets or notices, or any ticks | zero rows |
| `claim_running_low_week` twice for one week | true, then false |
| Backfill | one backfill tick per ticked item, `updated_at` unchanged, purchases only for people on the list then |

### Unit (`app/src/test`)

- `ItemKeyTest`: a table of names to keys (plurals, quantities, accents,
  modifiers) and match pairs, including "milk" ≠ "coconut milk" and
  "bannana" = "banana".
- `GotThisIdsTest`: the pinned id vectors shared with the SQL tests, and the
  holdout number from four bytes.
- `ItemJudgeTest`: weekly eggs become regular on the third shop after 14
  days; BBQ sausages bought once never do; three purchases in one week do
  not; a monthly item gets a 15-day window; 7-day eggs get a 3-day window
  (rounded down); an extra short gap leaves a weekly item regular; a lapsed
  item bought again is regular again; an item unbought for more than 2.5 ×
  *m* lapses; due at ⌈0.9 × *m*⌉.
- `PredictionScoringTest`: the forecast comes from the history up to the
  previous shop; a weekly item bought on day 60 is scored as > 7 days late,
  not skipped; the error sign.
- `ItemGroupsTest`: "banana", "bannana", "banana" form one group with
  "banana" canonical and three purchases, which `ItemJudge` then finds
  regular; "bannanna" does not chain in through "bannana"; the order is
  stable for equal counts; "milk" and "coconut milk" are separate groups; a
  mute under a non-canonical key still mutes the group.
- Future-dated purchases are ignored by `ItemJudge` until their date.
- `GotThisRulesTest`: every default category id has a window.
- `ShopDayTest`: a clear Saturday shopper, a shopper with no pattern, fewer
  than 4 days.
- `GotThisAccessTest`: account and guest holdout numbers, the flag ending the
  holdout, the kill switch and the Settings switches.

### Robolectric and Room

- Tick and untick transactions: tick writes the item, the purchase and the
  outbox event together; untick within 2 hours tombstones and sets
  `undoneAt`, or re-queues an event already sent; after 2 hours keeps;
  `pickedUpAt` is never cleared.
- Offline sequence: tick, untick after 2 hours, re-tick, then one push sends
  two events.
- Rename between tick and push: the event keeps the original name.
- Outbox compare-and-delete: an undo made while a push is in flight is sent
  on the next push.
- Guest backfill then claim produces one pending upload per purchase with
  account-form ids, and items carry the same tick times.
- Reset handling: a newer reset deletes purchases at or before it and keeps
  later ones, keeps every outbox event, and clears mutes, impressions and
  follow-ups; an older or equal reset does nothing.
- Clear with ticks unsent: local purchases go, outbox events stay, and the
  next push sends the clear before the events.
- Clear call fails, pull succeeds: purchases at or before the pending clear
  are not written; later ones are; the clear is still pending and is sent on
  the next push; once acknowledged, the returned reset time is stored and
  never lowers the watermark.
- Follow-ups: two notifications 3 days apart in consecutive ISO weeks leave
  two rows, each scored and deleted 7 days after its own posting.
- Untick with `pickedUpAt` in the future does not undo. A "retry" reply
  leaves the outbox row unchanged; a "rejected" reply for a far-future event
  deletes the row and the local purchase.
- A list created, ticked and deleted offline: the push turns its ticks into
  `pendingUpload` purchases in the same transaction that drops the list, and
  sends them with `upload_own_purchases`.
- `hasUnsyncedChanges` is true with only an outbox event, a pending upload or
  a pending clear.
- List screen lookup: an unticked "bannana" finds purchases stored under
  "banana".
- Key version change recomputes keys and clears mutes, impressions and
  follow-ups.
- `RunningLowWorker`: posts at most once per ISO week, only after the claim
  returns true; silent offline, with nothing due, or without permission;
  names up to 3 items.
- Migration 5 → 6 in `MigrationTest`, as for 4 → 5.

### Journeys (`app/src/androidTest`)

The instrumented suite replaces auth and Supabase with local fakes and never
reaches a network, so journeys cover one phone:

1. Guest: tick "Milk" on one list, add "milk" to another, see "Bought today",
   remove it from the hint menu.
2. Seeded history with a weekly regular: the suggestions row shows it, a chip
   adds it, and the row no longer offers it.
3. A finished list: adding a chip moves it back to the active lists and
   resets its progress card.

### Two-phone checks (manual, real backend)

The cross-member paths are covered automatically by the database tests plus
the sync tests. These confirm them end to end on a phone and an emulator
with throwaway accounts, after the migration is applied:

1. A ticks "Eggs" on a shared list; B adds "eggs" to B's own list and sees
   the hint after sync.
2. A goes offline, ticks "Butter" on a shared list already on the server,
   deletes the list, and reconnects; B sees the hint for "butter".
3. A ticks and unticks within a minute; B's hint goes after sync.
4. A clears history; A's hints go on both of A's devices; B's stay.
5. Two phones on one account with a due regular: only one gets the weekly
   notification.

### Deliberately not tested

Notification timing under Doze, and the Play Data safety wording. Telemetry
shows the first in the field; the second is a submission checklist item.

## Out of scope

- **Plus:** billing, the household subscription, the trial and any lock
  (own spec; D13).
- Family groups (D2) and list marks (D1).
- Leaving a shared list (see the note under Server).
- Quantities ("2 bags") in the rhythm: a bigger pack shows up as a longer gap
  anyway.
- Prices or spending.
- Matching across languages, or with a language model.
- Server-side push for running-low reminders.
- Auto-categorization of typed items (TODO.md). Until it ships, typed items
  often have no category and get the 7-day window.
- Viewing or editing the history itself.

## Accepted trade-offs

- People who delete bought items instead of ticking them are invisible to
  Got This.
- A monthly BBQ host's sausages become a regular, and get a monthly reminder.
- A member of a shared trip list gets history for what others ticked on it.
  Running-low is unaffected (one trip is one shop); a hint may show on a later
  list.
- Backfilled and old-app tick times are approximate: backfill uses the item's
  last edit time, old apps the time the server received the tick.
- An old app version records only the latest tick of an item between two of
  its pushes, and uses the name at push time.
- The category windows are judgment, not data. They tell us how long food
  keeps, not how fast it is used up, and are tuned from telemetry.
- A partner on an old app version cannot undo an accidental tick.
- A stale push from an old app version can still record a tick nobody made:
  an old app that has not pulled another phone's untick pushes an edit of
  the item, which looks like a tick (the push carries no tick time).
  Current apps are not affected: their pushes carry the tick time.
- A list member who fabricates event times can undo another member's tick
  from the last 30 days (D18).
- A phone offline for more than 30 days keeps those ticks to itself.
- Running-low says nothing until an item has 3 shops over 14 days; for people
  with no ticked items on their phone at release, that is at least 2 weeks.
- Mutes and "kept" counts are per phone.
- A signed-in phone that is offline at 18:00 on the eve of shopping day sends
  no notification that week unless a later daily check gets through.
