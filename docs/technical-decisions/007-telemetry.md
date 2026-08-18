# 007 — Telemetry: analytics and crash reporting

## Context

The app was approaching release with no visibility into either half of the picture: no
sight of which features people actually use, and no sight of how the app fails in the
field. Every product decision after launch — is voice input the differentiator we think
it is, does list sharing drive retention, is the 48-category taxonomy from 006 any good
— was a guess, and every crash a real user hit was invisible.

The constraint that shaped the vendor choice: FCM is the only viable push transport on
Play-services devices, so the Firebase SDK is entering this project regardless. Analytics
and Crashlytics therefore cost nothing extra to adopt.

The constraint that shaped everything else: Firebase Analytics is a weak product-analytics
tool. No real funnel exploration without BigQuery, reporting latency up to 24 hours, and
custom parameters are invisible until someone registers them by hand in the console. We
expect to outgrow it, and to move crash reporting to Sentry at the same kind of moment.

## Decision

Adopt Firebase Analytics and Crashlytics, behind an abstraction that makes leaving them
cost one class each.

### Two projects, not one

| Project | Package names | Collection |
| --- | --- | --- |
| `babe-get-this-stg` | `com.babegetthis.android.dev`, `.staging` | **on** |
| `babe-get-this` | `com.babegetthis.android` | on |

Per-flavor `google-services.json` under `app/src/<flavor>/`, so no build can resolve
against the wrong project.

The alternative — one project holding all three package names, with
`firebase_analytics_collection_enabled=false` in dev and staging manifests — was tried
first and rejected. Disabling collection in non-production builds means production is the
only environment where reporting is ever exercised, so the first evidence a pipeline is
broken is missing production data. Keeping collection **on** in a separate staging project
gives a live target for test crashes and DebugView and isolates production metrics just as
effectively.

### The abstraction

```
core/telemetry/
├── AnalyticsRepository.kt      track / setUser / setCollectionEnabled
├── CrashReporter.kt            breadcrumb / setKey / recordNonFatal / setUser / setCollectionEnabled
├── TelemetryConsent.kt         the user's opt-out
├── TelemetryContext.kt         identity, screen, custom keys, the safeCall hook
├── TelemetryMarkers.kt         once-only event bookkeeping
├── model/                      AnalyticsEvent (the catalog), Buckets, Screen
├── data/                       Firebase implementations + mapper + policy
└── di/TelemetryModule.kt       two @Binds — the entire vendor commitment
```

`com.google.firebase` appears in exactly two files, both under `data/`. Swapping analytics
to PostHog or crash reporting to Sentry is one new implementation class and one changed
`@Binds`; nothing above `core/telemetry` knows Firebase exists.

Two interfaces rather than one facade, because they will move to different vendors at
different times, and a combined facade would need splitting exactly when that is most
expensive.

### Events are a sealed hierarchy

`AnalyticsEvent` is a sealed interface with one subtype per event and typed constructor
parameters. `AnalyticsEventMapper` translates a subtype to a GA4 name and parameter set in
one exhaustive `when`.

This is more machinery than `track(name: String, params: Map<String, Any>)`, and it earns
it three ways. The compiler rejects typos and wrong-typed parameters, which a string API
discovers weeks later as a missing dimension. The `when` is exhaustive, so adding an event
without mapping it fails the build. And the hierarchy *is* the catalog — one file answers
"what does this app track?" with no wiki to drift from.

`MappedEvent` is plain Kotlin rather than an `android.os.Bundle`, so the mapper — the only
piece with real logic — unit-tests on the JVM with no Robolectric, and a future PostHog
implementation reuses it unchanged.

### The non-fatal whitelist

Only `AppError.UnknownError` and `AppError.DatabaseError` are reported. Everything else is
dropped: `NetworkError`, `TimeoutError`, `ValidationError`, `AuthError`,
`UnauthorizedError`, `NotFoundError`, `ServerError`.

This is the most important decision in the change, and it works by sending **less**. This
app is offline-first: a user on a train generates network failures continuously, and at any
real install base those alone would outnumber genuine defects by orders of magnitude. A
Crashlytics dashboard that is 99% "no internet connection" is one nobody opens, which
catches nothing at all.

`UnknownError` is the best signal in the set — it means `safeCall` met an exception it had
no mapping for, which is close to a definition of "we did not anticipate this".
`ServerError` is excluded because 5xx is already visible in the backend's own monitoring,
and duplicating it means one outage arriving as thousands of client reports.

`ErrorReportingPolicy` has no `else` branch, so adding an `AppError` subtype forces a
decision rather than defaulting into silence. The policy is applied inside
`CrashReporter.recordNonFatal`, so no call site can skip it.

### Hooking `safeCall`

Every repository failure already funnels through `safeCall` in `core/error/Result.kt`,
which holds both the original `Throwable` and the mapped `AppError`. That is where non-fatal
reporting belongs — reporting from ViewModels would mean a call site per screen and would
still miss any repository whose `Result` is consumed elsewhere.

`safeCall` is a top-level function, so it cannot take an injected dependency without adding
a parameter to most of the data layer. `ErrorReportingHook` is a `@Volatile` property on an
object, assigned once by `TelemetryContext.onAppStart()`. This is a deliberate service
locator; it is marked `ponytail:` in the source with its upgrade path (if `safeCall` ever
becomes a class, the dependency becomes ordinary Hilt wiring). Null by default, so tests
report nothing without setup.

The invocation is wrapped in `runCatching`. Telemetry must never turn a handled error into
an unhandled one.

### Privacy is structural

The sealed model offers no untyped map, so a free-text parameter cannot be added without
editing the catalog and the mapper — which is where review sees it. Permitted values are
bounded enums, bucketed numbers, booleans, and identifiers from our own fixed taxonomy.

Never transmitted: item names, user-entered category names, transcription text, email
addresses, display names, invite codes.

Three specific defences worth naming, because each was a real hole:

- **Screens go through a `Screen` enum, never a raw route.** `Routes.SHOPPING_ITEMS` is
  `"shopping_items/{listId}/{listName}"`. `NavDestination.route` returns the pattern today,
  so it happens to be safe — but that is an implementation detail of Navigation, not a
  guarantee. One day someone reads `arguments` instead and a user's list name lands in GA4
  forever.
- **Category ids are validated against `DEFAULT_CATEGORIES`.** Users can create their own
  categories, with generated ids and user-authored names. Anything outside the shipped
  taxonomy collapses to `custom`, which still answers the useful question: how often does
  our taxonomy have no home for something?
- **Failure reasons are mapped from the `AppError` type, never its message.**
  `ServerError` and `UnknownError` can carry text that came off the wire.

Identity is the Supabase user UUID and nothing else, set and cleared from the
`sessionStatus` collector already running in `BabeGetThisApp` — so analytics identity and
crash identity are set from one place on one signal and cannot drift apart.

### Consent

Two independent switches in Settings: usage analytics, and crash reports. Both default on
(opt-out).

They are separate because crash reporting has a stronger legitimate-interest argument than
product analytics does. When EU consent forces analytics to opt-in, that becomes a default
change in `TelemetryConsent` rather than a UI rewrite.

`setCollectionEnabled` lives on the two vendor interfaces rather than in a wrapper, because
only the vendor client can actually stop collecting — suppressing `track()` calls above the
SDK would leave it gathering sessions and installation ids anyway.

Both SDKs persist their own collection flag, so `TelemetryConsent` does not reimplement
storage. Its `SharedPreferences` exist only because Analytics exposes no getter, leaving the
switch unable to render its own state; they are display state, not truth. `applyPersisted()`
re-asserts the stored choice at startup — strictly redundant today, but "the vendor
remembers my users' opt-out for me" is not an assumption worth making load-bearing on a
privacy control.

## What the instrumentation is for

Each event exists to answer a stated question. Events that answer nothing are not added.

| Area | Question |
| --- | --- |
| Voice funnel | Is voice actually used, and where does it lose people? |
| Sharing loop | What fraction of users share, and does sharing change retention? |
| Activation | Does an install become a user, and how fast? |
| Habit & taxonomy | Is this a weekly habit, and does the 006 taxonomy fit how people shop? |

The taxonomy pair (`category_auto_assigned` / `category_corrected`) looks like the least
marketing-relevant events in the catalog and is the most decision-relevant: 006 commits to
a fixed category set, and the per-category correction rate is the only evidence we will
ever have about whether that set is right.

## Consequences

**Firebase BOM is pinned to 33.16.0, not the current 34.x.** From BOM 34.0.0 the analytics
module pulls `play-services-measurement` 23.x, whose `.kotlin_module` metadata is Kotlin
2.2.0; this project compiles with Kotlin 2.0.21, itself pinned by supabase 3.0.x. Moving to
34.x means a toolchain-wide Kotlin upgrade — the same deferred task the Supabase pin is
already waiting on. The two are now one job rather than two.

**Google Play Data Safety must be updated before the next release.** This is a store-listing
requirement, not cleanup. See below.

**GA4 custom parameters are invisible until registered.** Every parameter is collected but
unqueryable until someone clicks it into existence in the console. See below.

**Crashlytics deobfuscation will break the day R8 is enabled.** `isMinifyEnabled = false`
today, so stack traces arrive readable and no mapping upload is needed. When minification is
turned on, `firebaseCrashlytics { mappingFileUploadEnabled = true }` must be set on the
release build type or every production stack trace becomes unreadable.

**Two projects double the administrative surface** — two sets of console settings, two GA4
property links, and later two FCM service-account keys. Accepted for the isolation, and it
is the arrangement the eventual push work wants anyway.

**The debug logcat echo fires even when a user has opted out.** `track()` calls `logEvent`
unconditionally and the SDK discards it; nothing is transmitted. Debug builds only, but
during local verification the echo can read as "events flowing" when they are not.

**Instrumentation drifts as features change.** Events get added with features and rarely
removed with them. The sealed catalog makes the drift visible in one file, which is the
mitigation available; nothing enforces that an event still means what it meant.

## Open questions

- **Does EU launch require opt-in rather than opt-out for analytics?** Opt-out is common
  practice but is not consent under GDPR for non-essential analytics. Crash reporting can
  likely stay opt-out; product analytics is the exposed half. Answering before release is
  cheaper than retrofitting a consent gate after.
- **Should the staging project also be the FCM test target?** Assumed yes, but the push
  change owns that decision.
- **Is a shared-list count worth a user property?** It would make "shared vs solo users" a
  segment rather than a query, but user properties are capped at 25 and spending one this
  early may be premature.

## Appendix A — GA4 custom dimensions to register

Both properties, `Admin → Custom definitions`. Until each exists, the parameter is collected
and unqueryable.

`screen_name` is deliberately absent: Firebase treats it as a RESERVED parameter
(logcat shows it rewritten to `ga_screen(_sn)`), so it already populates the built-in
"Screen name" dimension. GA4 rejects reserved names as custom definitions.

Registration is NOT retroactive — a dimension only reports on data collected after it
exists. Register before you care about the data, not when you go looking for it.

**Custom dimensions** (scope: Event) — 9

| Parameter | Used by |
| --- | --- |
| `input_method` | `item_added`, `first_item_added` |
| `category_source` | `item_added` |
| `category` | `category_auto_assigned` |
| `from_category` | `category_corrected` |
| `to_category` | `category_corrected` |
| `reason` | `voice_transcription_failed`, `share_join_failed` |
| `item_count_bucket` | `voice_transcription_completed`, `voice_items_saved`, `first_list_completed`, `list_completed` |
| `latency_bucket` | `voice_transcription_completed`, `voice_transcription_failed` |
| `duration_bucket` | `voice_recording_cancelled` |

**Custom metrics** (scope: Event, unit: Standard)

| Parameter | Used by |
| --- | --- |
| `item_count` | `voice_transcription_completed`, `voice_items_saved`, `list_completed` |

Counts are sent both bucketed and raw on purpose: GA4 builds dimensions from strings and
metrics from numbers, and neither derives from the other. Latency is bucket-only — raw
milliseconds are effectively unique per event, which makes them a fingerprint rather than a
dimension.

## Appendix B — Play Data Safety: the full declaration

**Scope.** This form covers everything the app transmits to any server, not just Firebase.
Telemetry adds three rows; the rest were already true before this change and must be
declared alongside them. Filling in only the telemetry rows would be an inaccurate
submission.

### Step 2 — Data collection and security

| Question | Answer | Why |
| --- | --- | --- |
| Collects or shares required user data types? | **Yes** | |
| All data encrypted in transit? | **Yes** | HTTPS to Supabase, Railway, Firebase |
| Way to request data deletion? | **Yes** → *account deletion* | `deleteAccount()` → `delete_user` RPC |
| Delete *some* data without deleting the account? (optional) | **No** | no such path exists; per-item delete is content management, not a request mechanism |

### Step 3 — Data types

Play's purpose list has no "Diagnostics" purpose — Diagnostics is a data TYPE. Crash logs
and Diagnostics both take purpose **Analytics**, whose own definition covers monitoring
app health and diagnosing crashes.

Every telemetry row: Collected, NOT Shared, NOT processed ephemerally (Firebase stores it),
and "Users can choose" (the Settings toggles).

User IDs is optional rather than required because the app has no login wall — it works
fully signed out, so having an account at all is the user's choice.

Nothing here is **Shared**. Supabase, Railway and Firebase are service providers
processing on our behalf, which Play counts as *collected*, not *shared*.

| Category → Type | Collected | Optional? | Purpose | Source |
| --- | --- | --- | --- | --- |
| Personal info → Name | Yes | Required | App functionality, Account management | Supabase register |
| Personal info → Email address | Yes | Required | App functionality, Account management | Supabase auth |
| Personal info → User IDs | Yes | **Optional** | App functionality, Account management, Analytics | Supabase UUID, also sent to Firebase as the analytics/crash id |
| App activity → **Other user-generated content** | Yes | **Optional** | App functionality | list and item names, uploaded to Supabase **only once a list is shared** |
| Audio → Voice or sound recordings | Yes | **Optional** | App functionality | multipart upload to our `/transcribe` backend |
| App activity → **App interactions** | Yes | **Optional** | **Analytics** | telemetry — Settings toggle |
| Device or other IDs | Yes | **Optional** | **Analytics** | Firebase app instance id |
| App info & performance → **Crash logs** | Yes | **Optional** | **Analytics** | telemetry — Settings toggle |
| App info & performance → **Diagnostics** | Yes | **Optional** | **Analytics** | telemetry — Settings toggle |

The three bold telemetry rows are what this change adds. The rest predate it.

### Open question before submitting

**Is the uploaded audio retained by the transcribe backend?** If it is transcribed and
discarded, Audio can be marked *processed ephemerally*, which is a materially lighter
declaration. If Railway keeps the file, it is not ephemeral. This is a question about the
server, not the app, and cannot be answered from this repository.

### Elsewhere in App content, not part of Data safety

- **Data deletion** — a separate item. Needs a public web URL where deletion can be
  requested *without* installing the app. The in-app path alone does not satisfy it.
- **Privacy policy** — must be publicly reachable and must actually mention analytics and
  crash reporting; reviewers cross-check it against this form.
