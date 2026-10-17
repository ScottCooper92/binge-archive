---
name: audit
description: Whole-codebase (or package-scoped) sweep of binge-archive for bugs, licence-rule gaps, convention violations, doc-vs-code drift, dead code and weaknesses in the exported Service — the invariants CI does NOT gate, with the repo's exemptions baked in so findings arrive pre-filtered, verified, and filed as issues. Use when asked to audit the codebase, hunt for bugs/drift/dead code, or health-check a package. For a diff, use /code-review or /security-review instead — this skill is not diff-scoped.
---

# Codebase audit

CI runs the tasks of `./gradlew build` (`.github/workflows/ci.yml`): compile, unit tests, ktlint,
detekt (type-resolved, zero findings), Android lint, `koverVerify` (95% in `:app` and `:index`),
`validateDebugScreenshotTest`, `checkTvMaterialSeparation` and the R8 release build. Do not re-report
anything that set decides. The audit's value is what it does not gate: the licence rule, `Resolve`'s
four answers (sources, OK and empty, `NOT_FOUND`, `INVALID_ARGUMENT`), privacy, the exported Service, and doc drift.

Scope to the whole repo, or to named packages under `app/src/main/kotlin/io/github/scottcooper92/binge/archive/`
and `index/src/main/kotlin/…`.
Skip `binge-companions/`, `design-system/` and `binge-ci/` (submodules with their own repos) and `build/`.

**Three disciplines make this useful rather than noisy:**

1. **Exemptions first.** `src/test`, `src/screenshotTest`, `*PreviewData*` and `@Preview` bodies are
   not production code.
2. **Verify before reporting.** Re-read every candidate in context; label it **confirmed**
   (`file:line` and the exact violation or failure scenario) or **plausible** (needs a repro or a
   decision). Never report a raw grep hit.
3. **Sanity-check the hunt.** Run each grep against a known positive before trusting a zero.
   Manifest attributes, `res/xml` and generated code are real references a grep-only sweep calls dead.

## Dimension 1 — the licence rule (`CLAUDE.md` > The one rule)

- Every path from an Archive item to a `StreamSource` passes the allowlist check. Trace each one.
- The allowlist is exactly the four licences `CLAUDE.md` names. A prefix or substring match that also
  admits a non-commercial, no-derivatives or share-alike variant is a bug.
- A missing or unparseable licence is a rejection, never a default.
- Each source's label names its licence.
- Restricted, dark or non-`movies` items are dropped.

## Dimension 2 — the host boundary (`CLAUDE.md` > Talking to the host)

- `Resolve`'s four answers, checked against `stream.proto`'s own words, not a paraphrase: sources;
  OK and empty (a known title with no licit source); `NOT_FOUND` (not in the catalogue: the index
  and every network route found nothing; a valid series); `INVALID_ARGUMENT` (a bad request,
  including a known non-film). A network failure mapped onto one of them is a bug.
- `mime_type` only ever a `video/*` type.
- Cancellation: work after the host cancels, `runCatching` or `catch (e: Exception)` around suspend
  calls swallowing `CancellationException`.
- At most fifty sources and ten subtitle tracks per source.
- Capabilities declared in the handshake match the rpcs the Service answers; an undeclared rpc
  answers `PERMISSION_DENIED`.
- No contract redefinition: no `.proto`, no hand-written message class, no copied contract constant.

## Dimension 3 — privacy and the network

- Only Wikidata and the Internet Archive are contacted. Grep for every URL and host.
- Coverage-miss logs stay on the device: no upload, analytics or crash SDK.
- No user data stored; backup off; `data_extraction_rules.xml` excludes everything.
- `network_security_config.xml`: system CAs only, no cleartext.
- One HTTP client, with its timeouts.

## Dimension 4 — the exported Service

- Its guard is not loosened, optional or bypassable.
- Every request field treated as untrusted input (ids, episode refs, source ids).
- Blocking IO on a Binder thread; a scope that outlives the call.

## Dimension 5 — correctness

- Index refresh: a failed refresh keeps the previous index; nothing is deleted on failure; an empty
  Wikidata or Archive fetch, an Archive paging loop that ends with `seen < numFound`, and an Archive
  result under half the current `archive_imdb` size never replace the index; the seed never overwrites a newer index.
- Wikidata queries stay well inside the 20 s read timeout (time them live), and the seed task
  degrades to no seed offline.
- Room schema vs exported schemas and migrations.
- Parsers against their fixtures: missing fields, odd formats, empty results.
- Scope hygiene: `GlobalScope`, `runBlocking` in `src/main`, hard-coded dispatchers.

## Dimension 6 — screenshot coverage gaps

For each screen, list the states its composables render and diff against `app/src/screenshotTest`.
An unrendered state is zero coverage. TV screens have TV frames.

## Dimension 7 — doc-vs-code drift

Extract every checkable claim from `CLAUDE.md`, `.ai/agents/*.md`, `README.md` and workflow comments
(task names, the kover floor, SDK/JDK/AGP versions, status claims, capability lists) and verify each
against the source, `libs.versions.toml` and `app/build.gradle.kts`. Report claim, reality, and which
side should change.

## Dimension 8 — dead code

Unused declarations, dead enum entries, never-read DTO fields, never-mutated `MutableStateFlow`s, a
comment describing an abandoned mechanism. Lint already catches unused resources.

## Output contract

- Rank by severity; **confirmed** before **plausible**; every finding at `file:line` with the rule or
  the failure scenario (input, path, wrong result).
- Substantive findings become GitHub issues, deduped against the open backlog first and labelled
  with the repository's existing labels. Scope, not severity, decides issue vs fix-now
  (`CLAUDE.md` > Follow-ups). Micro-nits are a short list in chat. Never a `// TODO` comment.
- State what was **not** audited. "Checked, clean" per dimension is a valid result.
