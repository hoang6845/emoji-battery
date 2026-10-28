package com.hd.emojibattery.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Bundled → last-known-good → Firebase Remote Config URL → GitHub JSON fallback chain. */
class AppContentRepository private constructor(context: Context) {
    companion object {
        const val REMOTE_URL_KEY = "emoji_battery_content_url"
        private const val PREFS = "emoji_battery_content"
        private const val CACHE_KEY = "last_good_json"
        private const val MAX_JSON_BYTES = 3 * 1024 * 1024
        @Volatile private var instance: AppContentRepository? = null

        fun get(context: Context): AppContentRepository = instance ?: synchronized(this) {
            instance ?: AppContentRepository(context.applicationContext).also { instance = it }
        }
    }

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var content = readAsset()
    @Volatile private var refreshRunning = false

    init {
        parseValid(prefs.getString(CACHE_KEY, "").orEmpty())?.let { content = it }
    }

    @Synchronized fun snapshot(): JSONObject = runCatching { JSONObject(content.toString()) }.getOrElse { JSONObject() }
    fun array(key: String): JSONArray? = snapshot().optJSONArray(key)

    /** Delivers local/cached data immediately, then calls back again if remote content changes. */
    fun load(context: Context, callback: ((JSONObject, Boolean) -> Unit)?) {
        callback?.invoke(snapshot(), false)
        refresh(callback)
    }

    fun refresh(callback: ((JSONObject, Boolean) -> Unit)?) {
        synchronized(this) {
            if (refreshRunning) return
            refreshRunning = true
        }
        worker.execute {
            try {
                if (FirebaseApp.getApps(app).isEmpty()) {
                    finish(callback, false)
                    return@execute
                }
                val remoteConfig = FirebaseRemoteConfig.getInstance()
                val config = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
                remoteConfig.setConfigSettingsAsync(config)
                remoteConfig.setDefaultsAsync(mapOf(REMOTE_URL_KEY to ""))
                remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                    worker.execute {
                        var updated = false
                        if (task.isSuccessful) {
                            val remoteUrl = remoteConfig.getString(REMOTE_URL_KEY).trim()
                            val remote = if (remoteUrl.isNotEmpty()) downloadJson(remoteUrl) else null
                            if (remote != null) {
                                synchronized(this) { content = remote }
                                prefs.edit().putString(CACHE_KEY, remote.toString()).apply()
                                updated = true
                            }
                        }
                        finish(callback, updated)
                    }
                }
            } catch (_: Exception) {
                finish(callback, false)
            }
        }
    }

    private fun finish(callback: ((JSONObject, Boolean) -> Unit)?, updated: Boolean) {
        synchronized(this) { refreshRunning = false }
        if (callback != null) {
            val latest = snapshot()
            main.post { callback(latest, updated) }
        }
    }

    private fun downloadJson(rawUrl: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(rawUrl)
            if (!url.protocol.equals("https", true)) return null
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "EmojiBattery-Android")
            connection.connect()
            if (connection.responseCode !in 200..299 || !connection.url.protocol.equals("https", true)) return null
            if (connection.contentLength > MAX_JSON_BYTES) return null
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_JSON_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            parseValid(String(output.toByteArray(), Charsets.UTF_8))
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseValid(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        return try {
            JSONObject(raw).takeIf { value ->
                value.optInt("schema_version", 0) in 1..2 &&
                    value.optJSONArray("themes") != null && value.optJSONArray("emojis") != null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readAsset(): JSONObject = try {
        val raw = app.assets.open("app_content.json").use { String(it.readBytes(), Charsets.UTF_8) }
        parseValid(raw) ?: JSONObject()
    } catch (_: Exception) {
        JSONObject()
    }
}
