## What and why

<!-- One short paragraph, then `Closes ScottCooper92/binge-companions#N` (or `Part of …`) for the phase it delivers. Say which contract version and capabilities a change to the Service relies on. -->

## Checked before pushing

<!-- Tick what you ran locally on the final head. If you couldn't run one, leave it unticked and say why. Don't tick a box you didn't run. -->
- [ ] `./gradlew ktlintCheck detekt`
- [ ] `./gradlew :app:lintDebug`
- [ ] `./gradlew :index:test :app:testDebugUnitTest koverVerify`
- [ ] Re-recorded screenshots viewed: all of them, or every one checked by script and a sample by eye (say which)
- [ ] Left to CI, and why: <!-- e.g. the release build's R8 runs out of memory locally -->
