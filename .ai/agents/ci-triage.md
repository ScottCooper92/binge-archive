# CI triage

Maps a red CI run to its correct fix. `author-ci-fix.yml` reads this file from `main` and works from
the table below. It gets one repair attempt per commit, so a guess is expensive and stopping is cheap.

CI runs the tasks of `./gradlew build` as four jobs in parallel, checks the submodule pins in a fifth
on a PR, then aggregates them in a last one. `.github/workflows/ci.yml` is the source of truth:

| Job | Runs |
| --- | --- |
| `submodule-pins` | On a PR only: binge-ci's check that every submodule pin is merged upstream, unless the PR carries `hold` |
| `checks` | `./gradlew build` minus the tasks the next three jobs own: Kotlin compile, `ktlintCheck`, `detekt`, Android `lint`, `checkTvMaterialSeparation`, the debug assemble, `checkTranslationStaleness`, and `:index:test` with `:index:koverVerify` |
| `tests` | `:app:testDebugUnitTest` and `:app:koverVerify` |
| `screenshots` | `:app:validateDebugScreenshotTest` |
| `release` | `:app:assembleRelease`: R8 and lint-vital |
| `build` | Nothing of its own. Green only if the other five are. Merges the four Gradle jobs' reports into one `build-reports` artifact |

`build` is the check branch protection requires, and the one `author-ci-fix.yml` reads. Its log only
says which job failed. Read the failing job's log: that is where the error is.

`koverVerify` and `validateDebugScreenshotTest` have no row below. Raising coverage means writing a
real test, and accepting a new frame is a human's call, so both are a **stop**. So is any failure the
table does not name.

## The table

| Failure | Why | Fix |
| --- | --- | --- |
| `ktlint…SourceSetCheck` reports formatting | Style rule | `./gradlew ktlintFormat`, then read the diff it made. **Never** add a baseline or a disable comment |
| `detekt` reports a finding | Static analysis, gated at zero | Fix the code the finding describes. **Never** add a `detekt-baseline.xml` or a `@Suppress` to make it pass |
| Android `lint` reports an error | AGP's checks | Fix the code. **Never** add `lint-baseline.xml`, `abortOnError = false`, or a disable for the rule |
| Android `lint` reports `NewApi` | An API above `minSdk` 26 | Guard it with a version check or use the AndroidX call. Raising `minSdk` is a **stop** |
| `checkTvMaterialSeparation` | A Material 3 import under `ui/tv`, or a tv-material import elsewhere | Import from the right library. Do not add to the allowlist |
| Kotlin compile error | Ordinary | Fix it. If a contract symbol "does not exist", the app and the contract it builds against disagree: fix the app. A wrong contract is a PR against binge-companions |
| A unit test fails | Ordinary | Fix the code the test describes. Changing an assertion is only right when the PR deliberately changed that behaviour and says so |
| A licence test fails | The allowlist or its check | Fix the code so the test passes as written. **Never** widen the allowlist or loosen the check. If the test itself looks wrong, **stop** |
| `validateDebugScreenshotTest` reports a diff | A frame no longer renders like its PNG | **Stop.** Leave the tree clean and say in a PR comment: run `./gradlew :app:updateDebugScreenshotTest`, look at the PNGs, commit them with the code |
| `koverVerify` below the floor | Coverage | **Stop.** Writing the right test is a judgment call |
| `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 rejects the plugin being applied to a module | Remove the `apply` from that module. The root build's `apply false` is intentional; leave it. Do not downgrade AGP |
| Manifest merger failure | Conflicting declarations | Make them agree. `tools:replace` is a last resort and needs a reason in the PR |
| Gradle "could not resolve" | Usually transient, or a catalog edit | If the diff touched `libs.versions.toml`, fix that. Otherwise it is infrastructure: **stop** |
| Missing SDK component or licence | Runner image | **Stop.** Changing `compileSdk` to match the runner is not a repair |
| AGP needs a newer Gradle, or the reverse | The diff bumped one | **Stop** unless the diff meant to bump both |
| Empty or unreadable log | Nothing to triage | **Stop.** The workflow already comments on the PR |

## Verifying

Run only what failed:

```sh
./gradlew :app:ktlintCheck
./gradlew :app:detekt
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :index:test
```

Do not run the full gate. CI does that on push.

## Never

- Add a ktlint baseline, `lint-baseline.xml`, `detekt-baseline.xml`, or a disable comment.
- Set `abortOnError = false`, exclude a source set from `check`, or lower the kover floor.
- Raise `minSdk` or change `compileSdk` to make a failure go away.
- Widen the licence allowlist or loosen the licence check.
- Loosen whatever guards the exported Service.

## When in doubt

A red build is not permission to redesign. Leaving the tree clean and saying why is a valid and
preferred outcome; a wrong guess costs more than stopping.
