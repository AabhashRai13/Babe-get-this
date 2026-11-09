# Got This — design

**Date:** 2026-11-09 · **Status:** draft, third revision after review, awaiting approval · **Branch:** `feat/got-this`

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
| I3 | The people credited with a tick are the list's creator and the members whose `joined_at` is at or before the tick time. Nothing else changes who is credited. |
| I4 | A tick that is undone has no live purchases, whichever of its tick and undo reaches the server first. An undo affects that tick's purchases and nothing else. |
| I5 | Every tick made on a current app version reaches the server as its own event, carrying the name and category the item had when it was ticked, provided the phone reconnects within 30 days of the tick and the person does not choose to sign out with it unsent (D20). A tick on a list that never reached the server reaches it as that person's own purchase instead (D19). Clearing history never discards an unsent event. |
| I6 | After a person's reset time, they have no purchase with a tick time at or before it, on the server or any phone. Purchases with a later tick time are kept. |
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
- An item already unticked on any of the person's lists is never suggested.
- **Not now** (an icon at the end of the row) hides the row on every list for
  24 hours.
- Up to 6 chips, most overdue first; the rest collapse into "+N", which
  expands the row.
- When notifications are not allowed, the row ends with **Get a weekly
  heads-up**, which opens the existing permission sheet with Got This copy.

### The weekly notification

At most one a week per person (D16), at about 18:00 on the evening before the
person's usual shopping day:

> **Running low?**
> Eggs, milk and bread are usually bought around now.

- It names up to 3 items that are due by the next day, most overdue first.
  With more than 3: "Eggs, milk, bread and 2 more".
- Tapping it opens the items screen of the most recently edited list, finished
  or not, where the suggestions row is. With no lists at all, it opens the
  list catalog with the create-list dialog open; the new list shows the row.
- With no clear usual shopping day (see Running low), it goes out at about
  18:00 on the first day something becomes due, still at most once a week.
- A week is an ISO week (Monday to Sunday) in the phone's time zone.
- Its own notification channel, "Running-low reminders", separate from
  shopping reminders, so either can be muted in system settings.

### Settings

A new **Got This** section:

- **Bought-recently hints** — switch, on by default.
- **Running-low reminders** — switch, on by default. Shows "Notifications are
  off for this app" with a link to system settings when the permission is
  missing, like the shopping reminders row.
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
2. Punctuation to spaces, collapse whitespace, trim.
3. Drop a leading or trailing quantity: numbers, fractions, and the units
   `x g kg ml l pack packs bag bags bottle bottles can cans box boxes dozen`,
   plus a following "of". "2 bags of rice" → "rice", "milk 2l" → "milk".
4. Drop the size and freshness words `fresh organic large small big medium`.
5. Make each word singular with plain English rules: "-ies" → "-y"
   (berries), "-oes" → "-o" (tomatoes), "-ses/-xes/-ches/-shes" lose "-es",
   a final "s" goes unless the word ends in "ss", "us" or "is".

Two keys match when they are equal, or when both are at least 5 characters,
have the same number of words, and differ by one edit (Damerau–Levenshtein
distance 1: "bannana" and "banana"). "Milk" and "coconut milk" never match.

The key is stored with each purchase in Room. `ItemKey.VERSION` is stored in
prefs. When it changes, start-up recomputes every purchase's key from its
stored name, and clears what was stored by key and cannot be recomputed:
mutes, hint impressions and the pending reminder follow-up.

Names in other languages still match themselves exactly; only the plural
rules are English.

### What counts as a purchase

- A tick that is not undone within 2 hours (D9). Each tick is one purchase,
  so a list that is reused and ticked again next week records a new one.
- Purchases of the same key are grouped into **shops**: a purchase less than
  2 days after the first day of the current shop joins it. A BBQ bought over
  Friday and Saturday is one shop.
- Days are local calendar days.

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
for this; the earlier purchases are already in Room.

## Components

All new code lives in `feature/gotthis/`, following the existing feature
layout.

| Unit | Does | Depends on |
|---|---|---|
| `domain/ItemKey` | Name → key, and key matching | nothing |
| `domain/GotThisIds` | Tick id, purchase id, holdout number (MD5) | nothing |
| `domain/GotThisRules` | Every threshold and the category window table | nothing |
| `domain/ItemJudge` | Purchases of one key → `ItemProfile` (shops, regular, *m*, lapsed, due, window) for a given day, and prediction scoring | `GotThisRules` |
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

- **List screen:** read the owner's distinct item keys (one query on the
  `accountId, itemKey` index, observed as a Flow so a pull refreshes it).
  Match each unticked item's key against that set in memory with
  `ItemKey.matches`, which is where one-typo matches like "bannana" are
  found. Then read the purchases for the matched keys in one query.
- **Daily worker:** read all of the owner's live purchases and group them by
  key.

### Repository hooks

Both are single Room transactions, so a crash cannot leave an item, its
purchase and its outbox entry disagreeing.

- **Tick** (`togglePickedUp(itemId, true)`): set `isPickedUp`, set
  `pickedUpAt = now`, insert the local purchase for the current owner (the
  signed-in user, or the guest) with the ids from I1 and I2, and, on an
  account list, insert an outbox event with the item's current name and
  category. The local purchase is the same row the server will create, so
  the pull overwrites it rather than adding one.
- **Untick** (`togglePickedUp(itemId, false)`): clear `isPickedUp`; leave
  `pickedUpAt` as it is. If the tick was less than 2 hours ago:
  - tombstone the local purchases with that tick id;
  - on an account list, set `undoneAt = now` on the outbox event for that
    tick, or insert it again with `undoneAt` set when it was already sent.
    The name and category come from the local purchase, which holds the
    values at tick time;
  - on a guest list, delete the purchase.
- The `ShoppingItemsViewModel` tick and delete handlers report the hint
  outcome when the item has a hint impression.

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
| `listId` | TEXT | the list it was ticked on, for building undo events |
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
| `itemId`, `listId` | TEXT | |
| `tickedAt` | INTEGER | epoch millis |
| `name`, `categoryId` | TEXT | snapshot at tick time |
| `undoneAt` | INTEGER, null | set by an undo |
| `revision` | INTEGER | bumped on every change, for compare-and-delete after a push |

New tables on this phone only, never synced; guests use the empty string as
`accountId`:

- `item_mutes`: (`accountId`, `itemKey`) primary key, `keptInARow`,
  `mutedUntil`.
- `hint_impressions`: `itemId` primary key, `accountId`, `shownAt`,
  `holdout`. Written when a hint first applies to an item; read and deleted
  when that item is removed, ticked or muted, to send the outcome event.
  Rows older than 30 days are pruned at start-up.

Device prefs (`GotThisPrefs`): `keyVersion`, `backfillDone`, the guest
holdout number (0–99, drawn once), the last applied reset time per account,
a pending clear (its time) per account waiting to be sent,
`usualsHiddenUntil`, the ISO week a guest last got a notification, the
pending reminder follow-up (posting time and the item keys named, at most one
at a time), and the two Settings switches.

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
   reply lists the accepted tick ids, those to retry, and those rejected for
   good (which the phone drops).
10. **`upload_own_purchases(p_rows jsonb)`**: for the caller only, insert
    each purchase with the account-form id, which the server recomputes from
    the caller and the row's tick id, skipping rows at or before the caller's
    `reset_at`, `on conflict (id) do nothing`. At most 500 rows per call, with
    the same name and category limits as step 9. It carries the two kinds of
    history that cannot go through `ticks`: guest-era purchases, made before
    their lists were on the server, and ticks on lists that never reached it
    (D19). It credits only the caller, which is right for both: nobody else
    was on those lists.
11. **`clear_purchase_history(p_cleared_at timestamptz)`**: with `t =
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

13. **Before insert or update on `items`** (`items_track_picked_up_at`):
    - On update, `new.picked_up_at := greatest(new.picked_up_at,
      old.picked_up_at)`. `greatest` ignores nulls, so a null or older value
      from any client leaves the newer one in place.
    - When `is_picked_up` turns true (on insert, or from false on update) and
      `picked_up_at` is not newer than before, the push came from an app
      version that does not know about ticks. Set `picked_up_at := now()`
      and write a tick through `record_tick_rows` with `source = 'legacy'`,
      the item's current name and category, and `ticked_at` equal to that
      value. Current app versions always send a newer `picked_up_at` with a
      tick, so this path never fires for them and never duplicates their
      event.

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

1. A pending clear goes to `clear_purchase_history` with its stored time.
2. `tick_outbox` rows go to `record_ticks` in batches of 500. Accepted and
   rejected rows are deleted only if their `revision` is unchanged since they
   were read, so an undo made during the call is sent next time. Rows to
   retry stay.
3. Purchases with `pendingUpload` (guest-era, and D19) go to
   `upload_own_purchases` in batches of 500.

`hasUnsyncedChanges` also counts outbox rows, `pendingUpload` purchases and a
pending clear for the account (D20).

Item rows carry `picked_up_at` as before. An untick sends the same
`picked_up_at`, which changes nothing on the server.

### Pull

`catchUpAccount` gains a step after items, in `GotThisSync`:

1. Read the caller's `purchase_history_resets` row. If it is newer than the
   reset time this phone last applied for the account: delete the account's
   purchases with `boughtAt <= reset_at`, but **not** outbox events, which
   other people on the list are still owed (the server skips this person for
   them, I6); clear the mutes, hint
   impressions and pending follow-up, since they were learned from the
   deleted history; clear the purchases sync point; store the new reset time.
   Purchases with a later tick time stay (I6).
2. Fetch purchases changed since the `purchases` sync point, paged like every
   multi-row read (`fetchAll`, PostgREST's 1000-row cap). Upsert each into
   Room by id with its key computed locally; a tombstone replaces the row.
   The point moves only after everything landed, as for items.

### Sign-in, sign-out, session loss

- **Sign-in from guest** (`claimDeviceLists`): guest purchases get the user id,
  the account-form id (from the unchanged tick id), and `pendingUpload =
  true`. The uploaded guest lists' items carry their `picked_up_at`, which is
  newer than the server's null, so step 13 writes no legacy tick for them.
  Exactly one purchase exists per guest tick.
- **Explicit sign-out** (`evictAccountLists`): the account's purchases, outbox
  events, mutes and impressions leave the phone with the lists, after the
  final push the eviction already attempts; the purchases sync point is
  cleared, so signing back in pulls the history again. Unsent events are part
  of the existing unsynced-changes warning (D20); a person who signs out
  offline anyway loses them, as with unsent edits.
- **Session lost** (008 D6): history stays and keeps working; the outbox
  waits.

### Backfill on the phone

`GotThisBackfill` runs once at start-up, off the main thread, for guest lists
only. Every item with `isPickedUp`, no `deletedAt` and no `pickedUpAt` gets
`pickedUpAt = updatedAt`, and a guest purchase with that tick time, in one
transaction per list. Account lists are backfilled on the server (step 14)
and arrive with the next pull.

### Clearing history

- **Signed in:** the phone deletes the account's purchases up to now, plus
  the mutes, impressions and follow-up, stores the clear time as pending and
  sends it on the next push, before any tick events (push step 1). Unsent tick
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
| `GotThisReminderFollowUp` | 7 days after a posted or would-have-posted reminder | `addedAny` (was any named item added to a list), `holdout` |
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

- `got_this` (kill switch, read like the reminders flag at UI time): off hides
  hints, suggestions and notifications. Recording continues.
- `got_this_holdout`: on at release, off after 8 weeks (D14).

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
  | Staging | `babe-get-this-real-staging` | `vdiheceziirgvbudxdxg` (paused on 2026-10-31; resume first) |

- **Release order:** the database tests pass in CI; `006_got_this.sql` runs on
  staging (`vdiheceziirgvbudxdxg`), the e2e tests pass against it, then it
  runs on production (`fpzjyusgqdzqituokrwq`) with explicit confirmation;
  then the app is released. Old app versions keep working: they never send
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
- **Undo on another phone:** the second phone has the pulled purchase, with
  the same tick id, the list id and the snapshot, and applies the same 2-hour
  rule against `pickedUpAt`.
- **Clock skew:** tick times use the phone's clock. A phone that is days off
  records the wrong day and may credit or skip a member who joined near that
  time. Events more than a day in the future are rejected and stay in the
  outbox until the clock is fixed or they age past 30 days.
- **An outbox event older than 30 days:** rejected by the server; the phone
  drops it, and its purchase stays on that phone only.
- **Item or list deleted after the tick:** the purchase stays (no foreign
  key). If the item or list reached the server, it is still there as a
  tombstone when its tick is sent, so the event validates. If the list never
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
| An event older than 30 days, or a day in the future | rejected |
| An event for an item not yet on the server | returned for retry |
| An old-app push (no `picked_up_at`) that ticks | `picked_up_at` set to now; one legacy tick; purchases created |
| An old-app push that edits or unticks | nothing changes in history |
| A new-app push with `picked_up_at` null or older | `picked_up_at` unchanged |
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
  later ones, keeps every outbox event, and clears mutes, impressions and the
  follow-up; an older or equal reset does nothing.
- Clear with ticks unsent: local purchases go, outbox events stay, and the
  next push sends the clear before the events.
- A list created, ticked and deleted offline: the push turns its ticks into
  `pendingUpload` purchases in the same transaction that drops the list, and
  sends them with `upload_own_purchases`.
- `hasUnsyncedChanges` is true with only an outbox event, a pending upload or
  a pending clear.
- List screen lookup: an unticked "bannana" finds purchases stored under
  "banana".
- Key version change recomputes keys and clears mutes, impressions and the
  follow-up.
- `RunningLowWorker`: posts at most once per ISO week, only after the claim
  returns true; silent offline, with nothing due, or without permission;
  names up to 3 items.
- Migration 5 → 6 in `MigrationTest`, as for 4 → 5.

### End-to-end (`app/src/androidTest`, against staging)

1. Guest: tick "Milk" on one list, add "milk" to another, see "Bought today",
   remove it from the hint menu.
2. Two signed-in members on a shared list (phone and emulator, see the e2e
   setup): A ticks "Eggs", B adds "eggs" to B's own list and sees the hint
   after sync.
3. Two phones on one account with a due regular: only one gets the weekly
   notification.
4. Seeded history with a weekly regular: the suggestions row shows it, a chip
   adds it, and the row no longer offers it.
5. A finished list: adding a chip moves it back to the active lists and
   resets its progress card.

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
- A list member who fabricates event times can undo another member's tick
  from the last 30 days (D18).
- A phone offline for more than 30 days keeps those ticks to itself.
- Running-low says nothing until an item has 3 shops over 14 days; for people
  with no ticked items on their phone at release, that is at least 2 weeks.
- Mutes and "kept" counts are per phone.
- A signed-in phone that is offline at 18:00 on the eve of shopping day sends
  no notification that week unless a later daily check gets through.
