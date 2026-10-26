package com.cooper.binge.companion.archive.index

/** Reads a fixture from `src/test/resources/fixtures`: trimmed copies of real responses. */
internal fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "No fixture $name" }.readText()
