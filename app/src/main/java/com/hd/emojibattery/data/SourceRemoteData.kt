package com.hd.emojibattery.data

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Credentials are read only from activated Firebase config, never saved in catalog JSON. */
object SourceRemoteData {
    const val EMOJI_TOKEN = "emoji_access_key_remote_data"
    const val EMOJI_BACKUP_TOKEN = "emoji_access_key_remote_data_backup"
    const val PET_TOKEN = "pet_access_key_remote_data"

    fun tokens(rawUrl: String): List<String> {
        val keys = when {
            rawUrl.startsWith(SourceCatalog.EMOJI_BASE) -> listOf(EMOJI_TOKEN, EMOJI_BACKUP_TOKEN)
            rawUrl.startsWith(SourceCatalog.PET_BASE) -> listOf(PET_TOKEN)
            else -> emptyList()
        }
        return runCatching { keys.map { FirebaseRemoteConfig.getInstance().getString(it).trim() }
            .filter { it.isNotBlank() }.distinct() }.getOrDefault(emptyList()) + ""
    }

    /** No redirects: authorization must never leave the exact source repository. */
    fun download(rawUrl: String, limit: Int): ByteArray? {
        val url = runCatching { URL(rawUrl) }.getOrNull() ?: return null
        if (url.protocol != "https" || url.userInfo != null) return null
        for (token in tokens(rawUrl)) {
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 10_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github.v3.raw")
                if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
                val code = connection.responseCode
                if (code == 401 || code == 403 || code == 404) continue
                if (code !in 200..299 || connection.contentLength > limit) return null
                val bytes = connection.inputStream.use { it.readBytesLimited(limit) } ?: return null
                return bytes
            } catch (_: Exception) {
                return null
            } finally { connection?.disconnect() }
        }
        return null
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > limit) return null
            out.write(buffer, 0, count)
        }
    }

    fun load(context: Context, config: FirebaseRemoteConfig, base: JSONObject): JSONObject {
        val cache = context.getSharedPreferences("source_catalog_cache", Context.MODE_PRIVATE)
        val sources = linkedMapOf<String, JSONArray>()
        (SourceCatalog.emojiKeys + SourceCatalog.petKeys).forEach { (key, _) ->
            if (!PetSourcePolicy.allowsKey(key)) return@forEach
            val configured = config.getString(key).trim()
            // Never interpret cached legacy relative paths against the new public repository.
            val cacheKey = if (key in SourceCatalog.publicKeys) "public_v1_$key" else key
            val raw = when {
                configured.startsWith("[") -> configured
                configured.startsWith("{") -> configured // invalid array: preserve last good data
                key in SourceCatalog.petKeys && key !in SourceCatalog.publicKeys && key != "data_category_pet_on_screen" -> {
                    val path = configured.ifBlank { "$key.json" }
                    download(SourceCatalog.url(path, SourceCatalog.PET_BASE), 12 * 1024 * 1024)?.toString(Charsets.UTF_8)
                }
                configured.isNotBlank() -> download(SourceCatalog.url(configured,
                    SourceCatalog.baseUrl(key)), 12 * 1024 * 1024)?.toString(Charsets.UTF_8)
                else -> null
            }
            fun valid(value: String?): JSONArray? = runCatching {
                JSONArray(value ?: return null).also { SourceCatalog.adapt(key, it) }
            }.getOrNull()
            val remote = valid(raw)
            if (remote != null) cache.edit().putString(cacheKey, remote.toString()).apply()
            val fallback = valid(cache.getString(cacheKey, null)) ?: valid(runCatching {
                context.assets.open("source_data/$key.json").bufferedReader().use { it.readText() }
            }.getOrNull())
            (remote ?: fallback)?.let { sources[key] = it }
        }
        return SourceCatalog.merge(base, sources)
    }
}
