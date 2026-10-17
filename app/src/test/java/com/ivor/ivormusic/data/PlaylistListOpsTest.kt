package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaylistListOpsTest {

    private fun pl(id: String, createdAt: Long, name: String = id) =
        UserPlaylist(id = id, name = name, createdAt = createdAt)

    @Test
    fun upsertReplacesInPlaceAndKeepsNewestFirst() {
        val list = listOf(pl("c", 30), pl("b", 20), pl("a", 10))
        val next = PlaylistListOps.upsert(list, pl("b", 20, name = "renamed"))
        assertEquals(listOf("c", "b", "a"), next.map { it.id })
        assertEquals("renamed", next[1].name)
    }

    @Test
    fun upsertInsertsAtTheRightPlaceByCreatedAt() {
        val list = listOf(pl("c", 30), pl("a", 10))
        assertEquals(listOf("c", "b", "a"), PlaylistListOps.upsert(list, pl("b", 20)).map { it.id })
        assertEquals(listOf("d", "c", "a"), PlaylistListOps.upsert(list, pl("d", 40)).map { it.id })
        assertEquals(listOf("c", "a", "e"), PlaylistListOps.upsert(list, pl("e", 5)).map { it.id })
    }

    @Test
    fun upsertIntoEmptyList() {
        assertEquals(listOf("a"), PlaylistListOps.upsert(emptyList(), pl("a", 1)).map { it.id })
    }

    @Test
    fun equalCreatedAtKeepTheirRelativeOrder() {
        val list = listOf(pl("x", 10), pl("y", 10))
        assertEquals(listOf("x", "y"), PlaylistListOps.upsert(list, pl("x", 10, "new")).map { it.id })
    }

    @Test
    fun removeDropsOnlyThatPlaylist() {
        val list = listOf(pl("c", 30), pl("b", 20), pl("a", 10))
        assertEquals(listOf("c", "a"), PlaylistListOps.remove(list, "b").map { it.id })
    }

    @Test
    fun removeOfAbsentIdReturnsTheSameList() {
        val list = listOf(pl("a", 10))
        assertSame(list, PlaylistListOps.remove(list, "zzz"))
    }

    @Test
    fun upsertDoesNotMutateItsInput() {
        val list = listOf(pl("b", 20), pl("a", 10))
        PlaylistListOps.upsert(list, pl("c", 30))
        assertEquals(listOf("b", "a"), list.map { it.id })
    }
}
