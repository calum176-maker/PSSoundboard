package com.jms.pssoundboard

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TunaSoundTest {

    @Test
    fun tunaSound_toLibraryItem_mapsCorrectly() {
        val sound = TunaSound(
            id = "tuna_sound_123",
            title = "Vine Boom Loud",
            name = null,
            category = "Meme",
            tags = listOf("boom", "vine"),
            path = "https://us-tuna-sounds-files.voicemod.net/123-sound.mp3",
            soundPath = null,
            thumbnailM = "https://us-tuna-sounds-images.voicemod.net/123-thumb.png",
            properties = TunaProperties(duration = 1450.0)
        )

        val item = sound.toLibraryItem()

        assertEquals("tuna:tuna_sound_123", item.id)
        assertEquals("Vine Boom Loud", item.title)
        assertEquals("Meme", item.category)
        assertEquals("https://us-tuna-sounds-files.voicemod.net/123-sound.mp3", item.url)
        assertEquals("Voicemod Tuna", item.source)
        assertEquals(1450L, item.durationMs)
        assertEquals("https://us-tuna-sounds-images.voicemod.net/123-thumb.png", item.iconUrl)
    }

    @Test
    fun tunaSearchResponse_deserialization_works() {
        val json = """
            {
                "total": 42,
                "size": 10,
                "page": 1,
                "lastPage": 5,
                "items": [
                    {
                        "id": "abc1",
                        "title": "Emotional Damage",
                        "path": "https://us-tuna-sounds-files.voicemod.net/abc1.mp3",
                        "properties": {
                            "duration": 2200.0,
                            "loudness": -12.4
                        }
                    }
                ]
            }
        """.trimIndent()

        val response = Gson().fromJson(json, TunaSearchResponse::class.java)

        assertEquals(42, response.total)
        assertEquals(1, response.page)
        assertEquals(5, response.lastPage)
        assertNotNull(response.items)
        assertEquals(1, response.items?.size)

        val item = response.items!![0].toLibraryItem()
        assertEquals("Emotional Damage", item.title)
        assertEquals("https://us-tuna-sounds-files.voicemod.net/abc1.mp3", item.url)
        assertEquals(2200L, item.durationMs)
        assertEquals("Voicemod Tuna", item.source)
    }
}
