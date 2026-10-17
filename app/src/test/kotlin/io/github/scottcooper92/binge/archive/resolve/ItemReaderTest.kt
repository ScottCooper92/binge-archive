package io.github.scottcooper92.binge.archive.resolve

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class ItemReaderTest {
    private val archive = FakeArchive()
    private val nosferatu = WikidataTitle.parse(resolveFixture("wikidata-653.tsv"))

    @After
    fun close() {
        archive.close()
    }

    private fun found(
        route: Route,
        vararg ids: String,
        facts: TitleFacts = nosferatu,
    ) = Found(route, ids.map { Candidate(it, 0) }, facts)

    @Test
    fun `unlicensed and dark items are dropped, licensed ones kept with their licence`() =
        runTest {
            val items =
                ItemReader(archive.http()).read(
                    found(Route.WIKIDATA, "Nosferatu_unlicensed_transfer", "Nosferatu_dark_item", "PhantasmagoriaTheater-Nosferatu1922909"),
                )

            assertEquals(listOf("PhantasmagoriaTheater-Nosferatu1922909"), items.map { it.item.identifier })
            assertEquals(Licence(Licence.Kind.PUBLIC_DOMAIN_US), items.single().licence)
        }

    @Test
    fun `an item that fails to load is dropped while others are read`() =
        runTest {
            archive.metadataStatus["Nosferatu_DVD_quality"] = 503

            val items =
                ItemReader(
                    archive.http(),
                ).read(found(Route.WIKIDATA, "Nosferatu_DVD_quality", "PhantasmagoriaTheater-Nosferatu1922909"))

            assertEquals(listOf("PhantasmagoriaTheater-Nosferatu1922909"), items.map { it.item.identifier })
        }

    @Test
    fun `every item failing to load fails the read`() {
        archive.metadataStatus["PhantasmagoriaTheater-Nosferatu1922909"] = 503

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                ItemReader(
                    archive.http(),
                ).read(found(Route.WIKIDATA, "PhantasmagoriaTheater-Nosferatu1922909"))
            }
        }
    }

    @Test
    fun `a title-route item must run within 8 percent of the film`() =
        runTest {
            val reader = ItemReader(archive.http())
            val phantasmagoria = "PhantasmagoriaTheater-Nosferatu1922909"

            // 5,560 s against 94 minutes is 1.4% short: kept.
            assertEquals(1, reader.read(found(Route.TITLE, phantasmagoria)).size)
            // Against 120 minutes it is 23% short: dropped.
            assertEquals(0, reader.read(found(Route.TITLE, phantasmagoria, facts = nosferatu.copy(runtimeSeconds = 7200.0))).size)
            assertEquals(0, reader.read(found(Route.TITLE, phantasmagoria, facts = nosferatu.copy(runtimeSeconds = null))).size)
            // The Wikidata route takes Wikidata's word for it.
            assertEquals(1, reader.read(found(Route.WIKIDATA, phantasmagoria, facts = nosferatu.copy(runtimeSeconds = 7200.0))).size)
        }

    @Test
    fun `an item that is missing, restricted or not a movies item is dropped`() =
        runTest {
            val licensed = "\"licenseurl\":\"http://creativecommons.org/publicdomain/zero/1.0/\""
            archive.metadataBodies["restricted"] = """{"metadata":{"mediatype":"movies",$licensed,"access-restricted-item":"true"}}"""
            archive.metadataBodies["audio"] = """{"metadata":{"mediatype":"audio",$licensed}}"""
            archive.metadataBodies["fine"] = """{"metadata":{"mediatype":"movies",$licensed}}"""
            archive.metadataBodies["garbled"] = "<html>busy</html>"

            val items = ItemReader(archive.http()).read(found(Route.WIKIDATA, "missing", "restricted", "audio", "garbled", "fine"))

            assertEquals(listOf("fine"), items.map { it.item.identifier })
            assertEquals(Licence(Licence.Kind.CC0), items.single().licence)
        }

    @Test
    fun `no candidates is no items, without a request`() =
        runTest {
            assertEquals(emptyList<LicensedItem>(), ItemReader(archive.http()).read(Found(null, emptyList(), TitleFacts.NONE)))
        }
}
