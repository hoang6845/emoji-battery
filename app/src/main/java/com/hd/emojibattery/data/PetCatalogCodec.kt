package com.hd.emojibattery.data

import org.json.JSONArray
import org.json.JSONObject

/** Expands shared animation templates into the existing renderer's schema. */
object PetCatalogCodec {
    fun decode(raw: String): JSONArray {
        if (raw.trimStart().startsWith("[")) return JSONArray(raw)
        val catalog = JSONObject(raw)
        require(catalog.getString("format") == "pet_frames_v1") { "Unknown pet catalog format" }
        val base = catalog.getString("base_url")
        require(base.startsWith("https://") && base.endsWith("/")) { "Invalid pet asset base" }
        val templates = catalog.getJSONObject("animations")
        val items = catalog.getJSONArray("items")
        return JSONArray().apply {
            for (i in 0 until items.length()) {
                val item = JSONObject(items.getJSONObject(i).toString())
                val path = item.optString("path", item.getString("id") + "/")
                require(!path.contains(":") && !path.contains("..") && !path.startsWith("/"))
                item.put("source_base_url", base + path)
                val template = templates.getJSONObject(item.getString("animation"))
                template.keys().forEach { action ->
                    item.put(action, JSONObject().put("frames", template.getJSONArray(action)))
                }
                item.remove("animation")
                item.remove("path")
                put(item)
            }
        }
    }
}
