package com.hd.emojibattery.data

import com.hd.emojibattery.runtime.PetMedia
import org.junit.Assert.*
import org.junit.Test

class PetCatalogCodecTest {
    @Test fun sharedFramesResolveForEachPetAndPreserveOrderAndLock() {
        val raw = """{"format":"pet_frames_v1","base_url":"https://example.com/assets/",
          "animations":{"0":{"walk":["1.png","2.png","1.png"],"sit":["3.png"]}},
          "items":[{"id":"cat","name":"Cat","type":"Games","thumbnail":"1.png","animation":"0","is_lock":false},
          {"id":"dog-v1","path":"dog/","name":"Dog","thumbnail":"2.png","animation":"0","is_lock":true}]}"""
        val pets = SourceCatalog.adapt("data_pet_moving_v3", PetCatalogCodec.decode(raw))
        val cat = pets.getJSONObject(0)
        val dog = pets.getJSONObject(1)
        assertEquals("https://example.com/assets/cat/1.png", cat.getString("image_url"))
        assertEquals(listOf("https://example.com/assets/cat/1.png", "https://example.com/assets/cat/2.png",
            "https://example.com/assets/cat/1.png"), PetMedia.frames(cat, "walk", 50))
        assertEquals(listOf("https://example.com/assets/dog/3.png"), PetMedia.frames(dog, "sit", 100))
        assertTrue(cat.getBoolean("free"))
        assertFalse(dog.getBoolean("free"))
    }

    @Test fun legacyAbsoluteUrlsStillWork() {
        val pets = SourceCatalog.adapt("data_pet_moving_v3", PetCatalogCodec.decode(
            """[{"id":"old","thumbnail":"https://example.com/a.png","walk":{"frames":["https://example.com/b.png"]}}]"""))
        assertEquals(listOf("https://example.com/b.png"), PetMedia.frames(pets.getJSONObject(0), "walk", 20))
    }

    @Test(expected = org.json.JSONException::class) fun missingTemplateRejectsCatalog() {
        PetCatalogCodec.decode("""{"format":"pet_frames_v1","base_url":"https://example.com/","animations":{},"items":[{"id":"cat","animation":"missing"}]}""")
    }
}
