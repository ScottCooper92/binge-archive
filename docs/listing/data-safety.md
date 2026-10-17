# Data safety form

The answers for the Play Console's *Data safety* section, each with its reason, so a change to the
app can be checked against it. Play's definitions apply: data is **collected** when it leaves the
device for the developer or a third party the developer chose, and **shared** when it goes to a
third party.

## Overview

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | The app has no server, account, analytics or crash reporting. It sends Wikidata and the Archive the TMDB id of a film the user opened, which is not user data in Play's sense: it is a lookup of public catalogue data, carries no identifier and is not linked to the user. |
| Is all of the user data collected by your app encrypted in transit? | Not asked when nothing is collected | Every request goes over HTTPS regardless (`network_security_config.xml` allows no cleartext). |
| Do you provide a way for users to request that their data is deleted? | Not asked when nothing is collected | What is on the device is cleared on the app's screen or by uninstalling. |

If Play's review reads the film lookups as *App activity: Other actions*, the entry would be:
collected, not shared, required, for *App functionality*, not linked to the user. Record the answer
here once it is known.

## Security practices

| Practice | Answer | Why |
|---|---|---|
| Data is encrypted in transit | Yes | HTTPS only. |
| Committed to the Play Families policy | No | Not a children's app. |
| Independent security review | No | |

## On-device data, for completeness

Not asked by the form, but `privacy.md`'s reader will want it: the public film index; the last ten
films resolved; copies the user reported (each offered last for 30 days, then deleted; the user can clear
them sooner). The last two can be cleared on the app's screen. Android's backup and device transfer copy none of it (`allowBackup="false"`, and
`data_extraction_rules.xml` excludes every domain from both).

## Permissions declared

The app's own manifest declares `INTERNET`. The merged release manifest (checked from
`:app:processReleaseMainManifest`) also carries:

| Permission | Comes from |
|---|---|
| `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` | WorkManager |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` (signature-level, the app's own) |

They are listed in `privacy.md`. Re-check the merged manifest whenever a dependency changes.
