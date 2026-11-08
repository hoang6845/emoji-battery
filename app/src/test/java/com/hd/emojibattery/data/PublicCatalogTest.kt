package com.hd.emojibattery.data

import com.hd.emojibattery.runtime.CatalogGroups
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublicCatalogTest {
    @Test fun batteryAndTrollUseSeparatePublicCatalogsWithFolderCategories() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_battery_v3" to JSONArray("""[{"id":"battery_a","src":"battery/cute_animal/a.png","category":"cute_animal","is_lock":false}]"""),
            "data_battery_troll" to JSONArray("""[{"id":"troll_a","src":"Data Battery Troll/Data Battery Troll (DONE)/Concept 1/a.png","category":"Concept 1","is_lock":false}]"""),
            "data_theme_v3" to JSONArray("""[{"id":"old_theme","src":"themes/old.png","type":"cute_animal"}]"""),
            "theme_type_v3" to JSONArray("""[{"type":"cute_animal","name":"Old theme label"}]""")))
        val battery = result.getJSONArray("battery_styles").getJSONObject(0)
        val troll = result.getJSONArray("troll_styles").getJSONObject(0)
        assertEquals(SourceCatalog.PUBLIC_BASE + "battery/cute_animal/a.png", battery.getString("asset_url"))
        assertEquals(SourceCatalog.PUBLIC_BASE + "Data%20Battery%20Troll/Data%20Battery%20Troll%20%28DONE%29/Concept%201/a.png", troll.getString("asset_url"))
        assertTrue(battery.getBoolean("free"))
        assertTrue(troll.getBoolean("free"))
        assertEquals(listOf("All", "cute_animal"), CatalogGroups.names(result, "battery_styles"))
        assertEquals(listOf("All", "Concept 1"), CatalogGroups.names(result, "troll_styles"))
        assertEquals(SourceCatalog.EMOJI_BASE + "themes/old.png", result.getJSONArray("themes").getJSONObject(0).getString("asset_url"))
        val cleared = SourceCatalog.merge(result, mapOf("data_battery_troll" to JSONArray()))
        assertEquals(0, cleared.getJSONArray("troll_styles").length())
        assertEquals(1, cleared.getJSONArray("battery_styles").length())
    }

    @Test fun relativePathsEncodeUnicodeSpacesAndReservedCharactersOnce() {
        val path = "Data Emotion/Ảnh (1)+#.png"
        val url = SourceCatalog.url(path, SourceCatalog.PUBLIC_BASE)
        assertEquals(SourceCatalog.PUBLIC_BASE + "Data%20Emotion/%E1%BA%A2nh%20%281%29%2B%23.png", url)
        assertEquals(url, SourceCatalog.url(url, SourceCatalog.PUBLIC_BASE))
        assertEquals(SourceCatalog.EMOJI_BASE + "old/a%20b.png", SourceCatalog.url("old/a%20b.png", SourceCatalog.EMOJI_BASE))
    }

    @Test fun stickersUseOwnGifAndKeepBatteryEmojiSource() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_sticker" to JSONArray("""[{"id":"cat","name":"Cat","category":"anime","image_url":"Data Sticker/cat.gif","free":true}]"""),
            "data_emoji_v3" to JSONArray("""[{"id":"cat","src":"old/cat.gif","is_lock":false}]""")))
        val sticker = result.getJSONArray("stickers").getJSONObject(0)
        assertEquals(SourceCatalog.PUBLIC_BASE + "Data%20Sticker/cat.gif", sticker.getString("asset_url"))
        assertEquals(sticker.getString("asset_url"), sticker.getString("image_url"))
        assertTrue(sticker.getBoolean("free"))
        assertEquals(SourceCatalog.EMOJI_BASE + "old/cat.gif", result.getJSONArray("emojis").getJSONObject(0).getString("asset_url"))
        assertEquals(listOf("All", "anime"), CatalogGroups.names(result, "stickers"))
    }

    @Test fun chargingAndStatusAnimationsNeverShareCollection() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_animation" to JSONArray("""[{"id":"status","src":"animation/Animation_Status Bar/a.gif","is_lock":false}]"""),
            "data_charging_animation" to JSONArray("""[{"id":"charge","src":"animation/Animation_Charging Battery/Animals/b.gif","category":"Animals","is_lock":false}]""")))
        assertEquals("status", result.getJSONArray("animations").getJSONObject(0).getString("source_id"))
        assertEquals("charge", result.getJSONArray("charging_animations").getJSONObject(0).getString("source_id"))
        assertEquals(listOf("All", "Animals"), CatalogGroups.names(result, "charging_animations"))
        val cleared = SourceCatalog.merge(result, mapOf("data_charging_animation" to JSONArray()))
        assertEquals(0, cleared.getJSONArray("charging_animations").length())
        assertEquals(1, cleared.getJSONArray("animations").length())
        assertEquals(listOf("All"), CatalogGroups.names(cleared, "charging_animations"))
    }

    @Test fun wallpaperCategoryDoesNotReuseOrReplacePetMetadata() {
        val result = SourceCatalog.merge(JSONObject(), mapOf(
            "data_wallpaper_pet_on_screen" to JSONArray("""[{"imagePath":"Data Wallpaper/a.png","imagePathThumb":"Data Wallpaper/a.png","category":"Anime","is_lock":false}]"""),
            "data_category_pet_on_screen" to JSONArray("""[{"type":"Anime","name":"Existing pet label"}]""")))
        val wallpaper = result.getJSONArray("wallpapers").getJSONObject(0)
        assertEquals("Anime", wallpaper.getString("category"))
        assertTrue(wallpaper.getBoolean("free"))
        assertEquals(SourceCatalog.PUBLIC_BASE + "Data%20Wallpaper/a.png", wallpaper.getString("asset_url"))
        assertEquals(listOf("All", "Anime"), CatalogGroups.names(result, "wallpapers"))
        assertEquals("Existing pet label", result.getJSONArray("character_categories").getJSONObject(0).getString("name"))
        assertEquals(SourceCatalog.PET_BASE, SourceCatalog.baseUrl("data_character_pet_on_screen"))
        assertEquals(SourceCatalog.EMOJI_BASE, SourceCatalog.baseUrl("data_theme_v3"))
    }
}
