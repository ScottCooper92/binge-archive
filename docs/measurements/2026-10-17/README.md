# Coverage and latency, 17 October 2026

The first measurement for binge-companions#148, made with the built resolver against live Wikidata and
the Internet Archive. The raw rows are in `results.tsv`, the input lists in the `*.txt` files, and the
index refresh in `refresh.txt`. To run it again:

```sh
./gradlew :app:testDebugUnitTest --tests '*CoverageMeasurement*' --rerun -Parchive.measure=<dir>
```

`<dir>` holds one `<list>.txt` per list, with a TMDB movie id first on each line.

## How it was measured

- **Where:** a cloud container, on the JVM under Robolectric, calling the real `Resolver`. There is
  no Binder and no phone or TV. The network is a data centre's, not a home's. Treat the times as a
  floor, not as what a phone sees.
- **How:** one resolve at a time, 258 in about 70 minutes. Each resolve fetched everything it needed
  itself; nothing was cached between them.
- **The lists:** TMDB has no keyless API, so the ids come from TMDB's public website: top rated
  (100) and popular (100), each the first five pages, and trending today and this week (13 films;
  the trending panel lists people and series too). The public-domain list is 45 films picked by hand,
  their TMDB ids looked up through Wikidata. There is no watchlist sample: this session has no user's
  watchlist.

## Coverage

`NOT_FOUND` is not a failure (binge-companions#148: it does not count toward health). "Empty" means OK
with no sources: the film was placed on the Archive, but no copy passed the licence check.

| List | Films | Sources | Empty | `NOT_FOUND` | `UNAVAILABLE` |
| --- | ---: | ---: | ---: | ---: | ---: |
| Public domain (hand-picked) | 45 | 38 | 6 | 1 | 0 |
| Top rated | 100 | 6 | 39 | 54 | 1 |
| Popular | 100 | 5 | 18 | 77 | 0 |
| Trending | 13 | 0 | 0 | 13 | 0 |

**Which route found the sources:**

- Public domain: Wikidata 36, title 2.
- Top rated: title 6.
- Popular: title 5.

**The headline:** every source on the top-rated and popular lists is a false positive. All 11 came from
the title route, and each was an upload of a modern film that its uploader marked CC0 or Public Domain
Mark (*Interstellar*, *Inside Out 2*, *Your Name* and so on). Only the 2 title-route sources on the
public-domain list (*The Kid*, *The Navigator*) are plausibly genuine. That is filed as **#18**, which
needs the maintainer's decision.

With the title route discounted, the honest numbers are:

- **Trending:** 0 of 13. As the epic expected.
- **Popular:** 0 of 100.
- **Top rated:** 0 of 100. The epic expected a few percent from silent and early-sound classics, but
  TMDB's website top-rated pages hold no film from before 1930. The few older films on them (*Seven
  Samurai*, *Ikiru*) are not in the US public domain.
- **Public domain (hand-picked):** 36 of 44 counted films (82%) through Wikidata alone, or 38 of 44
  (86%) with the two genuine title-route films. The 6 empty ones (*Metropolis*, *A Trip to the Moon*,
  *The Great Train Robbery*, *Safety Last!*, *Charade*, *Dementia 13*) have Archive items, but none
  carries an allowlisted licence. *The Gold Rush* is `NOT_FOUND`: no route placed it.

**Empty answers are inflated by the same weakness.** 49 of the 57 empty answers on the top-rated and
popular lists came from the title route. A rip with no licence still "places" the film, so the answer is
OK-and-empty, not `NOT_FOUND`. If #18 stops the title route serving, these answers change too: decide
there whether such a match should still count as placing the film.

## Precision of the title route (item 2)

The title route's runtime check worked as designed: every admitted match was the right film. The problem
is the licence, not the identity. 2 of 11 admitted films were genuinely open (18%), far below the 95% bar
binge-companions#148 sets. See #18 for the options.

## Latency (item 3)

The resolver maps every item before it sends a source, so **time to first source equals time to
complete**. The harness stamps both, and they matched on every resolve.

Percentiles are nearest-rank over every resolve, the `UNAVAILABLE` one included.

| | p50 | p95 | Max |
| --- | ---: | ---: | ---: |
| All 258 resolves | 1.4 s | 5.5 s | 21.9 s |
| Resolves with sources (49) | 1.1 s | 3.5 s | 5.1 s |

- 14 resolves took over 5 s, 6 over 8 s and 4 over 10 s. The slowest ones are `NOT_FOUND` or empty
  answers that went through all three routes, plus the one `UNAVAILABLE`.
- No phone or TV numbers. They need a device and a Binge build with a STREAM host.

## Rate limits and cost (item 4)

- **Requests per resolve:** 5.3 on average, 8 at most. One Wikidata query, one or two Archive searches,
  and up to five metadata reads.
- **Per title page opened:** the host calls `Resolve` when a title page opens, so each page costs one
  resolve, 3 to 8 requests.
- **429s:** none, from either service, over 1,381 counted requests (more, counting the harness's own
  report calls). That was one resolve at a time; a burst from many users is not measured.
- **The weekly refresh:** 5 requests, about 353 KB read, 11 to 21 s. One attempt in the smoke runs
  timed out on Wikidata at the 20 s read limit. So the refresh can fail on a slow Wikidata day, keep the
  old index, and retry.
- **`UNAVAILABLE`:** 1 of 258. A title-route candidate's metadata did not load, so the resolve failed
  rather than guess.

## Proposals for the maintainer

These are proposals. The decisions are Scott's.

1. **The Resolve deadline: 10 s.** p95 is 5.5 s from a data centre. A phone on a home network will be
   slower, so 10 s leaves room above p95 while still cutting the few slow tail resolves (4 of 258 here).
   Because the first source arrives with the last, a deadline cuts a resolve to nothing, not to
   partial results. If the host wants sources sooner, the resolver could stream each item's sources as
   it is read. That would be a change here, not in the contract.
2. **TMDB fallback (item 5): stay keyless, and close the item.** A TMDB key would add alternative titles
   and runtimes to the title route. Wikidata lacked a runtime for 47 of the 145 `NOT_FOUND` films, so
   TMDB would put those through the title route. But the title route is the stage #18 shows to be unsafe:
   on these lists it returned no genuine film that the Wikidata route missed, except two silent films.
   Feeding it more titles would mostly feed it more rips. A TMDB-backed stage would make sense only
   after #18 settles how the title route may serve, and then only for films old enough to be in the
   public domain.

## What this did not measure

- Phone and TV latency, and anything across a real Binder.
- A user's watchlist.
- TMDB's `alternative_titles` directly: it needs a key, which this app does not have.
- Load from many devices at once.
