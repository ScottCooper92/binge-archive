# binge-archive

Free films for [Binge](https://github.com/ScottCooper92), from the Internet Archive. By default this
app only returns items with an open licence: the public domain, CC0 or CC BY. Its Sources settings
let you narrow that, or widen it, and only your own choice ever widens it.

## For the person watching

Install it beside Binge, on a phone or an Android TV. Open a film in Binge. By default, if the Internet Archive
holds an openly licensed copy, Binge offers it to play, and names the licence on the row.

- **Films only.** Series find nothing for now.
- **Mostly older films.** Most public-domain films are from before 1960, so many titles will have
  nothing here.
- **No account and no sign-in.** The app needs no key and asks you for nothing.
- **Nothing about you leaves your device.** The app asks Wikidata and the Internet Archive about the
  film you opened, and nothing else. It has no analytics and no crash reporting, and is left out of
  backups. Where you got to in a film stays on your device, for Binge alone to resume it, and is
  forgotten once you finish it or after 90 days unplayed.

The app itself has a status screen and a licence page. The status screen shows what the app does,
its Sources settings, how fresh its index is, the films it found for you recently, and how many titles Binge can resume, with a button to clear them. Sources chooses whether title matches are used, which licences are accepted, a
minimum quality, and whether a copy must have subtitles in your language. The licence
page says which licences it accepts, and why. You find films from Binge. The app speaks English and Spanish.

## For the person writing a companion

Binge ships as a plain TMDB client with no bundled providers. A companion app is a separate APK that
exports a bound Android Service implementing a contract from
[binge-companions](https://github.com/ScottCooper92/binge-companions). Only data crosses the
boundary, over gRPC on Binder, and there is no dynamic code loading.

This app serves the STREAM contract (`binge.companion.stream.v1`). It goes from a TMDB id to Archive
items through Wikidata, then an Archive search by IMDb id, then a title-and-year search checked
against the film's runtime. It keeps only items with an allowed licence, and maps their video files
to sources. The plan is
[binge-companions#141](https://github.com/ScottCooper92/binge-companions/issues/141).

Status is pre-alpha. The index, the resolver, the STREAM Service, the app's screens and the release
tooling are in. It is not on Play yet.

## Building

```sh
git clone --recurse-submodules https://github.com/ScottCooper92/binge-archive
cd binge-archive
./gradlew build
```

`build` is the whole gate: it compiles, tests, lints, checks coverage and compares the screenshot
frames. You need JDK 21 and an Android SDK with platform 37. The contracts, the SDK and the design
system are submodules built from source, so `--recurse-submodules` matters.

A `v*` tag builds a signed release bundle (`.github/workflows/release.yml`). The store listing and
the privacy policy are in [`docs/listing/`](docs/listing/).

`CLAUDE.md` holds the conventions, for people and agents alike.

## Licence

Internet Archive for Binge is free software. You can redistribute it and modify it under the terms of
the GNU General Public License, version 3 or (at your option) any later version
(GPL-3.0-or-later). See [LICENSE](LICENSE).

    Copyright (C) 2026 Scott Cooper and the identities that author commits here

    This program is free software: you can redistribute it and/or modify it under the terms of
    the GNU General Public License as published by the Free Software Foundation, either version 3
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU General Public License for more details.

The copyright holders are the owner, Scott Cooper, and the Claude and bot identities that authored
commits in this repository (`Claude`, `binge-pr-author[bot]` and `binge-pr-reviewer[bot]`), at the
owner's direction.

- **An additional permission** under section 7 of the GPL, for Google's Play services and Firebase
  libraries, is in [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md). The app links neither today.
- **The name and artwork are not licensed by the GPL.** See [TRADEMARKS.md](TRADEMARKS.md) and
  [ASSETS.md](ASSETS.md). A fork has to use another name and other artwork.
- **The contracts and SDK stay Apache 2.0.** They are in
  [binge-companions](https://github.com/ScottCooper92/binge-companions), so anyone can write a
  companion without taking on the GPL. The design system's licence is decided separately.
- **Dependencies** are all under licences the GPL is compatible with: Apache 2.0, and BSD 3-Clause for
  protobuf. JUnit (EPL 1.0) and Robolectric (MIT) are used for tests only and are not shipped.
