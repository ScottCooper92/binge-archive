# binge-archive

Free films for [Binge](https://github.com/ScottCooper92), from the Internet Archive. By default this
app only returns items with an open licence: the public domain, CC0 or CC BY. Its Sources settings
let you narrow that, or widen it, and only your own choice ever widens it.

## For the person watching

Install it beside Binge, on a phone or an Android TV. Open a film in Binge. If the Internet Archive
holds an openly licensed copy, Binge offers it to play, and names the licence on the row.

- **Films only.** Series find nothing for now.
- **Mostly older films.** Most public-domain films are from before 1960, so many titles will have
  nothing here.
- **No account and no sign-in.** The app needs no key and asks you for nothing.
- **Nothing about you leaves your device.** The app asks Wikidata and the Internet Archive about the
  film you opened, and nothing else. It has no analytics and no crash reporting, and is left out of
  backups. If you tell Binge a copy did not play, this app remembers that on your device for 30 days
  and offers that copy last.

The app itself has a status screen and a licence page. The status screen shows what the app does,
its Sources settings, how fresh its index is, the films it found for you recently and any copies
you reported. Sources chooses whether title matches are used, which licences are accepted, a
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

Apache 2.0. See [LICENSE](LICENSE).
