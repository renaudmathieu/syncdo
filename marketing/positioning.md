# Positioning: SyncDO

> Written and updated by the `app-positioning` skill, except the "What the code says" section (owned by `claims-proof-audit`). Keep the headings: they are the anchors of later updates. Hand edits are welcome and kept.

Status: to validate | Market: international, English-speaking Kotlin / KMP developers | Last update: 2026-10-15

## Verdict

**Sync you own:** SyncDO is offline sync for Kotlin Multiplatform that lives in your own Ktor server, under Apache 2.0, small enough to read in an afternoon. No vendor can switch it off or reprice it. This holds only if the "own it" story survives a real deployment. Before any public promotion: publish to Maven Central, add an auth hook and TLS (`wss`) to `:sync`, and fix the test-ratio figure in the talk. Until then, never say "production-ready".

## What the code says

Not audited yet.

## Context

| Item | Value |
|---|---|
| Target market and language | International, English. Kotlin and KMP developers (Android first, then iOS via KMP, then JVM backend). |
| Status | Pre-release: `0.1.0-SNAPSHOT`, not on Maven Central, public GitHub repo (25 stars on 2026-10-15) |
| Goal | GitHub stars and release watchers ahead of 0.1 on Maven Central. These are early adopters, not installs. |
| Business model | Pure OSS, Apache 2.0, no revenue, no hosted offer planned. Independence claims are fully usable. |
| Product positioned | The `:crdt` and `:sync` libraries. The Todo app is only a demo and is not the product. |
| Existing copy read | Root `README.md`, `crdt/README.md`, `sync/README.md`, `docs/crdt-vs-lww.md`, `PRESENTATION.md` (45 min talk, French), GitHub repo description (empty). There is no landing page and no social bio. |

## 1. Diagnosis of the current copy

- **Substitution test:** The README lead fails as a promise. "Kotlin Multiplatform libraries for CRDT-based sync" says what the libraries are, not why anyone should care. The `:crdt` line "building blocks of conflict-free, offline-first collaborative applications" could sit unchanged on Ditto's site (KMP SDK, CRDTs) or PowerSync's ("offline-first"). Generic.
- **Buried differentiator:** Three of them, none in the first sentence:
  1. You self-host: the server is a single Ktor route (`Route.syncEndpoint`). Today this appears only in the `:sync` README table.
  2. Apache 2.0 on both client and server. It is line 3 of the README, stated as a licence fact rather than a benefit.
  3. Size: about 730 lines of production code across the two libraries (333 for `:crdt`, 396 for `:sync`), with dependencies limited to kotlinx and Ktor. This appears only in the French talk.
- **Misleading elements:**
  - `crdt/README.md` installs from `mavenCentral()` ("or mavenLocal while snapshotting"), but the artifact is not on Maven Central.
  - The talk's "Les chiffres" slide says tests are about twice the production code on the libraries. Library tests today: 455 + 207 = 662 lines against 729 lines of production code, a ratio of about 0.9. The 952 figure includes the sample tests.
  - The README demo scenario says a change "appears on B within ~1s". The figure is unmeasured. Keep it out of marketing until it is timed.
  - The GitHub repo has no description and no topics, so it is invisible in topic search (`kotlin-multiplatform`, `crdt`, `offline-first`, `local-first`).

## 2. Market map

| Player | Territory occupied | Business model | Exploitable weakness |
|---|---|---|---|
| [Realm / Atlas Device Sync](https://www.mongodb.com/docs/atlas/app-services/sync/device-sync-deprecation) | Was the default mobile DB + sync for Kotlin | MongoDB Atlas, paid | Device Sync was removed on 2025-09-30. Users were sent to third parties. This is the live example of a vendor switching sync off. |
| [PowerSync](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform) | Syncs your existing Postgres / MongoDB / MySQL to SQLite on the device. KMP SDK covers Android, iOS, JVM. | [Cloud](https://www.powersync.com/pricing): Free, Pro from $49/mo, Team from $599/mo. The self-hosted service is [FSL-1.1-ALv2](https://github.com/powersync-ja/powersync-service) (source-available). | Needs a backend DB and their service. Conflicts are [per-field LWW by default](https://docs.powersync.com/usage/lifecycle-maintenance/handling-update-conflicts), with no built-in CRDTs. The server is not OSI open source. |
| [Ditto](https://docs.ditto.live/) | Edge and P2P mesh sync (BLE, LAN), CRDT-based. Kotlin SDK 5.x is [rebuilt on KMP](https://docs.ditto.live/sdk/v5/release-notes/kotlin) (5.0 on 2026-05-05). | [Free tier, then quote](https://www.ditto.com/pricing). Assumed proprietary (unverified). | Closed and quote-based, with portal credentials. Its CRDT internals cannot be inspected. This is the funded KMP-native competitor that also says "CRDT". |
| [Couchbase Lite via Kotbase](https://kotbase.dev/current/) | Embedded JSON document DB synced through Sync Gateway / Capella | Kotbase is Apache 2.0 with [one maintainer](https://github.com/jeffdgr8/kotbase). The server side is commercial or community edition. | Heavy stack. Conflicts are [document-level](https://docs.couchbase.com/couchbase-lite/current/android/conflict.html), with no field-level merge. The KMP wrapper has a bus factor of 1. |
| [Firebase Firestore via GitLive](https://github.com/GitLiveApp/firebase-kotlin-sdk) | Google-scale managed backend, [offline cache](https://firebase.google.com/docs/firestore/manage-data/enable-offline) | Pay as you go. The KMP SDK is run by volunteers and covers about 23% of the API. | Not self-hostable. Last write wins per document. The cache is not a local source of truth. |
| [ElectricSQL](https://electric.ax/) | Real-time Postgres sync, web and TS first | Apache 2.0. Electric Cloud is winding down after [joining Databricks](https://electric.ax/blog/2026/08/11/electric-joining-databricks) (2026-08-11). | Postgres only. No Kotlin client. No merge semantics. |
| [automerge-java](https://github.com/automerge/automerge-java) | A real JSON CRDT (Rust core) on JVM and Android | OSS | v0.0.9. No iOS / Kotlin-Native target. No sync server for Kotlin. Ships a JNI binary. |
| [ykt (y-crdt Kotlin)](https://github.com/y-crdt/ykt) | Kotlin bindings for Yjs / yrs | MIT | The repo says it is "currently not active". No releases. |

Also seen in Kotlin Slack search results, not verified: SyncForge (pre-1.0, KMP sync with LWW and merge strategies) and Buoyient syncable-objects (0.1.x, March 2026). Check both before the first post that uses "the only".

**Off-limits ground:** Do not compete on "managed, scalable sync for your existing Postgres / MongoDB with dashboards and SLAs" (PowerSync, Firebase) or on "enterprise P2P mesh / edge" (Ditto). Funded vendors hold those positions. "CRDT-powered" alone is not a differentiator either, because Ditto says it too.

## 3. User pains

| Signal | Figure (sample size) | Source |
|---|---|---|
| KMP share among cross-platform developers | 7% (2024) to 18% (2025). No sample size given for this chart. | [JetBrains, State of Kotlin 2026](https://kotlinlang.org/assets/state-of-kotlin-2026.pdf), p.21, 2026-09 |
| Kotlin developers working on multiplatform projects | 31% | Same report, p.14 |
| Kotlin Developer Survey sample | 1,532 respondents (2025) | Same report, p.11 |
| KMP in top 10,000 App Store apps | Under 1% (early 2024) to about 3% (mid-2026), Appfigures data quoted by JetBrains | Same report, p.21 |
| Kotlin usage, all developers | 10.8% (31,771 answers to the language question) | [Stack Overflow Survey 2025](https://survey.stackoverflow.co/2025/technology), 2025-07 |
| Realm Device Sync shutdown | Removed on 2025-09-30 | [MongoDB deprecation page](https://www.mongodb.com/docs/atlas/app-services/sync/device-sync-deprecation), 2026-10-15 |
| Visible reaction to the Realm shutdown | Tens of comments (realm-js discussion #6884, two HN stories with 14 and 3 comments) | [GitHub](https://github.com/realm/realm-js/discussions/6884), [HN](https://news.ycombinator.com/item?id=41489636) |
| Share of developers building offline support | No figure found | |

Recurring complaints, in the users' words:

- "This is a big hit to all of us who have built applications on top of sync." ([realm-js #6884](https://github.com/realm/realm-js/discussions/6884), 2024-09)
- "It is extremely frustrating that Mongo left us all hanging like this." ([realm-js #6884](https://github.com/realm/realm-js/discussions/6884), 2024-10)
- "I already had to migrate from Realm to MongoDB Realm and now again to another solution." ([HN](https://news.ycombinator.com/item?id=41490493), 2024-09)
- "We were weeks away from announcing a new product based on Atlas Flexible Sync. Now the project's fate is hanging in the balance." ([HN](https://news.ycombinator.com/item?id=41501356), 2024-09)
- "But I fear the same fate as Realm, since it doesn't have an open source sync component" ([HN](https://news.ycombinator.com/item?id=41525804), 2024-09, about ObjectBox)
- "You can implement local-first with the merging strategy of last-write-wins [...] With the caveat that changes may be lost" ([HN](https://news.ycombinator.com/item?id=40811339), 2024-06)
- "It did teach me that convergence is only a small part of sync." ([HN](https://news.ycombinator.com/item?id=49142812), 2026-08)
- "I hate signing up for your sync just as much as any other SAAS, but it's even more opaque and likely to break." ([HN](https://news.ycombinator.com/item?id=44476235), 2025-07)

**Thin data, flagged:**

- There are no Reddit, Kotlin Slack or MongoDB forum quotes. The sources were blocked or private.
- The strongest quotes date from 2024, when Realm was shut down. The vendor-risk pain is real but small in volume: tens of comments, not thousands.
- No verified quote supports the pains "CRDTs are JS/Rust only", "per-device pricing" or "black-box sync". Do not write hooks that claim developers say these things.
- KMP growth figures come from JetBrains, who have a stake in them.
- Your own Droidcon talk on the topic shows up in search. It is an asset for content, not independent evidence.

## 4. Angles and choice

| Angle | Demand | Defensibility | Social fit | Provability (first estimate) | Total |
|---|---|---|---|---|---|
| A. **Merges that keep both edits**: field-level and add-wins CRDT merges instead of one row overwriting the other | 2/3: well-understood pain, but most apps live with LWW | 1/3: Ditto says "CRDT", and PowerSync already does per-field LWW | 3/3: a two-phone demo of a lost edit is very visual | 3/3: demo scenarios 2 to 5 exist in the sample | 9 |
| B. **Sync you own**: an Apache 2.0 Ktor route in your own server, no vendor, no key, cannot be switched off | 2/3: the Realm shutdown proves the fear, but its volume is modest and dated | 3/3: vendors cannot say it, because their revenue is the hosted service or the licence (PowerSync is FSL, Ditto is quote-based, Firebase cannot be self-hosted) | 2/3: carousels and threads work, but the angle is less visual than A | 2/3: licence and self-hosted route exist. "Deployable" needs auth, TLS and Maven Central first. | 9 |
| C. **Small enough to read**: about 730 lines of Kotlin, no magic, you can debug your sync | 1/3: no verified quote supports it | 3/3: platforms cannot be small | 3/3: code on screen and "the whole thing fits in N slides" perform well with developers | 3/3: line counts are measurable today | 10 |

**Chosen angle:** B, "Sync you own", with C as its main proof and A as its demo. C scores higher but has no demand evidence: on its own it reads as a learning project. B carries the only pain backed by real quotes (vendor risk after the Realm shutdown), and it is the ground funded players structurally cannot take. C makes B believable ("you own it because you can read all of it"). A provides the visuals (the two-phone demo) without claiming "CRDT" as the differentiator. Status: **proposal, to be validated by the user.**

## 5. Messaging house

- **Promise:** Offline sync for Kotlin Multiplatform that runs on your own server, and that no vendor can switch off.
- **The sentence a third party repeats:** "SyncDO is a tiny Apache-2.0 sync library for KMP: you add one Ktor route to your server and your app syncs offline-first, with no vendor in between."

| Pillar (claim) | Required proof | Status |
|---|---|---|
| 1. You own it: Apache 2.0 client and server, the server is one Ktor route in your own backend, no account, no API key, no cloud | `LICENSE`, `Route.syncEndpoint`, the `:server` sample. For "deployable" also: an auth hook on `syncEndpoint`, `wss` / TLS support, published Maven Central artifacts. | Licence and route exist. Auth, TLS and Maven Central are to build. |
| 2. Edits merge instead of overwrite: per-field LWW, add-wins sets, delta catch-up after offline, convergence covered by tests | Tests for commutativity, associativity and idempotence of each type. A two-device recording of scenarios 2 to 5. Measured catch-up after offline. | Unit tests exist (coverage per type to confirm). The recording is to build. |
| 3. Small enough to read: about 730 lines of production code, kotlinx and Ktor dependencies only, binary-compat guard | Line count script. Dependency list. `apiCheck` in the build. | Exists (re-measure before every post). |

- **Tone:** An engineer explaining a design choice to a peer, showing code and numbers rather than adjectives. First person only for what Renaud actually built or tested. Calm about competitors: name facts (the Realm shutdown date, licence names), never mock.
- **Banned words:** seamless, magic, effortless, enterprise-grade, production-ready (until pillar 1 is complete), at scale, blazing fast, real-time (unless measured), "the Realm replacement" (implies feature parity), "the only KMP CRDT library" (unverified), "conflict-free" as the headline (category jargon, also claimed by Ditto), local-first platform.

Landing page rewrite (GitHub README and repo description):

- H1: Offline sync for Kotlin Multiplatform, on your own server.
- Subtitle: Two small Apache-2.0 libraries: CRDTs that merge edits instead of overwriting them, and a WebSocket engine with a Ktor route you host. No account, no API key, about 730 lines you can read.
- Single CTA: "Star the repo to follow the 0.1 release" (switch to the Maven Central dependency line once published).
- GitHub description: "Offline-first sync for Kotlin Multiplatform you self-host: CRDTs + a Ktor route. Apache 2.0." Topics: `kotlin-multiplatform`, `crdt`, `offline-first`, `local-first`, `ktor`, `sync`.
- Remove: the `mavenCentral()` install instruction until it is true, the "~1s" figure, the tests-to-code ratio claim in the talk, "conflict-free" in the first sentence.

## 6. Hook bank

Flags: `[test]` requires a real test before publishing, `[unproven]` depends on a pillar not yet proven.

### Family 1: Vendor risk (the Realm lesson)
1. MongoDB switched off Atlas Device Sync on September 30, 2025. Who can switch off yours?
2. Your sync layer is a dependency. Can you read its source?
3. "Free tier" is a pricing decision someone else can change.
4. The sync server for this KMP app is one Ktor route. Here it is.
5. What I wanted after Realm Sync shut down: sync I could fork. `[unproven]` (true only if Renaud was a Realm user, otherwise rephrase without "I")
6. Self-hosted sync for Kotlin Multiplatform, without the licence fine print. `[unproven]` (needs pillar 1 complete for "self-hosted in production")

### Family 2: The lost edit (demo)
7. Two phones, both offline, both edit the same todo. Who wins?
8. Last-write-wins on the whole row just deleted your colleague's change.
9. She renamed it. He checked it off. With row-level LWW, one of them loses.
10. Delete vs edit at the same time: which one should win?
11. I went offline for an hour, made 40 edits, reconnected. Here is what synced. `[test]`
12. I killed the server mid-sync. Nothing was lost. `[test]`

### Family 3: Small enough to read
13. Offline sync for Android, iOS and JVM in about 730 lines of Kotlin.
14. The whole sync protocol is three messages. Here they are.
15. A vector clock in 56 lines of Kotlin.
16. You do not need a platform to sync a todo list.
17. Every line of the sync engine fits in this carousel. `[test]` (check it really fits)
18. No Rust core, no JNI, no binary: pure commonMain Kotlin.

### Family 4: CRDT myths
19. CRDTs are not magic. They are a merge function with three properties.
20. "Just use last-write-wins" is a CRDT too. The question is at what granularity.
21. Why add-wins is the right default for a todo list.
22. Composing CRDTs gives you a CRDT. That is the whole trick.
23. Deltas vs full state: what actually goes over the wire. `[test]` (capture real payloads)
24. Convergence is the easy part of sync. Here is the hard part.

### Family 5: Building it in KMP
25. One merge function, running on Android, iOS and the JVM server.
26. How I test that a CRDT converges, on the iOS simulator too.
27. `expect`/`actual` for file IO: the only platform code in the sample.
28. Binary-compat checks on a 0.1 library: why `apiCheck` is on from day one.
29. From the Droidcon talk to a library: what changed. `[unproven]` (only once 0.1 is published)
30. Add offline sync to your KMP app in 20 minutes. `[test]` `[unproven]` (time it from a fresh project, needs Maven Central)

## 7. Slideshow templates

Channels: LinkedIn carousels and X / Bluesky image threads first (where Kotlin developers are), Instagram second, TikTok only if the first 10 posts show traction there.

| Slide | Template A: "Who can switch it off?" | Template B: "The lost edit" | Template C: "The whole thing in 6 slides" |
|---|---|---|---|
| 1 | Hook (family 1) | Hook (family 2) | Hook (family 3) |
| 2 | Fact: "Atlas Device Sync removed, 2025-09-30" | Two phones, both offline, same todo | Protocol: 3 message types |
| 3 | Question: "What does your sync depend on?" | Row-level LWW: one edit disappears | Client: `SyncEngine` in a few lines |
| 4 | The answer: one Ktor route, code shown | Field-level merge: both edits survive | Server: `syncEndpoint` in your Ktor app |
| 5 | Apache 2.0, no account, no key | Edit vs delete: add wins | Line count: about 730, tests included separately |
| 6 | Real screenshot: the sample on two devices, synced (`app-marketing-screenshots`) | Real screenshot: both devices after merge | Real screenshot: the sample app running on Android + iOS + desktop |

Production rules: 12 words maximum per slide (code snippets count as an image, keep them under 8 lines), one font plus one monospace font for code, brand color as accent, real or licensed photos only, no realistic AI-generated places or people, sound added by hand, no account farms. Posts go out under Renaud's own name. No fake testimonials or "a user told us" quotes.

## 8. KPI

- **KPI:** new GitHub stars per 1,000 views. Once 0.1 is on Maven Central, switch to release watchers or downloads per 1,000 views.
- **Diagnostic indicators:** saves, link clicks, GitHub traffic referrers (Insights, Traffic).
- **Weekly reading grid:** many views and few stars means fix slide 6, the caption or the README top section. Few views and good conversion means fix the hooks.
- **Target:** none before a baseline is measured over the first 10 posts.

## Decisions

| Date | Decision | By |
|---|---|---|
| 2026-10-15 | Position the `:crdt` and `:sync` libraries. The Todo app is a demo only. | Renaud Mathieu |
| 2026-10-15 | Market: international, English. Status: pre-release. Goal: stars and early adopters before Maven Central. | Renaud Mathieu |
| 2026-10-15 | Business model: pure OSS, Apache 2.0, no revenue | Renaud Mathieu |
| 2026-10-15 | Angle B "Sync you own" proposed, with C and A as proof points. To validate. | Proposed by app-positioning |

## Sources

- [MongoDB, Device Sync deprecation](https://www.mongodb.com/docs/atlas/app-services/sync/device-sync-deprecation), consulted 2026-10-15
- [PowerSync KMP SDK](https://docs.powersync.com/client-sdk-references/kotlin-multiplatform), [pricing](https://www.powersync.com/pricing), [conflict handling](https://docs.powersync.com/usage/lifecycle-maintenance/handling-update-conflicts), [service licence](https://github.com/powersync-ja/powersync-service), consulted 2026-10-15
- [Ditto docs](https://docs.ditto.live/), [pricing](https://www.ditto.com/pricing), [Kotlin v5 release notes](https://docs.ditto.live/sdk/v5/release-notes/kotlin), [Java Server SDK preview](https://www.ditto.com/blog/announcing-java-server-sdk-public-preview), consulted 2026-10-15
- [Kotbase](https://kotbase.dev/current/), [Kotbase repo](https://github.com/jeffdgr8/kotbase), [Couchbase Lite conflicts](https://docs.couchbase.com/couchbase-lite/current/android/conflict.html), consulted 2026-10-15
- [GitLive Firebase Kotlin SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk), [Firestore offline](https://firebase.google.com/docs/firestore/manage-data/enable-offline), consulted 2026-10-15
- [ElectricSQL](https://electric.ax/), [Electric joining Databricks](https://electric.ax/blog/2026/08/11/electric-joining-databricks), consulted 2026-10-15
- [automerge-java](https://github.com/automerge/automerge-java), [ykt](https://github.com/y-crdt/ykt), consulted 2026-10-15
- [JetBrains, State of Kotlin 2026](https://kotlinlang.org/assets/state-of-kotlin-2026.pdf), consulted 2026-10-15
- [Stack Overflow Developer Survey 2025](https://survey.stackoverflow.co/2025/technology), consulted 2026-10-15
- [realm-js discussion #6884](https://github.com/realm/realm-js/discussions/6884), consulted 2026-10-15
- Hacker News threads [41489636](https://news.ycombinator.com/item?id=41489636), [41490493](https://news.ycombinator.com/item?id=41490493), [41501356](https://news.ycombinator.com/item?id=41501356), [41525804](https://news.ycombinator.com/item?id=41525804), [40811339](https://news.ycombinator.com/item?id=40811339), [49142812](https://news.ycombinator.com/item?id=49142812), [44476235](https://news.ycombinator.com/item?id=44476235), consulted 2026-10-15
- Repo facts (line counts, dependencies, no auth or TLS option found in `:sync`): measured in the working tree on 2026-10-15

## History

| Date | Skill | Change |
|---|---|---|
| 2026-10-15 | app-positioning | Created |
