# Privacy policy

*Internet Archive for Binge* ("the app") finds openly licensed copies of films on the Internet
Archive for Binge to play. This policy says what the app stores, where it sends anything, and what
it does not do. It is written to be checked against the source code, which is public at
https://github.com/ScottCooper92/binge-archive.

## What the app stores on your device

- **A film index.** Public data from Wikidata and the Internet Archive: which films have a copy on
  the Archive. It is refreshed about once a week. It says nothing about you.
- **The last ten films it found for Binge**, so the app's screen can show them. You can clear the
  list on that screen.
- **Copies you reported**, when you tell Binge a copy did not play. The app offers that copy last
  for 30 days, then forgets the report. You can clear reports on the app's screen.
- **A log of films it could not find**, written to the device's own system log to help improve
  the index. It is never sent anywhere.

The app turns off Android's backup (`allowBackup="false"`) and excludes all its data from cloud
backup and device-to-device transfer, so none of it is copied to Google or to a new device.
Uninstalling the app removes it all.

## Where the app sends data

- **To Wikidata** (`query.wikidata.org`): the TMDB id of the film you opened in Binge, to look up
  its title, year, runtime and Archive links; and, about once a week, a query for the whole film
  index.
- **To the Internet Archive** (`archive.org`): searches for that film and requests for the
  details of matching items; and, about once a week, a search for the index.

Both requests go over HTTPS and carry a fixed app name and version, nothing about you or your
device. Wikimedia's and the Internet Archive's own privacy policies govern what they log, such as
your IP address.

When you play a copy, **Binge** fetches the video from `archive.org` itself. This app only hands
Binge the address, over an Android service binding that never leaves your device.

The app has no server of its own.

## What the app does not do

- No account and no sign-in. It needs no key and asks you for nothing.
- No analytics, crash reporting or advertising libraries.
- No access to your contacts, location, files, camera or microphone.
- No background activity except the weekly index refresh.

## Permissions

- **Internet**, to reach Wikidata and the Internet Archive.

Android's WorkManager library, which runs the weekly refresh, adds more to the installed app:
**network state** (to wait for a connection), **wake lock**, **run at startup** and **foreground
service** (to keep the refresh scheduled across restarts). `androidx.core` adds a signature-level
permission named after the app, which protects its own receivers; no other app can hold it.

## Children

The app is not directed at children.

## Changes

This policy changes when the app's behaviour changes, in the same change, and its history is the
repository's history.

## Contact

Open an issue at https://github.com/ScottCooper92/binge-archive/issues.
