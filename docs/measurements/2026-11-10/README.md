# Coverage and cost, 10 November 2026

The second measurement for binge-companions#148, made with the built resolver against live Wikidata and
the Internet Archive. It repeats the 17 October run on the same four id lists, so the two compare. The raw
rows are in `results.tsv`, the lists in the `*.txt` files and the index refresh in `refresh.txt`. To run it
again:

```sh
./gradlew :app:testDebugUnitTest --tests '*CoverageMeasurement*' --rerun -Parchive.measure=<dir>
```

## How it was measured

- **Build:** `main` at 67a2ece, the resolver as it was before `ListServedTitles` (#119). That change does
  not touch the resolver, so these numbers hold for it.
- **Where:** a cloud container, on the JVM under Robolectric, calling the real `Resolver`. No Binder, no
  phone or TV. Treat the times as a floor.
- **How:** one resolve at a time, 258 of them, in about 17 minutes. Nothing was cached between resolves.
- **429s:** the harness now waits out a `Retry-After` (up to 120 s, three tries) and counts it. None came.
- **Not recorded in a row:** the chosen item and its licence. The precision check below is by film, from the
  title and year, as on 17 October.

## Coverage, by stage

`NOT_FOUND` is not a failure. "Empty" is OK with no sources: the film was placed, but no copy passed the
licence check. The route is the stage that placed the film: 1 is the Wikidata item's Archive ids, 2 is the
Archive's IMDb tags, 3 is the title-and-year search.

| List | Films | Sources | Empty | `NOT_FOUND` | `UNAVAILABLE` |
| --- | ---: | ---: | ---: | ---: | ---: |
| Public domain (hand-picked) | 45 | 38 | 6 | 1 | 0 |
| Top rated | 100 | 6 | 27 | 61 | 6 |
| Popular | 100 | 3 | 10 | 83 | 4 |
| Trending | 13 | 0 | 0 | 13 | 0 |

| Stage | Films placed | With sources |
| --- | ---: | ---: |
| 1, Wikidata ids | 46 | 35 |
| 2, Archive IMDb tags | 0 | 0 |
| 3, title search | 41 | 11 |

- Stage 2 placed no film that stage 1 had not. Both stages are asked for each title, and stage 2 never won.
- One public-domain film answered with sources and recorded no route.
- The index knew 49 of the 258 films. It was right every time: 36 had sources, 13 were empty.
- 41 films answered with sources or empty without being in the index (`not_in_index`): the routes found
  what the index did not.

## Stage 3 precision

Stage 3 gave sources for 11 answers, which are 10 films (*Interstellar* is on two lists). Judged by film:

- **Plausibly genuine: 2.** *The Kid* (1921) and *The Navigator* (1924).
- **Not genuine: 8.** *Interstellar*, *Your Name*, *Perfect Blue*, *The Uprising*, *Colony* and *Voicemails for
  Isabelle* are modern films whose uploader marked them open. *Seven Samurai* (1954) and *Ikiru* (1952) are
  in copyright in the US, and an upload marked open does not change that.

That is 2 of 10 films (20%), or 2 of 11 answers (18%). Nothing has moved since 17 October (2 of 11), far below the 95%
bar. Another 30 answers on the top-rated and popular lists were empty from stage 3 alone: a rip placed the
film but passed no licence check. The runtime check worked: no admitted match was the wrong film.

## TMDB fallback

- 41 of the 158 `NOT_FOUND` answers had no Wikidata runtime, so stage 3 could not run on them. TMDB would
  supply one. That is 16% of all 258 resolves.
- Of those 41, 23 have a known release year. The oldest is 2013, and 18 are 2024 or later. The other 18
  have no year at all and are modern titles. None is old enough to be in the public domain.
- On the public-domain list, the one `NOT_FOUND` (*The Gold Rush*) has a runtime already, so TMDB would not
  change it.
- So the expected gain in genuine films is about 0 of 258, well under 5%. Feeding stage 3 more titles would
  feed it more rips (see the precision above). The app stays keyless.

## Latency

Percentiles are nearest-rank over every resolve.

| | p50 | p95 | Max |
| --- | ---: | ---: | ---: |
| All 258 resolves | 1.5 s | 4.0 s | 8.0 s |
| Resolves with sources (47) | 1.3 s | 2.8 s | 8.0 s |

- 4 resolves took over 5 s, 3 of them over 8 s. None went over 10 s: the 8 s resolve budget held.
- The first source came before the last on 30 of the 47 answers with sources. The median first source was
  1.1 s against 1.3 s for the whole resolve.
- No phone or TV numbers.

## Cost and failures

- **Requests:** 1,197 counted, 4.6 per resolve on average and 8 at most.
- **HTTP errors:** none. No 429, from either service.
- **The weekly refresh:** 5 requests, about 353 KB, 30 s, one attempt. 7,792 links and 2,110 Archive items.
  That is slower than on 17 October (11 to 21 s). The read timeout is 20 s per request.
- **`UNAVAILABLE`: 10 of 258 (3.9%), against 1 on 17 October.**
  - 8 ended at 4.0 s, which is the Wikidata query's own budget (`ResolverHttp.SPARQL_BUDGET_MILLIS`). None of
    the 8 was in the index, so no fallback applied.
  - 2 ended at 8 s with "No title match could be read": a candidate's metadata did not load.

## What this did not measure

- Phone and TV latency, and anything across a real Binder.
- A user's watchlist, and load from many devices at once.
- The licence of each admitted item, since the rows do not record the item.
