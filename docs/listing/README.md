# The Play listing

What the store page says, kept in the repository so it is reviewed like code and stays in step with
what the app does. Each file maps to one part of the Play Console.

| File | Play Console |
|---|---|
| [`listing.md`](listing.md) | Main store listing: app name, short description, full description (English) |
| [`listing.es.md`](listing.es.md) | The same, for the Spanish listing the app's `values-es/` earns it |
| [`privacy.md`](privacy.md) | The privacy policy the listing links to |
| [`data-safety.md`](data-safety.md) | The answers to the Data safety form, each derived from `privacy.md` and the code |

## Not done here

- **The Play Console app, signing and upload.** The release workflow builds a signed bundle from a
  tag and attaches it to a GitHub Release. Creating the app in the Play Console, the upload key and
  its secrets, and the first upload to internal testing are the maintainer's.
- **Publishing the privacy policy.** Play needs it at a public URL. Publishing `privacy.md` (for
  example to GitHub Pages) is the maintainer's choice.
- **Store screenshots.** These are captures from a device, not the `@PreviewTest` frames the build
  gates. Capture, on a phone at 1080×1920 or larger: the status screen with a few recently resolved
  films, and the licence page. Then two Android TV frames (1920×1080) of the same. A Binge title
  page showing this app's sources is the best "what it adds" frame. The feature graphic
  (1024×500) does not exist yet.
- **The app name is a maintainer's decision.** `listing.md` proposes one. The launcher label is
  `app_label` ("Binge Archive"). The name Binge shows for this companion stays `Internet Archive`
  (`companion_label`).

## Keeping it true

The listing describes behaviour, so a change to what the app stores, sends or asks for is a change
to `privacy.md` and `data-safety.md` in the same PR. The claims that must stay true:

- The app contacts `query.wikidata.org` and `archive.org`, over HTTPS, and nothing else
  (`index/ArchiveHttp.kt`, `res/xml/network_security_config.xml`). Binge plays the files from
  `archive.org` itself.
- No analytics, crash reporting or advertising SDK. Check `app/build.gradle.kts` before changing
  this claim.
- What is stored: the public index (`index.db`) and `device-records.db` (source reports and the
  last ten resolved titles). A report pushes its copy last for 30 days, then the weekly refresh
  deletes it (`reports/DeviceRecords.kt`, `IndexRefreshWorker`). Both lists can be cleared on the
  status screen.
- Coverage misses go to logcat on the device and nowhere else (`resolve/CoverageLog.kt`).
- `android:allowBackup="false"` and `data_extraction_rules.xml` exclude everything from cloud
  backup and device transfer. Keep both.
- The merged release manifest's permissions (from `:app:processReleaseMainManifest`) are listed in
  `privacy.md` and `data-safety.md`. Re-check them when a dependency changes.
