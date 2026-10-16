package io.github.scottcooper92.binge.archive.index

/** Reads a fixture from `src/test/resources/fixtures`: trimmed copies of real responses. */
internal fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "No fixture $name" }.readText()
