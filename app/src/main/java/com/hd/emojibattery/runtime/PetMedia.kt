package com.hd.emojibattery.runtime

import com.hd.emojibattery.data.SourceCatalog
import org.json.JSONArray
import org.json.JSONObject

object PetMedia {
    private fun urls(pet: JSONObject, array: JSONArray?): List<String> = (0 until (array?.length() ?: 0))
        .map { SourceCatalog.url(array!!.optString(it), pet.optString("source_base_url")) }.filter { it.isNotBlank() }
    fun frames(pet: JSONObject, action: String, battery: Int): List<String> {
        fun framesFor(name: String): List<String> {
            val status = if (name == "walk") {
                val walk = pet.optJSONObject("walkStatuses")
                val key = when { battery <= 20 -> "20"; battery <= 50 -> "50"; battery <= 80 -> "80"; else -> "100" }
                walk?.optJSONObject(key) ?: walk?.keys()?.asSequence()?.firstOrNull()?.let { walk.optJSONObject(it) }
            } else null
            val obj = status ?: pet.optJSONObject(name) ?: if (name == "fall") pet.optJSONObject("drop") else null
            return urls(pet, obj?.optJSONArray("frames") ?: obj?.optJSONArray("urls")).ifEmpty {
                obj?.optString("gif")?.takeIf { it.isNotBlank() }?.let { listOf(SourceCatalog.url(it, pet.optString("source_base_url"))) }.orEmpty()
            }
        }
        return framesFor(action).ifEmpty { framesFor("walk") }.ifEmpty {
            val idle = urls(pet, pet.optJSONArray("gifUrls"))
            val index = listOf("sit", "walk", "tap", "sleep", "fly").indexOf(action).coerceAtLeast(0)
            idle.getOrNull(index % idle.size.coerceAtLeast(1))?.let { listOf(it) }.orEmpty()
        }
            .ifEmpty { listOf(AppliedContent.url(pet, "image_url")).filter { it.isNotBlank() } }
    }
    fun allAssets(pet: JSONObject): List<String> = buildList {
        addAll(urls(pet, pet.optJSONArray("gifUrls")))
        listOf("walk", "climb", "drop", "fall", "fly", "drag", "sleep", "snap", "tap", "top", "sit", "run").forEach { action ->
            listOf(20, 50, 80, 100).forEach { addAll(frames(pet, action, it)) }
        }
    }.distinct()
}
