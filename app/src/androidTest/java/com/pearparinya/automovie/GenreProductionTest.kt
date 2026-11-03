package com.pearparinya.automovie

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GenreProductionTest {
    @Test fun everyVisibleGenreHasDistinctSituationsAndFilmDirection() {
        val ids = ChannelCatalog.storyShelves.flatMap { it.categoryIds }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(ChannelCatalog.mainCategories.map { it.id }.toSet(), ids.toSet())
        ChannelCatalog.mainCategories.forEach { genre ->
            val topics = ChannelCatalog.shortTopics(genre.id)
            assertTrue(topics.size >= 7)
            assertEquals(topics.size, topics.distinct().size)
            val direction = ChannelCatalog.direction(genre.label)
            assertTrue(direction.contains("FILMMAKING DIRECTION"))
            assertTrue(direction.contains("user-selected situation"))
            assertTrue(ChannelCatalog.genre(JSONObject().put("category", genre.label)).contains(genre.id))
        }
    }
}
