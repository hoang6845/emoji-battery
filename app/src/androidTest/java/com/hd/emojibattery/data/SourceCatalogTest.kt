package com.hd.emojibattery.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceCatalogTest {
    @Test fun preservesThemeComponentsAndDefaultLock() {
        val item = SourceCatalog.adapt("data_theme_v3", JSONArray("""[{"id":"1","type":"cute","src":"themes/a.png","battery_src":"b.png","emoji_src":"e.gif"}]""")).getJSONObject(0)
        assertEquals(SourceCatalog.EMOJI_BASE + "themes/a.png", item.getString("image_url"))
        assertEquals("b.png", item.getString("battery_src"))
        assertFalse(item.getBoolean("free"))
    }
    @Test fun separatesBackgroundFromWallpaperAndKeepsPetActions() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_background" to JSONArray("""[{"id":"b","src":"b.png","is_lock":false}]"""),
            "data_wallpaper_pet_on_screen" to JSONArray("""[{"imagePath":"wall/a.png","imagePathThumb":"wall/thumb.png","category":"cute"}]"""),
            "data_character_pet_on_screen" to JSONArray("""[{"character":"cat","thumbnail":"pets/cute/cat.png","price":15,"walk":{"urls":["a.png","b.png"]}}]"""),
            "data_category_pet_on_screen" to JSONArray("""[{"type":"cute","name":"Cute"}]""")))
        assertEquals(1, result.getJSONArray("backgrounds").length())
        assertEquals(SourceCatalog.PUBLIC_BASE + "wall/a.png", result.getJSONArray("wallpapers").getJSONObject(0).getString("asset_url"))
        val pet = result.getJSONArray("pets").getJSONObject(0)
        assertEquals("Cute", pet.getString("category"))
        assertEquals(2, pet.getJSONObject("walk").getJSONArray("urls").length())
        assertFalse(pet.getBoolean("free"))
        assertFalse(result.has("stickers"))
    }
    @Test fun duplicateIdsAcrossPetSourcesRemainDistinct() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_pet" to JSONArray("""[{"id":"cat","thumbnail":"a.png","gifUrls":["idle.gif"]}]"""),
            "data_pet_moving_v3" to JSONArray("""[{"id":"cat","thumbnail":"b.png","walkStatuses":{"20":{"frames":["low.png"]}}}]""")))
        val pets = result.getJSONArray("pets")
        assertNotEquals(pets.getJSONObject(0).getString("id"), pets.getJSONObject(1).getString("id"))
        assertEquals("low.png", pets.getJSONObject(1).getJSONObject("walkStatuses").getJSONObject("20").getJSONArray("frames").getString(0))
    }
    @Test(expected = org.json.JSONException::class) fun rejectsNonObjectEntries() {
        SourceCatalog.adapt("data_emoji_v3", JSONArray("[123]"))
    }
}
