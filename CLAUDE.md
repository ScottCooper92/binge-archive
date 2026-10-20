# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read this file from
`main` and treat it as the source of truth; so should you.

## What this repository is

An Android companion app for [Binge](https://github.com/ScottCooper92), on phones and Android TV.
It finds public-domain and openly licensed films on the Internet Archive and hands them to Binge
as playback sources. It is the first companion to serve the STREAM contract.

Binge ships as a plain TMDB client with no bundled providers. A companion is a separate APK that
exports a bound Android Service implementing a contract from
[binge-companions](https://github.com/ScottCooper92/binge-companions). This one implements
`binge.companion.stream.v1`. **Nothing in this repository defines a contract.** A change to the
wire format is a PR against binge-companions.

The plan is binge-companions' epic
[#141](https://github.com/ScottCooper92/binge-companions/issues/141) and its phases there,
#142 to #149.
Status is pre-alpha: the index, the resolver, the exported Service, the app's own screens and the
release tooling are in. Nothing is on Play yet, and the Service has yet to be checked against Binge
on a device.

The contracts, the SDK and the design system are consumed as source. `binge-companions/` and
`design-system/` are git submodules, included as composite builds in `settings.gradle.kts`.
`binge-ci/` is a third, for the shared Gradle gates. `.gitmodules` is an agent-governed path, so
an author bot's commit can revert a change to it: bump a submodule in a commit of its own, and check
the pins after any bot push.

The screens wear the design system's `BingeExpressiveTheme` and `BingeTvTheme`, with this app's
own emerald `ArchiveBrand` in place of Binge's amber (`theme/ArchiveTheme.kt`).

## The one rule everything else serves

**By default, only openly licensed items are returned. Anything wider is the user's own choice.**

An Archive item becomes a source only if the user's licence level admits its licence
(`LicenceLevel`, set in Sources on a three-stop slider). The default is `CREATIVE_COMMONS`: the
Public Domain Mark, the Creative Commons public-domain URL, CC0 and every Creative Commons licence.
Every one of them allows watching; NC, ND and SA limit reuse, not viewing. The user may narrow it to
the public domain only, or widen it to "Any licence", which Sources marks with a warning in the
error colour. That widest level admits every item: no licence stated, licence text this
app does not recognise, and text that reserves the rights. Restricted and dark items stay out at
every level, because that is the Archive's own access control, not a licence. Public Domain Mark
items are returned everywhere.

Each source carries its licence in `licence_label` and `licence_kind` (binge-companions #248), so
the host shows it on a line of its own and warns on anything but an open licence; the `label` is the
film alone. A recognised licence keeps its short name ("Public domain (US)", "CC BY-SA 4.0"). A
missing or blank one reads "No licence stated". Anything else shows the item's own text, without
control or format characters and shortened to 40 characters. `LicenceAllowlist.identify` classes
every licence field as one `Licence.Kind`; public domain and every Creative Commons licence go on
the wire as `OPEN`, and the rest as `NONE_STATED`, `RESTRICTED` or `UNKNOWN`.

- **Never widen the default.** `SourceSettings.DEFAULT` is today's behaviour, and a fresh install
  or a setting this build does not know reads as it.
- **Never skip the check.** Every item goes through `LicenceAllowlist` at the user's level, on
  every route and every fallback. No code path admits an item without it.
- **Nothing chooses a wider level for the user.** Only the user's own pick does, and "Any" always
  shows its warning.

This rule is why the app may exist at all. Widening the default, a level the user did not choose,
or a code path that skips the check, is never a fix for a test or a coverage gap. It is a decision
for the maintainer in an issue.

## Talking to the host

- **Feature detection never uses version numbers.** The host learns what this app can do from the
  handshake's capability set, and ignores enum values it does not know.
- **Errors are gRPC status codes**, never fields on a response message. `stream.proto` is the
  authority, and the resolver reports which answer applies. In its words:

  > So `Resolve` has four answers: sources; OK and empty (a known title with no licit source);
  > NOT_FOUND (not in the catalogue); and INVALID_ARGUMENT (a bad request).

  > NOT_FOUND — the title is not in this integration's catalogue. It is an answer, not a failure
  > […] Distinct from a `Resolve` that completes with no sources, which is OK and empty: the
  > integration knows the title and has no source for it.

  > INVALID_ARGUMENT — a bad request: an unusable `MediaId` (MEDIA_TYPE_UNSPECIFIED, `tmdb_id`
  > <= 0), a `MediaId` whose type the integration knows is wrong (a movie id that is not a film,
  > or the reverse), an `episode` on a movie, an episode the series does not have, or a
  > `source_id` this integration never issued. A valid series the integration does not carry is
  > NOT_FOUND.

  For this app, "the catalogue" is every film some route can place on the Archive: the index, and
  the network routes behind it. A film missing from the index is not `NOT_FOUND` until the routes
  have also found nothing.
- **Movies only in v1.** A series request is validated (`INVALID_ARGUMENT` when unusable) and is
  otherwise `NOT_FOUND`: a valid series this app does not carry.
- **`mime_type` comes from the Archive file's format**, and is only ever a video type
  (`video/mp4`, `video/webm`, `video/ogg` and so on). The host drops anything off its video
  allowlist (`common.proto`, `PlaybackSource`).
- **Media is identified as media type + TMDB id.** Translating that into Archive identifiers is
  this app's job, and it lives in one clearly named place.
- **Partial results are normal.** `Resolve` streams sources as it finds them and stops all work
  when the call is cancelled. The host reads at most fifty, and cancels at its 10 s deadline (set
  from binge-companions#148's measurement), so the sources sent before then are what the user gets.
- The exported Service is the attack surface. Whatever guards it is load-bearing, and is not
  loosened to make a test pass.

## The index

`CAPABILITY_PROBE` is answered from a small index on the device, never by resolving. It holds two
public sources, refreshed weekly:

- **Wikidata links**: items with a TMDB id (movie P4947 or series P4983) and an Internet Archive
  identifier (P724), with whether each is a known non-film (see below).
- **Archive IMDb externals**: Archive `movies` items tagged `urn:imdb:…`, with the TMDB id Wikidata
  gives for that IMDb id, and the same non-film flag.

**Not a film** is a known non-film kind, never "not called a film". A TMDB movie is not a film
only when it has classes (P31) and every one is a subclass of television series, television series
episode, miniseries, literary work or film series, and not also of film (Q11424). Anything else may
be a film and goes on to the routes: a television special, a "video", a serial, an item with no
class at all (#26). The status screen's "films linked" counts by the same rule.

The code is split in two:

- `:index` is plain JVM code: the queries, the parsers, the one HTTP client (`ArchiveHttp`), the
  fetcher and the seed format. It has its own fast JUnit tests against fixtures under
  `index/src/test/resources/fixtures`, trimmed from real responses.
- `:app`'s `index` package holds the Room tables (`wikidata_link`, `archive_imdb`, `refresh_log`),
  `IndexRepository` and the weekly `IndexRefreshWorker`. `IndexRepository.lookup(mediaId)` is what
  the resolver and Probe read. Probe answers from it. The resolver reads `isFilm`, and reads the hit's
  Archive ids only when its live Wikidata query fails (see The resolver).

Rules that hold it together:

- **A failed refresh keeps the previous index.** Only a whole, successful snapshot replaces it, in
  one transaction. A fetch that returns no links at all is a failure, not an empty index. So is an
  Archive search that returns no items, one that ends with fewer items than its `numFound`, and one
  that returns under half the Archive items the index holds (`IndexRepository.replace`).
- **The seed ships in the APK.** `generateIndexSeed` runs `:index`'s own fetcher at build time and
  writes `assets/index/seed.tsv`. Offline it writes nothing and the build goes on; the app then starts
  empty until its first refresh. A seed under a week old is reused; `-Parchive.skipSeed` skips it.
  Tests never depend on the live seed: `SeedAssetTest` skips itself when a build has none.
- The seed is loaded before the first read of any kind (a lookup, Probe, the status screen or the
  refresh worker), off the caller's thread, and only when the log is empty, so it never overwrites a
  newer index.
- The status screen's freshness is `refresh_log`'s last success (`IndexRepository.lastSuccess()`).
- The Room schema is exported to `app/schemas` and committed. The database falls back to a
  destructive migration: the index is rebuilt weekly and holds nothing anyone owns.

## The resolver

`Resolver.resolve(mediaId, episode)` (package `resolve`) reads the user's `SourceSettings` once, at
the start, and answers a `ResolveOutcome`, which the Service
maps to the contract: `Sources` (a flow, possibly empty), `NotFound`, `NotAFilm` or `Invalid`. A
network failure is an `IOException`, thrown out of `resolve()` itself (the finder's Wikidata call, its
IMDb and title searches, and the title route's loads) or out of the flow. The Service maps both to
`UNAVAILABLE`. The Wikidata route's download-count search only orders its items: if it fails, they
keep Wikidata's order. The same holds for the index's ids when they stand in for a failed Wikidata
query (step 3): they keep the index's order.

1. **The request.** An unusable `MediaId` or `EpisodeRef` is `Invalid` before anything is looked up.
2. **Series and the index.** A valid series is `NotFound` (v1 carries none). A movie id the index
   knows is a known non-film kind (see The index) is `NotAFilm`. A film the index does not know goes on to the routes: it is
   `NotFound` only if none of them finds an Archive item.
3. **`ItemFinder`.** Routes in a fixed order, stopping at the first that finds items: the Wikidata
   item's Archive ids, then Archive items tagged with its IMDb id, then a title-and-year search
   (only with a Wikidata runtime to check against, and only with title matches on in Sources).
   At most five candidates, best-downloaded first.
   A title match counts as found only if a video file runs within 8% of the runtime, checked
   before any licence. If none qualifies and any failed to load, the resolve fails, never
   `NotFound`.
   Wikidata is always asked first, since only it gives the title, year and runtime. That query has
   its own budget of 4 s (`ResolverHttp`), well inside the host's 10 s deadline, and running out of it
   is an `IOException` like any other failure. If that query
   fails, the index hit's Archive ids stand in: its Wikidata ids as route 1, else its IMDb-tagged ids
   as route 2. They are read with no title facts, so the label takes the item's own title, and the
   minimum length is off. The licence check still applies. The fallback is logged as a
   `WIKIDATA_FAILED` coverage miss. With no hit, or a hit with no ids, the resolve fails.
4. **`ItemReader`.** Reads every candidate's metadata in parallel, and hands on each admitted item
   as soon as it and the candidates ahead of it are read: a slow item holds back only those after
   it. It drops items whose licence the user's level does not admit, restricted or dark items,
   non-`movies` items, and items that fail to load. If none is admitted and any failed to load, the
   resolve fails rather than answering empty.
5. **`SourceMapper`.** Video files only (`VideoFormats`). With a runtime, a file under half of it is
   dropped: a reel, a trailer or an extra is not the film. One source per resolution class per
   item, preferring h.264 in MP4, then any MP4, then height, length and size. The label is
   "Title (year)", and the licence goes in `licence_label` and `licence_kind`. SubRip and WebVTT
   become subtitle tracks, at most ten. Each item's
   sources are sent as soon as the item is read, in download order; an item this device reported is
   held back and sent last. At most fifty sources. URLs are `https://archive.org/download/…`, with a
   nominal 24-hour expiry. Before an item's sources are sent, the user's minimum quality drops those
   below it, and "only copies with subtitles in my language" drops those with no track in the
   device's language.
6. **Settings never change the answer's kind.** A known film they filter down to nothing is OK and
   empty; a film only the title route could place is `NOT_FOUND` with title matches off.

Every gap is a `CoverageMiss` (route and reason) for binge-companions#148: a film not in the
catalogue, one the routes found but the index did not know, a resolve that fell back on the index
because Wikidata failed, and a resolve that ends with nothing. A resolve that ends with nothing
because a setting narrower than the default removed something is `FILTERED_BY_SETTINGS`.
`LogcatCoverageLog` writes them to logcat on the device and nowhere else.
Every HTTP call is cancellable: when the host cancels, the call is cancelled with it.

## The Service

`service/ArchiveCompanionService` is the exported Service, under the `com.binge.companion.STREAM`
action with the SDK's manifest meta-data (`com.binge.companion.name`, `.icon`, `.majors` = 1).
`ArchiveStreamService` serves STREAM v1:

- **`Handshake`** always succeeds: there is no sign-in. It declares `CAPABILITY_PROBE`,
  `CAPABILITY_SUBTITLES` and `CAPABILITY_REPORT_SOURCE`, names "Internet Archive" as the provider,
  and ignores `HostInfo`.
- **`Resolve`** is the resolver's `ResolveOutcome`, mapped in one place, `ArchiveErrors`.
  `NOT_FOUND` carries a `LocalizedMessage`, "Not on the Internet Archive", in the device's locale.
  A Wikidata or Archive failure is `UNAVAILABLE` with a fixed description, never an exception's
  message, before or after sources were sent.
- **`Probe`** reads the index and never the network, and ignores the Sources settings. It refuses
  what `Resolve` refuses, and a valid series is `NOT_FOUND`.
- **`ReportSource`** takes a `source_id` of the form `<identifier>/<file name>`, the identifier in
  the Archive's character set; anything else is `INVALID_ARGUMENT`. The index is not consulted, so
  an item found by any route can be reported. The report is stored on the device
  (`device-records.db`, separate from the index) and pushes that item after every other for 30 days.
- A gated rpc checks its capability with a STREAM `requireDeclared`, the twin of the SDK's
  REQUEST-typed one.
- **Who may bind:** a release build pins Binge's published certificate (`HostPolicy.pinned` with
  `BingeHosts.release`). A debug build admits Binge's package names under any certificate
  (`HostPolicy.anyCertificateOf`) plus this app's own uid, chosen by `BuildConfig.DEBUG` and
  nothing a user can switch.

## The app's own screens

A companion's UI is for what only it can do. With nothing to sign in to, this one is a status screen
with the Sources settings, its licence page, and the hand-off Binge's "manage" row starts:

- **`ui/status`**: `StatusViewModel` exposes one `StateFlow<StatusUiState>`, a sealed `Loading`,
  `Ready` or `Error`, never a set of flags. `StatusScreen` (phone) and `ui/tv/TvStatusScreen` take the
  state and a `StatusActions`, so every arm renders in a frame. Sections: what this is (and the
  licence page), Sources, the index (films linked, last refresh, a failed refresh, "Refresh now"),
  who the data comes from, recently resolved titles, and reported copies.
- **Wide windows** (#49): `PhoneStatusRoot` is the one place the phone's layout is chosen, from
  the window size class. Below 840dp it is `StatusScreen` or the licence page alone, capped at
  `content_max_width` and centred. From 840dp it is `StatusListDetail`: the status list and its
  detail (Sources, or the licence page) side by side, in even halves. No screen below it asks the
  window.
- **Recently resolved and reported** live in `device-records.db` (`reports/DeviceRecords.kt`), on
  the device only, never shown to the host. Each list has a clear action.
- **`SettingsHandOffActivity`** answers `CompanionManifest.ACTION_SETTINGS`. It checks its caller
  with the SDK's `HandOffPolicy` (the same pins as the Service) and opens `MainActivity`. A start
  with no calling package opens nothing.
- **Sources** (`settings/SourceSettingsStore`, a DataStore on the device, saved as made with no Save
  button): title matches (a switch, default on), licences (a slider of three levels, default openly
  licensed), minimum quality (any, 720p, 1080p; default any) and "only copies with subtitles in my
  language" (default off). Under the licence slider, the chosen level's name and what it means sit in
  a cell sized to the longest level's text, so nothing moves as it changes; "Any" reads as a warning
  in the error colour. On the TV the slider is the design system's `TvNumberStepper`. These four
  are the only settings that change what is resolved; adding another is a decision for the
  maintainer in an issue.
- **No browse and no search.**
- Everything is the design system's: `ItemGroup` rows on the phone, `TvTwoPanePage` and
  `TvOptionRow` on the television. The one exception is the phone's licence slider, Material 3's
  `Slider` (`ui/status/LicenceLevelSlider.kt`), until the design system has a stepped slider. The brand colour is chosen once, in `theme/`.

## Talking to Wikidata and the Archive

- **Keyless.** No API key, no account and no sign-in, and no TMDB fallback: binge-companions#148
  measured what one could add, and the maintainer decided the app stays keyless.
- One HTTP client for the whole app (`ArchiveHttp.client`), with fixed timeouts: 10 s to connect,
  20 s to read. The weekly refresh keeps both. The resolver's Wikidata query alone gets a 4 s budget
  (`ResolverHttp.SPARQL_BUDGET_MILLIS`), since the host cancels at 10 s. Every request names the app in its User-Agent, as Wikimedia asks. Both hosts are
  public and served over HTTPS; the network security config trusts system CAs only and refuses
  cleartext.
- Keep each Wikidata query well inside the read timeout. A subclass walk per row made the link query
  take up to 27 s, so the film classes are a separate query, joined on the device.
- No caching beyond the on-device index.
- **Coverage misses are logged on the device, by route**, for binge-companions#148's measurement.
  They never leave the device: no analytics, no crash reporting and no upload of any kind.
- The app stores no user data beyond the user's own source reports and the last ten titles it
  resolved, kept on the device in `device-records.db`, and the Sources settings in its DataStore.
  The index is public metadata it can rebuild. Backup is off.

## Kotlin, Gradle and Android

- Kotlin via AGP's built-in support, `jvmTarget` 17, built and tested on JDK 21.
- `compileSdk` 37, `minSdk` 26, `targetSdk` 36, and the AGP in `libs.versions.toml`, matching
  binge-seerr.
- The `org.jetbrains.kotlin.android` plugin is declared `apply false` in the root build and never
  applied. It puts the catalog's Kotlin on AGP's classpath. See the comment in `libs.versions.toml`.
- The version catalog is `libs.versions.toml` at the repository root, not under `gradle/`.
- Package `io.github.scottcooper92.binge.archive`; a debug build adds `.debug`.
- Two modules: `:app` and `:index` (plain JVM, see The index). Tests are JUnit 4 in both. Scope
  Gradle to a module (`:index:test`, `:app:testDebugUnitTest`) when you are checking one thing.
- No DI framework: `AppGraph` builds the handful of objects by hand, and `ArchiveApp` holds it and
  hands WorkManager its worker factory.
- A Robolectric test needs no `@Config` for the SDK: `app/src/test/resources/robolectric.properties`
  pins `sdk=34`, and `RobolectricDefaultsTest` asserts it. `@Config` is for what a test varies, a
  `qualifiers` string say.
- `runCatching` is banned in `src/main` by detekt. Use the SDK's `attempt`
  (`com.binge.companion.sdk`): `runCatching` also catches the cancellation a cancelled scope sends.
- Every user-visible string comes from `strings.xml`, with its Spanish in `values-es`. The default
  locale is en-GB (`res/resources.properties`).

## Gates

`./gradlew build` is the whole gate, and everything below turns it red. `app/build.gradle.kts` is
where to confirm the list.

- Kotlin compilation, and the debug and release builds (R8 on release).
- The unit tests (`:app:testDebugUnitTest`, `:index:test`).
- `ktlintCheck`.
- `detekt`, with type resolution, against the root `detekt.yml`. There is no baseline: the gate is
  zero findings. Keep it that way rather than creating one.
- Android lint (`:app:lint`). The translation checks (`MissingTranslation`, `ExtraTranslation` and the
  format and quantity checks) and `UnusedResources` are pinned to error: a locale is complete or it
  does not exist, so an English string added without its Spanish is a red build.
- `checkTranslationStaleness`: a reworded English string whose Spanish was not re-read. Re-read it,
  then `./gradlew updateTranslationHashes` and commit `translation-hashes.txt`.
- `koverVerify`: line coverage below **95%** in `:app` or in `:index`. The floors were measured
  (98.8% in each when the index landed), not inherited. Excluded: generated code (Room's DAOs and
  databases with their `$DefaultImpls`, BuildConfig), `MainActivity`, the Application and `AppGraph`
  (wiring), composables with their `ComposableSingletons`, the frames' `PreviewData`, and `:index`'s
  `SeedMain` (the build's entry point, covered through what it calls). `ArchiveCompanionService` is
  excluded too: it only hands the SDK objects from the graph, and what they choose is tested through
  `ArchiveStreamService` and `selectHostPolicy`. `SettingsHandOffActivity` is measured. Nothing is
  excluded for merely lacking tests.
  Raise the floors as the code grows; never lower them.
- `validateDebugScreenshotTest`: a `@PreviewTest` frame that no longer renders like its committed
  PNG. `app/build.gradle.kts` wires it into `check` by hand.
- `checkTvMaterialSeparation`: a Material 3 import under `ui/tv`, or a tv-material import anywhere
  else. `theme/ArchiveTheme.kt` is the one allowed crossing.

detekt's wiring, `checkTranslationStaleness` and `checkTvMaterialSeparation` are binge-ci's shared gates, from the `binge-ci`
submodule. Keep that submodule on the same binge-ci release as the `uses:` pins in
`.github/workflows/`, and move them together.

CI runs the same tasks as four jobs in parallel (`checks`, `tests`, `screenshots`, `release`;
`:index`'s tests and coverage run in `checks`), plus
`submodule-pins` on a PR, which fails a pin its own repository has not merged unless the PR carries
`hold`, and `actionlint`, which lints the workflows and is skipped on the nightly schedule. A last
job, `build`, is green only if all of them are. It is the check branch protection
requires, and the one the bots read. `ci.yml` explains the split.

There is no `buf` here, so do not report a finding as though one had caught it.

### Screenshot frames

Frames live in `app/src/screenshotTest/kotlin/…`, each a `@Composable` with `@PreviewTest` and one
of the multipreviews in `preview/ArchivePreviewMatrices.kt`. The PNGs are committed under
`app/src/screenshotTestDebug/reference/…`. When a change is meant to alter a frame, re-record with
`./gradlew :app:updateDebugScreenshotTest`, look at the new PNGs, and commit them with the code.
New UI comes with its frames in the same PR, every state arm included. Frames pin the locale
(`SOURCE_LOCALE`, or `es` for the Spanish cells) and a fixed `now`, so nothing drifts.

**Never silence a gate instead of fixing it.** Do not add a ktlint baseline or a `ktlint-disable`
comment (`./gradlew ktlintFormat` is the fix), a `lint-baseline.xml`, `abortOnError = false`, a
`detekt-baseline.xml` or a `@Suppress` to get past detekt, and do not exclude a source set from
`check` or lower the kover floor.

## Releasing

- **R8.** Release builds shrink. The app's own keep rules are the `keepRules` source set
  (`app/src/main/keepRules/`), in `main` so the minified debug build uses the same ones. The
  libraries and the contracts bring their own; add a rule here only for what they do not cover.
  `app/src/debug/keepRules/` keeps, for the minified debug build only, the exact members the
  instrumentation test calls that the app itself never uses: request builders, response getters,
  the gRPC client and a few stdlib and coroutines helpers. Without it the test fails on the device
  (`NoClassDefFoundError`, `NoSuchMethodError`). `tools/instrumentation-keep/check.sh` lists what is
  missing; run it after changing the test.
- **`-PminifyDebug`** builds a minified debug app. The device lane runs it, so a stale keep rule fails
  there rather than on a user. Its `optimization` block must match release's.
- **The device lane** (`device-smoke.yml`, never `ci.yml`): an emulator binds the Service across a real
  Binder on the minified debug build, then handshakes, probes and resolves Night of the Living Dead
  (`app/src/androidTest`). Its Resolve uses the live network, so an `UNAVAILABLE` there names
  Wikidata or the Archive first. A run with zero tests fails: a crashed test process can otherwise
  end green. It runs on a push to `main` that touches the paths it lists, weekly,
  and by hand. A red weekly run opens one issue, which closes on the next green one.
- **`release.yml`** builds a signed bundle from a `v*` tag, checks it is release-signed (never
  `CN=Android Debug`), shrunk and seeded, and attaches it with the R8 mapping to a GitHub Release. Play
  upload, the upload key's secrets and the Play Console app are the maintainer's.
- **The listing** lives in `docs/listing/`: the store text in English and Spanish, the privacy policy
  and the Data safety answers. A change to what the app stores, sends or asks for changes
  `privacy.md` and `data-safety.md` in the same PR.

## Follow-ups

Anything worth doing that does not belong in the diff in front of you becomes a GitHub issue, not
a TODO comment and not a line in a PR description.

The test is **scope, not severity**. A small problem that reaches beyond the current diff is still
an issue; a serious problem inside the diff is a change to make now. Dedupe against the open
backlog before filing, and use the labels the repository already has.

## Commits and pull requests

**Open every PR from `.github/pull_request_template.md`.** Tick only the checks you ran locally on
the final head, and say what you left to CI and why. The review bot reads the ticks against this
PR's earlier CI runs, so a ticked check that failed one of them is a finding.

- Conventional-commit subjects (`feat:`, `fix:`, `docs:`, `chore:`), imperative mood.
- One reviewable idea per PR, one PR per issue.
- A PR that changes how this app talks to the host says which contract version and which
  capabilities it relies on.
- Documentation is plain, direct English: short sentences, one idea each.

## Agent workflows

`.github/workflows/` holds a review bot and four author bots, from binge-ci's shared workflows.
They act only on PRs carrying the `agent` label. An approved, green PR merges on its own, with a
merge commit.

**That label is maintainers-only.** Applying it grants an agent code execution with this
repository's secrets in scope. Do not apply it to a PR you have not read, and never to one from a
fork. The workflows refuse fork PRs, and on a public repository that guard is the load-bearing
control.

**Every PR gets `agent`. Add `hold` only when a human must do the merging.** `hold` withholds the
bot's merge: the PR is still reviewed and kept current, but a maintainer lands it. Add it alongside
`agent` only when the PR:

- changes how the bots review or merge (`.ai/agents/`, `.github/workflows/`, or these label rules
  themselves);
- needs a check on a device first; or
- isn't ready to merge (a stack to land by hand, or a PR waiting on a decision or another
  repository).

Everything else, including `CLAUDE.md`, skills and docs, takes `agent` alone and merges once
reviewed and green.
